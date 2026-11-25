package com.alhaq.amnishield.security

import android.content.Context
import android.content.SharedPreferences
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.data.blockers.ShieldLockConfig
import com.alhaq.amnishield.data.blockers.ShieldLockMode
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.PasswordHasher

/**
 * Sealed target representing the authentication requirement determined for a rule or system action.
 */
sealed class AuthTarget {
    /** No authentication required; caller may proceed immediately. */
    data object PassThrough : AuthTarget()

    /** Requires verifying the global Master / Anti-Uninstall bypass PIN. */
    data class RequireGlobalPin(val storedHash: String?) : AuthTarget()

    /** Requires verifying a dedicated custom per-rule PIN. */
    data class RequireRulePin(
        val ruleId: String,
        val hash: String,
        val salt: String
    ) : AuthTarget()

    /** Blocked due to active rate-limiting / brute-force lockout. */
    data class LockedOut(val remainingMillis: Long) : AuthTarget()
}

/**
 * Sealed challenge target for Always-On Shields asymmetric friction verification.
 */
sealed class ShieldAuthTarget {
    data object PassThrough : ShieldAuthTarget()
    data class RequireGlobalPin(val storedHash: String) : ShieldAuthTarget()
    data class RequireCustomPin(val hash: String, val salt: String) : ShieldAuthTarget()
    data class RequireDelay(val delaySeconds: Int) : ShieldAuthTarget()
    data class RequireQr(val expectedValue: String) : ShieldAuthTarget()
    data class LockedOut(val remainingMillis: Long) : ShieldAuthTarget()
}

/**
 * Result of a PIN challenge verification attempt.
 */
sealed class AuthResult {
    /** PIN was correct. Authentication granted. */
    data object Success : AuthResult()

    /** PIN was incorrect. Returns remaining attempts and whether lockout was just triggered. */
    data class InvalidPin(
        val attemptsRemaining: Int,
        val isLockedOut: Boolean,
        val lockoutRemainingMillis: Long = 0L
    ) : AuthResult()

    /** Action rejected because verification is currently locked out. */
    data class LockedOut(val remainingMillis: Long) : AuthResult()
}

/**
 * Central security routing engine for rule-level and system-level authentication challenges.
 * Manages challenge resolution, PBKDF2 verification, and anti-brute-force rate limiting.
 */
