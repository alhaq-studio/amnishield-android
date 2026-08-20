package com.alhaq.amnshield.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.R
import com.alhaq.amnshield.databinding.DialogKeywordFeedbackStyleBinding
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.utils.SavedPreferencesLoader

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
            val overlayManager = com.alhaq.amnshield.ui.overlay.HandGestureOverlayManager(requireContext())
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
                sendRefreshRequest(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}
