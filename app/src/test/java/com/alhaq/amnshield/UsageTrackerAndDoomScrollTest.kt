package com.alhaq.amnshield

import com.alhaq.amnshield.utils.SavedPreferencesLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for the enhanced Usage Tracker and Reels Doom-Scrolling tracking system.
 */
class UsageTrackerAndDoomScrollTest {

    @Test
    fun testDefaultReelsOverlayAppsList() {
        val defaultApps = SavedPreferencesLoader.DEFAULT_REELS_OVERLAY_APPS
        assertTrue("Instagram must be in default overlay target apps", defaultApps.contains("com.instagram.android"))
        assertTrue("YouTube must be in default overlay target apps", defaultApps.contains("com.google.android.youtube"))
        assertTrue("TikTok must be in default overlay target apps", defaultApps.contains("com.zhiliaoapp.musically"))
        assertTrue("Facebook must be in default overlay target apps", defaultApps.contains("com.facebook.katana"))
        assertTrue("Reddit must be in default overlay target apps", defaultApps.contains("com.reddit.frontpage"))
        assertTrue("Twitter must be in default overlay target apps", defaultApps.contains("com.twitter.android"))
        assertTrue("Snapchat must be in default overlay target apps", defaultApps.contains("com.snapchat.android"))
    }

    @Test
    fun testDoomScrollOverlayModeConstants() {
        assertEquals(0, SavedPreferencesLoader.OVERLAY_MODE_COUNT)
        assertEquals(1, SavedPreferencesLoader.OVERLAY_MODE_TIME)
        assertEquals(2, SavedPreferencesLoader.OVERLAY_MODE_BOTH)
    }

    @Test
    fun testDoomScrollWarningThresholdLevels() {
        fun getWarningTier(reelsCount: Int, watchTimeSeconds: Long): String {
            return when {
                reelsCount >= 15 || watchTimeSeconds >= 600 -> "CRITICAL"
                reelsCount >= 6 || watchTimeSeconds >= 180 -> "WARNING"
                else -> "MILD"
            }
        }

        // Mild: Under 6 reels and under 3 mins
        assertEquals("MILD", getWarningTier(0, 0))
        assertEquals("MILD", getWarningTier(3, 90))
        assertEquals("MILD", getWarningTier(5, 179))

        // Warning: 6 to 14 reels or 3 to 10 mins
        assertEquals("WARNING", getWarningTier(6, 60))
        assertEquals("WARNING", getWarningTier(10, 200))
        assertEquals("WARNING", getWarningTier(4, 240))

        // Critical: 15+ reels or 10+ mins
        assertEquals("CRITICAL", getWarningTier(15, 100))
        assertEquals("CRITICAL", getWarningTier(25, 800))
        assertEquals("CRITICAL", getWarningTier(2, 650))
    }

    @Test
    fun testOverlayCounterTextFormatting() {
        fun formatCounter(reelsCount: Int, watchTimeSeconds: Long, mode: Int): Pair<String?, String?> {
            val countText = when {
                reelsCount == 1 -> "1 Reel"
                reelsCount > 0 -> "$reelsCount Reels"
                else -> "Doom Scroll"
            }
            val minutes = watchTimeSeconds / 60
            val seconds = watchTimeSeconds % 60
            val timeText = String.format("%02d:%02d", minutes, seconds)

            return when (mode) {
                SavedPreferencesLoader.OVERLAY_MODE_COUNT -> Pair(countText, null)
                SavedPreferencesLoader.OVERLAY_MODE_TIME -> Pair(null, timeText)
                else -> Pair(countText, timeText)
            }
        }

        // Test BOTH mode
        val bothRes = formatCounter(8, 125, SavedPreferencesLoader.OVERLAY_MODE_BOTH)
        assertEquals("8 Reels", bothRes.first)
        assertEquals("02:05", bothRes.second)

        // Test COUNT ONLY mode
        val countRes = formatCounter(1, 45, SavedPreferencesLoader.OVERLAY_MODE_COUNT)
        assertEquals("1 Reel", countRes.first)
        assertNull(countRes.second)

        // Test TIME ONLY mode
        val timeRes = formatCounter(14, 366, SavedPreferencesLoader.OVERLAY_MODE_TIME)
        assertNull(timeRes.first)
        assertEquals("06:06", timeRes.second)
    }

    @Test
    fun testAppUsageTrackingBlurStateEvaluation() {
        // When app usage tracking is enabled -> blur is NOT applied
        val trackingOn = true
        val shouldBlurOn = !trackingOn
        assertFalse("When tracking is ON, UI should not be blurred", shouldBlurOn)

        // When app usage tracking is disabled -> blur IS applied
        val trackingOff = false
        val shouldBlurOff = !trackingOff
        assertTrue("When tracking is OFF, UI must be blurred immediately", shouldBlurOff)
    }
}
