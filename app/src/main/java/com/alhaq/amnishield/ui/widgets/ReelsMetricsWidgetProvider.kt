package com.alhaq.amnishield.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.alhaq.amnishield.BuildConfig
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class ReelsMetricsWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ReelsMetricsWidget"
        private val ACTION_WIDGET_REFRESH = "${BuildConfig.APPLICATION_ID}.reels.WIDGET_REFRESH"
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
        if (intent.action == ACTION_WIDGET_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            widgetIds?.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        try {
            val loader = SavedPreferencesLoader(context)
            val reelsCount = loader.getReelsScrolledToday()
            val reelsLimit = loader.getReelBlockerDailyLimit()
            val isBlockerEnabled = loader.isReelBlockerEnabled()

            val pct = if (reelsLimit > 0) ((reelsCount.toFloat() / reelsLimit) * 100).toInt() else 0

            val views = RemoteViews(context.packageName, R.layout.widget_reels_metrics).apply {
                setTextViewText(R.id.txt_reels_scrolled_count, "$reelsCount Reels")
                setTextViewText(R.id.txt_reels_limit_info, if (reelsLimit > 0) "Limit: $reelsLimit • $pct% Used" else "No Daily Limit Set")

                setTextViewText(R.id.txt_reels_status_badge, if (isBlockerEnabled) "ACTIVE" else "PAUSED")

                // Refresh button
                val refreshIntent = Intent(context, ReelsMetricsWidgetProvider::class.java).apply {
                    action = ACTION_WIDGET_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context,
                    widgetId,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.refresh_reels_stats, refreshPending)

                // Open Detailed Reels Metrics Page on card tap
                val openIntent = Intent(context, FragmentActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("feature_type", "reels_metrics")
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.txt_reels_scrolled_count, pendingOpen)
                setOnClickPendingIntent(R.id.txt_reels_limit_info, pendingOpen)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating ReelsMetricsWidget", e)
        }
    }
}
