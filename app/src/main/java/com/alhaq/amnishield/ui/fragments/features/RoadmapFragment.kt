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
import com.alhaq.amnishield.Constants
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_support_hub, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.support_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.close, null)
            .create()

        dialogView.findViewById<View>(R.id.card_initiative_hub)?.setOnClickListener {
            openUrl(Constants.ALHAQ_INITIATIVE_DONATE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_sponsors_initiative)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_INITIATIVE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_sponsors_developer)?.setOnClickListener {
            openUrl(Constants.GITHUB_SPONSORS_PERSONAL_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_kofi)?.setOnClickListener {
            openUrl(Constants.KOFI_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_buymeacoffee)?.setOnClickListener {
            openUrl(Constants.BUY_ME_A_COFFEE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_patreon)?.setOnClickListener {
            openUrl(Constants.PATREON_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_studio_site)?.setOnClickListener {
            openUrl(Constants.ALHAQ_STUDIO_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_amnishield_website)?.setOnClickListener {
            openUrl(Constants.AMNISHIELD_WEBSITE_URL)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.card_pro_pass)?.setOnClickListener {
            val intent = Intent(requireContext(), com.alhaq.amnishield.ui.activity.FragmentActivity::class.java).apply {
                putExtra("feature_type", "premium_features")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
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
