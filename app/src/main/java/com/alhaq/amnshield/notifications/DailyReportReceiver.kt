package com.alhaq.amnshield.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.alhaq.amnshield.utils.NotificationHelper

/**
 * BroadcastReceiver that triggers daily report notifications
 * at the scheduled time set by the user, and automatically
 * schedules the subsequent day's alarm.
 */
class DailyReportReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPreferences = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
        val isEnabled = sharedPreferences.getBoolean("daily_report_enabled", true)
        
        if (isEnabled) {
            val notificationHelper = NotificationHelper.getInstance(context)
            notificationHelper.showDailyReportNotification()

            // Reschedule next day's alarm
            val hour = sharedPreferences.getInt("daily_report_hour", 20)
            val minute = sharedPreferences.getInt("daily_report_minute", 0)
            val scheduler = SmartNotificationScheduler(context)
            scheduler.scheduleDailyReport(hour, minute)
        }
    }
}
