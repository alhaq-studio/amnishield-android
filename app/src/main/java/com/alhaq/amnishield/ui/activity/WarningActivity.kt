package com.alhaq.amnishield.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogWarningOverlayBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader


class WarningActivity : AppCompatActivity() {

    private var proceedTimer: CountDownTimer? = null
    private var dialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        com.alhaq.amnishield.utils.ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val savedPreferencesLoader = SavedPreferencesLoader(this)


        val mode = intent.getIntExtra("mode", 0)
        
        val warningScreenConfig = when (mode) {
            Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER -> savedPreferencesLoader.loadViewBlockerWarningInfo()
            Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER -> savedPreferencesLoader.loadKeywordBlockerWarningInfo()
            else -> savedPreferencesLoader.loadAppBlockerWarningInfo()
        }
        val binding = DialogWarningOverlayBinding.inflate(layoutInflater)
        val isHomePressRequested = intent.getBooleanExtra("is_press_home", false)
        val isReelBlockerWarning = intent.getBooleanExtra("is_reel_blocker", false)
        val isAppBlockerMode = mode == Constants.WARNING_SCREEN_MODE_APP_BLOCKER
        val isKeywordBlockerMode = mode == Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER

        val isSimpleMode = savedPreferencesLoader.getEnforcementMode() == "SIMPLE"
        val blockedFeature = intent.getStringExtra("blocked_by_feature") 
            ?: if (isKeywordBlockerMode) "Keyword Blocker" else if (isReelBlockerWarning) "Reels Blocker" else "App Blocker"

        binding.minsPicker.setValue(3)
        binding.minsPicker.minValue = 2
        var isDialogCancelable = !isAppBlockerMode || isHomePressRequested

        if (isSimpleMode) {
            binding.warningTitle.text = "Access Blocked"
            binding.warningMsg.text = "This content has been blocked permanently under Simple Mode by the $blockedFeature feature."
            binding.btnProceed.visibility = View.GONE
            binding.proceedSeconds.visibility = View.GONE
            binding.minsPicker.visibility = View.GONE
            isDialogCancelable = false
        } else {
            binding.warningTitle.text = when {
                isAppBlockerMode -> getString(R.string.warning_title_app_blocker)
                isKeywordBlockerMode -> "Keyword Blocked"
                else -> getString(R.string.warning_title_reels_blocker)
            }

            if (warningScreenConfig.isProceedDisabled) {
                binding.btnProceed.visibility = View.GONE
                binding.proceedSeconds.visibility = View.GONE
            } else {
                proceedTimer =
                    object : CountDownTimer(warningScreenConfig.proceedDelayInSecs * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        binding.proceedSeconds.text =
                            getString(R.string.proceed_in, millisUntilFinished / 1000)
                    }

                    override fun onFinish() {
                        binding.btnProceed.let { button ->
                            button.isEnabled = true
                            if (warningScreenConfig.isDynamicIntervalSettingAllowed) {
                                binding.minsPicker.visibility = View.VISIBLE
                            }
                            button.setText(R.string.proceed)
                        }
                        binding.proceedSeconds.visibility = View.GONE
                    }
                }.start()
            }
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(isDialogCancelable)
            .setOnCancelListener {
                finish()
            }
            .show()

        val fallbackMessage = when {
            isAppBlockerMode -> getString(R.string.warning_default_message_app)
            isKeywordBlockerMode -> "Content containing a blocked keyword was detected."
            else -> getString(R.string.warning_default_message_reels)
        }
        val configuredMessage = warningScreenConfig.message.trim()

        if (isSimpleMode) {
            binding.btnCancel.text = "Go Home"
        } else {
            binding.warningMsg.text = if (configuredMessage.isNotEmpty()) configuredMessage else fallbackMessage
            binding.minsPicker.setValue(warningScreenConfig.timeInterval / 60000)
            binding.btnCancel.text = if (isAppBlockerMode || isHomePressRequested) {
                getString(R.string.warning_cancel_go_home)
            } else {
                getString(R.string.warning_cancel_stay_safe)
            }
        }

        binding.btnCancel.setOnClickListener {
            if (isSimpleMode || isAppBlockerMode || isHomePressRequested) {
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            dialog?.dismiss()
            finish()
        }
        binding.btnProceed.setOnClickListener {
            if (mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER) {
                intent.getStringExtra("result_id")
                    ?.let { it1 ->
                        val refreshAction = if (isReelBlockerWarning) {
                            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER_COOLDOWN
                        } else {
                            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN
                        }
                        sendRefreshRequest(
                            it1,
                            refreshAction,
                            binding.minsPicker.getValue()
                        )
                    }
            }

            if (mode == Constants.WARNING_SCREEN_MODE_APP_BLOCKER) {
                intent.getStringExtra("result_id")
                    ?.let { it1 ->
                        sendRefreshRequest(
                            it1,
                            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN,
                            binding.minsPicker.getValue()
                        )
                        val intent = packageManager.getLaunchIntentForPackage(it1)
                        if (intent != null) {
                            startActivity(intent)
                        }
                    }
            }

            if (mode == Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER) {
                intent.getStringExtra("result_id")
                    ?.let { it1 ->
                        sendRefreshRequest(
                            it1,
                            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN,
                            binding.minsPicker.getValue()
                        )
                    }
            }

            dialog?.dismiss()
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        proceedTimer?.cancel()
        dialog?.dismiss()  // Ensure dialog is dismissed before activity is destroyed


    }

    private fun sendRefreshRequest(id: String, action: String, time: Int) {
        val intent = Intent(action).setPackage(packageName)
        intent.putExtra("result_id", id)
        intent.putExtra("selected_time", time * 60_000)
        sendBroadcast(intent)
    }
}