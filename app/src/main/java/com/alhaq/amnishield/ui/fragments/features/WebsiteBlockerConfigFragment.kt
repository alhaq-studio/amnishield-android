package com.alhaq.amnishield.ui.fragments.features

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.dialogs.TweakAppBlockerWarning
import com.alhaq.amnishield.ui.screens.config.WebsiteBlockerConfigScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

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
                AmniShieldTheme(appTheme = activeTheme) {
                    WebsiteBlockerConfigScreen(
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
