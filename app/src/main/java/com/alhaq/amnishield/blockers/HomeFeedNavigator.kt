package com.alhaq.amnishield.blockers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Navigates to the home feed of a supported social media application by
 * inspecting the accessibility node tree and clicking the platform's
 * native home / news-feed navigation element.
 *
 * This is used as the "Home Feed Redirect" block response mode — a
 * gentler alternative to hard-blocking or pressing the Android Home button.
 * Rather than ejecting the user out of the app, we keep them inside the
 * platform but return them to a safe, non-addictive surface.
 *
 * Fallback chain: platform home tab → GLOBAL_ACTION_BACK.
 */
class HomeFeedNavigator {

    companion object {

        /** Ordered list of (viewId or desc: prefix, platform) candidates for each app's Home tab. */
        private val HOME_TAB_TARGETS: Map<String, List<String>> = mapOf(

            // Instagram — bottom nav home icon
            "com.instagram.android" to listOf(
                "com.instagram.android:id/tab_home",
                "com.instagram.android:id/ig_nav_tab_home",
                "desc:Home"
            ),
            "com.myinsta.android" to listOf(
                "com.myinsta.android:id/tab_home",
                "desc:Home"
            ),
            "com.instagram.lite" to listOf(
                "com.instagram.lite:id/tab_home",
                "desc:Home"
            ),

            // YouTube — home tab in the bottom bar
            "com.google.android.youtube" to listOf(
                "com.google.android.youtube:id/home_button",
                "com.google.android.youtube:id/pivot_bar_item_home",
                "desc:Home"
            ),
            "app.revanced.android.youtube" to listOf(
                "app.revanced.android.youtube:id/home_button",
                "app.revanced.android.youtube:id/pivot_bar_item_home",
                "desc:Home"
            ),
            "app.morphe.android.youtube" to listOf(
                "app.morphe.android.youtube:id/home_button",
                "desc:Home"
            ),

            // TikTok — home icon in bottom navigation
            "com.zhiliaoapp.musically" to listOf(
                "com.zhiliaoapp.musically:id/fl_tab_home",
                "desc:Home"
            ),
            "com.ss.android.ugc.trill" to listOf(
                "com.ss.android.ugc.trill:id/fl_tab_home",
                "desc:Home"
            ),
            "com.ss.android.ugc.aweme" to listOf(
                "com.ss.android.ugc.aweme:id/fl_tab_home",
                "desc:Home"
            ),

            // Facebook — News Feed tab in bottom navigation
            "com.facebook.katana" to listOf(
                "com.facebook.katana:id/tab_news_feed",
                "com.facebook.katana:id/bottom_bar_tab_home",
                "desc:News Feed",
                "desc:Home"
            ),
            "com.facebook.lite" to listOf(
                "com.facebook.lite:id/tab_news_feed",
                "desc:News Feed",
                "desc:Home"
            ),

            // Snapchat — Camera / Friend map is home; Stories is nearest safe surface
            // Back is best here since there is no clean "feed" equivalent
            "com.snapchat.android" to listOf(
                "desc:Friends",
                "desc:Map"
            )
        )
    }

    /**
     * Attempts to navigate to the platform home feed by clicking the home
     * navigation tab node in the accessibility tree.
     *
     * @param rootNode Current root accessibility node of the active window.
     * @param packageName Package name of the foreground app.
     * @return `true` if a home tab node was found and clicked successfully,
     *         `false` if the platform is unknown or the node could not be found
     *         (caller should fall back to `GLOBAL_ACTION_BACK`).
     */
    fun navigateToHomeFeed(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        val candidates = HOME_TAB_TARGETS[packageName] ?: return false

        for (targetId in candidates) {
            val node = findNode(rootNode, targetId)
            if (node != null) {
                try {
                    if (node.isClickable) {
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) return true
                    }
                    // Try parent if the icon itself is not directly clickable
                    val parent = node.parent
                    if (parent != null) {
                        val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        @Suppress("DEPRECATION")
                        parent.recycle()
                        if (parentClicked) return true
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    node.recycle()
                }
            }
        }

        return false
    }

    private fun findNode(rootNode: AccessibilityNodeInfo, identifier: String): AccessibilityNodeInfo? {
        return if (identifier.startsWith("desc:")) {
            val desc = identifier.substring(5)
            findNodeByDescription(rootNode, desc)
        } else {
            ViewBlocker.findElementById(rootNode, identifier)
        }
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString().equals(desc, ignoreCase = true)) {
            @Suppress("DEPRECATION")
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDescription(child, desc)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }
}
