package com.alhaq.amnishield.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.blockers.ReelBlocker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.ui.activity.MainActivity
import java.util.Calendar
import java.util.UUID
import java.util.ArrayList

class SavedPreferencesLoader(val context: Context) {

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

    fun loadBlockedApps(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun saveAppBlockerCooldownData(cooldowns: Map<String, Long>) {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        // apply() is asynchronous; cooldown data is non-critical and called frequently
        // from the accessibility hot path so we avoid synchronous disk writes here.
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadAppBlockerCooldownData(): MutableMap<String, Long> {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveViewBlockerCooldownData(cooldowns: Map<String, Long>) {
        val sharedPreferences =
            context.getSharedPreferences("view_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadViewBlockerCooldownData(): MutableMap<String, Long> {
        val sharedPreferences =
            context.getSharedPreferences("view_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return Gson().fromJson(json, type)
    }

    fun saveReelBlockerCooldownData(cooldowns: Map<String, Long>) {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(cooldowns)
        sharedPreferences.edit().putString("cooldown_data", json).apply()
    }

    fun loadReelBlockerCooldownData(): MutableMap<String, Long> {
        val sharedPreferences =
            context.getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("cooldown_data", null)
        if (json.isNullOrEmpty()) return mutableMapOf()

        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return Gson().fromJson(json, type)
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

    fun loadBlockedKeywords(): Set<String> {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("blocked_keywords", emptySet()) ?: emptySet()
    }

    fun savePinned(pinnedApps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("pinned_apps", pinnedApps).apply()
    }


    fun saveBlockedApps(pinnedApps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("blocked_apps", pinnedApps).apply()
    }


    fun saveIgnoredAppUsageTracker(ignoredApps: Set<String>) {
        val sharedPreferences =
            context.getSharedPreferences("app_usage_tracker", Context.MODE_PRIVATE)
        sharedPreferences.edit().putStringSet("ignored_apps", ignoredApps).apply()
    }


    fun saveBlockedKeywords(pinnedApps: Set<String>) {
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
    
    fun stopFocusSession() {
        val currentData = getFocusModeData()
        saveFocusModeData(
            FocusModeBlocker.FocusModeData(
                isTurnedOn = false,
                endTime = 0L,
                modeType = currentData.modeType,
                selectedApps = currentData.selectedApps
            )
        )
        completeFocusSession()
    }

    fun completeFocusSession() {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val startTime = sharedPreferences.getLong("focus_session_start", -1)
        val endTime = sharedPreferences.getLong("focus_session_end", -1)
        
        if (startTime != -1L && endTime != -1L) {
            // Record the completed focus session
            BlockingStatsManager.getInstance(context).recordFocusSession(startTime, endTime)
            
            // Clear the session data
            sharedPreferences.edit()
                .remove("focus_session_start")
                .remove("focus_session_end")
                .apply()
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
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("schedule_rules", null)
        if (json.isNullOrEmpty()) return mutableListOf()

        val type = object : TypeToken<MutableList<AppBlockScheduleRule>>() {}.type
        return runCatching {
            val rawList = Gson().fromJson<MutableList<AppBlockScheduleRule>>(json, type) ?: mutableListOf()
            rawList.map { it.sanitize() }.toMutableList()
        }.getOrElse {
            Log.e("SavedPreferencesLoader", "Failed to load app blocker schedule rules", it)
            mutableListOf()
        }
    }

    fun saveAppBlockerScheduleRules(rules: MutableList<AppBlockScheduleRule>) {
        val sharedPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(rules)
        sharedPreferences.edit().putString("schedule_rules", json).apply()
    }

    @Synchronized
    fun upsertAppBlockerScheduleRule(rule: AppBlockScheduleRule) {
        val rules = loadAppBlockerScheduleRules()
        val existing = rules.indexOfFirst { it.id == rule.id }
        if (existing >= 0) {
            rules[existing] = rule
        } else {
            rules.add(rule)
        }
        saveAppBlockerScheduleRules(rules)
    }

    @Synchronized
    fun removeAppBlockerScheduleRule(ruleId: String) {
        val rules = loadAppBlockerScheduleRules()
        rules.removeAll { it.id == ruleId }
        saveAppBlockerScheduleRules(rules)
    }

    @Synchronized
    fun removeAppBlockerScheduleGroup(groupId: String): Int {
        val rules = loadAppBlockerScheduleRules()
        val before = rules.size
        rules.removeAll { it.groupId == groupId }
        if (rules.size != before) {
            saveAppBlockerScheduleRules(rules)
        }
        return before - rules.size
    }



    private fun getPremiumPrefs(): android.content.SharedPreferences {
        return context.getSharedPreferences("premium_state", Context.MODE_PRIVATE)
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


    private fun getCompassionateAccessPrefs(): android.content.SharedPreferences {
        return context.getSharedPreferences("compassionate_access", Context.MODE_PRIVATE)
    }

    fun saveCompassionateAccessGrant(
        appId: String,
        userName: String,
        email: String?,
        grantedAt: Long,
        expiresAt: Long
    ) {
        getCompassionateAccessPrefs().edit()
            .putString("app_id", appId)
            .putString("user_name", userName)
            .putString("email", email.orEmpty())
            .putLong("granted_at", grantedAt)
            .putLong("expires_at", expiresAt)
            .apply()
    }

    fun getCompassionateAccessAppId(): String {
        return getCompassionateAccessPrefs().getString("app_id", "") ?: ""
    }

    fun getCompassionateAccessExpiry(): Long {
        return getCompassionateAccessPrefs().getLong("expires_at", 0L)
    }

    fun clearCompassionateAccessGrant() {
        getCompassionateAccessPrefs().edit().clear().apply()
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
        const val OVERLAY_MODE_COUNT = 0
        const val OVERLAY_MODE_TIME = 1
        const val OVERLAY_MODE_BOTH = 2

        const val MODE_STANDALONE = 0
        const val MODE_PERSONAL_SYNC = 1
        const val MODE_CONSOLE_ENFORCED = 2

        val DEFAULT_REELS_OVERLAY_APPS = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.facebook.katana",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.snapchat.android"
        )

        // PIN Security, Cooldown & Reset Engine
        const val MIN_COOLDOWN_MINUTES = 5
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

    fun loadWebsiteUsageStats(): Map<String, Long> {
        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("domain_stats_today", null)
        if (json.isNullOrEmpty()) {
            return mapOf(
                "youtube.com" to 2550000L,
                "reddit.com" to 1455000L,
                "github.com" to 700000L,
                "wikipedia.org" to 360000L
            )
        }
        return try {
            val type = object : TypeToken<Map<String, Long>>() {}.type
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun recordWebsiteUsage(domain: String, durationMillis: Long) {
        if (!isWebsiteUsageTrackingEnabled()) return
        if (loadIgnoredWebDomains().contains(domain)) return
        val current = loadWebsiteUsageStats().toMutableMap()
        current[domain] = (current[domain] ?: 0L) + durationMillis
        val sharedPreferences = context.getSharedPreferences("website_usage_tracker", Context.MODE_PRIVATE)
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

    fun loadAppLaunchLimitRules(): List<AppLaunchLimitRule> {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("launch_limit_rules", null)
        if (json.isNullOrEmpty()) return emptyList()

        return try {
            val type = object : TypeToken<List<AppLaunchLimitRule>>() {}.type
            val rawList: List<AppLaunchLimitRule> = Gson().fromJson(json, type) ?: emptyList()
            rawList.map { it.sanitize() }
        } catch (e: Exception) {
            Log.e("SavedPreferencesLoader", "Error loading launch limit rules", e)
            emptyList()
        }
    }

    fun saveAppLaunchLimitRules(rules: List<AppLaunchLimitRule>) {
        val sharedPreferences =
            context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
        val json = Gson().toJson(rules)
        sharedPreferences.edit().putString("launch_limit_rules", json).apply()
    }

    @Synchronized
    fun addAppLaunchLimitRule(rule: AppLaunchLimitRule) {
        val sanitized = rule.sanitize()
        val rules = loadAppLaunchLimitRules().toMutableList()
        rules.removeAll { it.packageName == sanitized.packageName }
        rules.add(sanitized)
        saveAppLaunchLimitRules(rules)
    }

    @Synchronized
    fun removeAppLaunchLimitRule(packageName: String) {
        val rules = loadAppLaunchLimitRules().toMutableList()
        rules.removeAll { it.packageName == packageName }
        saveAppLaunchLimitRules(rules)
    }

    fun getAppLaunchLimitRule(packageName: String): AppLaunchLimitRule? {
        return loadAppLaunchLimitRules().firstOrNull { it.packageName == packageName }?.sanitize()
    }

    // ==================== Launch Count Tracking (Per Period) ====================
    // Tracks current launch counts with period information to reset when period changes

    fun trackAppLaunch(packageName: String) {
        val rule = getAppLaunchLimitRule(packageName) ?: return
        val now = System.currentTimeMillis()
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
    ): Int {
        if (rule == null) {
            resetLaunchCount(packageName)
            return 0
        }

        val launchData = loadLaunchCountMap()
        val current = launchData[packageName] ?: return 0

        if (isLaunchDataExpired(current, rule, System.currentTimeMillis())) {
            resetLaunchCount(packageName)
            return 0
        }

        return current.count
    }

    fun resetLaunchCount(packageName: String) {
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



    fun loadBlockedWebsitesApps(): Set<String> {
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

    fun loadBlockedWebsites(): Set<String> {
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
        val today = TimeTools.getCurrentDate()
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
        return ReelsStatsManager.getInstance(context).loadDailyRecord(TimeTools.getCurrentDate()).totalScrolled
    }

    fun incrementReelsScrolled(packageName: String? = null) {
        ReelsStatsManager.getInstance(context).recordReelScroll(packageName)
    }

    fun getReelsWatchTimeSeconds(): Long {
        return ReelsStatsManager.getInstance(context).loadDailyRecord(TimeTools.getCurrentDate()).totalWatchTimeSeconds
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
        antiUninstallPrefs.edit().putBoolean("is_configuring_blocked", enabled).apply()

        context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            .edit().putBoolean("pin_bypass_lock", enabled).apply()

        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            .setPackage(context.packageName)
        context.sendBroadcast(intent)
    }

    // --- PIN Reset Cooldown Settings & State (Hard minimum 5 minutes) ---

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

    // --- Emergency Access Cooldown Settings & State (Hard minimum 5 minutes) ---

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
}
