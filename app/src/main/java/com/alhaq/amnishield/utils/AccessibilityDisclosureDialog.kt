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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amnishield.R
import com.alhaq.amnishield.data.datastore.AmniShieldDataStore
import com.alhaq.amnishield.databinding.DialogAccessibilityDisclosureBinding
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Google Play Policy Compliant Prominent Disclosure Dialog for AccessibilityService API
 * and other sensitive permissions.
 *
 * Play Store Requirements strictly enforced:
 * 1. Standalone and preemptive: Shown immediately before redirecting to Settings.ACTION_ACCESSIBILITY_SETTINGS.
 * 2. Non-dismissible without explicit choice: Prevents dismissal via outside tap or back press.
 * 3. Guaranteed scrollability & zero clipping up to 200% font scale / zoomed displays / landscape:
 *    Pinned header + Scrollable body (`weight(1f, fill = false)`) + Sticky action footer.
 * 4. Explicit affirmative & negative consent ("Agree & Enable" / "Decline").
 * 5. Mandatory 4 disclosure points:
 *    - Explicitly names the AccessibilityService API ("Accessibility Service").
 *    - Identifies exact core features requiring it (App/URL blocker, Keyword filter, Reels/Shorts redirection).
 *    - States exact data accessed (window state change events, foreground package names, on-screen text/URLs).
 *    - States how data is handled (processed 100% locally on-device in real time; never stored, logged, or shared).
 */
object AccessibilityDisclosureDialog {

    /**
     * Show the prominent disclosure dialog with explicit Agree & Enable and Decline options.
     * Non-dismissible on outside tap or back press, with a scrollable body and sticky action buttons.
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
            .setCancelable(false)
            .create()

        dialog.setCanceledOnTouchOutside(false)

        // Positive Button: "Agree & Enable" (Only action that proceeds to Settings)
        binding.btnAgree.setOnClickListener {
            persistConsentDecision(context, accepted = true)
            dialog.dismiss()
            onAgree()
        }

        // Negative Button: "Decline" (Dismisses dialog and stays in-app)
        binding.btnDecline.setOnClickListener {
            persistConsentDecision(context, accepted = false)
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
        context: Context,
        serviceClass: Class<out AccessibilityService> = AmniShieldAccessibilityService::class.java
    ) {
        Toast.makeText(
            context,
            context.getString(R.string.find_amnishield_and_press_enable),
            Toast.LENGTH_LONG
        ).show()

        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                val componentName = ComponentName(context, serviceClass)
                putExtra(":settings:fragment_args_key", componentName.flattenToString())
                val bundle = Bundle().apply {
                    putString(":settings:fragment_args_key", componentName.flattenToString())
                }
                putExtra(":settings:show_fragment_args", bundle)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                })
            } catch (fallbackError: Exception) {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(appSettingsIntent)
            }
        }
    }

    internal fun persistConsentDecision(context: Context, accepted: Boolean) {
        runCatching {
            SavedPreferencesLoader(context).setAccessibilityDisclosureAccepted(accepted)
        }
        runCatching {
            val dataStore = AmniShieldDataStore.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { dataStore.setAccessibilityDisclosureAccepted(accepted) }
            }
        }
    }
}

/**
 * Jetpack Compose Google Play Compliant Prominent Disclosure Dialog for the AccessibilityService API.
 * Uses a 3-zone layout (Pinned Header + Scrollable Body + Sticky Action Buttons) and prevents
 * outside tap or back press dismissal so it never clips even at 200% font scaling.
 */
@Composable
fun AccessibilityDisclosureComposableDialog(
    onAgree: () -> Unit,
    onDecline: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ProminentPermissionDisclosureDialog(
        icon = Icons.Outlined.Shield,
        title = stringResource(id = R.string.accessibility_disclosure_title),
        introText = "AmniShield uses the Android AccessibilityService API (\"Accessibility Service\") to provide core digital wellness and distraction-blocking features:",
        sectionTitle = stringResource(id = R.string.accessibility_disclosure_usage_title),
        bulletPoints = listOf(
            "App & URL Blocker: Detects window state change events, active foreground app package names, and browser URLs to enforce your scheduled blocks, focus sessions, and launch limits.",
            "Keyword & Search Inspection: Reads on-screen text and search inputs to detect and block explicit content, adult keywords, and harmful websites.",
            "Navigation & Reel Redirection: Performs automated \"Back\" or \"Home\" gestures to safely redirect away from blocked apps and short-video feeds (Instagram Reels, YouTube Shorts, TikTok)."
        ),
        privacyGuaranteeText = "100% On-Device Data Privacy: All window events, package names, and on-screen text accessed via the AccessibilityService API are processed ephemerally in local device memory. Zero personal or sensitive user data is stored, logged, or transmitted over the network.",
        confirmButtonText = stringResource(id = R.string.accessibility_disclosure_agree),
        declineButtonText = "Decline",
        isNonDismissible = true,
        onConfirm = {
            AccessibilityDisclosureDialog.persistConsentDecision(context, accepted = true)
            onAgree()
        },
        onDecline = {
            AccessibilityDisclosureDialog.persistConsentDecision(context, accepted = false)
            onDecline()
        }
    )
}

/**
 * Unified, zoom-resilient 3-zone Prominent Permission Disclosure Dialog used across all permission requests
 * (Accessibility Service, Display Over Other Apps, App Usage Access, Unrestricted Background).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProminentPermissionDisclosureDialog(
    icon: ImageVector,
    title: String,
    introText: String,
    sectionTitle: String = "How AmniShield uses this permission:",
    bulletPoints: List<String>,
    privacyGuaranteeText: String,
    confirmButtonText: String = "Agree & Enable",
    declineButtonText: String = "Decline",
    isNonDismissible: Boolean = true,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!isNonDismissible) {
                onDecline()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isNonDismissible,
            dismissOnClickOutside = !isNonDismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
                .testTag("prominent_disclosure_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. Pinned Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // 2. Scrollable Body (weight(1f, fill = false) guarantees sticky footer never clips at 200% zoom)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = introText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    // Core Features & Data Accessed Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = sectionTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            bulletPoints.forEach { point ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Mandatory Privacy & Data Handling Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = privacyGuaranteeText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Sticky Action Buttons Footer (Adaptive FlowRow so buttons never clip at 200% font scale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minWidth = 110.dp, minHeight = 48.dp)
                            .testTag("disclosure_decline_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = declineButtonText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1.3f)
                            .defaultMinSize(minWidth = 140.dp, minHeight = 48.dp)
                            .testTag("disclosure_agree_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = confirmButtonText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
