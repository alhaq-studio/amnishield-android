package com.alhaq.amnishield.ui.fragments.installation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "welcome_fragment"
    }

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!  // Safe getter for binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cbTos.setOnCheckedChangeListener { _, isChecked ->
            binding.btnNext.isEnabled = isChecked
            if (isChecked) {
                binding.cardTermsAgreement.strokeColor =
                    com.google.android.material.color.MaterialColors.getColor(
                        binding.cardTermsAgreement,
                        androidx.appcompat.R.attr.colorPrimary,
                        android.graphics.Color.BLUE
                    )
                binding.cardTermsAgreement.strokeWidth = 2
            } else {
                binding.cardTermsAgreement.strokeColor =
                    com.google.android.material.color.MaterialColors.getColor(
                        binding.cardTermsAgreement,
                        com.google.android.material.R.attr.colorOutlineVariant,
                        android.graphics.Color.GRAY
                    )
                binding.cardTermsAgreement.strokeWidth = 1
            }
        }

        // Underline link texts for visual affordance
        binding.openTos.paintFlags = binding.openTos.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        binding.openPrivacyPolicy.paintFlags = binding.openPrivacyPolicy.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG

        binding.rowAgreementToggle.setOnClickListener {
            binding.cbTos.isChecked = !binding.cbTos.isChecked
        }

        binding.openTos.setOnClickListener {
            openUrl(Constants.AMNISHIELD_TERMS_URL)
        }

        binding.openPrivacyPolicy.setOnClickListener {
            openUrl(Constants.AMNISHIELD_MOBILE_PRIVACY_URL)
        }

        binding.btnNext.setOnClickListener {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_holder,
                    TargetingFragment()
                )
                .addToBackStack(null)
                .commit()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                "No application found to open the link",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
