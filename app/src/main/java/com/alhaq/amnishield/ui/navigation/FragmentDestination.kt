package com.alhaq.amnishield.ui.navigation

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.ui.fragments.DiagnosticsFragment
import com.alhaq.amnishield.ui.fragments.FocusFragment
import com.alhaq.amnishield.ui.fragments.ManageLaunchLimitsFragment
import com.alhaq.amnishield.ui.fragments.ProfileFragment
import com.alhaq.amnishield.ui.fragments.anti_uninstall.ChooseModeFragment
import com.alhaq.amnishield.ui.fragments.anti_uninstall.SetupPasswordModeFragment
import com.alhaq.amnishield.ui.fragments.anti_uninstall.SetupTimedModeFragment
import com.alhaq.amnishield.ui.fragments.features.AppBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.FocusModeConfigFragment
import com.alhaq.amnishield.ui.fragments.features.KeywordBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.OurJourneyFragment
import com.alhaq.amnishield.ui.fragments.features.PremiumFeaturesFragment
import com.alhaq.amnishield.ui.fragments.features.ReelBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.RoadmapFragment
import com.alhaq.amnishield.ui.fragments.features.UsageTrackerConfigFragment
import com.alhaq.amnishield.ui.fragments.features.WebsiteBlockerConfigFragment
import com.alhaq.amnishield.ui.fragments.installation.AccessibilityGuide
import com.alhaq.amnishield.ui.fragments.installation.WelcomeFragment
import com.alhaq.amnishield.ui.fragments.usage.AllAppsUsageFragment
import com.alhaq.amnishield.ui.fragments.usage.ReelsMetricsFragment

/**
 * Type-safe sealed navigation contract for sub-screens hosted by [com.alhaq.amnishield.ui.activity.FragmentActivity].
 * Preserves full backward compatibility with legacy `feature_type` and `fragment` string extras
 * used by home screen widget PendingIntents and external shortcuts.
 */
sealed class FragmentDestination(val key: String) {
    abstract fun createFragment(): Fragment
    open val requiresBypassPin: Boolean = false

