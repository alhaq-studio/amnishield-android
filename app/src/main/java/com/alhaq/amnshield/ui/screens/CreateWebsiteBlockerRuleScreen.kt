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
import com.alhaq.amnshield.data.blockers.PreloadedPacks

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateWebsiteBlockerRuleScreen(
    state: AmnShieldState,
    viewModel: AmnShieldViewModel,
    initialType: String = "Block Schedule",
    editingRule: ScheduleRule? = null,
    onSaveRule: (ScheduleRule) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val initialName = remember(editingRule) {
        editingRule?.name ?: "Website Blocker Rule"
    }

    var ruleName by remember { mutableStateOf(initialName) }

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
                addAll(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"))
            }
        }
    }

    // Time Pickers Dialog State
    var activeTimePicker by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }

    // Websites State
    val loader = remember { SavedPreferencesLoader(context) }
    val blockedWebsites = remember {
        mutableStateListOf<String>().apply {
            addAll(loader.loadBlockedWebsites())
        }
    }
    var newWebsite by remember { mutableStateOf("") }

    val presetWebsites = listOf(
        "instagram.com",
        "tiktok.com",
        "twitter.com",
        "reddit.com",
        "youtube.com",
        "facebook.com"
    )

    val saveEnabled = ruleName.isNotBlank() && (isAlwaysBlockEnabled || isScheduleEnabled || isCheatEnabled)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = if (editingRule != null) "Edit Website Rule" else "Create Website Rule",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            CreateRuleBottomActionBar(
                onCancel = onBack,
                onSave = {
                    if (saveEnabled) {
                        loader.saveBlockedWebsites(blockedWebsites.toSet())
                        loader.setWebsiteBlockerEnabled(true, updateManual = true)

                        val newRule = ScheduleRule(
                            id = editingRule?.id ?: UUID.randomUUID().toString(),
                            name = ruleName,
                            appOrCategory = "Website Blocker",
                            restrictionType = "Website Blocker",
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays.toList(),
                            limitValue = 0,
                            isActive = true,
                            periods = emptyList(),
                            targetBlockerType = "Website Blocker",
                            selectedApps = emptyList(),
                            selectedKeywords = emptyList(),
                            selectedWebsites = blockedWebsites.toList(),
                            selectedPlatforms = emptyList(),
                            selectedBlockers = listOf("Website Blocker"),
                            isAlwaysBlockEnabled = isAlwaysBlockEnabled,
                            isScheduleEnabled = isScheduleEnabled,
                            isCheatEnabled = isCheatEnabled,
                            cheatStartTime = cheatStartTime,
                            cheatEndTime = cheatEndTime,
                            cheatDays = cheatDays.toList()
                        )

                        val refreshIntent = Intent(com.alhaq.amnshield.services.AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
                        refreshIntent.setPackage(context.packageName)
                        context.sendBroadcast(refreshIntent)

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
                title = "Website & Domain Protection",
                subtitle = "Block distractful web URLs, social domains, and explicit adult websites across browser sessions.",
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

            // Blacklisted Websites Management Card
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Blacklisted Domains",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${blockedWebsites.size} active",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newWebsite,
                            onValueChange = { newWebsite = it },
                            placeholder = { Text("Add website (e.g. facebook.com)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        IconButton(
                            onClick = {
                                var site = newWebsite.trim().lowercase()
                                site = site.removePrefix("http://").removePrefix("https://").removePrefix("www.")
                                if (site.isNotEmpty() && !blockedWebsites.contains(site)) {
                                    blockedWebsites.add(site)
                                    loader.saveBlockedWebsites(blockedWebsites.toSet())
                                    val intent = Intent(com.alhaq.amnshield.services.AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                                    intent.setPackage(context.packageName)
                                    context.sendBroadcast(intent)
                                    newWebsite = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Website")
                        }
                    }

                    // Preloaded Blacklist Packs Section
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Preloaded Website Packs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "One-tap import curated domain packs or skip to add custom domains below.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreloadedPacks.websitePacks.forEach { pack ->
                            val allAdded = pack.items.all { blockedWebsites.contains(it) }
                            val countAdded = pack.items.count { blockedWebsites.contains(it) }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (allAdded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    1.dp,
                                    if (allAdded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = pack.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = pack.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (allAdded) "All ${pack.items.size} domains imported"
                                                       else if (countAdded > 0) "$countAdded of ${pack.items.size} domains added"
                                                       else pack.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (allAdded) {
                                                blockedWebsites.removeAll(pack.items)
                                            } else {
                                                pack.items.forEach { site ->
                                                    if (!blockedWebsites.contains(site)) {
                                                        blockedWebsites.add(site)
                                                    }
                                                }
                                            }
                                            loader.saveBlockedWebsites(blockedWebsites.toSet())
                                            val intent = Intent(com.alhaq.amnshield.services.AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                                            intent.setPackage(context.packageName)
                                            context.sendBroadcast(intent)
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (allAdded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                                            contentColor = if (allAdded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = if (allAdded) "Remove" else if (countAdded > 0) "Add Rest" else "Import Pack",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Websites List
                    if (blockedWebsites.isEmpty()) {
                        Text(
                            text = "No websites blocked yet. Add custom domains or select presets above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            blockedWebsites.forEach { website ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = website,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    blockedWebsites.remove(website)
                                                    loader.saveBlockedWebsites(blockedWebsites.toSet())
                                                    val intent = Intent(com.alhaq.amnshield.services.AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
                                                    intent.setPackage(context.packageName)
                                                    context.sendBroadcast(intent)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Protection Mode & Schedule Settings Card
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
                                    text = "Block websites all day, every day",
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
                                            text = "Block websites during specific active hours",
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

            // Cheat Hours (Bypass Window) Card
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
                                    text = "Temporarily bypass website blocking during these hours",
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
}
