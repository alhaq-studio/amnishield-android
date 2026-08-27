package com.alhaq.amnishield.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthTarget
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.components.PasswordPromptDialog
import com.alhaq.amnishield.ui.components.RuleLockIconButton
import com.alhaq.amnishield.ui.components.SetRulePinDialog
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.ui.state.SchedulePeriod
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.ScheduleUtils
import com.alhaq.amnishield.ui.components.bounceClick


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksManagerScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    initialFilter: String = "All",
    onNavigateToCreateRule: (String) -> Unit,
    onEditRule: (ScheduleRule) -> Unit = {},
    onToggleRule: (String) -> Unit,
    onDeleteRule: (String) -> Unit,
    onUpdateRuleAuth: (ScheduleRule, AuthType, String?, String?) -> Unit = { _, _, _, _ -> },
    onNavigateToPremium: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver.getInstance(context) }

    var selectedFilter by remember { mutableStateOf(initialFilter) }
    var ruleForPinSetting by remember { mutableStateOf<ScheduleRule?>(null) }
    var activeChallengeTarget by remember { mutableStateOf<AuthTarget?>(null) }
    var pendingAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var challengeTitle by remember { mutableStateOf("PIN Verification") }
    var challengeSubtitle by remember { mutableStateOf("Enter PIN to modify this protected rule") }

    val requestAuthAndExecute: (ScheduleRule?, String, () -> Unit) -> Unit = { rule, actionDesc, onGranted ->
        val target = authResolver.resolveChallenge(rule)
        when (target) {
            is AuthTarget.PassThrough -> {
                onGranted()
            }
            is AuthTarget.RequireRulePin -> {
                challengeTitle = "Rule PIN Required"
                challengeSubtitle = "Enter the PIN configured for this rule to $actionDesc."
                activeChallengeTarget = target
                pendingAuthAction = onGranted
            }
            is AuthTarget.RequireGlobalPin -> {
                challengeTitle = "Master PIN Required"
                challengeSubtitle = "Enter your Master PIN to $actionDesc."
                activeChallengeTarget = target
                pendingAuthAction = onGranted
            }
            is AuthTarget.LockedOut -> {
                challengeTitle = "Security Lockout"
                challengeSubtitle = "Too many failed PIN attempts. Please wait."
                activeChallengeTarget = target
                pendingAuthAction = null
            }
        }
    }

    if (ruleForPinSetting != null) {
        val targetRule = ruleForPinSetting!!
        SetRulePinDialog(
            initialAuthType = targetRule.authType,
            isPremiumUser = state.isPremiumUser,
            onDismiss = { ruleForPinSetting = null },
            onNavigateToPremium = onNavigateToPremium,
            onSaveProtection = { newAuthType: AuthType, newHash: String?, newSalt: String? ->
                onUpdateRuleAuth(targetRule, newAuthType, newHash, newSalt)
                ruleForPinSetting = null
            }
        )
    }

    if (activeChallengeTarget != null) {
        PasswordPromptDialog(
            target = activeChallengeTarget,
            title = challengeTitle,
            subtitle = challengeSubtitle,
            onDismiss = {
                activeChallengeTarget = null
                pendingAuthAction = null
            },
            onSuccess = {
                val action = pendingAuthAction
                activeChallengeTarget = null
                pendingAuthAction = null
                action?.invoke()
            }
        )
    }

    val filteredRules = when (selectedFilter) {
        "Schedules" -> state.scheduleRules.filter { it.isScheduleEnabled }
        "Limits" -> state.scheduleRules.filter { it.isUsageLimitEnabled || it.isLaunchLimitEnabled }
        "Cheat Hours" -> state.scheduleRules.filter { it.isCheatEnabled }
        else -> state.scheduleRules
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text(
                                text = "Blocks & Rules",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Manage your active blocks and rules",
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
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add Rule", fontWeight = FontWeight.Bold) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                onClick = {
                    onNavigateToCreateRule("Block Schedule")
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // Horizontal filter pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val filters = listOf("All", "Schedules", "Limits", "Cheat Hours")
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .bounceClick()
                            .clickable { selectedFilter = filter }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (selectedFilter) {
                                    "Schedules" -> Icons.Outlined.CalendarToday
                                    "Limits" -> Icons.AutoMirrored.Outlined.Launch
                                    "Cheat Hours" -> Icons.Outlined.HourglassEmpty
                                    else -> Icons.AutoMirrored.Outlined.Rule
                                },
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Rules Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (selectedFilter) {
                                "Schedules" -> "No recurring schedule rules created."
                                "Limits" -> "No launch count or usage limits configured."
                                "Cheat Hours" -> "No cheat hour windows configured."
                                else -> "Tap '+ Add Rule' below to create your first boundary rule."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        RuleItemCard(
                            rule = rule,
                            onToggleActive = {
                                requestAuthAndExecute(rule, "toggle this rule") {
                                    onToggleRule(rule.id)
                                }
                            },
                            onDelete = {
                                requestAuthAndExecute(rule, "delete this rule") {
                                    onDeleteRule(rule.id)
                                }
                            },
                            onLockClick = {
                                if (rule.authType != AuthType.NONE) {
                                    requestAuthAndExecute(rule, "unlock this rule") {
                                        onUpdateRuleAuth(rule, AuthType.NONE, null, null)
                                    }
                                } else {
                                    if (!state.isPremiumUser) {
                                        onNavigateToPremium()
                                    } else {
                                        ruleForPinSetting = rule
                                    }
                                }
                            },
                            onClick = {
                                requestAuthAndExecute(rule, "edit this rule") {
                                    onEditRule(rule)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleItemCard(
    rule: ScheduleRule,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onLockClick: () -> Unit,
    onClick: () -> Unit
) {
    val blockerColor = when (rule.targetBlockerType) {
        "Keyword Blocker" -> Color(0xFF10B981) // Emerald
        "Website Blocker" -> Color(0xFF3B82F6) // Blue
        "Reels Blocker" -> Color(0xFFEC4899) // Pink
        else -> Color(0xFF8B5CF6) // Purple
    }

    val blockerIcon = when (rule.targetBlockerType) {
        "Keyword Blocker" -> Icons.Outlined.Search
        "Website Blocker" -> Icons.Outlined.Language
        "Reels Blocker" -> Icons.Outlined.PlayCircleOutline
        else -> Icons.Outlined.AppShortcut
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(blockerColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = blockerIcon,
                        contentDescription = null,
                        tint = blockerColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val safeName = rule.name.ifBlank { "Unnamed Blocker" }
                    val safeCategory = rule.appOrCategory.ifBlank { "Apps" }
                    val safeTargetType = rule.targetBlockerType.ifBlank { "App Blocker" }

                    Text(
                        text = safeName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = safeCategory,
                            style = MaterialTheme.typography.bodySmall,
                            color = blockerColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                        Text(
                            text = safeTargetType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Switch(
                    checked = rule.isActive,
                    onCheckedChange = { onToggleActive() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Display active modes as premium badges/chips in a horizontal scroll Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (rule.isScheduleEnabled) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Active: ${rule.startTime}-${rule.endTime}", fontSize = 11.sp) },
                        icon = { Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFEF4444).copy(alpha = 0.08f),
                            labelColor = Color(0xFFEF4444),
                            iconContentColor = Color(0xFFEF4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
                    )
                }

                if (rule.isCheatEnabled) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Cheat: ${rule.cheatStartTime}-${rule.cheatEndTime}", fontSize = 11.sp) },
                        icon = { Icon(imageVector = Icons.Outlined.HourglassEmpty, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.08f),
                            labelColor = Color(0xFFF59E0B),
                            iconContentColor = Color(0xFFF59E0B)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.2f))
                    )
                }

                if (rule.isUsageLimitEnabled) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Usage: ${rule.usageLimitHours}h/day", fontSize = 11.sp) },
                        icon = { Icon(imageVector = Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF3B82F6).copy(alpha = 0.08f),
                            labelColor = Color(0xFF3B82F6),
                            iconContentColor = Color(0xFF3B82F6)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.2f))
                    )
                }

                if (rule.isLaunchLimitEnabled) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Launch: ${rule.launchLimitCount}x/day", fontSize = 11.sp) },
                        icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.Launch, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                            labelColor = Color(0xFF8B5CF6),
                            iconContentColor = Color(0xFF8B5CF6)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.2f))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val activeDays: List<String>? = if (rule.isScheduleEnabled) rule.days else if (rule.isCheatEnabled) rule.cheatDays else null
                    val safeDaysList = activeDays?.filterNotNull() ?: emptyList()
                    if (safeDaysList.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = safeDaysList.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RuleLockIconButton(
                        rule = rule,
                        onClick = onLockClick
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Rule",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
