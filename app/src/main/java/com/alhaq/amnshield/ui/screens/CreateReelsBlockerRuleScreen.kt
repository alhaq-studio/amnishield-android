package com.alhaq.amnshield.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.UUID
import com.alhaq.amnshield.ui.state.AmnShieldState
import com.alhaq.amnshield.ui.state.ScheduleRule
import com.alhaq.amnshield.ui.viewmodel.AmnShieldViewModel
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReelsBlockerRuleScreen(
    state: AmnShieldState,
    viewModel: AmnShieldViewModel,
    initialType: String = "Block Schedule",
    editingRule: ScheduleRule? = null,
    onSaveRule: (ScheduleRule) -> Unit,
    onBack: () -> Unit,
    onDeleteRule: ((String) -> Unit)? = null
) {
    val context = LocalContext.current

    var showGuideDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val initialName = remember(editingRule) {
        editingRule?.name ?: "Reels Blocker Rule"
    }

    var ruleName by remember { mutableStateOf(initialName) }

    val loader = remember { SavedPreferencesLoader(context) }

    // 1. Always Block vs Block Schedule
    var isAlwaysBlockEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isAlwaysBlockEnabled ?: (editingRule == null))
    }
    var isScheduleEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isScheduleEnabled ?: false)
    }

    // Reels Limit Mode (scrolled limit)
    var isLimitByReelsScrolled by remember {
        mutableStateOf(loader.getReelBlockerMode(2) == 1)
    }
    var reelsLimitCount by remember {
        mutableStateOf(loader.getReelBlockerDailyLimit(25).toFloat())
    }

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

    // 2. Cheat Hours
    var isCheatEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isCheatEnabled ?: false)
    }
    var cheatStartTime by remember(editingRule) {
        mutableStateOf(editingRule?.cheatStartTime ?: "12:00")
    }
    var cheatEndTime by remember(editingRule) {
        mutableStateOf(editingRule?.cheatEndTime ?: "13:00")
    }
    val cheatDays = remember(editingRule) {
        mutableStateListOf<String>().apply {
            if (editingRule != null) {
                addAll(editingRule.cheatDays)
            } else {
                addAll(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
            }
        }
    }

    // Time Pickers Dialog State
    var activeTimePicker by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }

    val saveEnabled = ruleName.isNotBlank() && (isAlwaysBlockEnabled || isScheduleEnabled || isCheatEnabled || isLimitByReelsScrolled)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreateRuleTopAppBar(
                title = if (editingRule != null) "Edit Reels Rule" else "Create Reels Rule",
                onBack = onBack,
                onReset = {
                    ruleName = "Reels Blocker Rule"
                    isAlwaysBlockEnabled = true
                    isScheduleEnabled = false
                    scheduleStartTime = "09:00"
                    scheduleEndTime = "17:00"
                    scheduleDays.clear()
                    scheduleDays.addAll(listOf("Mon", "Tue", "Wed", "Thu", "Fri"))
                    isCheatEnabled = false
                    cheatStartTime = "12:00"
                    cheatEndTime = "13:00"
                    cheatDays.clear()
                    cheatDays.addAll(listOf("Sat", "Sun"))
                    isLimitByReelsScrolled = false
                    reelsLimitCount = 25f
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
                        val modeToSave = if (isLimitByReelsScrolled) 1 else 2
                        loader.setReelBlockerMode(modeToSave)
                        loader.setReelBlockerDailyLimit(reelsLimitCount.roundToInt())
                        loader.setReelBlockerEnabled(true, updateManual = true)

                        val newRule = ScheduleRule(
                            id = editingRule?.id ?: UUID.randomUUID().toString(),
                            name = ruleName,
                            appOrCategory = "Reels Blocker",
                            restrictionType = "Reels Blocker",
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays.toList(),
                            limitValue = reelsLimitCount.roundToInt(),
                            isActive = true,
                            periods = emptyList(),
                            targetBlockerType = "Reels Blocker",
                            selectedApps = emptyList(),
                            selectedKeywords = emptyList(),
                            selectedWebsites = emptyList(),
                            selectedPlatforms = listOf("Instagram", "YouTube", "TikTok", "Facebook"),
                            selectedBlockers = listOf("Reels Blocker"),
                            isAlwaysBlockEnabled = isAlwaysBlockEnabled,
                            isScheduleEnabled = isScheduleEnabled,
                            isCheatEnabled = isCheatEnabled,
                            cheatStartTime = cheatStartTime,
                            cheatEndTime = cheatEndTime,
                            cheatDays = cheatDays.toList()
                        )
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
                title = "Short-Form Video Protection",
                subtitle = "Restrict addictive YouTube Shorts, Instagram Reels, TikTok, and Facebook Video feeds.",
                icon = Icons.Outlined.Lock
            )

            // Rule Name Card
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

            // Protection Mode & Schedule Card
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
                        text = "Protection Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Always Block (24/7) Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Always Block (24/7)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Block short video feeds 24/7 without exception",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isAlwaysBlockEnabled,
                            onCheckedChange = {
                                isAlwaysBlockEnabled = it
                                if (it) {
                                    isScheduleEnabled = false
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Block Schedule Row
                    AnimatedVisibility(visible = !isAlwaysBlockEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "Scheduled Protection Window",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Block reels during specific active hours",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isScheduleEnabled,
                                    onCheckedChange = {
                                        isScheduleEnabled = it
                                        if (it) {
                                            isAlwaysBlockEnabled = false
                                        }
                                    }
                                )
                            }

                            AnimatedVisibility(visible = isScheduleEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        }
                    }
                }
            }

            // Scroll Limit Card (Dynamic Slider)
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
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Column {
                                Text(
                                    text = "Limit by Reels Scrolled",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Automatically block feeds after viewing N short videos daily",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isLimitByReelsScrolled,
                            onCheckedChange = { isLimitByReelsScrolled = it }
                        )
                    }

                    AnimatedVisibility(visible = isLimitByReelsScrolled) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Limit:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Block after ${reelsLimitCount.roundToInt()} Reels",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = reelsLimitCount,
                                onValueChange = { reelsLimitCount = it },
                                valueRange = 5f..150f,
                                steps = 28
                            )

                            // Quick Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(10, 25, 50, 100).forEach { limit ->
                                    val isSelected = reelsLimitCount.roundToInt() == limit
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { reelsLimitCount = limit.toFloat() },
                                        label = { Text("$limit reels") },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cheat Hours Card (Dimmed & Disabled when 24/7 is Active)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlwaysBlockEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
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
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (isAlwaysBlockEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Cheat Hours (Bypass)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isAlwaysBlockEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAlwaysBlockEnabled) "Disabled while Always Block (24/7) is active"
                                    else "Temporarily bypass reels blocking during these hours",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isCheatEnabled && !isAlwaysBlockEnabled,
                            onCheckedChange = { isCheatEnabled = it },
                            enabled = !isAlwaysBlockEnabled
                        )
                    }

                    AnimatedVisibility(visible = isCheatEnabled && !isAlwaysBlockEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Bypass Start", style = MaterialTheme.typography.labelSmall)
                                    OutlinedButton(
                                        onClick = {
                                            activeTimePicker = "cheat_start"
                                            showTimePicker = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(cheatStartTime, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Bypass End", style = MaterialTheme.typography.labelSmall)
                                    OutlinedButton(
                                        onClick = {
                                            activeTimePicker = "cheat_end"
                                            showTimePicker = true
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(cheatEndTime, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Cheat Days
                            Text("Bypass Days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val daysList = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                daysList.forEach { day ->
                                    val isSelected = cheatDays.contains(day)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.secondary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                if (isSelected) cheatDays.remove(day) else cheatDays.add(day)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = day.take(1),
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Time picker dialog logic
    if (showTimePicker) {
        val initialTime = when (activeTimePicker) {
            "start" -> scheduleStartTime
            "end" -> scheduleEndTime
            "cheat_start" -> cheatStartTime
            else -> cheatEndTime
        }
        val parts = initialTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        CreateRuleTimePickerDialog(
            title = when (activeTimePicker) {
                "start" -> "Select Start Time"
                "end" -> "Select End Time"
                "cheat_start" -> "Select Cheat Start Time"
                else -> "Select Cheat End Time"
            },
            initialHour = h,
            initialMinute = m,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                val formatted = String.format("%02d:%02d", hour, minute)
                when (activeTimePicker) {
                    "start" -> scheduleStartTime = formatted
                    "end" -> scheduleEndTime = formatted
                    "cheat_start" -> cheatStartTime = formatted
                    "cheat_end" -> cheatEndTime = formatted
                }
                showTimePicker = false
            }
        )
    }

    if (showGuideDialog) {
        RuleGuideDialog(
            title = "Reels Blocker Rules Guide",
            description = "Reels & Shorts Blocker intercepts short-form addictive video feeds across YouTube Shorts, Instagram Reels, TikTok, and Facebook Reels.",
            tips = listOf(
                "Always Block: Blocks all short-form video surfaces immediately upon access.",
                "Block Schedule: Enforces short-form video blocking during your focus hours.",
                "Scroll Limit: Allows scrolling up to your daily reel allowance before locking.",
                "Cheat Hours: Lets you enjoy short videos freely during break windows."
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
