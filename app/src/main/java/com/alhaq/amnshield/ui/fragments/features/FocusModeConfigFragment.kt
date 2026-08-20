package com.alhaq.amnshield.ui.fragments.features

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.activity.SelectAppsActivity
import com.alhaq.amnshield.ui.dialogs.StartFocusMode
import com.alhaq.amnshield.ui.screens.config.FocusModeConfigScreen
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.ThemeUtils

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
                Log.i(TAG, "Focus mode selected apps updated: ${it.size} apps")
                val intent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
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
                AmnShieldTheme(appTheme = activeTheme) {
                    FocusModeConfigScreen(
                        isServiceEnabled = isAccessibilityServiceEnabled(AmnShieldAccessibilityService::class.java),
                        onEnableServiceClick = {
                            showAccessibilityInfoDialog(
                                "AmnShield Accessibility Service",
                                AmnShieldAccessibilityService::class.java
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
                        onStartFocusModeClick = {
                            StartFocusMode(savedPreferencesLoader) {
                                // Focus mode session started
                                Log.i(TAG, "Quick Focus session started")
                            }.show(childFragmentManager, "start_focus_mode")
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
