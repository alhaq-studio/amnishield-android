package com.alhaq.amnishield.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogKeywordFeedbackStyleBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class ChooseKeywordFeedbackDialog(
    savedPreferencesLoader: SavedPreferencesLoader,
    private val onModeChanged: ((String) -> Unit)? = null
) : BaseDialog(savedPreferencesLoader) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogKeywordFeedbackStyleBinding.inflate(layoutInflater)
        val currentMode = savedPreferencesLoader?.getKeywordBlockerFeedbackMode()
            ?: Constants.KEYWORD_FEEDBACK_HAND_GESTURE

        when (currentMode) {
            Constants.KEYWORD_FEEDBACK_WARNING_SCREEN -> binding.radioWarningScreen.isChecked = true
            Constants.KEYWORD_FEEDBACK_SILENT -> binding.radioSilent.isChecked = true
            else -> binding.radioHandGesture.isChecked = true
        }

        binding.btnPreviewHandGesture.setOnClickListener {
            val overlayManager = com.alhaq.amnishield.ui.overlay.HandGestureOverlayManager(requireContext())
            overlayManager.showGestureOverlay(detectedKeyword = "gambling", isHomePress = false)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val selectedMode = when (binding.rgFeedbackMode.checkedRadioButtonId) {
                    R.id.radio_warning_screen -> Constants.KEYWORD_FEEDBACK_WARNING_SCREEN
                    R.id.radio_silent -> Constants.KEYWORD_FEEDBACK_SILENT
                    else -> Constants.KEYWORD_FEEDBACK_HAND_GESTURE
                }
                savedPreferencesLoader?.setKeywordBlockerFeedbackMode(selectedMode)
                onModeChanged?.invoke(selectedMode)
                sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}
