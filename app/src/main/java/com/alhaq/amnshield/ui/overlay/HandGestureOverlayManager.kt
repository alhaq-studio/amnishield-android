package com.alhaq.amnshield.ui.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.alhaq.amnshield.databinding.OverlayHandGestureBinding

class HandGestureOverlayManager(private val context: Context) {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("InlinedApi", "SetTextI18n")
    fun showGestureOverlay(detectedKeyword: String? = null, onComplete: () -> Unit) {
        mainHandler.post {
            if (isShowing) {
                return@post
            }
            isShowing = true

            try {
                val binding = OverlayHandGestureBinding.inflate(LayoutInflater.from(context))
                overlayView = binding.root

                if (!detectedKeyword.isNullOrBlank() && !detectedKeyword.startsWith("/")) {
                    binding.gestureSubtitle.text = "Detected restricted keyword"
                }

                val layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    LayoutParams.FLAG_NOT_FOCUSABLE or
                            LayoutParams.FLAG_NOT_TOUCHABLE or
                            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                windowManager?.addView(overlayView, layoutParams)

                // Haptic feedback pulse
                triggerHapticPulse()

                // Initial setup for entry animation
                binding.gestureCard.alpha = 0f
                binding.gestureCard.scaleX = 0.6f
                binding.gestureCard.scaleY = 0.6f

                // Pop-in animation
                val scaleX = ObjectAnimator.ofFloat(binding.gestureCard, View.SCALE_X, 0.6f, 1.0f)
                val scaleY = ObjectAnimator.ofFloat(binding.gestureCard, View.SCALE_Y, 0.6f, 1.0f)
                val alphaIn = ObjectAnimator.ofFloat(binding.gestureCard, View.ALPHA, 0f, 1.0f)

                val popInSet = AnimatorSet().apply {
                    playTogether(scaleX, scaleY, alphaIn)
                    duration = 260
                    interpolator = OvershootInterpolator(1.4f)
                }

                // Waving hand animation
                val waveAnim = ObjectAnimator.ofFloat(
                    binding.gestureHandIcon,
                    View.ROTATION,
                    0f, -20f, 18f, -14f, 12f, 0f
                ).apply {
                    duration = 650
                    startDelay = 150
                }

                popInSet.start()
                waveAnim.start()

                // Hold for mindfulness, then fade out and navigate back
                mainHandler.postDelayed({
                    if (overlayView != null && isShowing) {
                        val fadeOut = ObjectAnimator.ofFloat(binding.gestureCard, View.ALPHA, 1.0f, 0f).apply {
                            duration = 200
                            interpolator = AccelerateInterpolator()
                        }
                        fadeOut.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                dismissOverlay()
                                onComplete()
                            }
                        })
                        fadeOut.start()
                    } else {
                        dismissOverlay()
                        onComplete()
                    }
                }, 1100)

            } catch (e: Exception) {
                Log.e("HandGestureOverlay", "Failed to show hand gesture overlay", e)
                dismissOverlay()
                onComplete()
            }
        }
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(80)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun dismissOverlay() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e("HandGestureOverlay", "Failed to remove overlay view", e)
        } finally {
            overlayView = null
            isShowing = false
        }
    }
}
