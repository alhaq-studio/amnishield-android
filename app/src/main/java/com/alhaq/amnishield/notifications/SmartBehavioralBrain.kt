package com.alhaq.amnishield.notifications

import android.app.usage.UsageStatsManager
import android.content.Context
import com.alhaq.amnishield.utils.BlockingStatsManager
import com.alhaq.amnishield.utils.NotificationInboxStore
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.Calendar
import kotlin.random.Random

/**
 * The "Smart Brain" for behavioral notifications.
 * Analyzes user habits, real-time usage, blocked counts, daily goals, and streak state
 * to decide IF, WHEN, and WHAT to notify, with rich report insights.
 */
class SmartBehavioralBrain(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_notifications_prefs", Context.MODE_PRIVATE)
    private val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    private val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
    private val savedPreferencesLoader = SavedPreferencesLoader(context)

    enum class NotificationType {
        STREAK_MOTIVATION,
        STREAK_WARNING,
        PROTECTION_STATUS,
        FEATURE_DISCOVERY,
        RE_ENGAGEMENT,
        DAILY_PEAK_INSIGHT,
        NONE
    }

    data class NotificationPayload(
        val type: NotificationType,
        val title: String,
        val message: String,
        val actionText: String? = null,
        val actionIntentType: String? = null,
        val category: String = "smart",
        val customInsight: String = "",
        val actionTarget: String = "OPEN_STATS"
    )

    fun evaluateAndSelectNotification(): NotificationPayload {
        if (!reminderPrefs.getBoolean("reminders_enabled", true)) {
            return NotificationPayload(NotificationType.NONE, "", "")
        }

        if (isDndTime() || isFrequencyCapReached()) {
            return NotificationPayload(NotificationType.NONE, "", "")
        }

        val currentStreak = getStreakDays()
        val daysSinceLastOpen = getDaysSinceLastAppOpen()
        val isProtectionActive = isMainProtectionEnabled()
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val statsManager = BlockingStatsManager.getInstance(context)
        val todayStats = statsManager.getTodayStats()
        val totalBlocksToday = todayStats.appBlocksCount + todayStats.keywordBlocksCount + todayStats.viewBlocksCount
        val dailyGoalMins = appPrefs.getInt(
            "daily_screen_time_goal_minutes",
            reminderPrefs.getInt("daily_screen_time_goal_minutes", 180)
        )
        val screenTimeMins = queryTodayScreenTimeMinutes()
        val username = appPrefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() } ?: "Guardian"

        // 1. Critical Protection Warning (if disabled)
        if (!isProtectionActive && isCategoryEnabled("ProtectionStatus")) {
            return NotificationPayload(
                type = NotificationType.PROTECTION_STATUS,
                title = "Shield Protection Paused",
                message = "$username, your core blockers are currently inactive. Re-enable protection to guard your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} daily goal.",
                actionText = "Enable Now",
                actionIntentType = "ENABLE_PROTECTION",
                category = "blocking",
                customInsight = "Core Accessibility or Blocker toggles were detected as paused while screen time is ${NotificationInboxStore.formatMinutesShort(screenTimeMins)}.",
                actionTarget = "OPEN_BLOCKERS"
            )
        }

        // 2. Daily Screen Time Goal Threshold Check
        if (screenTimeMins >= dailyGoalMins && isCategoryEnabled("DailyInsights") && !hasSentToday("GOAL_THRESHOLD")) {
            markSentToday("GOAL_THRESHOLD")
            val overBy = (screenTimeMins - dailyGoalMins).coerceAtLeast(1)
            return NotificationPayload(
                type = NotificationType.DAILY_PEAK_INSIGHT,
                title = "Daily Goal Reached (${NotificationInboxStore.formatMinutesShort(screenTimeMins)})",
                message = "You've reached your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} screen time target (+${NotificationInboxStore.formatMinutesShort(overBy)}). Start a Focus Session to finish the day strong.",
                actionText = "Start Focus",
                actionIntentType = "OPEN_FOCUS",
                category = "report",
                customInsight = "Today's usage (${NotificationInboxStore.formatMinutesShort(screenTimeMins)}) passed your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} daily target with $totalBlocksToday distractions blocked.",
                actionTarget = "OPEN_STATS"
            )
        }

        // 3. Re-engagement (if user hasn't opened app in 3+ days)
        if (daysSinceLastOpen >= 3 && isCategoryEnabled("ReEngagement")) {
            return NotificationPayload(
                type = NotificationType.RE_ENGAGEMENT,
                title = "Shield Status Check-In",
                message = "AmniShield has been quietly guarding your device ($totalBlocksToday blocks today). Tap to review your latest focus report.",
                actionText = "View Report",
                actionIntentType = "OPEN_INBOX",
                category = "report",
                customInsight = "It has been $daysSinceLastOpen days since your last dashboard check-in. Your shield remains active.",
                actionTarget = "OPEN_STATS"
            )
        }

        // 4. Evening Streak Check / Motivation (Between 7 PM and 9 PM)
        if (hourOfDay in 19..21 && isCategoryEnabled("StreakMotivation")) {
            if (currentStreak > 0 && !hasSentToday("STREAK")) {
                markSentToday("STREAK")
                return getStreakMessage(currentStreak, totalBlocksToday, screenTimeMins, dailyGoalMins)
            }
        }

        // 5. Peak Usage Insight (Between 12 PM and 6 PM)
        if (hourOfDay in 12..18 && isCategoryEnabled("DailyInsights") && !hasSentToday("INSIGHT")) {
            if (Random.nextFloat() < 0.45f) {
                markSentToday("INSIGHT")
                return getPeakUsageInsight(totalBlocksToday, screenTimeMins, dailyGoalMins)
            }
        }

        // 6. Feature Discovery (Morning / Afternoon)
        if (hourOfDay in 10..16 && isCategoryEnabled("FeatureDiscovery") && !hasSentToday("DISCOVERY")) {
            if (Random.nextFloat() < 0.25f) {
                markSentToday("DISCOVERY")
                return getFeatureDiscoveryMessage()
            }
        }

        return NotificationPayload(NotificationType.NONE, "", "")
    }

    /**
     * Generates a rich on-demand smart coach payload (used for testing or manual snapshot generation)
     */
    fun generateInstantSmartSnapshot(): NotificationPayload {
        val statsManager = BlockingStatsManager.getInstance(context)
        val todayStats = statsManager.getTodayStats()
        val totalBlocks = todayStats.appBlocksCount + todayStats.keywordBlocksCount + todayStats.viewBlocksCount
        val streak = getStreakDays()
        val dailyGoalMins = appPrefs.getInt(
            "daily_screen_time_goal_minutes",
            reminderPrefs.getInt("daily_screen_time_goal_minutes", 180)
        )
        val screenTimeMins = queryTodayScreenTimeMinutes()
        val username = appPrefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() } ?: "Guardian"

        val remaining = dailyGoalMins - screenTimeMins
        val goalStatus = if (remaining >= 0) {
            "${NotificationInboxStore.formatMinutesShort(remaining)} remaining under your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} goal"
        } else {
            "${NotificationInboxStore.formatMinutesShort(-remaining)} above your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} goal"
        }

        return NotificationPayload(
            type = NotificationType.DAILY_PEAK_INSIGHT,
            title = "Smart Guardian Snapshot • $username",
            message = "Screen time: ${NotificationInboxStore.formatMinutesShort(screenTimeMins)} ($goalStatus). $totalBlocks distractions intercepted today.",
            actionText = "View Report",
            actionIntentType = "OPEN_INBOX",
            category = "smart",
            customInsight = "Live Behavioral Analysis: $goalStatus. Active streak: ${streak}d. Tap the quick action below to adjust your blockers or inspect app-by-app usage.",
            actionTarget = "OPEN_STATS"
        )
    }

    private fun queryTodayScreenTimeMinutes(): Int {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return 0
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis())
            val totalMs = stats.orEmpty()
                .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
                .sumOf { it.totalTimeInForeground }
            (totalMs / 60_000L).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    private fun getStreakMessage(
        streak: Int,
        totalBlocksToday: Int,
        screenTimeMins: Int,
        dailyGoalMins: Int
    ): NotificationPayload {
        val (title, msg) = when (streak) {
            1 -> "Day 1 Complete!" to "A great start! Every journey begins with a single step of self-control."
            3 -> "3-Day Focus Streak!" to "You're building momentum ($totalBlocksToday blocks today). Keep your shield strong."
            7 -> "1 Full Week Strong!" to "Incredible discipline! 7 days of guarding your focus and digital wellbeing."
            14 -> "2 Weeks Unstoppable!" to "Your habits are transforming. Two weeks of consistent digital mindfulness."
            30 -> "30-Day Master Guardian!" to "A full month of mastery! You are in command of your time and attention."
            else -> "$streak-Day Focus Streak!" to "Consistency is the secret to mastery. You've stayed protected for $streak days straight!"
        }
        return NotificationPayload(
            type = NotificationType.STREAK_MOTIVATION,
            title = title,
            message = msg,
            actionText = "View Streak Report",
            actionIntentType = "OPEN_INBOX",
            category = "streak",
            customInsight = "You've maintained a $streak-day streak while keeping today's usage at ${NotificationInboxStore.formatMinutesShort(screenTimeMins)} / ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)}.",
            actionTarget = "OPEN_FOCUS"
        )
    }

    private fun getPeakUsageInsight(
        totalBlocksToday: Int,
        screenTimeMins: Int,
        dailyGoalMins: Int
    ): NotificationPayload {
        val timeSavedMins = (totalBlocksToday * 4).coerceAtLeast(5)
        val insights = listOf(
            "Midday Focus Check" to "You've used ${NotificationInboxStore.formatMinutesShort(screenTimeMins)} of your ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} goal today and reclaimed ~${NotificationInboxStore.formatMinutesShort(timeSavedMins)} from distractions.",
            "Mindful Pause" to "Taking a 5-minute break from screens right now can boost your afternoon focus by 30%.",
            "Shield Efficiency" to "AmniShield intercepted $totalBlocksToday distractions today so you could stay in deep work."
        )
        val selected = insights.random()
        return NotificationPayload(
            type = NotificationType.DAILY_PEAK_INSIGHT,
            title = selected.first,
            message = selected.second,
            actionText = "See Breakdown",
            actionIntentType = "OPEN_INBOX",
            category = "smart",
            customInsight = "Peak-hour behavioral check: ${NotificationInboxStore.formatMinutesShort(screenTimeMins)} used out of ${NotificationInboxStore.formatMinutesShort(dailyGoalMins)} goal.",
            actionTarget = "OPEN_STATS"
        )
    }

    private fun getFeatureDiscoveryMessage(): NotificationPayload {
        val features = listOf(
            Triple("Discover Reel Blocker", "Block addictive short-form feeds (Reels, Shorts, TikTok) while keeping messaging accessible.", "OPEN_BLOCKERS"),
            Triple("Try Focus Mode", "Need deep work? Lock out distracting apps for a set timer with zero temptations.", "OPEN_FOCUS"),
            Triple("Custom Keyword Shield", "Add custom keywords or domains to personalize your content filter.", "OPEN_BLOCKERS")
        )
        val selected = features.random()
        return NotificationPayload(
            type = NotificationType.FEATURE_DISCOVERY,
            title = selected.first,
            message = selected.second,
            actionText = "Explore Tool",
            actionIntentType = selected.third,
            category = "smart",
            customInsight = "Smart recommendation based on your active protection profile.",
            actionTarget = selected.third
        )
    }

    private fun isDndTime(): Boolean {
        val dndEnabled = reminderPrefs.getBoolean("dnd_enabled", true)
        if (!dndEnabled) return false

        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = reminderPrefs.getInt("dnd_start_hour", 22) * 60 + reminderPrefs.getInt("dnd_start_minute", 0)
        val endMinutes = reminderPrefs.getInt("dnd_end_hour", 7) * 60 + reminderPrefs.getInt("dnd_end_minute", 0)

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    private fun isFrequencyCapReached(): Boolean {
        val lastSentTime = prefs.getLong("last_notification_time", 0L)
        val now = System.currentTimeMillis()
        val hoursSinceLast = (now - lastSentTime) / (1000 * 60 * 60)

        if (hoursSinceLast < 4) return true

        val todayKey = getTodayDateKey()
        val sentCountToday = prefs.getInt("count_$todayKey", 0)
        val maxAllowed = reminderPrefs.getInt("max_daily_notifications", 2)

        return sentCountToday >= maxAllowed
    }

    fun recordNotificationSent() {
        val now = System.currentTimeMillis()
        val todayKey = getTodayDateKey()
        val currentCount = prefs.getInt("count_$todayKey", 0)

        prefs.edit()
            .putLong("last_notification_time", now)
            .putInt("count_$todayKey", currentCount + 1)
            .apply()
    }

    private fun hasSentToday(categoryKey: String): Boolean {
        val todayKey = getTodayDateKey()
        return prefs.getBoolean("sent_${categoryKey}_$todayKey", false)
    }

    private fun markSentToday(categoryKey: String) {
        val todayKey = getTodayDateKey()
        prefs.edit().putBoolean("sent_${categoryKey}_$todayKey", true).apply()
    }

    private fun getTodayDateKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun isCategoryEnabled(category: String): Boolean {
        return reminderPrefs.getBoolean("smart_$category", true)
    }

    private fun getStreakDays(): Int {
        return BlockingStatsManager.getInstance(context).getCurrentStreak()
    }

    private fun getDaysSinceLastAppOpen(): Int {
        val lastOpen = appPrefs.getLong("last_app_open_time", System.currentTimeMillis())
        val diff = System.currentTimeMillis() - lastOpen
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun isMainProtectionEnabled(): Boolean {
        return appPrefs.getBoolean("adult_blocker_enabled", true) ||
                savedPreferencesLoader.loadBlockedApps().isNotEmpty() ||
                appPrefs.getBoolean("reel_blocker_enabled", false)
    }

    companion object {
        fun checkDoomscrollSpike(context: Context): NotificationPayload? {
            val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
            if (!reminderPrefs.getBoolean("master_notifications_enabled", true) ||
                !reminderPrefs.getBoolean("doomscroll_alerts_enabled", true)
            ) return null

            val stats = BlockingStatsManager.getInstance(context).getTodayStats()
            if (stats.viewBlocksCount >= 10) {
                return NotificationPayload(
                    type = NotificationType.DAILY_PEAK_INSIGHT,
                    title = "Doomscroll Spike Detected",
                    message = "AmniShield intercepted ${stats.viewBlocksCount} short-form Reels/Shorts today. Take a mindful break or start a Focus Session.",
                    actionText = "Start Focus",
                    actionIntentType = "OPEN_FOCUS",
                    category = "smart",
                    customInsight = "${stats.viewBlocksCount} short-form video scrolls were intercepted today.",
                    actionTarget = "OPEN_FOCUS"
                )
            }
            return null
        }

        fun checkPreBlockWarning(context: Context): NotificationPayload? {
            val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
            if (!reminderPrefs.getBoolean("master_notifications_enabled", true) ||
                !reminderPrefs.getBoolean("pre_block_warnings_enabled", true)
            ) return null
            return null
        }

        fun getRandomWellnessTip(): Pair<String, String> {
            val tips = listOf(
                "20-20-20 Eye Rule" to "Every 20 minutes, look at something 20 feet away for 20 seconds to relax digital eye strain.",
                "Intentional Unlocks" to "Before opening a social feed, ask yourself what specific item you're looking for.",
                "Deep Work Momentum" to "Batching notifications into 2 daily check-ins can boost cognitive focus by up to 40%."
            )
            return tips.random()
        }
    }
}
