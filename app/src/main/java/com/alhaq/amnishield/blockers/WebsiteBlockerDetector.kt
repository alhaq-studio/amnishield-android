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
    private val blockedWebsitesProvider: (() -> Set<String>)? = null,
    private val domainBlocklistEngine: DomainBlocklistEngine? = null
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
            "com.sec.android.app.sbrowser" to listOf(
                "location_bar_edit_text",
                "url_bar",
                "url_text",
                "location_bar_text",
                "toolbar_url_bar",
                "url_edit_text"
            ),
            "com.sec.android.app.sbrowser.beta" to listOf(
                "location_bar_edit_text",
                "url_bar",
                "url_text",
                "location_bar_text",
                "toolbar_url_bar",
                "url_edit_text"
            ),
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
    fun checkBlockedWebsites(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        activeWebsites: Set<String>? = null
    ): Boolean {
        return findBlockedWebsite(rootNode, packageName, activeWebsites) != null
    }

    /**
     * Returns the matched blocked domain if present in the address bar and not under cooldown.
     * If [activeWebsites] is provided, matches ONLY against those active websites.
     */
    fun findBlockedWebsite(
        rootNode: AccessibilityNodeInfo,
        packageName: String,
        activeWebsites: Set<String>? = null
    ): String? {
        val urlNode = getUrlBarNode(rootNode, packageName) ?: return null
        return try {
            val rawText = urlNode.text?.toString() ?: urlNode.contentDescription?.toString()
            val urlText = rawText.orEmpty().lowercase(Locale.ROOT).trim()
            if (urlText.isNotBlank()) {
                val host = cleanUrlToDomain(urlText)
                if (host != null) {
                    val engineMatch = domainBlocklistEngine?.findMatchingSuffix(host)
                    if (engineMatch != null && !isUnderCooldown(engineMatch)) {
                        return engineMatch
                    }
                }
                val candidateWebsites = activeWebsites
                    ?: blockedWebsitesProvider?.invoke()
                    ?: savedPreferencesLoader?.loadBlockedWebsites()
                    ?: emptySet()
                for (site in candidateWebsites) {
                    val siteLower = site.trim().lowercase(Locale.ROOT)
                    if (siteLower.isEmpty() || isUnderCooldown(siteLower)) continue
                    val matched = if (host != null) {
                        host == siteLower || host.endsWith(".$siteLower")
                    } else {
                        urlText.contains(siteLower)
                    }
                    if (matched) {
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
        // Fallback: Check active input focus and bounded BFS if inside a known browser
        if (BROWSER_URL_BAR_IDS.containsKey(packageName)) {
            val focused = try { rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
            if (focused != null) {
                val hasText = !focused.text.isNullOrBlank()
                if (focused.isEditable || hasText) {
                    return focused
                }
                try {
                    @Suppress("DEPRECATION")
                    focused.recycle()
                } catch (_: Exception) {}
            }

            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            try {
                for (i in 0 until rootNode.childCount) {
                    rootNode.getChild(i)?.let { queue.add(it) }
                }
                var visited = 0
                while (queue.isNotEmpty() && visited < 40) {
                    val node = queue.removeFirst()
                    visited++
                    val resId = node.viewIdResourceName?.lowercase(Locale.ROOT).orEmpty()
                    val cls = node.className?.toString().orEmpty()
                    val isTextOrEdit = cls == "android.widget.EditText" || cls == "android.widget.TextView" || node.isEditable
                    val isUrlId = resId.contains("url") ||
                        resId.contains("location_bar") ||
                        resId.contains("omnibox") ||
                        resId.contains("address")
                    val hasNonBlankContent = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
                    if (isTextOrEdit && isUrlId && hasNonBlankContent) {
                        return node
                    }
                    if (visited < 40) {
                        for (i in 0 until node.childCount) {
                            node.getChild(i)?.let { queue.add(it) }
                        }
                    }
                    try {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    } catch (_: Exception) {}
                }
            } finally {
                while (queue.isNotEmpty()) {
                    try {
                        @Suppress("DEPRECATION")
                        queue.removeFirst().recycle()
                    } catch (_: Exception) {}
                }
            }
        }
        return null
    }

    /**
     * Extracts the active domain from the browser's address bar.
     * Adheres to Node Lifecycle Invariant: does NOT recycle [rootNode], recycles resolved child node in finally.
     */
    fun extractCurrentDomain(rootNode: AccessibilityNodeInfo, packageName: String): String? {
        val urlNode = getUrlBarNode(rootNode, packageName) ?: return null
        return try {
            val rawText = urlNode.text?.toString() ?: urlNode.contentDescription?.toString()
            cleanUrlToDomain(rawText)
        } finally {
            try {
                @Suppress("DEPRECATION")
                urlNode.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cleans and validates a raw address bar string into a normalized domain name.
     */
    fun cleanUrlToDomain(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var text = raw.trim().lowercase(Locale.ROOT)
        // Strip protocols
        if (text.startsWith("http://")) text = text.substring(7)
        if (text.startsWith("https://")) text = text.substring(8)
        // Strip common prefixes
        if (text.startsWith("www.")) text = text.substring(4)
        if (text.startsWith("m.")) text = text.substring(2)
        if (text.startsWith("mobile.")) text = text.substring(7)
        // Cut off path, query, port, fragment
        val delimiters = charArrayOf('/', ':', '?', '#', '\\')
        val firstDelim = text.indexOfAny(delimiters)
        if (firstDelim != -1) {
            text = text.substring(0, firstDelim)
        }
        text = text.trim()
        // Domain validation: contains dot, no spaces, length >= 4, not starting/ending with dot
        return if (text.contains(".") && !text.contains(" ") && text.length >= 4 && !text.startsWith(".") && !text.endsWith(".")) {
            text
        } else {
            null
        }
    }

    /**
     * Checks whether the current browser window is an incognito / private browsing session.
     * Node Lifecycle Invariant: NEVER recycles [rootNode]; recycles only child nodes returned by find*.
     */
    fun isIncognitoOrPrivateTab(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        if (!BROWSER_URL_BAR_IDS.containsKey(packageName)) return false
        val markers = listOf("Incognito", "You've gone incognito", "Private browsing", "Secret mode", "InPrivate")
        for (marker in markers) {
            val nodes = runCatching { rootNode.findAccessibilityNodeInfosByText(marker) }.getOrNull()
            if (!nodes.isNullOrEmpty()) {
                nodes.forEach { child ->
                    try {
                        @Suppress("DEPRECATION")
                        child.recycle()
                    } catch (_: Exception) {}
                }
                return true
            }
        }
        return false
    }

    /**
     * Identifies known obscure/unsupported Android browsers not in [BROWSER_URL_BAR_IDS].
     */
    fun isUnsupportedBrowserPackage(packageName: String): Boolean {
        if (BROWSER_URL_BAR_IDS.containsKey(packageName)) return false
        val lower = packageName.lowercase(Locale.ROOT)
        return lower.contains("browser") ||
            lower.contains("ucmobile") ||
            lower.contains("puffin") ||
            lower.contains("torbrowser") ||
            lower.contains("dolphin") ||
            lower.contains("yandex.browser") ||
            lower.contains("alohabrowser")
    }
}
