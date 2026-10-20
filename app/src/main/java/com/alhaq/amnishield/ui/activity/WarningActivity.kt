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

    companion object {
        private const val KEY_REMAINING_PROCEED_MILLIS = "key_remaining_proceed_millis"
        private const val KEY_PROCEED_TIMER_FINISHED = "key_proceed_timer_finished"
        private const val KEY_SELECTED_MINS_VALUE = "key_selected_mins_value"
    }

    private var proceedTimer: CountDownTimer? = null
    private var dialog: AlertDialog? = null
    private var remainingProceedMillis: Long = 0L
    private var isProceedTimerFinished: Boolean = false
    private var dialogBinding: DialogWarningOverlayBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        com.alhaq.amnishield.utils.ThemeUtils.applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val savedPreferencesLoader = SavedPreferencesLoader(this)


        val mode = intent.getIntExtra("mode", 0)
        val isFocusMode = mode == Constants.WARNING_SCREEN_MODE_FOCUS_MODE
        
        val warningScreenConfig = when (mode) {
            Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER -> savedPreferencesLoader.loadViewBlockerWarningInfo()
            Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER -> savedPreferencesLoader.loadKeywordBlockerWarningInfo()
            Constants.WARNING_SCREEN_MODE_FOCUS_MODE -> savedPreferencesLoader.loadFocusModeWarningInfo()
            else -> savedPreferencesLoader.loadAppBlockerWarningInfo()
        }
        val binding = DialogWarningOverlayBinding.inflate(layoutInflater)
        dialogBinding = binding
        val isHomePressRequested = intent.getBooleanExtra("is_press_home", false)
        val isReelBlockerWarning = intent.getBooleanExtra("is_reel_blocker", false)
        val isAppBlockerMode = mode == Constants.WARNING_SCREEN_MODE_APP_BLOCKER
        val isKeywordBlockerMode = mode == Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER

        val isSimpleMode = savedPreferencesLoader.getEnforcementMode() == "SIMPLE"
        val blockedFeature = intent.getStringExtra("blocked_by_feature") 
            ?: if (isFocusMode) "Focus Mode" else if (isKeywordBlockerMode) "Keyword Blocker" else if (isReelBlockerWarning) "Reels Blocker" else "App Blocker"

        val defaultMins = (warningScreenConfig.timeInterval / 60000).coerceAtLeast(2)
        val restoredMins = savedInstanceState?.getInt(KEY_SELECTED_MINS_VALUE, defaultMins) ?: defaultMins
        binding.minsPicker.minValue = 2
        binding.minsPicker.setValue(restoredMins)
        var isDialogCancelable = !isAppBlockerMode || isHomePressRequested || isFocusMode

        if (isSimpleMode) {
            binding.warningTitle.text = "Access Blocked"
            binding.warningMsg.text = "This content has been blocked permanently under Simple Mode by the $blockedFeature feature."
            binding.btnProceed.visibility = View.GONE
            binding.proceedSeconds.visibility = View.GONE
            binding.minsPicker.visibility = View.GONE
            isDialogCancelable = false
        } else if (isFocusMode) {
            binding.warningTitle.text = "Focus Session Active"
            binding.btnProceed.visibility = View.GONE
            binding.proceedSeconds.visibility = View.GONE
            binding.minsPicker.visibility = View.GONE
            isDialogCancelable = true
        } else {
            binding.warningTitle.text = when {
                isAppBlockerMode -> getString(R.string.warning_title_app_blocker)
                isKeywordBlockerMode -> "Keyword Blocked"
                mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER && intent.getStringExtra("blocked_by_feature") == "Website Blocker" -> "Website Blocked"
                else -> getString(R.string.warning_title_reels_blocker)
            }

            if (warningScreenConfig.isProceedDisabled) {
                binding.btnProceed.visibility = View.GONE
                binding.proceedSeconds.visibility = View.GONE
            } else {
                val defaultDelayMillis = (warningScreenConfig.proceedDelayInSecs * 1000L).coerceAtLeast(0L)
                remainingProceedMillis = savedInstanceState?.getLong(KEY_REMAINING_PROCEED_MILLIS, defaultDelayMillis) ?: defaultDelayMillis
                isProceedTimerFinished = savedInstanceState?.getBoolean(KEY_PROCEED_TIMER_FINISHED, false) ?: false

                if (isProceedTimerFinished || remainingProceedMillis <= 0L) {
                    remainingProceedMillis = 0L
                    isProceedTimerFinished = true
                    binding.btnProceed.isEnabled = true
                    if (warningScreenConfig.isDynamicIntervalSettingAllowed) {
                        binding.minsPicker.visibility = View.VISIBLE
                    }
                    binding.btnProceed.setText(R.string.proceed)
                    binding.proceedSeconds.visibility = View.GONE
                } else {
                    binding.proceedSeconds.text = getString(R.string.proceed_in, remainingProceedMillis / 1000)
                    proceedTimer = object : CountDownTimer(remainingProceedMillis, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            remainingProceedMillis = millisUntilFinished
                            binding.proceedSeconds.text =
                                getString(R.string.proceed_in, millisUntilFinished / 1000)
                        }

                        override fun onFinish() {
                            remainingProceedMillis = 0L
                            isProceedTimerFinished = true
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
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .setCancelable(isDialogCancelable)
            .setOnCancelListener {
                if (mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER || isAppBlockerMode || isFocusMode) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                }
                finish()
            }
            .show()

        val fallbackMessage = when {
            isFocusMode -> "This app is restricted during your active Focus Session."
            isAppBlockerMode -> getString(R.string.warning_default_message_app)
            isKeywordBlockerMode -> "Content containing a blocked keyword was detected."
            mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER && intent.getStringExtra("blocked_by_feature") == "Website Blocker" -> "Access to this website is restricted."
            else -> getString(R.string.warning_default_message_reels)
        }
        val configuredMessage = warningScreenConfig.message.trim()

        if (isSimpleMode) {
            binding.btnCancel.text = "Go Home"
        } else if (isFocusMode) {
            binding.warningMsg.text = if (configuredMessage.isNotEmpty()) configuredMessage else fallbackMessage
            binding.btnCancel.text = "Return Home"
        } else {
            binding.warningMsg.text = if (configuredMessage.isNotEmpty()) configuredMessage else fallbackMessage
            binding.minsPicker.setValue(restoredMins)
            binding.btnCancel.text = if (isAppBlockerMode || isHomePressRequested || mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER) {
                getString(R.string.warning_cancel_go_home)
            } else {
                getString(R.string.warning_cancel_stay_safe)
            }
        }

        binding.btnCancel.setOnClickListener {
            if (isSimpleMode || isAppBlockerMode || isHomePressRequested || isFocusMode || mode == Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_REMAINING_PROCEED_MILLIS, remainingProceedMillis)
        outState.putBoolean(KEY_PROCEED_TIMER_FINISHED, isProceedTimerFinished)
        dialogBinding?.minsPicker?.let { outState.putInt(KEY_SELECTED_MINS_VALUE, it.getValue()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        proceedTimer?.cancel()
        dialog?.dismiss()  // Ensure dialog is dismissed before activity is destroyed
        dialogBinding = null
    }

    private fun sendRefreshRequest(id: String, action: String, time: Int) {
        val intent = Intent(action).setPackage(packageName)
        intent.putExtra("result_id", id)
        intent.putExtra("selected_time", time * 60_000)
        sendBroadcast(intent)
    }
}