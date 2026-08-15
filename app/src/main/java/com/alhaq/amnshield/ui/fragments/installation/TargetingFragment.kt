package com.alhaq.amnshield.ui.fragments.installation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alhaq.amnshield.R
import com.alhaq.amnshield.databinding.FragmentTargetingBinding
import com.alhaq.amnshield.utils.SavedPreferencesLoader

class TargetingFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "targeting_fragment"
    }

    private var _binding: FragmentTargetingBinding? = null
    private val binding get() = _binding!!

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

        // Make card clicking toggle checkboxes
        binding.cardWeb.setOnClickListener { binding.cbWeb.isChecked = !binding.cbWeb.isChecked }
        binding.cardReels.setOnClickListener { binding.cbReels.isChecked = !binding.cbReels.isChecked }
        binding.cardFocus.setOnClickListener { binding.cbFocus.isChecked = !binding.cbFocus.isChecked }

        binding.btnContinue.setOnClickListener { saveTargetingAndProceed() }
        binding.btnSkip.setOnClickListener { skipTargetingAndProceed() }
    }

    private fun saveTargetingAndProceed() {
        val context = requireContext()
        val isWeb = binding.cbWeb.isChecked
        val isReels = binding.cbReels.isChecked
        val isFocus = binding.cbFocus.isChecked

        val prefs = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("target_gaze_enabled", binding.cbGaze.isChecked)
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

        proceedToPermissions()
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

        proceedToPermissions()
    }

    private fun proceedToPermissions() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, PermissionsFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