class AuthResolver(
    private val context: Context? = null,
    injectedSecurityPrefs: SharedPreferences? = null,
    injectedAntiUninstallPrefs: SharedPreferences? = null
) {

    companion object {
        const val PREFS_SECURITY = "security_auth_locks"
        const val KEY_FAILED_ATTEMPTS = "failed_auth_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until_timestamp"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 2 * 60 * 1000L // 2 minutes

        private const val PREFS_ANTI_UNINSTALL = "anti_uninstall"
        private const val KEY_ANTI_UNINSTALL_PASSWORD = "password"

        @Volatile
        private var instance: AuthResolver? = null

        fun getInstance(context: Context): AuthResolver {
            return instance ?: synchronized(this) {
                instance ?: AuthResolver(context.applicationContext).also { instance = it }
            }
        }
    }

    private val securityPrefs: SharedPreferences by lazy {
        injectedSecurityPrefs ?: context?.getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
        ?: throw IllegalStateException("Context or SharedPreferences required")
    }

    private val antiUninstallPrefs: SharedPreferences by lazy {
        injectedAntiUninstallPrefs ?: context?.getSharedPreferences(PREFS_ANTI_UNINSTALL, Context.MODE_PRIVATE)
        ?: throw IllegalStateException("Context or SharedPreferences required")
    }

    /**
     * Checks if a global bypass / anti-uninstall PIN is configured in the app.
     */
    fun isGlobalPinConfigured(): Boolean = hasGlobalPin()

    /**
     * Resolves what authentication challenge is needed to modify, delete, or disable a rule (or system action).
     *
     * @param rule The target rule implementing [BaseRule], or null for system actions (accessibility, device admin, anti-uninstall).
     */
    fun resolveChallenge(rule: BaseRule?): AuthTarget {
        val lockoutRemaining = getLockoutRemainingMillis()
        if (lockoutRemaining > 0L) {
            return AuthTarget.LockedOut(lockoutRemaining)
        }

        if (rule == null) {
            // System-level action: require global PIN if configured
            val globalHash = getGlobalPinHash()
            return if (!globalHash.isNullOrEmpty()) {
                AuthTarget.RequireGlobalPin(globalHash)
            } else {
                AuthTarget.PassThrough
            }
        }

        return when (rule.authType) {
            AuthType.NONE -> AuthTarget.PassThrough
            AuthType.GLOBAL_PIN -> {
                val globalHash = getGlobalPinHash()
                if (!globalHash.isNullOrEmpty()) {
                    AuthTarget.RequireGlobalPin(globalHash)
                } else {
                    AuthTarget.PassThrough
                }
            }
            AuthType.RULE_PIN -> {
                val hash = rule.rulePasswordHash
                val salt = rule.rulePasswordSalt
                if (!hash.isNullOrEmpty() && !salt.isNullOrEmpty()) {
                    AuthTarget.RequireRulePin(rule.id, hash, salt)
                } else if (!hash.isNullOrEmpty()) {
                    // Self-contained hash fallback
                    AuthTarget.RequireGlobalPin(hash)
                } else {
                    AuthTarget.PassThrough
                }
            }
        }
    }

    /**
     * Verifies the user's entered PIN against the resolved challenge target.
     * Updates brute-force attempt counters and triggers lockout when max attempts are exceeded.
     */
    fun verifyChallenge(target: AuthTarget, inputPin: String): AuthResult {
        val lockoutRemaining = getLockoutRemainingMillis()
        if (lockoutRemaining > 0L) {
            return AuthResult.LockedOut(lockoutRemaining)
        }

        val isMatch = when (target) {
            is AuthTarget.PassThrough -> true
            is AuthTarget.RequireGlobalPin -> {
                PasswordHasher.verify(inputPin, target.storedHash)
            }
            is AuthTarget.RequireRulePin -> {
                PasswordHasher.verifyWithSalt(inputPin, target.hash, target.salt)
            }
            is AuthTarget.LockedOut -> false
        }

        return if (isMatch) {
            clearLockoutState()
            AuthResult.Success
        } else {
            handleFailedAttempt()
        }
    }

    /**
     * Resolves the unlock challenge for an Always-On Shield weakening action.
     * Note: Always evaluates active CUSTOM_PIN and QR_CODE locks regardless of Pro status
     * so users are never locked out if a Pro subscription expires.
     */
    fun resolveShieldChallenge(lockConfig: ShieldLockConfig): ShieldAuthTarget {
        val sanitized = lockConfig.sanitize()
        val lockoutRemaining = getLockoutRemainingMillis()
        if (lockoutRemaining > 0L && (sanitized.mode == ShieldLockMode.INHERIT_GLOBAL_PIN || sanitized.mode == ShieldLockMode.CUSTOM_PIN)) {
            return ShieldAuthTarget.LockedOut(lockoutRemaining)
        }

        return when (sanitized.mode) {
            ShieldLockMode.NONE -> ShieldAuthTarget.PassThrough
            ShieldLockMode.INHERIT_GLOBAL_PIN -> {
                val globalHash = getGlobalPinHash()
                if (!globalHash.isNullOrEmpty()) {
                    ShieldAuthTarget.RequireGlobalPin(globalHash)
                } else {
                    ShieldAuthTarget.PassThrough
                }
            }
            ShieldLockMode.PAUSE_DELAY -> {
                ShieldAuthTarget.RequireDelay(sanitized.delaySeconds.coerceIn(5, 300))
            }
            ShieldLockMode.CUSTOM_PIN -> {
                val hash = sanitized.customPinHash
                val salt = sanitized.customPinSalt
                if (!hash.isNullOrEmpty() && !salt.isNullOrEmpty()) {
                    ShieldAuthTarget.RequireCustomPin(hash, salt)
                } else {
                    ShieldAuthTarget.PassThrough
                }
            }
            ShieldLockMode.QR_CODE -> {
                val qr = sanitized.registeredQrValue
                if (!qr.isNullOrEmpty()) {
                    ShieldAuthTarget.RequireQr(qr)
                } else {
                    ShieldAuthTarget.PassThrough
                }
            }
        }
    }

    /**
     * Verifies a PIN entered against a [ShieldAuthTarget] using the shared PBKDF2 + lockout engine.
     */
    fun verifyShieldPin(target: ShieldAuthTarget, inputPin: String): AuthResult {
        val lockoutRemaining = getLockoutRemainingMillis()
        if (lockoutRemaining > 0L) {
            return AuthResult.LockedOut(lockoutRemaining)
        }

        val isMatch = when (target) {
            is ShieldAuthTarget.PassThrough -> true
            is ShieldAuthTarget.RequireGlobalPin -> PasswordHasher.verify(inputPin, target.storedHash)
            is ShieldAuthTarget.RequireCustomPin -> PasswordHasher.verifyWithSalt(inputPin, target.hash, target.salt)
            is ShieldAuthTarget.RequireDelay,
            is ShieldAuthTarget.RequireQr,
            is ShieldAuthTarget.LockedOut -> false
        }

        return if (isMatch) {
            clearLockoutState()
            AuthResult.Success
        } else {
            handleFailedAttempt()
        }
    }

    /**
     * Checks if a global bypass / anti-uninstall PIN is configured in the app.
     */
    fun hasGlobalPin(): Boolean {
        val hash = getGlobalPinHash()
        return !hash.isNullOrEmpty()
    }

    /**
     * Gets the stored global anti-uninstall PIN hash, migrating any legacy `AppPreferences.profile_pin` if needed.
     */
    fun getGlobalPinHash(): String? {
        val existing = antiUninstallPrefs.getString(KEY_ANTI_UNINSTALL_PASSWORD, null)
        if (!existing.isNullOrEmpty()) return existing
        val legacyPin = context?.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            ?.getString("profile_pin", null)
            ?.trim()
        if (!legacyPin.isNullOrEmpty()) {
            val hashed = if (PasswordHasher.isPlainText(legacyPin)) PasswordHasher.hash(legacyPin) else legacyPin
            antiUninstallPrefs.edit().putString(KEY_ANTI_UNINSTALL_PASSWORD, hashed).apply()
            return hashed
        }
        return existing
    }

    /**
     * Remaining milliseconds for the active brute-force lockout, or 0 if not locked out.
     */
    fun getLockoutRemainingMillis(): Long {
        val lockoutUntil = securityPrefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) lockoutUntil - now else 0L
    }

    /**
     * Current count of failed attempts within the active window.
     */
    fun getFailedAttempts(): Int {
        return securityPrefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Generates a new salted PBKDF2 hash pair (hash, salt) for a custom rule PIN.
     */
    fun createRulePin(pin: String): Pair<String, String> {
        return PasswordHasher.hashWithSalt(pin)
    }

    /**
     * Applies a custom Rule PIN to a [ScheduleRule].
     */
    fun applyRulePin(rule: ScheduleRule, pin: String): ScheduleRule {
        val (hash, salt) = createRulePin(pin)
        return rule.copy(
            authType = AuthType.RULE_PIN,
            rulePasswordHash = hash,
            rulePasswordSalt = salt
        )
    }

    /**
     * Locks a [ScheduleRule] with the Global Master PIN.
     */
    fun applyGlobalPin(rule: ScheduleRule): ScheduleRule {
        return rule.copy(
            authType = AuthType.GLOBAL_PIN,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    /**
     * Removes all PIN locks from a [ScheduleRule], making it freely editable/deletable.
     */
    fun removeRulePin(rule: ScheduleRule): ScheduleRule {
        return rule.copy(
            authType = AuthType.NONE,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    /**
     * Applies a custom Rule PIN to an [AppBlockScheduleRule].
     */
    fun applyRulePin(rule: AppBlockScheduleRule, pin: String): AppBlockScheduleRule {
        val (hash, salt) = createRulePin(pin)
        return rule.copy(
            authType = AuthType.RULE_PIN,
            rulePasswordHash = hash,
            rulePasswordSalt = salt
        )
    }

    /**
     * Locks an [AppBlockScheduleRule] with Global Master PIN.
     */
    fun applyGlobalPin(rule: AppBlockScheduleRule): AppBlockScheduleRule {
        return rule.copy(
            authType = AuthType.GLOBAL_PIN,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    /**
     * Removes PIN lock from an [AppBlockScheduleRule].
     */
    fun removeRulePin(rule: AppBlockScheduleRule): AppBlockScheduleRule {
        return rule.copy(
            authType = AuthType.NONE,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    /**
     * Applies a custom Rule PIN to an [AppLaunchLimitRule].
     */
    fun applyRulePin(rule: AppLaunchLimitRule, pin: String): AppLaunchLimitRule {
        val (hash, salt) = createRulePin(pin)
        return rule.copy(
            authType = AuthType.RULE_PIN,
            rulePasswordHash = hash,
            rulePasswordSalt = salt
        )
    }

    /**
     * Locks an [AppLaunchLimitRule] with Global Master PIN.
     */
    fun applyGlobalPin(rule: AppLaunchLimitRule): AppLaunchLimitRule {
        return rule.copy(
            authType = AuthType.GLOBAL_PIN,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    /**
     * Removes PIN lock from an [AppLaunchLimitRule].
     */
    fun removeRulePin(rule: AppLaunchLimitRule): AppLaunchLimitRule {
        return rule.copy(
            authType = AuthType.NONE,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )
    }

    private fun handleFailedAttempt(): AuthResult {
        val currentFailed = securityPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        return if (currentFailed >= MAX_FAILED_ATTEMPTS) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            securityPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                .apply()
            AuthResult.InvalidPin(
                attemptsRemaining = 0,
                isLockedOut = true,
                lockoutRemainingMillis = LOCKOUT_DURATION_MS
            )
        } else {
            securityPrefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, currentFailed)
                .apply()
            val remaining = (MAX_FAILED_ATTEMPTS - currentFailed).coerceAtLeast(0)
            AuthResult.InvalidPin(
                attemptsRemaining = remaining,
                isLockedOut = false
            )
        }
    }

    private fun clearLockoutState() {
        securityPrefs.edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }
}
