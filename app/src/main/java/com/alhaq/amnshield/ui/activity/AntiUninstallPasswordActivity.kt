package com.alhaq.amnshield.ui.activity

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.R
import com.alhaq.amnshield.databinding.ActivityAntiUninstallPasswordBinding
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.utils.PasswordHasher
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import com.alhaq.amnshield.utils.ThemeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

/**
 * Activity shown when user attempts to access protected Settings screens (Device Admin, Accessibility)
 * or uninstall/deactivate AmnShield.
 * Requires password verification to proceed, or strictly redirects to the Home screen.
 */
class AntiUninstallPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAntiUninstallPasswordBinding
    private var savedPassword: String? = null
    private var antiUninstallMode: Int = -1
    private var recoveryTimer: CountDownTimer? = null

    companion object {
        private const val RECOVERY_WAIT_MILLIS = 5 * 60 * 1000L
        private const val KEY_RECOVERY_UNLOCK_AT = "recovery_unlock_at"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCKOUT_MILLIS = 2 * 60 * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAntiUninstallPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                maxOf(systemBars.bottom, imeInsets.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }

        // Handle back button: force exit to Android Home to prevent backstack leaks to Settings
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                forceExitToHome()
            }
        })

        // Load anti-uninstall settings
        val prefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        savedPassword = prefs.getString("password", null)
        antiUninstallMode = prefs.getInt("mode", -1)

        setupUI()
    }

    private fun setupUI() {
        when (antiUninstallMode) {
            Constants.ANTI_UNINSTALL_PASSWORD_MODE -> {
                setupPasswordMode()
            }
            Constants.ANTI_UNINSTALL_TIMED_MODE -> {
                setupTimedMode()
            }
            else -> {
                // Unknown mode, force exit
                forceExitToHome()
            }
        }
    }

    private val loader by lazy { SavedPreferencesLoader(applicationContext) }

    private fun setupPasswordMode() {
        binding.txtTitle.text = getString(R.string.anti_uninstall_password_required)
        binding.txtMessage.text = getString(R.string.enter_password_to_proceed_settings)
        binding.passwordInputLayout.visibility = android.view.View.VISIBLE
        binding.btnCancel.visibility = android.view.View.VISIBLE
        binding.btnVerify.visibility = android.view.View.VISIBLE
        binding.btnForgotPassword.visibility = android.view.View.VISIBLE
        binding.btnForgotPassword.text = getString(R.string.forgot_pin)

        binding.btnVerify.setOnClickListener {
            val lockoutRemaining = getLockoutRemainingMillis()
            if (lockoutRemaining > 0L) {
                val remainingSeconds = (lockoutRemaining / 1000L).toInt().coerceAtLeast(1)
                Toast.makeText(
                    this,
                    getString(R.string.anti_uninstall_too_many_attempts, remainingSeconds),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val enteredPassword = binding.passwordInput.text.toString()

            if (PasswordHasher.verify(enteredPassword, savedPassword)) {
                // If the stored value was the legacy plaintext format, silently upgrade it
                if (PasswordHasher.isPlainText(savedPassword)) {
                    val upgraded = PasswordHasher.hash(enteredPassword)
                    getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                        .edit()
                        .putString("password", upgraded)
                        .apply()
                    savedPassword = upgraded
                }

                // Password correct - grant in-memory 5-minute admin grace period
                clearRecoveryTimerState()
                clearAttemptState()
                sendPasswordVerifiedBroadcast()

                // Allow user access into Settings
                finish()
            } else {
                // Password incorrect - register attempt and apply lockout if reached
                val attemptsLeft = registerFailedAttempt()
                binding.passwordInputLayout.error = if (attemptsLeft > 0) {
                    getString(R.string.anti_uninstall_attempts_remaining, attemptsLeft)
                } else {
                    getString(R.string.incorrect_password)
                }
                binding.passwordInput.text?.clear()
            }
        }

        binding.btnCancel.setOnClickListener {
            forceExitToHome()
        }

        binding.btnForgotPassword.setOnClickListener {
            val lockoutRemaining = getLockoutRemainingMillis()
            if (lockoutRemaining > 0L) {
                val remainingSeconds = (lockoutRemaining / 1000L).toInt().coerceAtLeast(1)
                Toast.makeText(
                    this,
                    getString(R.string.anti_uninstall_too_many_attempts, remainingSeconds),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val cooldownMins = loader.getEmergencyAccessCooldownMinutes()
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Password")
                .setMessage("Initiating Password Reset will start a mandatory $cooldownMins-minute cooldown. Protection remains 100% active until the countdown finishes.")
                .setPositiveButton("Start $cooldownMins-Min Cooldown") { _, _ ->
                    val unlockAt = System.currentTimeMillis() + (cooldownMins * 60 * 1000L)
                    getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_RECOVERY_UNLOCK_AT, unlockAt)
                        .apply()
                    beginRecoveryCountdown(unlockAt, isPasswordReset = true)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        updateLockoutUiState()
        maybeResumeRecoveryCountdown()
    }

    private fun setupTimedMode() {
        binding.txtTitle.text = getString(R.string.anti_uninstall_timed_mode_active)
        
        val antiPrefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val unlockAtMillis = antiPrefs.getLong("unlock_at_millis", 0L)
        val dateString = antiPrefs.getString("date", null)

        val targetUnlockMillis = if (unlockAtMillis > 0L) {
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

        val remainingMillis = targetUnlockMillis - System.currentTimeMillis()
        if (targetUnlockMillis > 0L && remainingMillis <= 0L) {
            // Expired! Turn off anti-uninstall
            antiPrefs.edit()
                .putBoolean("is_anti_uninstall_on", false)
                .remove("unlock_at_millis")
                .apply()

            val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                .setPackage(packageName)
            sendBroadcast(refreshIntent)

            Toast.makeText(this, getString(R.string.anti_uninstall_timed_mode_expired), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val daysDiff = if (remainingMillis > 0L) {
            kotlin.math.ceil(remainingMillis / (1000.0 * 60.0 * 60.0 * 24.0)).toInt().coerceAtLeast(1)
        } else {
            1
        }
        
        binding.txtMessage.text = getString(R.string.anti_uninstall_timed_mode_days_remaining, daysDiff)
        binding.passwordInputLayout.visibility = android.view.View.GONE
        binding.btnVerify.visibility = android.view.View.GONE
        binding.btnForgotPassword.visibility = android.view.View.VISIBLE
        binding.btnForgotPassword.text = getString(R.string.emergency_turn_off)
        binding.btnCancel.text = getString(R.string.ok)
        binding.btnCancel.visibility = android.view.View.VISIBLE

        binding.btnForgotPassword.setOnClickListener {
            val cooldownMins = loader.getEmergencyAccessCooldownMinutes()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.emergency_access_mode)
                .setMessage("Initiating Emergency Access will start a mandatory $cooldownMins-minute cooldown. Protection remains 100% active during the countdown.\n\nOnce the timer finishes, a 10-minute temporary unlock window will be granted.")
                .setPositiveButton("Start $cooldownMins-Min Cooldown") { _, _ ->
                    loader.requestEmergencyOverride()
                    val unlockAt = System.currentTimeMillis() + (cooldownMins * 60 * 1000L)
                    getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                        .edit()
                        .putLong(KEY_RECOVERY_UNLOCK_AT, unlockAt)
                        .apply()
                    beginRecoveryCountdown(unlockAt, isPasswordReset = false)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnCancel.setOnClickListener {
            forceExitToHome()
        }

        maybeResumeRecoveryCountdown()
    }

    private fun forceExitToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.passwordInput.text?.clear()
        recoveryTimer?.cancel()
        recoveryTimer = null
    }

    private fun maybeResumeRecoveryCountdown() {
        val unlockAt = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .getLong(KEY_RECOVERY_UNLOCK_AT, 0L)
        if (unlockAt > 0L) {
            val isPasswordMode = antiUninstallMode == Constants.ANTI_UNINSTALL_PASSWORD_MODE
            beginRecoveryCountdown(unlockAt, isPasswordReset = isPasswordMode)
        }
    }

    private fun beginRecoveryCountdown(unlockAt: Long, isPasswordReset: Boolean = true) {
        recoveryTimer?.cancel()

        val remaining = unlockAt - System.currentTimeMillis()
        if (remaining <= 0L) {
            onRecoveryReady(isPasswordReset)
            return
        }

        binding.btnForgotPassword.isEnabled = false
        binding.txtRecoveryStatus.visibility = android.view.View.VISIBLE

        recoveryTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = millisUntilFinished / 1000L
                val minutes = (totalSeconds / 60).toInt()
                val seconds = (totalSeconds % 60).toInt()
                binding.txtRecoveryStatus.text = if (isPasswordReset) {
                    getString(R.string.password_reset_in_progress_countdown, minutes, seconds)
                } else {
                    getString(R.string.emergency_override_active_countdown, minutes, seconds)
                }
            }

            override fun onFinish() {
                onRecoveryReady(isPasswordReset)
            }
        }.start()
    }

    private fun onRecoveryReady(isPasswordReset: Boolean) {
        clearRecoveryTimerState()
        clearAttemptState()
        loader.activateEmergencyWindow()
        
        if (!isPasswordReset) {
            // Timed mode emergency override completed -> disable anti-uninstall
            getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_anti_uninstall_on", false)
                .remove("unlock_at_millis")
                .remove("date")
                .apply()
            val refreshIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                .setPackage(packageName)
            sendBroadcast(refreshIntent)
            Toast.makeText(this, "Emergency Override complete. Protection disabled.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Password Reset Complete. Protection unlocked.", Toast.LENGTH_LONG).show()
            sendPasswordVerifiedBroadcast()
        }

        finish()
    }

    private fun clearRecoveryTimerState() {
        recoveryTimer?.cancel()
        recoveryTimer = null
        getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECOVERY_UNLOCK_AT)
            .apply()
    }

    private fun sendPasswordVerifiedBroadcast() {
        val intent = Intent(
            AmnShieldAccessibilityService.INTENT_ACTION_PASSWORD_VERIFIED
        ).setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun getLockoutRemainingMillis(): Long {
        val lockoutUntil = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .getLong(KEY_LOCKOUT_UNTIL, 0L)
        return (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun registerFailedAttempt(): Int {
        val prefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1

        if (failed >= MAX_FAILED_ATTEMPTS) {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + LOCKOUT_MILLIS)
                .apply()
            updateLockoutUiState()
            return 0
        }

        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, failed).apply()
        return (MAX_FAILED_ATTEMPTS - failed).coerceAtLeast(0)
    }

    private fun clearAttemptState() {
        getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
        updateLockoutUiState()
    }

    private fun updateLockoutUiState() {
        val isLocked = getLockoutRemainingMillis() > 0L
        binding.btnVerify.isEnabled = !isLocked
        binding.btnForgotPassword.isEnabled = !isLocked
    }
}
