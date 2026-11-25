package com.alhaq.amnishield.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Persistent store for in-app Notification Inbox items with rich structured report payloads.
 * Every notification fired by NotificationHelper or SmartNotificationScheduler is recorded here
 * with a live snapshot of screen time, daily goal progress, blocked counts, and behavioral insights.
 */
object NotificationInboxStore {

    private const val PREFS_NAME = "notification_inbox"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    data class InboxItem(
        val id: Long = System.currentTimeMillis(),
        val title: String,
        val body: String,
        val category: String,
        val timestamp: Long = System.currentTimeMillis(),
        val read: Boolean = false,
        val screenTimeMinutes: Int = 0,
        val dailyGoalMinutes: Int = 180,
        val topAppsSummary: String = "",
        val appsBlockedToday: Int = 0,
        val reelsBlockedToday: Int = 0,
        val keywordsBlockedToday: Int = 0,
        val threatsBlockedToday: Int = 0,
        val streakDays: Int = 0,
        val protectionScore: Int = 85,
        val behavioralInsight: String = "",
        val actionLabel: String = "",
        val actionTarget: String = ""
    ) {
        val totalBlockedToday: Int
            get() = appsBlockedToday + reelsBlockedToday + keywordsBlockedToday + threatsBlockedToday
    }

    private data class LiveDeviceSnapshot(
        val screenTimeMinutes: Int,
        val dailyGoalMinutes: Int,
        val topAppsSummary: String,
        val appsBlockedToday: Int,
        val reelsBlockedToday: Int,
        val keywordsBlockedToday: Int,
        val threatsBlockedToday: Int,
        val streakDays: Int,
        val protectionScore: Int,
        val username: String
    )

