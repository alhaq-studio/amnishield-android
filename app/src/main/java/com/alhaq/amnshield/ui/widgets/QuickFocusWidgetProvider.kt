package com.alhaq.amnshield.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.alhaq.amnshield.BuildConfig
import com.alhaq.amnshield.R
import com.alhaq.amnshield.blockers.FocusModeBlocker
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.activity.FragmentActivity
import com.alhaq.amnshield.utils.NotificationTimerManager
import com.alhaq.amnshield.utils.SavedPreferencesLoader

class QuickFocusWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "QuickFocusWidget"
        private val ACTION_START_FOCUS_15 = "${BuildConfig.APPLICATION_ID}.focus.START_15"
        private val ACTION_START_FOCUS_30 = "${BuildConfig.APPLICATION_ID}.focus.START_30"
        private val ACTION_START_FOCUS_60 = "${BuildConfig.APPLICATION_ID}.focus.START_60"
        private val ACTION_STOP_FOCUS = "${BuildConfig.APPLICATION_ID}.focus.STOP_FOCUS"
        private val ACTION_WIDGET_REFRESH = "${BuildConfig.APPLICATION_ID}.focus.WIDGET_REFRESH"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, QuickFocusWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds != null && widgetIds.isNotEmpty()) {
                    val intent = Intent(context, QuickFocusWidgetProvider::class.java).apply {
                        action = ACTION_WIDGET_REFRESH
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering updateAllWidgets", e)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action ?: return
        if (action.startsWith("${BuildConfig.APPLICATION_ID}.focus.START_")) {
            val minutes = when (action) {
                ACTION_START_FOCUS_15 -> 15
                ACTION_START_FOCUS_30 -> 30
                ACTION_START_FOCUS_60 -> 60
                else -> 30
            }
            startFocusSessionFromWidget(context, minutes)
        } else if (action == ACTION_STOP_FOCUS) {
            stopFocusSessionFromWidget(context)
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, QuickFocusWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        widgetIds?.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun startFocusSessionFromWidget(context: Context, durationMinutes: Int) {
        val loader = SavedPreferencesLoader(context)
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

        val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(refreshIntent)

        val timer = NotificationTimerManager(context)
        timer.startTimer(
            totalMillis = durationMillis,
            onFinishCallback = {
                stopFocusSessionFromWidget(context)
            }
        )
    }

    private fun stopFocusSessionFromWidget(context: Context) {
        val loader = SavedPreferencesLoader(context)
        loader.stopFocusSession()

        val timer = NotificationTimerManager(context)
        timer.stopTimer()

        val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(refreshIntent)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            val loader = SavedPreferencesLoader(context)
            val focusData = loader.getFocusModeData()
            val isFocusActive = focusData.isTurnedOn && focusData.endTime > System.currentTimeMillis()
            val remainingMins = if (isFocusActive) ((focusData.endTime - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1) else 0

            val views = RemoteViews(context.packageName, R.layout.widget_quick_focus).apply {
                if (isFocusActive) {
                    setTextViewText(R.id.txt_focus_status_badge, "ACTIVE")
                    setTextViewText(R.id.txt_focus_timer_display, "${remainingMins}m Remaining")
                    setTextViewText(R.id.txt_focus_subtitle, "Focus session active")

                    setViewVisibility(R.id.layout_active_focus_controls, View.VISIBLE)
                    setViewVisibility(R.id.layout_quick_start_buttons, View.GONE)

                    setOnClickPendingIntent(
                        R.id.btn_stop_focus,
                        createActionIntent(context, widgetId, ACTION_STOP_FOCUS)
                    )
                } else {
                    setTextViewText(R.id.txt_focus_status_badge, "READY")
                    setTextViewText(R.id.txt_focus_timer_display, "Ready to Focus")
                    setTextViewText(R.id.txt_focus_subtitle, "Tap a preset to start instant session")

                    setViewVisibility(R.id.layout_active_focus_controls, View.GONE)
                    setViewVisibility(R.id.layout_quick_start_buttons, View.VISIBLE)
                }

                // Preset button pending intents
                setOnClickPendingIntent(R.id.btn_quick_focus_15, createActionIntent(context, widgetId, ACTION_START_FOCUS_15))
                setOnClickPendingIntent(R.id.btn_quick_focus_30, createActionIntent(context, widgetId, ACTION_START_FOCUS_30))
                setOnClickPendingIntent(R.id.btn_quick_focus_60, createActionIntent(context, widgetId, ACTION_START_FOCUS_60))

                // Tap title/timer display to open Focus Space screen
                val openIntent = Intent(context, FragmentActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("feature_type", "focus_mode")
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.txt_focus_timer_display, pendingOpen)
                setOnClickPendingIntent(R.id.txt_focus_status_badge, pendingOpen)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating QuickFocusWidget", e)
        }
    }

    private fun createActionIntent(context: Context, widgetId: Int, actionStr: String): PendingIntent {
        val intent = Intent(context, QuickFocusWidgetProvider::class.java).apply {
            action = actionStr
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        return PendingIntent.getBroadcast(
            context,
            actionStr.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
