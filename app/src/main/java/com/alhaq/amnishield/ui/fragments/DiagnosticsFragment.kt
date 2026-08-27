/**
 * ============================================================================
 * AmniShield UI - DiagnosticsFragment
 * ============================================================================
 * Responsibility:
 * Android Fragment wrapper hosting the Compose [DiagnosticsScreen].
 * ============================================================================
 */
package com.alhaq.amnishield.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.ui.screens.diagnostics.DiagnosticsScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.utils.ThemeUtils

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
                AmniShieldTheme(appTheme = activeTheme) {
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
