package com.alhaq.amnshield.blockers

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.ui.activity.WarningActivity
import com.alhaq.amnshield.utils.SavedPreferencesLoader

/**
 * Handles action execution for Short-Form Video (Reels/Shorts) blocks:
 * - Home feed redirection (via [HomeFeedNavigator])
 * - Android home return (silent)
 * - Hard block (back press + warning dialog)
 */
class ReelActionHandler(
    private val context: Context,
    private val savedPreferencesLoader: SavedPreferencesLoader,
    private val homeFeedNavigator: HomeFeedNavigator
) {
    fun handleReelBlock(
        result: ReelBlocker.ReelBlockerResult,
        onPressHome: () -> Unit,
        onPressBack: () -> Unit,
        getRootInActiveWindow: () -> AccessibilityNodeInfo?
    ) {
        if (!result.isBlocked) return

        when (result.blockResponseMode) {
            ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT -> {
                val root = getRootInActiveWindow()
                val navigated = if (root != null) {
                    val pkg = root.packageName?.toString().orEmpty()
                    val success = homeFeedNavigator.navigateToHomeFeed(root, pkg)
                    try {
                        @Suppress("DEPRECATION")
                        root.recycle()
                    } catch (_: Exception) {}
                    success
                } else false
                if (!navigated) onPressBack()
                return
            }
            ReelBlocker.BlockResponseMode.ANDROID_HOME -> {
                onPressHome()
                return
            }
            ReelBlocker.BlockResponseMode.HARD_BLOCK -> {
                onPressBack()
            }
        }

        val warningConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
        if (warningConfig.isWarningDialogHidden) return

        val dialogIntent = Intent(context, WarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("mode", Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER)
            putExtra("result_id", result.viewId)
            putExtra("is_press_home", result.requestHomePressInstead)
            putExtra("is_reel_blocker", true)
            putExtra("blocked_by_feature", "Reels Blocker")
        }
        context.startActivity(dialogIntent)
    }
}
