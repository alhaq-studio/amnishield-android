package com.alhaq.amnishield.ui.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogConfigTrackerBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader

class TweakUsageTracker(
    savedPreferencesLoader: SavedPreferencesLoader
) : BaseDialog(savedPreferencesLoader) {

    private lateinit var trackerPreferences: SharedPreferences

    @SuppressLint("ApplySharedPref")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogConfigurationTracker = DialogConfigTrackerBinding.inflate(layoutInflater)

        // Load tracker preferences
        trackerPreferences =
            requireContext().getSharedPreferences("config_tracker", Context.MODE_PRIVATE)
        dialogConfigurationTracker.cbTimeElapsed.isChecked =
            trackerPreferences.getBoolean("is_time_elapsed", false)

        // Build and display dialog
        return MaterialAlertDialogBuilder(requireContext())
            .setView(dialogConfigurationTracker.root)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->

                // Save updated settings
                with(trackerPreferences.edit()) {
                    putBoolean(
                        "is_time_elapsed",
                        dialogConfigurationTracker.cbTimeElapsed.isChecked
                    )
                    commit() // Apply changes immediately
                }

                // Send broadcast to refresh UsageTrackingService
                sendRefreshRequest(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}
