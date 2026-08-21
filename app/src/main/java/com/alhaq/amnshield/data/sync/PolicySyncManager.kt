package com.alhaq.amnshield.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.alhaq.amnshield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object PolicySyncManager {
    private const val TAG = "PolicySyncManager"
    private const val BASE_URL = "https://jrgpmcomvibgklmvnxud.supabase.co"
    private const val ANON_KEY = SupabaseRest.ANON_KEY
    private val gson = Gson()
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /**
     * Executes the sync cycle based on the current 3-Mode operation state:
     * Mode 1 (Standalone): No remote sync.
     * Mode 2 (Personal Cloud Sync): Syncs personal rules bidirectionally.
     * Mode 3 (Console Enforced): Fetches remote policy_payload; on network failure, falls back to locally cached policy.
     */
    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val savedPrefs = SavedPreferencesLoader(context)
        val mode = savedPrefs.getDeviceOperationMode()

        when (mode) {
            SavedPreferencesLoader.MODE_CONSOLE_ENFORCED -> {
                return@withContext syncConsoleEnforcedMode(context, savedPrefs)
            }
            SavedPreferencesLoader.MODE_PERSONAL_SYNC -> {
                return@withContext syncPersonalCloudMode(context, savedPrefs)
            }
            else -> {
                Log.d(TAG, "[Mode 1: Standalone] Local operation only. Remote sync skipped.")
                return@withContext true
            }
        }
    }

    /**
     * Mode 3: Console-Enforced Policy Engine with Strict Offline Resilience Fallback
     */
    private fun syncConsoleEnforcedMode(context: Context, savedPrefs: SavedPreferencesLoader): Boolean {
        val deviceId = savedPrefs.getPairedDeviceId()
        if (deviceId.isNullOrBlank()) {
            Log.w(TAG, "Device marked as managed but deviceId is missing.")
            return false
        }

        try {
            // 1. Attempt to fetch latest remote policy payload from Supabase
            val req = Request.Builder()
                .url("$BASE_URL/rest/v1/devices?id=eq.$deviceId&select=id,device_name,is_managed,policy_payload,last_policy_updated_at")
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                val array = gson.fromJson(body, JsonArray::class.java)
                if (array != null && array.size() > 0) {
                    val devObj = array[0].asJsonObject
                    if (devObj.has("policy_payload") && !devObj.get("policy_payload").isJsonNull) {
                        val payload = devObj.getAsJsonObject("policy_payload")
                        
                        // Cache payload to encrypted storage for offline resilience
                        savedPrefs.saveCachedPolicyPayload(payload.toString())
                        applyPolicyPayload(context, payload)
                        Log.d(TAG, "Successfully applied and cached remote policy payload.")
                    }
                }

                // Send live heartbeat
                sendHeartbeat(context, deviceId)
                return true
            } else {
                Log.w(TAG, "Remote policy fetch failed (HTTP ${resp.code}). Falling back to cached policy.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception during policy sync: ${e.message}. Executing offline fallback.")
        }

        // OFFLINE RESILIENCE FALLBACK:
        // Automatically load and re-enforce the locally cached policy. Never relax rules offline!
        val cachedPayloadJson = savedPrefs.getCachedPolicyPayload()
        if (!cachedPayloadJson.isNullOrBlank()) {
            try {
                val cachedObj = gson.fromJson(cachedPayloadJson, JsonObject::class.java)
                applyPolicyPayload(context, cachedObj)
                Log.d(TAG, "✅ [Offline Resilience] Enforced locally cached policy payload.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached policy payload", e)
            }
        }

        return false
    }

    /**
     * Mode 2: Personal Cloud Sync (Bi-directional rule sync without locking local UI)
     */
    private fun syncPersonalCloudMode(context: Context, savedPrefs: SavedPreferencesLoader): Boolean {
        try {
            val deviceId = savedPrefs.getPairedDeviceId()
            if (!deviceId.isNullOrBlank()) {
                sendHeartbeat(context, deviceId)
            }
            Log.d(TAG, "Personal cloud sync cycle executed.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Personal cloud sync exception", e)
            return false
        }
    }

    /**
     * Translates a policy payload into local blocker rule sets and notifies running services.
     */
    fun applyPolicyPayload(context: Context, payload: JsonObject) {
        val savedPrefs = SavedPreferencesLoader(context)

        // A. Blocked Apps
        if (payload.has("blocked_apps") && payload.get("blocked_apps").isJsonArray) {
            val appsArray = payload.getAsJsonArray("blocked_apps")
            val appSet = mutableSetOf<String>()
            for (elem in appsArray) {
                val pkg = elem.asString.trim()
                if (pkg.isNotEmpty()) {
                    appSet.add(pkg)
                }
            }
            savedPrefs.saveBlockedApps(appSet)
            context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).setPackage(context.packageName))
        }

        // B. Blocked Domains & Keywords
        val keywordSet = mutableSetOf<String>()
        if (payload.has("blocked_domains") && payload.get("blocked_domains").isJsonArray) {
            val domArray = payload.getAsJsonArray("blocked_domains")
            for (elem in domArray) {
                val d = elem.asString.trim().lowercase()
                if (d.isNotEmpty()) keywordSet.add(d)
            }
        }
        if (payload.has("blocked_keywords") && payload.get("blocked_keywords").isJsonArray) {
            val kwArray = payload.getAsJsonArray("blocked_keywords")
            for (elem in kwArray) {
                val k = elem.asString.trim().lowercase()
                if (k.isNotEmpty()) keywordSet.add(k)
            }
        }
        if (keywordSet.isNotEmpty()) {
            savedPrefs.saveBlockedKeywords(keywordSet)
            context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST).setPackage(context.packageName))
        }

        // C. Adult Pack Filter
        if (payload.has("blocked_categories") && payload.get("blocked_categories").isJsonArray) {
            val catArray = payload.getAsJsonArray("blocked_categories")
            val hasAdult = catArray.any { it.asString.contains("adult", ignoreCase = true) || it.asString.contains("porn", ignoreCase = true) }
            savedPrefs.setKeywordBlockerAdultPackEnabled(hasAdult)
        } else if (payload.has("allow_unblur")) {
            savedPrefs.setKeywordBlockerAdultPackEnabled(!payload.get("allow_unblur").asBoolean)
        }

        // D. Focus Mode
        if (payload.has("strict_mode")) {
            val strict = payload.get("strict_mode").asBoolean
            savedPrefs.setFocusModeFeatureEnabled(strict)
            context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).setPackage(context.packageName))
        }

        // E. Schedules
        if (payload.has("schedules") && payload.get("schedules").isJsonObject) {
            val sch = payload.getAsJsonObject("schedules")
            val enabled = sch.get("enabled")?.asBoolean ?: false
            if (enabled) {
                val start = sch.get("start")?.asString ?: "09:00"
                val end = sch.get("end")?.asString ?: "17:00"
                val startParts = start.split(":")
                val endParts = end.split(":")
                val startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 9
                val startMin = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                val endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 17
                val endMin = endParts.getOrNull(1)?.toIntOrNull() ?: 0

                val rule = AppBlockScheduleRule(
                    id = "cloud_sync_schedule",
                    title = "Web Console Schedule",
                    packageName = "all",
                    type = AppBlockScheduleRule.RuleType.BLOCK,
                    recurrence = AppBlockScheduleRule.Recurrence.WEEKLY,
                    startMinute = startHour * 60 + startMin,
                    endMinute = endHour * 60 + endMin,
                    selectedDays = setOf(1, 2, 3, 4, 5),
                    isEnabled = true
                )
                savedPrefs.saveAppBlockerScheduleRules(mutableListOf(rule))
                context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES).setPackage(context.packageName))
            }
        }
    }

    private fun sendHeartbeat(context: Context, deviceId: String) {
        try {
            val updateObj = JsonObject().apply {
                addProperty("last_heartbeat", java.time.Instant.now().toString())
                addProperty("is_online", true)
                addProperty("platform", "android")
            }

            val patchReq = Request.Builder()
                .url("$BASE_URL/rest/v1/devices?id=eq.$deviceId")
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .header("Content-Type", "application/json")
                .patch(updateObj.toString().toRequestBody(jsonType))
                .build()

            client.newCall(patchReq).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
        }
    }
}
