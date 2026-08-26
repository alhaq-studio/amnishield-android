package com.alhaq.amnshield.ui.fragments.anti_uninstall

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnshield.AmnShield
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.R
import com.alhaq.amnshield.databinding.DialogRemoveAntiUninstallBinding
import com.alhaq.amnshield.databinding.FragmentChoseAntiUninstallModeBinding
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.components.PinPromptDialog
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.utils.PasswordHasher
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.alhaq.amnshield.utils.ThemeUtils
import java.util.Calendar

class ChooseModeFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "choose_anti_uninstall_mode"
    }

    private var _binding: FragmentChoseAntiUninstallModeBinding? = null
    private val binding get() = _binding!!

    private val loader by lazy { SavedPreferencesLoader(requireContext().applicationContext) }
    private var countdownTimer: CountDownTimer? = null
    private var isUpdatingUiProgrammatically = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChoseAntiUninstallModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUiProgrammatically) return@setOnCheckedChangeListener

            val antiPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            val isCurrentlyOn = antiPrefs.getBoolean("is_anti_uninstall_on", false)

            if (!isChecked && isCurrentlyOn) {
                // User is trying to toggle OFF active Anti-Uninstall
                // Revert switch visually first until verification succeeds
                isUpdatingUiProgrammatically = true
                binding.switchMaster.isChecked = true
                isUpdatingUiProgrammatically = false

                handleToggleOffAttempt()
            } else if (isChecked && !isCurrentlyOn) {
                // User is toggling ON -> guide them to choose mode and proceed
                Toast.makeText(requireContext(), "Select a protection mode and tap Next to activate", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNext.setOnClickListener {
            verifyPinIfNeeded {
                proceedToModeSetup()
            }
        }

        binding.btnEmergencyAccess.setOnClickListener {
            initiateEmergencyAccessFlow()
        }

        binding.btnResetPassword.setOnClickListener {
            initiatePasswordResetFlow()
        }

        binding.btnCancelCountdown.setOnClickListener {
            cancelActiveCountdown()
        }

        binding.btnTurnOffEmergency.setOnClickListener {
            turnOffAntiUninstallDirectly("Anti-Uninstall turned OFF via Emergency Override")
        }

        reloadState()
    }

    override fun onResume() {
        super.onResume()
        reloadState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
        countdownTimer = null
        _binding = null
    }

    private fun reloadState() {
        if (_binding == null) return

        val antiPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val isAntiUninstallOn = antiPrefs.getBoolean("is_anti_uninstall_on", false)
        val mode = antiPrefs.getInt("mode", -1)
        val unlockAtMillis = antiPrefs.getLong("unlock_at_millis", 0L)
        val dateString = antiPrefs.getString("date", null)

        isUpdatingUiProgrammatically = true
        binding.switchMaster.isChecked = isAntiUninstallOn
        isUpdatingUiProgrammatically = false

        if (isAntiUninstallOn) {
            val modeName = if (mode == Constants.ANTI_UNINSTALL_PASSWORD_MODE) "Password Mode" else "Timed Mode"
            binding.txtMasterStatus.text = getString(R.string.anti_uninstall_active_status, modeName)
            binding.cardActiveStatus.visibility = View.VISIBLE

            if (mode == Constants.ANTI_UNINSTALL_TIMED_MODE) {
                binding.txtActiveModeTitle.text = "Timed Protection Active"
                val remainingMillis = getTimedModeRemainingMillis(unlockAtMillis, dateString)
                if (remainingMillis <= 0L && unlockAtMillis > 0L) {
                    binding.txtActiveModeDetails.text = "Timed lock has reached its end date. Protection can now be removed."
                } else {
                    val daysRemaining = if (remainingMillis > 0L) {
                        kotlin.math.ceil(remainingMillis / (1000.0 * 60.0 * 60.0 * 24.0)).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }
                    binding.txtActiveModeDetails.text = "Blocking all uninstall attempts for $daysRemaining more day(s)."
                }
                binding.btnEmergencyAccess.visibility = View.VISIBLE
                binding.btnResetPassword.visibility = View.GONE
                binding.timedMode.isChecked = true
            } else {
                binding.txtActiveModeTitle.text = "Password Protection Active"
                binding.txtActiveModeDetails.text = "Uninstall attempts are strictly password-protected."
                binding.btnEmergencyAccess.visibility = View.GONE
                binding.btnResetPassword.visibility = View.VISIBLE
                binding.passMode.isChecked = true
            }
        } else {
            binding.txtMasterStatus.text = getString(R.string.anti_uninstall_inactive_status)
            binding.cardActiveStatus.visibility = View.GONE
        }

        // Check Emergency Override Countdown & Window
        if (loader.isEmergencyOverrideCooldownActive()) {
            showLiveEmergencyCountdown(loader.getEmergencyOverrideRemainingMillis())
        } else if (loader.isEmergencyOverrideReady()) {
            loader.activateEmergencyWindow()
            showEmergencyWindowUi()
        } else if (loader.isEmergencyWindowActive()) {
            showEmergencyWindowUi()
        } else {
            binding.cardCountdownBanner.visibility = View.GONE
            binding.cardEmergencyWindow.visibility = View.GONE
        }
    }

    private fun getTimedModeRemainingMillis(unlockAtMillis: Long, dateString: String?): Long {
        val target = if (unlockAtMillis > 0L) {
            unlockAtMillis
        } else if (dateString != null) {
            try {
                val parts = dateString.split("/")
                val cal = Calendar.getInstance().apply {
                    set(parts[2].toInt(), parts[0].toInt() - 1, parts[1].toInt(), 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                cal.timeInMillis
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        return target - System.currentTimeMillis()
    }

    private fun handleToggleOffAttempt() {
        // Step 1: PIN verification if enabled
        verifyPinIfNeeded {
            // Step 2: Mode-specific verification
            val antiPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            val mode = antiPrefs.getInt("mode", -1)

            if (loader.isEmergencyWindowActive()) {
                turnOffAntiUninstallDirectly("Anti-Uninstall turned OFF")
                return@verifyPinIfNeeded
            }

            if (mode == Constants.ANTI_UNINSTALL_PASSWORD_MODE) {
                showPasswordRemovalDialog()
            } else if (mode == Constants.ANTI_UNINSTALL_TIMED_MODE) {
                val unlockAt = antiPrefs.getLong("unlock_at_millis", 0L)
                val dateStr = antiPrefs.getString("date", null)
                val remaining = getTimedModeRemainingMillis(unlockAt, dateStr)
                if (remaining <= 0L && unlockAt > 0L) {
                    turnOffAntiUninstallDirectly("Timed period completed. Anti-Uninstall disabled.")
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Timed Protection Active")
                        .setMessage("Timed mode is active. To turn OFF protection before the target date, you must initiate Emergency Access Mode.")
                        .setPositiveButton("Emergency Turn Off") { _, _ ->
                            initiateEmergencyAccessFlow()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } else {
                turnOffAntiUninstallDirectly("Anti-Uninstall disabled")
            }
        }
    }

    private fun showPasswordRemovalDialog() {
        val dialogBinding = DialogRemoveAntiUninstallBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.remove_anti_uninstall)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.remove) { _, _ ->
                val entered = dialogBinding.password.text.toString()
                val antiPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                val stored = antiPrefs.getString("password", null)

                if (PasswordHasher.verify(entered, stored)) {
                    turnOffAntiUninstallDirectly("Anti-Uninstall removed successfully")
                } else {
                    Toast.makeText(requireContext(), getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Forgot Password?") { _, _ ->
                initiatePasswordResetFlow()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun initiateEmergencyAccessFlow() {
        val cooldownMins = loader.getEmergencyAccessCooldownMinutes()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.emergency_access_mode)
            .setMessage("Initiating Emergency Access will start a mandatory $cooldownMins-minute cooldown. Protection remains 100% active during the countdown.\n\nOnce the timer finishes, a 10-minute temporary unlock window will be granted.")
            .setPositiveButton("Start $cooldownMins-Min Cooldown") { _, _ ->
                loader.requestEmergencyOverride()
                val remaining = loader.getEmergencyOverrideRemainingMillis()
                showLiveEmergencyCountdown(remaining)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiatePasswordResetFlow() {
        val cooldownMins = loader.getEmergencyAccessCooldownMinutes()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Password")
            .setMessage("Initiating Password Reset will start a mandatory $cooldownMins-minute cooldown.\n\nAll protections stay active until the timer finishes.")
            .setPositiveButton("Start $cooldownMins-Min Cooldown") { _, _ ->
                loader.requestEmergencyOverride()
                val remaining = loader.getEmergencyOverrideRemainingMillis()
                showLiveEmergencyCountdown(remaining, isPasswordReset = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLiveEmergencyCountdown(remainingMillis: Long, isPasswordReset: Boolean = false) {
        countdownTimer?.cancel()

        binding.cardCountdownBanner.visibility = View.VISIBLE
        binding.cardEmergencyWindow.visibility = View.GONE
        binding.txtCountdownTitle.text = if (isPasswordReset) "Password Reset in Progress" else "Emergency Override Active"

        val totalDuration = loader.getEmergencyAccessCooldownMinutes() * 60 * 1000L

        countdownTimer = object : CountDownTimer(remainingMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000L
                val mins = (totalSeconds / 60).toInt()
                val secs = (totalSeconds % 60).toInt()
                binding.txtCountdownTimer.text = if (isPasswordReset) {
                    getString(R.string.password_reset_in_progress_countdown, mins, secs)
                } else {
                    getString(R.string.emergency_override_active_countdown, mins, secs)
                }

                val progress = ((totalDuration - millisUntilFinished).toFloat() / totalDuration.toFloat() * 100).toInt()
                binding.progressCountdown.progress = progress.coerceIn(0, 100)
            }

            override fun onFinish() {
                loader.activateEmergencyWindow()
                reloadState()
                Toast.makeText(requireContext(), "Emergency Override Complete. 10-minute temporary window active.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun showEmergencyWindowUi() {
        countdownTimer?.cancel()
        binding.cardCountdownBanner.visibility = View.GONE
        binding.cardEmergencyWindow.visibility = View.VISIBLE
    }

    private fun cancelActiveCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        loader.clearEmergencyOverrideRequest()
        reloadState()
        Toast.makeText(requireContext(), "Cooldown cancelled. Protection remains active.", Toast.LENGTH_SHORT).show()
    }

    private fun turnOffAntiUninstallDirectly(message: String) {
        val antiPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        antiPrefs.edit()
            .putBoolean("is_anti_uninstall_on", false)
            .remove("unlock_at_millis")
            .remove("date")
            .apply()

        loader.clearEmergencyOverride()

        val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            .setPackage(requireContext().packageName)
        requireContext().sendBroadcast(refreshIntent)

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        reloadState()
    }

    private fun proceedToModeSetup() {
        when (binding.radioGroup.checkedRadioButtonId) {
            binding.passMode.id -> {
                val fragment = SetupPasswordModeFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .addToBackStack(null)
                    .commit()
            }
            binding.timedMode.id -> {
                val fragment = SetupTimedModeFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_holder, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun verifyPinIfNeeded(onSuccess: () -> Unit) {
        val pinEnabled = loader.isPinSecurityEnabled()
        val pinCode = loader.getPinCode()

        val needsPin = pinEnabled && pinCode.isNotEmpty() && !AmnShield.isBypassSessionActive()

        if (!needsPin) {
            onSuccess()
            return
        }

        val dialog = Dialog(requireContext(), android.R.style.Theme_Material_NoActionBar_Fullscreen)
        dialog.window?.let { window ->
            window.decorView.setViewTreeLifecycleOwner(viewLifecycleOwner)
            window.decorView.setViewTreeViewModelStoreOwner(requireActivity())
            window.decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AmnShieldTheme(appTheme = ThemeUtils.resolveAppTheme(requireContext())) {
                    com.alhaq.amnshield.ui.components.PinPromptContent(
                        correctPin = pinCode,
                        title = getString(R.string.pin_verification_required_title),
                        subtitle = getString(R.string.pin_verification_required_desc),
                        allowForgotPin = true,
                        onDismiss = { dialog.dismiss() },
                        onPinSuccess = {
                            AmnShield.unlockBypassSession()
                            dialog.dismiss()
                            onSuccess()
                        },
                        onPinResetCompleted = { newPin ->
                            AmnShield.unlockBypassSession()
                            dialog.dismiss()
                            onSuccess()
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.setCancelable(false)
        dialog.show()
    }
}
