package com.alhaq.amnishield.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Manages notifications for daily reports, focus reminders, doomscroll alerts,
 * pre-block warnings, achievements, and digital wellbeing tips.
 * All notification channels and types can be granularly toggled in settings.
 */
class NotificationHelper(private val context: Context) {

    private val reminderPrefs by lazy {
        context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
    }

    private val achievementPrefs by lazy {
        context.getSharedPreferences("achievement_notifications", Context.MODE_PRIVATE)
    }

    companion object {
        const val CHANNEL_ID_REPORTS = "daily_reports"
        const val CHANNEL_ID_REMINDERS = "reminders"
        const val CHANNEL_ID_ACHIEVEMENTS = "achievements"
        const val CHANNEL_ID_TIPS = "wellness_tips"
        const val CHANNEL_ID_BLOCK_ALERTS = "blocking_alerts"

        const val NOTIFICATION_ID_DAILY_REPORT = 1001
        const val NOTIFICATION_ID_REMINDER = 2001
        const val NOTIFICATION_ID_ACHIEVEMENT = 3001
        const val NOTIFICATION_ID_WELLNESS_TIP = 4001
        const val NOTIFICATION_ID_BLOCK_ALERT = 5001

        @Volatile
        private var instance: NotificationHelper? = null

        fun getInstance(context: Context): NotificationHelper {
            return instance ?: synchronized(this) {
                instance ?: NotificationHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels for Android O and above
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Daily Reports Channel
            val reportsChannel = NotificationChannel(
                CHANNEL_ID_REPORTS,
                "Daily Reports & Summaries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily summaries of your screen time, focus, and blocked distractions"
                enableLights(true)
                enableVibration(false)
            }

            // 2. Reminders Channel
            val remindersChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Focus Reminders & Nudges",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart reminders, doomscroll spike alerts, and pre-block notices"
                enableLights(true)
                enableVibration(true)
            }

            // 3. Achievements Channel
            val achievementsChannel = NotificationChannel(
                CHANNEL_ID_ACHIEVEMENTS,
                "Productivity Milestones & Streaks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Celebrate focus streaks and daily shield milestones"
                enableLights(true)
                enableVibration(false)
            }

            // 4. Digital Wellbeing Tips Channel
            val tipsChannel = NotificationChannel(
                CHANNEL_ID_TIPS,
                "Digital Wellbeing Tips",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mindful tips to reduce digital fatigue and enhance discipline"
                enableLights(false)
                enableVibration(false)
            }

            // 5. Blocking Alerts Channel
            val blockAlertsChannel = NotificationChannel(
                CHANNEL_ID_BLOCK_ALERTS,
                "Real-time Blocking Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Real-time notifications when apps, keywords, or reels are intercepted"
                enableLights(true)
                enableVibration(false)
            }

            notificationManager.createNotificationChannels(
                listOf(reportsChannel, remindersChannel, achievementsChannel, tipsChannel, blockAlertsChannel)
            )
        }
    }

    /**
     * Helper to verify if notifications are globally allowed
     */
    fun areNotificationsAllowed(): Boolean {
        val masterEnabled = reminderPrefs.getBoolean("master_notifications_enabled", true)
        return masterEnabled && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Show daily report notification
     */
    fun showDailyReportNotification(date: LocalDate = LocalDate.now()) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("daily_report_enabled", true)) return

