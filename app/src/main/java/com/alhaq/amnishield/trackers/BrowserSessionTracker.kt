package com.alhaq.amnishield.trackers

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnishield.CrashLogger
import com.alhaq.amnishield.blockers.WebsiteBlockerDetector
import com.alhaq.amnishield.utils.SavedPreferencesLoader

/**
 * Dedicated tracker for monitoring browser website domain engagement.
 * Manages background 2-second active window polling when browsers are in foreground,
 * records duration per domain in SavedPreferencesLoader, and supports screen on/off power saving.
 */
class BrowserSessionTracker(
    private val service: AccessibilityService,
    private val savedPreferencesLoader: SavedPreferencesLoader,
    private val websiteBlockerDetector: WebsiteBlockerDetector,
    private val crashLogger: CrashLogger
) {
    private var isScreenOn = true
    private var isTrackingRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn || !isTrackingRunning) return
            try {
                pollActiveWindowForBrowser()
            } catch (e: Exception) {
                crashLogger.logNonFatalError("BrowserSessionTracker", "Error during browser tracking polling", e)
            }
            if (isTrackingRunning && isScreenOn) {
                handler.postDelayed(this, 2000L)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(pollingRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (isTrackingRunning) {
                        handler.removeCallbacks(pollingRunnable)
                        handler.post(pollingRunnable)
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (isTrackingRunning) return
        isTrackingRunning = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        service.registerReceiver(screenReceiver, filter)

        handler.removeCallbacks(pollingRunnable)
        handler.post(pollingRunnable)
    }

    fun stop() {
        if (!isTrackingRunning) return
        isTrackingRunning = false
        handler.removeCallbacks(pollingRunnable)
        try {
            service.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }

    /**
     * Handles window state change events to immediately capture newly navigated domains.
     * Note: Adheres strictly to the Node Lifecycle Invariant - NEVER recycles [rootNode].
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        if (!savedPreferencesLoader.isWebsiteUsageTrackingEnabled(true)) return

        val pkg = event.packageName?.toString().orEmpty()
        if (WebsiteBlockerDetector.BROWSER_URL_BAR_IDS.containsKey(pkg) &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val root = rootNode ?: return
            val domain = websiteBlockerDetector.extractCurrentDomain(root, pkg)
            if (domain != null) {
                savedPreferencesLoader.recordWebsiteUsage(domain, 1000L)
            }
        }
    }

    private fun pollActiveWindowForBrowser() {
        if (!savedPreferencesLoader.isWebsiteUsageTrackingEnabled(true)) return

        val root = service.rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (WebsiteBlockerDetector.BROWSER_URL_BAR_IDS.containsKey(pkg)) {
                val domain = websiteBlockerDetector.extractCurrentDomain(root, pkg)
                if (domain != null) {
                    savedPreferencesLoader.recordWebsiteUsage(domain, 2000L)
                }
            }
        } finally {
            try {
                @Suppress("DEPRECATION")
                root.recycle()
            } catch (_: Exception) {}
        }
    }
}
