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

    suspend fun syncNow(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("paired_device_id", null)
        val isPaired = prefs.getBoolean("is_paired_with_console", false)

        if (!isPaired || deviceId.isNullOrBlank()) {
            Log.d(TAG, "Device not paired with Web Console. Skipping remote policy pull.")
            return@withContext false
        }

        try {
            // 1. Fetch device record with latest policy_payload
            val req = Request.Builder()
                .url("$BASE_URL/rest/v1/devices?id=eq.$deviceId&select=id,device_name,is_managed,policy_payload")
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "Failed to fetch device policy: HTTP ${resp.code}")
                return@withContext false
            }

            val body = resp.body?.string() ?: return@withContext false
            val array = gson.fromJson(body, JsonArray::class.java)
            if (array == null || array.size() == 0) return@withContext false

            val devObj = array[0].asJsonObject
            val payload = if (devObj.has("policy_payload") && !devObj.get("policy_payload").isJsonNull) {
                devObj.getAsJsonObject("policy_payload")
            } else {
                null
            }

            if (payload != null) {
                applyPolicyPayload(context, payload)
            }

            // 2. Send Heartbeat and Telemetry
            sendHeartbeat(context, deviceId)

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Policy sync exception", e)
            return@withContext false
        }
    }

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
