package com.alhaq.amnishield.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.AppShortcut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.components.bounceClick

import com.alhaq.amnishield.ui.components.PinPromptDialog

@Composable
fun AdvancedScreen(
    state: AmniShieldState,
    onNavigateToAppBlocker: () -> Unit,
    onNavigateToKeywordBlocker: () -> Unit,
    onNavigateToWebBlocker: () -> Unit,
    onNavigateToReelsBlocker: () -> Unit,
    onNavigateToAntiUninstall: () -> Unit,
    onNavigateToUsageTracker: () -> Unit,
    onNavigateToPremium: () -> Unit,
    onTogglePinSecurity: (Boolean, String) -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
    onToggleBypassPinLock: (Boolean) -> Unit = {},
    onNavigateToRoadmap: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    onUpdatePinResetCooldown: (Int) -> Unit = {},
    onUpdateEmergencyCooldown: (Int) -> Unit = {}
) {
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showPinResetDialog by remember { mutableStateOf(false) }
    var showPinCooldownDialog by remember { mutableStateOf(false) }
    var showEmergencyCooldownDialog by remember { mutableStateOf(false) }

    if (showPinSetupDialog) {
        PinPromptDialog(
            isSettingUp = true,
            onDismiss = { showPinSetupDialog = false },
            onPinSuccess = { pin ->
                showPinSetupDialog = false
                onTogglePinSecurity(true, pin)
            }
        )
    }

    if (showPinVerifyDialog) {
        PinPromptDialog(
            correctPin = state.profilePin,
            title = "Verify Existing PIN",
            subtitle = "Enter current PIN to disable PIN security",
            allowForgotPin = true,
            onDismiss = { showPinVerifyDialog = false },
            onPinSuccess = {
                showPinVerifyDialog = false
                onTogglePinSecurity(false, "")
            },
            onPinResetCompleted = { newPin ->
                showPinVerifyDialog = false
                onTogglePinSecurity(true, newPin)
            }
        )
    }

    if (showPinResetDialog) {
        PinPromptDialog(
            correctPin = state.profilePin,
            title = "Reset Security PIN",
            subtitle = "Initiate delayed PIN reset sequence",
            allowForgotPin = true,
            onDismiss = { showPinResetDialog = false },
            onPinSuccess = { newPin ->
                showPinResetDialog = false
                onTogglePinSecurity(true, newPin)
            },
            onPinResetCompleted = { newPin ->
                showPinResetDialog = false
                onTogglePinSecurity(true, newPin)
            }
        )
    }

    if (showPinCooldownDialog) {
        CooldownSelectionDialog(
            title = "PIN Reset Cooldown",
            subtitle = "Enforce a minimum waiting delay before a forgotten PIN can be reset. Hard floor: 5 minutes.",
            selectedMinutes = state.pinResetCooldownMinutes,
            onDismiss = { showPinCooldownDialog = false },
            onSelectMinutes = { mins ->
                showPinCooldownDialog = false
                onUpdatePinResetCooldown(mins)
            }
        )
    }

    if (showEmergencyCooldownDialog) {
        CooldownSelectionDialog(
            title = "Emergency Access Cooldown",
            subtitle = "Enforce a minimum emergency delay before protection can be overridden in Timed Mode. Hard floor: 5 minutes.",
            selectedMinutes = state.emergencyAccessCooldownMinutes,
            onDismiss = { showEmergencyCooldownDialog = false },
            onSelectMinutes = { mins ->
                showEmergencyCooldownDialog = false
                onUpdateEmergencyCooldown(mins)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        if (state.isAdvancedMode) {
            // Section: Core Protection
            item {
                Text(
                    text = "CORE PROTECTION",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        AdvancedItemRow(
                            icon = Icons.Outlined.Lock,
                            title = "App Blocker",
                            summary = "Blocked apps and categories",
                            statusText = if (state.isAppBlockerEnabled) "ON" else "OFF",
                            onChecked = onNavigateToAppBlocker,
                            iconColor = Color(0xFF6366F1)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        AdvancedItemRow(
                            icon = Icons.AutoMirrored.Outlined.Label,
                            title = "Keyword Blocker",
                            summary = "Keywords and adult content packs",
                            statusText = "${state.keywords.size} keywords",
                            onChecked = onNavigateToKeywordBlocker,
                            iconColor = Color(0xFFEF4444)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        AdvancedItemRow(
                            icon = Icons.Outlined.PublicOff,
                            title = "Website/URL Blocker",
                            summary = "Block websites and custom URLs",
                            statusText = if (state.isWebFilterEnabled) "ON" else "OFF",
                            onChecked = onNavigateToWebBlocker,
                            iconColor = Color(0xFFEC4899)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        AdvancedItemRow(
                            icon = Icons.Outlined.VideoLibrary,
                            title = "Reels Blocker",
                            summary = "Block short-form video algorithms",
                            statusText = if (state.isReelsBlockerEnabled) "ON" else "OFF",
                            onChecked = onNavigateToReelsBlocker,
                            iconColor = Color(0xFFF43F5E)
                        )
                    }
                }
            }
        }

        // Section: System Settings
        item {
            Text(
                text = "SYSTEM SETTINGS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    AdvancedItemRow(
                        icon = Icons.Outlined.Security,
                        title = "Anti-Uninstall",
                        summary = "Prevent app uninstallation",
                        statusText = if (state.isAntiUninstallEnabled) "ON" else "OFF",
                        onChecked = onNavigateToAntiUninstall,
                        iconColor = Color(0xFF10B981)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    AdvancedItemRow(
                        icon = Icons.Outlined.Analytics,
                        title = "Usage Tracker",
                        summary = "Track detailed screen time data",
                        statusText = if (state.isUsageTrackerEnabled) "ON" else "OFF",
                        onChecked = onNavigateToUsageTracker,
                        iconColor = Color(0xFF0EA5E9)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    AdvancedItemRow(
                        icon = Icons.Outlined.Star,
                        title = "Premium Features",
                        summary = if (state.isPremiumUser) "Premium Active" else "Upgrade to Premium",
                        statusText = if (state.isPremiumUser) "ACTIVE" else "GET",
                        onChecked = onNavigateToPremium,
                        iconColor = Color(0xFFF59E0B)
                    )
                }
            }
        }

        // Section: PIN Security
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "PIN SECURITY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    // Row 1: Enable PIN Lock
                    AdvancedSwitchRow(
                        icon = Icons.Outlined.Lock,
                        title = "Enable PIN Security",
                        summary = "Protect blockers with a 4-digit PIN",
                        checked = state.isPinProtectionEnabled,
                        onCheckedChange = { checked ->
                            if (!state.isPremiumUser) {
                                onNavigateToPremium()
                            } else {
                                if (checked) {
                                    showPinSetupDialog = true
                                } else {
                                    showPinVerifyDialog = true
                                }
                            }
                        },
                        iconColor = Color(0xFF8B5CF6)
                    )

                    if (state.isPinProtectionEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Row 2: App Lock
                        AdvancedSwitchRow(
                            icon = Icons.Outlined.AppShortcut,
                            title = "App Lock",
                            summary = "Require PIN to open AmniShield",
                            checked = state.isAppLockEnabled,
                            onCheckedChange = { checked ->
                                if (!state.isPremiumUser) {
                                    onNavigateToPremium()
                                } else {
                                    onToggleAppLock(checked)
                                }
                            },
                            iconColor = Color(0xFF10B981)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Row 3: Bypass PIN Lock
                        AdvancedSwitchRow(
                            icon = Icons.Outlined.Security,
                            title = "Bypass PIN Lock",
                            summary = "Require PIN to modify settings while active",
                            checked = state.isBypassPinLockEnabled,
                            onCheckedChange = { checked ->
                                if (!state.isPremiumUser) {
                                    onNavigateToPremium()
                                } else {
                                    onToggleBypassPinLock(checked)
                                }
                            },
                            iconColor = Color(0xFFF59E0B)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Row 4: PIN Reset Cooldown
                        AdvancedItemRow(
                            icon = Icons.Outlined.Timer,
                            title = "PIN Reset Cooldown",
                            summary = "Minimum delay before forgotten PIN can be reset",
                            statusText = "${state.pinResetCooldownMinutes} mins",
                            onChecked = { showPinCooldownDialog = true },
                            iconColor = Color(0xFF6366F1)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Row 5: Emergency Access Cooldown
                        AdvancedItemRow(
                            icon = Icons.Outlined.HourglassTop,
                            title = "Emergency Access Cooldown",
                            summary = "Delay before Timed Mode emergency override unlocks",
                            statusText = "${state.emergencyAccessCooldownMinutes} mins",
                            onChecked = { showEmergencyCooldownDialog = true },
                            iconColor = Color(0xFFEF4444)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Row 6: Reset PIN Action
                        AdvancedItemRow(
                            icon = Icons.Outlined.LockReset,
                            title = "Reset PIN",
                            summary = "Trigger delayed reset sequence if PIN is forgotten",
                            statusText = "RESET",
                            onChecked = { showPinResetDialog = true },
                            iconColor = Color(0xFFEC4899)
                        )
                    }
                }
            }
        }

        // Section: Ecosystem & Community Initiative
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "COMMUNITY & VISION",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column {
                    AdvancedItemRow(
                        icon = Icons.Outlined.Explore,
                        title = "Roadmap & Vision",
                        summary = "Upcoming features, AI screen filter & milestones",
                        statusText = "VIEW",
                        onChecked = onNavigateToRoadmap,
                        iconColor = Color(0xFF6366F1)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    AdvancedItemRow(
                        icon = Icons.Outlined.VolunteerActivism,
                        title = "Support Amni Initiative",
                        summary = "Support open-source privacy and digital wellness",
                        statusText = "SUPPORT",
                        onChecked = onNavigateToSupport,
                        iconColor = Color(0xFF10B981)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AdvancedItemRow(
    icon: ImageVector,
    title: String,
    summary: String,
    statusText: String,
    onChecked: () -> Unit,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onChecked() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AdvancedSwitchRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupVerifyDialog(
    isSettingUp: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pinText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = if (isSettingUp) "Setup 4-Digit PIN" else "Verify Existing PIN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSettingUp) "Set a PIN code to secure your blockers and app access." else "Enter your current PIN to disable PIN security.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) { index ->
                        val hasChar = index < pinText.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasChar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                
                if (errorText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Clear", "0", "Delete")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    buttons.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            row.forEach { char ->
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (char == "Clear" || char == "Delete") Color.Transparent
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable {
                                            when (char) {
                                                "Clear" -> pinText = ""
                                                "Delete" -> {
                                                    if (pinText.isNotEmpty()) {
                                                        pinText = pinText.substring(0, pinText.length - 1)
                                                    }
                                                }
                                                else -> {
                                                    if (pinText.length < 4) {
                                                        pinText += char
                                                        errorText = ""
                                                        if (pinText.length == 4) {
                                                            onConfirm(pinText)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        style = if (char == "Clear" || char == "Delete") MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (char == "Clear" || char == "Delete") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CooldownSelectionDialog(
    title: String,
    subtitle: String,
    selectedMinutes: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { minutes ->
                    val isSelected = selectedMinutes == minutes
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectMinutes(minutes) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$minutes minutes" + if (minutes == 5) " (Default)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectMinutes(minutes) }
                            )
                        }
                    }
                }
            }
        }
    )
}
