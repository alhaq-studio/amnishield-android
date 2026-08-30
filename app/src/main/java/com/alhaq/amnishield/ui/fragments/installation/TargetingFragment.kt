package com.alhaq.amnishield.ui.fragments.installation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.FragmentTargetingBinding
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class TargetingFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "targeting_fragment"
    }

    private var _binding: FragmentTargetingBinding? = null
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
        _binding = FragmentTargetingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore any saved preference states or default to true
        val prefs = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        binding.cbWeb.isChecked = prefs.getBoolean("target_web_enabled", true)
        binding.cbReels.isChecked = prefs.getBoolean("target_reels_enabled", true)
        binding.cbFocus.isChecked = prefs.getBoolean("target_focus_enabled", true)

        updateCardState(binding.cardWeb, binding.cbWeb.isChecked)
        updateCardState(binding.cardReels, binding.cbReels.isChecked)
        updateCardState(binding.cardFocus, binding.cbFocus.isChecked)

        // Make card clicking toggle checkboxes
        binding.cardWeb.setOnClickListener { binding.cbWeb.isChecked = !binding.cbWeb.isChecked }
        binding.cardReels.setOnClickListener { binding.cbReels.isChecked = !binding.cbReels.isChecked }
        binding.cardFocus.setOnClickListener { binding.cbFocus.isChecked = !binding.cbFocus.isChecked }

        binding.cbWeb.setOnCheckedChangeListener { _, isChecked ->
            updateCardState(binding.cardWeb, isChecked)
            persistCurrentState()
        }
        binding.cbReels.setOnCheckedChangeListener { _, isChecked ->
            updateCardState(binding.cardReels, isChecked)
            persistCurrentState()
        }
        binding.cbFocus.setOnCheckedChangeListener { _, isChecked ->
            updateCardState(binding.cardFocus, isChecked)
            persistCurrentState()
        }

        binding.chipSelectAll.setOnClickListener {
            val allChecked = binding.cbWeb.isChecked && binding.cbReels.isChecked && binding.cbFocus.isChecked
            val target = !allChecked
            binding.cbWeb.isChecked = target
            binding.cbReels.isChecked = target
            binding.cbFocus.isChecked = target
        }

        binding.btnContinue.setOnClickListener { saveTargetingAndProceed() }
        binding.btnSkip.setOnClickListener { skipTargetingAndProceed() }
    }

    private fun updateCardState(card: com.google.android.material.card.MaterialCardView, isChecked: Boolean) {
        val primary = com.google.android.material.color.MaterialColors.getColor(
            card,
            androidx.appcompat.R.attr.colorPrimary,
            android.graphics.Color.BLUE
        )
        val outline = com.google.android.material.color.MaterialColors.getColor(
            card,
            com.google.android.material.R.attr.colorOutlineVariant,
            android.graphics.Color.GRAY
        )
        card.strokeColor = if (isChecked) primary else outline
        card.strokeWidth = if (isChecked) 2 else 1
    }

    private fun persistCurrentState() {
        val context = context ?: return
        val isWeb = _binding?.cbWeb?.isChecked ?: true
        val isReels = _binding?.cbReels?.isChecked ?: true
        val isFocus = _binding?.cbFocus?.isChecked ?: true

        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("target_web_enabled", isWeb)
            putBoolean("target_reels_enabled", isReels)
            putBoolean("target_focus_enabled", isFocus)
            apply()
        }

        val loader = SavedPreferencesLoader(context)
        loader.setReelBlockerEnabled(isReels)
        loader.setReelBlockerTiktokEnabled(isReels)
        loader.setReelBlockerYoutubeEnabled(isReels)
        loader.setReelBlockerInstagramEnabled(isReels)
        loader.setWebsiteBlockerEnabled(isWeb)
        loader.setKeywordBlockerFeatureEnabled(isWeb)
        loader.setAppBlockerFeatureEnabled(isFocus)
    }

    private fun saveTargetingAndProceed() {
        persistCurrentState()
        proceedToAccessibilityGuide()
    }

    private fun skipTargetingAndProceed() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("target_gaze_enabled", false)
            putBoolean("target_web_enabled", false)
            putBoolean("target_reels_enabled", false)
            putBoolean("target_focus_enabled", false)
            apply()
        }

        // Ensure everything is explicitly disabled on skip so user gets zero hidden background blocks
        val loader = SavedPreferencesLoader(context)
        loader.setReelBlockerEnabled(false)
        loader.setReelBlockerTiktokEnabled(false)
        loader.setReelBlockerYoutubeEnabled(false)
        loader.setReelBlockerInstagramEnabled(false)
        loader.setWebsiteBlockerEnabled(false)
        loader.setKeywordBlockerFeatureEnabled(false)
        loader.setAppBlockerFeatureEnabled(false)

        proceedToAccessibilityGuide()
    }

    private fun proceedToAccessibilityGuide() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, AccessibilityGuide())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
