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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Dedicated tracker for monitoring browser website domain engagement.
 * Captures active browser domains from accessibility events passed by AmniShieldAccessibilityService,
 * attributes 2-second engagement intervals off the main thread on Dispatchers.IO,
 * and supports screen on/off power saving.
 */
class BrowserSessionTracker(
    private val service: AccessibilityService,
    private val savedPreferencesLoader: SavedPreferencesLoader,
    private val websiteBlockerDetector: WebsiteBlockerDetector,
    private val crashLogger: CrashLogger
) {
    private var isScreenOn = true
    private var isTrackingRunning = false
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var activeBrowserPackage: String? = null
    @Volatile
    private var activeDomain: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn || !isTrackingRunning) return
            try {
                recordActiveBrowserSlice()
            } catch (e: Exception) {
                crashLogger.logNonFatalError("BrowserSessionTracker", "Error during browser usage recording", e)
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
                        handler.postDelayed(pollingRunnable, 2000L)
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
        handler.postDelayed(pollingRunnable, 2000L)
    }

    fun stop() {
        if (!isTrackingRunning) return
        isTrackingRunning = false
        activeBrowserPackage = null
        activeDomain = null
        handler.removeCallbacks(pollingRunnable)
        ioScope.coroutineContext.cancelChildren()
        try {
            service.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }

    /**
     * Handles window and view events to capture active browser domains without polling Binder IPC.
     * Note: Adheres strictly to the Node Lifecycle Invariant - NEVER recycles [rootNode].
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        if (!savedPreferencesLoader.isWebsiteUsageTrackingEnabled(true)) {
            activeBrowserPackage = null
            activeDomain = null
            return
        }

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty()) return

        if (WebsiteBlockerDetector.BROWSER_URL_BAR_IDS.containsKey(pkg)) {
            activeBrowserPackage = pkg
            val root = rootNode ?: return
            val domain = websiteBlockerDetector.extractCurrentDomain(root, pkg)
            if (domain != null) {
                val previousDomain = activeDomain
                activeDomain = domain
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && domain != previousDomain) {
                    ioScope.launch {
                        savedPreferencesLoader.recordWebsiteUsage(domain, 1000L)
                    }
                }
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isTransientSystemWindow = pkg.equals("com.android.systemui", ignoreCase = true) ||
                pkg.equals("android", ignoreCase = true) ||
                pkg.equals(service.packageName, ignoreCase = true) ||
                pkg.equals("com.alhaq.amnishield", ignoreCase = true) ||
                pkg.equals("com.alhaq.deenshield", ignoreCase = true)
            if (!isTransientSystemWindow) {
                activeBrowserPackage = null
                activeDomain = null
            }
        }
    }

    private fun recordActiveBrowserSlice() {
        if (!savedPreferencesLoader.isWebsiteUsageTrackingEnabled(true)) return
        val pkg = activeBrowserPackage ?: return
        if (!WebsiteBlockerDetector.BROWSER_URL_BAR_IDS.containsKey(pkg)) return
        val domain = activeDomain ?: return

        ioScope.launch {
            savedPreferencesLoader.recordWebsiteUsage(domain, 2000L)
        }
    }
}