    private fun captureLiveSnapshot(context: Context): LiveDeviceSnapshot {
        val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val dailyGoalMinutes = appPrefs.getInt(
            "daily_screen_time_goal_minutes",
            reminderPrefs.getInt("daily_screen_time_goal_minutes", 180)
        ).coerceAtLeast(15)
        val username = appPrefs.getString("user_display_name", null)
            ?.takeIf { it.isNotBlank() } ?: "Guardian"

        val statsManager = try {
            BlockingStatsManager.getInstance(context)
        } catch (_: Exception) {
            null
        }

        val appsBlocked = statsManager?.getAppsBlockedCount("today") ?: 0
        val reelsBlocked = statsManager?.getReelsBlockedCount("today") ?: 0
        val keywordsBlocked = statsManager?.getKeywordsBlockedCount("today") ?: 0
        val threatsBlocked = statsManager?.getThreatsBlockedCount("today") ?: 0
        val streakDays = statsManager?.getCurrentStreak() ?: 0

        var screenTimeMinutes = 0
        var topAppsSummary = ""
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis
                val now = System.currentTimeMillis()
                val rawStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
                if (!rawStats.isNullOrEmpty()) {
                    val pm = context.packageManager
                    val mergedByPkg = mutableMapOf<String, Long>()
                    for (stat in rawStats) {
                        if (stat.totalTimeInForeground > 0 && stat.packageName != context.packageName) {
                            mergedByPkg[stat.packageName] =
                                (mergedByPkg[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                        }
                    }
                    val totalMs = mergedByPkg.values.sum()
                    screenTimeMinutes = (totalMs / 60_000L).toInt().coerceAtLeast(0)

                    val top3 = mergedByPkg.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .mapNotNull { entry ->
                            val mins = (entry.value / 60_000L).toInt()
                            if (mins <= 0) return@mapNotNull null
                            val label = try {
                                val appInfo = pm.getApplicationInfo(entry.key, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                entry.key.substringAfterLast('.')
                            }
                            "$label (${formatMinutesShort(mins)})"
                        }
                    if (top3.isNotEmpty()) {
                        topAppsSummary = top3.joinToString(" • ")
                    }
                }
            }
        } catch (_: Exception) {
            // UsageStats permission may not be granted yet
        }

        val goalRatio = if (dailyGoalMinutes > 0) {
            screenTimeMinutes.toFloat() / dailyGoalMinutes.toFloat()
        } else 0.5f

        val baseScore = when {
            screenTimeMinutes == 0 -> 92
            goalRatio <= 0.5f -> 96
            goalRatio <= 0.8f -> 88
            goalRatio <= 1.0f -> 78
            goalRatio <= 1.25f -> 64
            else -> 48
        }
        val bonus = ((streakDays.coerceAtMost(14)) + ((appsBlocked + reelsBlocked + keywordsBlocked + threatsBlocked).coerceAtMost(10)) / 2)
        val protectionScore = (baseScore + bonus).coerceIn(35, 100)

        return LiveDeviceSnapshot(
            screenTimeMinutes = screenTimeMinutes,
            dailyGoalMinutes = dailyGoalMinutes,
            topAppsSummary = topAppsSummary,
            appsBlockedToday = appsBlocked,
            reelsBlockedToday = reelsBlocked,
            keywordsBlockedToday = keywordsBlocked,
            threatsBlockedToday = threatsBlocked,
            streakDays = streakDays,
            protectionScore = protectionScore,
            username = username
        )
    }

    private fun buildDefaultInsight(
        category: String,
        snapshot: LiveDeviceSnapshot
    ): String {
        val totalBlocked = snapshot.appsBlockedToday + snapshot.reelsBlockedToday +
                snapshot.keywordsBlockedToday + snapshot.threatsBlockedToday
        val timeSavedMins = (snapshot.appsBlockedToday * 5) + (snapshot.reelsBlockedToday * 2) +
                (snapshot.keywordsBlockedToday * 3) + (snapshot.threatsBlockedToday * 3)
        val remainingMins = (snapshot.dailyGoalMinutes - snapshot.screenTimeMinutes)

        return when (category.lowercase()) {
            "report" -> {
                val goalText = if (remainingMins >= 0) {
                    "${formatMinutesShort(remainingMins)} remaining under your ${formatMinutesShort(snapshot.dailyGoalMinutes)} daily goal."
                } else {
                    "${formatMinutesShort(-remainingMins)} over your ${formatMinutesShort(snapshot.dailyGoalMinutes)} daily goal."
                }
                val savedText = if (timeSavedMins > 0) {
                    " AmniShield reclaimed ~${formatMinutesShort(timeSavedMins)} across $totalBlocked blocks today."
                } else {
                    " Keep your blockers active to automatically reclaim focus time."
                }
                "$goalText$savedText"
            }
            "blocking" -> {
                if (totalBlocked > 0) {
                    "Shield intercepted $totalBlocked distractions today (~${formatMinutesShort(timeSavedMins)} saved). Momentum is strong, ${snapshot.username}."
                } else {
                    "Real-time shield is monitoring apps, reels, keywords, and web domains."
                }
            }
            "achievement", "streak" -> {
                if (snapshot.streakDays > 0) {
                    "${snapshot.streakDays}-day active focus streak! Consistency rewires habits and strengthens self-command."
                } else {
                    "Every distraction blocked builds your daily discipline streak."
                }
            }
            else -> {
                if (snapshot.screenTimeMinutes > snapshot.dailyGoalMinutes) {
                    "Screen time has passed your ${formatMinutesShort(snapshot.dailyGoalMinutes)} goal. Consider enabling Focus Mode for deep recovery."
                } else {
                    "You are tracking at ${formatMinutesShort(snapshot.screenTimeMinutes)} / ${formatMinutesShort(snapshot.dailyGoalMinutes)} today with a ${snapshot.protectionScore}% Shield Score."
                }
            }
        }
    }

    private fun defaultActionForCategory(category: String): Pair<String, String> {
        return when (category.lowercase()) {
            "report" -> "View Full Usage & Stats" to "OPEN_STATS"
            "blocking" -> "Configure Active Blockers" to "OPEN_BLOCKERS"
            "achievement", "streak" -> "View Focus & Streak Hub" to "OPEN_FOCUS"
            "smart" -> "Tune Smart Reminders" to "OPEN_REMINDERS"
            else -> "Open Usage Dashboard" to "OPEN_STATS"
        }
    }

    fun add(
        context: Context,
        title: String,
        body: String,
        category: String = "general",
        customInsight: String = "",
        customActionLabel: String = "",
        customActionTarget: String = ""
    ): InboxItem {
        val snapshot = captureLiveSnapshot(context)
        val (defaultLabel, defaultTarget) = defaultActionForCategory(category)
        val item = InboxItem(
            id = System.currentTimeMillis(),
            title = title,
            body = body,
            category = category,
            timestamp = System.currentTimeMillis(),
            read = false,
            screenTimeMinutes = snapshot.screenTimeMinutes,
            dailyGoalMinutes = snapshot.dailyGoalMinutes,
            topAppsSummary = snapshot.topAppsSummary,
            appsBlockedToday = snapshot.appsBlockedToday,
            reelsBlockedToday = snapshot.reelsBlockedToday,
            keywordsBlockedToday = snapshot.keywordsBlockedToday,
            threatsBlockedToday = snapshot.threatsBlockedToday,
            streakDays = snapshot.streakDays,
            protectionScore = snapshot.protectionScore,
            behavioralInsight = customInsight.ifBlank { buildDefaultInsight(category, snapshot) },
            actionLabel = customActionLabel.ifBlank { defaultLabel },
            actionTarget = customActionTarget.ifBlank { defaultTarget }
        )
        return addRichItem(context, item)
    }

    fun addRichItem(context: Context, item: InboxItem): InboxItem {
        val current = getAllRaw(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveItems(context, current.take(MAX_ITEMS))
        return item
    }

    fun getAll(context: Context): List<InboxItem> {
        val raw = getAllRaw(context)
        if (raw.isEmpty()) return emptyList()
        val fallbackSnapshot by lazy { captureLiveSnapshot(context) }
        return raw.map { item ->
            val (defaultLabel, defaultTarget) = defaultActionForCategory(item.category)
            item.copy(
                dailyGoalMinutes = if (item.dailyGoalMinutes > 0) item.dailyGoalMinutes else fallbackSnapshot.dailyGoalMinutes,
                behavioralInsight = item.behavioralInsight.ifBlank {
                    buildDefaultInsight(item.category, fallbackSnapshot)
                },
                actionLabel = item.actionLabel.ifBlank { defaultLabel },
                actionTarget = item.actionTarget.ifBlank { defaultTarget }
            )
        }
    }

    private fun getAllRaw(context: Context): List<InboxItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<InboxItem>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                list.add(
                    InboxItem(
                        id = obj.optLong("id", ts + i),
                        title = obj.optString("title", "Notification"),
                        body = obj.optString("body", ""),
                        category = obj.optString("category", "general"),
                        timestamp = ts,
                        read = obj.optBoolean("read", false),
                        screenTimeMinutes = obj.optInt("screenTimeMinutes", 0),
                        dailyGoalMinutes = obj.optInt("dailyGoalMinutes", 180),
                        topAppsSummary = obj.optString("topAppsSummary", ""),
                        appsBlockedToday = obj.optInt("appsBlockedToday", 0),
                        reelsBlockedToday = obj.optInt("reelsBlockedToday", 0),
                        keywordsBlockedToday = obj.optInt("keywordsBlockedToday", 0),
                        threatsBlockedToday = obj.optInt("threatsBlockedToday", 0),
                        streakDays = obj.optInt("streakDays", 0),
                        protectionScore = obj.optInt("protectionScore", 85),
                        behavioralInsight = obj.optString("behavioralInsight", ""),
                        actionLabel = obj.optString("actionLabel", ""),
                        actionTarget = obj.optString("actionTarget", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getUnreadCount(context: Context): Int {
        return getAllRaw(context).count { !it.read }
    }

    fun markAsRead(context: Context, itemId: Long) {
        val updated = getAllRaw(context).map {
            if (it.id == itemId) it.copy(read = true) else it
        }
        saveItems(context, updated)
    }

    fun markAllRead(context: Context) {
        val updated = getAllRaw(context).map { it.copy(read = true) }
        saveItems(context, updated)
    }

    fun removeById(context: Context, itemId: Long): InboxItem? {
        val current = getAllRaw(context).toMutableList()
        val index = current.indexOfFirst { it.id == itemId }
        if (index == -1) return null
        val removed = current.removeAt(index)
        saveItems(context, current)
        return removed
    }

    fun restoreItem(context: Context, item: InboxItem) {
        val current = getAllRaw(context).toMutableList()
        current.removeAll { it.id == item.id }
        current.add(item)
        current.sortByDescending { it.timestamp }
        saveItems(context, current.take(MAX_ITEMS))
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ITEMS)
            .apply()
    }

    private fun saveItems(context: Context, items: List<InboxItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("body", item.body)
                    put("category", item.category)
                    put("timestamp", item.timestamp)
                    put("read", item.read)
                    put("screenTimeMinutes", item.screenTimeMinutes)
                    put("dailyGoalMinutes", item.dailyGoalMinutes)
                    put("topAppsSummary", item.topAppsSummary)
                    put("appsBlockedToday", item.appsBlockedToday)
                    put("reelsBlockedToday", item.reelsBlockedToday)
                    put("keywordsBlockedToday", item.keywordsBlockedToday)
                    put("threatsBlockedToday", item.threatsBlockedToday)
                    put("streakDays", item.streakDays)
                    put("protectionScore", item.protectionScore)
                    put("behavioralInsight", item.behavioralInsight)
                    put("actionLabel", item.actionLabel)
                    put("actionTarget", item.actionTarget)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    fun formatMinutesShort(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
