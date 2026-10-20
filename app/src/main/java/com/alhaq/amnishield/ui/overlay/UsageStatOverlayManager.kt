package com.alhaq.amnishield.ui.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.alhaq.amnishield.databinding.OverlayUsageStatBinding
import com.alhaq.amnishield.utils.SavedPreferencesLoader

private const val TAG = "UsageStatOverlayManager"

/**
 * Manages the floating on-screen doom-scrolling counter and elapsed time overlay.
 *
 * Displays real-time doom-scrolling metrics (reels/shorts count in current session,
 * active watch duration ticker) directly on top of target social media applications.
 *
 * Attribution:
 * Overlay WindowManager lifecycle and positioning architecture inspired by Curbox by Nethical (GPL-3.0-or-later).
 * Reference: https://github.com/curbox-app/curbox-android
 */
class UsageStatOverlayManager(private val context: Context) {

    private var overlayView: View? = null
    var binding: OverlayUsageStatBinding? = null
    var isOverlayVisible = false
        private set

    private var windowManager: WindowManager? = null
    private var currentLayoutParams: LayoutParams? = null
    private var currentGravity: Int = Gravity.TOP or Gravity.END
    private var isConfigReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    var reelsScrolledThisSession = 0
    var sessionWatchSeconds = 0L

