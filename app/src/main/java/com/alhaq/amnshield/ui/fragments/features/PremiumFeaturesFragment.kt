package com.alhaq.amnshield.ui.fragments.features

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.alhaq.amnshield.BuildConfig
import com.alhaq.amnshield.R
import com.alhaq.amnshield.data.AmnShieldProductDetails
import com.alhaq.amnshield.data.sync.SupabaseRest
import com.alhaq.amnshield.databinding.FragmentPremiumFeaturesBinding
import com.alhaq.amnshield.premium.PaymentManager
import com.alhaq.amnshield.premium.PremiumManager
import com.alhaq.amnshield.premium.PremiumProducts
import com.alhaq.amnshield.utils.BillingClientWrapper
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PremiumFeaturesFragment : Fragment() {

    private var _binding: FragmentPremiumFeaturesBinding? = null
    private val binding get() = _binding!!
    private val premiumManager by lazy { PremiumManager.getInstance(requireContext().applicationContext) }
    private val preferencesLoader by lazy { SavedPreferencesLoader(requireContext().applicationContext) }
    private var billingClientWrapper: BillingClientWrapper? = null
    private val products = mutableMapOf<String, AmnShieldProductDetails>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPremiumFeaturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        updatePremiumState()

        if (BuildConfig.IS_PLAYSTORE) {
            billingClientWrapper = BillingClientWrapper(requireContext()).apply {
                startConnection {
                    queryProducts(PremiumProducts.allInAppProducts + PremiumProducts.allSubscriptionProducts) { productDetailsList ->
                        productDetailsList.forEach { products[it.productId] = it }
                        updateProductDetails()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        binding.btnBuyMonthly.setOnClickListener {
            handlePlanSelection("monthly", PremiumProducts.PRODUCT_MONTHLY)
        }
        binding.btnBuyYearly.setOnClickListener {
            handlePlanSelection("annual", PremiumProducts.PRODUCT_YEARLY)
        }
        binding.btnBuyLifetime.setOnClickListener {
            handlePlanSelection("lifetime", PremiumProducts.PRODUCT_LIFETIME)
        }
        binding.btnSignInEmailOtp.setOnClickListener {
            showEmailOtpSignInDialog()
        }
        binding.btnRestore.setOnClickListener {
            restorePurchases()
        }
        binding.btnCompassionateAccess.setOnClickListener {
            showCompassionateAccessDialog()
        }
        binding.btnRedeemLicense.setOnClickListener {
            val key = binding.etKey.text?.toString()?.trim().orEmpty()
            if (key.isNotEmpty()) {
                if (premiumManager.redeemLicenseKey(key)) {
                    Toast.makeText(requireContext(), "Pro License Activated Successfully!", Toast.LENGTH_LONG).show()
                    binding.etKey.text?.clear()
                    updatePremiumState()
                } else {
                    Toast.makeText(requireContext(), "Invalid or Expired License Key", Toast.LENGTH_LONG).show()
                }
            } else {
                showLicenseRedemptionDialog()
            }
        }
    }

    private fun handlePlanSelection(planType: String, playProductId: String) {
        if (premiumManager.isPremium()) {
            Toast.makeText(requireContext(), R.string.premium_already_active, Toast.LENGTH_SHORT).show()
            return
        }

        if (BuildConfig.IS_PLAYSTORE) {
            launchPlayPurchase(playProductId)
        } else {
            // Direct Supabase Stripe checkout for Universal & F-Droid
            PaymentManager.openStripeCheckout(requireContext(), plan = planType)
        }
    }

    private fun launchPlayPurchase(productId: String) {
        val activity = activity ?: return
        val wrapper = billingClientWrapper ?: return
        products[productId]?.let { product ->
            wrapper.launchPurchaseFlow(activity, product) { isSuccess, debugMessage ->
                if (isSuccess) {
                    premiumManager.updatePremiumStatus(true)
                    updatePremiumState()
                    Toast.makeText(requireContext(), R.string.premium_purchase_success, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Purchase failed: $debugMessage", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Toast.makeText(requireContext(), "Connecting to Google Play Store...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restorePurchases() {
        billingClientWrapper?.queryPurchases { purchases ->
            if (purchases.isNotEmpty()) {
                premiumManager.updatePremiumStatus(true)
                updatePremiumState()
                Toast.makeText(requireContext(), R.string.premium_purchase_success, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.premium_no_previous_purchases, Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), "Use license key activation for non-Play builds.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePremiumState() {
        val userType = premiumManager.getUserType()
        val isPremium = userType != PremiumManager.UserType.FREE
        binding.premiumActiveContainer.visibility = if (isPremium) View.VISIBLE else View.GONE
        binding.premiumUpsellContainer.visibility = if (isPremium) View.GONE else View.VISIBLE
        binding.btnBuyMonthly.isEnabled = !isPremium
        binding.btnBuyYearly.isEnabled = !isPremium
        binding.btnBuyLifetime.isEnabled = !isPremium
        binding.btnRestore.visibility = if (isPremium || !BuildConfig.IS_PLAYSTORE) View.GONE else View.VISIBLE

        val activeMessage = when (userType) {
            PremiumManager.UserType.PREMIUM -> getString(R.string.premium_active_message)
            PremiumManager.UserType.COMPASSIONATE -> {
                val expiry = preferencesLoader.getCompassionateAccessExpiry()
                if (expiry > 0L) {
                    getString(
                        R.string.compassionate_access_active_until,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiry))
                    )
                } else {
                    getString(R.string.compassionate_access_active_description)
                }
            }
            PremiumManager.UserType.FREE -> getString(R.string.premium_active_message)
        }
        binding.txtPremiumActiveMessage.text = activeMessage
    }

    private fun updateProductDetails() {
        activity?.runOnUiThread {
            products[PremiumProducts.PRODUCT_MONTHLY]?.let { product ->
                binding.txtMonthlyPrice.text = product.priceText
            }
            products[PremiumProducts.PRODUCT_YEARLY]?.let { product ->
                binding.txtYearlyPrice.text = product.priceText
            }
            products[PremiumProducts.PRODUCT_LIFETIME]?.let { product ->
                binding.txtLifetimePrice.text = product.priceText
            }
        }
    }

    private fun showCompassionateAccessDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.compassionate_access_title)
            .setMessage(getString(R.string.compassionate_access_intro_message))
            .setPositiveButton(R.string.compassionate_access_intro_positive) { _, _ ->
                showCompassionateAccessForm()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCompassionateAccessForm() {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.padding_normal)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }

        val nameInput = EditText(context).apply {
            hint = getString(R.string.compassionate_access_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val emailInput = EditText(context).apply {
            hint = getString(R.string.compassionate_access_email_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        container.addView(nameInput)
        container.addView(emailInput)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.compassionate_access_form_title)
            .setMessage(getString(R.string.compassionate_access_form_message))
            .setView(container)
            .setPositiveButton(R.string.compassionate_access_proceed, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text?.toString()?.trim().orEmpty()
                        val email = emailInput.text?.toString()?.trim().orEmpty()

                        if (name.isEmpty()) {
                            nameInput.error = getString(R.string.compassionate_access_name_required)
                            return@setOnClickListener
                        }

                        if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            emailInput.error = getString(R.string.compassionate_access_email_invalid)
                            return@setOnClickListener
                        }

                        dialog.dismiss()
                        grantCompassionateAccess(name, email.ifBlank { null })
                    }
                }
            }
            .show()
    }

    private fun grantCompassionateAccess(userName: String, email: String?) {
        val grantedAt = System.currentTimeMillis()
        val appId = "CAP-$grantedAt-${(10000..99999).random()}"
        val expiresAt = grantedAt + (365L * 24 * 60 * 60 * 1000)

        try {
            preferencesLoader.saveCompassionateAccessGrant(
                appId = appId,
                userName = userName,
                email = email,
                grantedAt = grantedAt,
                expiresAt = expiresAt
            )
            premiumManager.resetReminderWindow()
            updatePremiumState()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.compassionate_access_success_title)
                .setMessage(
                    getString(
                        R.string.compassionate_access_success_message,
                        appId,
                        email ?: getString(R.string.compassionate_access_no_email_value)
                    )
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } catch (_: Exception) {
            Toast.makeText(
                requireContext(),
                R.string.compassionate_access_error,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showLicenseRedemptionDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Paste your license key here"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Redeem License Key")
            .setMessage("Paste the license key you received after purchasing AmnShield Pro.")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val licenseKey = input.text.toString().trim()
                if (premiumManager.redeemLicenseKey(licenseKey)) {
                    Toast.makeText(
                        requireContext(),
                        "Pro License Activated Successfully!",
                        Toast.LENGTH_LONG
                    ).show()
                    updatePremiumState()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Invalid or Expired License Key",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEmailOtpSignInDialog() {
        val emailInput = EditText(requireContext()).apply {
            hint = "Enter your purchase email"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sign In with Email (OTP)")
            .setMessage("We will send a 6-digit one-time code to your email to verify your account and activate Pro.")
            .setView(emailInput)
            .setPositiveButton("Send Code") { _, _ ->
                val email = emailInput.text.toString().trim()
                if (email.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    requestOtpCode(email)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestOtpCode(email: String) {
        Toast.makeText(requireContext(), "Sending 6-digit code to $email...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = SupabaseRest()
                rest.sendEmailOtp(email)
                withContext(Dispatchers.Main) {
                    showOtpVerificationDialog(email)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to send code: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOtpVerificationDialog(email: String) {
        val codeInput = EditText(requireContext()).apply {
            hint = "Enter verification code (e.g. 48139226)"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(12))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enter Verification Code")
            .setMessage("We sent a verification email to $email.\n\n• Enter the verification code below, OR\n• Tap the link in the email to verify and activate automatically.")
            .setView(codeInput)
            .setPositiveButton("Verify & Activate") { _, _ ->
                val code = codeInput.text.toString().trim()
                if (code.length >= 6 && code.all { it.isDigit() }) {
                    verifyOtpAndActivate(email, code)
                } else {
                    Toast.makeText(requireContext(), "Please enter the verification code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun verifyOtpAndActivate(email: String, code: String) {
        Toast.makeText(requireContext(), "Verifying account...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = SupabaseRest()
                val session = rest.verifyOtp(email, code, "email")
                val profile = rest.fetchProfile(session)

                withContext(Dispatchers.Main) {
                    if (profile != null && !profile.licenseKey.isNullOrBlank()) {
                        if (premiumManager.redeemLicenseKey(profile.licenseKey)) {
                            Toast.makeText(requireContext(), "AmnShield Pro Activated for $email!", Toast.LENGTH_LONG).show()
                            updatePremiumState()
                        } else {
                            Toast.makeText(requireContext(), "License key found in profile was invalid or expired.", Toast.LENGTH_LONG).show()
                        }
                    } else if (profile?.isPremium == true) {
                        premiumManager.updatePremiumStatus(true)
                        updatePremiumState()
                        Toast.makeText(requireContext(), "AmnShield Pro Status Restored!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "No active Pro license found for $email. Please check your purchase email.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
