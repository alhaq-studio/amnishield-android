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
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.ui.overlay.UsageStatOverlayManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader

/**
 * Dedicated tracker for monitoring short-form video engagement (Reels, Shorts, TikTok).
 * Manages background 1-second watch duration polling, scroll progression detection,
 * real-time floating overlay updates, and screen on/off power saving.
 */
class ReelsSessionTracker(
    private val service: AccessibilityService,
    private val savedPreferencesLoader: SavedPreferencesLoader,
    private val reelBlocker: ReelBlocker,
    private val crashLogger: CrashLogger,
    private val isFeatureActive: (String) -> Boolean
) {
    private val reelDetectionEngine = ReelDetectionEngine()
    private var usageStatOverlayManager: UsageStatOverlayManager? = null

    private var currentReelsPackage: String? = null
    var sessionReelsWatchSeconds: Long = 0L
        private set
    var sessionReelsScrolled: Int = 0
        private set
    var lastReelScrollTime: Long = 0L
        private set

    private var isScreenOn = true
    private var isTrackingRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val pollingRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn || !isTrackingRunning) return
            try {
                pollActiveWindowForReels()
            } catch (e: Exception) {
                crashLogger.logNonFatalError("ReelsSessionTracker", "Error during reels tracking polling", e)
            }
            if (isTrackingRunning && isScreenOn) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(pollingRunnable)
                    usageStatOverlayManager?.removeOverlay()
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
        usageStatOverlayManager?.removeOverlay()
        usageStatOverlayManager = null
    }

    /**
     * Handles TYPE_VIEW_SCROLLED and TYPE_WINDOW_STATE_CHANGED events for reel progression.
     * Note: Adheres strictly to the Node Lifecycle Invariant - NEVER recycles [rootNode].
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        val activePackage = event.packageName?.toString().orEmpty()
        val isReelsTracking = savedPreferencesLoader.isReelsTrackingEnabled(true)
        val isReelsBlocker = (reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)) && isFeatureActive("reel_blocker")

        if ((isReelsTracking || isReelsBlocker) && ReelDetectionEngine.isReelCandidatePackage(activePackage)) {
            val root = rootNode ?: return
            val detection = reelDetectionEngine.detectReelSurface(root, activePackage)
            if (detection != null && detection.isReelSurface) {
                val comparator = reelDetectionEngine.extractReelComparator(root, activePackage, event)
                val isProgression = reelDetectionEngine.checkReelProgression(activePackage, comparator)

                if (isProgression) {
                    val now = System.currentTimeMillis()
                    lastReelScrollTime = now
                    sessionReelsScrolled++
                    savedPreferencesLoader.incrementReelsScrolled(activePackage)
                    reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()
                }

                updateOverlay(activePackage)
            }
        }
    }

    private fun pollActiveWindowForReels() {
        val isReelsTrackingEnabled = savedPreferencesLoader.isReelsTrackingEnabled(true)
        val isReelsBlockerEnabled = reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)

        if (!isReelsTrackingEnabled && !isReelsBlockerEnabled) return

        val root = service.rootInActiveWindow ?: return
        try {
            val pkg = root.packageName?.toString().orEmpty()
            if (ReelDetectionEngine.isReelCandidatePackage(pkg)) {
                val detection = reelDetectionEngine.detectReelSurface(root, pkg)
                if (detection != null && detection.isReelSurface) {
                    if (currentReelsPackage != pkg) {
                        currentReelsPackage = pkg
                        sessionReelsWatchSeconds = 0L
                        sessionReelsScrolled = 0
                        reelDetectionEngine.resetSession(pkg)
                    }
                    sessionReelsWatchSeconds++
                    savedPreferencesLoader.addReelsWatchTime(1L, pkg)
                    reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()

                    val comparator = reelDetectionEngine.extractReelComparator(root, pkg, null)
                    val isProgression = reelDetectionEngine.checkReelProgression(pkg, comparator)
                    if (isProgression) {
                        sessionReelsScrolled++
                        savedPreferencesLoader.incrementReelsScrolled(pkg)
                        reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()
                    }

                    updateOverlay(pkg)
                } else {
                    resetCurrentSession()
                }
            } else {
                resetCurrentSession()
            }
        } finally {
            try {
                @Suppress("DEPRECATION")
                root.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun resetCurrentSession() {
        if (currentReelsPackage != null) {
            reelDetectionEngine.resetSession(currentReelsPackage)
            currentReelsPackage = null
        }
        if (usageStatOverlayManager?.isOverlayVisible == true) {
            usageStatOverlayManager?.removeOverlay()
        }
    }

    private fun updateOverlay(pkg: String) {
        val isOverlayEnabled = savedPreferencesLoader.isReelsOverlayCounterEnabled(true)
        val overlayApps = savedPreferencesLoader.getReelsOverlayApps()
        if (isOverlayEnabled && (overlayApps.contains(pkg) || overlayApps.isEmpty())) {
            val overlayMgr = usageStatOverlayManager ?: UsageStatOverlayManager(service).also {
                usageStatOverlayManager = it
            }
            if (!overlayMgr.isOverlayVisible) {
                overlayMgr.startDisplaying()
            }
            val mode = savedPreferencesLoader.getOverlayCounterDisplayMode()
            overlayMgr.updateCounter(sessionReelsScrolled, sessionReelsWatchSeconds, mode)
        }
    }
}
