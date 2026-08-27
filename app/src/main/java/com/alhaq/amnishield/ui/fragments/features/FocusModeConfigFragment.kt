package com.alhaq.amnishield.ui.fragments.features

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.SelectAppsActivity
import com.alhaq.amnishield.ui.screens.config.FocusModeConfigScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

private const val TAG = "FocusModeConfigFragment"

/**
 * Modern Jetpack Compose bridge fragment for Focus Mode Configuration.
 */
class FocusModeConfigFragment : BaseFeatureFragment() {

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                savedPreferencesLoader.saveFocusModeSelectedApps(it)
                Log.i(TAG, "Focus mode target apps updated: ${it.size} apps")
                val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                    setPackage(requireContext().packageName)
                }
                requireContext().sendBroadcast(intent)
            }
        }
    }

    private val selectAlwaysWhitelistedLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                savedPreferencesLoader.saveAlwaysWhitelistedApps(it)
                Log.i(TAG, "Always-whitelisted emergency apps updated: ${it.size} apps")
                val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                    setPackage(requireContext().packageName)
                }
                requireContext().sendBroadcast(intent)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "Creating FocusModeConfigFragment ComposeView")
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    FocusModeConfigScreen(
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
                        onSelectAppsClick = {
                            val intent = Intent(requireContext(), SelectAppsActivity::class.java).apply {
                                putStringArrayListExtra(
                                    "PRE_SELECTED_APPS",
                                    ArrayList(savedPreferencesLoader.getFocusModeSelectedApps())
                                )
                            }
                            selectAppsLauncher.launch(intent, activityOptions)
                        },
                        onSelectAlwaysWhitelistedClick = {
                            val intent = Intent(requireContext(), SelectAppsActivity::class.java).apply {
                                putStringArrayListExtra(
                                    "PRE_SELECTED_APPS",
                                    ArrayList(savedPreferencesLoader.getAlwaysWhitelistedApps())
                                )
                            }
                            selectAlwaysWhitelistedLauncher.launch(intent, activityOptions)
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "focus_mode_config"
    }
}
