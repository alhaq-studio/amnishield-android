/**
 * ============================================================================
 * AmniShield Blocker Pipeline - KeywordActionHandler
 * ============================================================================
 * Architecture: Action Dispatcher & Input Sanitizer
 * Priority: Realtime Text & Query Protection
 * 
 * Description:
 * Handles action execution for Keyword Blocker detections:
 * - Cleans offending text from input / search fields via accessibility actions.
 * - Manages 3-stage silent counter (1st/2nd clean text, 3rd trigger overlay without navigation).
 * - Dispatches Hand Gesture overlay or customized Warning dialogs.
 * 
 * Execution Context:
 * Main-thread dispatching & accessibility action execution.
 * ============================================================================
 */
package com.alhaq.amnishield.blockers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.ui.activity.WarningActivity
import com.alhaq.amnishield.ui.overlay.HandGestureOverlayManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class KeywordActionHandler(
    private val context: Context,
    private val savedPreferencesLoader: SavedPreferencesLoader,
    private val keywordBlocker: KeywordBlocker,
    private val handGestureOverlayManager: HandGestureOverlayManager
) {
    private var silentAttemptCount = 0
    private var lastSilentPackage: String? = null
    private var lastSilentKeyword: String? = null
    private var lastSilentAttemptTime = 0L

    fun handleKeywordBlock(
        result: KeywordBlocker.KeywordBlockerResult,
        packageName: String,
        rootNode: AccessibilityNodeInfo?,
        event: AccessibilityEvent,
        onPressHome: () -> Unit
    ) {
        val feedbackMode = savedPreferencesLoader.getKeywordBlockerFeedbackMode()
        val isHomePress = result.isHomePressRequested

        try {
            keywordBlocker.clearOffendingText(rootNode, event)
        } catch (e: Exception) {
            android.util.Log.e("KeywordActionHandler", "Failed to clear offending text: ${e.message}")
        }

        when (feedbackMode) {
            Constants.KEYWORD_FEEDBACK_SILENT -> {
                val now = System.currentTimeMillis()
                val detectedWord = result.resultDetectWord ?: ""
                if (lastSilentPackage == packageName && lastSilentKeyword == detectedWord && (now - lastSilentAttemptTime) < 45_000L) {
                    silentAttemptCount++
                } else {
                    silentAttemptCount = 1
                    lastSilentPackage = packageName
                    lastSilentKeyword = detectedWord
                }
                lastSilentAttemptTime = now

                if (silentAttemptCount >= 3) {
                    silentAttemptCount = 0
                    Handler(Looper.getMainLooper()).post {
                        handGestureOverlayManager.showGestureOverlay(
                            detectedKeyword = result.resultDetectWord,
                            isHomePress = false,
                            onComplete = {}
                        )
                    }
                }
            }

            Constants.KEYWORD_FEEDBACK_HAND_GESTURE -> {
                Handler(Looper.getMainLooper()).post {
                    handGestureOverlayManager.showGestureOverlay(
                        detectedKeyword = result.resultDetectWord,
                        isHomePress = isHomePress,
                        onComplete = {
                            if (isHomePress) {
                                onPressHome()
                            }
                        }
                    )
                }
            }

            Constants.KEYWORD_FEEDBACK_WARNING_SCREEN -> {
                val warningConfig = savedPreferencesLoader.loadKeywordBlockerWarningInfo()
                if (warningConfig.isWarningDialogHidden) {
                    if (isHomePress) onPressHome()
                    return
                }

                val dialogIntent = Intent(context, WarningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("mode", Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER)
                    putExtra("result_id", result.resultDetectWord ?: "detected_keyword")
                    putExtra("is_press_home", isHomePress)
                    putExtra("blocked_by_feature", "Keyword Blocker")
                }
                context.startActivity(dialogIntent)
            }
        }
    }
}
