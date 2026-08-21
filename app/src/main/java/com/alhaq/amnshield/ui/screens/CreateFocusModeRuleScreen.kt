package com.alhaq.amnshield.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alhaq.amnshield.Constants
import com.alhaq.amnshield.ui.activity.SelectAppsActivity
import com.alhaq.amnshield.ui.state.AmnShieldState
import com.alhaq.amnshield.ui.state.ScheduleRule
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFocusModeRuleScreen(
    state: AmnShieldState,
    onSaveRule: (ScheduleRule) -> Unit,
    onBack: () -> Unit,
    editingRule: ScheduleRule? = null,
    onDeleteRule: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    var showGuideDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val initialName = remember(editingRule) {
        editingRule?.name ?: "Auto Focus Schedule"
    }

    var ruleName by remember { mutableStateOf(initialName) }

    val selectedApps = remember(editingRule) {
        mutableStateListOf<String>().apply {
            if (editingRule != null) {
                addAll(editingRule.selectedApps)
            } else {
                val loader = SavedPreferencesLoader(context)
                addAll(loader.getFocusModeSelectedApps())
            }
        }
    }

    // Focus Mode Strategy (Block All Except Whitelist vs Block Selected Blacklist)
    var focusModeType by remember(editingRule) {
        val loader = SavedPreferencesLoader(context)
        val defaultType = loader.getFocusModeData().modeType
        mutableStateOf(if (defaultType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) 1 else 0)
    }

    // Time Window & Days Schedule
    var scheduleStartTime by remember(editingRule) {
        mutableStateOf(editingRule?.startTime ?: "09:00")
    }
    var scheduleEndTime by remember(editingRule) {
        mutableStateOf(editingRule?.endTime ?: "17:00")
    }
    val scheduleDays = remember(editingRule) {
        mutableStateListOf<String>().apply {
            if (editingRule != null) {
                addAll(editingRule.days)
            } else {
                addAll(listOf("Mon", "Tue", "Wed", "Thu", "Fri"))
            }
        }
    }

    // Time picker dialog state
    var showTimePicker by remember { mutableStateOf(false) }
    var activeTimePicker by remember { mutableStateOf("start") }

    // App Picker Activity Launcher
    val selectAppsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            if (apps != null) {
                selectedApps.clear()
                selectedApps.addAll(apps)
            }
        }
    }

    val saveEnabled = ruleName.isNotBlank() && scheduleDays.isNotEmpty()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreateRuleTopAppBar(
                title = if (editingRule != null) "Edit Auto Focus Schedule" else "Create Auto Focus Schedule",
                onBack = onBack,
                onReset = {
                    ruleName = "Auto Focus Schedule"
                    focusModeType = Constants.FOCUS_MODE_BLOCK_SELECTED
                    scheduleStartTime = "09:00"
                    scheduleEndTime = "17:00"
                    scheduleDays.clear()
                    scheduleDays.addAll(listOf("Mon", "Tue", "Wed", "Thu", "Fri"))
                },
                onHelp = { showGuideDialog = true },
                onDelete = if (editingRule != null && onDeleteRule != null) { { showDeleteConfirmDialog = true } } else null
            )
        },
        bottomBar = {
            CreateRuleBottomActionBar(
                onCancel = onBack,
                onSave = {
                    if (saveEnabled) {
                        val ruleId = editingRule?.id ?: UUID.randomUUID().toString()
                        val newRule = ScheduleRule(
                            id = ruleId,
                            name = ruleName,
                            appOrCategory = "Focus Mode",
                            restrictionType = "Focus Mode",
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays.toList(),
                            targetBlockerType = "Focus Mode",
                            selectedApps = selectedApps.toList(),
                            selectedBlockers = listOf("Focus Mode"),
                            isScheduleEnabled = true,
                            isActive = editingRule?.isActive ?: true
                        )

                        val loader = SavedPreferencesLoader(context)
                        val currentData = loader.getFocusModeData()
                        val selectedMode = if (focusModeType == 1) Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED else Constants.FOCUS_MODE_BLOCK_SELECTED
                        loader.saveFocusModeData(
                            com.alhaq.amnshield.blockers.FocusModeBlocker.FocusModeData(
                                isTurnedOn = currentData.isTurnedOn,
                                endTime = currentData.endTime,
                                modeType = selectedMode,
                                selectedApps = HashSet(selectedApps)
                            )
                        )
                        loader.saveFocusModeSelectedApps(selectedApps.toList())

                        onSaveRule(newRule)
                    }
                },
                saveEnabled = saveEnabled
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Banner
            BoundaryHeader(
                title = "Auto Focus Schedule",
                subtitle = "Automatically lock distractions during work hours, study sessions, or quiet time.",
                icon = Icons.Outlined.Lock
            )

            // Schedule Name Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleNameSection(
                        ruleName = ruleName,
                        onRuleNameChange = { ruleName = it }
                    )
                }
            }

            // Focus Mode Strategy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Focus Mode Strategy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = focusModeType == 1,
                            onClick = { focusModeType = 1 },
                            label = { Text("Block All Except Whitelist") },
                            leadingIcon = if (focusModeType == 1) {
                                { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        FilterChip(
                            selected = focusModeType == 0,
                            onClick = { focusModeType = 0 },
                            label = { Text("Block Selected Only") },
                            leadingIcon = if (focusModeType == 0) {
                                { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Schedule Window Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Active Schedule Window",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Start Time", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(
                                onClick = {
                                    activeTimePicker = "start"
                                    showTimePicker = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(scheduleStartTime, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("End Time", style = MaterialTheme.typography.labelSmall)
                            OutlinedButton(
                                onClick = {
                                    activeTimePicker = "end"
                                    showTimePicker = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(scheduleEndTime, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Active Days Selection
                    Text("Active Schedule Days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val daysList = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        daysList.forEach { day ->
                            val isSelected = scheduleDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        if (isSelected) scheduleDays.remove(day) else scheduleDays.add(day)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.take(1),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Target Apps Selector Card with Mini Icon Preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (focusModeType == 1) "Allowed Whitelist Apps" else "Blocked Blacklist Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedApps.size} apps configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(context, SelectAppsActivity::class.java).apply {
                                    putStringArrayListExtra("PRE_SELECTED_APPS", ArrayList(selectedApps))
                                }
                                selectAppsLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Select Apps", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Mini App Icon Preview Row
                    if (selectedApps.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedApps.take(5).forEach { pkg ->
                                val appIcon = remember(pkg) {
                                    try {
                                        context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (selectedApps.size > 5) {
                                Text(
                                    text = "+${selectedApps.size - 5} more",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Time picker dialog logic
    if (showTimePicker) {
        val initialTime = if (activeTimePicker == "start") scheduleStartTime else scheduleEndTime
        val parts = initialTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        CreateRuleTimePickerDialog(
            title = if (activeTimePicker == "start") "Select Auto Focus Start Time" else "Select Auto Focus End Time",
            initialHour = h,
            initialMinute = m,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour: Int, minute: Int ->
                val formatted = String.format("%02d:%02d", hour, minute)
                if (activeTimePicker == "start") {
                    scheduleStartTime = formatted
                } else {
                    scheduleEndTime = formatted
                }
                showTimePicker = false
            }
        )
    }

    if (showGuideDialog) {
        RuleGuideDialog(
            title = "Auto Focus Schedule Guide",
            description = "Auto Focus rules turn on deep distraction blocking automatically on your chosen work hours and days.",
            tips = listOf(
                "Block Selected (Default): Blocks your selected blacklist apps while allowing productive apps.",
                "Allow Only Selected (Whitelist): Blocks all apps on your device except your chosen essential apps.",
                "Timed Auto Activation: Focus mode begins and ends automatically on scheduled days."
            ),
            onDismiss = { showGuideDialog = false }
        )
    }

    if (showDeleteConfirmDialog && editingRule != null && onDeleteRule != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Rule?") },
            text = { Text("Are you sure you want to delete \"${editingRule.name}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteRule(editingRule.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
