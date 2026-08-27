package com.alhaq.amnishield.utils

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.alhaq.amnishield.R
import com.alhaq.amnishield.databinding.DialogAccessibilityDisclosureBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Google Play Policy Compliant Prominent Disclosure Dialog for AccessibilityService API.
 *
 * Requirements met:
 * 1. Must be shown before requesting the permission in settings.
 * 2. Requires affirmative user action via explicit two buttons (Agree & Enable vs Decline).
 * 3. Does not interpret navigation away / touch outside as consent.
 * 4. Transparently explains what data is observed (URLs, apps, keywords), why (blocking features),
 *    and guarantees 100% on-device local zero-knowledge privacy.
 */
object AccessibilityDisclosureDialog {

    /**
     * Show the prominent disclosure dialog with explicit Agree & Enable and Deny options.
     * Guaranteed single-screen display with zero scrolling required.
     */
    fun show(
        context: Context,
        onAgree: () -> Unit,
        onDecline: () -> Unit = {}
    ): AlertDialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return null
        }

        val inflater = LayoutInflater.from(context)
        val binding = DialogAccessibilityDisclosureBinding.inflate(inflater)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setCancelable(true)
            .setOnCancelListener {
                onDecline()
            }
            .create()

        // Positive Button: "Agree & Enable" (Only action that proceeds to Settings)
        binding.btnAgree.setOnClickListener {
            dialog.dismiss()
            onAgree()
        }

        // Negative Button: "Deny" (Dismisses dialog and stays in-app)
        binding.btnDecline.setOnClickListener {
            dialog.dismiss()
            onDecline()
        }

        dialog.show()
        return dialog
    }

    /**
     * Helper to check if the accessibility service is enabled, and if not, show the prominent disclosure
     * dialog before navigating the user to Android's accessibility settings.
     */
    fun showAndRequestPermission(
        activity: Activity,
        serviceClass: Class<out AccessibilityService> = AmniShieldAccessibilityService::class.java,
        onAlreadyEnabled: () -> Unit = {},
        onDecline: () -> Unit = {}
    ) {
        if (AccessibilityUtils.isAccessibilityServiceEnabled(activity, serviceClass)) {
            onAlreadyEnabled()
            return
        }

        show(
            context = activity,
            onAgree = {
                openAccessibilitySettings(activity, serviceClass)
            },
            onDecline = onDecline
        )
    }

    /**
     * Open Android Accessibility Settings with target component highlighting where supported.
     */
    fun openAccessibilitySettings(
        activity: Activity,
        serviceClass: Class<out AccessibilityService> = AmniShieldAccessibilityService::class.java
    ) {
        Toast.makeText(
            activity,
            activity.getString(R.string.find_amnishield_and_press_enable),
            Toast.LENGTH_LONG
        ).show()

        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                val componentName = ComponentName(activity, serviceClass)
                putExtra(":settings:fragment_args_key", componentName.flattenToString())
                val bundle = Bundle().apply {
                    putString(":settings:fragment_args_key", componentName.flattenToString())
                }
                putExtra(":settings:show_fragment_args", bundle)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (fallbackError: Exception) {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(appSettingsIntent)
            }
        }
    }
}
