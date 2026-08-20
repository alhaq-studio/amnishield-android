package com.alhaq.amnshield

import com.alhaq.amnshield.trackers.ReelDetectionEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [ReelDetectionEngine].
 *
 * Verifies platform detection mapping, candidate package lookups,
 * browser short-form video regex patterns, and substantive reel progression logic.
 */
class ReelDetectionEngineTest {

    @Test
    fun testSupportedPackagesContainAllMajorSocialPlatforms() {
        val supported = ReelDetectionEngine.SUPPORTED_REEL_PACKAGES
        assertTrue("Instagram must be a supported package", supported.contains("com.instagram.android"))
        assertTrue("Instagram Lite must be a supported package", supported.contains("com.instagram.lite"))
        assertTrue("YouTube must be a supported package", supported.contains("com.google.android.youtube"))
        assertTrue("ReVanced YouTube must be a supported package", supported.contains("app.revanced.android.youtube"))
        assertTrue("TikTok must be a supported package", supported.contains("com.zhiliaoapp.musically"))
        assertTrue("TikTok Trill must be a supported package", supported.contains("com.ss.android.ugc.trill"))
        assertTrue("Facebook must be a supported package", supported.contains("com.facebook.katana"))
        assertTrue("Snapchat must be a supported package", supported.contains("com.snapchat.android"))
    }

    @Test
    fun testIsCandidatePackageResolution() {
        // Native apps
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.instagram.android"))
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.google.android.youtube"))
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.zhiliaoapp.musically"))

        // Browsers
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.android.chrome"))
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("org.mozilla.firefox"))
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.brave.browser"))
        assertTrue(ReelDetectionEngine.isReelCandidatePackage("com.sec.android.app.sbrowser"))

        // Unrelated app
        assertFalse(ReelDetectionEngine.isReelCandidatePackage("com.google.android.calculator"))
        assertFalse(ReelDetectionEngine.isReelCandidatePackage("com.android.settings"))
    }

    @Test
    fun testTikTokDedicatedPackageDetection() {
        val engine = ReelDetectionEngine()
        for (pkg in ReelDetectionEngine.TIKTOK_PACKAGES) {
            val result = engine.detectReelSurface(null, pkg)
            // When rootNode is null, it should safely return null
            assertNull(result)
        }
    }

    @Test
    fun testBrowserShortsRegexPatterns() {
        val ytShorts1 = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val ytShorts2 = "https://m.youtube.com/shorts/abc12345"
        val ytShorts3 = "https://youtu.be/shorts/xyz"
        val igReel1 = "https://www.instagram.com/reels/C8K123456/"
        val igReel2 = "https://www.instagram.com/user/reel/C8K123456/"
        val tiktok1 = "https://www.tiktok.com/@creator/video/1234567890"

        val normalYt = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val normalIg = "https://www.instagram.com/direct/inbox/"

        val ytPattern1 = Regex("""youtube\.com/shorts/""", RegexOption.IGNORE_CASE)
        val ytPattern2 = Regex("""youtu\.be/shorts/""", RegexOption.IGNORE_CASE)
        val igPattern1 = Regex("""instagram\.com/reels?(/|$|\?)""", RegexOption.IGNORE_CASE)
        val igPattern2 = Regex("""instagram\.com/[^/?#]+/reel/""", RegexOption.IGNORE_CASE)
        val tiktokPattern = Regex("""tiktok\.com""", RegexOption.IGNORE_CASE)

        assertTrue(ytPattern1.containsMatchIn(ytShorts1))
        assertTrue(ytPattern1.containsMatchIn(ytShorts2))
        assertTrue(ytPattern2.containsMatchIn(ytShorts3))
        assertFalse(ytPattern1.containsMatchIn(normalYt))

        assertTrue(igPattern1.containsMatchIn(igReel1))
        assertTrue(igPattern2.containsMatchIn(igReel2))
        assertFalse(igPattern1.containsMatchIn(normalIg))

        assertTrue(tiktokPattern.containsMatchIn(tiktok1))
    }

    @Test
    fun testSubstantialTextChangeAvoidsFalsePositivesOnMinorMovement() {
        val engine = ReelDetectionEngine()

        val reelA = "How to build android apps with jetpack compose - by dev_expert"
        val reelASlightJitter = "How to build android apps with jetpack compose - by dev_expert"
        val reelAWithOneMinorWord = "How to build android apps with jetpack compose and kotlin - by dev_expert"

        // Identical texts or slight movement should NOT count as substantial change
        assertFalse(engine.isSubstantialTextChange(reelA, reelASlightJitter))
        assertFalse(engine.isSubstantialTextChange(reelA, reelAWithOneMinorWord))

        // A completely new reel caption should be recognized as substantial change
        val reelB = "Top 10 morning productivity habits that will change your life completely"
        assertTrue(engine.isSubstantialTextChange(reelA, reelB))
    }

    @Test
    fun testProgressionDeduplicationWithLruCache() {
        val engine = ReelDetectionEngine()
        val pkg = "com.instagram.android"

        val reel1 = "First Reel Caption Here by user1"
        val reel2 = "Second Reel Caption Completely Different by user2"

        // First appearance sets baseline (returns false so initial open isn't counted as a scroll)
        assertFalse(engine.checkReelProgression(pkg, reel1))

        // Minor movement on the same reel -> returns false
        assertFalse(engine.checkReelProgression(pkg, reel1))

        // Swiping to reel 2 -> returns true (counted!)
        assertTrue(engine.checkReelProgression(pkg, reel2))

        // Minor movement on reel 2 -> returns false
        assertFalse(engine.checkReelProgression(pkg, reel2))

        // Scrolling back up to reel 1 -> deduplicated by LruCache (returns false, not counted again)
        assertFalse(engine.checkReelProgression(pkg, reel1))
    }

    @Test
    fun testPositionIndexProgression() {
        val engine = ReelDetectionEngine()
        val pkg = "com.instagram.lite"

        assertFalse(engine.checkReelProgression(pkg, "position:0"))
        assertTrue(engine.checkReelProgression(pkg, "position:1"))
        assertTrue(engine.checkReelProgression(pkg, "position:2"))
        assertFalse(engine.checkReelProgression(pkg, "position:2")) // same position -> false
    }

    @Test
    fun testBlockResponseModeValuesAndFromInt() {
        assertEquals(com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.HARD_BLOCK, com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.fromInt(0))
        assertEquals(com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.ANDROID_HOME, com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.fromInt(1))
        assertEquals(com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT, com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.fromInt(2))
        // Invalid or unknown ints fall back to HARD_BLOCK
        assertEquals(com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.HARD_BLOCK, com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.fromInt(99))
        assertEquals(com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.HARD_BLOCK, com.alhaq.amnshield.blockers.ReelBlocker.BlockResponseMode.fromInt(-1))
    }
}

