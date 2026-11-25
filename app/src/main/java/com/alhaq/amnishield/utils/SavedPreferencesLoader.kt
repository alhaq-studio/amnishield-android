package com.alhaq.amnishield.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.alhaq.amnishield.contract.GuardianContract
import com.alhaq.amnishield.data.db.AmniShieldRuleDatabase
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.blockers.ReelBlocker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.data.blockers.AlwaysOnShieldsConfig
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.data.blockers.BlockRuleStatus
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.data.blockers.LimitPeriod
import com.alhaq.amnishield.data.blockers.OpenLimitTrigger
import com.alhaq.amnishield.data.blockers.PauseOptionConfig
import com.alhaq.amnishield.data.blockers.PauseOptionType
import com.alhaq.amnishield.data.blockers.ReelsLimitMode
import com.alhaq.amnishield.data.blockers.ScreenTimeLimitTrigger
import com.alhaq.amnishield.data.blockers.UnifiedBlockRule
import com.alhaq.amnishield.data.blockers.WeeklyScheduleTrigger
import com.alhaq.amnishield.ui.activity.MainActivity
import java.util.Calendar
import java.util.UUID
import java.util.ArrayList
import java.time.LocalDate
import android.os.SystemClock

open class SavedPreferencesLoader(
    val context: Context,
    private val injectedCompassionatePrefs: SharedPreferences? = null,
    private val injectedPremiumPrefs: SharedPreferences? = null,
    private val injectedFounderPassPrefs: SharedPreferences? = null,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockProvider: () -> Long = { System.currentTimeMillis() }
) {

    fun loadPinnedApps(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("pinned_apps", emptySet()) ?: emptySet()
    }


    fun loadIgnoredAppUsageTracker(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("app_usage_tracker", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("ignored_apps", emptySet()) ?: emptySet()
    }

    fun loadBlockedApps(): Set<String> = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return@synchronized (sharedPreferences.getStringSet("blocked_apps", emptySet()) ?: emptySet()).toSet()
    }

    fun saveAppBlockerCooldownData(cooldowns: Map<String, Long>) = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        // apply() is asynchronous; cooldown data is non-critical and called frequently
        // from the accessibility hot path so we avoid synchronous disk writes here.
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadAppBlockerCooldownData(): MutableMap<String, Long> = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return@synchronized mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return@synchronized Gson().fromJson<MutableMap<String, Long>>(json, type) ?: mutableMapOf()
    }

    fun saveViewBlockerCooldownData(cooldowns: Map<String, Long>) = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("view_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadViewBlockerCooldownData(): MutableMap<String, Long> = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("view_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return@synchronized mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return@synchronized Gson().fromJson<MutableMap<String, Long>>(json, type) ?: mutableMapOf()
    }

    fun saveReelBlockerCooldownData(cooldowns: Map<String, Long>) = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadReelBlockerCooldownData(): MutableMap<String, Long> = synchronized(statsLock) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return@synchronized mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return@synchronized Gson().fromJson<MutableMap<String, Long>>(json, type) ?: mutableMapOf()
    }

    fun isReelBlockerEnabled(defaultValue: Boolean = false): Boolean {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("is_enabled", defaultValue)
    }

    fun setReelBlockerEnabled(enabled: Boolean, updateManual: Boolean = true) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit().putBoolean("is_enabled", enabled)
        if (updateManual) {
            editor.putBoolean("is_enabled_manual", enabled)
        }
        editor.apply()
    }

    fun getReelBlockerMode(defaultMode: Int = ReelBlocker.MODE_BLOCK_ALL): Int {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("mode_type", defaultMode)
    }

    fun setReelBlockerMode(mode: Int) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("mode_type", mode).apply()
    }

    fun getReelBlockerDailyLimit(defaultLimit: Int = 200): Int {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("daily_limit", defaultLimit)
    }

    fun setReelBlockerDailyLimit(limit: Int) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("daily_limit", limit).apply()
    }

    fun getReelBlockerBlockResponseMode(): ReelBlocker.BlockResponseMode {
        val raw = context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .getInt("block_response_mode", ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT.value)
        return ReelBlocker.BlockResponseMode.fromInt(raw)
    }

    fun setReelBlockerBlockResponseMode(mode: ReelBlocker.BlockResponseMode) {
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .edit().putInt("block_response_mode", mode.value).apply()
    }

    // -- Reel Blocker per-platform / browser toggles ---------------------------
    // All toggles default to `false` (opt-in model) so fresh installs have zero background rules.

    fun isReelBlockerYoutubeEnabled(default: Boolean = false): Boolean =
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .getBoolean("is_youtube_enabled", default)

    fun setReelBlockerYoutubeEnabled(enabled: Boolean) {
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .edit().putBoolean("is_youtube_enabled", enabled).apply()
    }

    fun isReelBlockerInstagramEnabled(default: Boolean = false): Boolean =
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .getBoolean("is_instagram_enabled", default)

    fun setReelBlockerInstagramEnabled(enabled: Boolean) {
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .edit().putBoolean("is_instagram_enabled", enabled).apply()
    }

    fun isReelBlockerTiktokEnabled(default: Boolean = false): Boolean =
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .getBoolean("is_tiktok_enabled", default)

    fun setReelBlockerTiktokEnabled(enabled: Boolean) {
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .edit().putBoolean("is_tiktok_enabled", enabled).apply()
    }

    fun isReelBlockerBrowserEnabled(default: Boolean = false): Boolean =
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .getBoolean("is_browser_enabled", default)

    fun setReelBlockerBrowserEnabled(enabled: Boolean) {
        context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
            .edit().putBoolean("is_browser_enabled", enabled).apply()
    }

    fun hasAnyReelPlatformConfigured(): Boolean {
        val prefs = context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        return prefs.contains("is_youtube_enabled") ||
                prefs.contains("is_instagram_enabled") ||
                prefs.contains("is_tiktok_enabled") ||
                prefs.contains("is_browser_enabled")
    }

    fun loadBlockedKeywords(): Set<String> = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return@synchronized (sharedPreferences.getStringSet("blocked_keywords", emptySet()) ?: emptySet()).toSet()
    }

    fun savePinned(pinnedApps: Set<String>) = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("pinned_apps", pinnedApps).apply()
    }


    fun saveBlockedApps(pinnedApps: Set<String>) = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("blocked_apps", pinnedApps).apply()
    }


    fun saveIgnoredAppUsageTracker(ignoredApps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("app_usage_tracker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("ignored_apps", ignoredApps).apply()
    }


    fun saveBlockedKeywords(pinnedApps: Set<String>) = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("blocked_keywords", pinnedApps).apply()
    }
    private data class LegacyAutoTimedActionItem(
        val title: String = "",
        val startTimeInMins: Int = 0,
        val endTimeInMins: Int = 0,
        val packages: ArrayList<String> = ArrayList()
    )

    fun migrateLegacySchedulesIfNeeded() {
        val migrationPrefs = context.getSharedPreferences("schedules_migration", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean("migrated_v2", false)) {
            return
        }

        // 1. Migrate App Blocker Cheat Hours
        val cheatHoursPrefs = context.getSharedPreferences("cheat_hours", Context.MODE_PRIVATE)
        val legacyCheatJson = cheatHoursPrefs.getString("cheatHoursList", null)
        if (!legacyCheatJson.isNullOrEmpty()) {
            try {
                val legacyType = object : TypeToken<List<LegacyAutoTimedActionItem>>() {}.type
                val legacyList = Gson().fromJson<List<LegacyAutoTimedActionItem>>(legacyCheatJson, legacyType) ?: emptyList()
                val currentRules = loadAppBlockerScheduleRules()
                legacyList.forEach { item ->
                    item.packages.forEach { pkg ->
                        val rule = AppBlockScheduleRule(
                            id = UUID.randomUUID().toString(),
                            title = item.title,
                            packageName = pkg,
                            type = AppBlockScheduleRule.RuleType.CHEAT,
                            recurrence = AppBlockScheduleRule.Recurrence.DAILY,
                            startMinute = item.startTimeInMins,
                            endMinute = item.endTimeInMins
                        )
                        currentRules.add(rule)
                    }
                }
                saveAppBlockerScheduleRules(currentRules)
            } catch (e: Exception) {
                Log.e("SavedPreferencesLoader", "Error migrating app blocker cheat hours", e)
            }
        }

        // 4. Mark migration as complete
        migrationPrefs.edit().putBoolean("migrated_v2", true).apply()

        // 5. Clean up old preferences keys/files
        context.getSharedPreferences("cheat_hours", Context.MODE_PRIVATE).edit()
            .remove("cheatHoursList")
            .remove("view_blocker_start_time")
            .remove("view_blocker_end_time")
            .apply()
        context.getSharedPreferences("auto_focus_hours", Context.MODE_PRIVATE).edit()
            .remove("auto_focus_list")
            .apply()
    }

    fun saveAppBlockerWarningInfo(warningData: MainActivity.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(warningData)

        editor.putString("app_blocker", json)
        editor.apply()
    }

    fun loadAppBlockerWarningInfo(): MainActivity.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("app_blocker", null)

        if (json.isNullOrEmpty()) return MainActivity.WarningData()

        val type = object : TypeToken<MainActivity.WarningData>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveViewBlockerWarningInfo(warningData: MainActivity.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(warningData)

        editor.putString("view_blocker", json)
        editor.apply()
    }

    fun saveKeywordBlockerWarningInfo(warningData: MainActivity.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(warningData)

        editor.putString("keyword_blocker", json)
        editor.apply()
    }

    fun loadKeywordBlockerWarningInfo(): MainActivity.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("keyword_blocker", null)

        if (json.isNullOrEmpty()) return MainActivity.WarningData(
            message = "Content containing a blocked keyword was detected.",
            isProceedDisabled = true
        )

        val type = object : TypeToken<MainActivity.WarningData>() {}.type
        return gson.fromJson(json, type)
    }

    fun getKeywordBlockerFeedbackMode(): String {
        val sharedPreferences = context.getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE)
        val mode = sharedPreferences.getString("feedback_mode", com.alhaq.amnishield.Constants.KEYWORD_FEEDBACK_SILENT)
        return if (mode.isNullOrEmpty()) com.alhaq.amnishield.Constants.KEYWORD_FEEDBACK_SILENT else mode
    }

    fun setKeywordBlockerFeedbackMode(mode: String) {
        val sharedPreferences = context.getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("feedback_mode", mode).apply()
    }

    fun getAppBlockerWarningStyle(): String {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        return sharedPreferences.getString("app_blocker_warning_style", com.alhaq.amnishield.Constants.BLOCKER_WARNING_STYLE_DIALOG)
            ?: com.alhaq.amnishield.Constants.BLOCKER_WARNING_STYLE_DIALOG
    }

    fun setAppBlockerWarningStyle(style: String) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("app_blocker_warning_style", style).apply()
    }

    fun getWebsiteBlockerWarningStyle(): String {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        return sharedPreferences.getString("website_blocker_warning_style", com.alhaq.amnishield.Constants.BLOCKER_WARNING_STYLE_SILENT)
            ?: com.alhaq.amnishield.Constants.BLOCKER_WARNING_STYLE_SILENT
    }

    fun setWebsiteBlockerWarningStyle(style: String) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("website_blocker_warning_style", style).apply()
    }

    fun getFocusModeInterceptionStyle(): String {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val defaultStyle = if (isAmniSpaceFocusLauncherEnabled()) {
            com.alhaq.amnishield.Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE
        } else {
            com.alhaq.amnishield.Constants.FOCUS_INTERCEPTION_STYLE_DIALOG
        }
        return sharedPreferences.getString("focus_mode_interception_style", defaultStyle) ?: defaultStyle
    }

    fun setFocusModeInterceptionStyle(style: String) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("focus_mode_interception_style", style).apply()
        // Sync with legacy boolean preference for backwards compatibility
        setAmniSpaceFocusLauncherEnabled(style == com.alhaq.amnishield.Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE)
    }

    fun loadFocusModeWarningInfo(): MainActivity.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("focus_mode_warning", null)
        if (json.isNullOrEmpty()) return MainActivity.WarningData(
            message = "This app is restricted during your active Focus Session.",
            isProceedDisabled = true
        )
        val type = object : TypeToken<MainActivity.WarningData>() {}.type
        return try {
            gson.fromJson(json, type) ?: MainActivity.WarningData(
                message = "This app is restricted during your active Focus Session.",
                isProceedDisabled = true
            )
        } catch (e: Exception) {
            MainActivity.WarningData(
                message = "This app is restricted during your active Focus Session.",
                isProceedDisabled = true
            )
        }
    }

    fun saveFocusModeWarningInfo(warningData: MainActivity.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(warningData)
        editor.putString("focus_mode_warning", json)
        editor.apply()
    }

    fun saveCheatHoursForViewBlocker(startTime: Int, endTime: Int) {
        val sharedPreferences = context.getSharedPreferences("cheat_hours", Context.MODE_PRIVATE)
        val edit = sharedPreferences.edit()
        edit.putInt("view_blocker_start_time", startTime)
        edit.putInt("view_blocker_end_time", endTime)
        edit.apply()
    }

    fun loadViewBlockerWarningInfo(): MainActivity.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("view_blocker", null)

        if (json.isNullOrEmpty()) return MainActivity.WarningData()

        val type = object : TypeToken<MainActivity.WarningData>() {}.type
        return gson.fromJson(json, type)
    }



    fun saveFocusModeData(focusModeData: FocusModeBlocker.FocusModeData) {
        val sharedPreferences =
            context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(focusModeData)

        editor.putString("focus_mode", json)
        editor.apply()

        if (focusModeData.selectedApps.isNotEmpty()) {
            saveFocusModeSelectedApps(focusModeData.selectedApps.toList())
        }
    }


    fun getFocusModeData(): FocusModeBlocker.FocusModeData {

        val sharedPreferences =
            context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("focus_mode", null)

        val data = if (json.isNullOrEmpty()) {
            FocusModeBlocker.FocusModeData()
        } else {
            val type = object : TypeToken<FocusModeBlocker.FocusModeData>() {}.type
            try {
                gson.fromJson<FocusModeBlocker.FocusModeData>(json, type) ?: FocusModeBlocker.FocusModeData()
            } catch (e: Exception) {
                FocusModeBlocker.FocusModeData()
            }
        }
        
        data.selectedApps = getFocusModeSelectedApps().toHashSet()

        // Auto-expire session if end time has passed
        if (data.isTurnedOn && data.endTime > 0 && System.currentTimeMillis() >= data.endTime) {
            val expiredData = FocusModeBlocker.FocusModeData(
                isTurnedOn = false,
                endTime = 0L,
                modeType = data.modeType,
                selectedApps = data.selectedApps
            )
            saveFocusModeData(expiredData)
            completeFocusSession()
            return expiredData
        }

        return data
    }
    
    fun saveFocusSessionStartTime(startTime: Long, endTime: Long) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putLong("focus_session_start", startTime)
            .putLong("focus_session_end", endTime)
            .apply()
    }
    
    fun stopFocusSession(isCancelled: Boolean = false) = synchronized(rulesLock) {
        val currentData = getFocusModeData()
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val startTime = sharedPreferences.getLong("focus_session_start", -1L)
        val endTime = sharedPreferences.getLong("focus_session_end", -1L)

        saveFocusModeData(
            FocusModeBlocker.FocusModeData(
                isTurnedOn = false,
                endTime = 0L,
                modeType = currentData.modeType,
                selectedApps = currentData.selectedApps
            )
        )

        if (startTime != -1L && endTime != -1L) {
            val isEarly = isCancelled || (System.currentTimeMillis() < endTime)
            if (isEarly) {
                sharedPreferences.edit()
                    .remove("focus_session_start")
                    .remove("focus_session_end")
                    .apply()

                try {
                    context.contentResolver.notifyChange(GuardianContract.URI_FOCUS, null)
                } catch (_: Exception) {
                }

                val targetPackages = listOf("com.alhaq.amniquest", "com.alhaq.amniquest.debug")
                for (pkg in targetPackages) {
                    val abortIntent = Intent(GuardianContract.ACTION_FOCUS_ABORTED).apply {
                        setPackage(pkg)
                    }
                    try {
                        context.sendBroadcast(abortIntent, GuardianContract.PERMISSION_GUARDIAN_IPC)
                    } catch (_: Exception) {
                    }
                }
            } else {
                completeFocusSession()
            }
        }
    }

    fun completeFocusSession() = synchronized(rulesLock) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val startTime = sharedPreferences.getLong("focus_session_start", -1L)
        val endTime = sharedPreferences.getLong("focus_session_end", -1L)

        if (startTime != -1L && endTime != -1L) {
            val durationMinutes = ((endTime - startTime) / 60000L).toInt()
            // Record the completed focus session
            BlockingStatsManager.getInstance(context).recordFocusSession(startTime, endTime)

            // Clear the session data
            sharedPreferences.edit()
                .remove("focus_session_start")
                .remove("focus_session_end")
                .apply()

            try {
                context.contentResolver.notifyChange(GuardianContract.URI_FOCUS, null)
            } catch (_: Exception) {
            }

            val targetPackages = listOf("com.alhaq.amniquest", "com.alhaq.amniquest.debug")
            for (pkg in targetPackages) {
                val intent = Intent(GuardianContract.ACTION_FOCUS_COMPLETED).apply {
                    setPackage(pkg)
                    putExtra(GuardianContract.EXTRA_DURATION_MINUTES, durationMinutes)
                }
                try {
                    context.sendBroadcast(intent, GuardianContract.PERMISSION_GUARDIAN_IPC)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun saveFocusModeSelectedApps(appList: List<String>) {
        val sharedPreferences =
            context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(appList)

        editor.putString("selected_apps", json)
        editor.apply()
    }

    fun getFocusModeSelectedApps(): List<String> {
        val sharedPreferences =
            context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("selected_apps", null)

        if (json.isNullOrEmpty()) return listOf()

        val type =
            object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * Emergency / Always Whitelisted apps that are NEVER blocked under any focus session.
     */
    fun getAlwaysWhitelistedApps(): List<String> {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("always_whitelisted_apps", null)
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlwaysWhitelistedApps(apps: List<String>) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString("always_whitelisted_apps", Gson().toJson(apps))
            .apply()
    }

    /**
     * Focus Mode Strictness:
     * 0 = Standard (Mindful Pause, allows early stop / warning countdown)
     * 1 = Deep Focus (Strict Lock, cannot stop early / zero bypass)
     */
    fun getFocusModeStrictness(): Int {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("focus_strictness", 0)
    }

    fun setFocusModeStrictness(strictness: Int) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("focus_strictness", strictness).apply()
    }

    /**
     * Configured duration presets for Home Screen Widget and Quick Focus sheet (in minutes).
     * Defaults to (15, 30, 60).
     */
    fun getQuickFocusDurationPresets(): Triple<Int, Int, Int> {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val p1 = sharedPreferences.getInt("quick_focus_preset_1", 15)
        val p2 = sharedPreferences.getInt("quick_focus_preset_2", 30)
        val p3 = sharedPreferences.getInt("quick_focus_preset_3", 60)
        return Triple(p1, p2, p3)
    }

    fun saveQuickFocusDurationPresets(p1: Int, p2: Int, p3: Int) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putInt("quick_focus_preset_1", p1)
            .putInt("quick_focus_preset_2", p2)
            .putInt("quick_focus_preset_3", p3)
            .apply()
    }

    fun isFocusModeEarlyStopAllowed(): Boolean {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("allow_early_stop", true)
    }

    fun setFocusModeEarlyStopAllowed(allowed: Boolean) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("allow_early_stop", allowed).apply()
    }

    fun saveKeywordBlockerIgnoredApps(appList: List<String>) {
        val sharedPreferences =
            context.getSharedPreferences("Keyword_blocker_ignored_apps", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()

        val json = gson.toJson(appList)

        editor.putString("selected_apps", json)
        editor.apply()
    }

    fun getKeywordBlockerIgnoredApps(): List<String> {
        val sharedPreferences =
            context.getSharedPreferences("Keyword_blocker_ignored_apps", Context.MODE_PRIVATE)
        val gson = Gson()

        val json = sharedPreferences.getString("selected_apps", null)

        if (json.isNullOrEmpty()) return listOf()

        val type =
            object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }


    fun setOverlayApps(selectedApps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("overlay_apps", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("apps", selectedApps).apply()
    }

    fun getOverlayApps(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("overlay_apps", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("apps", emptySet()) ?: emptySet()
    }

    fun loadGrayScaleApps(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("grayscale", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("apps", emptySet()) ?: emptySet()
    }

    fun saveGrayScaleApps(apps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("grayscale", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("apps", apps).apply()
    }

    // Auto-block categories for newly installed apps
    fun getAutoBlockCategories(): Set<String> {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("auto_block_categories", emptySet()) ?: emptySet()
    }

    fun setAutoBlockCategories(categories: Set<String>) {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("auto_block_categories", categories).apply()
    }

    fun isAutoBlockEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("auto_block_enabled", false)
    }

    fun setAutoBlockEnabled(enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("auto_block_enabled", enabled).apply()
    }

    fun loadAppBlockLists(): MutableMap<String, MutableSet<String>> {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("named_block_lists", null)
        if (json.isNullOrEmpty()) return mutableMapOf()

        val type = object : TypeToken<MutableMap<String, MutableSet<String>>>() {}.type
        return runCatching {
            Gson().fromJson<MutableMap<String, MutableSet<String>>>(json, type) ?: mutableMapOf()
        }.getOrElse {
            Log.e("SavedPreferencesLoader", "Failed to load named block lists", it)
            mutableMapOf()
        }
    }

    fun saveAppBlockLists(lists: MutableMap<String, MutableSet<String>>) {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(lists)
        sharedPreferences.edit().putString("named_block_lists", json).apply()

        // Keep legacy blocked_apps in sync as a union for compatibility.
        val merged = lists.values.flatten().toSet()
        saveBlockedApps(merged)
    }

    fun addPackageToBlockList(listName: String, packageName: String) {
        val lists = loadAppBlockLists()
        val selectedList = lists.getOrPut(listName) { mutableSetOf() }
        selectedList.add(packageName)
        saveAppBlockLists(lists)
    }

    fun loadAppBlockerScheduleRules(): MutableList<AppBlockScheduleRule> {
        cachedScheduleRulesSnapshot?.let { return it.toMutableList() }

        synchronized(rulesLock) {
            cachedScheduleRulesSnapshot?.let { return it.toMutableList() }

            val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
            val json = sharedPreferences.getString("schedule_rules", null)
            if (json.isNullOrEmpty()) {
                cachedScheduleRulesSnapshot = emptyList()
                return mutableListOf()
            }

            val type = object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type
            val sanitizedList = runCatching {
                val rawList = Gson().fromJson<MutableList<AppBlockScheduleRule>>(json, type) ?: mutableListOf()
                val legacyDummyIds = setOf(
                    "reel_blocker_default",
                    "website_blocker_default",
                    "keyword_blocker_default",
                    "focus_mode_default",
                    "app_blocker_direct_default"
                )
                rawList.map { it.sanitize() }
                    .filterNot { rule ->
                        legacyDummyIds.contains(rule.id) ||
                        legacyDummyIds.contains(rule.groupId) ||
                        (rule.blockerType == BlockerType.APP && rule.title.startsWith("Block Always • ") && rule.targets.isEmpty() && rule.targetWebsites.isEmpty() && rule.targetKeywords.isEmpty())
                    }
            }.getOrElse {
                Log.e("SavedPreferencesLoader", "Failed to load app blocker schedule rules", it)
                emptyList()
            }

            cachedScheduleRulesSnapshot = sanitizedList
            return sanitizedList.toMutableList()
        }
    }

    fun saveAppBlockerScheduleRules(rules: MutableList<AppBlockScheduleRule>) {
        val immutableSnapshot = rules.map { it.sanitize() }
        synchronized(rulesLock) {
            cachedScheduleRulesSnapshot = immutableSnapshot
            val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
            val json = Gson().toJson(immutableSnapshot)
            sharedPreferences.edit().putString("schedule_rules", json).apply()
        }
        runCatching {
            AmniShieldRuleDatabase.getInstance(context)
                .syncAppBlockScheduleRules(immutableSnapshot)
        }
    }

    fun refreshScheduleRulesSnapshot(): List<AppBlockScheduleRule> {
        invalidateScheduleRulesCache()
        return loadAppBlockerScheduleRules().toList()
    }

    fun invalidateScheduleRulesCache() {
        Companion.invalidateScheduleRulesCache()
    }

    fun upsertAppBlockerScheduleRule(rule: AppBlockScheduleRule) = synchronized(rulesLock) {
        val rules = loadAppBlockerScheduleRules()
        val existing = rules.indexOfFirst { it.id == rule.id }
        if (existing >= 0) {
            rules[existing] = rule
        } else {
            rules.add(rule)
        }
        saveAppBlockerScheduleRules(rules)
    }

    fun removeAppBlockerScheduleRule(ruleId: String) = synchronized(rulesLock) {
        val rules = loadAppBlockerScheduleRules()
        rules.removeAll { it.id == ruleId }
        saveAppBlockerScheduleRules(rules)
    }

    fun removeAppBlockerScheduleGroup(groupId: String): Int = synchronized(rulesLock) {
        val rules = loadAppBlockerScheduleRules()
        val before = rules.size
        rules.removeAll { it.groupId == groupId }
        if (rules.size != before) {
            saveAppBlockerScheduleRules(rules)
        }
        return@synchronized before - rules.size
    }



    protected open fun getPremiumPrefs(): android.content.SharedPreferences {
        return injectedPremiumPrefs ?: context.getSharedPreferences("premium_state", Context.MODE_PRIVATE)
    }

    fun isPremiumUser(): Boolean {
        return getPremiumPrefs().getBoolean("is_premium", false)
    }

    fun setPremiumUser(enabled: Boolean) {
        getPremiumPrefs().edit().putBoolean("is_premium", enabled).apply()
    }

    fun getLicenseKey(): String? {
        return getPremiumPrefs().getString("license_key", null)
    }

    fun getLicenseEmail(): String? {
        return getPremiumPrefs().getString("license_email", null)
    }

    fun saveLicenseKey(email: String, licenseKey: String) {
        getPremiumPrefs().edit()
            .putString("license_email", email)
            .putString("license_key", licenseKey)
            .apply()
    }

    fun clearLicenseKey() {
        getPremiumPrefs().edit()
            .remove("license_email")
            .remove("license_key")
            .remove("user_id")
            .remove("user_email")
            .apply()
    }

    fun getDeviceId(): String {
        val prefs = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("unique_device_id", null)
        if (deviceId.isNullOrEmpty()) {
            deviceId = "android_" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString("unique_device_id", deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceLabel(): String {
        val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    fun saveUserProfile(userId: String, email: String, displayName: String? = null) {
        getPremiumPrefs().edit()
            .putString("user_id", userId)
            .putString("user_email", email)
            .putString("display_name", displayName ?: email.substringBefore("@"))
            .putLong("last_profile_sync", System.currentTimeMillis())
            .apply()
    }

    fun getUserId(): String? {
        return getPremiumPrefs().getString("user_id", null)
    }

    fun getUserEmail(): String? {
        return getPremiumPrefs().getString("user_email", getLicenseEmail())
    }

    fun getDisplayName(): String {
        return getPremiumPrefs().getString("display_name", null)
            ?: getUserEmail()?.substringBefore("@")
            ?: "AmniShield User"
    }

    fun getLastPremiumReminder(): Long {
        return getPremiumPrefs().getLong("last_premium_reminder", 0L)
    }

    fun setLastPremiumReminder(timestamp: Long) {
        getPremiumPrefs().edit().putLong("last_premium_reminder", timestamp).apply()
    }


    protected open fun getFounderPassPrefs(): android.content.SharedPreferences {
        return injectedFounderPassPrefs ?: context.getSharedPreferences("founder_pass_state", Context.MODE_PRIVATE)
    }

    fun isFounderEligible(): Boolean {
        return getFounderPassPrefs().getBoolean("is_eligible", false)
    }

    fun setFounderEligible(eligible: Boolean) {
        getFounderPassPrefs().edit()
            .putBoolean("is_eligible", eligible)
            .apply()
    }

    fun isFounderPassClaimed(): Boolean {
        return getFounderPassPrefs().getBoolean("is_claimed", false)
    }

    fun isFounderPassActive(): Boolean {
        val prefs = getFounderPassPrefs()
        if (!prefs.getBoolean("is_claimed", false)) return false
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (expiresAt <= 0L) return false
        return verifyFounderPassMonotonicExpiry()
    }

    fun grantFounderPass(
        supporterName: String?,
        optInPublic: Boolean,
        durationDays: Int = 90
    ) {
        val nowWall = wallClockProvider()
        val nowElapsed = elapsedRealtimeProvider()
        val expiryWall = nowWall + (durationDays.toLong() * 24L * 60L * 60L * 1000L)
        val cleanName = supporterName?.trim()?.takeIf { it.isNotEmpty() } ?: "Anonymous Supporter"

        getFounderPassPrefs().edit()
            .putBoolean("is_claimed", true)
            .putLong("granted_at", nowWall)
            .putLong("expires_at", expiryWall)
            .putLong("last_observed_elapsed_realtime", nowElapsed)
            .putLong("last_observed_wall_clock", nowWall)
            .putLong("accumulated_uptime_ms", 0L)
            .putString("supporter_name", cleanName)
            .putBoolean("opted_in_public", optInPublic)
            .putBoolean("badge_unlocked", true)
            .putBoolean("theme_unlocked", true)
            .apply()
    }

    fun verifyFounderPassMonotonicExpiry(): Boolean {
        val prefs = getFounderPassPrefs()
        if (!prefs.getBoolean("is_claimed", false)) return false
        val grantedAt = prefs.getLong("granted_at", 0L)
        val expiresAt = prefs.getLong("expires_at", 0L)
        if (grantedAt <= 0L || expiresAt <= 0L) return false

        val currentWall = wallClockProvider()
        val currentElapsed = elapsedRealtimeProvider()
        val lastObservedWall = prefs.getLong("last_observed_wall_clock", grantedAt)
        val lastObservedElapsed = prefs.getLong("last_observed_elapsed_realtime", currentElapsed)
        var accumulatedUptime = prefs.getLong("accumulated_uptime_ms", 0L)

        // Monotonic hardware uptime progression check
        if (currentElapsed >= lastObservedElapsed) {
            accumulatedUptime += (currentElapsed - lastObservedElapsed)
        } else {
            // Device rebooted: elapsedRealtime reset to 0
            accumulatedUptime += currentElapsed
        }

        // Clock tampering check: if currentWall was moved backwards significantly (>5 min)
        if (currentWall < lastObservedWall - 300_000L) {
            return false
        }

        val totalGrantDuration = expiresAt - grantedAt
        if (accumulatedUptime > totalGrantDuration || currentWall > expiresAt) {
            return false
        }

        // Update tracking state
        prefs.edit()
            .putLong("last_observed_elapsed_realtime", currentElapsed)
            .putLong("last_observed_wall_clock", currentWall)
            .putLong("accumulated_uptime_ms", accumulatedUptime)
            .apply()

        return true
    }

    fun getFounderSupporterName(): String {
        return getFounderPassPrefs().getString("supporter_name", "") ?: ""
    }

    fun isFounderBadgeUnlocked(): Boolean {
        return getFounderPassPrefs().getBoolean("badge_unlocked", false)
    }

    fun isFounderThemeUnlocked(): Boolean {
        return getFounderPassPrefs().getBoolean("theme_unlocked", false)
    }

    fun isFounderOptedInPublic(): Boolean {
        return getFounderPassPrefs().getBoolean("opted_in_public", false)
    }

    fun getFounderPassExpiry(): Long {
        return getFounderPassPrefs().getLong("expires_at", 0L)
    }

    protected open fun getCompassionateAccessPrefs(): android.content.SharedPreferences {
        return injectedCompassionatePrefs ?: context.getSharedPreferences("compassionate_access", Context.MODE_PRIVATE)
    }

    fun saveCompassionateAccessGrant(
        appId: String,
        userName: String,
        email: String?,
        grantedAt: Long,
        expiresAt: Long,
        status: String = "pending_review",
        licenseKey: String? = null
    ) {
        val nowElapsed = elapsedRealtimeProvider()
        getCompassionateAccessPrefs().edit()
            .putString("app_id", appId)
            .putString("user_name", userName)
            .putString("email", email.orEmpty())
            .putLong("granted_at", grantedAt)
            .putLong("expires_at", expiresAt)
            .putString("status", status)
            .putString("license_key", licenseKey.orEmpty())
            .putLong("granted_elapsed_realtime", nowElapsed)
            .putLong("last_observed_elapsed_realtime", nowElapsed)
            .putLong("last_observed_wall_clock", grantedAt)
            .putLong("accumulated_uptime_ms", 0L)
            .apply()
    }

    fun getCompassionateAccessAppId(): String {
        return getCompassionateAccessPrefs().getString("app_id", "") ?: ""
    }

    fun getCompassionateAccessEmail(): String {
        return getCompassionateAccessPrefs().getString("email", "") ?: ""
    }

    fun getCompassionateAccessUserName(): String {
        return getCompassionateAccessPrefs().getString("user_name", "") ?: ""
    }

    fun getCompassionateAccessStatus(): String {
        return getCompassionateAccessPrefs().getString("status", "pending_review") ?: "pending_review"
    }

    fun setCompassionateAccessStatus(status: String) {
        getCompassionateAccessPrefs().edit().putString("status", status).apply()
    }

    fun getCompassionateAccessExpiry(): Long {
        return getCompassionateAccessPrefs().getLong("expires_at", 0L)
    }

    fun setCompassionateAccessExpiry(expiry: Long) {
        getCompassionateAccessPrefs().edit().putLong("expires_at", expiry).apply()
    }

    fun getCompassionateAccessLicenseKey(): String? {
        val key = getCompassionateAccessPrefs().getString("license_key", "")
        return if (key.isNullOrBlank()) null else key
    }

    fun setCompassionateAccessLicenseKey(key: String) {
        getCompassionateAccessPrefs().edit().putString("license_key", key).apply()
    }

    fun clearCompassionateAccessGrant() {
        getCompassionateAccessPrefs().edit().clear().apply()
    }

    fun cancelCompassionateAccess() {
        getCompassionateAccessPrefs().edit()
            .putString("status", "cancelled")
            .putLong("expires_at", 0L)
            .remove("license_key")
            .apply()
    }

    /**
     * Anti-Clock Manipulation Guard & Monotonic Progression:
     * - Uses SystemClock.elapsedRealtime() to track monotonic hardware uptime across boots.
     * - Detects backwards clock manipulation (wall clock set back by > 5 minutes).
     * - Automatically graduates a "pending_review" grant to "verified_active" (1-year pass)
     *   once 7 days have elapsed without being flagged or cancelled.
     */
    fun updateCompassionateUptimeAndVerify(): Boolean {
        val prefs = getCompassionateAccessPrefs()
        val appId = prefs.getString("app_id", null) ?: return false
        val status = prefs.getString("status", "pending_review") ?: "pending_review"

        if (status == "cancelled" || status == "flagged_email") {
            return false
        }

        val grantedAt = prefs.getLong("granted_at", 0L)
        if (grantedAt <= 0L) return false

        val currentWall = wallClockProvider()
        val currentElapsed = elapsedRealtimeProvider()
        val lastObservedWall = prefs.getLong("last_observed_wall_clock", grantedAt)
        val lastObservedElapsed = prefs.getLong("last_observed_elapsed_realtime", currentElapsed)
        var accumulatedUptime = prefs.getLong("accumulated_uptime_ms", 0L)

        // 1. Detect backwards clock manipulation (e.g. manual rollback > 5 minutes)
        val isClockTurnedBack = currentWall < (lastObservedWall - 5 * 60 * 1000L)

        // 2. Track monotonic elapsed uptime
        val deltaElapsed = if (currentElapsed >= lastObservedElapsed) {
            currentElapsed - lastObservedElapsed
        } else {
            // Device rebooted; currentElapsed is uptime since boot
            currentElapsed
        }
        accumulatedUptime += maxOf(0L, deltaElapsed)

        // 3. Monotonically advance observed wall clock
        val newObservedWall = if (isClockTurnedBack) lastObservedWall else maxOf(lastObservedWall, currentWall)

        val editor = prefs.edit()
            .putLong("last_observed_elapsed_realtime", currentElapsed)
            .putLong("last_observed_wall_clock", newObservedWall)
            .putLong("accumulated_uptime_ms", accumulatedUptime)

        // 4. Seven-day grace window check (7 days = 604,800,000 ms)
        val gracePeriodMs = 7L * 24 * 60 * 60 * 1000L
        val wallClockElapsed = newObservedWall - grantedAt

        // Auto-graduate from pending_review to verified_active if 7 days have elapsed
        if (status == "pending_review") {
            if (wallClockElapsed >= gracePeriodMs || accumulatedUptime >= gracePeriodMs) {
                val fullExpiry = grantedAt + (372L * 24 * 60 * 60 * 1000L)
                editor.putString("status", "verified_active")
                editor.putLong("expires_at", fullExpiry)
            }
        }

        editor.apply()

        val currentExpiry = prefs.getLong("expires_at", 0L)
        return currentExpiry > currentWall && !isClockTurnedBack
    }

    private fun getHomePrefs(): android.content.SharedPreferences {
        return context.getSharedPreferences("home_dashboard", Context.MODE_PRIVATE)
    }

    fun isHomeWelcomeCardVisible(): Boolean {
        return getHomePrefs().getBoolean("show_welcome_card", true)
    }

    fun setHomeWelcomeCardVisible(visible: Boolean) {
        getHomePrefs().edit().putBoolean("show_welcome_card", visible).apply()
    }

    private fun getFeatureTogglesPrefs(): android.content.SharedPreferences {
        return context.getSharedPreferences("feature_toggles", Context.MODE_PRIVATE)
    }

    fun isAppBlockerFeatureEnabled(default: Boolean = false): Boolean {
        return getFeatureTogglesPrefs().getBoolean("app_blocker_enabled", default)
    }

    fun setAppBlockerFeatureEnabled(enabled: Boolean, updateManual: Boolean = true) {
        val editor = getFeatureTogglesPrefs().edit().putBoolean("app_blocker_enabled", enabled)
        if (updateManual) {
            editor.putBoolean("app_blocker_enabled_manual", enabled)
        }
        editor.apply()
    }

    fun isKeywordBlockerFeatureEnabled(default: Boolean = false): Boolean {
        val prefs = getFeatureTogglesPrefs()
        if (prefs.contains("keyword_blocker_enabled")) {
            return prefs.getBoolean("keyword_blocker_enabled", default)
        }
        val hasKeywords = loadBlockedKeywords().isNotEmpty() || isKeywordBlockerAdultPackEnabled()
        return if (hasKeywords) true else default
    }

    fun setKeywordBlockerFeatureEnabled(enabled: Boolean, updateManual: Boolean = true) {
        val editor = getFeatureTogglesPrefs().edit().putBoolean("keyword_blocker_enabled", enabled)
        if (updateManual) {
            editor.putBoolean("keyword_blocker_enabled_manual", enabled)
        }
        editor.apply()
    }

    // ==================== Usage Tracker & Doom Scrolling ====================
    companion object {
        private val rulesLock = Any()
        private val statsLock = Any()
        private val alwaysOnShieldsLock = Any()

        @Volatile
        private var cachedScheduleRulesSnapshot: List<AppBlockScheduleRule>? = null

        fun invalidateScheduleRulesCache() {
            cachedScheduleRulesSnapshot = null
        }

        const val OVERLAY_MODE_COUNT = 0
        const val OVERLAY_MODE_TIME = 1
        const val OVERLAY_MODE_BOTH = 2

        const val MODE_STANDALONE = 0
        const val MODE_PERSONAL_SYNC = 1
        const val MODE_CONSOLE_ENFORCED = 2

        val DEFAULT_REELS_OVERLAY_APPS = setOf(
            "com.instagram.android",
            "com.myinsta.android",
            "com.instagram.lite",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.morphe.android.youtube",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
            "com.zhiliao.musically.go",
            "com.reddit.frontpage",
            "com.twitter.android"
        )

        // PIN Security, Cooldown & Reset Engine
        const val APP_LOCK_MODE_DEVICE = "DEVICE_LOCK"
        const val APP_LOCK_MODE_APP_PIN = "APP_PIN"
        const val APP_LOCK_MODE_GLOBAL_PIN = "GLOBAL_PIN"

        const val MIN_COOLDOWN_MINUTES = 2
        const val DEFAULT_COOLDOWN_MINUTES = 5
        const val PREF_PIN_RESET_COOLDOWN_MINS = "pin_reset_cooldown_mins"
        const val PREF_EMERGENCY_ACCESS_COOLDOWN_MINS = "emergency_access_cooldown_mins"
        const val PREF_PIN_RESET_REQUESTED_TIMESTAMP = "pin_reset_requested_timestamp"
        const val PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP = "emergency_override_requested_timestamp"
        const val PREF_EMERGENCY_OVERRIDE_ACTIVE = "is_emergency_override_active"
        const val PREF_EMERGENCY_WINDOW_EXPIRY_TIMESTAMP = "emergency_window_expiry_timestamp"
        const val EMERGENCY_WINDOW_DURATION_MS = 10 * 60 * 1000L // 10-minute temporary unlock window
    }

    fun isUsageTrackerFeatureEnabled(default: Boolean = true): Boolean {
        return getFeatureTogglesPrefs().getBoolean("usage_tracker_enabled", default)
    }

    fun setUsageTrackerFeatureEnabled(enabled: Boolean) {
        getFeatureTogglesPrefs().edit().putBoolean("usage_tracker_enabled", enabled).apply()
    }

    /**
     * Master switch for global app usage screen-time recording.
     * When disabled, app usage stats and charts across the UI are instantly blurred.
     */
    fun isAppUsageTrackingEnabled(default: Boolean = true): Boolean {
        return getFeatureTogglesPrefs().getBoolean("app_usage_tracking_enabled", default)
    }

    fun setAppUsageTrackingEnabled(enabled: Boolean) {
        getFeatureTogglesPrefs().edit().putBoolean("app_usage_tracking_enabled", enabled).apply()
    }

    /**
     * Master switch for Website Usage Data Tracking.
     * When enabled, time spent on web domains in supported browsers is measured 100% locally on-device.
     * When disabled, website tracking stops completely with zero logging.
     */
    fun isWebsiteUsageTrackingEnabled(default: Boolean = true): Boolean {
        return getFeatureTogglesPrefs().getBoolean("website_usage_tracking_enabled", default)
    }

    fun setWebsiteUsageTrackingEnabled(enabled: Boolean) {
        getFeatureTogglesPrefs().edit().putBoolean("website_usage_tracking_enabled", enabled).apply()
    }

    fun loadIgnoredWebDomains(): Set<String> {
        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("ignored_domains", emptySet()) ?: emptySet()
    }

    fun saveIgnoredWebDomains(domains: Set<String>) {
        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("ignored_domains", domains).apply()
    }

    fun loadWebsiteUsageStats(): Map<String, Long> = synchronized(statsLock) {
        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDate = sharedPreferences.getString("domain_stats_date", "")
        if (savedDate != today) {
            sharedPreferences.edit()
                .putString("domain_stats_date", today)
                .remove("domain_stats_today")
                .apply()
            return@synchronized emptyMap()
        }

        val json = sharedPreferences.getString("domain_stats_today", null)
        if (json.isNullOrEmpty()) {
            return@synchronized emptyMap()
        }
        return@synchronized try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            val map: Map<String, Long>? = Gson().fromJson(json, type)
            // Defensive cleanup: purge any legacy placeholder map if present
            if (map != null && map.size == 4 && map["youtube.com"] == 2550000L && map["reddit.com"] == 1455000L) {
                sharedPreferences.edit().remove("domain_stats_today").apply()
                emptyMap()
            } else {
                map ?: emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun recordWebsiteUsage(domain: String, durationMillis: Long) = synchronized(statsLock) {
        if (durationMillis <= 0) return@synchronized
        if (!isWebsiteUsageTrackingEnabled()) return@synchronized
        if (loadIgnoredWebDomains().contains(domain)) return@synchronized

        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val savedDate = sharedPreferences.getString("domain_stats_date", "")
        if (savedDate != today) {
            sharedPreferences.edit()
                .putString("domain_stats_date", today)
                .remove("domain_stats_today")
                .apply()
        }

        val current = loadWebsiteUsageStats().toMutableMap()
        current[domain] = (current[domain] ?: 0L) + durationMillis
        sharedPreferences.edit().putString("domain_stats_today", Gson().toJson(current)).apply()
    }

    /**
     * Master switch for Reels & Shorts doom-scrolling tracking.
     * When enabled, scrolling events in short-form video surfaces are tracked and synced to ReelsMetrics.
     */
    fun isReelsTrackingEnabled(default: Boolean = true): Boolean {
        return getFeatureTogglesPrefs().getBoolean("reels_tracking_enabled", default)
    }

    fun setReelsTrackingEnabled(enabled: Boolean) {
        getFeatureTogglesPrefs().edit().putBoolean("reels_tracking_enabled", enabled).apply()
    }

    /**
     * Floating doom-scrolling counter overlay switch.
     */
    fun isReelsOverlayCounterEnabled(default: Boolean = true): Boolean {
        return context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
            .getBoolean("reels_overlay_counter_enabled", default)
    }

    fun setReelsOverlayCounterEnabled(enabled: Boolean) {
        context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
            .edit().putBoolean("reels_overlay_counter_enabled", enabled).apply()
    }

    /**
     * Selected apps on which the floating doom-scrolling counter overlay should appear.
     */
    fun getReelsOverlayApps(): Set<String> {
        val prefs = context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
        return prefs.getStringSet("reels_overlay_target_apps", null) ?: DEFAULT_REELS_OVERLAY_APPS
    }

    fun setReelsOverlayApps(apps: Set<String>) {
        context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
            .edit().putStringSet("reels_overlay_target_apps", apps).apply()
    }

    /**
     * Display mode for the floating overlay (0: Count, 1: Time, 2: Both)
     */
    fun getOverlayCounterDisplayMode(default: Int = OVERLAY_MODE_BOTH): Int {
        return context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
            .getInt("overlay_counter_mode", default)
    }

    fun setOverlayCounterDisplayMode(mode: Int) {
        context.getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
            .edit().putInt("overlay_counter_mode", mode).apply()
    }

    fun isFocusModeFeatureEnabled(default: Boolean = false): Boolean {
        return getFeatureTogglesPrefs().getBoolean("focus_mode_enabled", default)
    }

    fun setFocusModeFeatureEnabled(enabled: Boolean, updateManual: Boolean = true) {
        val editor = getFeatureTogglesPrefs().edit().putBoolean("focus_mode_enabled", enabled)
        if (updateManual) {
            editor.putBoolean("focus_mode_enabled_manual", enabled)
        }
        editor.apply()
    }

    fun getProfileGoalMinutes(default: Int = 120): Int {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getInt("profile_goal_minutes", default)
    }

    // ==================== App Launch Limit Rules ====================

    fun loadAppLaunchLimitRules(): List<AppLaunchLimitRule> = synchronized(rulesLock) {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("launch_limit_rules", null)
        if (json.isNullOrEmpty()) return@synchronized emptyList()

        return@synchronized try {
            val type = object : TypeToken<List<AppLaunchLimitRule>>() {}.type
            val rawList: List<AppLaunchLimitRule> = Gson().fromJson(json, type) ?: emptyList()
            rawList.map { it.sanitize() }
        } catch (e: Exception) {
            Log.e("SavedPreferencesLoader", "Error loading launch limit rules", e)
            emptyList()
        }
    }

    fun saveAppLaunchLimitRules(rules: List<AppLaunchLimitRule>) {
        val sanitized = rules.map { it.sanitize() }
        synchronized(rulesLock) {
            val sharedPreferences =
                context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
            val json = Gson().toJson(sanitized)
            sharedPreferences.edit().putString("launch_limit_rules", json).apply()
        }
        runCatching {
            AmniShieldRuleDatabase.getInstance(context)
                .syncLaunchLimitRules(sanitized)
        }
    }

    fun addAppLaunchLimitRule(rule: AppLaunchLimitRule) = synchronized(rulesLock) {
        val sanitized = rule.sanitize()
        val rules = loadAppLaunchLimitRules().toMutableList()
        rules.removeAll { it.packageName == sanitized.packageName }
        rules.add(sanitized)
        saveAppLaunchLimitRules(rules)
    }

    fun removeAppLaunchLimitRule(packageName: String) = synchronized(rulesLock) {
        val rules = loadAppLaunchLimitRules().toMutableList()
        rules.removeAll { it.packageName == packageName }
        saveAppLaunchLimitRules(rules)
    }

    fun getAppLaunchLimitRule(packageName: String): AppLaunchLimitRule? {
        return loadAppLaunchLimitRules().firstOrNull { it.packageName == packageName }?.sanitize()
    }

    // ==================== Launch Count Tracking (Per Period) ====================
    // Tracks current launch counts with period information to reset when period changes

    fun trackAppLaunch(packageName: String) = synchronized(statsLock) {
        if (packageName.isBlank()) return@synchronized
        val now = wallClockProvider()
        recordUniversalAppLaunch(packageName, now)

        val rule = getAppLaunchLimitRule(packageName) ?: return@synchronized
        val launchData = loadLaunchCountMap().toMutableMap()
        val current = launchData[packageName]

        launchData[packageName] = if (current == null || isLaunchDataExpired(current, rule, now)) {
            LaunchCountData(count = 1, firstLaunchTime = now, period = rule.timePeriod.name)
        } else {
            current.copy(count = current.count + 1, period = rule.timePeriod.name)
        }

        saveLaunchCountMap(launchData)
    }

    fun getCurrentLaunchCount(
        packageName: String,
        rule: AppLaunchLimitRule? = getAppLaunchLimitRule(packageName)
    ): Int = synchronized(statsLock) {
        if (rule == null) {
            resetLaunchCount(packageName)
            return@synchronized 0
        }

        val launchData = loadLaunchCountMap()
        val current = launchData[packageName] ?: return@synchronized 0

        if (isLaunchDataExpired(current, rule, System.currentTimeMillis())) {
            resetLaunchCount(packageName)
            return@synchronized 0
        }

        return@synchronized current.count
    }

    fun resetLaunchCount(packageName: String) = synchronized(statsLock) {
        val launchData = loadLaunchCountMap().toMutableMap()
        if (launchData.remove(packageName) != null) {
            saveLaunchCountMap(launchData)
        }
    }

    private fun loadLaunchCountMap(): Map<String, LaunchCountData> {
        val sharedPreferences = context.getSharedPreferences("app_launch_tracking", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("launch_counts", null)
        if (json.isNullOrEmpty()) return emptyMap()

        return try {
            val type = object : TypeToken<Map<String, LaunchCountData>>() {}.type
            Gson().fromJson<Map<String, LaunchCountData>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e("SavedPreferencesLoader", "Error loading launch data", e)
            emptyMap()
        }
    }

    private fun saveLaunchCountMap(data: Map<String, LaunchCountData>) {
        val sharedPreferences = context.getSharedPreferences("app_launch_tracking", Context.MODE_PRIVATE)
        val newJson = Gson().toJson(data)
        sharedPreferences.edit().putString("launch_counts", newJson).apply()
    }

    private fun isLaunchDataExpired(
        launchData: LaunchCountData,
        rule: AppLaunchLimitRule,
        nowMillis: Long
    ): Boolean {
        if (launchData.period != rule.timePeriod.name) return true

        return when (rule.timePeriod) {
            AppLaunchLimitRule.TimePeriod.HOURLY -> {
                nowMillis - launchData.firstLaunchTime >= 60L * 60L * 1000L
            }

            AppLaunchLimitRule.TimePeriod.DAILY -> {
                val first = Calendar.getInstance().apply { timeInMillis = launchData.firstLaunchTime }
                val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
                first.get(Calendar.YEAR) != now.get(Calendar.YEAR) ||
                    first.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
            }

            AppLaunchLimitRule.TimePeriod.WEEKLY -> {
                launchData.firstLaunchTime < getWeeklyWindowStartMillis(nowMillis, rule.dayOfWeek)
            }
        }
    }

    private fun getWeeklyWindowStartMillis(nowMillis: Long, weekStartDay: Int): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        while (calendar.get(Calendar.DAY_OF_WEEK) != weekStartDay) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return calendar.timeInMillis
    }

    fun isKeywordBlockerAdultPackEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences("keyword_blocker_packs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("adult_blocker", false)
    }

    fun setKeywordBlockerAdultPackEnabled(enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("keyword_blocker_packs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("adult_blocker", enabled).apply()
    }

    fun isWebsiteBlockerEnabled(defaultValue: Boolean = false): Boolean {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        if (sharedPreferences.contains("is_enabled")) {
            return sharedPreferences.getBoolean("is_enabled", defaultValue)
        }
        val legacyPreferences = context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
        return legacyPreferences.getBoolean("is_enabled", defaultValue)
    }

    fun setWebsiteBlockerEnabled(enabled: Boolean, updateManual: Boolean = true) {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit().putBoolean("is_enabled", enabled)
        if (updateManual) {
            editor.putBoolean("is_enabled_manual", enabled)
        }
        editor.apply()

        // Keep legacy in sync
        context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_enabled", enabled)
            .apply()
    }



    open fun loadBlockedWebsitesApps(): Set<String> {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        val legacyPreferences = context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
        val activePrefs = if (sharedPreferences.contains("blocked_apps")) sharedPreferences else legacyPreferences
        val hasKey = activePrefs.contains("blocked_apps")
        if (!hasKey) {
            return emptySet()
        }
        return activePrefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun saveBlockedWebsitesApps(apps: Set<String>) {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("blocked_apps", apps).apply()
        // Sync to legacy
        val legacyPreferences = context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
        legacyPreferences.edit().putStringSet("blocked_apps", apps).apply()
    }

    open fun loadBlockedWebsites(): Set<String> {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        val legacyPreferences = context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
        val activePrefs = if (sharedPreferences.contains("blocked_websites")) sharedPreferences else legacyPreferences
        val hasKey = activePrefs.contains("blocked_websites")
        if (!hasKey) {
            return emptySet()
        }
        return activePrefs.getStringSet("blocked_websites", emptySet()) ?: emptySet()
    }

    fun saveBlockedWebsites(websites: Set<String>) {
        val sharedPreferences = context.getSharedPreferences("website_blocker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("blocked_websites", websites).apply()
        // Sync to legacy
        val legacyPreferences = context.getSharedPreferences("social_media_blocker", Context.MODE_PRIVATE)
        legacyPreferences.edit().putStringSet("blocked_websites", websites).apply()
    }

    /**
     * Data class to track launch count with timestamp for period-based reset logic
     */
    data class LaunchCountData(
        val count: Int,
        val firstLaunchTime: Long,
        val period: String // "HOURLY", "DAILY", "WEEKLY"
    )

    private fun checkAndResetReelsStatsDaily(sharedPreferences: SharedPreferences) {
        val today = LocalDate.now().toString()
        val savedDate = sharedPreferences.getString("reels_stats_date", "")
        if (savedDate != today) {
            val editor = sharedPreferences.edit()
            editor.putString("reels_stats_date", today)
            editor.putInt("reels_scrolled_today", 0)
            editor.putLong("reels_watch_time_seconds_today", 0L)
            editor.apply()
        }
    }

    fun getReelsScrolledToday(): Int {
        return ReelsStatsManager.getInstance(context).loadDailyRecord(LocalDate.now().toString()).totalScrolled
    }

    fun incrementReelsScrolled(packageName: String? = null) {
        ReelsStatsManager.getInstance(context).recordReelScroll(packageName)
    }

    fun getReelsWatchTimeSeconds(): Long {
        return ReelsStatsManager.getInstance(context).loadDailyRecord(LocalDate.now().toString()).totalWatchTimeSeconds
    }

    fun addReelsWatchTime(seconds: Long, packageName: String? = null) {
        if (seconds <= 0) return
        ReelsStatsManager.getInstance(context).recordReelWatchTime(packageName, seconds)
    }

    // ==================== PIN Security, Cooldown & Reset Engine ====================

    fun isPinSecurityEnabled(): Boolean {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getBoolean("pin_protection_enabled", false)
    }

    fun setPinSecurityEnabled(enabled: Boolean) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("pin_protection_enabled", enabled).apply()
    }

    fun getPinCode(): String {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getString("profile_pin", "") ?: ""
    }

    fun setPinCode(pin: String) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putString("profile_pin", pin).apply()
        if (pin.isNotEmpty()) {
            val antiPrefs = context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            val useSeparate = antiPrefs.getBoolean("use_separate_anti_uninstall_password", false)
            if (!useSeparate) {
                antiPrefs.edit()
                    .putString("password", PasswordHasher.hash(pin))
                    .apply()
                context.sendBroadcast(
                    Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                        .setPackage(context.packageName)
                )
            }
        }
    }

    fun saveGlobalAntiBypassPin(pin: String, linkAntiUninstall: Boolean = true) {
        val cleanPin = pin.trim()
        if (cleanPin.length < 4) return
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("profile_pin", cleanPin)
            .putBoolean("pin_protection_enabled", true)
            .putBoolean("pin_bypass_lock", true)
            .apply()

        val antiPrefs = context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val useSeparate = antiPrefs.getBoolean("use_separate_anti_uninstall_password", false)
        val currentMode = antiPrefs.getInt("mode", -1)
        val hashedPin = PasswordHasher.hash(cleanPin)

        antiPrefs.edit().apply {
            if (!useSeparate) {
                putString("password", hashedPin)
            }
            putBoolean("is_configuring_blocked", true)
            if (linkAntiUninstall && currentMode != com.alhaq.amnishield.Constants.ANTI_UNINSTALL_TIMED_MODE) {
                putBoolean("is_anti_uninstall_on", true)
                putInt("mode", com.alhaq.amnishield.Constants.ANTI_UNINSTALL_PASSWORD_MODE)
            }
            apply()
        }

        context.sendBroadcast(
            Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                .setPackage(context.packageName)
        )
    }

    fun getAppLockMode(): String {
        val mode = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getString("app_lock_mode", APP_LOCK_MODE_DEVICE)
        return when (mode) {
            APP_LOCK_MODE_APP_PIN, APP_LOCK_MODE_GLOBAL_PIN -> mode
            else -> APP_LOCK_MODE_DEVICE
        }
    }

    fun setAppLockMode(mode: String) {
        val safeMode = when (mode) {
            APP_LOCK_MODE_APP_PIN, APP_LOCK_MODE_GLOBAL_PIN -> mode
            else -> APP_LOCK_MODE_DEVICE
        }
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putString("app_lock_mode", safeMode).apply()
    }

    fun getAppLockCustomPin(): String {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getString("app_lock_custom_pin", "") ?: ""
    }

    fun setAppLockCustomPin(pin: String) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putString("app_lock_custom_pin", pin.trim()).apply()
    }

    fun isAppLockEnabled(): Boolean {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getBoolean("pin_app_lock", false)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("pin_app_lock", enabled).apply()
    }

    fun isBypassPinLockEnabled(): Boolean {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getBoolean("pin_bypass_lock", false)
    }

    fun setBypassPinLockEnabled(enabled: Boolean) {
        val antiUninstallPrefs = context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val currentMode = antiUninstallPrefs.getInt("mode", -1)
        val useSeparate = antiUninstallPrefs.getBoolean("use_separate_anti_uninstall_password", false)
        val globalPin = getPinCode()
        antiUninstallPrefs.edit().apply {
            putBoolean("is_configuring_blocked", enabled)
            if (enabled && globalPin.isNotEmpty() && currentMode != com.alhaq.amnishield.Constants.ANTI_UNINSTALL_TIMED_MODE) {
                if (!useSeparate) {
                    putString("password", PasswordHasher.hash(globalPin))
                }
                putBoolean("is_anti_uninstall_on", true)
                putInt("mode", com.alhaq.amnishield.Constants.ANTI_UNINSTALL_PASSWORD_MODE)
            }
            apply()
        }

        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("pin_bypass_lock", enabled).apply()

        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    // --- PIN Reset Cooldown Settings & State (Hard minimum 2 minutes) ---

    fun getPinResetCooldownMinutes(): Int {
        val mins = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getInt(PREF_PIN_RESET_COOLDOWN_MINS, DEFAULT_COOLDOWN_MINUTES)
        return mins.coerceAtLeast(MIN_COOLDOWN_MINUTES)
    }

    fun setPinResetCooldownMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(MIN_COOLDOWN_MINUTES)
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putInt(PREF_PIN_RESET_COOLDOWN_MINS, safeMinutes).apply()
    }

    fun getPinResetRequestedTimestamp(): Long {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getLong(PREF_PIN_RESET_REQUESTED_TIMESTAMP, 0L)
    }

    fun setPinResetRequestedTimestamp(timestamp: Long) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putLong(PREF_PIN_RESET_REQUESTED_TIMESTAMP, timestamp).apply()
    }

    fun requestPinReset() {
        setPinResetRequestedTimestamp(System.currentTimeMillis())
    }

    fun clearPinResetRequest() {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().remove(PREF_PIN_RESET_REQUESTED_TIMESTAMP).apply()
    }

    fun isPinResetCooldownActive(): Boolean {
        val requestedAt = getPinResetRequestedTimestamp()
        if (requestedAt <= 0L) return false
        val cooldownMs = getPinResetCooldownMinutes() * 60 * 1000L
        return System.currentTimeMillis() < (requestedAt + cooldownMs)
    }

    fun getPinResetRemainingMillis(): Long {
        val requestedAt = getPinResetRequestedTimestamp()
        if (requestedAt <= 0L) return 0L
        val cooldownMs = getPinResetCooldownMinutes() * 60 * 1000L
        val remaining = (requestedAt + cooldownMs) - System.currentTimeMillis()
        return remaining.coerceAtLeast(0L)
    }

    fun isPinResetReady(): Boolean {
        val requestedAt = getPinResetRequestedTimestamp()
        return requestedAt > 0L && getPinResetRemainingMillis() <= 0L
    }

    // --- Emergency Access Cooldown Settings & State (Hard minimum 2 minutes) ---

    fun getEmergencyAccessCooldownMinutes(): Int {
        val mins = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getInt(PREF_EMERGENCY_ACCESS_COOLDOWN_MINS, DEFAULT_COOLDOWN_MINUTES)
        return mins.coerceAtLeast(MIN_COOLDOWN_MINUTES)
    }

    fun setEmergencyAccessCooldownMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(MIN_COOLDOWN_MINUTES)
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putInt(PREF_EMERGENCY_ACCESS_COOLDOWN_MINS, safeMinutes).apply()
    }

    fun getEmergencyOverrideRequestedTimestamp(): Long {
        return context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .getLong(PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP, 0L)
    }

    fun setEmergencyOverrideRequestedTimestamp(timestamp: Long) {
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit().putLong(PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP, timestamp).apply()
    }

    fun requestEmergencyOverride() {
        setEmergencyOverrideRequestedTimestamp(System.currentTimeMillis())
    }

    fun clearEmergencyOverrideRequest() {
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit().remove(PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP).apply()
    }

    fun isEmergencyOverrideCooldownActive(): Boolean {
        val requestedAt = getEmergencyOverrideRequestedTimestamp()
        if (requestedAt <= 0L) return false
        val cooldownMs = getEmergencyAccessCooldownMinutes() * 60 * 1000L
        return System.currentTimeMillis() < (requestedAt + cooldownMs)
    }

    fun getEmergencyOverrideRemainingMillis(): Long {
        val requestedAt = getEmergencyOverrideRequestedTimestamp()
        if (requestedAt <= 0L) return 0L
        val cooldownMs = getEmergencyAccessCooldownMinutes() * 60 * 1000L
        val remaining = (requestedAt + cooldownMs) - System.currentTimeMillis()
        return remaining.coerceAtLeast(0L)
    }

    fun isEmergencyOverrideReady(): Boolean {
        val requestedAt = getEmergencyOverrideRequestedTimestamp()
        return requestedAt > 0L && getEmergencyOverrideRemainingMillis() <= 0L
    }

    fun isEmergencyOverrideActive(): Boolean {
        return context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .getBoolean(PREF_EMERGENCY_OVERRIDE_ACTIVE, false)
    }

    fun setEmergencyOverrideActive(active: Boolean) {
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_EMERGENCY_OVERRIDE_ACTIVE, active).apply()
    }

    fun getEmergencyWindowExpiryTimestamp(): Long {
        return context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .getLong(PREF_EMERGENCY_WINDOW_EXPIRY_TIMESTAMP, 0L)
    }

    fun setEmergencyWindowExpiryTimestamp(timestamp: Long) {
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit().putLong(PREF_EMERGENCY_WINDOW_EXPIRY_TIMESTAMP, timestamp).apply()
    }

    /**
     * Called when the emergency countdown finishes.
     * Grants a 10-minute temporary unlock window and records PREF_EMERGENCY_OVERRIDE_ACTIVE.
     */
    fun activateEmergencyWindow() {
        val expiry = System.currentTimeMillis() + EMERGENCY_WINDOW_DURATION_MS
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_EMERGENCY_OVERRIDE_ACTIVE, true)
            .putLong(PREF_EMERGENCY_WINDOW_EXPIRY_TIMESTAMP, expiry)
            .remove(PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP)
            .apply()

        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    fun isEmergencyWindowActive(): Boolean {
        if (!isEmergencyOverrideActive()) return false
        val expiry = getEmergencyWindowExpiryTimestamp()
        if (expiry <= 0L) return false
        if (System.currentTimeMillis() >= expiry) {
            // Expired, auto-clear
            clearEmergencyOverride()
            return false
        }
        return true
    }

    fun clearEmergencyOverride() {
        context.getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_EMERGENCY_OVERRIDE_ACTIVE)
            .remove(PREF_EMERGENCY_WINDOW_EXPIRY_TIMESTAMP)
            .remove(PREF_EMERGENCY_OVERRIDE_REQUESTED_TIMESTAMP)
            .apply()

        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    fun getEnforcementMode(): String {
        return "ADVANCED"
    }

    fun setEnforcementMode(mode: String) {
        // Mode setting is deprecated and simple mode is removed.
    }

    // ==================== 3-Mode Architecture & Offline Resilience ====================

    fun getDeviceOperationMode(): Int {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isManaged = prefs.getBoolean("is_console_managed", false) || prefs.getBoolean("is_paired_with_console", false)
        val pairedDeviceId = prefs.getString("paired_device_id", null)

        if (isManaged && !pairedDeviceId.isNullOrBlank()) {
            return MODE_CONSOLE_ENFORCED
        }

        val personalSync = prefs.getBoolean("personal_cloud_sync_enabled", false)
        val licenseKey = prefs.getString("license_key", null)
        if (personalSync || (!licenseKey.isNullOrBlank() && personalSync)) {
            return MODE_PERSONAL_SYNC
        }

        return MODE_STANDALONE
    }

    fun isConsoleManaged(): Boolean {
        return getDeviceOperationMode() == MODE_CONSOLE_ENFORCED
    }

    fun setConsoleManaged(managed: Boolean, deviceId: String? = null, ownerId: String? = null) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).edit()
        prefs.putBoolean("is_console_managed", managed)
        prefs.putBoolean("is_paired_with_console", managed)
        if (deviceId != null) prefs.putString("paired_device_id", deviceId)
        if (ownerId != null) prefs.putString("paired_owner_id", ownerId)
        if (!managed) {
            prefs.remove("paired_device_id")
            prefs.remove("paired_owner_id")
            prefs.remove("cached_policy_payload")
        }
        prefs.apply()
    }

    fun isPersonalSyncEnabled(): Boolean {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getBoolean("personal_cloud_sync_enabled", false)
    }

    fun setPersonalSyncEnabled(enabled: Boolean) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("personal_cloud_sync_enabled", enabled).apply()
    }

    fun getPairedDeviceId(): String? {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getString("paired_device_id", null)
    }

    fun saveCachedPolicyPayload(payloadJson: String) {
        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit()
            .putString("cached_policy_payload", payloadJson)
            .putLong("last_policy_sync_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun getCachedPolicyPayload(): String? {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getString("cached_policy_payload", null)
    }

    fun getLastPolicySyncTimestamp(): Long {
        return context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .getLong("last_policy_sync_timestamp", 0L)
    }

    // ========================================================================
    // AmniSpace (Mindful Focus Launcher Space) Preferences
    // ========================================================================

    fun isAmniSpaceFocusLauncherEnabled(): Boolean {
        return context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_focus_launcher_enabled", false)
    }

    fun setAmniSpaceFocusLauncherEnabled(enabled: Boolean) {
        context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_focus_launcher_enabled", enabled).apply()
    }

    fun getAmniSpaceEssentialApps(): Set<String> {
        val prefs = context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
        val defaultApps = setOf(
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage"
        )
        return prefs.getStringSet("essential_apps", defaultApps) ?: defaultApps
    }

    fun setAmniSpaceEssentialApps(apps: Set<String>) {
        context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("essential_apps", apps).apply()
    }

    fun getAmniSpaceBreathingDurationSeconds(): Int {
        return context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .getInt("breathing_duration_seconds", 5)
    }

    fun setAmniSpaceBreathingDurationSeconds(seconds: Int) {
        context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .edit().putInt("breathing_duration_seconds", seconds.coerceIn(3, 30)).apply()
    }

    fun isAmniSpaceUsageLimitFrictionEnabled(): Boolean {
        return context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_usage_limit_friction_enabled", true)
    }

    fun setAmniSpaceUsageLimitFrictionEnabled(enabled: Boolean) {
        context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_usage_limit_friction_enabled", enabled).apply()
    }

    fun getBreathingWidgetDurationMinutes(): Int {
        return context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .getInt("widget_breathing_duration_minutes", 2)
    }

    fun setBreathingWidgetDurationMinutes(minutes: Int) {
        context.getSharedPreferences("amnispace_prefs", Context.MODE_PRIVATE)
            .edit().putInt("widget_breathing_duration_minutes", minutes.coerceIn(1, 30)).apply()
    }

    // -------------------------------------------------------------------------
    // Profile Identity, Daily Screen Time Goal & App ID Backend Sync Helpers
    // -------------------------------------------------------------------------

    fun getProfileName(): String {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return prefs.getString("profile_name", "Guardian")?.takeIf { it.isNotBlank() } ?: "Guardian"
    }

    fun setProfileName(name: String) {
        val cleanName = name.trim().ifEmpty { "Guardian" }
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_name", cleanName).apply()
        // Keep community access display name in sync if already set or initialized
        val caPrefs = context.getSharedPreferences("saved_preferences", Context.MODE_PRIVATE)
        if (caPrefs.getString("compassionate_access_name", "").isNullOrBlank()) {
            caPrefs.edit().putString("compassionate_access_name", cleanName).apply()
        }
    }

    fun getProfileGoalMinutes(): Int {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return prefs.getInt("profile_goal_minutes", 180).coerceIn(15, 720)
    }

    fun setProfileGoalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(15, 720)
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().putInt("profile_goal_minutes", clamped).apply()
    }

    fun getProfileAvatarId(): String {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return try {
            prefs.getString("profile_avatar_id", "avatar_shield")?.takeIf { it.isNotBlank() } ?: "avatar_shield"
        } catch (_: ClassCastException) {
            prefs.edit().remove("profile_avatar_id").putString("profile_avatar_id", "avatar_shield").apply()
            "avatar_shield"
        }
    }

    fun setProfileAvatarId(avatarId: String) {
        val cleanId = avatarId.trim().ifEmpty { "avatar_shield" }
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().putString("profile_avatar_id", cleanId).apply()
    }

    fun getProfileImageUri(): String? {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return prefs.getString("profile_image_uri", null)?.takeIf { it.isNotBlank() }
    }

    fun setProfileImageUri(uriString: String?) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        if (uriString.isNullOrBlank()) {
            prefs.edit().remove("profile_image_uri").apply()
        } else {
            prefs.edit().putString("profile_image_uri", uriString).apply()
        }
    }

    fun getShortAppId(): String {
        val raw = getDeviceId().replace("-", "").uppercase()
        return if (raw.length >= 8) {
            "AMNI-${raw.take(4)}-${raw.takeLast(4)}"
        } else {
            "AMNI-$raw"
        }
    }

    fun getLastIdentitySyncTime(): Long {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        return prefs.getLong("last_identity_sync_time", 0L)
    }

    fun markIdentitySynced(userName: String, appId: String = getDeviceId()) {
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("synced_user_name", userName.trim().ifEmpty { "Guardian" })
            .putString("synced_app_id", appId)
            .putLong("last_identity_sync_time", System.currentTimeMillis())
            .apply()
    }

    // ==================== Universal Hourly & Daily Launch Tracking ====================

    data class UniversalLaunchRecord(
        val dailyDateKey: String = "",
        val dailyCount: Int = 0,
        val hourlyKey: String = "",
        val hourlyCount: Int = 0
    )

    private fun formatDateAndHourKeys(nowMillis: Long): Pair<String, String> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return "$year-$day" to "$year-$day-$hour"
    }

    fun recordUniversalAppLaunch(packageName: String, nowMillis: Long = wallClockProvider()) = synchronized(statsLock) {
        val prefs = context.getSharedPreferences("app_launch_tracking", Context.MODE_PRIVATE)
        val (dateKey, hourKey) = formatDateAndHourKeys(nowMillis)
        val map = loadUniversalLaunchMap(prefs).toMutableMap()
        val existing = map[packageName]
        val nextDaily = if (existing?.dailyDateKey == dateKey) existing.dailyCount + 1 else 1
        val nextHourly = if (existing?.hourlyKey == hourKey) existing.hourlyCount + 1 else 1
        map[packageName] = UniversalLaunchRecord(
            dailyDateKey = dateKey,
            dailyCount = nextDaily,
            hourlyKey = hourKey,
            hourlyCount = nextHourly
        )
        prefs.edit().putString("universal_launch_counts", Gson().toJson(map)).apply()
    }

    fun getUniversalDailyLaunchCount(packageName: String, nowMillis: Long = wallClockProvider()): Int = synchronized(statsLock) {
        val prefs = context.getSharedPreferences("app_launch_tracking", Context.MODE_PRIVATE)
        val (dateKey, _) = formatDateAndHourKeys(nowMillis)
        val record = loadUniversalLaunchMap(prefs)[packageName] ?: return@synchronized 0
        return@synchronized if (record.dailyDateKey == dateKey) record.dailyCount else 0
    }

    fun getUniversalHourlyLaunchCount(packageName: String, nowMillis: Long = wallClockProvider()): Int = synchronized(statsLock) {
        val prefs = context.getSharedPreferences("app_launch_tracking", Context.MODE_PRIVATE)
        val (_, hourKey) = formatDateAndHourKeys(nowMillis)
        val record = loadUniversalLaunchMap(prefs)[packageName] ?: return@synchronized 0
        return@synchronized if (record.hourlyKey == hourKey) record.hourlyCount else 0
    }

    private fun loadUniversalLaunchMap(prefs: SharedPreferences): Map<String, UniversalLaunchRecord> {
        val json = prefs.getString("universal_launch_counts", null)
        if (json.isNullOrEmpty()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, UniversalLaunchRecord>>() {}.type
            Gson().fromJson<Map<String, UniversalLaunchRecord>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ==================== Unified Block Engine ("All-in-One Block") ====================

    fun loadUnifiedBlockRules(): MutableList<UnifiedBlockRule> = synchronized(rulesLock) {
        val prefs = context.getSharedPreferences("unified_block_engine", Context.MODE_PRIVATE)
        val migrated = prefs.getBoolean("legacy_rules_migrated_v1", false)
        val json = prefs.getString("unified_block_rules_json", null)
        if (!migrated && json.isNullOrEmpty()) {
            return@synchronized migrateLegacyRulesToUnifiedIfNeeded()
        }
        if (json.isNullOrEmpty()) return@synchronized mutableListOf()

        val type = object : TypeToken<MutableList<UnifiedBlockRule>>() {}.type
        return@synchronized runCatching {
            val rawList = Gson().fromJson<MutableList<UnifiedBlockRule>>(json, type) ?: mutableListOf()
            val nowElapsed = elapsedRealtimeProvider()
            val nowWall = wallClockProvider()
            var anyExpiredPause = false
            val sanitizedList = rawList.map { raw ->
                val clean = raw.sanitize()
                if (clean.status == BlockRuleStatus.PAUSED_UNTIL && !clean.isPaused(nowElapsed, nowWall)) {
                    anyExpiredPause = true
                    clean.copy(
                        status = BlockRuleStatus.ACTIVE,
                        pausedUntilElapsedRealtime = 0L,
                        pausedUntilWallClockMillis = 0L
                    )
                } else {
                    clean
                }
            }.toMutableList()
            if (anyExpiredPause) {
                prefs.edit().putString("unified_block_rules_json", Gson().toJson(sanitizedList)).apply()
            }
            sanitizedList
        }.getOrElse {
            Log.e("SavedPreferencesLoader", "Failed to load unified block rules", it)
            mutableListOf()
        }
    }

    fun saveUnifiedBlockRules(rules: List<UnifiedBlockRule>, syncToEngines: Boolean = true) {
        val sanitized = rules.map { it.sanitize() }
        synchronized(rulesLock) {
            val prefs = context.getSharedPreferences("unified_block_engine", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("legacy_rules_migrated_v1", true)
                .putString("unified_block_rules_json", Gson().toJson(sanitized))
                .apply()
        }
        runCatching {
            AmniShieldRuleDatabase.getInstance(context)
                .syncUnifiedBlockRules(sanitized)
        }
        if (syncToEngines) {
            syncUnifiedRulesToLegacyEngines(sanitized)
        }
    }

    fun upsertUnifiedBlockRule(rule: UnifiedBlockRule) = synchronized(rulesLock) {
        val rules = loadUnifiedBlockRules()
        val sanitized = rule.sanitize()
        val index = rules.indexOfFirst { it.id == sanitized.id }
        if (index >= 0) {
            rules[index] = sanitized
        } else {
            rules.add(0, sanitized)
        }
        saveUnifiedBlockRules(rules, syncToEngines = true)
    }

    fun deleteUnifiedBlockRule(ruleId: String) = synchronized(rulesLock) {
        val rules = loadUnifiedBlockRules()
        val removed = rules.removeAll { it.id == ruleId }
        if (removed) {
            saveUnifiedBlockRules(rules, syncToEngines = true)
        }
    }

    fun pauseUnifiedBlockRule(ruleId: String, durationMinutes: Int) = synchronized(rulesLock) {
        val safeMinutes = durationMinutes.coerceIn(1, 720)
        val durationMs = safeMinutes * 60_000L
        val nowElapsed = elapsedRealtimeProvider()
        val nowWall = wallClockProvider()
        val rules = loadUnifiedBlockRules()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index >= 0) {
            val target = rules[index]
            rules[index] = target.copy(
                status = BlockRuleStatus.PAUSED_UNTIL,
                pausedUntilElapsedRealtime = nowElapsed + durationMs,
                pausedUntilWallClockMillis = nowWall + durationMs
            )
            saveUnifiedBlockRules(rules, syncToEngines = true)
        }
    }

    fun setUnifiedBlockRuleStatus(ruleId: String, newStatus: BlockRuleStatus) = synchronized(rulesLock) {
        val rules = loadUnifiedBlockRules()
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index >= 0) {
            val current = rules[index]
            rules[index] = current.copy(
                status = newStatus,
                pausedUntilElapsedRealtime = if (newStatus == BlockRuleStatus.PAUSED_UNTIL) current.pausedUntilElapsedRealtime else 0L,
                pausedUntilWallClockMillis = if (newStatus == BlockRuleStatus.PAUSED_UNTIL) current.pausedUntilWallClockMillis else 0L
            )
            saveUnifiedBlockRules(rules, syncToEngines = true)
        }
    }

    fun migrateLegacyRulesToUnifiedIfNeeded(): MutableList<UnifiedBlockRule> = synchronized(rulesLock) {
        val prefs = context.getSharedPreferences("unified_block_engine", Context.MODE_PRIVATE)
        val legacySchedules = loadAppBlockerScheduleRules()
        val legacyLaunchLimits = loadAppLaunchLimitRules()
        val migratedList = mutableListOf<UnifiedBlockRule>()

        val groupIds = legacySchedules.mapNotNull { (it.groupId ?: it.id).takeIf { id -> id.isNotBlank() } }.distinct()
        val coveredPackages = mutableSetOf<String>()

        groupIds.forEach { groupId ->
            val groupItems = legacySchedules.filter { (it.groupId ?: it.id) == groupId }
            if (groupItems.isEmpty()) return@forEach
            val first = groupItems.first()
            val name = (first.groupTitle ?: first.title).removePrefix("Block Always • ").removePrefix("Block • ").ifBlank { "Block" }
            val isEnabled = first.isRuleEnabled

            val appTargets = mutableMapOf<String, AppInterceptionLevel>()
            val domains = mutableListOf<String>()
            val keywords = mutableListOf<String>()
            val schedules = mutableListOf<WeeklyScheduleTrigger>()
            val screenLimits = mutableListOf<ScreenTimeLimitTrigger>()
            val openLimits = mutableListOf<OpenLimitTrigger>()

            groupItems.forEach { item ->
                when (item.blockerType) {
                    BlockerType.WEBSITE -> {
                        domains.addAll(item.targets.ifEmpty { item.targetWebsites })
                    }
                    BlockerType.KEYWORD -> {
                        keywords.addAll(item.targets.ifEmpty { item.targetKeywords })
                    }
                    BlockerType.REELS -> {
                        val pkgs = item.targets.filter { it != "reel_blocker" }
                            .ifEmpty { listOf("com.instagram.android", "com.google.android.youtube") }
                        pkgs.forEach { pkg ->
                            appTargets[pkg] = AppInterceptionLevel.BLOCK_SHORTFORM_ONLY
                        }
                    }
                    else -> {
                        val pkg = item.packageName
                        if (pkg.isNotBlank() &&
                            pkg != "keyword_blocker" &&
                            pkg != "website_blocker" &&
                            pkg != "reel_blocker" &&
                            !pkg.equals("FOCUS_MODE", ignoreCase = true)
                        ) {
                            appTargets[pkg] = AppInterceptionLevel.BLOCK_ENTIRE_APP
                            coveredPackages.add(pkg)
                        }
                        item.targets.filter {
                            it.isNotBlank() &&
                                it != "keyword_blocker" &&
                                it != "website_blocker" &&
                                it != "reel_blocker" &&
                                !it.equals("FOCUS_MODE", ignoreCase = true)
                        }.forEach { tPkg ->
                            appTargets[tPkg] = AppInterceptionLevel.BLOCK_ENTIRE_APP
                            coveredPackages.add(tPkg)
                        }
                    }
                }

                if (item.durationHours > 0) {
                    val mins = item.durationHours * 60
                    if (screenLimits.none { it.maxMinutes == mins && it.period == LimitPeriod.DAILY }) {
                        screenLimits.add(ScreenTimeLimitTrigger(period = LimitPeriod.DAILY, maxMinutes = mins))
                    }
                } else if (item.recurrence != AppBlockScheduleRule.Recurrence.ALWAYS &&
                    item.type == AppBlockScheduleRule.RuleType.BLOCK
                ) {
                    val days = if (item.selectedDays.isNotEmpty()) {
                        item.selectedDays
                    } else {
                        setOf(
                            Calendar.MONDAY,
                            Calendar.TUESDAY,
                            Calendar.WEDNESDAY,
                            Calendar.THURSDAY,
                            Calendar.FRIDAY,
                            Calendar.SATURDAY,
                            Calendar.SUNDAY
                        )
                    }
                    if (schedules.none {
                            it.startTimeMinutes == item.startMinute &&
                                it.endTimeMinutes == item.endMinute &&
                                it.daysOfWeek == days
                        }
                    ) {
                        schedules.add(
                            WeeklyScheduleTrigger(
                                daysOfWeek = days,
                                startTimeMinutes = item.startMinute.coerceIn(0, 1439),
                                endTimeMinutes = item.endMinute.coerceIn(0, 1439)
                            )
                        )
                    }
                }
            }

            // Attach any matching launch limit rules
            appTargets.keys.forEach { pkg ->
                val limit = legacyLaunchLimits.firstOrNull { it.packageName == pkg }
                if (limit != null) {
                    val period = if (limit.timePeriod == AppLaunchLimitRule.TimePeriod.HOURLY) {
                        LimitPeriod.HOURLY
                    } else {
                        LimitPeriod.DAILY
                    }
                    if (openLimits.none { it.maxLaunches == limit.maxLaunches && it.period == period }) {
                        openLimits.add(OpenLimitTrigger(period = period, maxLaunches = limit.maxLaunches))
                    }
                }
            }

            if (appTargets.isNotEmpty() || domains.isNotEmpty() || keywords.isNotEmpty()) {
                migratedList.add(
                    UnifiedBlockRule(
                        id = groupId,
                        name = name,
                        status = if (isEnabled) BlockRuleStatus.ACTIVE else BlockRuleStatus.INACTIVE_MANUAL,
                        appTargets = appTargets,
                        blockedDomains = domains.distinct(),
                        blockedKeywords = keywords.distinct(),
                        weeklySchedules = schedules,
                        screenTimeLimits = screenLimits,
                        openLimits = openLimits,
                        pauseOption = PauseOptionConfig(type = PauseOptionType.DELAY, delaySeconds = 10, maxPauseDurationMinutes = 60),
                        createdAt = first.createdAt,
                        authType = first.authType,
                        rulePasswordHash = first.rulePasswordHash,
                        rulePasswordSalt = first.rulePasswordSalt
                    ).sanitize()
                )
            }
        }

        // Convert standalone launch limits not already covered by a group
        legacyLaunchLimits.filter { !coveredPackages.contains(it.packageName) && it.packageName.isNotBlank() }.forEach { limit ->
            val period = if (limit.timePeriod == AppLaunchLimitRule.TimePeriod.HOURLY) {
                LimitPeriod.HOURLY
            } else {
                LimitPeriod.DAILY
            }
            migratedList.add(
                UnifiedBlockRule(
                    id = "limit::${limit.packageName}",
                    name = "Open Limit",
                    status = if (limit.isEnabled) BlockRuleStatus.ACTIVE else BlockRuleStatus.INACTIVE_MANUAL,
                    appTargets = mapOf(limit.packageName to AppInterceptionLevel.BLOCK_ENTIRE_APP),
                    openLimits = listOf(OpenLimitTrigger(period = period, maxLaunches = limit.maxLaunches)),
                    createdAt = limit.createdAt,
                    authType = limit.authType,
                    rulePasswordHash = limit.rulePasswordHash,
                    rulePasswordSalt = limit.rulePasswordSalt
                ).sanitize()
            )
        }

        prefs.edit()
            .putBoolean("legacy_rules_migrated_v1", true)
            .putString("unified_block_rules_json", Gson().toJson(migratedList))
            .apply()

        return@synchronized migratedList
    }

    // ==================== Always-On Shields Config ====================

    fun loadAlwaysOnShieldsConfig(): AlwaysOnShieldsConfig {
        synchronized(alwaysOnShieldsLock) {
            val prefs = context.getSharedPreferences("always_on_shields", Context.MODE_PRIVATE)
            val json = prefs.getString("always_on_shields_json", null)
            if (json.isNullOrBlank()) {
                return AlwaysOnShieldsConfig()
            }
            return runCatching {
                Gson().fromJson(json, AlwaysOnShieldsConfig::class.java)?.sanitize()
                    ?: AlwaysOnShieldsConfig()
            }.getOrElse {
                Log.e("SavedPreferencesLoader", "Failed to deserialize AlwaysOnShieldsConfig", it)
                AlwaysOnShieldsConfig()
            }
        }
    }

    fun saveAlwaysOnShieldsConfig(config: AlwaysOnShieldsConfig, syncToEngines: Boolean = true) {
        val sanitized = config.sanitize()
        synchronized(alwaysOnShieldsLock) {
            val prefs = context.getSharedPreferences("always_on_shields", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("always_on_shields_json", Gson().toJson(sanitized))
                .apply()
        }
        if (syncToEngines) {
            val currentRules = loadUnifiedBlockRules()
            syncUnifiedRulesToLegacyEngines(currentRules, sanitized)
        }
    }

    /**
     * Synchronizes both UnifiedBlockRules and AlwaysOnShieldsConfig with the underlying engines
     * (unioning active targets so Always-On Shields work independently of Custom Rules),
     * broadcasts scoped refresh intents, and updates the persistent Active Protection notification.
     */
    fun syncUnifiedRulesToLegacyEngines(
        rules: List<UnifiedBlockRule>,
        alwaysOnOverride: AlwaysOnShieldsConfig? = null
    ) {
        val alwaysOn = (alwaysOnOverride ?: loadAlwaysOnShieldsConfig()).sanitize()
        val nowElapsed = elapsedRealtimeProvider()
        val nowWall = wallClockProvider()
        val nonArchived = rules.filter { it.status != BlockRuleStatus.ARCHIVED }
        val activeRules = nonArchived.filter {
            it.isEffectivelyActive(nowElapsed, nowWall)
        }

        // 1. Authoritative Sync for Websites (Custom Rules U Always-On Website custom domains;
        // large bundled packs are streamed off-thread directly by DomainBlocklistEngine without bloating SharedPreferences)
        val unifiedDomains = activeRules
            .filter { it.isActiveOrScheduledForContentNow(nowElapsed, nowWall) }
            .flatMap { it.blockedDomains }
            .toMutableSet()
        if (alwaysOn.website.enabled) {
            alwaysOn.website.customDomains.forEach { domain ->
                com.alhaq.amnishield.data.blockers.WebsiteGuardConfig.normalizeDomainInput(domain)
                    ?.let { unifiedDomains.add(it) }
            }
        }
        saveBlockedWebsites(unifiedDomains)
        setWebsiteBlockerEnabled(
            unifiedDomains.isNotEmpty() || (
                alwaysOn.website.enabled && (
                    alwaysOn.website.enabledPackIds.isNotEmpty() ||
                        alwaysOn.website.blockUnsupportedBrowsers ||
                        alwaysOn.website.blockIncognitoMode
                    )
                ),
            updateManual = false
        )

        // 2. Authoritative Union Sync for Keywords (Custom Rules U Always-On Keyword Guard)
        val unifiedKeywords = activeRules
            .filter { it.isActiveOrScheduledForContentNow(nowElapsed, nowWall) }
            .flatMap { it.blockedKeywords }
            .toMutableSet()
        if (alwaysOn.keyword.enabled) {
            unifiedKeywords.addAll(alwaysOn.keyword.effectiveKeywords())
            val kwConfigPrefs = context.getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE)
            kwConfigPrefs.edit()
                .putBoolean("whole_word_only", alwaysOn.keyword.wholeWordOnly)
                .putBoolean("search_all_text_fields", alwaysOn.keyword.scanAllTextFields)
                .apply()
            saveKeywordBlockerIgnoredApps(alwaysOn.keyword.ignoredPackages.toList())
        }
        saveBlockedKeywords(unifiedKeywords)
        setKeywordBlockerFeatureEnabled(unifiedKeywords.isNotEmpty(), updateManual = false)

        // 3. Authoritative Union Sync for Short-form / Reels & In-Browser Short Videos
        val shortformPackages = activeRules.flatMap { rule ->
            rule.appTargets.filterValues { it == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY }.keys
        }.toSet()
        val browserShortformByRules = activeRules.any {
            it.includeInBrowserShortform && it.isActiveOrScheduledForContentNow(nowElapsed, nowWall)
        }
        val reelsGuardOn = alwaysOn.reels.enabled
        val ytEnabled = shortformPackages.contains("com.google.android.youtube") ||
            (reelsGuardOn && alwaysOn.reels.blockYoutubeShorts)
        val igEnabled = shortformPackages.contains("com.instagram.android") ||
            (reelsGuardOn && alwaysOn.reels.blockInstagramReels)
        val ttEnabled = shortformPackages.contains("com.zhiliaoapp.musically") ||
            shortformPackages.contains("com.ss.android.ugc.trill") ||
            (reelsGuardOn && alwaysOn.reels.blockTikTok)
        val browserEnabled = browserShortformByRules ||
            (reelsGuardOn && alwaysOn.reels.blockInBrowserShorts)

        val anyShortformActive = ytEnabled || igEnabled || ttEnabled || browserEnabled
        setReelBlockerEnabled(anyShortformActive, updateManual = false)
        setReelBlockerYoutubeEnabled(ytEnabled)
        setReelBlockerInstagramEnabled(igEnabled)
        setReelBlockerTiktokEnabled(ttEnabled)
        setReelBlockerBrowserEnabled(browserEnabled)

        // Wire ReelsLimitMode directly into existing ReelBlocker mode & daily limit prefs
        if (reelsGuardOn) {
            val mode = when (alwaysOn.reels.limitMode) {
                ReelsLimitMode.ALWAYS_BLOCK_24_7 -> ReelBlocker.MODE_BLOCK_ALL
                ReelsLimitMode.LIMIT_BY_COUNT -> ReelBlocker.MODE_BLOCK_AFTER_DAILY_COUNT
            }
            setReelBlockerMode(mode)
            setReelBlockerDailyLimit(alwaysOn.reels.dailyReelLimit)
        } else {
            setReelBlockerMode(ReelBlocker.MODE_BLOCK_ALL)
        }

        // 4. Authoritative Sync for Full-App blocks that are active right now
        val unconditionalFullBlockedPackages = activeRules
            .filter { it.blockAppLaunch && it.isUnconditionalOrScheduledActiveNow(nowElapsed, nowWall) }
            .flatMap { rule ->
                rule.appTargets.filterValues { it == AppInterceptionLevel.BLOCK_ENTIRE_APP }.keys
            }
            .toSet()

        val anyAppRuleActive = activeRules.any {
            it.appTargets.containsValue(AppInterceptionLevel.BLOCK_ENTIRE_APP)
        }
        setAppBlockerFeatureEnabled(anyAppRuleActive, updateManual = false)
        saveBlockedApps(unconditionalFullBlockedPackages)

        // 5. Update persistent Active Protection notification
        runCatching {
            NotificationHelper.getInstance(context).updateActiveProtectionStatusNotification(
                activeAlwaysOnNames = alwaysOn.activeShieldTypes().map { it.pillLabel },
                activeCustomRuleCount = activeRules.size
            )
        }

        // 6. Broadcast package-scoped refresh intents to AmniShieldAccessibilityService
        runCatching {
            val actions = listOf(
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ALWAYS_ON_SHIELDS,
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES,
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER,
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER,
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER,
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST
            )
            actions.forEach { action ->
                val intent = Intent(action).apply { setPackage(context.packageName) }
                context.sendBroadcast(intent)
            }
        }
    }

    // ==================== Weekly Usage Report Notes ====================

    fun getWeeklyReportNote(weekKey: String): String {
        val prefs = context.getSharedPreferences("weekly_usage_reports", Context.MODE_PRIVATE)
        return prefs.getString("note_$weekKey", "") ?: ""
    }

    fun saveWeeklyReportNote(weekKey: String, note: String) {
        val safeNote = note.take(500)
        val prefs = context.getSharedPreferences("weekly_usage_reports", Context.MODE_PRIVATE)
        prefs.edit().putString("note_$weekKey", safeNote).apply()
    }

    // ========================================================================
    // Accessibility Prominent Disclosure Consent Persistence & Baseline Aliases
    // ========================================================================

    fun isAccessibilityDisclosureAccepted(): Boolean {
        return context.getSharedPreferences("permission_guide_prefs", Context.MODE_PRIVATE)
            .getBoolean("has_user_accepted_accessibility_disclosure", false)
    }

    fun getAccessibilityDisclosureAcceptedTimestamp(): Long {
        return context.getSharedPreferences("permission_guide_prefs", Context.MODE_PRIVATE)
            .getLong("accessibility_disclosure_accepted_at", 0L)
    }

    fun setAccessibilityDisclosureAccepted(accepted: Boolean, version: Int = 1) {
        val editor = context.getSharedPreferences("permission_guide_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("has_user_accepted_accessibility_disclosure", accepted)
            .putInt("accessibility_disclosure_version", version)
        if (accepted) {
            editor.putLong("accessibility_disclosure_accepted_at", System.currentTimeMillis())
        }
        editor.apply()
    }

    fun isReelsBlockerFeatureEnabled(default: Boolean = false): Boolean = isReelBlockerEnabled(default)
    fun setReelsBlockerFeatureEnabled(enabled: Boolean, updateManual: Boolean = true) = setReelBlockerEnabled(enabled, updateManual)
    fun isWebsiteBlockerFeatureEnabled(default: Boolean = false): Boolean = isWebsiteBlockerEnabled(default)
    fun setWebsiteBlockerFeatureEnabled(enabled: Boolean, updateManual: Boolean = true) = setWebsiteBlockerEnabled(enabled, updateManual)

    fun isAppUsageLimitEnabled(): Boolean {
        return context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("app_usage_limit_enabled", false)
    }

    fun setAppUsageLimitEnabled(enabled: Boolean) {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("app_usage_limit_enabled", enabled)
            .apply()
    }

    fun isAdaptiveShellEnabled(): Boolean {
        return context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .getBoolean("adaptive_compose_shell_enabled", false)
    }

    fun setAdaptiveShellEnabled(enabled: Boolean) {
        context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("adaptive_compose_shell_enabled", enabled)
            .apply()
    }
}
