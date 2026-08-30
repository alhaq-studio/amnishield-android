package com.alhaq.amnishield.ui.fragments.installation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.databinding.FragmentAccessibilityGuideBinding
import com.alhaq.amnishield.ui.activity.MainActivity

class AccessibilityGuide : Fragment() {
    companion object {
        const val FRAGMENT_ID = "accessibility_guide_fragment"
    }

    private var _binding: FragmentAccessibilityGuideBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ true
        )
        returnTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ false
        )
        exitTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ true
        )
        reenterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ false
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessibilityGuideBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("CommitPrefEdits")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOpenSettings.setOnClickListener {
            com.alhaq.amnishield.utils.AccessibilityDisclosureDialog.show(
                context = requireContext(),
                onAgree = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback
                    }
                },
                onDecline = {
                    // User declined permission consent
                }
            )
        }

        binding.btnNext.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    com.alhaq.amnishield.R.id.fragment_holder,
                    PermissionsFragment()
                )
                .addToBackStack(null)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}