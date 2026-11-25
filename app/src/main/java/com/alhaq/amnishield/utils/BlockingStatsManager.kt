package com.alhaq.amnishield.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Manager class for tracking and persisting blocking statistics.
 * Tracks app blocks, keyword blocks, view/reel blocks, and focus sessions.
 */
class BlockingStatsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Data classes for different types of block events
    data class BlockEvent(
        val timestamp: Long,
        val type: BlockType,
        val packageName: String? = null,
        val keyword: String? = null,
        val viewId: String? = null,
        val details: String? = null
    )

    data class FocusSession(
        val startTime: Long,
        val endTime: Long,
        val durationMinutes: Long
    )

    enum class BlockType {
        APP_BLOCK,
        KEYWORD_BLOCK,
        VIEW_REEL_BLOCK,
        FOCUS_SESSION
    }

    /**
     * Record an app block event
     */
    fun recordAppBlock(packageName: String, details: String? = null) {
        val event = BlockEvent(
            timestamp = System.currentTimeMillis(),
            type = BlockType.APP_BLOCK,
            packageName = packageName,
            details = details
        )
        saveBlockEvent(event)
        maybeTriggerBlockingAlert("App", resolveReadableLabel(packageName, details))
        
        // Check for achievements
        checkAndNotifyAchievements()
    }

    /**
     * Record a keyword block event
     */
    fun recordKeywordBlock(keyword: String, packageName: String? = null) {
        val event = BlockEvent(
            timestamp = System.currentTimeMillis(),
            type = BlockType.KEYWORD_BLOCK,
            keyword = keyword,
            packageName = packageName
        )
        saveBlockEvent(event)
        maybeTriggerBlockingAlert("Keyword", keyword.ifBlank { "Filtered term" })
        
        // Check for achievements
        checkAndNotifyAchievements()
    }

    /**
     * Record a view/reel block event
     */
    fun recordViewBlock(packageName: String, viewId: String? = null) {
        val event = BlockEvent(
            timestamp = System.currentTimeMillis(),
            type = BlockType.VIEW_REEL_BLOCK,
            packageName = packageName,
            viewId = viewId
        )
        saveBlockEvent(event)
        maybeTriggerBlockingAlert("Reel/Short", resolveReadableLabel(packageName, viewId))
        
        // Check for achievements
        checkAndNotifyAchievements()
    }

    /**
     * Record a focus session
     */
    fun recordFocusSession(startTime: Long, endTime: Long) {
        val durationMinutes = (endTime - startTime) / (60 * 1000)
        val session = FocusSession(startTime, endTime, durationMinutes)
        saveFocusSession(session)
        
        // Check for achievements
        checkAndNotifyAchievements()
    }

    private fun resolveReadableLabel(packageName: String?, fallback: String?): String {
        if (!packageName.isNullOrBlank()) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(info).toString()
                if (label.isNotBlank()) return label
            } catch (_: Exception) {
                return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
        return fallback?.takeIf { it.isNotBlank() } ?: "Distraction"
    }

    private fun maybeTriggerBlockingAlert(blockedType: String, itemName: String) {
        val today = getTodayStats()
        val totalToday = today.appBlocksCount + today.keywordBlocksCount + today.viewBlocksCount
        val lastAlertTime = prefs.getLong("last_blocking_alert_time", 0L)
        val now = System.currentTimeMillis()
        // Trigger on the 1st block of the day or every 5th block, throttled to at least 15 minutes apart
        if ((totalToday == 1 || totalToday % 5 == 0) && (now - lastAlertTime >= 15 * 60 * 1000L)) {
            prefs.edit().putLong("last_blocking_alert_time", now).apply()
            NotificationHelper.getInstance(context).showBlockingAlert(
                blockedType = blockedType,
                itemName = itemName,
                totalBlockedToday = totalToday
            )
        }
    }
    
    /**
     * Check current stats and notify achievements if enabled
     */
    private fun checkAndNotifyAchievements() {
        val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val achievementsEnabled = reminderPrefs.getBoolean("achievement_enabled", true)
        
        if (achievementsEnabled) {
            val notificationHelper = NotificationHelper.getInstance(context)
            notificationHelper.checkAndNotifyAchievements()
        }
    }

    /**
     * Get all block events for a specific date
     */
    fun getBlockEventsForDate(date: LocalDate): List<BlockEvent> {
        val key = getDateKey(date)
        val json = prefs.getString(key, null) ?: return emptyList()
        
        return try {
            val jsonArray = JSONArray(json)
            val events = mutableListOf<BlockEvent>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                events.add(BlockEvent(
                    timestamp = obj.getLong("timestamp"),
                    type = BlockType.valueOf(obj.getString("type")),
                    packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
                    keyword = obj.optString("keyword").takeIf { it.isNotEmpty() },
                    viewId = obj.optString("viewId").takeIf { it.isNotEmpty() },
                    details = obj.optString("details").takeIf { it.isNotEmpty() }
                ))
            }
            
            events
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get focus sessions for a specific date
     */
    fun getFocusSessionsForDate(date: LocalDate): List<FocusSession> {
        val key = getFocusSessionKey(date)
        val json = prefs.getString(key, null) ?: return emptyList()
        
        return try {
            val jsonArray = JSONArray(json)
            val sessions = mutableListOf<FocusSession>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                sessions.add(FocusSession(
                    startTime = obj.getLong("startTime"),
                    endTime = obj.getLong("endTime"),
                    durationMinutes = obj.getLong("durationMinutes")
                ))
            }
            
            sessions
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get statistics summary for a specific date
     */
    fun getStatsSummaryForDate(date: LocalDate): StatsSummary {
        val events = getBlockEventsForDate(date)
        val focusSessions = getFocusSessionsForDate(date)
        
        return StatsSummary(
            appBlocksCount = events.count { it.type == BlockType.APP_BLOCK },
            keywordBlocksCount = events.count { it.type == BlockType.KEYWORD_BLOCK },
            viewBlocksCount = events.count { it.type == BlockType.VIEW_REEL_BLOCK },
            focusSessionsCount = focusSessions.size,
            totalFocusMinutes = focusSessions.sumOf { it.durationMinutes }
        )
    }

    /**
     * Get statistics for today
     */
    fun getTodayStats(): StatsSummary {
        return getStatsSummaryForDate(LocalDate.now())
    }

    fun getAppsBlockedCount(period: String = "today"): Int {
        return getTodayStats().appBlocksCount
    }

    fun getReelsBlockedCount(period: String = "today"): Int {
        return getTodayStats().viewBlocksCount
    }

    fun getKeywordsBlockedCount(period: String = "today"): Int {
        return getTodayStats().keywordBlocksCount
    }

    fun getThreatsBlockedCount(period: String = "today"): Int {
        val dnsPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val dnsEnabled = dnsPrefs.getBoolean("website_blocker_enabled", false) ||
                dnsPrefs.getBoolean("dns_protection_enabled", false)
        val events = getBlockEventsForDate(LocalDate.now())
        val explicitThreats = events.count {
            it.details?.contains("domain", ignoreCase = true) == true ||
                    it.details?.contains("url", ignoreCase = true) == true ||
                    it.details?.contains("threat", ignoreCase = true) == true
        }
        return if (explicitThreats > 0) explicitThreats else if (dnsEnabled) (events.count { it.type == BlockType.KEYWORD_BLOCK } / 2) else 0
    }

    fun getCurrentStreak(): Int {
        val streakPrefs = context.getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
        val savedStreak = streakPrefs.getInt("current_streak", 0)
        if (savedStreak > 0) return savedStreak
        var consecutiveDays = 0
        var checkDate = LocalDate.now()
        for (i in 0..30) {
            val summary = getStatsSummaryForDate(checkDate)
            val hasActivity = (summary.appBlocksCount + summary.keywordBlocksCount + summary.viewBlocksCount + summary.focusSessionsCount) > 0
            if (hasActivity) {
                consecutiveDays++
                checkDate = checkDate.minusDays(1)
            } else if (i == 0) {
                // Allow today to still be in progress if yesterday had activity
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        return consecutiveDays
    }

    /**
     * Get total count of all block events ever recorded (lifetime)
     */
    fun getTotalBlocksCount(): Int {
        var total = 0
        prefs.all.keys.forEach { key ->
            if (key.startsWith(DATE_PREFIX)) {
                try {
                    val json = prefs.getString(key, null)
                    if (json != null) {
                        val jsonArray = JSONArray(json)
                        total += jsonArray.length()
                    }
                } catch (e: Exception) {
                    // Skip invalid
                }
            }
        }
        return total
    }

    /**
     * Clear old data (older than specified days)
     */
    fun clearOldData(daysToKeep: Int = 30) {
        val editor = prefs.edit()
        val cutoffDate = LocalDate.now().minusDays(daysToKeep.toLong())
        
        prefs.all.keys.forEach { key ->
            if (key.startsWith(DATE_PREFIX) || key.startsWith(FOCUS_PREFIX)) {
                try {
                    val dateStr = key.substringAfter("_")
                    val date = LocalDate.parse(dateStr)
                    if (date.isBefore(cutoffDate)) {
                        editor.remove(key)
                    }
                } catch (e: Exception) {
                    // Skip invalid keys
                }
            }
        }
        
        editor.apply()
    }

    // Private helper methods

    private fun saveBlockEvent(event: BlockEvent) {
        val date = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(event.timestamp),
            ZoneId.systemDefault()
        )
        val key = getDateKey(date)
        
        val existingEvents = getBlockEventsForDate(date).toMutableList()
        existingEvents.add(event)
        
        val jsonArray = JSONArray()
        existingEvents.forEach { e ->
            val obj = JSONObject().apply {
                put("timestamp", e.timestamp)
                put("type", e.type.name)
                e.packageName?.let { put("packageName", it) }
                e.keyword?.let { put("keyword", it) }
                e.viewId?.let { put("viewId", it) }
                e.details?.let { put("details", it) }
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun saveFocusSession(session: FocusSession) {
        val date = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(session.startTime),
            ZoneId.systemDefault()
        )
        val key = getFocusSessionKey(date)
        
        val existingSessions = getFocusSessionsForDate(date).toMutableList()
        existingSessions.add(session)
        
        val jsonArray = JSONArray()
        existingSessions.forEach { s ->
            val obj = JSONObject().apply {
                put("startTime", s.startTime)
                put("endTime", s.endTime)
                put("durationMinutes", s.durationMinutes)
            }
            jsonArray.put(obj)
        }
        
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    private fun getDateKey(date: LocalDate): String {
        return "${DATE_PREFIX}${date}"
    }

    private fun getFocusSessionKey(date: LocalDate): String {
        return "${FOCUS_PREFIX}${date}"
    }

    data class StatsSummary(
        val appBlocksCount: Int,
        val keywordBlocksCount: Int,
        val viewBlocksCount: Int,
        val focusSessionsCount: Int,
        val totalFocusMinutes: Long
    )

    companion object {
        private const val PREFS_NAME = "blocking_stats"
        private const val DATE_PREFIX = "events_"
        private const val FOCUS_PREFIX = "focus_"

        @Volatile
        private var instance: BlockingStatsManager? = null

        fun getInstance(context: Context): BlockingStatsManager {
            return instance ?: synchronized(this) {
                instance ?: BlockingStatsManager(context).also { instance = it }
            }
        }
    }
}
