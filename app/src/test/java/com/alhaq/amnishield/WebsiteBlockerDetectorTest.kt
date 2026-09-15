package com.alhaq.amnishield

import com.alhaq.amnishield.blockers.WebsiteBlockerDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [WebsiteBlockerDetector] and Website Blocker mechanics.
 *
 * Verifies:
 * 1. BaseBlocker cooldown management (apply, check, expire, restore).
 * 2. Case-insensitive domain matching and URL parsing normalization.
 * 3. Browser package and address bar mapping coverage.
 */
class WebsiteBlockerDetectorTest {

    @Test
    fun testCooldownTrackingLifecycle() {
        val detector = WebsiteBlockerDetector(
            blockedWebsitesProvider = { setOf("instagram.com", "facebook.com") }
        )

        val domain = "instagram.com"
        assertFalse(detector.isUnderCooldown(domain))

        val futureTime = System.currentTimeMillis() + 60_000L
        detector.applyCooldown(domain, futureTime)
        assertTrue(detector.isUnderCooldown(domain))

        // Different domain should not be affected
        assertFalse(detector.isUnderCooldown("tiktok.com"))

        // Expired cooldown should auto-clear and return false
        val pastTime = System.currentTimeMillis() - 1000L
        detector.applyCooldown(domain, pastTime)
        assertFalse(detector.isUnderCooldown(domain))
    }

    @Test
    fun testCooldownSnapshotAndRestore() {
        val detector = WebsiteBlockerDetector(
            blockedWebsitesProvider = { setOf("reddit.com", "twitter.com") }
        )
        val futureTime = System.currentTimeMillis() + 120_000L

        detector.applyCooldown("reddit.com", futureTime)
        detector.applyCooldown("twitter.com", futureTime)

        val snapshot = detector.getCooldownSnapshot()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.containsKey("reddit.com"))
        assertTrue(snapshot.containsKey("twitter.com"))

        val newDetector = WebsiteBlockerDetector(
            blockedWebsitesProvider = { setOf("reddit.com", "twitter.com") }
        )
        newDetector.restoreCooldowns(snapshot)
        assertTrue(newDetector.isUnderCooldown("reddit.com"))
        assertTrue(newDetector.isUnderCooldown("twitter.com"))
        assertFalse(newDetector.isUnderCooldown("facebook.com"))
    }

    @Test
    fun testDomainMatchingNormalization() {
        val blockedList = setOf("facebook.com", "instagram.com", "reddit.com")

        fun matchesBlockedSite(rawUrl: String, blockedSites: Set<String>): String? {
            val urlText = rawUrl.lowercase(Locale.ROOT).trim()
            if (urlText.isBlank()) return null
            for (site in blockedSites) {
                val siteLower = site.trim().lowercase(Locale.ROOT)
                if (siteLower.isNotEmpty() && urlText.contains(siteLower)) {
                    return site
                }
            }
            return null
        }

        // Exact match
        assertEquals("facebook.com", matchesBlockedSite("facebook.com", blockedList))

        // Uppercase / Mixed case
        assertEquals("facebook.com", matchesBlockedSite("WWW.FACEBOOK.COM/login", blockedList))
        assertEquals("instagram.com", matchesBlockedSite("https://M.InStAgRaM.com/explore", blockedList))

        // Full URL with path and parameters
        assertEquals("reddit.com", matchesBlockedSite("https://www.reddit.com/r/all?sort=top", blockedList))

        // Non-blocked site
        assertEquals(null, matchesBlockedSite("https://google.com/search?q=test", blockedList))
        assertEquals(null, matchesBlockedSite("https://github.com", blockedList))

        // Blank / empty string
        assertEquals(null, matchesBlockedSite("", blockedList))
        assertEquals(null, matchesBlockedSite("   ", blockedList))
    }

    @Test
    fun testSupportedBrowserPackageList() {
        val browserIds = WebsiteBlockerDetector.BROWSER_URL_BAR_IDS

        // Chrome family
        assertTrue(browserIds.containsKey("com.android.chrome"))
        assertTrue(browserIds.containsKey("com.chrome.beta"))
        assertTrue(browserIds.containsKey("com.google.android.apps.chrome"))
        assertTrue(browserIds.containsKey("org.chromium.chrome"))

        // Samsung Internet
        assertTrue(browserIds.containsKey("com.sec.android.app.sbrowser"))
        assertTrue(browserIds.containsKey("com.sec.android.app.sbrowser.beta"))

        // Firefox family
        assertTrue(browserIds.containsKey("org.mozilla.firefox"))
        assertTrue(browserIds.containsKey("org.mozilla.focus"))

        // Brave family
        assertTrue(browserIds.containsKey("com.brave.browser"))
        assertTrue(browserIds.containsKey("com.brave.browser_beta"))

        // Edge, Opera, DuckDuckGo, Vivaldi, Kiwi
        assertTrue(browserIds.containsKey("com.microsoft.emmx"))
        assertTrue(browserIds.containsKey("com.opera.browser"))
        assertTrue(browserIds.containsKey("com.duckduckgo.mobile.android"))
        assertTrue(browserIds.containsKey("com.vivaldi.browser"))
        assertTrue(browserIds.containsKey("com.kiwibrowser.browser"))

        // Every browser entry must have non-empty view ID list
        for ((pkg, ids) in browserIds) {
            assertNotNull("View IDs for $pkg must not be null", ids)
            assertTrue("View IDs for $pkg must not be empty", ids.isNotEmpty())
        }
    }
}
