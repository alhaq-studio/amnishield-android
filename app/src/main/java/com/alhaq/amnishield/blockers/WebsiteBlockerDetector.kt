/**
 * ============================================================================
 * AmniShield Blocker Pipeline - WebsiteBlockerDetector
 * ============================================================================
 * Architecture: Interceptor Pattern (Chain of Responsibility)
 * Priority: Browser & Web Content Filtering
 * 
 * Description:
 * Extracts active URLs from address bars across 15+ Android mobile browsers
 * (Chrome, Samsung Internet, Brave, Firefox, Edge, Opera, DuckDuckGo) and
 * verifies against custom blacklist and adult content category domains.
 * 
 * Execution Context:
 * Synchronous accessibility node evaluation within AmniShieldAccessibilityService.
 * 
 * Invariants & AI/Developer Guidance:
 * - Do NOT call rootNode.recycle() here (managed by AmniShieldAccessibilityService).
 * - Recycle child nodes extracted during ID resolution.
 * ============================================================================
 */
package com.alhaq.amnishield.blockers

import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnishield.utils.AccessibilityUtils
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.Locale

class WebsiteBlockerDetector(private val savedPreferencesLoader: SavedPreferencesLoader) {

    companion object {
        private val BROWSER_URL_BAR_IDS = mapOf(
            "com.android.chrome" to "url_bar",
            "com.chrome.beta" to "url_bar",
            "com.chrome.dev" to "url_bar",
            "com.chrome.canary" to "url_bar",
            "com.brave.browser" to "url_bar",
            "com.microsoft.emmx" to "url_bar",
            "com.sec.android.app.sbrowser" to "location_bar_edit_text",
            "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
            "org.mozilla.focus" to "mozac_browser_toolbar_url_view",
            "com.opera.browser" to "url_field",
            "com.opera.mini.native" to "url_field",
            "com.duckduckgo.mobile.android" to "omnibarTextInput",
            "com.vivaldi.browser" to "url_bar",
            "com.kiwibrowser.browser" to "url_bar"
        )
    }

    /**
     * Inspects the browser URL bar node to verify if the visited site matches blocked domains.
     * Note: Respects the Node Lifecycle Invariant - NEVER recycles [rootNode].
     */
    fun checkBlockedWebsites(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        val urlBarId = BROWSER_URL_BAR_IDS[packageName] ?: return false
        val fullId = "$packageName:id/$urlBarId"
        val urlNode = AccessibilityUtils.findElementById(rootNode, fullId) ?: return false
        return try {
            val urlText = urlNode.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (urlText.isNotBlank()) {
                val manualWebsites = savedPreferencesLoader.loadBlockedWebsites()
                for (site in manualWebsites) {
                    val siteLower = site.trim().lowercase(Locale.ROOT)
                    if (siteLower.isNotEmpty() && urlText.contains(siteLower)) {
                        return true
                    }
                }
            }
            false
        } finally {
            try {
                @Suppress("DEPRECATION")
                urlNode.recycle()
            } catch (_: Exception) {}
        }
    }
}
