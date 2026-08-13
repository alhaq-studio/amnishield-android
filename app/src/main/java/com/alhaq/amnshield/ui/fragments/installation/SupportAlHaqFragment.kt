package com.alhaq.amnshield.ui.fragments.installation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.alhaq.amnshield.databinding.FragmentSupportAlhaqBinding
import com.alhaq.amnshield.premium.PaymentManager
import com.alhaq.amnshield.premium.PremiumManager
import com.alhaq.amnshield.ui.activity.MainActivity

class SupportAlHaqFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "support_alhaq_fragment"
    }

    private var _binding: FragmentSupportAlhaqBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSupportAlhaqBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPlanMonthly.setOnClickListener {
            PaymentManager.openStripeCheckout(requireContext(), plan = "monthly")
        }

        binding.btnPlanAnnual.setOnClickListener {
            PaymentManager.openStripeCheckout(requireContext(), plan = "annual")
        }

        binding.btnActivate.setOnClickListener {
            val key = binding.etKey.text?.toString()?.trim() ?: ""
            if (key.isNotEmpty()) {
                val success = PremiumManager.getInstance(requireContext()).redeemLicenseKey(key)
                if (success) {
                    Toast.makeText(requireContext(), "Pro License Key Activated! Welcome to AmnShield Pro!", Toast.LENGTH_LONG).show()
                    finishOnboarding()
                } else {
                    Toast.makeText(requireContext(), "Invalid License Key.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Please enter a license key", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFinish.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun finishOnboarding() {
        val sharedPreferences = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isFirstLaunchComplete", true).apply()

        // Launch MainActivity
        val intent = Intent(requireActivity(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
