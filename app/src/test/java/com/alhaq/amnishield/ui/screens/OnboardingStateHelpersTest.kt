package com.alhaq.amnishield.ui.screens

import com.alhaq.amnishield.data.blockers.AlwaysOnShieldsConfig
import com.alhaq.amnishield.data.blockers.ReelsGuardConfig
import com.alhaq.amnishield.data.blockers.ShieldLockConfig
import com.alhaq.amnishield.data.blockers.ShieldLockMode
import com.alhaq.amnishield.data.blockers.WebsiteGuardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateHelpersTest {

    @Test
    fun `applyToConfig activates reels and website packs while preserving existing custom domains`() {
        val baseline = AlwaysOnShieldsConfig(
            reels = ReelsGuardConfig(
                enabled = false,
                lockConfig = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 45)
            ),
            website = WebsiteGuardConfig(
                enabled = false,
                customDomains = listOf("custom-example.org")
            )
        )

        val quick = OnboardingQuickShieldState(
            blockInstagramReels = true,
            blockYoutubeShorts = true,
            blockTikTok = false,
            blockBrowserReels = false,
            blockAdultSites = true,
            blockGamblingSites = true
        )

        val updated = quick.applyToConfig(baseline)
        assertTrue(updated.reels.enabled)
        assertTrue(updated.reels.blockInstagramReels)
        assertTrue(updated.reels.blockYoutubeShorts)
        assertFalse(updated.reels.blockTikTok)
        assertEquals(45, updated.reels.lockConfig.delaySeconds)

        assertTrue(updated.website.enabled)
        assertTrue(OnboardingQuickShieldState.WEB_ADULT_PACK_ID in updated.website.enabledPackIds)
        assertTrue(OnboardingQuickShieldState.WEB_GAMBLING_PACK_ID in updated.website.enabledPackIds)
        assertEquals(listOf("custom-example.org"), updated.website.customDomains)
    }

    @Test
    fun `fromConfig restores quick shield state accurately and preserves website when custom domain remains`() {
        val config = AlwaysOnShieldsConfig(
            reels = ReelsGuardConfig(
                enabled = true,
                blockInstagramReels = true,
                blockYoutubeShorts = false,
                blockTikTok = true,
                blockInBrowserShorts = false
            ),
            website = WebsiteGuardConfig(
                enabled = true,
                enabledPackIds = setOf(OnboardingQuickShieldState.WEB_ADULT_PACK_ID),
                customDomains = listOf("site.example")
            )
        )

        val parsed = OnboardingQuickShieldState.fromConfig(config)
        assertTrue(parsed.blockInstagramReels)
        assertFalse(parsed.blockYoutubeShorts)
        assertTrue(parsed.blockTikTok)
        assertTrue(parsed.blockAdultSites)
        assertFalse(parsed.blockGamblingSites)
        assertEquals(3, parsed.activeShieldCount())

        val clearedStarterPacks = parsed.copy(
            blockInstagramReels = false,
            blockTikTok = false,
            blockAdultSites = false
        ).applyToConfig(config)

        assertFalse(clearedStarterPacks.reels.enabled)
        assertTrue(clearedStarterPacks.website.enabled)
        assertTrue(clearedStarterPacks.website.enabledPackIds.isEmpty())
    }

    @Test
    fun `pin draft validates 4 digits and detects match or mismatch`() {
        assertFalse(OnboardingPinDraft(pin = "123", confirmPin = "123").isMatching())
        assertFalse(OnboardingPinDraft(pin = "12ab", confirmPin = "12ab").isMatching())

        val mismatch = OnboardingPinDraft(pin = "1234", confirmPin = "1235")
        assertFalse(mismatch.isMatching())
        assertTrue(mismatch.hasMismatchError())

        val matched = OnboardingPinDraft(pin = "4829", confirmPin = "4829")
        assertTrue(matched.isPinValidFormat())
        assertTrue(matched.isMatching())
        assertFalse(matched.hasMismatchError())
    }

    @Test
    fun `daily goal helpers format hours and moods cleanly`() {
        assertEquals("1h", formatDailyGoalDuration(60))
        assertEquals("2h 15m", formatDailyGoalDuration(135))
        assertEquals("45m", formatDailyGoalDuration(45))

        assertEquals("Deep Focus Mode", resolveDailyGoalMood(90))
        assertEquals("Balanced Digital Life", resolveDailyGoalMood(120))
        assertEquals("Moderate Routine", resolveDailyGoalMood(240))
        assertEquals("Flexible Schedule", resolveDailyGoalMood(360))
    }
}