    data object FocusMode : FragmentDestination("focus_mode") {
        override fun createFragment(): Fragment = FocusFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object FocusModeConfig : FragmentDestination("focus_mode_config") {
        override fun createFragment(): Fragment = FocusModeConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object AppBlockerConfig : FragmentDestination("app_blocker") {
        override fun createFragment(): Fragment = AppBlockerConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data class AppBlockerSchedules(val filterType: String = "App Blocker") : FragmentDestination("app_blocker_schedules") {
        override fun createFragment(): Fragment = BlocksManagerFragment().apply {
            arguments = Bundle().apply {
                putString("filter_type", filterType)
            }
        }
        override val requiresBypassPin: Boolean = true
    }

    data class BlocksManager(val filterType: String? = null) : FragmentDestination(BlocksManagerFragment.FRAGMENT_ID) {
        override fun createFragment(): Fragment = BlocksManagerFragment().apply {
            if (filterType != null) {
                arguments = Bundle().apply {
                    putString("filter_type", filterType)
                }
            }
        }
        override val requiresBypassPin: Boolean = true
    }

    data object ReelBlockerConfig : FragmentDestination("reel_blocker") {
        override fun createFragment(): Fragment = ReelBlockerConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object ReelsMetrics : FragmentDestination("reels_metrics") {
        override fun createFragment(): Fragment = ReelsMetricsFragment()
    }

    data object WebsiteBlockerConfig : FragmentDestination("website_blocker") {
        override fun createFragment(): Fragment = WebsiteBlockerConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object UsageTrackerConfig : FragmentDestination("usage_tracker") {
        override fun createFragment(): Fragment = UsageTrackerConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object KeywordBlockerConfig : FragmentDestination("keyword_blocker") {
        override fun createFragment(): Fragment = KeywordBlockerConfigFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object AntiUninstall : FragmentDestination("anti_uninstall") {
        override fun createFragment(): Fragment = ChooseModeFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object SetupPasswordMode : FragmentDestination("setup_password_mode") {
        override fun createFragment(): Fragment = SetupPasswordModeFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object SetupTimedMode : FragmentDestination("setup_timed_mode") {
        override fun createFragment(): Fragment = SetupTimedModeFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object PremiumFeatures : FragmentDestination("premium_features") {
        override fun createFragment(): Fragment = PremiumFeaturesFragment()
    }

    data object Roadmap : FragmentDestination("roadmap") {
        override fun createFragment(): Fragment = RoadmapFragment()
    }

    data object OurJourney : FragmentDestination("journey") {
        override fun createFragment(): Fragment = OurJourneyFragment()
    }

    data object Diagnostics : FragmentDestination("diagnostics") {
        override fun createFragment(): Fragment = DiagnosticsFragment()
    }

    data object AllAppsUsage : FragmentDestination(AllAppsUsageFragment.FRAGMENT_ID) {
        override fun createFragment(): Fragment = AllAppsUsageFragment()
    }

    data object ManageLaunchLimits : FragmentDestination(ManageLaunchLimitsFragment.FRAGMENT_ID) {
        override fun createFragment(): Fragment = ManageLaunchLimitsFragment()
        override val requiresBypassPin: Boolean = true
    }

    data object Welcome : FragmentDestination(WelcomeFragment.FRAGMENT_ID) {
        override fun createFragment(): Fragment = WelcomeFragment()
    }

    data object AccessibilityGuideDest : FragmentDestination(AccessibilityGuide.FRAGMENT_ID) {
        override fun createFragment(): Fragment = AccessibilityGuide()
    }

    data object Profile : FragmentDestination(ProfileFragment.FRAGMENT_ID) {
        override fun createFragment(): Fragment = ProfileFragment()
    }

    companion object {
        const val EXTRA_DESTINATION_KEY = "extra_destination_key"
        const val EXTRA_FEATURE_TYPE = "feature_type"
        const val EXTRA_FRAGMENT = "fragment"

        fun fromKey(key: String?, filterType: String? = null): FragmentDestination? {
            if (key.isNullOrBlank()) return null
            return when (key) {
                "focus_mode" -> FocusMode
                "focus_mode_config", FocusModeConfigFragment.FRAGMENT_ID -> FocusModeConfig
                "app_blocker", AppBlockerConfigFragment.FRAGMENT_ID -> AppBlockerConfig
                "app_blocker_schedules" -> AppBlockerSchedules(filterType ?: "App Blocker")
                BlocksManagerFragment.FRAGMENT_ID -> BlocksManager(filterType)
                "reel_blocker", ReelBlockerConfigFragment.FRAGMENT_ID -> ReelBlockerConfig
                "reels_metrics", ReelsMetricsFragment.FRAGMENT_ID -> ReelsMetrics
                "website_blocker", "social_media_blocker", WebsiteBlockerConfigFragment.FRAGMENT_ID -> WebsiteBlockerConfig
                "usage_tracker", UsageTrackerConfigFragment.FRAGMENT_ID -> UsageTrackerConfig
                "keyword_blocker", KeywordBlockerConfigFragment.FRAGMENT_ID -> KeywordBlockerConfig
                "anti_uninstall", ChooseModeFragment.FRAGMENT_ID -> AntiUninstall
                "setup_password_mode" -> SetupPasswordMode
                "setup_timed_mode" -> SetupTimedMode
                "additional_features", "premium_features", PremiumFeaturesFragment.FRAGMENT_ID -> PremiumFeatures
                "roadmap" -> Roadmap
                "journey", "our_journey", OurJourneyFragment.FRAGMENT_ID -> OurJourney
                "diagnostics", "system_logs", "crash_logs", DiagnosticsFragment.FRAGMENT_ID -> Diagnostics
                "all_apps_usage", AllAppsUsageFragment.FRAGMENT_ID -> AllAppsUsage
                ManageLaunchLimitsFragment.FRAGMENT_ID -> ManageLaunchLimits
                "welcome", WelcomeFragment.FRAGMENT_ID -> Welcome
                "accessibility_guide", AccessibilityGuide.FRAGMENT_ID -> AccessibilityGuideDest
                ProfileFragment.FRAGMENT_ID -> Profile
                else -> null
            }
        }

        fun fromIntent(intent: Intent?): FragmentDestination? {
            if (intent == null) return null
            val filterType = intent.getStringExtra("filter_type")

            // 1. Check type-safe destination key first
            fromKey(intent.getStringExtra(EXTRA_DESTINATION_KEY), filterType)?.let { return it }

            // 2. Fallback to feature_type extra
            fromKey(intent.getStringExtra(EXTRA_FEATURE_TYPE), filterType)?.let { return it }

            // 3. Fallback to legacy fragment ID extra
            return fromKey(intent.getStringExtra(EXTRA_FRAGMENT), filterType)
        }
    }
}
