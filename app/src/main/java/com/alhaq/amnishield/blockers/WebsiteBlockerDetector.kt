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
 * Supports Silent Intercept Mode to clean up blocked URLs directly.
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

import android.os.Bundle
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
        return findBlockedWebsite(rootNode, packageName) != null
    }

    /**
     * Returns the matched blocked domain/keyword if present in the address bar.
     */
    fun findBlockedWebsite(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        val urlNode = getUrlBarNode(rootNode, packageName) ?: return null
        return try {
            val urlText = urlNode.text?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (urlText.isNotBlank()) {
                val manualWebsites = savedPreferencesLoader.loadBlockedWebsites()
                for (site in manualWebsites) {
                    val siteLower = site.trim().lowercase(Locale.ROOT)
                    if (siteLower.isNotEmpty() && urlText.contains(siteLower)) {
                        return site
                    }
                }
            }
            null
        } finally {
            try {
                @Suppress("DEPRECATION")
                urlNode.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cleans up / clears the blocked domain from the address bar (Silent Intercept Mode).
     */
    fun clearBlockedUrl(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        val urlNode = getUrlBarNode(rootNode, packageName) ?: return false
        return try {
            urlNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        ""
                    )
                }
            )
        } catch (_: Exception) {
            false
        } finally {
            try {
                @Suppress("DEPRECATION")
                urlNode.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun getUrlBarNode(rootNode: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        val urlBarId = BROWSER_URL_BAR_IDS[packageName]
        if (urlBarId != null) {
            val fullId = "$packageName:id/$urlBarId"
            val node = AccessibilityUtils.findElementById(rootNode, fullId)
            if (node != null) return node
        }
        // Fallback: Check active input focus if inside a known browser
        if (BROWSER_URL_BAR_IDS.containsKey(packageName)) {
            val focused = try { rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
            if (focused != null && focused.isEditable) {
                return focused
            }
        }
        return null
    }
}
