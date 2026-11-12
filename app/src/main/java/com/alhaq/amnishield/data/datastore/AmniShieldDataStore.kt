package com.alhaq.amnishield.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

private val Context.amniShieldPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "amnishield_core_datastore"
)

/**
 * Reactive Jetpack Preferences DataStore layer for core application flags, onboarding consent,
 * active theme, and PIN security state (Milestone 0.3.5).
 *
 * Automatically migrates legacy values from `AppPreferences`, `app_preferences`, and `theme_prefs`
 * on first access while keeping `SavedPreferencesLoader` synchronized for Main Binder thread safety.
 */
class AmniShieldDataStore private constructor(private val context: Context) {

    data class CorePreferencesSnapshot(
        val isFirstLaunchComplete: Boolean = false,
        val accessibilityDisclosureAccepted: Boolean = false,
        val accessibilityDisclosureTimestamp: Long = 0L,
        val themeMode: String = "SYSTEM_DEFAULT",
        val isPinProtectionEnabled: Boolean = false,
        val isAppLockEnabled: Boolean = false,
        val isBypassPinLockEnabled: Boolean = false,
        val pinResetCooldownMinutes: Int = 5,
        val emergencyAccessCooldownMinutes: Int = 5
    )

    val snapshotFlow: Flow<CorePreferencesSnapshot> = runCatching {
        context.amniShieldPreferencesDataStore.data.map { prefs ->
            CorePreferencesSnapshot(
                isFirstLaunchComplete = prefs[KEY_FIRST_LAUNCH_COMPLETE] ?: false,
                accessibilityDisclosureAccepted = prefs[KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false,
                accessibilityDisclosureTimestamp = prefs[KEY_ACCESSIBILITY_DISCLOSURE_TIMESTAMP] ?: 0L,
                themeMode = prefs[KEY_THEME_MODE] ?: "SYSTEM_DEFAULT",
                isPinProtectionEnabled = prefs[KEY_PIN_PROTECTION_ENABLED] ?: false,
                isAppLockEnabled = prefs[KEY_APP_LOCK_ENABLED] ?: false,
                isBypassPinLockEnabled = prefs[KEY_BYPASS_PIN_LOCK_ENABLED] ?: false,
                pinResetCooldownMinutes = prefs[KEY_PIN_RESET_COOLDOWN_MINS] ?: 5,
                emergencyAccessCooldownMinutes = prefs[KEY_EMERGENCY_ACCESS_COOLDOWN_MINS] ?: 5
            )
        }
    }.getOrElse {
        kotlinx.coroutines.flow.flowOf(readLegacySnapshot())
    }

    suspend fun migrateFromLegacyIfNeeded() {
        runCatching {
            val legacy = readLegacySnapshot()
            context.amniShieldPreferencesDataStore.edit { prefs ->
                if (prefs[KEY_MIGRATED_V1] != true) {
                    prefs[KEY_FIRST_LAUNCH_COMPLETE] = legacy.isFirstLaunchComplete
                    prefs[KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED] = legacy.accessibilityDisclosureAccepted
                    prefs[KEY_ACCESSIBILITY_DISCLOSURE_TIMESTAMP] = legacy.accessibilityDisclosureTimestamp
                    prefs[KEY_THEME_MODE] = legacy.themeMode
                    prefs[KEY_PIN_PROTECTION_ENABLED] = legacy.isPinProtectionEnabled
                    prefs[KEY_APP_LOCK_ENABLED] = legacy.isAppLockEnabled
                    prefs[KEY_BYPASS_PIN_LOCK_ENABLED] = legacy.isBypassPinLockEnabled
                    prefs[KEY_PIN_RESET_COOLDOWN_MINS] = legacy.pinResetCooldownMinutes
                    prefs[KEY_EMERGENCY_ACCESS_COOLDOWN_MINS] = legacy.emergencyAccessCooldownMinutes
                    prefs[KEY_MIGRATED_V1] = true
                }
            }
        }
    }

    suspend fun setFirstLaunchComplete(completed: Boolean) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("isFirstLaunchComplete", completed).apply()
        runCatching {
            context.amniShieldPreferencesDataStore.edit { prefs ->
                prefs[KEY_FIRST_LAUNCH_COMPLETE] = completed
            }
        }
    }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("accessibility_disclosure_accepted", accepted)
            .putLong("accessibility_disclosure_accepted_at", if (accepted) timestamp else 0L)
            .apply()
        runCatching {
            context.amniShieldPreferencesDataStore.edit { prefs ->
                prefs[KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED] = accepted
                prefs[KEY_ACCESSIBILITY_DISCLOSURE_TIMESTAMP] = if (accepted) timestamp else 0L
            }
        }
    }

    suspend fun setThemeMode(themeName: String) {
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_theme", themeName).apply()
        runCatching {
            context.amniShieldPreferencesDataStore.edit { prefs ->
                prefs[KEY_THEME_MODE] = themeName
            }
        }
    }

    fun readLegacySnapshot(): CorePreferencesSnapshot {
        val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val lowerPrefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

        return CorePreferencesSnapshot(
            isFirstLaunchComplete = appPrefs.getBoolean("isFirstLaunchComplete", false),
            accessibilityDisclosureAccepted = lowerPrefs.getBoolean("accessibility_disclosure_accepted", false),
            accessibilityDisclosureTimestamp = lowerPrefs.getLong("accessibility_disclosure_accepted_at", 0L),
            themeMode = themePrefs.getString("selected_theme", "SYSTEM_DEFAULT") ?: "SYSTEM_DEFAULT",
            isPinProtectionEnabled = appPrefs.getBoolean("pin_protection_enabled", false),
            isAppLockEnabled = appPrefs.getBoolean("pin_app_lock", false),
            isBypassPinLockEnabled = appPrefs.getBoolean("pin_bypass_lock", false),
            pinResetCooldownMinutes = appPrefs.getInt("pin_reset_cooldown_mins", 5).coerceAtLeast(2),
            emergencyAccessCooldownMinutes = appPrefs.getInt("emergency_access_cooldown_mins", 5).coerceAtLeast(2)
        )
    }

    companion object {
        val KEY_MIGRATED_V1 = booleanPreferencesKey("migrated_v1")
        val KEY_FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("is_first_launch_complete")
        val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val KEY_ACCESSIBILITY_DISCLOSURE_TIMESTAMP = longPreferencesKey("accessibility_disclosure_accepted_at")
        val KEY_THEME_MODE = stringPreferencesKey("selected_theme")
        val KEY_PIN_PROTECTION_ENABLED = booleanPreferencesKey("pin_protection_enabled")
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("pin_app_lock")
        val KEY_BYPASS_PIN_LOCK_ENABLED = booleanPreferencesKey("pin_bypass_lock")
        val KEY_PIN_RESET_COOLDOWN_MINS = intPreferencesKey("pin_reset_cooldown_mins")
        val KEY_EMERGENCY_ACCESS_COOLDOWN_MINS = intPreferencesKey("emergency_access_cooldown_mins")

        private val instances = ConcurrentHashMap<Context, AmniShieldDataStore>()

        fun getInstance(context: Context): AmniShieldDataStore {
            val key = context.applicationContext ?: context
            return instances.getOrPut(key) { AmniShieldDataStore(key) }
        }
    }
}
