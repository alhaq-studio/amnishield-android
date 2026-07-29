package com.alhaq.amnshield.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.alhaq.amnshield.BuildConfig
import com.alhaq.amnshield.blockers.FocusModeBlocker
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.utils.NotificationTimerManager
import com.alhaq.amnshield.utils.SavedPreferencesLoader

/**
 * Handles interactive actions clicked directly from notification shade action buttons:
 * - Start Quick Focus (25m)
 * - Snooze Block Warning (15m)
 * - Quick Block App
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        val ACTION_START_QUICK_FOCUS = "${BuildConfig.APPLICATION_ID}.notifications.START_QUICK_FOCUS"
        val ACTION_SNOOZE_BLOCK = "${BuildConfig.APPLICATION_ID}.notifications.SNOOZE_BLOCK"
        val ACTION_QUICK_BLOCK_APP = "${BuildConfig.APPLICATION_ID}.notifications.QUICK_BLOCK_APP"

        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val loader = SavedPreferencesLoader(context)

        when (action) {
            ACTION_START_QUICK_FOCUS -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 25)
                startQuickFocus(context, loader, minutes)
            }
            ACTION_SNOOZE_BLOCK -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
                Toast.makeText(context, "Block snoozed for $minutes minutes", Toast.LENGTH_SHORT).show()
            }
            ACTION_QUICK_BLOCK_APP -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (!pkg.isNullOrEmpty()) {
                    val currentBlocked = loader.loadBlockedApps().toMutableSet()
                    if (!currentBlocked.contains(pkg)) {
                        currentBlocked.add(pkg)
                        loader.saveBlockedApps(currentBlocked)
                        context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).apply {
                            setPackage(context.packageName)
                        })
                        Toast.makeText(context, "App blocked successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startQuickFocus(context: Context, loader: SavedPreferencesLoader, durationMinutes: Int) {
        val durationMillis = durationMinutes * 60_000L
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis

        val currentData = loader.getFocusModeData()
        loader.saveFocusModeData(
            FocusModeBlocker.FocusModeData(
                isTurnedOn = true,
                endTime = endTime,
                modeType = currentData.modeType,
                selectedApps = currentData.selectedApps
            )
        )
        loader.saveFocusSessionStartTime(startTime, endTime)

        context.sendBroadcast(Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(context.packageName)
        })

        val timer = NotificationTimerManager(context)
        timer.startTimer(totalMillis = durationMillis)

        Toast.makeText(context, "$durationMinutes-min Focus Session started!", Toast.LENGTH_SHORT).show()
    }
}
