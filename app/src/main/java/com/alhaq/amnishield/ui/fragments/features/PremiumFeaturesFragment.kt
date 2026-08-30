package com.alhaq.amnishield.ui.fragments.features

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
import com.alhaq.amnishield.BuildConfig
import com.alhaq.amnishield.R
import com.alhaq.amnishield.data.AmniShieldProductDetails
import com.alhaq.amnishield.data.sync.SupabaseRest
import com.alhaq.amnishield.databinding.FragmentPremiumFeaturesBinding
import com.alhaq.amnishield.premium.PaymentManager
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.premium.PremiumProducts
import com.alhaq.amnishield.utils.BillingClientWrapper
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.content.Context
import android.content.Intent

open class PremiumFeaturesFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "premium_features_fragment"
        const val ARG_IS_ONBOARDING = "is_onboarding"

        fun newInstance(isOnboarding: Boolean = false): PremiumFeaturesFragment {
            return PremiumFeaturesFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_ONBOARDING, isOnboarding)
                }
            }
        }
    }

    private var _binding: FragmentPremiumFeaturesBinding? = null
    protected val binding get() = _binding!!
    private val premiumManager by lazy { PremiumManager.getInstance(requireContext().applicationContext) }
    private val preferencesLoader by lazy { SavedPreferencesLoader(requireContext().applicationContext) }
    private var billingClientWrapper: BillingClientWrapper? = null
    private val products = mutableMapOf<String, AmniShieldProductDetails>()

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

        val isOnboarding = arguments?.getBoolean(ARG_IS_ONBOARDING, false) ?: false
        if (isOnboarding) {
            binding.btnFinishOnboarding.visibility = View.VISIBLE
            binding.btnFinishOnboarding.setOnClickListener {
                finishOnboarding()
            }
        } else {
            binding.btnFinishOnboarding.visibility = View.GONE
        }

        if (BuildConfig.IS_PLAYSTORE) {
            billingClientWrapper = BillingClientWrapper(requireContext().applicationContext).apply {
                startConnection {
                    if (!isAdded || _binding == null) return@startConnection
                    queryProducts(PremiumProducts.allInAppProducts + PremiumProducts.allSubscriptionProducts) { productDetailsList ->
                        if (!isAdded || _binding == null) return@queryProducts
                        productDetailsList.forEach { products[it.productId] = it }
                        updateProductDetails()
                    }
                }
            }
        }
    }

    fun finishOnboarding() {
        val ctx = context ?: return
        val sharedPreferences = ctx.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isFirstLaunchComplete", true).apply()

        val intent = Intent(ctx, com.alhaq.amnishield.ui.activity.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        billingClientWrapper?.endConnection()
        billingClientWrapper = null
        _binding = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        binding.btnContinueToApp.setOnClickListener {
            finishOnboarding()
        }
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
            val ctx = context ?: return@setOnClickListener
            if (key.isNotEmpty()) {
                if (premiumManager.redeemLicenseKey(key)) {
                    binding.etKey.text?.clear()
                    handleActivationSuccess("Pro License Activated Successfully!")
                } else {
                    Toast.makeText(ctx, "Invalid or Expired License Key", Toast.LENGTH_LONG).show()
                }
            } else {
                showLicenseRedemptionDialog()
            }
        }
    }

    private fun handleActivationSuccess(successMessage: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, successMessage, Toast.LENGTH_LONG).show()
        updatePremiumState()
        val isOnboarding = arguments?.getBoolean(ARG_IS_ONBOARDING, false) ?: false
        if (isOnboarding) {
            finishOnboarding()
        }
    }

    private fun handlePlanSelection(planType: String, playProductId: String) {
        val ctx = context ?: return
        if (premiumManager.isPremium()) {
            Toast.makeText(ctx, R.string.premium_already_active, Toast.LENGTH_SHORT).show()
            return
        }

        if (BuildConfig.IS_PLAYSTORE) {
            launchPlayPurchase(playProductId)
        } else {
            // Direct Supabase Stripe checkout for Universal & F-Droid
            PaymentManager.openStripeCheckout(ctx, plan = planType)
        }
    }

    private fun launchPlayPurchase(productId: String) {
        val activity = activity ?: return
        val wrapper = billingClientWrapper ?: return
        val ctx = context ?: return
        products[productId]?.let { product ->
            wrapper.launchPurchaseFlow(activity, product) { isSuccess, debugMessage ->
                val activeCtx = context ?: return@launchPurchaseFlow
                if (!isAdded || _binding == null) return@launchPurchaseFlow
                if (isSuccess) {
                    premiumManager.updatePremiumStatus(true)
                    handleActivationSuccess(getString(R.string.premium_purchase_success))
                } else {
                    Toast.makeText(activeCtx, "Purchase failed: $debugMessage", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Toast.makeText(ctx, "Connecting to Google Play Store...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restorePurchases() {
        val ctx = context ?: return
        billingClientWrapper?.queryPurchases { purchases ->
            val activeCtx = context ?: return@queryPurchases
            if (!isAdded || _binding == null) return@queryPurchases
            if (purchases.isNotEmpty()) {
                premiumManager.updatePremiumStatus(true)
                handleActivationSuccess(getString(R.string.premium_purchase_success))
            } else {
                Toast.makeText(activeCtx, R.string.premium_no_previous_purchases, Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(ctx, "Use license key activation for non-Play builds.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePremiumState() {
        if (!isAdded || _binding == null || context == null) return
        val userType = premiumManager.getUserType()
        val isPremium = userType != PremiumManager.UserType.FREE
        binding.premiumActiveContainer.visibility = if (isPremium) View.VISIBLE else View.GONE
        binding.premiumUpsellContainer.visibility = if (isPremium) View.GONE else View.VISIBLE
        binding.btnBuyMonthly.isEnabled = !isPremium
        binding.btnBuyYearly.isEnabled = !isPremium
        binding.btnBuyLifetime.isEnabled = !isPremium
        binding.btnRestore.visibility = if (isPremium || !BuildConfig.IS_PLAYSTORE) View.GONE else View.VISIBLE

        val isOnboarding = arguments?.getBoolean(ARG_IS_ONBOARDING, false) ?: false
        binding.btnFinishOnboarding.visibility = if (isOnboarding && !isPremium) View.VISIBLE else View.GONE

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
        val act = activity ?: return
        act.runOnUiThread {
            if (!isAdded || _binding == null) return@runOnUiThread
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

            val isOnboarding = arguments?.getBoolean(ARG_IS_ONBOARDING, false) ?: false
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.compassionate_access_success_title)
                .setMessage(
                    getString(
                        R.string.compassionate_access_success_message,
                        appId,
                        email ?: getString(R.string.compassionate_access_no_email_value)
                    )
                )
                .setPositiveButton(if (isOnboarding) "Continue to AmniShield" else "OK") { _, _ ->
                    if (isOnboarding) {
                        finishOnboarding()
                    }
                }
                .setCancelable(!isOnboarding)
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
            .setMessage("Paste the license key you received after purchasing AmniShield Pro.")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val licenseKey = input.text.toString().trim()
                if (premiumManager.redeemLicenseKey(licenseKey)) {
                    handleActivationSuccess("Pro License Activated Successfully!")
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
        val ctx = context ?: return
        Toast.makeText(ctx, "Sending 6-digit code to $email...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = SupabaseRest()
                rest.sendEmailOtp(email)
                withContext(Dispatchers.Main) {
                    if (isAdded && _binding != null) {
                        showOtpVerificationDialog(email)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val activeCtx = context ?: return@withContext
                    Toast.makeText(activeCtx, "Failed to send code: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showOtpVerificationDialog(email: String) {
        val ctx = context ?: return
        val codeInput = EditText(ctx).apply {
            hint = "Enter verification code (e.g. 48139226)"
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(12))
        }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Enter Verification Code")
            .setMessage("We sent a verification email to $email.\n\n• Enter the verification code below, OR\n• Tap the link in the email to verify and activate automatically.")
            .setView(codeInput)
            .setPositiveButton("Verify & Activate") { _, _ ->
                val code = codeInput.text.toString().trim()
                if (code.length >= 6 && code.all { it.isDigit() }) {
                    verifyOtpAndActivate(email, code)
                } else {
                    val activeCtx = context ?: return@setPositiveButton
                    Toast.makeText(activeCtx, "Please enter the verification code", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun verifyOtpAndActivate(email: String, code: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, "Verifying account...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = SupabaseRest()
                val session = rest.verifyOtp(email, code, "email")
                val profile = rest.fetchProfile(session)

                withContext(Dispatchers.Main) {
                    val activeCtx = context ?: return@withContext
                    if (profile != null && !profile.licenseKey.isNullOrBlank()) {
                        if (premiumManager.redeemLicenseKey(profile.licenseKey)) {
                            handleActivationSuccess("AmniShield Pro Activated for $email!")
                        } else {
                            Toast.makeText(activeCtx, "License key found in profile was invalid or expired.", Toast.LENGTH_LONG).show()
                        }
                    } else if (profile?.isPremium == true) {
                        premiumManager.updatePremiumStatus(true)
                        handleActivationSuccess("AmniShield Pro Status Restored!")
                    } else {
                        Toast.makeText(activeCtx, "No active Pro license found for $email. Please check your purchase email.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val activeCtx = context ?: return@withContext
                    Toast.makeText(activeCtx, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
