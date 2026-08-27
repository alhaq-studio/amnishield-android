package com.alhaq.amnishield.ui.fragments.features

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.activity.SelectAppsActivity
import com.alhaq.amnishield.ui.dialogs.*
import com.alhaq.amnishield.ui.screens.config.*
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

private const val TAG = "FeatureConfigFragments"

/**
 * Modern Jetpack Compose bridge fragment for App Blocker Configuration.
 */
class AppBlockerConfigFragment : BaseFeatureFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating AppBlockerConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    AppBlockerConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmniShield Accessibility Service",
                                AmniShieldAccessibilityService::class.java
                            )
                        },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                activity?.finish()
                            }
                        },
                        onConfigureWarning = {
                            TweakAppBlockerWarning(savedPreferencesLoader).show(
                                childFragmentManager,
                                "tweak_app_blocker_warning"
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "app_blocker_config"
    }
}

/**
 * Modern Jetpack Compose bridge fragment for Short Video / Reel Blocker Configuration.
 */
class ReelBlockerConfigFragment : BaseFeatureFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating ReelBlockerConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    ReelBlockerConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmniShield Accessibility Service",
                                AmniShieldAccessibilityService::class.java
                            )
                        },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                activity?.finish()
                            }
                        },
                        onConfigureWarning = {
                            TweakViewBlockerWarning(savedPreferencesLoader).show(
                                childFragmentManager,
                                "tweak_reel_blocker_warning"
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "reel_blocker_config"
    }
}

/**
 * Modern Jetpack Compose bridge fragment for Usage Tracker Configuration.
 */
class UsageTrackerConfigFragment : BaseFeatureFragment() {

    private val selectOverlayAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                savedPreferencesLoader.setReelsOverlayApps(it.toSet())
                val prefs = requireContext().getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("overlay_apps", it.toSet()).apply()
                Toast.makeText(requireContext(), "Doom-scrolling overlay apps updated (${it.size} apps)", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Usage Tracker overlay apps updated: ${it.size} apps")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating UsageTrackerConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    UsageTrackerConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmniShield Accessibility Service",
                                AmniShieldAccessibilityService::class.java
                            )
                        },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                activity?.finish()
                            }
                        },
                        onSelectOverlayAppsClick = {
                            val overlayApps = savedPreferencesLoader.getReelsOverlayApps()
                            val intent = Intent(requireContext(), SelectAppsActivity::class.java).apply {
                                putStringArrayListExtra("PRE_SELECTED_APPS", ArrayList(overlayApps))
                            }
                            selectOverlayAppsLauncher.launch(intent, activityOptions)
                        },
                        onConfigureTweaksClick = {
                            TweakUsageTracker(savedPreferencesLoader).show(
                                childFragmentManager,
                                "tweak_usage_tracker"
                            )
                        },
                        onViewReelsMetricsClick = {
                            val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                                putExtra("feature_type", "reels_metrics")
                            }
                            startActivity(intent, activityOptions.toBundle())
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "usage_tracker_config"
    }
}

/**
 * Modern Jetpack Compose bridge fragment for Keyword Blocker Configuration.
 */
class KeywordBlockerConfigFragment : BaseFeatureFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating KeywordBlockerConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    KeywordBlockerConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmniShield Accessibility Service",
                                AmniShieldAccessibilityService::class.java
                            )
                        },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                activity?.finish()
                            }
                        },
                        onConfigureWarning = {
                            TweakKeywordBlockerWarning(savedPreferencesLoader).show(
                                childFragmentManager,
                                "tweak_keyword_warning"
                            )
                        },
                        onConfigureSensitivity = {
                            TweakKeywordBlocker(savedPreferencesLoader).show(
                                childFragmentManager,
                                "tweak_keyword_blocker"
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "keyword_blocker_config"
    }
}
