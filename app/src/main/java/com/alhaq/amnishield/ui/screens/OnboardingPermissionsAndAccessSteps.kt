package com.alhaq.amnishield.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.BuildConfig
import com.alhaq.amnishield.data.sync.SupabaseRest
import com.alhaq.amnishield.premium.PaymentManager
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.utils.AccessibilityDisclosureComposableDialog
import com.alhaq.amnishield.utils.AccessibilityDisclosureDialog
import com.alhaq.amnishield.utils.ProminentPermissionDisclosureDialog
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingPinAndBarriersStep(
    pinDraft: OnboardingPinDraft,
    alreadyHasPin: Boolean,
    onUpdateDraft: ((OnboardingPinDraft) -> OnboardingPinDraft) -> Unit
) {
    var showPinChars by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Establish mindful barriers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (alreadyHasPin) {
                    "A protection PIN is already configured. You can update your 4-digit barrier below or skip to keep it."
                } else {
                    "An intentional friction barrier prevents impulsive disarming during deep focus intervals."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        // Master Shield PIN input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Master Shield PIN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "4 digits",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = pinDraft.pin,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(4)
                    onUpdateDraft { it.copy(pin = filtered) }
                },
                placeholder = { Text("Enter 4-digit PIN") },
                singleLine = true,
                visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    IconButton(onClick = { showPinChars = !showPinChars }) {
                        Icon(
                            imageVector = if (showPinChars) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle PIN visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_pin_input")
            )
        }

        // Confirm Master PIN input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirm Master PIN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (pinDraft.isMatching()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Matches",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (pinDraft.hasMismatchError()) {
                    Text(
                        text = "PINs do not match",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = pinDraft.confirmPin,
                onValueChange = { raw ->
                    val filtered = raw.filter { it.isDigit() }.take(4)
                    onUpdateDraft { it.copy(confirmPin = filtered) }
                },
                placeholder = { Text("Re-enter 4-digit PIN") },
                singleLine = true,
                isError = pinDraft.hasMismatchError(),
                visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                trailingIcon = {
                    if (pinDraft.isMatching()) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Matches",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_pin_confirm_input")
            )
        }

        // Bypass Cooldown Delay chips (real minimum 2 minutes -> 2m / 5m / 15m / 30m)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Bypass Cooldown Delay",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Mandatory waiting delay before a forgotten PIN reset or override can apply.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val cooldownOptions = listOf(2 to "2m", 5 to "5m", 15 to "15m", 30 to "30m")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cooldownOptions.forEach { (mins, label) ->
                    val isSelected = pinDraft.cooldownMinutes == mins
                    Surface(
                        onClick = { onUpdateDraft { it.copy(cooldownMinutes = mins) } },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("onboarding_cooldown_${mins}m")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Settings & Tamper Lock toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onUpdateDraft { it.copy(lockSettingsWithPin = !it.lockSettingsWithPin) }
                }
                .padding(vertical = 4.dp)
                .testTag("onboarding_lock_settings_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lock Blocker Rules & Settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Require Master PIN before modifying rules or disabling protection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = pinDraft.lockSettingsWithPin,
                onCheckedChange = { checked ->
                    onUpdateDraft { it.copy(lockSettingsWithPin = checked) }
                }
            )
        }
    }
}