        val reportGenerator = ReportGenerator(context)
        val summaryText = reportGenerator.generateDailySummaryText(date)
        val shortDate = date.format(DateTimeFormatter.ofPattern("MMM d"))

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("start_tab", R.id.navigation_stats)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REPORTS)
            .setSmallIcon(R.drawable.ic_logo_mini)
            .setContentTitle("Daily AmniShield Report • $shortDate")
            .setContentText("Tap to review your blocking, focus, and reels summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_DAILY_REPORT, notification)
                NotificationInboxStore.add(
                    context,
                    title = "Daily AmniShield Report • $shortDate",
                    message = summaryText,
                    category = NotificationInboxStore.Category.DAILY_REPORT
                )
            } catch (e: SecurityException) {
                // Ignore missing POST_NOTIFICATIONS
            }
        }
    }

    /**
     * Show focus reminder notification with action buttons
     */
    fun showFocusReminder(title: String, message: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("focus_reminder_enabled", true)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val focusIntent = Intent(context, com.alhaq.amnishield.notifications.NotificationActionReceiver::class.java).apply {
            action = com.alhaq.amnishield.notifications.NotificationActionReceiver.ACTION_START_QUICK_FOCUS
            putExtra(com.alhaq.amnishield.notifications.NotificationActionReceiver.EXTRA_MINUTES, 25)
        }
        val focusPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            focusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, com.alhaq.amnishield.notifications.NotificationActionReceiver::class.java).apply {
            action = com.alhaq.amnishield.notifications.NotificationActionReceiver.ACTION_SNOOZE_BLOCK
            putExtra(com.alhaq.amnishield.notifications.NotificationActionReceiver.EXTRA_MINUTES, 15)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
            .setSmallIcon(R.drawable.ic_logo_mini)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_logo_mini, "Start 25m Focus", focusPendingIntent)
            .addAction(R.drawable.ic_logo_mini, "Snooze 15m", snoozePendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_REMINDER, notification)
                NotificationInboxStore.add(
                    context,
                    title = title,
                    message = message,
                    category = NotificationInboxStore.Category.REMINDER
                )
            } catch (e: SecurityException) {
                // Ignore missing POST_NOTIFICATIONS
            }
        }
    }

    /**
     * Show doomscroll spike alert
     */
    fun showDoomscrollAlert(title: String, message: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("doomscroll_alerts_enabled", true)) return
        showFocusReminder(title, message)
    }

    /**
     * Show pre-block warning alert
     */
    fun showPreBlockWarning(title: String, message: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("pre_block_warnings_enabled", true)) return
        showFocusReminder(title, message)
    }

    /**
     * Show achievement milestone notification
     */
    fun showAchievementNotification(achievement: String, description: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("achievement_enabled", true)) return

        val todayKey = LocalDate.now().toString()
        val notificationKey = "${achievement.lowercase()}_$todayKey"
        if (achievementPrefs.getBoolean(notificationKey, false)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("start_tab", R.id.navigation_stats)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_logo_mini)
            .setContentTitle("Achievement Unlocked")
            .setContentText(achievement)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$achievement\n\n$description"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_ACHIEVEMENT, notification)
                achievementPrefs.edit().putBoolean(notificationKey, true).apply()
                NotificationInboxStore.add(
                    context,
                    title = achievement,
                    message = description,
                    category = NotificationInboxStore.Category.ACHIEVEMENT
                )
            } catch (e: SecurityException) {
                // Ignore missing POST_NOTIFICATIONS
            }
        }
    }

    /**
     * Show digital wellbeing tip notification
     */
    fun showWellnessTipNotification(title: String, tip: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("wellness_tips_enabled", true)) return

        val todayKey = LocalDate.now().toString()
        val tipSentToday = reminderPrefs.getString("last_wellness_tip_date", "") == todayKey
        if (tipSentToday) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TIPS)
            .setSmallIcon(R.drawable.ic_logo_mini)
            .setContentTitle(title)
            .setContentText(tip)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tip))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_WELLNESS_TIP, notification)
                reminderPrefs.edit().putString("last_wellness_tip_date", todayKey).apply()
                NotificationInboxStore.add(
                    context,
                    title = title,
                    message = tip,
                    category = NotificationInboxStore.Category.REMINDER
                )
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    /**
     * Show real-time blocking alert
     */
    fun showBlockingAlert(title: String, message: String) {
        if (!areNotificationsAllowed()) return
        if (!reminderPrefs.getBoolean("blocking_alerts_enabled", true)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_BLOCK_ALERTS)
            .setSmallIcon(R.drawable.ic_logo_mini)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(NOTIFICATION_ID_BLOCK_ALERT, notification)
            } catch (e: SecurityException) {
                // Ignore
            }
        }
    }

    /**
     * Check achievements based on live blocking and focus stats
     */
    fun checkAndNotifyAchievements() {
        val statsManager = BlockingStatsManager.getInstance(context)
        val summary = statsManager.getStatsSummaryForDate(LocalDate.now())

        when {
            summary.appBlocksCount >= 10 -> {
                showAchievementNotification(
                    "Productivity Warrior",
                    "You've resisted 10 app distractions today!"
                )
            }
            summary.totalFocusMinutes >= 60 -> {
                showAchievementNotification(
                    "Focus Master",
                    "You've completed 1 hour of focused work today!"
                )
            }
            summary.totalFocusMinutes >= 120 -> {
                showAchievementNotification(
                    "Deep Work Champion",
                    "You've achieved 2 hours of deep focus today!"
                )
            }
            summary.keywordBlocksCount >= 20 -> {
                showAchievementNotification(
                    "Content Guardian",
                    "You've blocked 20+ harmful search keywords today!"
                )
            }
            summary.viewBlocksCount >= 30 -> {
                showAchievementNotification(
                    "Time Saver",
                    "You've avoided 30+ time-wasting reels/views today!"
                )
            }
        }
    }

    /**
     * Show custom reminder
     */
    fun showCustomReminder(title: String, message: String, iconResId: Int = R.drawable.ic_logo_mini) {
        showFocusReminder(title, message)
    }
}
