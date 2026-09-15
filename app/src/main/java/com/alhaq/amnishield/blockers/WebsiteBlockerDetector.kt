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

import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnishield.utils.AccessibilityUtils
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.Locale

open class WebsiteBlockerDetector(
    private val savedPreferencesLoader: SavedPreferencesLoader? = null,
    private val blockedWebsitesProvider: (() -> Set<String>)? = null
) : BaseBlocker() {

    companion object {
        val BROWSER_URL_BAR_IDS = mapOf(
            "com.android.chrome" to listOf("url_bar", "title_url"),
            "com.chrome.beta" to listOf("url_bar", "title_url"),
            "com.chrome.dev" to listOf("url_bar", "title_url"),
            "com.chrome.canary" to listOf("url_bar", "title_url"),
            "com.google.android.apps.chrome" to listOf("url_bar", "title_url"),
            "org.chromium.chrome" to listOf("url_bar", "title_url"),
            "com.brave.browser" to listOf("url_bar"),
            "com.brave.browser_beta" to listOf("url_bar"),
            "com.microsoft.emmx" to listOf("url_bar"),
            "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
            "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
            "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view"),
            "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view"),
            "com.opera.browser" to listOf("url_field"),
            "com.opera.mini.native" to listOf("url_field"),
            "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
            "com.vivaldi.browser" to listOf("url_bar"),
            "com.kiwibrowser.browser" to listOf("url_bar")
        )
    }

    /**
     * Inspects the browser URL bar node to verify if the visited site matches blocked domains.
     * Respects cooldowns and Node Lifecycle Invariant (NEVER recycles rootNode).
     */
    fun checkBlockedWebsites(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        return findBlockedWebsite(rootNode, packageName) != null
    }

    /**
     * Returns the matched blocked domain if present in the address bar and not under cooldown.
     */
    fun findBlockedWebsite(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        val urlNode = getUrlBarNode(rootNode, packageName) ?: return null
        return try {
            val rawText = urlNode.text?.toString() ?: urlNode.contentDescription?.toString()
            val urlText = rawText.orEmpty().lowercase(Locale.ROOT).trim()
            if (urlText.isNotBlank()) {
                val manualWebsites = blockedWebsitesProvider?.invoke()
                    ?: savedPreferencesLoader?.loadBlockedWebsites()
                    ?: emptySet()
                for (site in manualWebsites) {
                    val siteLower = site.trim().lowercase(Locale.ROOT)
                    if (siteLower.isNotEmpty() && urlText.contains(siteLower) && !isUnderCooldown(siteLower)) {
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

    private fun getUrlBarNode(rootNode: AccessibilityNodeInfo, packageName: String): AccessibilityNodeInfo? {
        val urlBarIds = BROWSER_URL_BAR_IDS[packageName]
        if (urlBarIds != null) {
            for (barId in urlBarIds) {
                val fullId = "$packageName:id/$barId"
                val node = AccessibilityUtils.findElementById(rootNode, fullId)
                if (node != null) return node
            }
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
