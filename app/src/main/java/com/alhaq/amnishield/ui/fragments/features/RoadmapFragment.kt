package com.alhaq.amnishield.ui.fragments.features

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.R

/**
 * Fragment displaying the AmniShield development roadmap, completed milestones,
 * active in-development features, and long-term ecosystem vision.
 */
class RoadmapFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_roadmap, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btnSupportInitiative)?.setOnClickListener {
            showSupportHubDialog()
        }
    }

    private fun showSupportHubDialog() {
        val options = arrayOf(
            getString(R.string.support_option_initiative_hub),
            getString(R.string.support_option_github_sponsors_personal),
            getString(R.string.support_option_github_sponsors_studio),
            getString(R.string.support_option_studio_site),
            getString(R.string.support_option_kofi),
            getString(R.string.support_option_pro_pass)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.support_dialog_title)
            .setMessage(R.string.support_dialog_message)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> openUrl("https://alhaq-initiative.org")
                    1 -> openUrl("https://github.com/sponsors/Afrasyaab-GH")
                    2 -> openUrl("https://github.com/sponsors/alhaq-studio")
                    3 -> openUrl("https://alhaq.uk")
                    4 -> openUrl("https://ko-fi.com/alhaq")
                    5 -> {
                        // Open Premium screen
                        val intent = Intent(requireContext(), com.alhaq.amnishield.ui.activity.FragmentActivity::class.java).apply {
                            putExtra("feature_type", "premium_features")
                        }
                        startActivity(intent)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}
