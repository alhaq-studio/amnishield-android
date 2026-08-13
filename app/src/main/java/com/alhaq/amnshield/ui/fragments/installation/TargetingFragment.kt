package com.alhaq.amnshield.ui.fragments.installation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alhaq.amnshield.R
import com.alhaq.amnshield.databinding.FragmentTargetingBinding

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
        binding.btnSkip.setOnClickListener { proceedToPermissions() }
    }

    private fun saveTargetingAndProceed() {
        val prefs = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("target_gaze_enabled", binding.cbGaze.isChecked)
            putBoolean("target_web_enabled", binding.cbWeb.isChecked)
            putBoolean("target_reels_enabled", binding.cbReels.isChecked)
            putBoolean("target_focus_enabled", binding.cbFocus.isChecked)
            apply()
        }
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
