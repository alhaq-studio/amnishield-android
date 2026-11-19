package com.alhaq.amnishield.ui.fragments.installation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.ui.screens.SetupModeScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ThemeUtils
import com.google.android.material.transition.MaterialSharedAxis

/**
 * Onboarding step presenting the choice between Offline Standalone mode
 * and Multi-Device Cloud Synchronization. Wraps SetupModeScreen in ComposeView
 * with zero XML layout overhead.
 */
class SetupModeOnboardingFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "setup_mode_onboarding_fragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmniShieldTheme(appTheme = activeTheme) {
                    SetupModeScreen(
                        onContinueOffline = {
                            val loader = SavedPreferencesLoader(requireContext())
                            loader.setStandaloneMode()
                            navigateToSupportAlHaq()
                        },
                        onContinueCloudSync = {
                            val loader = SavedPreferencesLoader(requireContext())
                            loader.setPersonalCloudSyncEnabled(true)
                            runCatching {
                                com.alhaq.amnishield.data.sync.SyncWorker.schedule(requireContext())
                            }
                            navigateToSupportAlHaq()
                        },
                        onBack = {
                            parentFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }

    private fun navigateToSupportAlHaq() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_holder,
                SupportAlHaqFragment()
            )
            .addToBackStack(null)
            .commit()
    }
}
