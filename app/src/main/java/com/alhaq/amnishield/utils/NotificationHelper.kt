package com.alhaq.amnishield.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.activity.NotificationsActivity
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages notifications for blocking events, active protection status, daily reports,
 * unlock audit alerts, and achievements, while synchronizing alerts with NotificationInboxStore.
 */
class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)
    private val lastAlertElapsedByTarget = ConcurrentHashMap<String, Long>()

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for Android O and above
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DAILY_REPORTS,
                    "Daily Reports",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily summary of blocked content and focus sessions"
                },
                NotificationChannel(
                    CHANNEL_ACHIEVEMENTS,
                    "Achievements & Milestones",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when you reach blocking milestones"
                },
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Blocking Alerts",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Real-time notifications when content is blocked or shields are unlocked"
                },
                NotificationChannel(
                    CHANNEL_ACTIVE_PROTECTION,
                    "Active Protection Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent status summary while Always-On Shields or Block Rules are active"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    CHANNEL_ID_SECURITY_ALERTS,
                    "Protection Paused",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Critical alerts when accessibility protection is paused or disabled after device restart"
                    enableLights(true)
                    enableVibration(true)
                }
            )

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    private fun createInboxPendingIntent(requestCode: Int, itemId: Long): PendingIntent {
        val intent = Intent(context, NotificationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationsActivity.EXTRA_EXPAND_ITEM_ID, itemId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createMainDestinationPendingIntent(requestCode: Int, navigateTo: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", navigateTo)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Send daily summary notification and record rich report in NotificationInboxStore
     */
    fun showDailyReportNotification(): NotificationInboxStore.InboxItem {
        val statsManager = BlockingStatsManager.getInstance(context)
        val todayStats = statsManager.getTodayStats()
        val totalBlocks = todayStats.appBlocksCount + todayStats.keywordBlocksCount + todayStats.viewBlocksCount

        val appPrefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val username = appPrefs.getString("user_display_name", null)?.takeIf { it.isNotBlank() } ?: "Guardian"
        val dailyGoalMins = appPrefs.getInt(
            "daily_screen_time_goal_minutes",
            reminderPrefs.getInt("daily_screen_time_goal_minutes", 180)
        )
        val goalHours = dailyGoalMins / 60
        val goalRemMins = dailyGoalMins % 60
        val goalFormatted = if (goalHours > 0 && goalRemMins > 0) "${goalHours}h ${goalRemMins}m"
        else if (goalHours > 0) "${goalHours}h" else "${goalRemMins}m"

        val title = "Daily Shield Report for $username"
        val message = buildString {
            append("Daily Goal: $goalFormatted\n")
            if (totalBlocks > 0) {
                append("Blocked $totalBlocks distractions today:\n")
                if (todayStats.appBlocksCount > 0) append("• ${todayStats.appBlocksCount} app launches\n")
                if (todayStats.keywordBlocksCount > 0) append("• ${todayStats.keywordBlocksCount} keywords\n")
                if (todayStats.viewBlocksCount > 0) append("• ${todayStats.viewBlocksCount} reels/shorts\n")
            } else {
                append("No blocks needed today — great self-control!\n")
            }
            if (todayStats.focusSessionsCount > 0) {
                append("Completed ${todayStats.focusSessionsCount} focus sessions (${todayStats.totalFocusMinutes} min)")
            }
        }.trim()

        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = title,
            body = message,
            category = "report",
            customActionLabel = "View Full Usage & Stats",
            customActionTarget = "OPEN_STATS"
        )

        val inboxPendingIntent = createInboxPendingIntent(NOTIFICATION_ID_DAILY_REPORT, inboxItem.id)
        val statsPendingIntent = createMainDestinationPendingIntent(NOTIFICATION_ID_DAILY_REPORT + 100, "stats")

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REPORTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(
                if (totalBlocks > 0) "$totalBlocks distractions blocked • Goal: $goalFormatted"
                else "Great self-control today! • Goal: $goalFormatted"
            )
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(inboxPendingIntent)
            .addAction(0, "View Report", inboxPendingIntent)
            .addAction(0, "Open Stats", statsPendingIntent)
            .setAutoCancel(true)
            .build()

        if (areNotificationsAllowed()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_DAILY_REPORT, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    /**
     * Send achievement notification for reaching milestones
     */
    fun showAchievementNotification(achievementTitle: String, achievementDescription: String): NotificationInboxStore.InboxItem {
        val fullTitle = "Achievement Unlocked: $achievementTitle"
        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = fullTitle,
            body = achievementDescription,
            category = "achievement",
            customActionLabel = "View Focus & Streak Hub",
            customActionTarget = "OPEN_FOCUS"
        )

        val inboxPendingIntent = createInboxPendingIntent(NOTIFICATION_ID_ACHIEVEMENT, inboxItem.id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(fullTitle)
            .setContentText(achievementDescription)
            .setStyle(NotificationCompat.BigTextStyle().bigText(achievementDescription))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(inboxPendingIntent)
            .addAction(0, "Inspect Report", inboxPendingIntent)
            .setAutoCancel(true)
            .build()

        if (areNotificationsAllowed()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_ACHIEVEMENT, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    /**
     * Send real-time blocking alert (rate-limited per target to prevent rapid scroll spam)
     * and persist to NotificationInboxStore.
     */
    fun showBlockingAlert(blockedType: String, itemName: String, totalBlockedToday: Int = 1): NotificationInboxStore.InboxItem? {
        val throttleKey = "$blockedType::$itemName"
        val nowElapsed = SystemClock.elapsedRealtime()
        val lastElapsed = lastAlertElapsedByTarget[throttleKey] ?: 0L
        if (lastElapsed > 0L && (nowElapsed - lastElapsed) < BLOCKING_ALERT_COOLDOWN_MS) {
            return null
        }
        lastAlertElapsedByTarget[throttleKey] = nowElapsed

        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val alertsEnabled = prefs.getBoolean("blocking_alerts_enabled", true) &&
                reminderPrefs.getBoolean("smart_ProtectionStatus", true)

        val title = "$blockedType Triggered"
        val body = if (totalBlockedToday > 1) {
            "Blocked $itemName ($totalBlockedToday total distractions intercepted today)."
        } else {
            "Blocked $itemName to protect your focus."
        }

        // Always record in NotificationInboxStore so user can inspect blocking reports in-app
        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = title,
            body = body,
            category = "blocking",
            customActionLabel = "Configure Active Blockers",
            customActionTarget = "OPEN_BLOCKERS"
        )

        if (!alertsEnabled || !areNotificationsAllowed()) return inboxItem

        val inboxPendingIntent = createInboxPendingIntent(NOTIFICATION_ID_BLOCKING_ALERT, inboxItem.id)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(inboxPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(8000)
            .build()

        if (hasNotificationPermission()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_BLOCKING_ALERT, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    /**
     * Posts, updates, or dismisses the persistent Active Protection status notification.
     * Shows whenever at least one Always-On Shield or active Custom Block Rule is running.
     */
    fun updateActiveProtectionStatusNotification(
        activeAlwaysOnNames: List<String>,
        activeCustomRuleCount: Int
    ) {
        val totalActive = activeAlwaysOnNames.size + activeCustomRuleCount
        if (totalActive <= 0) {
            runCatching { notificationManager.cancel(NOTIFICATION_ID_ACTIVE_PROTECTION) }
            return
        }

        if (!hasNotificationPermission()) return

        val titleParts = buildList {
            if (activeAlwaysOnNames.isNotEmpty()) {
                add("${activeAlwaysOnNames.size} Always-On Shield${if (activeAlwaysOnNames.size == 1) "" else "s"}")
            }
            if (activeCustomRuleCount > 0) {
                add("$activeCustomRuleCount Custom Rule${if (activeCustomRuleCount == 1) "" else "s"}")
            }
        }
        val title = "AmniShield Active - ${titleParts.joinToString(", ")}"
        val summary = if (activeAlwaysOnNames.isNotEmpty()) {
            "Active shields: ${activeAlwaysOnNames.joinToString(" | ")}"
        } else {
            "Tap to manage your active block rules and schedules."
        }

        val blocksPendingIntent = createMainDestinationPendingIntent(NOTIFICATION_ID_ACTIVE_PROTECTION, "blocks")
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVE_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(blocksPendingIntent)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ACTIVE_PROTECTION, notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * Posts and records an audit alert whenever a protected Always-On Shield is unlocked or weakened.
     */
    fun showShieldUnlockAlert(shieldName: String, actionDetail: String): NotificationInboxStore.InboxItem {
        val title = "$shieldName Unlocked"
        val body = actionDetail.ifBlank { "$shieldName protection settings were modified after verification." }

        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = title,
            body = body,
            category = "blocking",
            customActionLabel = "Review Always-On Shields",
            customActionTarget = "OPEN_BLOCKERS"
        )

        val blocksPendingIntent = createMainDestinationPendingIntent(NOTIFICATION_ID_SHIELD_UNLOCK, "blocks")
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(blocksPendingIntent)
            .setAutoCancel(true)
            .build()

        if (hasNotificationPermission()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_SHIELD_UNLOCK, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    /**
     * Send focus / behavioral reminder and persist to NotificationInboxStore
     */
    fun showFocusReminder(title: String, message: String): NotificationInboxStore.InboxItem {
        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = title,
            body = message,
            category = "smart",
            customActionLabel = "Open Focus & Stats",
            customActionTarget = "OPEN_FOCUS"
        )

        val inboxPendingIntent = createInboxPendingIntent(NOTIFICATION_ID_FOCUS_REMINDER, inboxItem.id)
        val focusPendingIntent = createMainDestinationPendingIntent(NOTIFICATION_ID_FOCUS_REMINDER + 100, "focus")

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REPORTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(inboxPendingIntent)
            .addAction(0, "View Report", inboxPendingIntent)
            .addAction(0, "Start Focus", focusPendingIntent)
            .setAutoCancel(true)
            .build()

        if (areNotificationsAllowed()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_FOCUS_REMINDER, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    fun showDoomscrollAlert(title: String, message: String): NotificationInboxStore.InboxItem {
        return showFocusReminder(title, message)
    }

    fun showPreBlockWarning(title: String, message: String): NotificationInboxStore.InboxItem {
        return showFocusReminder(title, message)
    }

    fun showWellnessTipNotification(title: String, message: String): NotificationInboxStore.InboxItem {
        val inboxItem = NotificationInboxStore.add(
            context = context,
            title = title,
            body = message,
            category = "smart",
            customActionLabel = "Open Usage Dashboard",
            customActionTarget = "OPEN_STATS"
        )
        val inboxPendingIntent = createInboxPendingIntent(NOTIFICATION_ID_FOCUS_REMINDER + 1, inboxItem.id)
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REPORTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(inboxPendingIntent)
            .setAutoCancel(true)
            .build()
        if (areNotificationsAllowed()) {
            try {
                notificationManager.notify(NOTIFICATION_ID_FOCUS_REMINDER + 1, notification)
            } catch (_: SecurityException) {
            }
        }
        return inboxItem
    }

    /**
     * Check and trigger achievement notifications based on current stats
     */
    fun checkAndNotifyAchievements() {
        val statsManager = BlockingStatsManager.getInstance(context)
        val todayStats = statsManager.getTodayStats()
        val prefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)

        val todayTotal = todayStats.appBlocksCount + todayStats.keywordBlocksCount + todayStats.viewBlocksCount

        // Check daily milestones in descending order so higher milestones are never masked
        when {
            todayTotal >= 100 && !prefs.getBoolean("daily_100_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "Unstoppable Shield!",
                    "100+ blocks in a single day! Your digital discipline is rock solid."
                )
                prefs.edit().putBoolean("daily_100_${java.time.LocalDate.now()}", true).apply()
            }
            todayTotal >= 50 && !prefs.getBoolean("daily_50_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "Focus Champion!",
                    "50 distractions blocked today! You're in complete command of your time."
                )
                prefs.edit().putBoolean("daily_50_${java.time.LocalDate.now()}", true).apply()
            }
            todayTotal >= 25 && !prefs.getBoolean("daily_25_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "Staying Focused!",
                    "25 blocks today! Your focus is getting stronger."
                )
                prefs.edit().putBoolean("daily_25_${java.time.LocalDate.now()}", true).apply()
            }
            todayTotal >= 10 && !prefs.getBoolean("daily_10_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "First Shield!",
                    "You've blocked 10 distractions today. Great start!"
                )
                prefs.edit().putBoolean("daily_10_${java.time.LocalDate.now()}", true).apply()
            }
        }

        // Check focus session milestones in descending order
        when {
            todayStats.totalFocusMinutes >= 180 && !prefs.getBoolean("focus_180_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "Deep Work Master!",
                    "3 hours of focused work today! Incredible dedication."
                )
                prefs.edit().putBoolean("focus_180_${java.time.LocalDate.now()}", true).apply()
            }
            todayStats.totalFocusMinutes >= 60 && !prefs.getBoolean("focus_60_${java.time.LocalDate.now()}", false) -> {
                showAchievementNotification(
                    "One Hour Focused!",
                    "You've completed 60 minutes of focus time today!"
                )
                prefs.edit().putBoolean("focus_60_${java.time.LocalDate.now()}", true).apply()
            }
        }

        // Check lifetime milestones in descending order
        val totalBlocks = statsManager.getTotalBlocksCount()
        when {
            totalBlocks >= 1000 && !prefs.getBoolean("lifetime_1000", false) -> {
                showAchievementNotification(
                    "Legendary Guardian!",
                    "1,000 lifetime blocks! You've reclaimed hours of your life."
                )
                prefs.edit().putBoolean("lifetime_1000", true).apply()
            }
            totalBlocks >= 500 && !prefs.getBoolean("lifetime_500", false) -> {
                showAchievementNotification(
                    "Master of Focus!",
                    "500 lifetime blocks! Your commitment to productivity is inspiring."
                )
                prefs.edit().putBoolean("lifetime_500", true).apply()
            }
            totalBlocks >= 100 && !prefs.getBoolean("lifetime_100", false) -> {
                showAchievementNotification(
                    "Centurion!",
                    "100 lifetime blocks! You're building strong digital habits."
                )
                prefs.edit().putBoolean("lifetime_100", true).apply()
            }
        }
    }

    /**
     * Show high-priority alert when Accessibility Service is disabled after boot or killed by OEM battery saver.
     */
    fun showProtectionPausedNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PROTECTION_PAUSED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Protection Paused"
        val message = "AmniShield accessibility service is inactive. Tap to reopen AmniShield and restore active blocking protection."

        NotificationInboxStore.add(
            context = context,
            title = title,
            body = message,
            category = "blocking",
            customActionLabel = "Restore Protection",
            customActionTarget = "OPEN_BLOCKERS"
        )

        if (!areNotificationsAllowed()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SECURITY_ALERTS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_PROTECTION_PAUSED, notification)
        } catch (_: SecurityException) {
        }
    }

    /**
     * Check if app has notification permission (Android 13+ runtime permission + system toggle).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    /**
     * Verify both user master toggle and Android notification permission.
     */
    fun areNotificationsAllowed(): Boolean {
        val reminderPrefs = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val masterEnabled = reminderPrefs.getBoolean("master_notifications_enabled", true)
        return masterEnabled && hasNotificationPermission()
    }

    companion object {
        const val CHANNEL_DAILY_REPORTS = "daily_reports"
        const val CHANNEL_ACHIEVEMENTS = "achievements"
        const val CHANNEL_ALERTS = "blocking_alerts"
        const val CHANNEL_ACTIVE_PROTECTION = "active_protection_status"
        const val CHANNEL_ID_SECURITY_ALERTS = "security_alerts"

        private const val BLOCKING_ALERT_COOLDOWN_MS = 15_000L

        private const val NOTIFICATION_ID_DAILY_REPORT = 1001
        private const val NOTIFICATION_ID_ACHIEVEMENT = 1002
        private const val NOTIFICATION_ID_BLOCKING_ALERT = 1003
        private const val NOTIFICATION_ID_FOCUS_REMINDER = 1004
        private const val NOTIFICATION_ID_ACTIVE_PROTECTION = 1005
        private const val NOTIFICATION_ID_SHIELD_UNLOCK = 1006
        const val NOTIFICATION_ID_PROTECTION_PAUSED = 6001

        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
