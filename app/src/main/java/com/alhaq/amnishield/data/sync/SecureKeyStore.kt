package com.alhaq.amnishield.data.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SecureKeyStore(context: Context) {
    private val prefs = createEncryptedPreferences(context)

    private fun createEncryptedPreferences(context: Context): android.content.SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "amnishield_sync_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Throwable) {
            android.util.Log.e("SecureKeyStore", "Failed to create EncryptedSharedPreferences, attempting self-healing cleanup", e)
            try {
                // Delete corrupted encrypted preferences file
                val sharedPrefsFile = java.io.File(context.filesDir.parent, "shared_prefs/amnishield_sync_secrets.xml")
                if (sharedPrefsFile.exists()) {
                    sharedPrefsFile.delete()
                }
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "amnishield_sync_secrets",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e2: Throwable) {
                android.util.Log.e("SecureKeyStore", "EncryptedSharedPreferences self-healing failed, falling back to standard SharedPreferences", e2)
                context.getSharedPreferences("amnishield_sync_secrets_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    var dekB64: String?
        get() = prefs.getString("dek", null)
        set(v) = prefs.edit().putString("dek", v).apply()

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(v) = prefs.edit().putString("access_token", v).apply()

    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(v) = prefs.edit().putString("refresh_token", v).apply()

    var cursor: String
        get() = prefs.getString("cursor", "1970-01-01T00:00:00Z")!!
        set(v) = prefs.edit().putString("cursor", v).apply()

    var fcmToken: String?
        get() = prefs.getString("fcm_token", null)
        set(v) = prefs.edit().putString("fcm_token", v).apply()

    val deviceId: String
        get() = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }

    var deviceName: String
        get() = prefs.getString("device_name", "Android")!!
        set(v) = prefs.edit().putString("device_name", v).apply()

    var syncUsageStats: Boolean
        get() = prefs.getBoolean("sync_usage_stats", true)
        set(v) = prefs.edit().putBoolean("sync_usage_stats", v).apply()

    var syncReducerConfigs: Boolean
        get() = prefs.getBoolean("sync_reducer_configs", true)
        set(v) = prefs.edit().putBoolean("sync_reducer_configs", v).apply()

    var usageDeviceIds: Set<String>
        get() = prefs.getStringSet("usage_device_ids", emptySet())?.toSet().orEmpty()
        set(v) = prefs.edit().putStringSet("usage_device_ids", v).apply()

    fun clear() {
        prefs.edit().remove("dek").remove("access_token").remove("refresh_token").remove("cursor").remove("fcm_token").apply()
    }
}
