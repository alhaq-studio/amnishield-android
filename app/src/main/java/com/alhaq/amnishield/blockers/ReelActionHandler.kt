package com.alhaq.amnishield.blockers

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.ui.activity.WarningActivity
import com.alhaq.amnishield.utils.SavedPreferencesLoader

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
            ReelBlocker.BlockResponseMode.MINDFUL_PAUSE -> {
                val root = getRootInActiveWindow()
                val pkg = root?.packageName?.toString().orEmpty()
                try {
                    @Suppress("DEPRECATION")
                    root?.recycle()
                } catch (_: Exception) {}
                val amnispaceIntent = Intent(context, com.alhaq.amnishield.ui.activity.AmniSpaceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Reels Interception")
                    if (pkg.isNotEmpty()) {
                        putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, pkg)
                    }
                }
                context.startActivity(amnispaceIntent)
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
