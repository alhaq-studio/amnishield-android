package com.alhaq.amnishield.premium

import android.content.Context
import com.alhaq.amnishield.BuildConfig
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class PremiumManager internal constructor(
    private val preferencesLoader: SavedPreferencesLoader
) {
    constructor(context: Context) : this(SavedPreferencesLoader(context.applicationContext))

    enum class UserType {
        FREE,
        COMPASSIONATE,
        PREMIUM
    }

    /**
     * Check if user has premium or special access
     */
    fun isPremium(): Boolean {
        val hasLocalPremium = if (BuildConfig.IS_PLAYSTORE) {
            preferencesLoader.isPremiumUser()
        } else {
            isLicenseKeyValid()
        }
        return hasLocalPremium || isCompassionateAccessActive()
    }

    fun isCompassionateAccessActive(): Boolean {
        val status = preferencesLoader.getCompassionateAccessStatus()
        if (status == "cancelled" || status == "flagged_email") {
            return false
        }

        // Verify cryptographic offline ECDSA license key if present
        val communityKey = preferencesLoader.getCompassionateAccessLicenseKey()
        if (!communityKey.isNullOrBlank()) {
            val payload = LicenseValidator.verifyLicense(communityKey)
            if (payload != null && payload.expires > System.currentTimeMillis()) {
                return true
            }
        }

        // Anti-clock manipulation guard & monotonic verification
        return preferencesLoader.updateCompassionateUptimeAndVerify()
    }

    /**
     * Check if the offline license key is valid
     */
    fun isLicenseKeyValid(): Boolean {
        val key = preferencesLoader.getLicenseKey() ?: return false
        val email = preferencesLoader.getLicenseEmail() ?: return false
        val payload = LicenseValidator.verifyLicense(key) ?: return false
        return payload.email.equals(email, ignoreCase = true) && payload.expires > System.currentTimeMillis()
    }

    /**
     * Get the current user type
     */
    fun getUserType(): UserType {
        val isPremiumActive = preferencesLoader.isPremiumUser() || isLicenseKeyValid()
        return when {
            isCompassionateAccessActive() -> UserType.COMPASSIONATE
            isPremiumActive -> UserType.PREMIUM
            else -> UserType.FREE
        }
    }

    /**
     * Get user type as display string
     */
    fun getUserTypeLabel(): String {
        return when (getUserType()) {
            UserType.FREE -> "Free"
            UserType.COMPASSIONATE -> {
                val status = preferencesLoader.getCompassionateAccessStatus()
                if (status == "verified_active") {
                    "Al-Haq Community Pass"
                } else {
                    "Community Access (Review)"
                }
            }
            UserType.PREMIUM -> "Premium"
        }
    }

    /**
     * Update regular premium status (purchased)
     */
    fun updatePremiumStatus(active: Boolean) {
        preferencesLoader.setPremiumUser(active)
    }

    /**
     * Redeem offline license key
     */
    fun redeemLicenseKey(licenseString: String): Boolean {
        val payload = LicenseValidator.verifyLicense(licenseString) ?: return false
        preferencesLoader.saveLicenseKey(payload.email, licenseString)
        preferencesLoader.setPremiumUser(true)
        if (!payload.user_id.isNullOrEmpty()) {
            preferencesLoader.saveUserProfile(payload.user_id, payload.email)
        }
        return true
    }

    /**
     * Remove offline license key
     */
    fun removeLicenseKey() {
        preferencesLoader.clearLicenseKey()
    }

    fun shouldShowReminder(): Boolean {
        if (isPremium()) return false
        val now = System.currentTimeMillis()
        val last = preferencesLoader.getLastPremiumReminder()
        return now - last >= REMINDER_INTERVAL_MS
    }

    fun markReminderShown() {
        preferencesLoader.setLastPremiumReminder(System.currentTimeMillis())
    }

    fun resetReminderWindow() {
        preferencesLoader.setLastPremiumReminder(0L)
    }

    companion object {
        private const val REMINDER_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000 // ~twice per week

        @Volatile
        private var instance: PremiumManager? = null

        fun getInstance(context: Context): PremiumManager {
            return instance ?: synchronized(this) {
                instance ?: PremiumManager(context).also { instance = it }
            }
        }
    }
}