    private val configChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                runOnMain {
                    val view = overlayView ?: return@runOnMain
                    val params = currentLayoutParams ?: return@runOnMain
                    applyAdaptiveWindowOffsets(view, params, null)
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update overlay layout on configuration change", e)
                    }
                }
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun applyAdaptiveWindowOffsets(
        view: View,
        params: LayoutParams,
        insetsCompat: WindowInsetsCompat?
    ) {
        val density = context.resources.displayMetrics.density
        val isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val systemAndCutoutInsets = (insetsCompat ?: ViewCompat.getRootWindowInsets(view))
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

        val safeHorizontalInset = maxOf(systemAndCutoutInsets?.left ?: 0, systemAndCutoutInsets?.right ?: 0)
        val safeTopInset = systemAndCutoutInsets?.top ?: 0

        val baseHorizontalMarginPx = (16 * density).toInt()
        val baseVerticalMarginPx = if (isLandscape) {
            (24 * density).toInt()
        } else {
            (72 * density).toInt()
        }

        params.gravity = currentGravity
        params.x = baseHorizontalMarginPx + safeHorizontalInset
        params.y = maxOf(baseVerticalMarginPx, safeTopInset + (12 * density).toInt())
    }

    /**
     * Attaches the floating wrap_content pill view to the WindowManager.
     * Tries TYPE_ACCESSIBILITY_OVERLAY first, falling back to TYPE_APPLICATION_OVERLAY if permitted.
     */
    @SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")
    fun startDisplaying(
        positionGravity: Int = Gravity.TOP or Gravity.END,
        initialCount: Int = 0,
        initialWatchTimeSeconds: Long = 0L,
        mode: Int = SavedPreferencesLoader.OVERLAY_MODE_BOTH
    ) {
        reelsScrolledThisSession = initialCount
        sessionWatchSeconds = initialWatchTimeSeconds
        currentGravity = positionGravity

        runOnMain {
            if (overlayView != null || isOverlayVisible) {
                binding?.let { applyCounterDisplay(it, reelsScrolledThisSession, sessionWatchSeconds, mode) }
                return@runOnMain
            }

            try {
                binding = OverlayUsageStatBinding.inflate(LayoutInflater.from(context))
                val rootView = binding?.root ?: return@runOnMain
                overlayView = rootView
                isOverlayVisible = true

                val overlayType = if (context is android.accessibilityservice.AccessibilityService) {
                    LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    LayoutParams.TYPE_PHONE
                }

                val layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    overlayType,
                    LayoutParams.FLAG_NOT_FOCUSABLE or
                            LayoutParams.FLAG_NOT_TOUCHABLE or
                            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                applyAdaptiveWindowOffsets(rootView, layoutParams, null)
                currentLayoutParams = layoutParams

                ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
                    val params = currentLayoutParams
                    if (params != null && isOverlayVisible) {
                        val prevX = params.x
                        val prevY = params.y
                        applyAdaptiveWindowOffsets(v, params, windowInsets)
                        if (prevX != params.x || prevY != params.y) {
                            try {
                                windowManager?.updateViewLayout(v, params)
                            } catch (_: Exception) {}
                        }
                    }
                    windowInsets
                }

                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.addView(rootView, layoutParams)
                binding?.let { applyCounterDisplay(it, reelsScrolledThisSession, sessionWatchSeconds, mode) }

                if (!isConfigReceiverRegistered) {
                    try {
                        val filter = IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
                        context.registerReceiver(configChangeReceiver, filter)
                        isConfigReceiverRegistered = true
                    } catch (_: Exception) {}
                }

                Log.d(TAG, "Floating doom-scrolling overlay attached successfully (type: $overlayType)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating doom-scrolling overlay", e)
                overlayView = null
                binding = null
                currentLayoutParams = null
                isOverlayVisible = false
            }
        }
    }

    /**
     * Updates the counter text, time elapsed ticker, and warning color on the live overlay pill.
     */
    fun updateCounter(
        reelsCount: Int,
        watchTimeSeconds: Long,
        mode: Int = SavedPreferencesLoader.OVERLAY_MODE_BOTH
    ) {
        reelsScrolledThisSession = reelsCount
        sessionWatchSeconds = watchTimeSeconds

        runOnMain {
            val b = binding ?: return@runOnMain
            applyCounterDisplay(b, reelsCount, watchTimeSeconds, mode)
        }
    }

    private fun applyCounterDisplay(
        b: OverlayUsageStatBinding,
        reelsCount: Int,
        watchTimeSeconds: Long,
        mode: Int
    ) {
        // 1. Text badge formatting
        val countText = when {
            reelsCount == 1 -> "1 Reel"
            reelsCount > 0 -> "$reelsCount Reels"
            else -> "0 Reels"
        }

        val hours = watchTimeSeconds / 3600
        val minutes = (watchTimeSeconds % 3600) / 60
        val seconds = watchTimeSeconds % 60
        val timeText = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }

        when (mode) {
            SavedPreferencesLoader.OVERLAY_MODE_COUNT -> {
                b.overlayCounterText.visibility = View.VISIBLE
                b.overlayCounterText.text = countText
                b.overlayDivider.visibility = View.GONE
                b.timeElapsedTxt.visibility = View.GONE
            }
            SavedPreferencesLoader.OVERLAY_MODE_TIME -> {
                b.overlayCounterText.visibility = View.GONE
                b.overlayDivider.visibility = View.GONE
                b.timeElapsedTxt.visibility = View.VISIBLE
                b.timeElapsedTxt.text = timeText
            }
            else -> {
                b.overlayCounterText.visibility = View.VISIBLE
                b.overlayCounterText.text = countText
                b.overlayDivider.visibility = View.VISIBLE
                b.timeElapsedTxt.visibility = View.VISIBLE
                b.timeElapsedTxt.text = timeText
            }
        }

        // 2. Dynamic warning color scheme progression
        val (accentColor, textColor) = when {
            reelsCount >= 15 || watchTimeSeconds >= 600 -> {
                // Critical threshold (Red)
                Pair(Color.parseColor("#FF5252"), Color.parseColor("#FF8A80"))
            }
            reelsCount >= 6 || watchTimeSeconds >= 180 -> {
                // Warning threshold (Amber)
                Pair(Color.parseColor("#FFB300"), Color.parseColor("#FFE082"))
            }
            else -> {
                // Mild threshold (Cyan/White)
                Pair(Color.parseColor("#00E5FF"), Color.parseColor("#FFFFFF"))
            }
        }

        b.overlayCounterText.setTextColor(textColor)
        b.overlayIcon.setColorFilter(accentColor)
    }

    /**
     * Safely removes the overlay from the WindowManager.
     */
    fun removeOverlay() {
        runOnMain {
            if (isConfigReceiverRegistered) {
                try {
                    context.unregisterReceiver(configChangeReceiver)
                } catch (_: Exception) {}
                isConfigReceiverRegistered = false
            }
            try {
                if (overlayView != null && windowManager != null) {
                    windowManager?.removeView(overlayView)
                    Log.d(TAG, "Floating doom-scrolling overlay removed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            } finally {
                overlayView = null
                binding = null
                currentLayoutParams = null
                isOverlayVisible = false
                reelsScrolledThisSession = 0
                sessionWatchSeconds = 0L
            }
        }
    }
}
