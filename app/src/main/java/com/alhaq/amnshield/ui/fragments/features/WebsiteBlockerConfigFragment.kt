package com.alhaq.amnshield.ui.fragments.features

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.dialogs.TweakAppBlockerWarning
import com.alhaq.amnshield.ui.screens.config.WebsiteBlockerConfigScreen
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.ThemeUtils

private const val TAG = "WebsiteBlockerConfig"

/**
 * Modern Jetpack Compose bridge fragment for Website Blocker Configuration.
 */
class WebsiteBlockerConfigFragment : BaseFeatureFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating WebsiteBlockerConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmnShieldTheme(appTheme = activeTheme) {
                    WebsiteBlockerConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmnShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmniShield Accessibility Service",
                                AmnShieldAccessibilityService::class.java
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
                                "tweak_website_blocker_warning"
                            )
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "website_blocker"
    }
}
