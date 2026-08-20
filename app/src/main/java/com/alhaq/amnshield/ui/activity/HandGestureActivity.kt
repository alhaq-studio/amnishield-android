package com.alhaq.amnshield.ui.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.alhaq.amnshield.databinding.OverlayHandGestureBinding

/**
 * Translucent, floating activity providing the Animated Hand Gesture Overlay.
 * Serves as a rock-solid, 100% reliable fail-safe for keyword intercepts.
 */
class HandGestureActivity : AppCompatActivity() {

    private lateinit var binding: OverlayHandGestureBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverlayHandGestureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val detectedKeyword = intent.getStringExtra("detected_keyword")
        val isHomePress = intent.getBooleanExtra("is_home_press", true)

        if (!detectedKeyword.isNullOrBlank() && !detectedKeyword.startsWith("/")) {
            binding.gestureSubtitle.text = "Detected restricted keyword"
        }

        triggerHapticPulse()

        // Pop-in entry animation
        binding.gestureCard.alpha = 0f
        binding.gestureCard.scaleX = 0.6f
        binding.gestureCard.scaleY = 0.6f

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

        // Hold for mindfulness, then navigate away and finish
        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val fadeOut = ObjectAnimator.ofFloat(binding.gestureCard, View.ALPHA, 1.0f, 0f).apply {
                    duration = 200
                    interpolator = AccelerateInterpolator()
                }
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        performExit(isHomePress)
                    }
                })
                fadeOut.start()
            }
        }, 1100)
    }

    private fun performExit(isHomePress: Boolean) {
        if (isHomePress) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun triggerHapticPulse() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
}
