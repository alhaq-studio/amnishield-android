package com.alhaq.amnishield.data.sync

import android.content.Context
import android.content.Intent
import android.util.Log
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Calendar
import java.util.Locale
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
    const val INTENT_ACTION_REFRESH_KEYWORD_BLOCKER =
        AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST

    @Volatile
    internal var broadcastListenerForTesting: ((String) -> Unit)? = null

    private val gson = Gson()
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    enum class ApplyMode {
        MERGE,
        FORCE_REPLACE
    }

    data class SyncSummary(
        val success: Boolean,
        val appsCount: Int = 0,
        val websitesCount: Int = 0,
        val keywordsCount: Int = 0,
        val schedulesCount: Int = 0,
        val launchLimitsCount: Int = 0,
        val message: String = "",
        val mode: ApplyMode = ApplyMode.MERGE,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun toSummaryText(): String {
            if (!success) return message.ifBlank { "Policy sync failed." }
            return message.ifBlank {
                "Synced $schedulesCount schedules, $appsCount apps, $websitesCount domains, $keywordsCount keywords, $launchLimitsCount launch limits (${mode.name})."
            }
        }
    }

    suspend fun forcePullAndApply(
        context: Context,
        mode: ApplyMode = ApplyMode.FORCE_REPLACE
    ): SyncSummary = syncNow(context, mode)

    suspend fun syncNow(
        context: Context,
        mode: ApplyMode = ApplyMode.MERGE
    ): SyncSummary = withContext(Dispatchers.IO) {
        val savedPrefs = SavedPreferencesLoader(context)
        val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val pairedDeviceId = appPrefs.getString("paired_device_id", null)?.trim()
        val pairedOwnerId = appPrefs.getString("paired_owner_id", null)?.trim()
        val isPaired = appPrefs.getBoolean("is_paired_with_console", false) ||
            appPrefs.getBoolean("is_console_managed", false)

        val rest = SupabaseRest()
        val session = resolveActiveSession(context, rest)
        val fallbackOwnerId = pairedOwnerId
            ?.takeIf { it.isNotEmpty() }
            ?: savedPrefs.getUserId()?.takeIf { it.isNotBlank() }
        val userEmail = appPrefs.getString("profile_email", null)?.takeIf { it.isNotBlank() }
            ?: savedPrefs.getUserEmail()?.takeIf { it.isNotBlank() }

        if (!isPaired && pairedDeviceId.isNullOrBlank() && session == null && fallbackOwnerId.isNullOrBlank() && userEmail.isNullOrBlank()) {
            Log.d(TAG, "Device not linked with Cloud Sync Hub or signed-in account. Skipping remote policy pull.")
            return@withContext SyncSummary(
                success = false,
                mode = mode,
                message = "Device is not paired with Console or signed in to Cloud Sync."
            )
        }

        try {
            var payload: JsonObject? = null

            // 1. Primary path: Paired Console Device ID
            if (!pairedDeviceId.isNullOrBlank()) {
                payload = runCatching {
                    rest.fetchDevicePolicy(pairedDeviceId, session?.accessToken)
                }.getOrNull()
                sendHeartbeat(context, pairedDeviceId, session?.accessToken)
            }

            // 2. Secondary path: Authenticated Supabase user session (`devices` + `sync_records`)
            if (payload == null && session != null) {
                val keysDeviceId = runCatching { SecureKeyStore(context).deviceId }.getOrNull()
                payload = runCatching {
                    rest.fetchUserCloudPolicy(session, pairedDeviceId ?: keysDeviceId)
                }.getOrNull()
            }

            // 3. Tertiary path: Owner ID or profile email lookup
            if (payload == null) {
                val resolvedOwnerId = fallbackOwnerId ?: if (!userEmail.isNullOrBlank()) {
                    runCatching { rest.fetchProfileByEmail(userEmail, session?.accessToken)?.id }.getOrNull()
                } else null
                if (!resolvedOwnerId.isNullOrBlank()) {
                    payload = runCatching {
                        rest.fetchPolicyByOwner(resolvedOwnerId, session?.accessToken)
                    }.getOrNull()
                }
            }

            // 4. Offline resilience fallback: use cached policy payload if network returned nothing
            if (payload == null) {
                val cachedJson = savedPrefs.getCachedPolicyPayload()
                if (!cachedJson.isNullOrBlank()) {
                    payload = runCatching { gson.fromJson(cachedJson, JsonObject::class.java) }.getOrNull()
                }
            }

            if (payload != null) {
                val summary = applyPolicyPayload(context, payload, mode)
                return@withContext summary
            }

            return@withContext SyncSummary(
                success = false,
                mode = mode,
                message = "No remote policy payload found for this account or device."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Policy sync exception", e)
            return@withContext SyncSummary(
                success = false,
                mode = mode,
                message = e.message ?: "Policy synchronization failed."
            )
        }
    }

    fun applyPolicyPayload(
        context: Context,
        payload: JsonObject,
        mode: ApplyMode = ApplyMode.MERGE
    ): SyncSummary {
        val savedPrefs = SavedPreferencesLoader(context)

        // Save pre-sync recovery snapshot before overwriting local rules in FORCE_REPLACE mode
        if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.savePreSyncRuleSnapshot()
        }

        // Always cache the raw policy payload and update last_policy_sync_timestamp
        savedPrefs.saveCachedPolicyPayload(payload.toString())

        val hasNamedListsField = payload.has("named_block_lists") && payload.get("named_block_lists").isJsonObject
        val hasBlockedAppsField = payload.has("blocked_apps") && payload.get("blocked_apps").isJsonArray
        val hasDomainsField = payload.has("blocked_domains") && payload.get("blocked_domains").isJsonArray
        val hasKeywordsField = payload.has("blocked_keywords") && payload.get("blocked_keywords").isJsonArray
        val hasCategoriesField = payload.has("blocked_categories") && payload.get("blocked_categories").isJsonArray
        val hasReelBlockerField = payload.has("reel_blocker") && payload.get("reel_blocker").isJsonObject
        val hasSchedulesField = payload.has("schedules") && !payload.get("schedules").isJsonNull
        val hasLaunchLimitsField = payload.has("launch_limits") && payload.get("launch_limits").isJsonArray

        // A. Parse Blocked Apps & Named Block Lists
        val remoteNamedLists = mutableMapOf<String, MutableSet<String>>()
        if (hasNamedListsField) {
            val listsObj = payload.getAsJsonObject("named_block_lists")
            for ((listName, listElem) in listsObj.entrySet()) {
                if (listElem != null && listElem.isJsonArray) {
                    val pkgs = listElem.asJsonArray
                        .mapNotNull { it?.takeIf { !it.isJsonNull }?.asString?.trim() }
                        .filter { it.isNotEmpty() }
                        .toMutableSet()
                    if (listName.isNotBlank()) {
                        remoteNamedLists[listName.trim()] = pkgs
                    }
                }
            }
        }

        val remoteApps = mutableSetOf<String>()
        if (hasBlockedAppsField) {
            val appsArray = payload.getAsJsonArray("blocked_apps")
            for (elem in appsArray) {
                val pkg = elem?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                if (pkg.isNotEmpty()) {
                    remoteApps.add(pkg)
                }
            }
        }
        remoteNamedLists.values.forEach { remoteApps.addAll(it) }

        if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.saveAppBlockLists(remoteNamedLists)
            savedPrefs.saveBlockedApps(remoteApps)
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        } else if (hasBlockedAppsField || hasNamedListsField) {
            if (hasNamedListsField) {
                val mergedLists = savedPrefs.loadAppBlockLists()
                for ((listName, pkgs) in remoteNamedLists) {
                    val targetSet = mergedLists.getOrPut(listName) { mutableSetOf() }
                    targetSet.addAll(pkgs)
                }
                savedPrefs.saveAppBlockLists(mergedLists)
            }
            val appSet = savedPrefs.loadBlockedApps().toMutableSet()
            appSet.addAll(remoteApps)
            savedPrefs.saveBlockedApps(appSet)
            if (appSet.isNotEmpty()) {
                savedPrefs.setAppBlockerFeatureEnabled(true)
            }
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        }

        // B1. Blocked Domains (Website Blocker - strictly separated from Blocked Keywords)
        val remoteDomains = mutableSetOf<String>()
        if (hasDomainsField) {
            val domArray = payload.getAsJsonArray("blocked_domains")
            for (elem in domArray) {
                val d = normalizeDomain(elem?.takeIf { !it.isJsonNull }?.asString)
                if (d.isNotEmpty()) {
                    remoteDomains.add(d)
                }
            }
        }

        if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.saveBlockedWebsites(remoteDomains)
            val enableWeb = remoteDomains.isNotEmpty()
            savedPrefs.setWebsiteBlockerEnabled(enableWeb)
            savedPrefs.setWebsiteBlockerFeatureEnabled(enableWeb)
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER)
        } else if (hasDomainsField) {
            val websiteSet = savedPrefs.loadBlockedWebsites().toMutableSet()
            websiteSet.addAll(remoteDomains)
            savedPrefs.saveBlockedWebsites(websiteSet)
            if (remoteDomains.isNotEmpty() || websiteSet.isNotEmpty()) {
                savedPrefs.setWebsiteBlockerEnabled(true)
                savedPrefs.setWebsiteBlockerFeatureEnabled(true)
            }
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER)
        }

        // B2. Blocked Keywords (Keyword Blocker - strictly separated from Blocked Domains)
        val remoteKeywords = mutableSetOf<String>()
        if (hasKeywordsField) {
            val kwArray = payload.getAsJsonArray("blocked_keywords")
            for (elem in kwArray) {
                val k = elem?.takeIf { !it.isJsonNull }?.asString?.trim()?.lowercase(Locale.ROOT).orEmpty()
                if (k.isNotEmpty()) {
                    remoteKeywords.add(k)
                }
            }
        }

        if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.saveBlockedKeywords(remoteKeywords)
            sendRefreshBroadcast(context, INTENT_ACTION_REFRESH_KEYWORD_BLOCKER)
        } else if (hasKeywordsField) {
            val keywordSet = savedPrefs.loadBlockedKeywords().toMutableSet()
            keywordSet.addAll(remoteKeywords)
            savedPrefs.saveBlockedKeywords(keywordSet)
            if (remoteKeywords.isNotEmpty() || keywordSet.isNotEmpty()) {
                savedPrefs.setKeywordBlockerEnabled(true)
                savedPrefs.setKeywordBlockerFeatureEnabled(true)
            }
            sendRefreshBroadcast(context, INTENT_ACTION_REFRESH_KEYWORD_BLOCKER)
        }

        // C. Adult Pack & Categories Filter
        if (hasCategoriesField) {
            val catArray = payload.getAsJsonArray("blocked_categories")
            val categories = catArray.mapNotNull { it?.takeIf { !it.isJsonNull }?.asString?.trim()?.lowercase(Locale.ROOT) }
            val hasAdult = categories.any { it.contains("adult") || it.contains("porn") || it.contains("nsfw") }
            savedPrefs.setKeywordBlockerAdultPackEnabled(hasAdult)
            if (hasAdult) {
                savedPrefs.setKeywordBlockerEnabled(true)
                savedPrefs.setKeywordBlockerFeatureEnabled(true)
            }
        } else if (payload.has("adult_pack") && !payload.get("adult_pack").isJsonNull) {
            val adultPack = payload.get("adult_pack").asBoolean
            savedPrefs.setKeywordBlockerAdultPackEnabled(adultPack)
            if (adultPack) {
                savedPrefs.setKeywordBlockerEnabled(true)
                savedPrefs.setKeywordBlockerFeatureEnabled(true)
            }
        } else if (payload.has("allow_unblur") && !payload.get("allow_unblur").isJsonNull) {
            val adultPack = !payload.get("allow_unblur").asBoolean
            savedPrefs.setKeywordBlockerAdultPackEnabled(adultPack)
        } else if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.setKeywordBlockerAdultPackEnabled(false)
        }

        // D. Focus Mode & Strict Mode
        val hasStrictMode = payload.has("strict_mode") && !payload.get("strict_mode").isJsonNull
        val strictFlag = if (hasStrictMode) payload.get("strict_mode").asBoolean else false

        if (payload.has("focus_mode") && payload.get("focus_mode").isJsonObject) {
            val fm = payload.getAsJsonObject("focus_mode")
            if (fm.has("strictness") && !fm.get("strictness").isJsonNull) {
                savedPrefs.setFocusModeStrictness(fm.get("strictness").asInt.coerceIn(0, 1))
            }
            if (fm.has("allow_early_stop") && !fm.get("allow_early_stop").isJsonNull) {
                savedPrefs.setFocusModeEarlyStopAllowed(fm.get("allow_early_stop").asBoolean)
            }
            if (fm.has("always_whitelisted_apps") && fm.get("always_whitelisted_apps").isJsonArray) {
                val whitelist = fm.getAsJsonArray("always_whitelisted_apps")
                    .mapNotNull { it?.takeIf { !it.isJsonNull }?.asString?.trim() }
                    .filter { it.isNotEmpty() }
                if (mode == ApplyMode.FORCE_REPLACE) {
                    savedPrefs.saveAlwaysWhitelistedApps(whitelist)
                } else {
                    val merged = (savedPrefs.getAlwaysWhitelistedApps() + whitelist).distinct()
                    savedPrefs.saveAlwaysWhitelistedApps(merged)
                }
            }
        }

        if (hasStrictMode) {
            savedPrefs.setFocusModeFeatureEnabled(strictFlag)
            if (strictFlag) {
                savedPrefs.setFocusModeStrictness(1)
                savedPrefs.setFocusModeEarlyStopAllowed(false)
            } else if (!payload.has("focus_mode") || !payload.get("focus_mode").isJsonObject) {
                savedPrefs.setFocusModeStrictness(0)
                savedPrefs.setFocusModeEarlyStopAllowed(true)
            }
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        } else if (payload.has("focus_mode")) {
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        }

        // E. Reel Blocker
        if (hasReelBlockerField) {
            val rb = payload.getAsJsonObject("reel_blocker")
            val enabled = rb.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            savedPrefs.setReelBlockerEnabled(enabled)
            savedPrefs.setReelsBlockerFeatureEnabled(enabled)
            if (rb.has("mode") && !rb.get("mode").isJsonNull) {
                savedPrefs.setReelBlockerMode(rb.get("mode").asInt)
            }
            val limitElem = rb.get("daily_limit_minutes")?.takeIf { !it.isJsonNull }
                ?: rb.get("daily_limit")?.takeIf { !it.isJsonNull }
            if (limitElem != null) {
                savedPrefs.setReelBlockerDailyLimit(limitElem.asInt)
            }
            if (rb.has("youtube") && !rb.get("youtube").isJsonNull) {
                savedPrefs.setReelBlockerYoutubeEnabled(rb.get("youtube").asBoolean)
            }
            if (rb.has("instagram") && !rb.get("instagram").isJsonNull) {
                savedPrefs.setReelBlockerInstagramEnabled(rb.get("instagram").asBoolean)
            }
            if (rb.has("tiktok") && !rb.get("tiktok").isJsonNull) {
                savedPrefs.setReelBlockerTiktokEnabled(rb.get("tiktok").asBoolean)
            }
            if (rb.has("browser") && !rb.get("browser").isJsonNull) {
                savedPrefs.setReelBlockerBrowserEnabled(rb.get("browser").asBoolean)
            }
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER)
        } else if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.setReelBlockerEnabled(false)
            savedPrefs.setReelsBlockerFeatureEnabled(false)
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER)
        }

        // F. Launch Limits (`AppLaunchLimitRule`)
        val remoteLaunchLimits = mutableListOf<AppLaunchLimitRule>()
        if (hasLaunchLimitsField) {
            val limitsArr = payload.getAsJsonArray("launch_limits")
            limitsArr.forEachIndexed { index, elem ->
                if (elem != null && elem.isJsonObject) {
                    parseLaunchLimitRuleFromJson(elem.asJsonObject, index)?.let {
                        remoteLaunchLimits.add(it)
                    }
                }
            }
        }

        if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.saveAppLaunchLimitRules(remoteLaunchLimits)
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        } else if (hasLaunchLimitsField) {
            for (limitRule in remoteLaunchLimits) {
                savedPrefs.addAppLaunchLimitRule(limitRule)
            }
            if (remoteLaunchLimits.isNotEmpty()) {
                savedPrefs.setAppBlockerFeatureEnabled(true)
            }
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        }

        // G. Schedules (v2 JsonArray of multi-blocker rules + v1 JsonObject backward compatibility)
        var syncedSchedulesCount = 0
        val parsedRemoteSchedules = mutableListOf<AppBlockScheduleRule>()

        if (hasSchedulesField) {
            val schedulesElement = payload.get("schedules")
            if (schedulesElement.isJsonArray) {
                val schArray = schedulesElement.asJsonArray
                schArray.forEachIndexed { index, elem ->
                    if (elem != null && elem.isJsonObject) {
                        parseScheduleRuleFromJson(elem.asJsonObject, index)?.let { rule ->
                            parsedRemoteSchedules.add(rule)
                        }
                    }
                }
                syncedSchedulesCount = parsedRemoteSchedules.size

                if (mode == ApplyMode.FORCE_REPLACE) {
                    savedPrefs.saveAppBlockerScheduleRules(parsedRemoteSchedules.toMutableList())
                } else {
                    for (rule in parsedRemoteSchedules) {
                        savedPrefs.upsertAppBlockerScheduleRule(rule)
                    }
                }
                enableFeaturesForSchedules(savedPrefs, parsedRemoteSchedules)
                sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
            } else if (schedulesElement.isJsonObject) {
                val sch = schedulesElement.asJsonObject
                val enabled = sch.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                if (enabled) {
                    val startMinute = parseMinutesField(sch, "startMinute", "start", 9 * 60)
                    val endMinute = parseMinutesField(sch, "endMinute", "end", 17 * 60)
                    val daysSet = parseSelectedDays(sch.get("days") ?: sch.get("selectedDays"))
                        .ifEmpty { setOf(1, 2, 3, 4, 5) }

                    val rule = AppBlockScheduleRule(
                        id = "cloud_sync_schedule",
                        groupId = "cloud_sync_group",
                        title = "Cloud Sync Schedule",
                        packageName = "all",
                        blockerType = BlockerType.APP,
                        targets = listOf("all"),
                        type = AppBlockScheduleRule.RuleType.BLOCK,
                        recurrence = AppBlockScheduleRule.Recurrence.WEEKLY,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        selectedDays = daysSet,
                        isEnabled = true
                    ).sanitize()

                    parsedRemoteSchedules.add(rule)
                    syncedSchedulesCount = 1

                    if (mode == ApplyMode.FORCE_REPLACE) {
                        savedPrefs.saveAppBlockerScheduleRules(mutableListOf(rule))
                    } else {
                        savedPrefs.upsertAppBlockerScheduleRule(rule)
                    }
                    savedPrefs.setAppBlockerFeatureEnabled(true)
                    sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
                } else {
                    // v1 enabled == false: remove or disable "cloud_sync_schedule"
                    if (mode == ApplyMode.FORCE_REPLACE) {
                        savedPrefs.saveAppBlockerScheduleRules(mutableListOf())
                    } else {
                        savedPrefs.removeAppBlockerScheduleRule("cloud_sync_schedule")
                    }
                    sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
                }
            }
        } else if (mode == ApplyMode.FORCE_REPLACE) {
            savedPrefs.saveAppBlockerScheduleRules(mutableListOf())
            sendRefreshBroadcast(context, AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
        }

        // H. Reconcile Feature Toggles in FORCE_REPLACE mode
        if (mode == ApplyMode.FORCE_REPLACE) {
            val activeSchedules = parsedRemoteSchedules.filter { it.isRuleEnabled }
            val appBlockerOn = remoteApps.isNotEmpty() ||
                remoteLaunchLimits.any { it.isEnabled } ||
                activeSchedules.any { it.blockerType == BlockerType.APP }
            val webBlockerOn = remoteDomains.isNotEmpty() ||
                activeSchedules.any { it.blockerType == BlockerType.WEBSITE }
            val keywordBlockerOn = remoteKeywords.isNotEmpty() ||
                savedPrefs.isKeywordBlockerAdultPackEnabled() ||
                activeSchedules.any { it.blockerType == BlockerType.KEYWORD }
            val reelBlockerFromField = if (hasReelBlockerField) {
                payload.getAsJsonObject("reel_blocker").get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
            } else false
            val reelBlockerOn = reelBlockerFromField ||
                activeSchedules.any { it.blockerType == BlockerType.REELS }

            savedPrefs.setAppBlockerFeatureEnabled(appBlockerOn)
            savedPrefs.setWebsiteBlockerEnabled(webBlockerOn)
            savedPrefs.setWebsiteBlockerFeatureEnabled(webBlockerOn)
            savedPrefs.setKeywordBlockerEnabled(keywordBlockerOn)
            savedPrefs.setKeywordBlockerFeatureEnabled(keywordBlockerOn)
            savedPrefs.setReelBlockerEnabled(reelBlockerOn)
            savedPrefs.setReelsBlockerFeatureEnabled(reelBlockerOn)
        }

        return SyncSummary(
            success = true,
            appsCount = remoteApps.size,
            websitesCount = remoteDomains.size,
            keywordsCount = remoteKeywords.size,
            schedulesCount = syncedSchedulesCount,
            launchLimitsCount = remoteLaunchLimits.size,
            mode = mode,
            timestamp = savedPrefs.getLastPolicySyncTimestamp().takeIf { it > 0L } ?: System.currentTimeMillis(),
            message = "Applied $syncedSchedulesCount schedules, ${remoteApps.size} apps, ${remoteDomains.size} websites, ${remoteKeywords.size} keywords."
        )
    }

    fun exportLocalPolicyPayload(context: Context): JsonObject {
        val savedPrefs = SavedPreferencesLoader(context)
        return JsonObject().apply {
            addProperty("schema_version", 2)
            addProperty("updated_at", java.time.Instant.now().toString())

            val isStrict = savedPrefs.getFocusModeStrictness() >= 1 || !savedPrefs.isFocusModeEarlyStopAllowed()
            addProperty("strict_mode", isStrict)
            addProperty("allow_unblur", !savedPrefs.isKeywordBlockerAdultPackEnabled())

            val appsArray = JsonArray()
            savedPrefs.loadBlockedApps().sorted().forEach { appsArray.add(it) }
            add("blocked_apps", appsArray)

            val namedListsObj = JsonObject()
            savedPrefs.loadAppBlockLists().forEach { (listName, pkgs) ->
                val arr = JsonArray()
                pkgs.sorted().forEach { arr.add(it) }
                namedListsObj.add(listName, arr)
            }
            add("named_block_lists", namedListsObj)

            val domainsArray = JsonArray()
            savedPrefs.loadBlockedWebsites().sorted().forEach { domainsArray.add(it) }
            add("blocked_domains", domainsArray)

            val keywordsArray = JsonArray()
            savedPrefs.loadBlockedKeywords().sorted().forEach { keywordsArray.add(it) }
            add("blocked_keywords", keywordsArray)

            val categoriesArray = JsonArray()
            val categories = mutableSetOf<String>()
            if (savedPrefs.isKeywordBlockerAdultPackEnabled()) {
                categories.add("adult")
            }
            categories.addAll(savedPrefs.getAutoBlockCategories().map { it.lowercase(Locale.ROOT) })
            categories.sorted().forEach { categoriesArray.add(it) }
            add("blocked_categories", categoriesArray)

            val reelObj = JsonObject().apply {
                addProperty("enabled", savedPrefs.isReelBlockerEnabled(false))
                addProperty("mode", savedPrefs.getReelBlockerMode())
                addProperty("daily_limit_minutes", savedPrefs.getReelBlockerDailyLimit())
                addProperty("youtube", savedPrefs.isReelBlockerYoutubeEnabled(false))
                addProperty("instagram", savedPrefs.isReelBlockerInstagramEnabled(false))
                addProperty("tiktok", savedPrefs.isReelBlockerTiktokEnabled(false))
                addProperty("browser", savedPrefs.isReelBlockerBrowserEnabled(false))
            }
            add("reel_blocker", reelObj)

            val focusObj = JsonObject().apply {
                addProperty("strictness", savedPrefs.getFocusModeStrictness())
                addProperty("allow_early_stop", savedPrefs.isFocusModeEarlyStopAllowed())
                val whitelistArr = JsonArray()
                savedPrefs.getAlwaysWhitelistedApps().forEach { whitelistArr.add(it) }
                add("always_whitelisted_apps", whitelistArr)
            }
            add("focus_mode", focusObj)

            val schedulesArr = JsonArray()
            savedPrefs.loadAppBlockerScheduleRules().forEach { rule ->
                schedulesArr.add(serializeScheduleRuleToJson(rule))
            }
            add("schedules", schedulesArr)

            val launchLimitsArr = JsonArray()
            savedPrefs.loadAppLaunchLimitRules().forEach { limit ->
                launchLimitsArr.add(serializeLaunchLimitRuleToJson(limit))
            }
            add("launch_limits", launchLimitsArr)
        }
    }

    suspend fun pushLocalPolicyToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        val savedPrefs = SavedPreferencesLoader(context)
        val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val payload = exportLocalPolicyPayload(context)
        savedPrefs.saveCachedPolicyPayload(payload.toString())

        val pairedDeviceId = appPrefs.getString("paired_device_id", null)?.trim()
        val pairedOwnerId = appPrefs.getString("paired_owner_id", null)?.trim()
        val deviceLabel = runCatching { savedPrefs.getDeviceLabel() }.getOrDefault("Android")
        val rest = SupabaseRest()
        val session = resolveActiveSession(context, rest)

        var pushed = false

        try {
            if (!pairedDeviceId.isNullOrBlank()) {
                if (rest.updateDevicePolicy(pairedDeviceId, payload, deviceLabel, session?.accessToken)) {
                    pushed = true
                }
            }

            if (session != null) {
                val targetDeviceId = pairedDeviceId?.takeIf { it.isNotEmpty() }
                    ?: runCatching { SecureKeyStore(context).deviceId }.getOrDefault(savedPrefs.getDeviceId())
                if (rest.pushUserCloudPolicy(session, targetDeviceId, deviceLabel, payload)) {
                    pushed = true
                }
            }

            if (!pushed) {
                val userEmail = appPrefs.getString("profile_email", null)?.takeIf { it.isNotBlank() }
                    ?: savedPrefs.getUserEmail()?.takeIf { it.isNotBlank() }
                val ownerId = pairedOwnerId?.takeIf { it.isNotEmpty() }
                    ?: savedPrefs.getUserId()?.takeIf { it.isNotBlank() }
                    ?: if (!userEmail.isNullOrBlank()) {
                        runCatching { rest.fetchProfileByEmail(userEmail)?.id }.getOrNull()
                    } else null

                if (!ownerId.isNullOrBlank()) {
                    if (rest.upsertDevicePolicyForOwner(ownerId, pairedDeviceId, deviceLabel, payload)) {
                        pushed = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push local policy to cloud", e)
        }

        return@withContext pushed
    }

    fun hasPreSyncSnapshot(context: Context): Boolean {
        return SavedPreferencesLoader(context).hasPreSyncRuleSnapshot()
    }

    fun restorePreSyncSnapshot(context: Context): Boolean {
        return SavedPreferencesLoader(context).restorePreSyncRuleSnapshot()
    }

    internal fun parseScheduleRuleFromJson(obj: JsonObject, index: Int = 0): AppBlockScheduleRule? {
        return try {
            val rawId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val safeId = rawId.ifEmpty { "cloud_sync_schedule_$index" }

            val groupId = obj.get("groupId")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: obj.get("group_id")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: "cloud_sync_group"
            val groupTitle = obj.get("groupTitle")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: obj.get("group_title")?.takeIf { !it.isJsonNull }?.asString?.trim()

            val rawTitle = obj.get("title")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val rawPackage = obj.get("packageName")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: obj.get("package_name")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: ""

            val rawBlockerTypeStr = obj.get("blockerType")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: obj.get("blocker_type")?.takeIf { !it.isJsonNull }?.asString?.trim()
            val blockerType = parseBlockerType(rawBlockerTypeStr, rawPackage)

            val targets = parseStringList(obj.get("targets"))
            val targetWebsites = parseStringList(obj.get("targetWebsites") ?: obj.get("target_websites"))
                .map { normalizeDomain(it) }
                .filter { it.isNotEmpty() }
            val targetKeywords = parseStringList(obj.get("targetKeywords") ?: obj.get("target_keywords"))
                .map { it.lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }

            val resolvedTargets = when {
                targets.isNotEmpty() -> when (blockerType) {
                    BlockerType.WEBSITE -> targets.map { normalizeDomain(it) }.filter { it.isNotEmpty() }
                    BlockerType.KEYWORD -> targets.map { it.lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
                    else -> targets
                }
                blockerType == BlockerType.WEBSITE && targetWebsites.isNotEmpty() -> targetWebsites
                blockerType == BlockerType.KEYWORD && targetKeywords.isNotEmpty() -> targetKeywords
                blockerType == BlockerType.REELS -> listOf("reel_blocker")
                blockerType == BlockerType.FOCUS_MODE -> listOf("FOCUS_MODE")
                rawPackage.isNotEmpty() -> listOf(rawPackage)
                else -> listOf("all")
            }

            val effectivePackage = when {
                rawPackage.isNotEmpty() -> rawPackage
                blockerType == BlockerType.APP -> resolvedTargets.firstOrNull() ?: "all"
                else -> blockerType.packageNameIdentifier
            }

            val ruleTypeStr = obj.get("type")?.takeIf { !it.isJsonNull }?.asString?.trim()?.uppercase(Locale.ROOT)
            val ruleType = if (ruleTypeStr == "CHEAT") {
                AppBlockScheduleRule.RuleType.CHEAT
            } else {
                AppBlockScheduleRule.RuleType.BLOCK
            }

            val selectedDays = parseSelectedDays(
                obj.get("selectedDays") ?: obj.get("selected_days") ?: obj.get("days")
            )

            val recurrenceStr = obj.get("recurrence")?.takeIf { !it.isJsonNull }?.asString?.trim()?.uppercase(Locale.ROOT)
            val recurrence = when (recurrenceStr) {
                "WEEKLY" -> AppBlockScheduleRule.Recurrence.WEEKLY
                "DAILY" -> AppBlockScheduleRule.Recurrence.DAILY
                "HOURLY" -> AppBlockScheduleRule.Recurrence.HOURLY
                "ALWAYS" -> AppBlockScheduleRule.Recurrence.ALWAYS
                else -> if (selectedDays.isNotEmpty() && selectedDays.size < 7) {
                    AppBlockScheduleRule.Recurrence.WEEKLY
                } else {
                    AppBlockScheduleRule.Recurrence.DAILY
                }
            }

            val startMinute = parseMinutesField(obj, "startMinute", "start", 0)
            val endMinute = parseMinutesField(obj, "endMinute", "end", 1439)

            val isEnabled = obj.get("isEnabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: obj.get("is_enabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: obj.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: true

            AppBlockScheduleRule(
                id = safeId,
                groupId = groupId,
                groupTitle = groupTitle,
                title = rawTitle.ifEmpty { "Cloud Sync Schedule" },
                packageName = effectivePackage,
                blockerType = blockerType,
                targets = resolvedTargets,
                targetWebsites = if (blockerType == BlockerType.WEBSITE) resolvedTargets else targetWebsites,
                targetKeywords = if (blockerType == BlockerType.KEYWORD) resolvedTargets else targetKeywords,
                type = ruleType,
                recurrence = recurrence,
                startMinute = startMinute.coerceIn(0, 1439),
                endMinute = endMinute.coerceIn(0, 1439),
                selectedDays = if (selectedDays.isEmpty() && recurrence == AppBlockScheduleRule.Recurrence.WEEKLY) {
                    setOf(1, 2, 3, 4, 5, 6, 7)
                } else {
                    selectedDays
                },
                isEnabled = isEnabled
            ).sanitize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse schedule rule at index $index", e)
            null
        }
    }

    internal fun parseLaunchLimitRuleFromJson(obj: JsonObject, index: Int = 0): AppLaunchLimitRule? {
        return try {
            val pkg = obj.get("packageName")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: obj.get("package_name")?.takeIf { !it.isJsonNull }?.asString?.trim()
                ?: ""
            if (pkg.isEmpty()) return null

            val rawId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            val safeId = rawId.ifEmpty { "cloud_limit_${pkg}_$index" }
            val maxLaunches = obj.get("maxLaunches")?.takeIf { !it.isJsonNull }?.asInt
                ?: obj.get("max_launches")?.takeIf { !it.isJsonNull }?.asInt
                ?: 5

            val periodStr = obj.get("timePeriod")?.takeIf { !it.isJsonNull }?.asString?.trim()?.uppercase(Locale.ROOT)
                ?: obj.get("time_period")?.takeIf { !it.isJsonNull }?.asString?.trim()?.uppercase(Locale.ROOT)
            val timePeriod = when (periodStr) {
                "HOURLY" -> AppLaunchLimitRule.TimePeriod.HOURLY
                "WEEKLY" -> AppLaunchLimitRule.TimePeriod.WEEKLY
                else -> AppLaunchLimitRule.TimePeriod.DAILY
            }

            val dayOfWeekElem = obj.get("dayOfWeek")?.takeIf { !it.isJsonNull }
                ?: obj.get("day_of_week")?.takeIf { !it.isJsonNull }
            val dayOfWeek = parseDayOfWeekElement(dayOfWeekElem) ?: Calendar.MONDAY

            val isEnabled = obj.get("isEnabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: obj.get("is_enabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: obj.get("enabled")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: true

            AppLaunchLimitRule(
                id = safeId,
                packageName = pkg,
                maxLaunches = maxLaunches.coerceAtLeast(1),
                timePeriod = timePeriod,
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                isEnabled = isEnabled
            ).sanitize()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse launch limit rule at index $index", e)
            null
        }
    }

    internal fun parseSelectedDays(element: JsonElement?): Set<Int> {
        if (element == null || element.isJsonNull || !element.isJsonArray) return emptySet()
        val arr = element.asJsonArray
        val result = mutableSetOf<Int>()
        for (item in arr) {
            parseDayOfWeekElement(item)?.let { result.add(it) }
        }
        return result
    }

    internal fun parseDayOfWeekElement(element: JsonElement?): Int? {
        if (element == null || element.isJsonNull || !element.isJsonPrimitive) return null
        val prim = element.asJsonPrimitive
        if (prim.isNumber) {
            val n = runCatching { prim.asInt }.getOrNull() ?: return null
            return when {
                n in 1..7 -> n
                n == 0 -> Calendar.SUNDAY
                else -> null
            }
        }
        val str = prim.asString.trim().uppercase(Locale.ROOT)
        if (str.isEmpty()) return null
        str.toIntOrNull()?.let { n ->
            return when {
                n in 1..7 -> n
                n == 0 -> Calendar.SUNDAY
                else -> null
            }
        }
        return when {
            str.startsWith("SUN") || str == "SU" -> Calendar.SUNDAY
            str.startsWith("MON") || str == "MO" -> Calendar.MONDAY
            str.startsWith("TUE") || str == "TU" -> Calendar.TUESDAY
            str.startsWith("WED") || str == "WE" -> Calendar.WEDNESDAY
            str.startsWith("THU") || str == "TH" -> Calendar.THURSDAY
            str.startsWith("FRI") || str == "FR" -> Calendar.FRIDAY
            str.startsWith("SAT") || str == "SA" -> Calendar.SATURDAY
            else -> null
        }
    }

    private fun parseBlockerType(rawBlockerType: String?, rawPackageName: String?): BlockerType {
        if (!rawBlockerType.isNullOrBlank()) {
            when (rawBlockerType.trim().uppercase(Locale.ROOT)) {
                "APP", "APPS", "APPLICATION" -> return BlockerType.APP
                "WEBSITE", "WEBSITES", "DOMAIN", "DOMAINS", "WEB" -> return BlockerType.WEBSITE
                "KEYWORD", "KEYWORDS" -> return BlockerType.KEYWORD
                "REELS", "REEL", "SHORTS", "SHORT_FORM" -> return BlockerType.REELS
                "FOCUS_MODE", "FOCUS", "FOCUSMODE" -> return BlockerType.FOCUS_MODE
            }
            val byTarget = BlockerType.fromTargetTypeString(rawBlockerType)
            if (byTarget != BlockerType.APP) return byTarget
        }
        return BlockerType.fromPackageName(rawPackageName)
    }

    private fun parseMinutesField(
        obj: JsonObject,
        minuteKey: String,
        timeStrKey: String,
        defaultMinutes: Int
    ): Int {
        val snakeKey = minuteKey.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase(Locale.ROOT)
        val numElem = obj.get(minuteKey)?.takeIf { !it.isJsonNull }
            ?: obj.get(snakeKey)?.takeIf { !it.isJsonNull }
        if (numElem != null && numElem.isJsonPrimitive) {
            val prim = numElem.asJsonPrimitive
            if (prim.isNumber) {
                return runCatching { prim.asInt }.getOrDefault(defaultMinutes)
            }
            prim.asString.trim().toIntOrNull()?.let { return it }
        }
        val strElem = obj.get(timeStrKey)?.takeIf { !it.isJsonNull }
        if (strElem != null && strElem.isJsonPrimitive) {
            val timeStr = strElem.asString.trim()
            if (timeStr.contains(":")) {
                val parts = timeStr.split(":")
                val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: (defaultMinutes / 60)
                val min = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: (defaultMinutes % 60)
                return (hour.coerceIn(0, 23) * 60) + min.coerceIn(0, 59)
            }
            timeStr.toIntOrNull()?.let { return it }
        }
        return defaultMinutes
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonArray) {
            return element.asJsonArray
                .mapNotNull { it?.takeIf { !it.isJsonNull }?.asString?.trim() }
                .filter { it.isNotEmpty() }
        }
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            val single = element.asString.trim()
            return if (single.isNotEmpty()) listOf(single) else emptyList()
        }
        return emptyList()
    }

    private fun normalizeDomain(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var cleaned = raw.trim().lowercase(Locale.ROOT)
        if (cleaned.startsWith("http://")) cleaned = cleaned.removePrefix("http://")
        if (cleaned.startsWith("https://")) cleaned = cleaned.removePrefix("https://")
        if (cleaned.startsWith("www.")) cleaned = cleaned.removePrefix("www.")
        cleaned = cleaned.trimEnd('/')
        return cleaned.trim()
    }

    private fun enableFeaturesForSchedules(
        savedPrefs: SavedPreferencesLoader,
        schedules: List<AppBlockScheduleRule>
    ) {
        for (rule in schedules) {
            if (!rule.isRuleEnabled) continue
            when (rule.blockerType) {
                BlockerType.APP -> savedPrefs.setAppBlockerFeatureEnabled(true)
                BlockerType.WEBSITE -> {
                    savedPrefs.setWebsiteBlockerEnabled(true)
                    savedPrefs.setWebsiteBlockerFeatureEnabled(true)
                }
                BlockerType.KEYWORD -> {
                    savedPrefs.setKeywordBlockerEnabled(true)
                    savedPrefs.setKeywordBlockerFeatureEnabled(true)
                }
                BlockerType.REELS -> {
                    savedPrefs.setReelBlockerEnabled(true)
                    savedPrefs.setReelsBlockerFeatureEnabled(true)
                }
                BlockerType.FOCUS_MODE -> savedPrefs.setFocusModeFeatureEnabled(true)
            }
        }
    }

    private fun serializeScheduleRuleToJson(rule: AppBlockScheduleRule): JsonObject {
        val sanitized = rule.sanitize()
        return JsonObject().apply {
            addProperty("id", sanitized.id)
            addProperty("groupId", sanitized.groupId ?: "default_group")
            if (!sanitized.groupTitle.isNullOrBlank()) {
                addProperty("groupTitle", sanitized.groupTitle)
            }
            addProperty("title", sanitized.title)
            addProperty("packageName", sanitized.packageName)
            addProperty("blockerType", sanitized.blockerType.name)

            val targetsArr = JsonArray()
            sanitized.targets.forEach { targetsArr.add(it) }
            add("targets", targetsArr)

            val websitesArr = JsonArray()
            sanitized.targetWebsites.forEach { websitesArr.add(it) }
            add("targetWebsites", websitesArr)

            val keywordsArr = JsonArray()
            sanitized.targetKeywords.forEach { keywordsArr.add(it) }
            add("targetKeywords", keywordsArr)

            addProperty("type", sanitized.type.name)
            addProperty("recurrence", sanitized.recurrence.name)
            addProperty("startMinute", sanitized.startMinute)
            addProperty("endMinute", sanitized.endMinute)

            val daysArr = JsonArray()
            sanitized.selectedDays.sorted().forEach { daysArr.add(it) }
            add("selectedDays", daysArr)

            addProperty("isEnabled", sanitized.isRuleEnabled)
        }
    }

    private fun serializeLaunchLimitRuleToJson(rule: AppLaunchLimitRule): JsonObject {
        val sanitized = rule.sanitize()
        return JsonObject().apply {
            addProperty("id", sanitized.id)
            addProperty("packageName", sanitized.packageName)
            addProperty("maxLaunches", sanitized.maxLaunches)
            addProperty("timePeriod", sanitized.timePeriod.name)
            addProperty("dayOfWeek", sanitized.dayOfWeek)
            addProperty("isEnabled", sanitized.isEnabled)
        }
    }

    private fun resolveActiveSession(context: Context, rest: SupabaseRest): SupabaseRest.Session? {
        return try {
            val keys = SecureKeyStore(context)
            val accessToken = keys.accessToken
            val refreshToken = keys.refreshToken
            if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
                return null
            }
            try {
                val refreshed = rest.refresh(refreshToken)
                keys.accessToken = refreshed.accessToken
                keys.refreshToken = refreshed.refreshToken
                refreshed
            } catch (_: Exception) {
                val savedPrefs = SavedPreferencesLoader(context)
                val userId = savedPrefs.getUserId().orEmpty()
                if (userId.isNotBlank()) {
                    SupabaseRest.Session(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        userId = userId,
                        email = savedPrefs.getUserEmail()
                    )
                } else {
                    null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun sendRefreshBroadcast(context: Context, action: String) {
        broadcastListenerForTesting?.invoke(action)
        runCatching {
            val pkg = runCatching { context.packageName }.getOrNull()
            val intent = Intent(action)
            if (!pkg.isNullOrBlank()) {
                intent.setPackage(pkg)
            }
            context.sendBroadcast(intent)
        }
    }

    private fun sendHeartbeat(context: Context, deviceId: String, accessToken: String? = null) {
        try {
            val nowIso = java.time.Instant.now().toString()
            val updateObj = JsonObject().apply {
                addProperty("last_heartbeat", nowIso)
                addProperty("last_seen", nowIso)
                addProperty("is_online", true)
                addProperty("platform", "android")
            }

            val bearer = accessToken?.takeIf { it.isNotBlank() } ?: ANON_KEY
            val patchReq = Request.Builder()
                .url("$BASE_URL/rest/v1/devices?id=eq.$deviceId")
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $bearer")
                .header("Content-Type", "application/json")
                .patch(updateObj.toString().toRequestBody(jsonType))
                .build()

            client.newCall(patchReq).execute().close()
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed", e)
        }
    }
}
