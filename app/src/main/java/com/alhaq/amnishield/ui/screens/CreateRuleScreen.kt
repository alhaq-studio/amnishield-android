package com.alhaq.amnishield.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.activity.SelectAppsActivity
import com.alhaq.amnishield.ui.components.PasswordPromptDialog
import com.alhaq.amnishield.ui.components.RuleSecurityLevelSection
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRuleScreen(
    state: AmniShieldState,
    onSaveRule: (ScheduleRule) -> Unit,
    onBack: () -> Unit,
    editingRule: ScheduleRule? = null,
    prefillTarget: String = "APP_BLOCKER",
    prefillApp: String? = null,
    onDeleteRule: ((String) -> Unit)? = null,
    onNavigateToPremium: () -> Unit = {}
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver(context) }

    var showGuideDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAuthPromptForSave by remember { mutableStateOf(false) }
    var showAuthPromptForDelete by remember { mutableStateOf(false) }

    // Security Level State
    var authType by remember(editingRule) {
        mutableStateOf(editingRule?.authType ?: AuthType.NONE)
    }
    var customPin by remember { mutableStateOf("") }
    var customPinConfirm by remember { mutableStateOf("") }

    val initialName = remember(editingRule, prefillApp) {
        if (editingRule != null) {
            editingRule.name
        } else if (!prefillApp.isNullOrEmpty()) {
            val appLabel = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(prefillApp, 0)
                ).toString()
            } catch (_: Exception) {
                prefillApp
            }
            "Block: $appLabel"
        } else {
            "App Blocker Rule"
        }
    }

    var ruleName by remember { mutableStateOf(initialName) }

    val selectedApps = remember(editingRule, prefillApp) {
        mutableStateListOf<String>().apply {
            if (editingRule != null) {
                addAll(editingRule.selectedApps)
            } else if (!prefillApp.isNullOrEmpty()) {
                add(prefillApp)
            } else {
                val loader = SavedPreferencesLoader(context)
                addAll(loader.loadBlockedApps())
            }
        }
    }

    // 1. Always Block vs Block Schedule
    var isAlwaysBlockEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isAlwaysBlockEnabled ?: (editingRule == null))
    }
    var isScheduleEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isScheduleEnabled ?: false)
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
                addAll(listOf("Mon", "Tue", "Wed", "Thu", "Fri"))
            }
        }
    }

    // 3. Usage & Launch Limits
    var isUsageLimitEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isUsageLimitEnabled ?: false)
    }
    var usageHoursStr by remember(editingRule) {
        mutableStateOf(editingRule?.usageLimitHours?.toString() ?: "1")
    }

    var isLaunchLimitEnabled by remember(editingRule) {
        mutableStateOf(editingRule?.isLaunchLimitEnabled ?: false)
    }
    var launchCountStr by remember(editingRule) {
        mutableStateOf(editingRule?.launchLimitCount?.toString() ?: "5")
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

    val saveEnabled = ruleName.isNotBlank() && selectedApps.isNotEmpty()

    val openAppBlockerConfig = {
        val intent = Intent(context, com.alhaq.amnishield.ui.activity.FragmentActivity::class.java).apply {
            putExtra("feature_type", "app_blocker")
        }
        context.startActivity(intent)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CreateRuleTopAppBar(
                title = if (editingRule != null) "Edit App Rule" else "Create App Rule",
                onBack = onBack,
                onConfigure = openAppBlockerConfig,
                configureLabel = "Configure App Blocker",
                onReset = {
                    ruleName = "App Blocker Rule"
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
                    isUsageLimitEnabled = false
                    usageHoursStr = "1"
                    isLaunchLimitEnabled = false
                    launchCountStr = "5"
                },
                onDelete = if (editingRule != null && onDeleteRule != null) {
                    {
                        if (editingRule.authType != AuthType.NONE) {
                            showAuthPromptForDelete = true
                        } else {
                            showDeleteConfirmDialog = true
                        }
                    }
                } else null
            )
        },
        bottomBar = {
            CreateRuleBottomActionBar(
                onCancel = onBack,
                onSave = {
                    if (saveEnabled) {
                        if (authType != AuthType.NONE && !state.isPremiumUser) {
                            onNavigateToPremium()
                            return@CreateRuleBottomActionBar
                        }
                        val usageHours = usageHoursStr.toIntOrNull() ?: 1
                        val launchCount = launchCountStr.toIntOrNull() ?: 5

                        val (hash, salt) = when (authType) {
                            AuthType.RULE_PIN -> {
                                if (customPin.isNotBlank()) {
                                    authResolver.createRulePin(customPin)
                                } else {
                                    Pair(editingRule?.rulePasswordHash, editingRule?.rulePasswordSalt)
                                }
                            }
                            else -> Pair(null, null)
                        }

                        val newRule = ScheduleRule(
                            id = editingRule?.id ?: UUID.randomUUID().toString(),
                            name = ruleName,
                            appOrCategory = "App Blocker",
                            restrictionType = "App Blocker",
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays.toList(),
                            limitValue = usageHours,
                            isActive = true,
                            periods = emptyList(),
                            targetBlockerType = "App Blocker",
                            selectedApps = selectedApps.toList(),
                            selectedKeywords = emptyList(),
                            selectedWebsites = emptyList(),
                            selectedPlatforms = emptyList(),
                            selectedBlockers = listOf("App Blocker"),
                            isAlwaysBlockEnabled = isAlwaysBlockEnabled,
                            isScheduleEnabled = isScheduleEnabled,
                            isCheatEnabled = isCheatEnabled,
                            cheatStartTime = cheatStartTime,
                            cheatEndTime = cheatEndTime,
                            cheatDays = cheatDays.toList(),
                            isUsageLimitEnabled = isUsageLimitEnabled,
                            usageLimitHours = usageHours,
                            isLaunchLimitEnabled = isLaunchLimitEnabled,
                            launchLimitCount = launchCount,
                            authType = authType,
                            rulePasswordHash = hash,
                            rulePasswordSalt = salt
                        )

                        if (editingRule != null && editingRule.authType != AuthType.NONE) {
                            showAuthPromptForSave = true
                        } else {
                            onSaveRule(newRule)
                        }
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
                title = "App Protection Rules",
                subtitle = "Schedule block windows, daily usage limits, and cheat hours for target apps.",
                icon = Icons.Outlined.Lock,
                onConfigure = openAppBlockerConfig,
                configureLabel = "App Blocker Settings"
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

            // Target Apps Card with Mini Icon Preview
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
                                text = "Target Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${selectedApps.size} apps selected",
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
                        text = "Protection Schedule",
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
                                    text = "Block target apps all day, every day",
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
                                            text = "Block apps during specific active hours",
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

            // Cheat Hours Card
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
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "Cheat Hours (Bypass)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Temporarily bypass app blocking during these hours",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isCheatEnabled,
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
                                horizontalArrangement = Arrangement.SpaceBetween
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

            // Usage & Launch Limits Card
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
                        text = "Usage & Launch Limits",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Daily Screen Time Limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Screen Time Limit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Max total hours allowed per day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isUsageLimitEnabled,
                            onCheckedChange = { isUsageLimitEnabled = it }
                        )
                    }

                    AnimatedVisibility(visible = isUsageLimitEnabled) {
                        OutlinedTextField(
                            value = usageHoursStr,
                            onValueChange = { usageHoursStr = it },
                            label = { Text("Max Daily Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Daily Launch Count Limit
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Launch Count Limit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Max app opens allowed per day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isLaunchLimitEnabled,
                            onCheckedChange = { isLaunchLimitEnabled = it }
                        )
                    }

                    AnimatedVisibility(visible = isLaunchLimitEnabled) {
                        OutlinedTextField(
                            value = launchCountStr,
                            onValueChange = { launchCountStr = it },
                            label = { Text("Max App Launches") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Security Level & Protection Section
            RuleSecurityLevelSection(
                authType = authType,
                onAuthTypeChange = { authType = it },
                customPin = customPin,
                onCustomPinChange = { customPin = it },
                customPinConfirm = customPinConfirm,
                onCustomPinConfirmChange = { customPinConfirm = it },
                isPremiumUser = state.isPremiumUser,
                onNavigateToPremium = onNavigateToPremium
            )
        }
    }

    // Auth challenge before saving an edited locked rule
    if (showAuthPromptForSave && editingRule != null) {
        PasswordPromptDialog(
            rule = editingRule,
            title = "Verify PIN to Update Rule",
            subtitle = "Enter PIN to save changes to this protected rule",
            onDismiss = { showAuthPromptForSave = false },
            onSuccess = {
                showAuthPromptForSave = false
                val usageHours = usageHoursStr.toIntOrNull() ?: 1
                val launchCount = launchCountStr.toIntOrNull() ?: 5
                val (hash, salt) = when (authType) {
                    AuthType.RULE_PIN -> {
                        if (customPin.isNotBlank()) authResolver.createRulePin(customPin)
                        else Pair(editingRule.rulePasswordHash, editingRule.rulePasswordSalt)
                    }
                    else -> Pair(null, null)
                }
                val updatedRule = ScheduleRule(
                    id = editingRule.id,
                    name = ruleName,
                    appOrCategory = "App Blocker",
                    restrictionType = "App Blocker",
                    startTime = scheduleStartTime,
                    endTime = scheduleEndTime,
                    days = scheduleDays.toList(),
                    limitValue = usageHours,
                    isActive = true,
                    periods = emptyList(),
                    targetBlockerType = "App Blocker",
                    selectedApps = selectedApps.toList(),
                    selectedKeywords = emptyList(),
                    selectedWebsites = emptyList(),
                    selectedPlatforms = emptyList(),
                    selectedBlockers = listOf("App Blocker"),
                    isAlwaysBlockEnabled = isAlwaysBlockEnabled,
                    isScheduleEnabled = isScheduleEnabled,
                    isCheatEnabled = isCheatEnabled,
                    cheatStartTime = cheatStartTime,
                    cheatEndTime = cheatEndTime,
                    cheatDays = cheatDays.toList(),
                    isUsageLimitEnabled = isUsageLimitEnabled,
                    usageLimitHours = usageHours,
                    isLaunchLimitEnabled = isLaunchLimitEnabled,
                    launchLimitCount = launchCount,
                    authType = authType,
                    rulePasswordHash = hash,
                    rulePasswordSalt = salt
                )
                onSaveRule(updatedRule)
            }
        )
    }

    // Auth challenge before deleting a locked rule
    if (showAuthPromptForDelete && editingRule != null && onDeleteRule != null) {
        PasswordPromptDialog(
            rule = editingRule,
            title = "Verify PIN to Delete Rule",
            subtitle = "Enter PIN to delete \"${editingRule.name}\"",
            onDismiss = { showAuthPromptForDelete = false },
            onSuccess = {
                showAuthPromptForDelete = false
                onDeleteRule(editingRule.id)
            }
        )
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
            onConfirm = { hour: Int, minute: Int ->
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
            title = "App Blocker Rules Guide",
            description = "App Blocker rules prevent chosen distracting apps from being launched according to your schedule.",
            tips = listOf(
                "Always Block: Blocks selected apps continuously 24/7.",
                "Block Schedule: Automatically restricts app usage during set hours on chosen days.",
                "Cheat Hours: Grants an allowed window during active restriction schedules.",
                "Daily Limits: Enforces max total usage hours or launch counts per day."
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
