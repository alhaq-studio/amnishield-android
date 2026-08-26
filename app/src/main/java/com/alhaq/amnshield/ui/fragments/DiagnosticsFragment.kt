/**
 * ============================================================================
 * AmniShield UI - DiagnosticsFragment
 * ============================================================================
 * Responsibility:
 * Android Fragment wrapper hosting the Compose [DiagnosticsScreen].
 * ============================================================================
 */
package com.alhaq.amnshield.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.alhaq.amnshield.ui.screens.diagnostics.DiagnosticsScreen
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.ThemeUtils

class DiagnosticsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "DiagnosticsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = ThemeUtils.resolveAppTheme(requireContext())
                AmnShieldTheme(appTheme = activeTheme) {
                    DiagnosticsScreen(
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                requireActivity().finish()
                            }
                        }
                    )
                }
            }
        }
    }
}
