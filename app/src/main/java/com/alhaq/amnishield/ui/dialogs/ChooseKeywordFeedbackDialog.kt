package com.alhaq.amnishield.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogKeywordFeedbackStyleBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.AmniSpaceActivity
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class ChooseKeywordFeedbackDialog(
    savedPreferencesLoader: SavedPreferencesLoader,
    private val onModeChanged: ((String) -> Unit)? = null
) : BaseDialog(savedPreferencesLoader) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogKeywordFeedbackStyleBinding.inflate(layoutInflater)
        val currentMode = savedPreferencesLoader?.getKeywordBlockerFeedbackMode()
            ?: Constants.KEYWORD_FEEDBACK_AMNISPACE

        when (currentMode) {
            Constants.KEYWORD_FEEDBACK_WARNING_SCREEN -> binding.radioWarningScreen.isChecked = true
            Constants.KEYWORD_FEEDBACK_SILENT -> binding.radioSilent.isChecked = true
            else -> binding.radioAmnispace.isChecked = true
        }

        binding.btnPreviewAmnispace.setOnClickListener {
            val intent = Intent(requireContext(), AmniSpaceActivity::class.java).apply {
                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Keyword Blocker Preview")
                putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 5)
            }
            startActivity(intent)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val selectedMode = when (binding.rgFeedbackMode.checkedRadioButtonId) {
                    R.id.radio_warning_screen -> Constants.KEYWORD_FEEDBACK_WARNING_SCREEN
                    R.id.radio_silent -> Constants.KEYWORD_FEEDBACK_SILENT
                    else -> Constants.KEYWORD_FEEDBACK_AMNISPACE
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
