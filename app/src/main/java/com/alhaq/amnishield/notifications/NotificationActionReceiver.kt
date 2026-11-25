package com.alhaq.amnishield.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.activity.NotificationsActivity
import com.alhaq.amnishield.ui.activity.RemindersActivity

/**
 * Handles interactive action buttons clicked directly inside notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ENABLE_PROTECTION = "ENABLE_PROTECTION"
        const val ACTION_OPEN_APP = "OPEN_APP"
        const val ACTION_OPEN_INBOX = "OPEN_INBOX"
        const val ACTION_OPEN_STATS = "OPEN_STATS"
        const val ACTION_OPEN_BLOCKERS = "OPEN_BLOCKERS"
        const val ACTION_OPEN_FOCUS = "OPEN_FOCUS"
        const val ACTION_OPEN_REMINDERS = "OPEN_REMINDERS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val expandItemId = intent.getLongExtra(NotificationsActivity.EXTRA_EXPAND_ITEM_ID, -1L)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()

        when (action) {
            ACTION_ENABLE_PROTECTION -> {
                val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("adult_blocker_enabled", true).apply()
                Toast.makeText(context, "AmniShield Core Protection Enabled!", Toast.LENGTH_SHORT).show()

                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("start_tab", R.id.navigation_blocks)
                }
                context.startActivity(mainIntent)
            }

            ACTION_OPEN_INBOX -> {
                val inboxIntent = Intent(context, NotificationsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    if (expandItemId != -1L) {
                        putExtra(NotificationsActivity.EXTRA_EXPAND_ITEM_ID, expandItemId)
                    }
                }
                context.startActivity(inboxIntent)
            }

            ACTION_OPEN_STATS, ACTION_OPEN_APP -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("start_tab", R.id.navigation_stats)
                }
                context.startActivity(mainIntent)
            }

            ACTION_OPEN_BLOCKERS -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("start_tab", R.id.navigation_blocks)
                }
                context.startActivity(mainIntent)
            }

            ACTION_OPEN_FOCUS -> {
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("start_tab", R.id.navigation_focus)
                }
                context.startActivity(mainIntent)
            }

            ACTION_OPEN_REMINDERS -> {
                val remindersIntent = Intent(context, RemindersActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(remindersIntent)
            }
        }
    }
}
