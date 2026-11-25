package com.alhaq.amnishield.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.AuthTarget
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.components.AlwaysOnShieldsStatusBanner
import com.alhaq.amnishield.ui.components.AlwaysOnShieldsTabContent
import com.alhaq.amnishield.utils.AccessibilityDisclosureComposableDialog
import com.alhaq.amnishield.utils.AccessibilityDisclosureDialog
import com.alhaq.amnishield.utils.NotificationHelper
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay

enum class BlocksPrimaryTab {
    CUSTOM_RULES,
    ALWAYS_ON_SHIELDS
}

enum class CustomRulesFilter {
    ALL,
    SCHEDULES,
    LIMITS,
    ARCHIVE
}

private sealed class PendingRuleFrictionRequest {
    data class PauseRule(val rule: UnifiedBlockRule) : PendingRuleFrictionRequest()
    data class ActionAfterChallenge(
        val rule: UnifiedBlockRule,
        val actionLabel: String,
        val onVerified: () -> Unit
    ) : PendingRuleFrictionRequest()
    data class RulePinChallenge(
        val rule: UnifiedBlockRule,
        val target: AuthTarget,
        val actionLabel: String,
        val onVerified: () -> Unit
    ) : PendingRuleFrictionRequest()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedBlocksDashboardScreen(
    rules: List<UnifiedBlockRule>,
    alwaysOnShieldsConfig: AlwaysOnShieldsConfig,
    reelsScrolledToday: Int,
    isPremiumUser: Boolean,
    initialPrimaryTab: BlocksPrimaryTab = BlocksPrimaryTab.CUSTOM_RULES,
    initialCustomFilter: CustomRulesFilter = CustomRulesFilter.ALL,
    hasGlobalPinOverride: Boolean? = null,
    onUpdateAlwaysOnShieldsConfig: (AlwaysOnShieldsConfig) -> Unit,
    onNavigateToPremium: () -> Unit,
    onCreateNewBlock: () -> Unit,
    onEditBlock: (UnifiedBlockRule) -> Unit,
    onActivateBlockNow: (UnifiedBlockRule) -> Unit,
    onDeactivateManualBlock: (UnifiedBlockRule) -> Unit,
    onPauseBlockForMinutes: (UnifiedBlockRule, Int) -> Unit,
    onArchiveBlock: (UnifiedBlockRule) -> Unit,
    onRestoreArchivedBlock: (UnifiedBlockRule) -> Unit,
    onDuplicateBlock: (UnifiedBlockRule) -> Unit,
    onDeleteBlock: (UnifiedBlockRule) -> Unit,
    onOpenWeeklyReport: () -> Unit
) {
    val context = LocalContext.current
    val authResolver = remember { AuthResolver.getInstance(context) }
    var primaryTab by remember(initialPrimaryTab) { mutableStateOf(initialPrimaryTab) }
    var customFilter by remember(initialCustomFilter) { mutableStateOf(initialCustomFilter) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var isAccessibilityGranted by remember {
        mutableStateOf(checkAccessibilityEnabled(context))
    }
    var isUsageStatsGranted by remember {
        mutableStateOf(checkUsageStatsEnabled(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationHelper.getInstance(context).updateActiveProtectionStatusNotification(
                activeAlwaysOnNames = alwaysOnShieldsConfig.activeShieldTypes().map { it.pillLabel },
                activeCustomRuleCount = rules.count { it.isEffectivelyActive() }
            )
        }
    }

    val ensureNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            nowWall = System.currentTimeMillis()
            isAccessibilityGranted = checkAccessibilityEnabled(context)
            isUsageStatsGranted = checkUsageStatsEnabled(context)
            delay(5_000L)
        }
    }

    var pendingFrictionRequest by remember { mutableStateOf<PendingRuleFrictionRequest?>(null) }

    val nonArchivedRules = remember(rules) {
        rules.filter { it.status != BlockRuleStatus.ARCHIVED }
    }
    val archivedRules = remember(rules) {
        rules.filter { it.status == BlockRuleStatus.ARCHIVED }
    }
    val scheduledRulesCount = remember(nonArchivedRules) {
        nonArchivedRules.count { it.weeklySchedules.isNotEmpty() }
    }
    val limitedRulesCount = remember(nonArchivedRules) {
        nonArchivedRules.count { it.screenTimeLimits.isNotEmpty() || it.openLimits.isNotEmpty() }
    }

    val filteredNonArchivedRules = remember(nonArchivedRules, customFilter) {
        when (customFilter) {
            CustomRulesFilter.ALL -> nonArchivedRules
            CustomRulesFilter.SCHEDULES -> nonArchivedRules.filter { it.weeklySchedules.isNotEmpty() }
            CustomRulesFilter.LIMITS -> nonArchivedRules.filter {
                it.screenTimeLimits.isNotEmpty() || it.openLimits.isNotEmpty()
            }
            CustomRulesFilter.ARCHIVE -> emptyList()
        }
    }

    val (currentlyBlockingRules, notBlockingRules) = remember(
        filteredNonArchivedRules,
        nowElapsed,
        nowWall
    ) {
        filteredNonArchivedRules.partition { rule ->
            isRuleVisiblyBlockingNow(rule, nowElapsed, nowWall)
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

    if (pendingFrictionRequest != null) {
        when (val req = pendingFrictionRequest!!) {
            is PendingRuleFrictionRequest.RulePinChallenge -> {
                RulePinChallengeDialog(
                    rule = req.rule,
                    authResolver = authResolver,
                    authTarget = req.target,
                    actionLabel = req.actionLabel,
                    onDismiss = { pendingFrictionRequest = null },
                    onVerified = {
                        val action = req.onVerified
                        pendingFrictionRequest = null
                        action()
                    }
                )
            }
            is PendingRuleFrictionRequest.PauseRule,
            is PendingRuleFrictionRequest.ActionAfterChallenge -> {
                val targetRule = when (req) {
                    is PendingRuleFrictionRequest.PauseRule -> req.rule
                    is PendingRuleFrictionRequest.ActionAfterChallenge -> req.rule
                    is PendingRuleFrictionRequest.RulePinChallenge -> null
                }
                if (targetRule != null) {
                    PauseFrictionChallengeDialog(
                        rule = targetRule,
                        isPauseFlow = req is PendingRuleFrictionRequest.PauseRule,
                        actionLabel = when (req) {
                            is PendingRuleFrictionRequest.PauseRule -> "Pause"
                            is PendingRuleFrictionRequest.ActionAfterChallenge -> req.actionLabel
                            is PendingRuleFrictionRequest.RulePinChallenge -> "Unlock"
                        },
                        onDismiss = { pendingFrictionRequest = null },
                        onPauseConfirmed = { minutes ->
                            onPauseBlockForMinutes(targetRule, minutes)
                            pendingFrictionRequest = null
                        },
                        onActionVerified = {
                            if (req is PendingRuleFrictionRequest.ActionAfterChallenge) {
                                req.onVerified()
                            }
                            pendingFrictionRequest = null
                        }
                    )
                }
            }
        }
    }

    val requestPauseRule: (UnifiedBlockRule) -> Unit = { rule ->
        if (rule.authType == AuthType.RULE_PIN) {
            val target = authResolver.resolveChallenge(rule)
            if (target !is AuthTarget.PassThrough) {
                pendingFrictionRequest = PendingRuleFrictionRequest.RulePinChallenge(
                    rule = rule,
                    target = target,
                    actionLabel = "Pause",
                    onVerified = { pendingFrictionRequest = PendingRuleFrictionRequest.PauseRule(rule) }
                )
            } else {
                pendingFrictionRequest = PendingRuleFrictionRequest.PauseRule(rule)
            }
        } else {
            pendingFrictionRequest = PendingRuleFrictionRequest.PauseRule(rule)
        }
    }

    val gateDestructiveRuleAction: (UnifiedBlockRule, String, () -> Unit) -> Unit = { rule, label, action ->
        val isProtectedRule = rule.status == BlockRuleStatus.ACTIVE || rule.status == BlockRuleStatus.PAUSED_UNTIL
        if (!isProtectedRule) {
            action()
        } else {
            val isEnforcingNow = isRuleVisiblyBlockingNow(rule, nowElapsed, nowWall)
            val challengeRule = if (
                !isEnforcingNow &&
                rule.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING &&
                !rule.pauseOption.protectOutsideScheduleWindow
            ) {
                rule.copy(
                    pauseOption = rule.pauseOption.copy(
                        type = PauseOptionType.DELAY,
                        delaySeconds = 15
                    )
                )
            } else {
                rule
            }

            if (rule.authType == AuthType.RULE_PIN) {
                val target = authResolver.resolveChallenge(rule)
                if (target !is AuthTarget.PassThrough) {
                    pendingFrictionRequest = PendingRuleFrictionRequest.RulePinChallenge(
                        rule = challengeRule,
                        target = target,
                        actionLabel = label,
                        onVerified = action
                    )
                } else {
                    pendingFrictionRequest = PendingRuleFrictionRequest.ActionAfterChallenge(
                        rule = challengeRule,
                        actionLabel = label,
                        onVerified = action
                    )
                }
            } else {
                pendingFrictionRequest = PendingRuleFrictionRequest.ActionAfterChallenge(
                    rule = challengeRule,
                    actionLabel = label,
                    onVerified = action
                )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (primaryTab == BlocksPrimaryTab.CUSTOM_RULES) {
                FloatingActionButton(
                    onClick = onCreateNewBlock,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("unified_create_block_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create block",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Material 3 Segmented Sub-Tab Shell: Custom Rules | Always-On Shields
            item(key = "primary_segmented_tabs") {
                val activeShieldCount = alwaysOnShieldsConfig.activeCount()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocks_primary_segmented_tabs")
                ) {
                    SegmentedButton(
                        selected = primaryTab == BlocksPrimaryTab.CUSTOM_RULES,
                        onClick = { primaryTab = BlocksPrimaryTab.CUSTOM_RULES },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("tab_custom_rules")
                    ) {
                        Text(
                            text = "Custom Rules",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SegmentedButton(
                        selected = primaryTab == BlocksPrimaryTab.ALWAYS_ON_SHIELDS,
                        onClick = { primaryTab = BlocksPrimaryTab.ALWAYS_ON_SHIELDS },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("tab_always_on_shields")
                    ) {
                        Text(
                            text = if (activeShieldCount > 0) {
                                "Always-On ($activeShieldCount)"
                            } else {
                                "Always-On Shields"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Contextual "Permissions needed" banner if Accessibility or Usage Access is missing
            if (!isAccessibilityGranted || !isUsageStatsGranted) {
                item(key = "permissions_needed") {
                    PermissionsNeededCard(
                        onGivePermission = {
                            if (!isAccessibilityGranted) {
                                showAccessibilityDisclosure = true
                            } else {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            when (primaryTab) {
                BlocksPrimaryTab.CUSTOM_RULES -> {
                    // Pinned Compact Always-On Shields Status Banner
                    item(key = "always_on_status_banner") {
                        AlwaysOnShieldsStatusBanner(
                            config = alwaysOnShieldsConfig,
                            onOpenAlwaysOnTab = { primaryTab = BlocksPrimaryTab.ALWAYS_ON_SHIELDS }
                        )
                    }

                    // Filter Chips Row: All | Schedules | Limits | Archive + Reports shortcut
                    item(key = "custom_rules_filter_row") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                FilterChip(
                                    selected = customFilter == CustomRulesFilter.ALL,
                                    onClick = { customFilter = CustomRulesFilter.ALL },
                                    label = { Text("All (${nonArchivedRules.size})") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("filter_all")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = customFilter == CustomRulesFilter.SCHEDULES,
                                    onClick = { customFilter = CustomRulesFilter.SCHEDULES },
                                    label = {
                                        Text(
                                            if (scheduledRulesCount > 0) "Schedules ($scheduledRulesCount)"
                                            else "Schedules"
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("filter_schedules")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = customFilter == CustomRulesFilter.LIMITS,
                                    onClick = { customFilter = CustomRulesFilter.LIMITS },
                                    label = {
                                        Text(
                                            if (limitedRulesCount > 0) "Limits ($limitedRulesCount)"
                                            else "Limits"
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("filter_limits")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = customFilter == CustomRulesFilter.ARCHIVE,
                                    onClick = { customFilter = CustomRulesFilter.ARCHIVE },
                                    label = {
                                        Text(
                                            if (archivedRules.isNotEmpty()) "Archive (${archivedRules.size})"
                                            else "Archive"
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("subtab_archive")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onOpenWeeklyReport,
                                    label = { Text("Reports") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.BarChart,
                                            contentDescription = "Weekly usage report",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("open_weekly_report_chip")
                                )
                            }
                        }
                    }

                    if (customFilter == CustomRulesFilter.ARCHIVE) {
                        if (archivedRules.isEmpty()) {
                            item(key = "empty_archive") {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Inventory2,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "No archived blocks",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Archived block rules can be restored or duplicated anytime.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(archivedRules, key = { it.id }) { rule ->
                                ArchivedBlockRuleCard(
                                    context = context,
                                    rule = rule,
                                    onRestore = {
                                        ensureNotificationPermission()
                                        onRestoreArchivedBlock(rule)
                                    },
                                    onDuplicate = { onDuplicateBlock(rule) },
                                    onDelete = { onDeleteBlock(rule) }
                                )
                            }
                        }
                    } else {
                        if (currentlyBlockingRules.isNotEmpty()) {
                            item(key = "section_blocking") {
                                Text(
                                    text = "Blocking",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            items(currentlyBlockingRules, key = { it.id }) { rule ->
                                UnifiedBlockRuleCard(
                                    context = context,
                                    rule = rule,
                                    isEnforcingNow = true,
                                    nowElapsed = nowElapsed,
                                    nowWall = nowWall,
                                    onCardClick = { onEditBlock(rule) },
                                    onQuickActionClick = {
                                        requestPauseRule(rule)
                                    },
                                    onMenuPause = {
                                        requestPauseRule(rule)
                                    },
                                    onMenuBlockNow = {
                                        ensureNotificationPermission()
                                        onActivateBlockNow(rule)
                                    },
                                    onMenuEdit = { onEditBlock(rule) },
                                    onMenuArchive = {
                                        gateDestructiveRuleAction(rule, "Archive") { onArchiveBlock(rule) }
                                    },
                                    onMenuDelete = {
                                        gateDestructiveRuleAction(rule, "Delete") { onDeleteBlock(rule) }
                                    }
                                )
                            }
                        }

                        if (filteredNonArchivedRules.isEmpty()) {
                            item(key = "empty_custom_rules") {
                                if (customFilter == CustomRulesFilter.ALL) {
                                    EmptyUnifiedBlocksCard(onCreateNewBlock = onCreateNewBlock)
                                } else {
                                    EmptyFilteredRulesCard(
                                        filter = customFilter,
                                        onCreateNewBlock = onCreateNewBlock
                                    )
                                }
                            }
                        } else {
                            item(key = "section_not_blocking") {
                                Text(
                                    text = "Not blocking",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }

                            if (notBlockingRules.isEmpty()) {
                                item(key = "all_enforcing_note") {
                                    Text(
                                        text = "All active blocks in this view are currently enforcing.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(notBlockingRules, key = { it.id }) { rule ->
                                    UnifiedBlockRuleCard(
                                        context = context,
                                        rule = rule,
                                        isEnforcingNow = false,
                                        nowElapsed = nowElapsed,
                                        nowWall = nowWall,
                                        onCardClick = { onEditBlock(rule) },
                                        onQuickActionClick = {
                                            if (rule.status == BlockRuleStatus.INACTIVE_MANUAL ||
                                                rule.isPaused(nowElapsed, nowWall)
                                            ) {
                                                ensureNotificationPermission()
                                                onActivateBlockNow(rule)
                                            } else {
                                                gateDestructiveRuleAction(rule, "Disable") {
                                                    onDeactivateManualBlock(rule)
                                                }
                                            }
                                        },
                                        onMenuPause = {
                                            requestPauseRule(rule)
                                        },
                                        onMenuBlockNow = {
                                            ensureNotificationPermission()
                                            onActivateBlockNow(rule)
                                        },
                                        onMenuDeactivate = {
                                            gateDestructiveRuleAction(rule, "Disable") {
                                                onDeactivateManualBlock(rule)
                                            }
                                        },
                                        onMenuEdit = {
                                            onEditBlock(rule)
                                        },
                                        onMenuArchive = {
                                            gateDestructiveRuleAction(rule, "Archive") {
                                                onArchiveBlock(rule)
                                            }
                                        },
                                        onMenuDelete = {
                                            gateDestructiveRuleAction(rule, "Delete") {
                                                onDeleteBlock(rule)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                BlocksPrimaryTab.ALWAYS_ON_SHIELDS -> {
                    item(key = "always_on_shields_content") {
                        AlwaysOnShieldsTabContent(
                            config = alwaysOnShieldsConfig,
                            reelsScrolledToday = reelsScrolledToday,
                            isPremiumUser = isPremiumUser,
                            hasGlobalPinOverride = hasGlobalPinOverride,
                            onUpdateConfig = onUpdateAlwaysOnShieldsConfig,
                            onEnsureNotificationPermission = ensureNotificationPermission,
                            onNavigateToPremium = onNavigateToPremium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFilteredRulesCard(
    filter: CustomRulesFilter,
    onCreateNewBlock: () -> Unit
) {
    val title = when (filter) {
        CustomRulesFilter.SCHEDULES -> "No scheduled blocks yet"
        CustomRulesFilter.LIMITS -> "No time or launch limits yet"
        else -> "No blocks match this filter"
    }
    val subtitle = when (filter) {
        CustomRulesFilter.SCHEDULES -> "Create a unified block with a weekly schedule to automate your focus windows."
        CustomRulesFilter.LIMITS -> "Add screen time or daily open limits to a block card to control usage."
        else -> "Tap + to create a custom rule."
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (filter == CustomRulesFilter.SCHEDULES) Icons.Outlined.Schedule else Icons.Outlined.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onCreateNewBlock,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Block")
            }
        }
    }
}

@Composable
private fun PermissionsNeededCard(
    onGivePermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_needed_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "Permissions needed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = "Needed for blocking apps, websites, notifications, and displaying your usage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )

            Button(
                onClick = onGivePermission,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("give_permission_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Give permission",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UnifiedBlockRuleCard(
    context: Context,
    rule: UnifiedBlockRule,
    isEnforcingNow: Boolean,
    nowElapsed: Long,
    nowWall: Long,
    onCardClick: () -> Unit,
    onQuickActionClick: () -> Unit,
    onMenuPause: () -> Unit,
    onMenuBlockNow: () -> Unit,
    onMenuDeactivate: () -> Unit = {},
    onMenuEdit: () -> Unit,
    onMenuArchive: () -> Unit,
    onMenuDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val multiTargetSummary = remember(rule) { buildRuleTargetsSummary(rule) }
    val lockBadgeLabel = remember(rule.pauseOption) { buildRuleLockBadge(rule.pauseOption) }
    val isManuallyOffOrPaused = !isEnforcingNow && (
        rule.status == BlockRuleStatus.INACTIVE_MANUAL ||
            rule.isPaused(nowElapsed, nowWall)
        )

    Surface(
        onClick = onCardClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isEnforcingNow) 1.5.dp else 1.dp,
            color = if (isEnforcingNow) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unified_block_card_${rule.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (rule.blockNotifications) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = "Notifications blocked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        if (lockBadgeLabel != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (rule.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = if (rule.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = lockBadgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (rule.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = rule.statusSubtitle(nowElapsed, nowWall),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnforcingNow) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    if (multiTargetSummary.isNotBlank()) {
                        Text(
                            text = multiTargetSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onQuickActionClick,
                        shape = RoundedCornerShape(14.dp),
                        color = if (isEnforcingNow) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("quick_toggle_block_${rule.id}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isManuallyOffOrPaused) {
                                    Icons.Outlined.Block
                                } else {
                                    Icons.Outlined.Pause
                                },
                                contentDescription = when {
                                    isEnforcingNow -> "Pause block"
                                    isManuallyOffOrPaused -> "Activate block"
                                    else -> "Disable schedule"
                                },
                                tint = when {
                                    isEnforcingNow -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isManuallyOffOrPaused -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("overflow_menu_block_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            if (isEnforcingNow) {
                                DropdownMenuItem(
                                    text = { Text("Pause") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.PauseCircleOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onMenuPause()
                                    }
                                )
                            } else if (isManuallyOffOrPaused) {
                                DropdownMenuItem(
                                    text = { Text("Block now") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Block, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onMenuBlockNow()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Disable") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.PauseCircleOutline, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onMenuDeactivate()
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMenuEdit()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMenuArchive()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onMenuDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Visual Target Chips (Apps, [IG] Reels, [YT] Shorts, [Globe] domains, [ABC] keywords)
            if (rule.hasAnyTargets()) {
                UnifiedTargetChipsFlowRow(
                    context = context,
                    appTargets = rule.appTargets,
                    blockedDomains = rule.blockedDomains,
                    blockedKeywords = rule.blockedKeywords,
                    includeInBrowserShortform = rule.includeInBrowserShortform
                )
            }
        }
    }
}

private fun buildRuleTargetsSummary(rule: UnifiedBlockRule): String {
    val parts = mutableListOf<String>()
    val fullAppsCount = rule.appTargets.count { it.value == AppInterceptionLevel.BLOCK_ENTIRE_APP }
    val shortformCount = rule.appTargets.count { it.value == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY }
    if (fullAppsCount > 0) {
        parts.add(if (fullAppsCount == 1) "1 App" else "$fullAppsCount Apps")
    }
    if (shortformCount > 0 || rule.includeInBrowserShortform) {
        val label = if (shortformCount > 0 && rule.includeInBrowserShortform) {
            "$shortformCount Reels/Shorts + Browser"
        } else if (shortformCount > 0) {
            if (shortformCount == 1) "1 Short-Form Feed" else "$shortformCount Short-Form Feeds"
        } else {
            "Browser Short Videos"
        }
        parts.add(label)
    }
    if (rule.blockedDomains.isNotEmpty()) {
        val count = rule.blockedDomains.size
        parts.add(if (count == 1) "1 Website" else "$count Websites")
    }
    if (rule.blockedKeywords.isNotEmpty()) {
        val count = rule.blockedKeywords.size
        parts.add(if (count == 1) "1 Keyword" else "$count Keywords")
    }
    return parts.joinToString(" - ")
}

private fun buildRuleLockBadge(pauseOption: PauseOptionConfig): String? {
    return when (pauseOption.type) {
        PauseOptionType.STRICT_NO_PAUSING -> "Strict"
        PauseOptionType.DELAY -> "${pauseOption.delaySeconds}s Delay"
        PauseOptionType.TYPE_OVER_TEXT -> "Type Text"
        PauseOptionType.SCAN_QR_OR_BARCODE -> "QR Lock"
        PauseOptionType.SCAN_NFC_TAG -> "NFC Lock"
    }
}

@Composable
private fun ArchivedBlockRuleCard(
    context: Context,
    rule: UnifiedBlockRule,
    onRestore: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Archived",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onRestore) {
                        Text("Restore")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Duplicate"
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (rule.hasAnyTargets()) {
                UnifiedTargetChipsFlowRow(
                    context = context,
                    appTargets = rule.appTargets,
                    blockedDomains = rule.blockedDomains,
                    blockedKeywords = rule.blockedKeywords,
                    includeInBrowserShortform = rule.includeInBrowserShortform
                )
            }
        }
    }
}

@Composable
private fun EmptyUnifiedBlocksCard(
    onCreateNewBlock: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Create your first All-in-One Block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Combine apps, Reels/Shorts, websites, keywords, schedules, and pause friction in one card.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCreateNewBlock,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Block")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PauseFrictionChallengeDialog(
    rule: UnifiedBlockRule,
    isPauseFlow: Boolean,
    actionLabel: String,
    onDismiss: () -> Unit,
    onPauseConfirmed: (Int) -> Unit,
    onActionVerified: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val config = rule.pauseOption

    val delayDurationSeconds = if (config.type == PauseOptionType.DELAY) {
        config.delaySeconds.coerceAtLeast(3)
    } else {
        0
    }
    val delayDeadlineElapsedMs = rememberSaveable(rule.id, config.type, delayDurationSeconds) {
        if (config.type == PauseOptionType.DELAY) {
            SystemClock.elapsedRealtime() + delayDurationSeconds * 1_000L
        } else {
            0L
        }
    }
    var remainingDelaySeconds by remember(rule.id, config.type, delayDeadlineElapsedMs) {
        val remMs = (delayDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        mutableIntStateOf(((remMs + 999L) / 1_000L).toInt())
    }
    var typedInput by remember { mutableStateOf("") }
    var scannedCodeMatched by remember { mutableStateOf(false) }
    var nfcTagMatched by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    val maxPauseMins = config.maxPauseDurationMinutes.coerceIn(1, 720)
    val durationOptions = remember(maxPauseMins) {
        PauseOptionConfig.computePauseDurationOptions(maxPauseMins)
    }
    var selectedPauseMinutes by remember(rule.id, maxPauseMins) {
        mutableIntStateOf(durationOptions.firstOrNull { it >= 15 } ?: durationOptions.last())
    }

    // Monotonic countdown timer for DELAY mode (resists recomposition drift & clock rollback)
    LaunchedEffect(config.type, delayDeadlineElapsedMs) {
        if (config.type == PauseOptionType.DELAY) {
            while (true) {
                val remMs = (delayDeadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                remainingDelaySeconds = ((remMs + 999L) / 1_000L).toInt()
                if (remMs <= 0L) break
                delay(250L)
            }
        }
    }

    // NFC Reader Mode hook when SCAN_NFC_TAG is active
    DisposableEffect(config.type) {
        val activity = context as? Activity
        val nfcAdapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        if (config.type == PauseOptionType.SCAN_NFC_TAG && activity != null && nfcAdapter != null && nfcAdapter.isEnabled) {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val tagHex = tag.id?.joinToString("") { "%02X".format(it) }.orEmpty()
                val expected = OverrideChallengeValidator.sanitizeNfcTagIdHex(config.registeredTagIdHex)
                activity.runOnUiThread {
                    if (expected == OverrideChallengeValidator.ANY_NFC_TAG || expected == tagHex) {
                        nfcTagMatched = true
                        statusError = null
                    } else {
                        statusError = "Scanned NFC tag ($tagHex) does not match registered tag."
                    }
                }
            }
            runCatching {
                nfcAdapter.enableReaderMode(
                    activity,
                    callback,
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or
                        NfcAdapter.FLAG_READER_NFC_V,
                    Bundle.EMPTY
                )
            }
        }
        onDispose {
            if (config.type == PauseOptionType.SCAN_NFC_TAG && activity != null && nfcAdapter != null) {
                runCatching { nfcAdapter.disableReaderMode(activity) }
            }
        }
    }

    val barcodeVerifyLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val expected = config.registeredCodeValue.trim()
            if (expected.isNotEmpty() && expected == contents.trim()) {
                scannedCodeMatched = true
                statusError = null
            } else {
                statusError = if (expected.isEmpty()) {
                    "No QR/barcode is registered for this rule."
                } else {
                    "Scanned code does not match the registered barcode/QR."
                }
            }
        }
    }

    val typeOverEval = remember(typedInput, config.customOrRandomText, config.type) {
        if (config.type == PauseOptionType.TYPE_OVER_TEXT) {
            OverrideChallengeValidator.evaluateTypeOverText(
                typedRaw = typedInput,
                targetRaw = config.customOrRandomText
            )
        } else {
            null
        }
    }

    val isChallengeSatisfied = when (config.type) {
        PauseOptionType.STRICT_NO_PAUSING -> false
        PauseOptionType.DELAY -> remainingDelaySeconds <= 0
        PauseOptionType.TYPE_OVER_TEXT -> typeOverEval?.isComplete == true
        PauseOptionType.SCAN_QR_OR_BARCODE -> scannedCodeMatched
        PauseOptionType.SCAN_NFC_TAG -> nfcTagMatched
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (config.type) {
                    PauseOptionType.STRICT_NO_PAUSING -> "Strict Mode Active"
                    else -> "$actionLabel \"${rule.name}\""
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (config.type) {
                    PauseOptionType.STRICT_NO_PAUSING -> {
                        Text(
                            text = "This block uses Strict: no pausing. You cannot pause, edit, or delete it while a schedule or limit is active.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PauseOptionType.DELAY -> {
                        if (remainingDelaySeconds > 0) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${remainingDelaySeconds}s",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Take a mindful breath while waiting to unlock...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                text = "Delay complete.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    PauseOptionType.TYPE_OVER_TEXT -> {
                        val targetPassage = OverrideChallengeValidator.normalizeOverrideText(config.customOrRandomText)
                        val eval = typeOverEval
                        Text(
                            text = "Type the following text accurately (paste is disabled; smart quotes auto-normalized):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = targetPassage,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        OutlinedTextField(
                            value = typedInput,
                            onValueChange = { next ->
                                if (!OverrideChallengeValidator.isPasteAttempt(typedInput, next)) {
                                    typedInput = next
                                    statusError = null
                                } else {
                                    clipboardManager.setText(AnnotatedString(""))
                                    statusError = "Pasting is disabled. Please type the passage character by character."
                                }
                            },
                            placeholder = { Text("Type passage here...") },
                            minLines = 2,
                            isError = eval?.firstMismatchIndex != null,
                            supportingText = {
                                if (eval != null) {
                                    when {
                                        eval.isComplete -> Text(
                                            text = "Passage matched (${eval.targetLength}/${eval.targetLength}).",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        eval.firstMismatchIndex != null -> Text(
                                            text = "Mismatch at character ${eval.firstMismatchIndex + 1} (${eval.matchedChars}/${eval.targetLength} matched).",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        else -> Text(
                                            text = "${eval.matchedChars} / ${eval.targetLength} characters matched",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("type_over_text_challenge_input")
                        )
                    }

                    PauseOptionType.SCAN_QR_OR_BARCODE -> {
                        Text(
                            text = "Scan your registered QR code or barcode to unlock this action.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val options = ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                    setPrompt("Scan registered QR or barcode")
                                    setBeepEnabled(false)
                                    setOrientationLocked(true)
                                }
                                runCatching { barcodeVerifyLauncher.launch(options) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (scannedCodeMatched) "Code Verified" else "Scan QR / Barcode")
                        }
                    }

                    PauseOptionType.SCAN_NFC_TAG -> {
                        val nfcAdapter = remember {
                            runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
                        }
                        Text(
                            text = when {
                                nfcAdapter == null -> "NFC hardware is not available on this device."
                                !nfcAdapter.isEnabled -> "NFC is currently disabled. Enable NFC in device settings to tap your registered tag."
                                else -> "Hold your registered NFC tag against the back of your device."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (nfcAdapter != null && !nfcAdapter.isEnabled) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_NFC_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Nfc, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open NFC Settings")
                            }
                        }
                    }
                }

                if (statusError != null) {
                    Text(
                        text = statusError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (isPauseFlow && isChallengeSatisfied) {
                    Text(
                        text = "Pause duration (Max $maxPauseMins min):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durationOptions.forEach { mins ->
                            FilterChip(
                                selected = selectedPauseMinutes == mins,
                                onClick = { selectedPauseMinutes = mins },
                                label = {
                                    Text(if (mins >= 60 && mins % 60 == 0) "${mins / 60}h" else if (mins > 60) "${mins / 60}h ${mins % 60}m" else "${mins}m")
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (config.type != PauseOptionType.STRICT_NO_PAUSING) {
                Button(
                    enabled = isChallengeSatisfied,
                    onClick = {
                        if (isPauseFlow) {
                            onPauseConfirmed(selectedPauseMinutes)
                        } else {
                            onActionVerified()
                        }
                    },
                    modifier = Modifier.testTag("confirm_friction_challenge_button")
                ) {
                    Text(if (isPauseFlow) "Pause Block" else actionLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun isRuleVisiblyBlockingNow(
    rule: UnifiedBlockRule,
    nowElapsed: Long,
    nowWall: Long
): Boolean {
    if (!rule.isEffectivelyActive(nowElapsed, nowWall)) {
        return false
    }
    if (!rule.hasAnyTriggers()) {
        return true
    }
    if (rule.weeklySchedules.any { it.isCurrentlyInWindow(nowWall) }) {
        return true
    }
    // Rules with active daily/hourly screen-time or open limits are also in the active monitoring group
    return rule.screenTimeLimits.isNotEmpty() || rule.openLimits.isNotEmpty()
}

private fun checkAccessibilityEnabled(context: Context): Boolean {
    return runCatching {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedComponent =
            "${context.packageName}/${AmniShieldAccessibilityService::class.java.name}"
        enabledServices.contains(expectedComponent, ignoreCase = true) ||
            enabledServices.contains(AmniShieldAccessibilityService::class.java.simpleName, ignoreCase = true)
    }.getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun checkUsageStatsEnabled(context: Context): Boolean {
    return runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
}

@Composable
private fun RulePinChallengeDialog(
    rule: UnifiedBlockRule,
    authResolver: AuthResolver,
    authTarget: AuthTarget,
    actionLabel: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val isLockedOut = authTarget is AuthTarget.LockedOut

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$actionLabel \"${rule.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isLockedOut) {
                    val secs = (((authTarget as AuthTarget.LockedOut).remainingMillis + 999L) / 1000L).coerceAtLeast(1L)
                    Text(
                        text = "Too many failed PIN attempts. Try again in ${secs}s.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Enter this rule's custom PIN to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 12 && it.all { ch -> ch.isDigit() }) {
                                pinInput = it
                                errorMsg = null
                            }
                        },
                        label = { Text("Rule PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (!isLockedOut) {
                Button(
                    enabled = pinInput.isNotEmpty(),
                    onClick = {
                        when (val result = authResolver.verifyChallenge(authTarget, pinInput)) {
                            is AuthResult.Success -> onVerified()
                            is AuthResult.InvalidPin -> {
                                errorMsg = if (result.isLockedOut) {
                                    "Too many failed attempts. Temporarily locked out."
                                } else {
                                    "Incorrect PIN (${result.attemptsRemaining} attempts left)."
                                }
                            }
                            is AuthResult.LockedOut -> {
                                val secs = ((result.remainingMillis + 999L) / 1000L).coerceAtLeast(1L)
                                errorMsg = "Locked out for ${secs}s."
                            }
                        }
                    }
                ) {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
