package com.alhaq.amnshield.trackers

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.alhaq.amnshield.blockers.ViewBlocker

/**
 * Lightweight LRU Cache with zero Android framework dependencies.
 */
class LruMemoryCache<K, V>(private val maxEntries: Int = 50) : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxEntries
    }
}

/**
 * High-performance Reel & Short-Form Video Surface Detection Engine.
 *
 * Inspects accessibility node hierarchies across supported social media apps
 * and web browsers to identify active doom-scrolling surfaces (Instagram Reels,
 * YouTube Shorts, TikTok, Facebook Reels, Snapchat Spotlight, etc.), and extracts
 * content comparators (captions, authors, video IDs, or scroll positions) to accurately
 * count reel progressions without false positives from minor touch movements or UI changes.
 *
 * Attribution:
 * Reel surface node patterns and comparator progression mechanics inspired by Curbox by Nethical (GPL-3.0-or-later).
 * Reference: https://github.com/curbox-app/curbox-android
 */
class ReelDetectionEngine {

    companion object {
        const val PLATFORM_INSTAGRAM = "instagram"
        const val PLATFORM_YOUTUBE = "youtube"
        const val PLATFORM_TIKTOK = "tiktok"
        const val PLATFORM_FACEBOOK = "facebook"
        const val PLATFORM_SNAPCHAT = "snapchat"
        const val PLATFORM_BROWSER = "browser"

        val TIKTOK_PACKAGES = hashSetOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
            "com.zhiliao.musically.go"
        )

        val SUPPORTED_REEL_PACKAGES = hashSetOf(
            "com.instagram.android",
            "com.myinsta.android",
            "com.instagram.lite",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.morphe.android.youtube",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.snapchat.android",
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme"
        )

        // Native view ID identifiers for short-form video surfaces mapped to platform
        private val NATIVE_SURFACE_PLATFORMS = linkedMapOf(
            // Instagram Reels ViewPager / Container
            "com.instagram.android:id/clips_viewer_view_pager" to PLATFORM_INSTAGRAM,
            "com.instagram.android:id/clips_video_container" to PLATFORM_INSTAGRAM,
            "com.instagram.android:id/clips_ufi_component" to PLATFORM_INSTAGRAM,
            "com.instagram.android:id/clips_captions_component" to PLATFORM_INSTAGRAM,
            "com.myinsta.android:id/clips_viewer_view_pager" to PLATFORM_INSTAGRAM,
            "com.myinsta.android:id/clips_video_container" to PLATFORM_INSTAGRAM,
            "desc:Tap to show video controls" to PLATFORM_INSTAGRAM,
            "desc:Reels tab" to PLATFORM_INSTAGRAM,

            // YouTube Shorts Recycler / Page Container
            "com.google.android.youtube:id/reel_recycler" to PLATFORM_YOUTUBE,
            "com.google.android.youtube:id/reel_player_page_container" to PLATFORM_YOUTUBE,
            "com.google.android.youtube:id/reel_player_page_content" to PLATFORM_YOUTUBE,
            "com.google.android.youtube:id/reel_view_pager" to PLATFORM_YOUTUBE,
            "app.revanced.android.youtube:id/reel_recycler" to PLATFORM_YOUTUBE,
            "app.revanced.android.youtube:id/reel_player_page_container" to PLATFORM_YOUTUBE,
            "app.morphe.android.youtube:id/reel_recycler" to PLATFORM_YOUTUBE,

            // Facebook Reels
            "desc:Reels tab details" to PLATFORM_FACEBOOK,
            "desc:Reels viewer" to PLATFORM_FACEBOOK,
            "com.facebook.katana:id/fb_shorts_container" to PLATFORM_FACEBOOK,
            "com.facebook.katana:id/fb_reels_viewer_root" to PLATFORM_FACEBOOK,

            // Snapchat Spotlight
            "com.snapchat.android:id/spotlight_container" to PLATFORM_SNAPCHAT,
            "com.snapchat.android:id/spotlight_view_pager" to PLATFORM_SNAPCHAT
        )

