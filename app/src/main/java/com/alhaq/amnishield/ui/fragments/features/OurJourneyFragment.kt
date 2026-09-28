package com.alhaq.amnishield.ui.fragments.features

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.ui.screens.JourneyAndFoundersScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

/**
 * Fragment displaying the AmniShield Journey, Core Pillars, and the Founding Supporters Wall.
 */
class OurJourneyFragment : Fragment() {

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
                    JourneyAndFoundersScreen(
                        onNavigateBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                activity?.finish()
                            }
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val FRAGMENT_ID = "journey"
    }
}
