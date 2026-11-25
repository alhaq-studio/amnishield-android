package com.alhaq.amnishield

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.AuthTarget
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.security.ShieldAuthTarget
import com.alhaq.amnishield.utils.PasswordHasher
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ProtectionMechanismsVerificationTest {

    private class InMemPrefs : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun createTestEnv(): Pair<Context, Map<String, InMemPrefs>> {
        val prefsMap = mutableMapOf<String, InMemPrefs>()
        val ctx = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.deenshield"
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemPrefs() }
            }
            override fun sendBroadcast(intent: android.content.Intent?) {}
        }
        return ctx to prefsMap
    }

    @Test
    fun `unified Global Anti-Bypass PIN syncs to Anti-Uninstall and verifies PBKDF2 with lockout`() {
        val (ctx, prefsMap) = createTestEnv()
        val loader = SavedPreferencesLoader(ctx)
        val auth = AuthResolver(
            context = ctx,
            injectedSecurityPrefs = prefsMap["security_auth_locks"] ?: InMemPrefs(),
            injectedAntiUninstallPrefs = prefsMap["anti_uninstall"] ?: InMemPrefs()
        )

        loader.saveGlobalAntiBypassPin("4829", linkAntiUninstall = true)

        assertTrue(loader.isPinSecurityEnabled())
        assertTrue(loader.isBypassPinLockEnabled())
        assertEquals("4829", loader.getPinCode())
        assertTrue(auth.hasGlobalPin())

        val antiPrefs = ctx.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        assertTrue(antiPrefs.getBoolean("is_anti_uninstall_on", false))
        assertTrue(antiPrefs.getBoolean("is_configuring_blocked", false))
        assertEquals(Constants.ANTI_UNINSTALL_PASSWORD_MODE, antiPrefs.getInt("mode", -1))

        val target = auth.resolveShieldChallenge(ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN))
        assertTrue(target is ShieldAuthTarget.RequireGlobalPin)
        assertEquals(AuthResult.Success, auth.verifyShieldPin(target, "4829"))

        repeat(AuthResolver.MAX_FAILED_ATTEMPTS - 1) {
            val res = auth.verifyShieldPin(target, "0000")
            assertTrue(res is AuthResult.InvalidPin && !res.isLockedOut)
        }
        val lockedOutRes = auth.verifyShieldPin(target, "0000")
        assertTrue(lockedOutRes is AuthResult.InvalidPin && lockedOutRes.isLockedOut)
        assertTrue(auth.resolveShieldChallenge(ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN)) is ShieldAuthTarget.LockedOut)
    }

    @Test
    fun `AuthResolver seamlessly migrates legacy profile_pin when anti_uninstall password is empty`() {
        val (ctx, prefsMap) = createTestEnv()
        ctx.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putString("profile_pin", "7755").apply()

        val auth = AuthResolver(
            context = ctx,
            injectedSecurityPrefs = prefsMap["security_auth_locks"] ?: InMemPrefs(),
            injectedAntiUninstallPrefs = prefsMap["anti_uninstall"] ?: InMemPrefs()
        )

        assertTrue(auth.hasGlobalPin())
        val target = auth.resolveShieldChallenge(ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN))
        assertTrue(target is ShieldAuthTarget.RequireGlobalPin)
        assertEquals(AuthResult.Success, auth.verifyShieldPin(target, "7755"))
        assertTrue(!PasswordHasher.isPlainText(auth.getGlobalPinHash()!!))
    }

    @Test
    fun `all Always-On Shield lock modes resolve and verify properly`() {
        val (ctx, prefsMap) = createTestEnv()
        val auth = AuthResolver(
            context = ctx,
            injectedSecurityPrefs = prefsMap["security_auth_locks"] ?: InMemPrefs(),
            injectedAntiUninstallPrefs = prefsMap["anti_uninstall"] ?: InMemPrefs()
        )

        assertEquals(
            ShieldAuthTarget.PassThrough,
            auth.resolveShieldChallenge(ShieldLockConfig(mode = ShieldLockMode.NONE))
        )

        val delayTarget = auth.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 45)
        )
        assertEquals(ShieldAuthTarget.RequireDelay(45), delayTarget)

        val (customHash, customSalt) = auth.createRulePin("9182")
        val customTarget = auth.resolveShieldChallenge(
            ShieldLockConfig(
                mode = ShieldLockMode.CUSTOM_PIN,
                customPinHash = customHash,
                customPinSalt = customSalt
            )
        )
        assertTrue(customTarget is ShieldAuthTarget.RequireCustomPin)
        assertEquals(AuthResult.Success, auth.verifyShieldPin(customTarget, "9182"))
        assertTrue(auth.verifyShieldPin(customTarget, "1111") is AuthResult.InvalidPin)

        val qrTarget = auth.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.QR_CODE, registeredQrValue = "AMNI-QR-KEY-99")
        )
        assertEquals(ShieldAuthTarget.RequireQr("AMNI-QR-KEY-99"), qrTarget)
    }

    @Test
    fun `RuleMutationClassifier allows strengthening edits freely and flags weakening edits`() {
        val orig = UnifiedBlockRule(
            id = "rule_focus",
            name = "Deep Work",
            status = BlockRuleStatus.ACTIVE,
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY),
            blockedDomains = listOf("reddit.com"),
            blockedKeywords = listOf("spoiler"),
            weeklySchedules = listOf(
                WeeklyScheduleTrigger(daysOfWeek = setOf(Calendar.MONDAY), startTimeMinutes = 9 * 60, endTimeMinutes = 12 * 60)
            ),
            screenTimeLimits = listOf(ScreenTimeLimitTrigger(period = LimitPeriod.DAILY, maxMinutes = 30)),
            openLimits = listOf(OpenLimitTrigger(period = LimitPeriod.DAILY, maxLaunches = 5)),
            pauseOption = PauseOptionConfig(
                type = PauseOptionType.DELAY,
                delaySeconds = 30,
                maxPauseDurationMinutes = 60,
                protectOutsideScheduleWindow = false
            )
        )

        // Adding another app, domain, keyword, schedule window, tighter limit, or stronger friction -> NOT weakening
        val strengthened = orig.copy(
            appTargets = orig.appTargets + ("com.zhiliaoapp.musically" to AppInterceptionLevel.BLOCK_ENTIRE_APP),
            blockedDomains = orig.blockedDomains + "tiktok.com",
            blockedKeywords = orig.blockedKeywords + "reels",
            weeklySchedules = orig.weeklySchedules + WeeklyScheduleTrigger(
                daysOfWeek = setOf(Calendar.TUESDAY),
                startTimeMinutes = 14 * 60,
                endTimeMinutes = 16 * 60
            ),
            screenTimeLimits = listOf(ScreenTimeLimitTrigger(period = LimitPeriod.DAILY, maxMinutes = 20)),
            openLimits = listOf(OpenLimitTrigger(period = LimitPeriod.DAILY, maxLaunches = 3)),
            pauseOption = orig.pauseOption.copy(
                type = PauseOptionType.STRICT_NO_PAUSING,
                protectOutsideScheduleWindow = true
            )
        )
        assertFalse(RuleMutationClassifier.isWeakeningEdit(orig, strengthened))

        // Removing domain -> weakening
        assertTrue(RuleMutationClassifier.isWeakeningEdit(orig, orig.copy(blockedDomains = emptyList())))
        // Removing schedule -> weakening
        assertTrue(RuleMutationClassifier.isWeakeningEdit(orig, orig.copy(weeklySchedules = emptyList())))
        // Loosening delay seconds -> weakening
        assertTrue(RuleMutationClassifier.isWeakeningEdit(orig, orig.copy(pauseOption = orig.pauseOption.copy(delaySeconds = 10))))
        // Turning off protectOutsideScheduleWindow -> weakening
        val strictOutside = orig.copy(pauseOption = orig.pauseOption.copy(protectOutsideScheduleWindow = true))
        assertTrue(
            RuleMutationClassifier.isWeakeningEdit(
                strictOutside,
                strictOutside.copy(pauseOption = strictOutside.pauseOption.copy(protectOutsideScheduleWindow = false))
            )
        )
    }

    @Test
    fun `App Lock mode persists independently from Global Anti-Bypass PIN and defaults to DEVICE_LOCK`() {
        val (ctx, _) = createTestEnv()
        val loader = SavedPreferencesLoader(ctx)

        assertEquals(SavedPreferencesLoader.APP_LOCK_MODE_DEVICE, loader.getAppLockMode())

        loader.setAppLockEnabled(true)
        assertTrue(loader.isAppLockEnabled())
        assertFalse(loader.isPinSecurityEnabled())

        loader.setAppLockCustomPin("2580")
        loader.setAppLockMode(SavedPreferencesLoader.APP_LOCK_MODE_APP_PIN)
        assertEquals(SavedPreferencesLoader.APP_LOCK_MODE_APP_PIN, loader.getAppLockMode())
        assertEquals("2580", loader.getAppLockCustomPin())

        loader.setAppLockMode(SavedPreferencesLoader.APP_LOCK_MODE_GLOBAL_PIN)
        assertEquals(SavedPreferencesLoader.APP_LOCK_MODE_GLOBAL_PIN, loader.getAppLockMode())
    }

    @Test
    fun `all Custom Block pause challenge types verify deterministically`() {
        // Delay, Type-over, QR, NFC, Strict
        val targetPassage = "Stay consistent and focused."
        assertTrue(OverrideChallengeValidator.evaluateTypeOverText(targetPassage, targetPassage).isComplete)
        assertFalse(OverrideChallengeValidator.evaluateTypeOverText("Stay confused", targetPassage).isComplete)
        assertTrue(OverrideChallengeValidator.isPasteAttempt("", targetPassage))

        val strictRule = UnifiedBlockRule(
            name = "Strict Shield",
            pauseOption = PauseOptionConfig(
                type = PauseOptionType.STRICT_NO_PAUSING,
                protectOutsideScheduleWindow = true
            )
        ).sanitize()
        assertTrue(strictRule.pauseOption.protectOutsideScheduleWindow)
        assertEquals(4, RuleMutationClassifier.pauseOptionStrengthRank(strictRule.pauseOption))

        val nfcRule = UnifiedBlockRule(
            name = "NFC Guard",
            pauseOption = PauseOptionConfig(
                type = PauseOptionType.SCAN_NFC_TAG,
                registeredTagIdHex = "04:aa:bb:11"
            )
        ).sanitize()
        assertEquals("04AABB11", nfcRule.pauseOption.registeredTagIdHex)

        val (ctx, prefsMap) = createTestEnv()
        val ruleAuth = AuthResolver(
            context = ctx,
            injectedSecurityPrefs = prefsMap["security_auth_locks"] ?: InMemPrefs(),
            injectedAntiUninstallPrefs = prefsMap["anti_uninstall"] ?: InMemPrefs()
        )
        val (pinHash, pinSalt) = ruleAuth.createRulePin("6420")
        val ruleWithPin = UnifiedBlockRule(
            name = "PIN Rule",
            authType = AuthType.RULE_PIN,
            rulePasswordHash = pinHash,
            rulePasswordSalt = pinSalt
        )
        val resolvedRulePin = ruleAuth.resolveChallenge(ruleWithPin)
        assertTrue(resolvedRulePin is AuthTarget.RequireRulePin)
        assertEquals(AuthResult.Success, ruleAuth.verifyChallenge(resolvedRulePin, "6420"))
    }
}
