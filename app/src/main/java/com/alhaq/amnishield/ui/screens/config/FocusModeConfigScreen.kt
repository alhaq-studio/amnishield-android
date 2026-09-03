/**
 * ============================================================================
 * AmniShield UI - FocusModeConfigScreen (Material 3)
 * ============================================================================
 * Architecture: Central Configuration Hub for Global Focus Mode Settings
 * 
 * Responsibilities:
 * - Default Protection Strategy (Blacklist vs Strict Whitelist)
 * - Target Focus Apps Selection
 * - Global Whitelist / Always-Ignored Apps (Emergency & Essential tools)
 * - Session Strictness & Warning Screen Behavior (Mindful Pause vs Deep Focus Hard Lock)
 * - Home Widget & Quick Start Duration Presets (3 customizable presets)
 * - AutoFocus Schedules Navigation
 * ============================================================================
 */
package com.alhaq.amnishield.ui.screens.config

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.activity.SelectAppsActivity
import com.alhaq.amnishield.ui.components.bounceClick
import com.alhaq.amnishield.ui.fragments.BlocksManagerFragment
import com.alhaq.amnishield.ui.widgets.QuickFocusWidgetProvider
import com.alhaq.amnishield.utils.SavedPreferencesLoader

private const val TAG = "FocusModeConfigScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusModeConfigScreen(
    isServiceEnabled: Boolean,
    onEnableServiceClick: () -> Unit,
    onBack: () -> Unit,
    onSelectAppsClick: () -> Unit,
    onSelectAlwaysWhitelistedClick: () -> Unit,
    onConfigureWarning: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context) }

    var selectedAppsCount by remember { mutableStateOf(loader.getFocusModeSelectedApps().size) }
    var alwaysWhitelistedCount by remember { mutableStateOf(loader.getAlwaysWhitelistedApps().size) }
    var focusModeType by remember {
        val initialMode = loader.getFocusModeData().modeType
        mutableStateOf(if (initialMode == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED else Constants.FOCUS_MODE_BLOCK_SELECTED)
    }

    var strictnessMode by remember { mutableStateOf(loader.getFocusModeStrictness()) }
    var allowEarlyStop by remember { mutableStateOf(loader.isFocusModeEarlyStopAllowed()) }
    var interceptionStyle by remember { mutableStateOf(loader.getFocusModeInterceptionStyle()) }
    var amnispaceAppsCount by remember { mutableStateOf(loader.getAmniSpaceEssentialApps().size) }

    val initialPresets = remember { loader.getQuickFocusDurationPresets() }
    var preset1 by remember { mutableIntStateOf(initialPresets.first) }
    var preset2 by remember { mutableIntStateOf(initialPresets.second) }
    var preset3 by remember { mutableIntStateOf(initialPresets.third) }

    var showPresetDialog by remember { mutableStateOf(false) }
    var showWarningMessageDialog by remember { mutableStateOf(false) }
    var warningMessageInput by remember { mutableStateOf(loader.loadFocusModeWarningInfo().message) }

    val autoFocusRulesCount = remember {
        loader.loadAppBlockerScheduleRules()
            .filter {
                it.packageName.equals("FOCUS_MODE", ignoreCase = true) ||
                it.packageName.equals("focus_mode", ignoreCase = true)
            }
            .map { it.groupId ?: it.id }
            .distinct()
            .size
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(
                            text = "Focus Mode Settings",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Configure whitelist strategy & presets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Accessibility Service Banner if disabled
            if (!isServiceEnabled) {
                ServiceRequiredCard(onEnableClick = onEnableServiceClick)
            }

            // =========================================================================
            // CATEGORY 1: QUICK FOCUS CONFIGURATIONS
            // =========================================================================
            CategoryHeaderBanner(
                title = "QUICK FOCUS DEFAULTS",
                subtitle = "Configuration applied when starting instant Quick Focus sessions or tapping the Home Screen widget.",
                icon = Icons.Outlined.Timer
            )

            // 1. Protection Strategy (Blacklist vs Strict Whitelist)
            ConfigSectionCard(
                title = "DEFAULT PROTECTION STRATEGY",
                icon = Icons.Outlined.Shield,
                badge = "QUICK FOCUS"
            ) {
                val isBlockSelected = focusModeType == Constants.FOCUS_MODE_BLOCK_SELECTED
                ModeSelectionOptionCard(
                    title = "Block Selected Distractions (Default)",
                    description = "Only chosen distracting apps are blocked; all other work and system tools remain accessible.",
                    selected = isBlockSelected,
                    onClick = {
                        focusModeType = Constants.FOCUS_MODE_BLOCK_SELECTED
                        val existingData = loader.getFocusModeData()
                        loader.saveFocusModeData(existingData.copy(modeType = Constants.FOCUS_MODE_BLOCK_SELECTED))
                        broadcastFocusRefresh(context)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                val isBlockAllExcept = focusModeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
                ModeSelectionOptionCard(
                    title = "Strict Whitelist (Block All Except Allowed)",
                    description = "All apps are locked during focus mode EXCEPT explicitly chosen allowed apps and emergency tools.",
                    selected = isBlockAllExcept,
                    onClick = {
                        focusModeType = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED
                        val existingData = loader.getFocusModeData()
                        loader.saveFocusModeData(existingData.copy(modeType = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED))
                        broadcastFocusRefresh(context)
                    }
                )
            }

            // 2. Target Focus Apps
            ConfigSectionCard(
                title = "TARGET FOCUS APPS",
                icon = Icons.Outlined.Apps,
                badge = "QUICK FOCUS"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (focusModeType == Constants.FOCUS_MODE_BLOCK_SELECTED) "Distracting Apps List" else "Allowed Focus Apps List",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$selectedAppsCount apps currently configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = onSelectAppsClick,
                        modifier = Modifier.bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 3. Quick-Focus Duration Presets
            ConfigSectionCard(
                title = "QUICK-FOCUS DURATION PRESETS",
                icon = Icons.Outlined.Timer,
                badge = "QUICK FOCUS"
            ) {
                Text(
                    text = "Preset button options available on the main Focus tab and Home Screen widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetBadge("PRESET 1", preset1, Modifier.weight(1f))
                    PresetBadge("PRESET 2", preset2, Modifier.weight(1f))
                    PresetBadge("PRESET 3", preset3, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showPresetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Customize Durations", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // =========================================================================
            // CATEGORY 2: GLOBAL FOCUS & SECURITY ENGINE
            // =========================================================================
            CategoryHeaderBanner(
                title = "GLOBAL FOCUS & SECURITY ENGINE",
                subtitle = "System-wide rules that govern all focus sessions, widgets, and scheduled automation rules.",
                icon = Icons.Outlined.Security
            )

            // 4. Always-Ignored / Global Whitelist (Emergency & Essential Tools)
            ConfigSectionCard(
                title = "ALWAYS-WHITELISTED APPS (EMERGENCY)",
                icon = Icons.Outlined.VerifiedUser,
                badge = "GLOBAL"
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency & Essential Apps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$alwaysWhitelistedCount custom apps whitelisted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onSelectAlwaysWhitelistedClick,
                        modifier = Modifier.bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddModerator,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Whitelist", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 5. Interception Reaction Style
            ConfigSectionCard(
                title = "INTERCEPTION REACTION STYLE",
                icon = Icons.Outlined.Visibility,
                badge = "GLOBAL"
            ) {
                Text(
                    text = "Choose what happens when you attempt to open a blocked distraction during an active focus session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: Focus Space Workspace Overlay (Recommended)
                ModeSelectionOptionCard(
                    title = "Focus Space Workspace Overlay (Recommended)",
                    description = "Takes you to your distraction-free session workspace with live countdown and allowed tools.",
                    selected = interceptionStyle == Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE,
                    onClick = {
                        interceptionStyle = Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE
                        loader.setFocusModeInterceptionStyle(Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Focus Warning Dialog (Default when Workspace is OFF)
                ModeSelectionOptionCard(
                    title = "Focus Warning Dialog (Default)",
                    description = "Informative popup reminding you that a Focus Session is running with remaining time.",
                    selected = interceptionStyle == Constants.FOCUS_INTERCEPTION_STYLE_DIALOG,
                    onClick = {
                        interceptionStyle = Constants.FOCUS_INTERCEPTION_STYLE_DIALOG
                        loader.setFocusModeInterceptionStyle(Constants.FOCUS_INTERCEPTION_STYLE_DIALOG)
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Silent Instant Exit
                ModeSelectionOptionCard(
                    title = "Silent Instant Exit (Opt-in)",
                    description = "Silently closes restricted apps and returns to the device home screen without an overlay.",
                    selected = interceptionStyle == Constants.FOCUS_INTERCEPTION_STYLE_SILENT,
                    onClick = {
                        interceptionStyle = Constants.FOCUS_INTERCEPTION_STYLE_SILENT
                        loader.setFocusModeInterceptionStyle(Constants.FOCUS_INTERCEPTION_STYLE_SILENT)
                    }
                )

                // Sub-action for Workspace (Configure allowed workspace apps)
                if (interceptionStyle == Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allowed Workspace Apps",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$amnispaceAppsCount essential tools accessible inside workspace",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onSelectAlwaysWhitelistedClick,
                            modifier = Modifier.bounceClick(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manage", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Sub-action for Dialog (Configure Warning Message)
                if (interceptionStyle == Constants.FOCUS_INTERCEPTION_STYLE_DIALOG) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                            .clickable { showWarningMessageDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Focus Warning Dialog Message",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Customize reminder text shown during focus lockdown",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Configure",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. Strictness & Lock Rules
            ConfigSectionCard(
                title = "STRICTNESS & LOCK PREFERENCES",
                icon = Icons.Outlined.Lock,
                badge = "GLOBAL"
            ) {
                // Strictness Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Focus Enforcement",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (strictnessMode == FocusModeBlocker.STRICTNESS_HARD_LOCK) "Aggressive interception & instant exit" else "Standard friendly reminder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictnessMode == FocusModeBlocker.STRICTNESS_HARD_LOCK,
                        onCheckedChange = { checked ->
                            strictnessMode = if (checked) FocusModeBlocker.STRICTNESS_HARD_LOCK else FocusModeBlocker.STRICTNESS_MINDFUL_PAUSE
                            loader.setFocusModeStrictness(strictnessMode)
                            broadcastFocusRefresh(context)
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                // Allow Early Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Early Session Stop",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (allowEarlyStop) "Can pause/stop session early from app" else "Session cannot be cancelled until timer expires",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowEarlyStop,
                        onCheckedChange = { checked ->
                            allowEarlyStop = checked
                            loader.setFocusModeEarlyStopAllowed(checked)
                        }
                    )
                }
            }

            // 7. Active Focus Mode Automation Rules Link
            ConfigSectionCard(
                title = "AUTOMATED FOCUS SCHEDULES",
                icon = Icons.Outlined.CalendarMonth,
                badge = "GLOBAL"
            ) {
                Text(
                    text = "Recurring focus sessions that activate automatically based on time of day and active days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$autoFocusRulesCount automated schedule" + if (autoFocusRulesCount != 1) "s" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = {
                            val intent = Intent(context, FragmentActivity::class.java).apply {
                                putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
                                putExtra("filter_type", "Focus Mode")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.bounceClick(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manage Schedules", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Presets Customization Dialog
    if (showPresetDialog) {
        var tempP1 by remember { mutableIntStateOf(preset1) }
        var tempP2 by remember { mutableIntStateOf(preset2) }
        var tempP3 by remember { mutableIntStateOf(preset3) }

        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Customize Focus Presets", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PresetSliderRow(label = "Preset 1", minutes = tempP1, onValueChange = { tempP1 = it })
                    PresetSliderRow(label = "Preset 2", minutes = tempP2, onValueChange = { tempP2 = it })
                    PresetSliderRow(label = "Preset 3", minutes = tempP3, onValueChange = { tempP3 = it })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        preset1 = tempP1
                        preset2 = tempP2
                        preset3 = tempP3
                        loader.saveQuickFocusDurationPresets(tempP1, tempP2, tempP3)
                        showPresetDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Focus Warning Dialog Message Customization Dialog
    if (showWarningMessageDialog) {
        var tempMsg by remember { mutableStateOf(warningMessageInput) }

        AlertDialog(
            onDismissRequest = { showWarningMessageDialog = false },
            title = { Text("Focus Warning Message", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Customize the message shown when a restricted app is blocked during a focus session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempMsg,
                        onValueChange = { tempMsg = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Reminder Message") },
                        placeholder = { Text("This app is restricted during your active Focus Session.") },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempMsg.trim().ifEmpty { "This app is restricted during your active Focus Session." }
                        warningMessageInput = trimmed
                        loader.saveFocusModeWarningInfo(loader.loadFocusModeWarningInfo().copy(message = trimmed))
                        showWarningMessageDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningMessageDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryHeaderBanner(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 36.dp)
        )
    }
}

@Composable
private fun ConfigSectionCard(
    title: String,
    icon: ImageVector,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun ModeSelectionOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PresetBadge(
    label: String,
    minutes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${minutes}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PresetSliderRow(
    label: String,
    minutes: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("${minutes} mins", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 5f..120f,
            steps = 22
        )
    }
}

@Composable
private fun ServiceRequiredCard(onEnableClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .bounceClick()
                .clickable { onEnableClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Accessibility Service Disabled", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Tap to enable AmniShield Accessibility Service to enforce Focus Mode.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            }
        }
    }
}

private fun broadcastFocusRefresh(context: Context) {
    val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
        setPackage(context.packageName)
    }
    context.sendBroadcast(intent)
}
