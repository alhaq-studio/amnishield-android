package com.alhaq.amnshield.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import com.alhaq.amnshield.databinding.OverlayUsageStatBinding
import com.alhaq.amnshield.utils.SavedPreferencesLoader

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
    private val mainHandler = Handler(Looper.getMainLooper())

    var reelsScrolledThisSession = 0
    var sessionWatchSeconds = 0L

    /**
     * Attaches the floating wrap_content pill view to the WindowManager.
     * Tries TYPE_ACCESSIBILITY_OVERLAY first, falling back to TYPE_APPLICATION_OVERLAY if permitted.
     */
    @SuppressLint("InlinedApi")
    fun startDisplaying(positionGravity: Int = Gravity.TOP or Gravity.END) {
        mainHandler.post {
            if (overlayView != null || isOverlayVisible) return@post

            try {
                binding = OverlayUsageStatBinding.inflate(LayoutInflater.from(context))
                overlayView = binding?.root
                isOverlayVisible = true

                val density = context.resources.displayMetrics.density
                val offsetX = (16 * density).toInt()
                val offsetY = (72 * density).toInt() // Clean clearance below status bar

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
                    gravity = positionGravity
                    x = offsetX
                    y = offsetY
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.addView(overlayView, layoutParams)
                Log.d(TAG, "Floating doom-scrolling overlay attached successfully (type: $overlayType)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach floating doom-scrolling overlay", e)
                overlayView = null
                binding = null
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

        mainHandler.post {
            val b = binding ?: return@post

            // 1. Text badge formatting
            val countText = when {
                reelsCount == 1 -> "1 Reel"
                reelsCount > 0 -> "$reelsCount Reels"
                else -> "Doom Scroll"
            }

            val minutes = watchTimeSeconds / 60
            val seconds = watchTimeSeconds % 60
            val timeText = String.format("%02d:%02d", minutes, seconds)

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
    }

    /**
     * Safely removes the overlay from the WindowManager.
     */
    fun removeOverlay() {
        mainHandler.post {
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
                isOverlayVisible = false
                reelsScrolledThisSession = 0
                sessionWatchSeconds = 0L
            }
        }
    }
}