        // Browsers whose URL bar is monitored for short video paths
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

        private val BROWSER_URL_PATTERNS = listOf(
            Regex("""youtube\.com/shorts/""", RegexOption.IGNORE_CASE) to PLATFORM_YOUTUBE,
            Regex("""youtu\.be/shorts/""", RegexOption.IGNORE_CASE) to PLATFORM_YOUTUBE,
            Regex("""m\.youtube\.com/shorts/""", RegexOption.IGNORE_CASE) to PLATFORM_YOUTUBE,
            Regex("""instagram\.com/reels?(/|$|\?)""", RegexOption.IGNORE_CASE) to PLATFORM_INSTAGRAM,
            Regex("""instagram\.com/[^/?#]+/reel/""", RegexOption.IGNORE_CASE) to PLATFORM_INSTAGRAM,
            Regex("""tiktok\.com""", RegexOption.IGNORE_CASE) to PLATFORM_TIKTOK
        )

        fun isBrowserPackage(packageName: String): Boolean =
            BROWSER_URL_BAR_IDS.containsKey(packageName)

        fun isReelCandidatePackage(packageName: String): Boolean =
            SUPPORTED_REEL_PACKAGES.contains(packageName) || isBrowserPackage(packageName)
    }

    data class DetectionResult(
        val surfaceId: String,
        val platform: String,
        val isReelSurface: Boolean
    )

    private val lastDynamicText = mutableMapOf<String, String>()
    private val seenReelsCache = mutableMapOf<String, LruMemoryCache<String, Boolean>>()

    /**
     * Inspects the active window root node to detect if a short-form video surface is currently visible.
     * Operates completely independently of whether the Reel Blocker is active or inactive.
     */
    fun detectReelSurface(rootNode: AccessibilityNodeInfo?, packageName: String): DetectionResult? {
        if (rootNode == null || packageName.isBlank()) return null

        // 1. TikTok dedicated package detection
        if (TIKTOK_PACKAGES.contains(packageName)) {
            return DetectionResult(
                surfaceId = packageName,
                platform = PLATFORM_TIKTOK,
                isReelSurface = true
            )
        }

        // 2. Native social media app surfaces (Instagram, YouTube, Facebook, Snapchat)
        for ((viewId, platform) in NATIVE_SURFACE_PLATFORMS) {
            if (isViewVisibleOnScreen(rootNode, viewId)) {
                return DetectionResult(
                    surfaceId = viewId,
                    platform = platform,
                    isReelSurface = true
                )
            }
        }

        // 3. Browser short-form URL detection (Chrome, Firefox, Samsung Internet, etc.)
        if (isBrowserPackage(packageName)) {
            val browserSurface = detectBrowserShortsSurface(rootNode, packageName)
            if (browserSurface != null) {
                return DetectionResult(
                    surfaceId = browserSurface.first,
                    platform = browserSurface.second,
                    isReelSurface = true
                )
            }
        }

        return null
    }

    /**
     * Extracts a content comparator text for the current reel on screen.
     * Extracts captions, authors, titles, or page index.
     */
    fun extractReelComparator(rootNode: AccessibilityNodeInfo?, packageName: String, event: AccessibilityEvent?): String? {
        if (rootNode == null) return null

        // 1. Instagram: extract caption and author
        if (packageName == "com.instagram.android" || packageName == "com.myinsta.android") {
            val caption = readNodeSubtreeText(rootNode, "$packageName:id/clips_captions_component")
            val author = readNodeSubtreeText(rootNode, "$packageName:id/clips_author_username")
            val combined = (caption.orEmpty() + " " + author.orEmpty()).trim()
            if (combined.isNotEmpty()) return combined

            val ufi = readNodeSubtreeText(rootNode, "$packageName:id/clips_ufi_component")
            if (!ufi.isNullOrBlank()) return ufi
        }

        // 2. Instagram Lite: check position index
        if (packageName == "com.instagram.lite") {
            if (event != null && event.fromIndex >= 0) {
                return "position:${event.fromIndex}"
            }
        }

        // 3. YouTube Shorts: extract player page content or container
        if (packageName == "com.google.android.youtube" ||
            packageName == "app.revanced.android.youtube" ||
            packageName == "app.morphe.android.youtube"
        ) {
            val content = readNodeSubtreeText(rootNode, "$packageName:id/reel_player_page_content")
            if (!content.isNullOrBlank()) return content

            val container = readNodeSubtreeText(rootNode, "$packageName:id/reel_player_page_container")
            if (!container.isNullOrBlank()) return container
        }

        // 4. Facebook Reels
        if (packageName == "com.facebook.katana" || packageName == "com.facebook.lite") {
            val reelsTab = findElementByDescription(rootNode, "Reels tab details")
            if (reelsTab != null) {
                val text = getNodeSubtreeText(reelsTab, maxDepth = 6)
                @Suppress("DEPRECATION")
                reelsTab.recycle()
                if (text.isNotBlank()) return text
            }
            val fbShorts = readNodeSubtreeText(rootNode, "$packageName:id/fb_shorts_container")
            if (!fbShorts.isNullOrBlank()) return fbShorts
        }

        // 5. Snapchat Spotlight
        if (packageName == "com.snapchat.android") {
            val spotlight = readNodeSubtreeText(rootNode, "$packageName:id/spotlight_container")
            if (!spotlight.isNullOrBlank()) return spotlight
        }

        // 6. TikTok
        if (TIKTOK_PACKAGES.contains(packageName)) {
            if (event != null && event.fromIndex >= 0) {
                return "position:${event.fromIndex}"
            }
            val text = getNodeSubtreeText(rootNode, maxDepth = 4)
            if (text.isNotBlank()) return text
        }

        // 7. Browsers
        if (isBrowserPackage(packageName)) {
            val browserSurface = detectBrowserShortsSurface(rootNode, packageName)
            if (browserSurface != null) {
                return browserSurface.first
            }
        }

        return null
    }

    /**
     * Checks if a genuine, substantial reel progression has occurred.
     * Prevents minor UI wiggles or window events from falsely incrementing the counter.
     */
    fun checkReelProgression(packageName: String, currentComparator: String?): Boolean {
        if (currentComparator.isNullOrBlank()) return false

        val cache = seenReelsCache.getOrPut(packageName) { LruMemoryCache(50) }
        val previousText = lastDynamicText[packageName] ?: ""

        if (previousText.isEmpty()) {
            // Initial baseline reel seen
            lastDynamicText[packageName] = currentComparator
            cache[currentComparator] = true
            return false
        }

        if (currentComparator != previousText) {
            val isSubstantial = isSubstantialTextChange(currentComparator, previousText)
            if (isSubstantial) {
                if (cache[currentComparator] == null) {
                    cache[currentComparator] = true
                    lastDynamicText[packageName] = currentComparator
                    return true
                }
                lastDynamicText[packageName] = currentComparator
            } else if (currentComparator.length > previousText.length) {
                lastDynamicText[packageName] = currentComparator
            }
        }
        return false
    }

    /**
     * Determines whether two comparator strings represent a substantially different reel.
     * Uses word intersection analysis (< 85% overlap indicates a new reel).
     */
    fun isSubstantialTextChange(currentText: String, previousText: String): Boolean {
        if (currentText.isEmpty() || previousText.isEmpty()) return true
        if (currentText == previousText) return false

        // Position index comparisons
        if (currentText.startsWith("position:") && previousText.startsWith("position:")) {
            return currentText != previousText
        }

        // Browser URL comparisons
        if (currentText.startsWith("browser:") && previousText.startsWith("browser:")) {
            return currentText != previousText
        }

        fun countWords(text: String, wordCounts: HashMap<String, Int>) {
            val len = text.length
            var start = -1
            for (i in 0 until len) {
                if (text[i].isWhitespace()) {
                    if (start != -1) {
                        val word = text.substring(start, i).lowercase()
                        wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
                        start = -1
                    }
                } else {
                    if (start == -1) start = i
                }
            }
            if (start != -1) {
                val word = text.substring(start, len).lowercase()
                wordCounts[word] = wordCounts.getOrDefault(word, 0) + 1
            }
        }

        val currentWords = HashMap<String, Int>()
        val previousWords = HashMap<String, Int>()

        countWords(currentText, currentWords)
        countWords(previousText, previousWords)

        if (currentWords.isEmpty() || previousWords.isEmpty()) return true

        var intersectionSize = 0
        var totalSmaller = 0

        val smallerMap = if (currentWords.size < previousWords.size) currentWords else previousWords
        val largerMap = if (currentWords.size < previousWords.size) previousWords else currentWords

        for ((word, count) in smallerMap) {
            totalSmaller += count
            val largerCount = largerMap[word] ?: 0
            intersectionSize += minOf(count, largerCount)
        }

        if (totalSmaller == 0) return true

        val overlapRatio = intersectionSize.toFloat() / totalSmaller
        return overlapRatio < 0.85f
    }

    fun resetSession(packageName: String? = null) {
        if (packageName != null) {
            lastDynamicText.remove(packageName)
            seenReelsCache.remove(packageName)
        } else {
            lastDynamicText.clear()
            seenReelsCache.clear()
        }
    }

    private fun isViewVisibleOnScreen(rootNode: AccessibilityNodeInfo, identifier: String): Boolean {
        if (identifier.startsWith("desc:")) {
            val desc = identifier.substring(5)
            val node = findElementByDescription(rootNode, desc)
            val found = node != null
            @Suppress("DEPRECATION")
            node?.recycle()
            return found
        }
        val node = ViewBlocker.findElementById(rootNode, identifier)
        val found = node != null
        @Suppress("DEPRECATION")
        node?.recycle()
        return found
    }

    private fun readNodeSubtreeText(rootNode: AccessibilityNodeInfo, viewId: String): String? {
        val node = ViewBlocker.findElementById(rootNode, viewId) ?: return null
        return try {
            getNodeSubtreeText(node, maxDepth = 4)
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun getNodeSubtreeText(node: AccessibilityNodeInfo?, maxDepth: Int): String {
        if (node == null || maxDepth <= 0) return ""
        val sb = StringBuilder()
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            sb.append(text).append(" ")
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc != text) {
            sb.append(desc).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childText = getNodeSubtreeText(child, maxDepth - 1)
            @Suppress("DEPRECATION")
            child.recycle()
            if (childText.isNotBlank()) {
                sb.append(childText).append(" ")
            }
        }
        return sb.toString().trim()
    }

    private fun findElementByDescription(node: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString().equals(desc, ignoreCase = true)) {
            @Suppress("DEPRECATION")
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findElementByDescription(child, desc)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun detectBrowserShortsSurface(
        rootNode: AccessibilityNodeInfo,
        packageName: String
    ): Pair<String, String>? {
        val urlBarId = BROWSER_URL_BAR_IDS[packageName] ?: return null
        val fullId = "$packageName:id/$urlBarId"
        val node = ViewBlocker.findElementById(rootNode, fullId) ?: return null
        val urlText = try {
            val text = node.text?.toString().orEmpty()
            if (text.isNotBlank()) text else node.contentDescription?.toString().orEmpty()
        } finally {
            @Suppress("DEPRECATION")
            node.recycle()
        }

        if (urlText.isBlank()) return null

        for ((regex, platform) in BROWSER_URL_PATTERNS) {
            if (regex.containsMatchIn(urlText)) {
                return "browser:$packageName:$platform" to platform
            }
        }
        return null
    }
}
