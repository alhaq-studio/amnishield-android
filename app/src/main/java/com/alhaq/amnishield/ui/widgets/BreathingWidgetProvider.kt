package com.alhaq.amnishield.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.alhaq.amnishield.BuildConfig
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.AmniSpaceActivity
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class BreathingWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "BreathingWidget"
        private val ACTION_START_BREATHING = "${BuildConfig.APPLICATION_ID}.breathing.START"
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
        if (intent.action == ACTION_START_BREATHING) {
            val loader = SavedPreferencesLoader(context)
            val durationMinutes = loader.getBreathingWidgetDurationMinutes()
            val openIntent = Intent(context, AmniSpaceActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Mindful Breathing Space")
                putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, durationMinutes * 60)
            }
            context.startActivity(openIntent)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            val loader = SavedPreferencesLoader(context)
            val durationMinutes = loader.getBreathingWidgetDurationMinutes()

            val views = RemoteViews(context.packageName, R.layout.widget_breathing).apply {
                val openIntent = Intent(context, AmniSpaceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Mindful Breathing Space")
                    putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, durationMinutes * 60)
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    widgetId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                setOnClickPendingIntent(R.id.btn_start_breathing, pendingOpen)
                setOnClickPendingIntent(R.id.widget_bg_breathing, pendingOpen)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating BreathingWidget", e)
        }
    }
}
