package com.alhaq.amnishield.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootAndAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootAndAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received system broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED
        ) {
            val sharedPreferences = context.getSharedPreferences("reminder_settings", Context.MODE_PRIVATE)
            val isEnabled = sharedPreferences.getBoolean("daily_report_enabled", false)

            if (isEnabled) {
                val hour = sharedPreferences.getInt("daily_report_hour", 20)
                val minute = sharedPreferences.getInt("daily_report_minute", 0)

                val scheduler = SmartNotificationScheduler(context)
                scheduler.scheduleDailyReport(hour, minute)
                Log.d(TAG, "Rescheduled daily report alarm after $action")
            }
        }
    }
}
