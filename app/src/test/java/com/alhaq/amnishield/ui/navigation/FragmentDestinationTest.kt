package com.alhaq.amnishield.ui.navigation

import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.ui.fragments.DiagnosticsFragment
import com.alhaq.amnishield.ui.fragments.ManageLaunchLimitsFragment
import com.alhaq.amnishield.ui.fragments.ProfileFragment
import com.alhaq.amnishield.ui.fragments.anti_uninstall.ChooseModeFragment
import com.alhaq.amnishield.ui.fragments.features.AppBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.FocusModeConfigFragment
import com.alhaq.amnishield.ui.fragments.features.KeywordBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.OurJourneyFragment
import com.alhaq.amnishield.ui.fragments.features.PremiumFeaturesFragment
import com.alhaq.amnishield.ui.fragments.features.ReelBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.UsageTrackerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.WebsiteBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.installation.AccessibilityGuide
import com.alhaq.amnishield.ui.fragments.installation.WelcomeFragment
import com.alhaq.amnishield.ui.fragments.usage.AllAppsUsageFragment
import com.alhaq.amnishield.ui.fragments.usage.ReelsMetricsFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FragmentDestinationTest {

    @Test
    fun `fromKey resolves modern destination keys and legacy aliases`() {
        assertEquals(FragmentDestination.FocusMode, FragmentDestination.fromKey("focus_mode"))
        assertEquals(FragmentDestination.FocusModeConfig, FragmentDestination.fromKey("focus_mode_config"))
        assertEquals(FragmentDestination.AppBlockerConfig, FragmentDestination.fromKey("app_blocker"))
        assertEquals(FragmentDestination.ReelBlockerConfig, FragmentDestination.fromKey("reel_blocker"))
        assertEquals(FragmentDestination.ReelsMetrics, FragmentDestination.fromKey("reels_metrics"))
        assertEquals(FragmentDestination.WebsiteBlockerConfig, FragmentDestination.fromKey("website_blocker"))
        assertEquals(FragmentDestination.WebsiteBlockerConfig, FragmentDestination.fromKey("social_media_blocker"))
        assertEquals(FragmentDestination.UsageTrackerConfig, FragmentDestination.fromKey("usage_tracker"))
        assertEquals(FragmentDestination.KeywordBlockerConfig, FragmentDestination.fromKey("keyword_blocker"))
        assertEquals(FragmentDestination.AntiUninstall, FragmentDestination.fromKey("anti_uninstall"))
        assertEquals(FragmentDestination.SetupPasswordMode, FragmentDestination.fromKey("setup_password_mode"))
        assertEquals(FragmentDestination.SetupTimedMode, FragmentDestination.fromKey("setup_timed_mode"))
        assertEquals(FragmentDestination.PremiumFeatures, FragmentDestination.fromKey("premium_features"))
        assertEquals(FragmentDestination.PremiumFeatures, FragmentDestination.fromKey("additional_features"))
        assertEquals(FragmentDestination.Roadmap, FragmentDestination.fromKey("roadmap"))
        assertEquals(FragmentDestination.OurJourney, FragmentDestination.fromKey("journey"))
        assertEquals(FragmentDestination.OurJourney, FragmentDestination.fromKey("our_journey"))
        assertEquals(FragmentDestination.Diagnostics, FragmentDestination.fromKey("diagnostics"))
        assertEquals(FragmentDestination.Diagnostics, FragmentDestination.fromKey("system_logs"))
        assertEquals(FragmentDestination.Diagnostics, FragmentDestination.fromKey("crash_logs"))
        assertEquals(FragmentDestination.AllAppsUsage, FragmentDestination.fromKey("all_apps_usage"))
    }

    @Test
    fun `fromKey resolves all legacy FRAGMENT_ID constants`() {
        assertEquals(FragmentDestination.FocusModeConfig, FragmentDestination.fromKey(FocusModeConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.AntiUninstall, FragmentDestination.fromKey(ChooseModeFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.AllAppsUsage, FragmentDestination.fromKey(AllAppsUsageFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.ReelsMetrics, FragmentDestination.fromKey(ReelsMetricsFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.AppBlockerConfig, FragmentDestination.fromKey(AppBlockerConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.ReelBlockerConfig, FragmentDestination.fromKey(ReelBlockerConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.WebsiteBlockerConfig, FragmentDestination.fromKey(WebsiteBlockerConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.UsageTrackerConfig, FragmentDestination.fromKey(UsageTrackerConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.KeywordBlockerConfig, FragmentDestination.fromKey(KeywordBlockerConfigFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.ManageLaunchLimits, FragmentDestination.fromKey(ManageLaunchLimitsFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.Welcome, FragmentDestination.fromKey(WelcomeFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.AccessibilityGuideDest, FragmentDestination.fromKey(AccessibilityGuide.FRAGMENT_ID))
        assertEquals(FragmentDestination.Profile, FragmentDestination.fromKey(ProfileFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.Diagnostics, FragmentDestination.fromKey(DiagnosticsFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.PremiumFeatures, FragmentDestination.fromKey(PremiumFeaturesFragment.FRAGMENT_ID))
        assertEquals(FragmentDestination.OurJourney, FragmentDestination.fromKey(OurJourneyFragment.FRAGMENT_ID))
    }

    @Test
    fun `fromKey preserves filterType distinction between AppBlockerSchedules and BlocksManager`() {
        val defaultAppSchedules = FragmentDestination.fromKey("app_blocker_schedules")
        assertTrue(defaultAppSchedules is FragmentDestination.AppBlockerSchedules)
        assertEquals("App Blocker", (defaultAppSchedules as FragmentDestination.AppBlockerSchedules).filterType)

        val customAppSchedules = FragmentDestination.fromKey("app_blocker_schedules", "Focus Mode")
        assertEquals("Focus Mode", (customAppSchedules as FragmentDestination.AppBlockerSchedules).filterType)

        val unfilteredBlocksManager = FragmentDestination.fromKey(BlocksManagerFragment.FRAGMENT_ID)
        assertTrue(unfilteredBlocksManager is FragmentDestination.BlocksManager)
        assertNull((unfilteredBlocksManager as FragmentDestination.BlocksManager).filterType)

        val filteredBlocksManager = FragmentDestination.fromKey(BlocksManagerFragment.FRAGMENT_ID, "Website Blocker")
        assertEquals("Website Blocker", (filteredBlocksManager as FragmentDestination.BlocksManager).filterType)
    }

    @Test
    fun `fromKey returns null for unknown or deprecated view_blocker route`() {
        assertNull(FragmentDestination.fromKey(null))
        assertNull(FragmentDestination.fromKey(""))
        assertNull(FragmentDestination.fromKey("   "))
        assertNull(FragmentDestination.fromKey("view_blocker"))
        assertNull(FragmentDestination.fromKey("non_existent_route"))
    }

    @Test
    fun `requiresBypassPin accurately classifies protected blocker configs vs non-protected screens`() {
        assertTrue(FragmentDestination.FocusMode.requiresBypassPin)
        assertTrue(FragmentDestination.FocusModeConfig.requiresBypassPin)
        assertTrue(FragmentDestination.AppBlockerConfig.requiresBypassPin)
        assertTrue(FragmentDestination.AppBlockerSchedules().requiresBypassPin)
        assertTrue(FragmentDestination.BlocksManager().requiresBypassPin)
        assertTrue(FragmentDestination.ReelBlockerConfig.requiresBypassPin)
        assertTrue(FragmentDestination.WebsiteBlockerConfig.requiresBypassPin)
        assertTrue(FragmentDestination.UsageTrackerConfig.requiresBypassPin)
        assertTrue(FragmentDestination.KeywordBlockerConfig.requiresBypassPin)
        assertTrue(FragmentDestination.AntiUninstall.requiresBypassPin)
        assertTrue(FragmentDestination.SetupPasswordMode.requiresBypassPin)
        assertTrue(FragmentDestination.SetupTimedMode.requiresBypassPin)
        assertTrue(FragmentDestination.ManageLaunchLimits.requiresBypassPin)

        assertFalse(FragmentDestination.ReelsMetrics.requiresBypassPin)
        assertFalse(FragmentDestination.PremiumFeatures.requiresBypassPin)
        assertFalse(FragmentDestination.Roadmap.requiresBypassPin)
        assertFalse(FragmentDestination.OurJourney.requiresBypassPin)
        assertFalse(FragmentDestination.Diagnostics.requiresBypassPin)
        assertFalse(FragmentDestination.AllAppsUsage.requiresBypassPin)
        assertFalse(FragmentDestination.Welcome.requiresBypassPin)
        assertFalse(FragmentDestination.AccessibilityGuideDest.requiresBypassPin)
        assertFalse(FragmentDestination.Profile.requiresBypassPin)
    }
}
