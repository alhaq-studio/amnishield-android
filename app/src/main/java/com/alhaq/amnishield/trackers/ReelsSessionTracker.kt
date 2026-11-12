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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Dedicated tracker for monitoring short-form video engagement (Reels, Shorts, TikTok).
 * Inspects accessibility events passed from AmniShieldAccessibilityService, manages
 * event-driven 1-second watch duration ticking, offloads disk persistence to Dispatchers.IO,
 * updates the floating overlay pill, and handles screen on/off power saving.
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
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var currentReelsPackage: String? = null
    @Volatile
    private var isOnActiveReelSurface = false
    @Volatile
    private var cachedTodayScrolled: Int = 0
    @Volatile
    private var cachedTodayWatchSeconds: Long = 0L
    @Volatile
    private var lastSyncedDayKey: String = ""

    var sessionReelsWatchSeconds: Long = 0L
        private set
    var sessionReelsScrolled: Int = 0
        private set
    var lastReelScrollTime: Long = 0L
        private set

    private var consecutiveMissedDetections = 0
    private var isScreenOn = true
    private var isTrackingRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val watchTickerRunnable = object : Runnable {
        override fun run() {
            if (!isScreenOn || !isTrackingRunning || !isOnActiveReelSurface) return
            try {
                tickActiveReelWatchSecond()
            } catch (e: Exception) {
                crashLogger.logNonFatalError("ReelsSessionTracker", "Error during reels watch ticker", e)
            }
            if (isTrackingRunning && isScreenOn && isOnActiveReelSurface) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(watchTickerRunnable)
                    usageStatOverlayManager?.removeOverlay()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (isTrackingRunning && isOnActiveReelSurface) {
                        handler.removeCallbacks(watchTickerRunnable)
                        handler.postDelayed(watchTickerRunnable, 1000L)
                    }
                }
            }
        }
    }

    private fun syncDailyCountersIfNeeded() {
        val today = LocalDate.now().toString()
        if (today != lastSyncedDayKey) {
            lastSyncedDayKey = today
            cachedTodayScrolled = savedPreferencesLoader.getReelsScrolledToday()
            cachedTodayWatchSeconds = savedPreferencesLoader.getReelsWatchTimeSeconds()
            reelBlocker.reelsScrolledToday = cachedTodayScrolled
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start() {
        if (isTrackingRunning) return
        isTrackingRunning = true
        syncDailyCountersIfNeeded()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        service.registerReceiver(screenReceiver, filter)
    }

    fun stop() {
        if (!isTrackingRunning) return
        isTrackingRunning = false
        isOnActiveReelSurface = false
        handler.removeCallbacks(watchTickerRunnable)
        ioScope.coroutineContext.cancelChildren()
        try {
            service.unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        usageStatOverlayManager?.removeOverlay()
        usageStatOverlayManager = null
    }

    /**
     * Handles TYPE_VIEW_SCROLLED, TYPE_VIEW_SELECTED, and TYPE_WINDOW_STATE_CHANGED events for reel progression.
     * Note: Adheres strictly to the Node Lifecycle Invariant - NEVER recycles [rootNode].
     */
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        val activePackage = event.packageName?.toString().orEmpty()
        if (activePackage.isEmpty()) return

        val isReelsTracking = savedPreferencesLoader.isReelsTrackingEnabled(true)
        val isReelsBlocker = (reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)) && isFeatureActive("reel_blocker")

        if (!isReelsTracking && !isReelsBlocker) {
            if (isOnActiveReelSurface) {
                resetCurrentSession()
            }
            return
        }

        syncDailyCountersIfNeeded()

        if (ReelDetectionEngine.isReelCandidatePackage(activePackage)) {
            val root = rootNode ?: return
            val detection = reelDetectionEngine.detectReelSurface(root, activePackage)
            if (detection != null && detection.isReelSurface) {
                consecutiveMissedDetections = 0
                val wasOnReelSurface = isOnActiveReelSurface
                isOnActiveReelSurface = true

                if (currentReelsPackage != activePackage) {
                    if (currentReelsPackage != null) {
                        reelDetectionEngine.clearActiveDynamicText(currentReelsPackage)
                    }
                    currentReelsPackage = activePackage
                    sessionReelsWatchSeconds = 0L
                    sessionReelsScrolled = 0
                }

                if (!wasOnReelSurface && isScreenOn && isTrackingRunning) {
                    handler.removeCallbacks(watchTickerRunnable)
                    handler.postDelayed(watchTickerRunnable, 1000L)
                }

                val comparator = reelDetectionEngine.extractReelComparator(root, activePackage, event)
                val isProgression = reelDetectionEngine.checkReelProgression(activePackage, comparator)

                if (isProgression) {
                    val now = System.currentTimeMillis()
                    if (now - lastReelScrollTime >= 500L) {
                        lastReelScrollTime = now
                        sessionReelsScrolled++
                        cachedTodayScrolled++
                        reelBlocker.reelsScrolledToday = cachedTodayScrolled
                        val pkgSnapshot = activePackage
                        ioScope.launch {
                            savedPreferencesLoader.incrementReelsScrolled(pkgSnapshot)
                        }
                    }
                }

                updateOverlay(activePackage)
            } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
            ) {
                consecutiveMissedDetections++
                if (consecutiveMissedDetections >= 2) {
                    resetCurrentSession()
                }
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isTransientSystemWindow = activePackage.equals("com.android.systemui", ignoreCase = true) ||
                activePackage.equals("android", ignoreCase = true) ||
                activePackage.equals(service.packageName, ignoreCase = true) ||
                activePackage.equals("com.alhaq.amnishield", ignoreCase = true) ||
                activePackage.equals("com.alhaq.deenshield", ignoreCase = true)
            if (!isTransientSystemWindow) {
                resetCurrentSession()
            }
        }
    }

    private fun tickActiveReelWatchSecond() {
        val isReelsTrackingEnabled = savedPreferencesLoader.isReelsTrackingEnabled(true)
        val isReelsBlockerEnabled = reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)
        if (!isReelsTrackingEnabled && !isReelsBlockerEnabled) {
            resetCurrentSession()
            return
        }

        val pkg = currentReelsPackage ?: return
        syncDailyCountersIfNeeded()

        sessionReelsWatchSeconds++
        cachedTodayWatchSeconds++
        reelBlocker.reelsScrolledToday = cachedTodayScrolled

        updateOverlay(pkg)

        ioScope.launch {
            savedPreferencesLoader.addReelsWatchTime(1L, pkg)
        }
    }

    private fun resetCurrentSession() {
        isOnActiveReelSurface = false
        handler.removeCallbacks(watchTickerRunnable)
        if (currentReelsPackage != null) {
            reelDetectionEngine.clearActiveDynamicText(currentReelsPackage)
            currentReelsPackage = null
        }
        consecutiveMissedDetections = 0
        if (usageStatOverlayManager?.isOverlayVisible == true) {
            usageStatOverlayManager?.removeOverlay()
        }
    }

    private fun updateOverlay(pkg: String) {
        val isOverlayEnabled = savedPreferencesLoader.isReelsOverlayCounterEnabled(true)
        val overlayApps = savedPreferencesLoader.getReelsOverlayApps()
        val isCandidate = ReelDetectionEngine.isReelCandidatePackage(pkg)
        if (isOverlayEnabled && (overlayApps.contains(pkg) || overlayApps.isEmpty() || isCandidate)) {
            val overlayMgr = usageStatOverlayManager ?: UsageStatOverlayManager(service).also {
                usageStatOverlayManager = it
            }
            val mode = savedPreferencesLoader.getOverlayCounterDisplayMode()

            if (!overlayMgr.isOverlayVisible) {
                overlayMgr.startDisplaying(
                    initialCount = cachedTodayScrolled,
                    initialWatchTimeSeconds = cachedTodayWatchSeconds,
                    mode = mode
                )
            } else {
                overlayMgr.updateCounter(cachedTodayScrolled, cachedTodayWatchSeconds, mode)
            }
        }
    }
}