@Composable
fun OnboardingPermissionsStep(
    isAccessibilityGranted: Boolean,
    isOverlayGranted: Boolean,
    isUsageGranted: Boolean,
    isBatteryGranted: Boolean,
    isNotifGranted: Boolean,
    onRefreshPermissions: () -> Unit,
    onBackupRestored: () -> Unit = onRefreshPermissions
) {
    val context = LocalContext.current
    var showOemGuide by remember { mutableStateOf(false) }

    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showOverlayDisclosure by remember { mutableStateOf(false) }
    var showUsageDisclosure by remember { mutableStateOf(false) }
    var showBatteryDisclosure by remember { mutableStateOf(false) }

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onRefreshPermissions()
    }
    val usageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onRefreshPermissions()
    }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onRefreshPermissions()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onRefreshPermissions()
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.data?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            ZipUtils.unzipSharedPreferencesFromUri(context, uri)
            onBackupRestored()
            Toast.makeText(context, "Backup restored successfully.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero visual emblem matching grant_permissions inspiration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "Grant Device Access",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "AmniShield needs key system permissions to shield deep focus and gently intercept endless loops.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // 1. Accessibility Service Card + Collapsible Android 13+ Guide
        Surface(
            onClick = {
                if (!isAccessibilityGranted) {
                    showAccessibilityDisclosure = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_perm_accessibility"),
            shape = RoundedCornerShape(18.dp),
            color = if (isAccessibilityGranted) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    PermissionIconBubble(
                        icon = Icons.Outlined.Shield,
                        isGranted = isAccessibilityGranted
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Accessibility Service",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            PermissionRequirementPill(label = "Required", isRequired = true)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Detects active apps, website URLs, and short-video reels to enforce your blocks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    PermissionActionPill(
                        isGranted = isAccessibilityGranted,
                        isRequired = true,
                        onClick = {
                            if (!isAccessibilityGranted) {
                                showAccessibilityDisclosure = true
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showOemGuide = !showOemGuide }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to enable & fix 'Restricted Setting' (Android 13+)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showOemGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = showOemGuide) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "1. Tap Grant and select 'Agree & Enable' in the disclosure dialog.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "2. Open 'Installed Apps' or 'Downloaded Services' -> AmniShield -> turn ON.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "3. If Android shows 'Restricted Setting': Open Settings -> Apps -> AmniShield -> top-right menu -> 'Allow restricted settings'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Display Over Other Apps
        OnboardingPermissionItemCard(
            title = "Display Over Apps",
            description = "Presents calm breathing screens and shields before distraction starts.",
            icon = Icons.Outlined.Layers,
            isGranted = isOverlayGranted,
            requirementLabel = "Required",
            isRequired = true,
            testTag = "onboarding_perm_overlay",
            onClick = {
                if (!isOverlayGranted) {
                    showOverlayDisclosure = true
                }
            }
        )

        // 3. App Usage Access
        OnboardingPermissionItemCard(
            title = "Usage Access",
            description = "Measures intentional engagement trends and launch limits privately on device.",
            icon = Icons.Outlined.BarChart,
            isGranted = isUsageGranted,
            requirementLabel = "Recommended",
            isRequired = false,
            testTag = "onboarding_perm_usage",
            onClick = {
                if (!isUsageGranted) {
                    showUsageDisclosure = true
                }
            }
        )

        // 4. Unrestricted Background
        OnboardingPermissionItemCard(
            title = "Unrestricted Background",
            description = "Prevents battery saver from pausing active schedules and focus sessions.",
            icon = Icons.Outlined.BatteryChargingFull,
            isGranted = isBatteryGranted,
            requirementLabel = "Recommended",
            isRequired = false,
            testTag = "onboarding_perm_battery",
            onClick = {
                if (!isBatteryGranted) {
                    showBatteryDisclosure = true
                }
            }
        )

        // 5. Notification Alerts
        OnboardingPermissionItemCard(
            title = "Notification Alerts",
            description = "Sends daily goal recaps and active shield session reminders.",
            icon = Icons.Outlined.Notifications,
            isGranted = isNotifGranted,
            requirementLabel = "Optional",
            isRequired = false,
            testTag = "onboarding_perm_notifications",
            onClick = {
                if (!isNotifGranted) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
            }
        )

        // Privacy Guarantee Card (pure tonal depth)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "100% On-Device Guardian",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "AmniShield processes all app stats strictly locally. Your screen time, text, and browsing history never depart this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Subtle Restore Backup pill
        TextButton(
            onClick = { ZipUtils.showRestorePicker(restoreLauncher) },
            modifier = Modifier.testTag("onboarding_restore_backup_button")
        ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Restore Configuration from Backup (.zip)")
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureComposableDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                AccessibilityDisclosureDialog.openAccessibilitySettings(context)
            },
            onDecline = {
                showAccessibilityDisclosure = false
            }
        )
    }

    if (showOverlayDisclosure) {
        ProminentPermissionDisclosureDialog(
            icon = Icons.Outlined.Layers,
            title = "Display Over Other Apps",
            introText = "AmniShield requires the Display Over Other Apps (Overlay) permission to render distraction shields and mindful pause timers when a restricted app is opened:",
            bulletPoints = listOf(
                "Displays instant full-screen distraction shields over blocked apps.",
                "Shows mindful breathing exercises and pause countdown timers.",
                "Renders floating focus session progress indicators."
            ),
            privacyGuaranteeText = "100% On-Device Privacy: Overlay screens are rendered strictly locally and never capture or transmit screen pixels.",
            confirmButtonText = "Agree & Configure",
            declineButtonText = "Decline",
            onConfirm = {
                showOverlayDisclosure = false
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    } catch (_: Exception) {}
                }
            },
            onDecline = { showOverlayDisclosure = false }
        )
    }

    if (showUsageDisclosure) {
        ProminentPermissionDisclosureDialog(
            icon = Icons.Outlined.BarChart,
            title = "App Usage Access",
            introText = "AmniShield requires App Usage Access (UsageStatsManager) to calculate screen time, count daily app opens, and enforce your daily screen time goals:",
            bulletPoints = listOf(
                "Tracks daily screen time and compares progress against your personal daily goal.",
                "Counts app launches and short-video reel sessions for habit insights.",
                "Enforces scheduled screen-time thresholds and app launch limits."
            ),
            privacyGuaranteeText = "100% On-Device Privacy: All usage statistics remain in your local device database and are never shared with third parties.",
            confirmButtonText = "Agree & Configure",
            declineButtonText = "Decline",
            onConfirm = {
                showUsageDisclosure = false
                try {
                    val intent = Intent(
                        Settings.ACTION_USAGE_ACCESS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                    usageLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {}
                }
            },
            onDecline = { showUsageDisclosure = false }
        )
    }

    if (showBatteryDisclosure) {
        ProminentPermissionDisclosureDialog(
            icon = Icons.Outlined.BatteryChargingFull,
            title = "Unrestricted Background",
            introText = "AmniShield requests exemption from aggressive battery optimizations so your focus sessions and scheduled blocks stay reliable:",
            bulletPoints = listOf(
                "Prevents Android OS from pausing active focus mode timers.",
                "Ensures scheduled blocks and daily goal reminders fire on time.",
                "Maintains lightweight local protection with minimal battery impact."
            ),
            privacyGuaranteeText = "Zero Background Telemetry: Unrestricted background execution is used solely for local timer and blocker enforcement.",
            confirmButtonText = "Agree & Configure",
            declineButtonText = "Decline",
            onConfirm = {
                showBatteryDisclosure = false
                try {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    batteryLauncher.launch(intent)
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {}
                }
            },
            onDecline = { showBatteryDisclosure = false }
        )
    }
}

@Composable
private fun OnboardingPermissionItemCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    requirementLabel: String,
    isRequired: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        color = if (isGranted) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            PermissionIconBubble(icon = icon, isGranted = isGranted)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    PermissionRequirementPill(label = requirementLabel, isRequired = isRequired)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            PermissionActionPill(
                isGranted = isGranted,
                isRequired = isRequired,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun PermissionIconBubble(icon: ImageVector, isGranted: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isGranted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PermissionRequirementPill(label: String, isRequired: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isRequired) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isRequired) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun PermissionActionPill(
    isGranted: Boolean,
    isRequired: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isGranted -> MaterialTheme.colorScheme.secondaryContainer
        isRequired -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        isGranted -> MaterialTheme.colorScheme.primary
        isRequired -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isGranted) "Allowed" else "Grant",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (isGranted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun OnboardingReadyAndSupportStep(
    userName: String,
    dailyGoalMinutes: Int,
    shortAppId: String,
    fullAppId: String,
    quickShieldSummary: String,
    isPinEnabled: Boolean,
    cooldownMinutes: Int,
    appTrackerEnabled: Boolean = true,
    webTrackerEnabled: Boolean = true,
    reelsTrackerEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember { SavedPreferencesLoader(context) }
    val premiumManager = remember { PremiumManager.getInstance(context.applicationContext) }

    var userType by remember { mutableStateOf(premiumManager.getUserType()) }
    var showCommunityDialog by remember { mutableStateOf(false) }
    var showProAndLicenseSection by remember { mutableStateOf(false) }
    var licenseInput by remember { mutableStateOf("") }
    var copiedAppIdJustNow by remember { mutableStateOf(false) }

    var communityEmail by remember { mutableStateOf(loader.getCompassionateAccessEmail()) }
    var communityReason by remember { mutableStateOf("") }
    var isSubmittingCommunity by remember { mutableStateOf(false) }

    val goalFormatted = "${formatDailyGoalDuration(dailyGoalMinutes)}/day"
    val activeTrackersList = buildList {
        if (appTrackerEnabled) add("Apps")
        if (webTrackerEnabled) add("Web")
        if (reelsTrackerEnabled) add("Reels")
    }
    val trackersSummaryText = if (activeTrackersList.isEmpty()) "Paused" else activeTrackersList.joinToString(", ")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Serene concentric halo shield hero matching setup_complete inspiration
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(112.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        OnboardingStatusPill(text = "Sanctuary Ready", showDot = true)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "AmniShield is ready, ${userName.ifBlank { "Guardian" }}.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Your digital sanctuary is configured and active locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Hairline-separated protection checklist inspired by setup_complete/screen.png
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_protection_summary_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SummaryCheckRow(
                    icon = Icons.Outlined.FilterAlt,
                    title = "Shields & Guards",
                    subtitle = quickShieldSummary
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                SummaryCheckRow(
                    icon = Icons.Outlined.Lock,
                    title = if (isPinEnabled) "Strict PIN Lock" else "PIN Friction",
                    subtitle = if (isPinEnabled) {
                        "Master PIN active ($cooldownMinutes min cooldown)"
                    } else {
                        "Optional (configure anytime in Settings)"
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                SummaryCheckRow(
                    icon = Icons.Outlined.Timer,
                    title = "Daily Goal & Trackers",
                    subtitle = "$goalFormatted - Trackers: $trackersSummaryText"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Device Privacy & App ID",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "100% On-Device - ID: $shortAppId",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("AmniShield App ID", fullAppId))
                            copiedAppIdJustNow = true
                            Toast.makeText(context, "App ID copied: $shortAppId", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (copiedAppIdJustNow) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy App ID",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (userType != PremiumManager.UserType.FREE) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Supporter / Pro Features Unlocked (${userType.name})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // Open-Source Community & Support Card (inspired by support_donate/screen.png)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VolunteerActivism,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Open-Source & Community-First",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Core blockers stay 100% free with zero ads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Need Pro tamper-locks or upcoming cloud sync but cannot afford it? Claim a free 1-Year Community Access pass anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Surface(
                    onClick = { showCommunityDialog = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("onboarding_community_access_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Request Free 1-Year Community Access",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Expandable Supporter Tiers & License Key
                Surface(
                    onClick = { showProAndLicenseSection = !showProAndLicenseSection },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_collapsible_pro_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Optional Supporter Tiers & License Key",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showProAndLicenseSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(visible = showProAndLicenseSection) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SupportTierChip(
                                modifier = Modifier.weight(1f),
                                title = "Monthly",
                                price = "$2.99",
                                highlighted = false,
                                onClick = {
                                    if (!BuildConfig.IS_PLAYSTORE) {
                                        PaymentManager.openStripeCheckout(context, plan = "monthly")
                                    } else {
                                        Toast.makeText(context, "Subscribe anytime from Profile -> Pro", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            SupportTierChip(
                                modifier = Modifier.weight(1f),
                                title = "Annual",
                                price = "$19.99",
                                highlighted = true,
                                onClick = {
                                    if (!BuildConfig.IS_PLAYSTORE) {
                                        PaymentManager.openStripeCheckout(context, plan = "annual")
                                    } else {
                                        Toast.makeText(context, "Subscribe anytime from Profile -> Pro", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            SupportTierChip(
                                modifier = Modifier.weight(1f),
                                title = "Lifetime",
                                price = "$49.99",
                                highlighted = false,
                                onClick = {
                                    if (!BuildConfig.IS_PLAYSTORE) {
                                        PaymentManager.openStripeCheckout(context, plan = "lifetime")
                                    } else {
                                        Toast.makeText(context, "Unlock Lifetime anytime from Profile -> Pro", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        OutlinedTextField(
                            value = licenseInput,
                            onValueChange = { licenseInput = it },
                            label = { Text("Paste Pro License Key") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Button(
                            onClick = {
                                val key = licenseInput.trim()
                                if (key.isNotEmpty() && premiumManager.redeemLicenseKey(key)) {
                                    userType = premiumManager.getUserType()
                                    licenseInput = ""
                                    Toast.makeText(context, "Pro License Activated!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Invalid or expired license key", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape
                        ) {
                            Text("Activate License Key", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showCommunityDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingCommunity) showCommunityDialog = false },
            icon = { Icon(Icons.Outlined.VolunteerActivism, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("1-Year Community Access", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your Username (${userName.ifBlank { "Guardian" }}) and App ID ($shortAppId) will be linked to your Community Access pass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = communityEmail,
                        onValueChange = { communityEmail = it },
                        label = { Text("Your Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = communityReason,
                        onValueChange = { communityReason = it },
                        label = { Text("Brief note (Student, seeker, financial need)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmittingCommunity,
                    onClick = {
                        val cleanEmail = communityEmail.trim()
                        if (cleanEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmittingCommunity = true
                        val resolvedName = userName.trim().ifEmpty { "Guardian" }
                        val now = System.currentTimeMillis()
                        val oneYearMs = 365L * 24 * 60 * 60 * 1000L
                        loader.saveCompassionateAccessGrant(
                            appId = fullAppId,
                            userName = resolvedName,
                            email = cleanEmail,
                            grantedAt = now,
                            expiresAt = now + oneYearMs,
                            status = "approved"
                        )
                        userType = premiumManager.getUserType()
                        scope.launch(Dispatchers.IO) {
                            try {
                                val rest = SupabaseRest()
                                rest.registerCommunityAccess(
                                    userName = resolvedName,
                                    email = cleanEmail,
                                    appId = fullAppId,
                                    deviceIdentifier = fullAppId
                                )
                                rest.syncUserIdentityWithAppId(
                                    context = context,
                                    userName = resolvedName,
                                    goalMinutes = dailyGoalMinutes
                                )
                            } catch (_: Exception) {}
                            withContext(Dispatchers.Main) {
                                isSubmittingCommunity = false
                                showCommunityDialog = false
                                Toast.makeText(context, "1-Year Community Access Activated.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text(if (isSubmittingCommunity) "Activating..." else "Activate Free Pass")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSubmittingCommunity,
                    onClick = { showCommunityDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryCheckRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SupportTierChip(
    modifier: Modifier = Modifier,
    title: String,
    price: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
