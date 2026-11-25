package com.alhaq.amnishield.ui.screens

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alhaq.amnishield.data.blockers.*
import com.google.gson.Gson
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

internal fun resolveBlockRuleForDraftOrSave(
    initialRule: UnifiedBlockRule?,
    existingRulesForCopy: List<UnifiedBlockRule>,
    ruleName: String,
    appTargets: Map<String, AppInterceptionLevel>,
    blockedDomains: List<String>,
    blockedKeywords: List<String>,
    blockNewlyInstalled: Boolean,
    includeInBrowserShortform: Boolean,
    blockAppLaunch: Boolean,
    blockNotifications: Boolean,
    weeklySchedules: List<WeeklyScheduleTrigger>,
    screenTimeLimits: List<ScreenTimeLimitTrigger>,
    openLimits: List<OpenLimitTrigger>,
    pauseOption: PauseOptionConfig,
    interceptionStyle: BlockInterceptionStyle,
    shortformInterceptionStyle: ShortformInterceptionStyle,
    customWarningMessage: String,
    closeDelaySeconds: Int,
    isFinalSave: Boolean
): UnifiedBlockRule {
    val hasSchedulesOrLimits =
        weeklySchedules.isNotEmpty() || screenTimeLimits.isNotEmpty() || openLimits.isNotEmpty()
    val isPersistedRule = initialRule != null && existingRulesForCopy.any { it.id == initialRule.id }
    val resolvedStatus = if (!isFinalSave) {
        initialRule?.status ?: BlockRuleStatus.INACTIVE_MANUAL
    } else if (isPersistedRule) {
        initialRule!!.status
    } else if (hasSchedulesOrLimits) {
        BlockRuleStatus.ACTIVE
    } else {
        initialRule?.status ?: BlockRuleStatus.INACTIVE_MANUAL
    }
    val resolvedName = if (isFinalSave) {
        ruleName.trim().ifBlank { "Block" }
    } else {
        ruleName
    }
    return UnifiedBlockRule(
        id = initialRule?.id ?: UUID.randomUUID().toString(),
        name = resolvedName,
        status = resolvedStatus,
        pausedUntilElapsedRealtime = initialRule?.pausedUntilElapsedRealtime ?: 0L,
        pausedUntilWallClockMillis = initialRule?.pausedUntilWallClockMillis ?: 0L,
        appTargets = appTargets,
        blockedDomains = blockedDomains,
        blockedKeywords = blockedKeywords,
        blockNewlyInstalledApps = blockNewlyInstalled,
        includeInBrowserShortform = includeInBrowserShortform,
        blockAppLaunch = blockAppLaunch,
        blockNotifications = blockNotifications,
        weeklySchedules = weeklySchedules,
        screenTimeLimits = screenTimeLimits,
        openLimits = openLimits,
        pauseOption = pauseOption,
        interceptionStyle = interceptionStyle,
        shortformInterceptionStyle = shortformInterceptionStyle,
        customWarningMessage = customWarningMessage,
        closeDelaySeconds = closeDelaySeconds,
        createdAt = initialRule?.createdAt ?: System.currentTimeMillis(),
        authType = initialRule?.authType ?: com.alhaq.amnishield.security.AuthType.NONE,
        rulePasswordHash = initialRule?.rulePasswordHash,
        rulePasswordSalt = initialRule?.rulePasswordSalt
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditBlockScreen(
    initialRule: UnifiedBlockRule?,
    existingRulesForCopy: List<UnifiedBlockRule>,
    onDraftUpdated: ((UnifiedBlockRule) -> Unit)? = null,
    onOpenTargetPicker: (
        currentApps: Map<String, AppInterceptionLevel>,
        currentDomains: List<String>,
        currentKeywords: List<String>,
        currentBlockNewlyInstalled: Boolean,
        currentIncludeInBrowserShortform: Boolean,
        onResult: (
            Map<String, AppInterceptionLevel>,
            List<String>,
            List<String>,
            Boolean,
            Boolean
        ) -> Unit
    ) -> Unit,
    onSave: (UnifiedBlockRule) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val gson = remember { Gson() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val draftKey = remember(initialRule?.id) { initialRule?.id ?: "new_block_draft" }
    var draftJson by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(
            if (initialRule != null) gson.toJson(initialRule) else ""
        )
    }
    val restoredInitial = remember(draftKey, initialRule) {
        if (draftJson.isNotBlank()) {
            runCatching { gson.fromJson(draftJson, UnifiedBlockRule::class.java) }.getOrNull() ?: initialRule
        } else {
            initialRule
        }
    }

    var ruleName by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.name ?: "")
    }
    var appTargets by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.appTargets ?: emptyMap())
    }
    var blockedDomains by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.blockedDomains ?: emptyList())
    }
    var blockedKeywords by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.blockedKeywords ?: emptyList())
    }
    var blockNewlyInstalled by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.blockNewlyInstalledApps ?: false)
    }
    var includeInBrowserShortform by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.includeInBrowserShortform ?: false)
    }
    var weeklySchedules by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.weeklySchedules ?: emptyList())
    }
    var screenTimeLimits by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.screenTimeLimits ?: emptyList())
    }
    var openLimits by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.openLimits ?: emptyList())
    }
    var pauseOption by remember(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.pauseOption ?: PauseOptionConfig())
    }
    var interceptionStyle by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.interceptionStyle ?: BlockInterceptionStyle.STANDARD_WARNING_DIALOG)
    }
    var shortformInterceptionStyle by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.shortformInterceptionStyle ?: ShortformInterceptionStyle.REDIRECT_TO_FEED)
    }
    var customWarningMessage by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.customWarningMessage ?: UnifiedBlockRule.DEFAULT_WARNING_MESSAGE)
    }
    var closeDelaySeconds by rememberSaveable(draftKey, initialRule) {
        mutableIntStateOf(restoredInitial?.closeDelaySeconds ?: 3)
    }
    var blockAppLaunch by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.blockAppLaunch ?: true)
    }
    var blockNotifications by rememberSaveable(draftKey, initialRule) {
        mutableStateOf(restoredInitial?.blockNotifications ?: false)
    }
    var targetValidationError by rememberSaveable(draftKey) {
        mutableStateOf<String?>(null)
    }

    fun buildCurrentDraftRule(isFinalSave: Boolean): UnifiedBlockRule {
        return resolveBlockRuleForDraftOrSave(
            initialRule = initialRule,
            existingRulesForCopy = existingRulesForCopy,
            ruleName = ruleName,
            appTargets = appTargets,
            blockedDomains = blockedDomains,
            blockedKeywords = blockedKeywords,
            blockNewlyInstalled = blockNewlyInstalled,
            includeInBrowserShortform = includeInBrowserShortform,
            blockAppLaunch = blockAppLaunch,
            blockNotifications = blockNotifications,
            weeklySchedules = weeklySchedules,
            screenTimeLimits = screenTimeLimits,
            openLimits = openLimits,
            pauseOption = pauseOption,
            interceptionStyle = interceptionStyle,
            shortformInterceptionStyle = shortformInterceptionStyle,
            customWarningMessage = customWarningMessage,
            closeDelaySeconds = closeDelaySeconds,
            isFinalSave = isFinalSave
        )
    }

    LaunchedEffect(
        ruleName,
        appTargets,
        blockedDomains,
        blockedKeywords,
        blockNewlyInstalled,
        includeInBrowserShortform,
        weeklySchedules,
        screenTimeLimits,
        openLimits,
        pauseOption,
        interceptionStyle,
        shortformInterceptionStyle,
        customWarningMessage,
        closeDelaySeconds,
        blockAppLaunch,
        blockNotifications
    ) {
        draftJson = gson.toJson(buildCurrentDraftRule(isFinalSave = false))
    }

    var showTargetsMenu by remember { mutableStateOf(false) }
    var showCopyFromDialog by remember { mutableStateOf(false) }
    var showAddScheduleOrLimitSheet by remember { mutableStateOf(false) }
    var editingWeeklyScheduleId by rememberSaveable { mutableStateOf<String?>(null) }
    var showWeeklyScheduleSheet by rememberSaveable { mutableStateOf(false) }
    var showScreenTimeLimitDialog by rememberSaveable { mutableStateOf<LimitPeriod?>(null) }
    var showOpenLimitDialog by rememberSaveable { mutableStateOf<LimitPeriod?>(null) }
    var showPauseOptionsSheet by remember { mutableStateOf(false) }
    var showPauseConfigDialog by rememberSaveable { mutableStateOf<PauseOptionType?>(null) }
    var showWarningStyleDialog by remember { mutableStateOf(false) }
    var pendingWeakeningSaveCandidate by remember { mutableStateOf<UnifiedBlockRule?>(null) }
    var pendingWeakeningChallengeRule by remember { mutableStateOf<UnifiedBlockRule?>(null) }
    var pendingNewRuleWarningCandidate by remember { mutableStateOf<UnifiedBlockRule?>(null) }

    val persistedOriginal = remember(initialRule?.id, existingRulesForCopy) {
        initialRule?.let { init -> existingRulesForCopy.firstOrNull { it.id == init.id } }
    }

    val barcodeScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            pauseOption = pauseOption.copy(
                registeredCodeValue = contents.trim()
            )
            showPauseConfigDialog = PauseOptionType.SCAN_QR_OR_BARCODE
        } else {
            showPauseConfigDialog = PauseOptionType.SCAN_QR_OR_BARCODE
        }
    }

    val openTargetSelection = {
        targetValidationError = null
        onDraftUpdated?.invoke(buildCurrentDraftRule(isFinalSave = false))
        onOpenTargetPicker(
            appTargets,
            blockedDomains,
            blockedKeywords,
            blockNewlyInstalled,
            includeInBrowserShortform
        ) { newApps, newDomains, newKeywords, newBlockNewlyInstalled, newBrowserShortform ->
            appTargets = newApps
            blockedDomains = newDomains
            blockedKeywords = newKeywords
            blockNewlyInstalled = newBlockNewlyInstalled
            includeInBrowserShortform = newBrowserShortform
        }
    }

    if (showCopyFromDialog) {
        CopyTargetsFromRuleDialog(
            rules = existingRulesForCopy.filter { it.id != initialRule?.id && it.hasAnyTargets() },
            onDismiss = { showCopyFromDialog = false },
            onSelectRule = { sourceRule ->
                appTargets = sourceRule.appTargets
                blockedDomains = sourceRule.blockedDomains
                blockedKeywords = sourceRule.blockedKeywords
                blockNewlyInstalled = sourceRule.blockNewlyInstalledApps
                includeInBrowserShortform = sourceRule.includeInBrowserShortform
                showCopyFromDialog = false
            }
        )
    }

    if (showAddScheduleOrLimitSheet) {
        AddScheduleOrLimitBottomSheet(
            onDismiss = { showAddScheduleOrLimitSheet = false },
            onSelectWeeklySchedule = {
                showAddScheduleOrLimitSheet = false
                editingWeeklyScheduleId = null
                showWeeklyScheduleSheet = true
            },
            onSelectScreenTimeLimit = { period ->
                showAddScheduleOrLimitSheet = false
                showScreenTimeLimitDialog = period
            },
            onSelectOpenLimit = { period ->
                showAddScheduleOrLimitSheet = false
                showOpenLimitDialog = period
            }
        )
    }

    if (showWeeklyScheduleSheet) {
        ConfigureWeeklyScheduleBottomSheet(
            existingSchedules = weeklySchedules,
            initialEditScheduleId = editingWeeklyScheduleId,
            onDismiss = {
                editingWeeklyScheduleId = null
                showWeeklyScheduleSheet = false
            },
            onSaveAll = { normalizedSchedules, adjustments ->
                weeklySchedules = normalizedSchedules
                editingWeeklyScheduleId = null
                showWeeklyScheduleSheet = false
                if (adjustments.isNotEmpty()) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(adjustments.first())
                    }
                }
            }
        )
    }

    if (showScreenTimeLimitDialog != null) {
        val initialPeriod = showScreenTimeLimitDialog!!
        ConfigureScreenTimeLimitDialog(
            initialPeriod = initialPeriod,
            existingLimits = screenTimeLimits,
            onDismiss = { showScreenTimeLimitDialog = null },
            onSave = { trigger ->
                screenTimeLimits = screenTimeLimits.filterNot { it.period == trigger.period } + trigger
                showScreenTimeLimitDialog = null
            }
        )
    }

    if (showOpenLimitDialog != null) {
        val initialPeriod = showOpenLimitDialog!!
        ConfigureOpenLimitDialog(
            initialPeriod = initialPeriod,
            existingLimits = openLimits,
            onDismiss = { showOpenLimitDialog = null },
            onSave = { trigger ->
                openLimits = openLimits.filterNot { it.period == trigger.period } + trigger
                showOpenLimitDialog = null
            }
        )
    }

    if (showPauseOptionsSheet) {
        PauseOptionsBottomSheet(
            currentConfig = pauseOption,
            onDismiss = { showPauseOptionsSheet = false },
            onToggleProtectOutsideWindow = { checked ->
                pauseOption = pauseOption.copy(protectOutsideScheduleWindow = checked)
            },
            onSelectOption = { selectedType ->
                showPauseOptionsSheet = false
                when (selectedType) {
                    PauseOptionType.STRICT_NO_PAUSING -> {
                        pauseOption = pauseOption.copy(type = PauseOptionType.STRICT_NO_PAUSING)
                    }
                    PauseOptionType.DELAY,
                    PauseOptionType.TYPE_OVER_TEXT,
                    PauseOptionType.SCAN_QR_OR_BARCODE,
                    PauseOptionType.SCAN_NFC_TAG -> {
                        showPauseConfigDialog = selectedType
                    }
                }
            }
        )
    }

    if (pendingWeakeningSaveCandidate != null && pendingWeakeningChallengeRule != null) {
        val candidateToSave = pendingWeakeningSaveCandidate!!
        PauseFrictionChallengeDialog(
            rule = pendingWeakeningChallengeRule!!,
            isPauseFlow = false,
            actionLabel = "Save Weakening Edit",
            onDismiss = {
                pendingWeakeningSaveCandidate = null
                pendingWeakeningChallengeRule = null
            },
            onPauseConfirmed = {},
            onActionVerified = {
                pendingWeakeningSaveCandidate = null
                pendingWeakeningChallengeRule = null
                onSave(candidateToSave)
            }
        )
    }

    if (pendingNewRuleWarningCandidate != null) {
        val candidate = pendingNewRuleWarningCandidate!!
        AlertDialog(
            onDismissRequest = { pendingNewRuleWarningCandidate = null },
            title = { Text("Enable Strong Rule Protection?") },
            text = {
                Text(
                    text = if (candidate.pauseOption.protectOutsideScheduleWindow) {
                        "Full change protection is enabled. Adding new apps, sites, or schedules remains free, while removing, disabling, or weakening will be locked even outside active schedule windows."
                    } else {
                        "Strict Mode is enabled. Adding new apps, sites, or schedules remains free, while weakening or deleting is locked during active hours (and uses a 15s reflection delay outside schedule windows)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingNewRuleWarningCandidate = null
                        onSave(candidate)
                    }
                ) {
                    Text("Activate Rule")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingNewRuleWarningCandidate = null }) {
                    Text("Review")
                }
            }
        )
    }

    if (showPauseConfigDialog != null) {
        val configType = showPauseConfigDialog!!
        ConfigurePauseOptionDialog(
            type = configType,
            currentConfig = pauseOption,
            onDismiss = { showPauseConfigDialog = null },
            onRequestBarcodeScan = {
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Scan a QR code or barcode to register for pausing")
                    setBeepEnabled(false)
                    setOrientationLocked(true)
                }
                runCatching { barcodeScanLauncher.launch(options) }
            },
            onSave = { updatedConfig ->
                pauseOption = updatedConfig
                showPauseConfigDialog = null
            }
        )
    }

    if (showWarningStyleDialog) {
        ConfigureWarningStyleDialog(
            currentInterceptionStyle = interceptionStyle,
            currentShortformStyle = shortformInterceptionStyle,
            currentMessage = customWarningMessage,
            currentCloseDelaySeconds = closeDelaySeconds,
            onDismiss = { showWarningStyleDialog = false },
            onSave = { newStyle, newShortformStyle, newMsg, newDelay ->
                interceptionStyle = newStyle
                shortformInterceptionStyle = newShortformStyle
                customWarningMessage = newMsg
                closeDelaySeconds = newDelay
                showWarningStyleDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                },
                onClick = {
                    val hasAnyTargets =
                        appTargets.isNotEmpty() ||
                            blockedDomains.isNotEmpty() ||
                            blockedKeywords.isNotEmpty() ||
                            blockNewlyInstalled ||
                            includeInBrowserShortform
                    if (!hasAnyTargets) {
                        targetValidationError = "Select at least one app, website, keyword, or browser target."
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Select at least one app, website, or keyword before saving.")
                        }
                        return@ExtendedFloatingActionButton
                    }
                    if (pauseOption.type == PauseOptionType.SCAN_QR_OR_BARCODE && pauseOption.registeredCodeValue.isBlank()) {
                        showPauseConfigDialog = PauseOptionType.SCAN_QR_OR_BARCODE
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Scan or enter a QR/barcode value for Pause Options.")
                        }
                        return@ExtendedFloatingActionButton
                    }
                    if (pauseOption.type == PauseOptionType.TYPE_OVER_TEXT &&
                        OverrideChallengeValidator.normalizeOverrideText(pauseOption.customOrRandomText).length <
                        OverrideChallengeValidator.MIN_PASSAGE_LENGTH
                    ) {
                        showPauseConfigDialog = PauseOptionType.TYPE_OVER_TEXT
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                "Type-over passage must be at least ${OverrideChallengeValidator.MIN_PASSAGE_LENGTH} characters."
                            )
                        }
                        return@ExtendedFloatingActionButton
                    }
                    if (pauseOption.type == PauseOptionType.SCAN_NFC_TAG &&
                        !OverrideChallengeValidator.isValidNfcTagIdHex(pauseOption.registeredTagIdHex)
                    ) {
                        showPauseConfigDialog = PauseOptionType.SCAN_NFC_TAG
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Enter a valid NFC tag hex UID or leave default.")
                        }
                        return@ExtendedFloatingActionButton
                    }
                    val finalRule = buildCurrentDraftRule(isFinalSave = true).sanitize()
                    val orig = persistedOriginal
                    if (orig != null &&
                        orig.status != BlockRuleStatus.INACTIVE_MANUAL &&
                        orig.status != BlockRuleStatus.ARCHIVED &&
                        RuleMutationClassifier.isWeakeningEdit(orig, finalRule)
                    ) {
                        val nowWall = System.currentTimeMillis()
                        val isActiveNow = orig.isEffectivelyActive() && (
                            !orig.hasAnyTriggers() ||
                                orig.weeklySchedules.any { it.isCurrentlyInWindow(nowWall) } ||
                                orig.screenTimeLimits.isNotEmpty() ||
                                orig.openLimits.isNotEmpty()
                            )
                        if (orig.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING &&
                            (isActiveNow || orig.pauseOption.protectOutsideScheduleWindow)
                        ) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Strict Mode locked: you can add protections, but removing or weakening is blocked."
                                )
                            }
                            return@ExtendedFloatingActionButton
                        }
                        val challengeRule = if (orig.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING) {
                            orig.copy(
                                pauseOption = orig.pauseOption.copy(
                                    type = PauseOptionType.DELAY,
                                    delaySeconds = 15
                                )
                            )
                        } else {
                            orig
                        }
                        pendingWeakeningChallengeRule = challengeRule
                        pendingWeakeningSaveCandidate = finalRule
                        return@ExtendedFloatingActionButton
                    }

                    if (orig == null &&
                        (finalRule.pauseOption.type == PauseOptionType.STRICT_NO_PAUSING ||
                            finalRule.pauseOption.protectOutsideScheduleWindow)
                    ) {
                        pendingNewRuleWarningCandidate = finalRule
                        return@ExtendedFloatingActionButton
                    }

                    onSave(finalRule)
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("create_block_done_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Top bar: Close (X) + Outlined Text Field "Set name of block"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_create_block_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    placeholder = { Text("Set name of block") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("block_name_input")
                )
            }

            // Section 1: Blocked apps and websites
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Blocked apps and websites",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val hasAnyTargets =
                    appTargets.isNotEmpty() ||
                        blockedDomains.isNotEmpty() ||
                        blockedKeywords.isNotEmpty() ||
                        blockNewlyInstalled ||
                        includeInBrowserShortform

                Surface(
                    onClick = openTargetSelection,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (targetValidationError != null && !hasAnyTargets) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_what_to_block_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!hasAnyTargets) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Select what to block",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UnifiedTargetChipsFlowRow(
                                    context = context,
                                    appTargets = appTargets,
                                    blockedDomains = blockedDomains,
                                    blockedKeywords = blockedKeywords,
                                    includeInBrowserShortform = includeInBrowserShortform
                                )
                                if (blockNewlyInstalled) {
                                    Text(
                                        text = "Includes newly installed apps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showTargetsMenu = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Target options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showTargetsMenu,
                                onDismissRequest = { showTargetsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        showTargetsMenu = false
                                        openTargetSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy from") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        showTargetsMenu = false
                                        showCopyFromDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Delete, contentDescription = null)
                                    },
                                    onClick = {
                                        showTargetsMenu = false
                                        appTargets = emptyMap()
                                        blockedDomains = emptyList()
                                        blockedKeywords = emptyList()
                                        blockNewlyInstalled = false
                                        includeInBrowserShortform = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (targetValidationError != null && !hasAnyTargets) {
                    Text(
                        text = targetValidationError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Section 2: When to block
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "When to block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Set a schedule or limit to control when blocking is active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Existing Weekly Schedules
                weeklySchedules.forEach { schedule ->
                    TriggerSummaryCard(
                        icon = Icons.Outlined.Schedule,
                        title = schedule.formatTimeRange(),
                        subtitle = "${schedule.formatDaysSummary()} • ${schedule.formatDurationSummary()}",
                        onClick = {
                            editingWeeklyScheduleId = schedule.id
                            showWeeklyScheduleSheet = true
                        },
                        onRemove = {
                            weeklySchedules = weeklySchedules.filterNot { it.id == schedule.id }
                        }
                    )
                }

                if (weeklySchedules.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            editingWeeklyScheduleId = null
                            showWeeklyScheduleSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_another_schedule_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (weeklySchedules.size == 1) {
                                "Add another time window"
                            } else {
                                "Manage or add time windows (${weeklySchedules.size})"
                            }
                        )
                    }
                }

                // Existing Screen Time Limits
                screenTimeLimits.forEach { limit ->
                    TriggerSummaryCard(
                        icon = Icons.Outlined.HourglassEmpty,
                        title = "Screen time limit: ${limit.formatSummary()}",
                        subtitle = if (limit.period == LimitPeriod.HOURLY) "Resets every hour" else "Resets daily at midnight",
                        onClick = {
                            showScreenTimeLimitDialog = limit.period
                        },
                        onRemove = {
                            screenTimeLimits = screenTimeLimits.filterNot { it.id == limit.id }
                        }
                    )
                }

                // Existing Open Limits
                openLimits.forEach { limit ->
                    TriggerSummaryCard(
                        icon = Icons.Outlined.TouchApp,
                        title = "Open limit: ${limit.formatSummary()}",
                        subtitle = if (limit.period == LimitPeriod.HOURLY) "Resets every hour" else "Resets daily at midnight",
                        onClick = {
                            showOpenLimitDialog = limit.period
                        },
                        onRemove = {
                            openLimits = openLimits.filterNot { it.id == limit.id }
                        }
                    )
                }

                // Add schedule or limit card
                Surface(
                    onClick = { showAddScheduleOrLimitSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_schedule_or_limit_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Outlined.HourglassFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Add schedule or limit",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Create a schedule, set screen time limits, or leave empty to manually start the block",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Section 3: Pause options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pause options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = { showPauseOptionsSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pause_options_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = pauseOptionIcon(pauseOption.type),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = pauseOption.titleLabel(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pauseOption.subtitleLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (pauseOption.type != PauseOptionType.STRICT_NO_PAUSING) {
                                    Text(
                                        text = pauseOption.formatMaxPauseBadge(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (pauseOption.type == PauseOptionType.DELAY) {
                            Text(
                                text = pauseOption.formatDelayBadge(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Section 4: Warning & interception style
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Warning & interception style",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = { showWarningStyleDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("warning_style_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = when (interceptionStyle) {
                                    BlockInterceptionStyle.STANDARD_WARNING_DIALOG -> Icons.Outlined.WarningAmber
                                    BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE -> Icons.Outlined.SelfImprovement
                                    BlockInterceptionStyle.SILENT_CLEAN -> Icons.Outlined.CleaningServices
                                    BlockInterceptionStyle.INSTANT_HOME_OR_BACK -> Icons.Outlined.Home
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = interceptionStyle.titleLabel(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Short videos: ${shortformInterceptionStyle.titleLabel()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (interceptionStyle == BlockInterceptionStyle.STANDARD_WARNING_DIALOG) {
                                    Text(
                                        text = "\"${customWarningMessage.take(60)}${if (customWarningMessage.length > 60) "..." else ""}\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (interceptionStyle == BlockInterceptionStyle.STANDARD_WARNING_DIALOG) {
                            Text(
                                text = "${closeDelaySeconds}s",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Section 5: What to block
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "What to block",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { blockAppLaunch = !blockAppLaunch }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "App launch",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Checkbox(
                        checked = blockAppLaunch,
                        onCheckedChange = { blockAppLaunch = it },
                        modifier = Modifier.testTag("checkbox_block_app_launch")
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { blockNotifications = !blockNotifications }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Checkbox(
                        checked = blockNotifications,
                        onCheckedChange = { blockNotifications = it },
                        modifier = Modifier.testTag("checkbox_block_notifications")
                    )
                }
            }

            Spacer(modifier = Modifier.height(84.dp))
        }
    }
}

@Composable
private fun TriggerSummaryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onClick != null) mod.clickable(onClick = onClick) else mod }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove trigger",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleOrLimitBottomSheet(
    onDismiss: () -> Unit,
    onSelectWeeklySchedule: () -> Unit,
    onSelectScreenTimeLimit: (LimitPeriod) -> Unit,
    onSelectOpenLimit: (LimitPeriod) -> Unit
) {
    var screenTimePeriod by remember { mutableStateOf(LimitPeriod.DAILY) }
    var openLimitPeriod by remember { mutableStateOf(LimitPeriod.DAILY) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add schedule or limit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // 1. Block on a weekly schedule
            Surface(
                onClick = onSelectWeeklySchedule,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block on a weekly schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Schedule specific times and days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Screen time limit (with inline Hourly | Daily segmented toggle)
            Surface(
                onClick = { onSelectScreenTimeLimit(screenTimePeriod) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HourglassFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Screen time limit",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Block after using for a set duration",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HourlyDailySegmentedToggle(
                        selectedPeriod = screenTimePeriod,
                        onSelectPeriod = {
                            screenTimePeriod = it
                            onSelectScreenTimeLimit(it)
                        },
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }

            // 3. Open limit (with inline Hourly | Daily segmented toggle)
            Surface(
                onClick = { onSelectOpenLimit(openLimitPeriod) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Open limit",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Block after opening a set number of times",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HourlyDailySegmentedToggle(
                        selectedPeriod = openLimitPeriod,
                        onSelectPeriod = {
                            openLimitPeriod = it
                            onSelectOpenLimit(it)
                        },
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyDailySegmentedToggle(
    selectedPeriod: LimitPeriod,
    onSelectPeriod: (LimitPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            val isHourly = selectedPeriod == LimitPeriod.HOURLY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .background(
                        if (isHourly) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelectPeriod(LimitPeriod.HOURLY) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Hourly",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isHourly) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHourly) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            val isDaily = selectedPeriod == LimitPeriod.DAILY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(
                        if (isDaily) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelectPeriod(LimitPeriod.DAILY) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Daily",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isDaily) FontWeight.Bold else FontWeight.Medium,
                    color = if (isDaily) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseOptionsBottomSheet(
    currentConfig: PauseOptionConfig,
    onDismiss: () -> Unit,
    onToggleProtectOutsideWindow: (Boolean) -> Unit,
    onSelectOption: (PauseOptionType) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pause options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            PauseOptionType.values().forEach { optionType ->
                val isSelected = currentConfig.type == optionType
                val previewConfig = currentConfig.copy(type = optionType)
                Surface(
                    onClick = { onSelectOption(optionType) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = pauseOptionIcon(optionType),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = previewConfig.titleLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = previewConfig.subtitleLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Surface(
                onClick = { onToggleProtectOutsideWindow(!currentConfig.protectOutsideScheduleWindow) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Protect outside schedule window",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Off: adding rules is free; weakening outside schedule uses a 15s reflection delay. On: full lock applies even outside schedule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = currentConfig.protectOutsideScheduleWindow,
                        onCheckedChange = onToggleProtectOutsideWindow
                    )
                }
            }
        }
    }
}

private fun presetIcon(presetId: String): ImageVector = when (presetId) {
    "sleep" -> Icons.Outlined.Bedtime
    "study" -> Icons.Outlined.MenuBook
    "work" -> Icons.Outlined.WorkOutline
    "morning" -> Icons.Outlined.WbSunny
    "evening" -> Icons.Outlined.NightsStay
    "school" -> Icons.Outlined.School
    else -> Icons.Outlined.Schedule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = is24Hour
    )
    var useKeyboardInput by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { useKeyboardInput = !useKeyboardInput },
                    modifier = Modifier.testTag("toggle_time_picker_input_mode")
                ) {
                    Icon(
                        imageVector = if (useKeyboardInput) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
                        contentDescription = if (useKeyboardInput) "Switch to clock dial" else "Switch to keyboard input"
                    )
                }
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (useKeyboardInput) {
                    TimeInput(state = timePickerState)
                } else {
                    TimePicker(state = timePickerState)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) },
                modifier = Modifier.testTag("confirm_time_picker_button")
            ) {
                Text("Set time")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConfigureWeeklyScheduleBottomSheet(
    existingSchedules: List<WeeklyScheduleTrigger>,
    initialEditScheduleId: String? = null,
    onDismiss: () -> Unit,
    onSaveAll: (List<WeeklyScheduleTrigger>, List<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var workingSchedules by remember(existingSchedules) {
        mutableStateOf(WeeklyScheduleNormalizer.normalize(existingSchedules).schedules)
    }
    var editingId by remember(initialEditScheduleId) {
        mutableStateOf(initialEditScheduleId)
    }
    val initialEditing = remember(initialEditScheduleId, workingSchedules) {
        initialEditScheduleId?.let { id -> workingSchedules.firstOrNull { it.id == id } }
    }

    var selectedDays by remember(initialEditScheduleId) {
        mutableStateOf(initialEditing?.sanitizedDays() ?: WeeklyScheduleTrigger.ALL_DAYS)
    }
    var isAllDay by remember(initialEditScheduleId) {
        mutableStateOf(initialEditing?.isAllDay() ?: false)
    }
    var startHour by remember(initialEditScheduleId) {
        mutableIntStateOf((initialEditing?.startTimeMinutes ?: (22 * 60)) / 60)
    }
    var startMinute by remember(initialEditScheduleId) {
        mutableIntStateOf((initialEditing?.startTimeMinutes ?: (22 * 60)) % 60)
    }
    var endHour by remember(initialEditScheduleId) {
        mutableIntStateOf((initialEditing?.endTimeMinutes ?: (7 * 60)) / 60)
    }
    var endMinute by remember(initialEditScheduleId) {
        mutableIntStateOf((initialEditing?.endTimeMinutes ?: (7 * 60)) % 60)
    }
    var editorDirty by remember(initialEditScheduleId) {
        mutableStateOf(existingSchedules.isEmpty() || initialEditing != null)
    }
    var editorValidationError by remember { mutableStateOf<String?>(null) }
    var autoCorrectionNotice by remember { mutableStateOf<String?>(null) }
    var activeTimePickerTarget by remember { mutableStateOf<String?>(null) } // "start" or "end"

    val dayItems = remember {
        listOf(
            Triple(Calendar.MONDAY, "M", "Monday"),
            Triple(Calendar.TUESDAY, "T", "Tuesday"),
            Triple(Calendar.WEDNESDAY, "W", "Wednesday"),
            Triple(Calendar.THURSDAY, "T", "Thursday"),
            Triple(Calendar.FRIDAY, "F", "Friday"),
            Triple(Calendar.SATURDAY, "S", "Saturday"),
            Triple(Calendar.SUNDAY, "S", "Sunday")
        )
    }

    val startTotalMinutes = startHour * 60 + startMinute
    val endTotalMinutes = endHour * 60 + endMinute
    val isSameStartAndEnd = !isAllDay && startTotalMinutes == endTotalMinutes

    val previewTrigger = remember(selectedDays, isAllDay, startTotalMinutes, endTotalMinutes) {
        WeeklyScheduleTrigger(
            id = editingId ?: UUID.randomUUID().toString(),
            daysOfWeek = selectedDays,
            startTimeMinutes = if (isAllDay) 0 else startTotalMinutes,
            endTimeMinutes = if (isAllDay) 0 else endTotalMinutes
        )
    }

    fun loadIntoEditor(trigger: WeeklyScheduleTrigger) {
        editingId = trigger.id
        selectedDays = trigger.sanitizedDays()
        isAllDay = trigger.isAllDay()
        startHour = if (trigger.isAllDay()) 0 else trigger.startTimeMinutes / 60
        startMinute = if (trigger.isAllDay()) 0 else trigger.startTimeMinutes % 60
        endHour = if (trigger.isAllDay()) 0 else trigger.endTimeMinutes / 60
        endMinute = if (trigger.isAllDay()) 0 else trigger.endTimeMinutes % 60
        editorDirty = false
        editorValidationError = null
    }

    fun resetEditorForNewWindow() {
        editingId = null
        selectedDays = WeeklyScheduleTrigger.WEEKDAYS
        isAllDay = false
        startHour = 9
        startMinute = 0
        endHour = 17
        endMinute = 0
        editorDirty = false
        editorValidationError = null
    }

    fun commitCandidateList(rawList: List<WeeklyScheduleTrigger>): ScheduleNormalizationResult {
        val result = WeeklyScheduleNormalizer.normalize(rawList)
        workingSchedules = result.schedules
        autoCorrectionNotice = result.adjustments.firstOrNull()
        return result
    }

    fun commitEditorToWorkingList(): Boolean {
        if (selectedDays.isEmpty()) {
            editorValidationError = "Select at least one day of the week."
            return false
        }
        if (isSameStartAndEnd) {
            editorValidationError = "Start and end times are the same. Pick a different end time or enable All day."
            return false
        }
        editorValidationError = null
        val candidate = WeeklyScheduleTrigger(
            id = editingId ?: UUID.randomUUID().toString(),
            daysOfWeek = selectedDays,
            startTimeMinutes = if (isAllDay) 0 else startTotalMinutes,
            endTimeMinutes = if (isAllDay) 0 else endTotalMinutes
        )
        val idx = workingSchedules.indexOfFirst { it.id == candidate.id }
        val updated = if (idx >= 0) {
            workingSchedules.toMutableList().apply { this[idx] = candidate }
        } else {
            workingSchedules + candidate
        }
        commitCandidateList(updated)
        editingId = null
        editorDirty = false
        return true
    }

    if (activeTimePickerTarget != null) {
        val isStart = activeTimePickerTarget == "start"
        ScheduleTimePickerDialog(
            title = if (isStart) "Select start time" else "Select end time",
            initialHour = if (isStart) startHour else endHour,
            initialMinute = if (isStart) startMinute else endMinute,
            onDismiss = { activeTimePickerTarget = null },
            onConfirm = { pickedHour, pickedMinute ->
                if (isStart) {
                    startHour = pickedHour
                    startMinute = pickedMinute
                } else {
                    endHour = pickedHour
                    endMinute = pickedMinute
                }
                isAllDay = false
                editorDirty = true
                editorValidationError = null
                activeTimePickerTarget = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("weekly_schedule_bottom_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Weekly schedule",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Add one or more time windows to this block. Overlapping or duplicate windows auto-correct cleanly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Prepopulated Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick presets",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(WeeklyScheduleTrigger.PRESETS, key = { it.id }) { preset ->
                        val matchingExisting = workingSchedules.firstOrNull {
                            it.startTimeMinutes == preset.startTimeMinutes &&
                                it.endTimeMinutes == preset.endTimeMinutes &&
                                it.daysOfWeek == preset.daysOfWeek
                        }
                        val presetTrigger = remember(preset) { preset.toTrigger() }
                        FilterChip(
                            selected = matchingExisting != null,
                            onClick = {
                                if (matchingExisting != null) {
                                    loadIntoEditor(matchingExisting)
                                } else {
                                    val result = commitCandidateList(workingSchedules + preset.toTrigger())
                                    if (result.adjustments.isEmpty()) {
                                        autoCorrectionNotice = "Added ${preset.label} (${presetTrigger.formatTimeRange()})."
                                    }
                                    editorDirty = false
                                    editorValidationError = null
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = presetIcon(preset.id),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "${preset.label} ${presetTrigger.formatStartTime()}-${presetTrigger.formatEndTime()}",
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag("schedule_preset_${preset.id}")
                        )
                    }
                }
            }

            // Auto-correction / status notice banner
            if (autoCorrectionNotice != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoFixHigh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = autoCorrectionNotice!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Active Time Window Picker & Editor Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (editingId != null) "Edit time window" else "Custom time window",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = if (isSameStartAndEnd) "Invalid range" else previewTrigger.formatDurationSummary(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSameStartAndEnd) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // All-day toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                isAllDay = !isAllDay
                                editorDirty = true
                                editorValidationError = null
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All day (24h)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Block continuously for the full day on selected days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = {
                                isAllDay = it
                                editorDirty = true
                                editorValidationError = null
                            },
                            modifier = Modifier.testTag("schedule_all_day_switch")
                        )
                    }

                    // Time Window Picker Cards (Start -> End)
                    if (!isAllDay) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                onClick = { activeTimePickerTarget = "start" },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSameStartAndEnd) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("schedule_start_time_picker_button")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Start time",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = String.format("%02d:%02d", startHour, startMinute),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )

                            Surface(
                                onClick = { activeTimePickerTarget = "end" },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSameStartAndEnd) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("schedule_end_time_picker_button")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (previewTrigger.isOvernight()) "End (next day)" else "End time",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = String.format("%02d:%02d", endHour, endMinute),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (isSameStartAndEnd) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        endHour = (startHour + 1) % 24
                                        endMinute = startMinute
                                        editorDirty = true
                                        editorValidationError = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Set +1h end", maxLines = 1, softWrap = false)
                                }
                                OutlinedButton(
                                    onClick = {
                                        isAllDay = true
                                        editorDirty = true
                                        editorValidationError = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Use All day", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    // Days active + quick day-group selectors
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Days active",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedDays == WeeklyScheduleTrigger.ALL_DAYS,
                                onClick = {
                                    selectedDays = WeeklyScheduleTrigger.ALL_DAYS
                                    editorDirty = true
                                    editorValidationError = null
                                },
                                label = { Text("Every day", maxLines = 1, softWrap = false) }
                            )
                            FilterChip(
                                selected = selectedDays == WeeklyScheduleTrigger.WEEKDAYS,
                                onClick = {
                                    selectedDays = WeeklyScheduleTrigger.WEEKDAYS
                                    editorDirty = true
                                    editorValidationError = null
                                },
                                label = { Text("Weekdays", maxLines = 1, softWrap = false) }
                            )
                            FilterChip(
                                selected = selectedDays == WeeklyScheduleTrigger.WEEKENDS,
                                onClick = {
                                    selectedDays = WeeklyScheduleTrigger.WEEKENDS
                                    editorDirty = true
                                    editorValidationError = null
                                },
                                label = { Text("Weekends", maxLines = 1, softWrap = false) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            dayItems.forEach { (dayConst, shortLabel, fullName) ->
                                val selected = selectedDays.contains(dayConst)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                        .semantics { contentDescription = fullName }
                                        .clickable {
                                            if (selected && selectedDays.size <= 1) {
                                                editorValidationError = "Keep at least one active day selected."
                                            } else {
                                                selectedDays = if (selected) {
                                                    selectedDays - dayConst
                                                } else {
                                                    selectedDays + dayConst
                                                }
                                                editorDirty = true
                                                editorValidationError = null
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (editorValidationError != null) {
                        Text(
                            text = editorValidationError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingId != null) {
                            OutlinedButton(
                                onClick = { resetEditorForNewWindow() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("New window")
                            }
                        }
                        FilledTonalButton(
                            onClick = { commitEditorToWorkingList() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_or_update_schedule_window_button")
                        ) {
                            Icon(
                                imageVector = if (editingId != null) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (editingId != null) "Update window" else "Add window")
                        }
                    }
                }
            }

            // Multi-window list for this block
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (workingSchedules.isEmpty()) {
                        "No windows added yet"
                    } else {
                        "Time windows in this block (${workingSchedules.size})"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                workingSchedules.forEach { schedule ->
                    val isBeingEdited = editingId == schedule.id
                    Surface(
                        onClick = { loadIntoEditor(schedule) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isBeingEdited) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = if (isBeingEdited) 1.5.dp else 1.dp,
                            color = if (isBeingEdited) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = schedule.formatTimeRange(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${schedule.formatDaysSummary()} • ${schedule.formatDurationSummary()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    workingSchedules = workingSchedules.filterNot { it.id == schedule.id }
                                    if (editingId == schedule.id) {
                                        editingId = null
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove time window",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Footer Cancel / Save Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val finalRaw = if (editorDirty || workingSchedules.isEmpty()) {
                            if (selectedDays.isEmpty()) {
                                editorValidationError = "Select at least one day of the week."
                                return@Button
                            }
                            if (isSameStartAndEnd) {
                                editorValidationError = "Start and end times are the same. Pick a different end time or enable All day."
                                return@Button
                            }
                            val candidate = WeeklyScheduleTrigger(
                                id = editingId ?: UUID.randomUUID().toString(),
                                daysOfWeek = selectedDays,
                                startTimeMinutes = if (isAllDay) 0 else startTotalMinutes,
                                endTimeMinutes = if (isAllDay) 0 else endTotalMinutes
                            )
                            val idx = workingSchedules.indexOfFirst { it.id == candidate.id }
                            if (idx >= 0) {
                                workingSchedules.toMutableList().apply { this[idx] = candidate }
                            } else {
                                workingSchedules + candidate
                            }
                        } else {
                            workingSchedules
                        }
                        val normalized = WeeklyScheduleNormalizer.normalize(finalRaw)
                        onSaveAll(normalized.schedules, normalized.adjustments)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_weekly_schedule_sheet_button")
                ) {
                    val count = if (workingSchedules.isEmpty()) 1 else workingSchedules.size
                    Text(if (count > 1) "Save ($count)" else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureScreenTimeLimitDialog(
    initialPeriod: LimitPeriod,
    existingLimits: List<ScreenTimeLimitTrigger> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ScreenTimeLimitTrigger) -> Unit
) {
    var period by remember { mutableStateOf(initialPeriod) }
    var maxMinutes by remember(initialPeriod, existingLimits) {
        val existing = existingLimits.firstOrNull { it.period == initialPeriod }
        mutableIntStateOf(existing?.maxMinutes ?: if (initialPeriod == LimitPeriod.HOURLY) 15 else 60)
    }

    val presets = if (period == LimitPeriod.HOURLY) {
        listOf(5, 10, 15, 20, 30, 45)
    } else {
        listOf(15, 30, 45, 60, 90, 120, 180)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (period == LimitPeriod.HOURLY) "Hourly screen time limit"
                else "Daily screen time limit"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HourlyDailySegmentedToggle(
                    selectedPeriod = period,
                    onSelectPeriod = {
                        period = it
                        val existing = existingLimits.firstOrNull { l -> l.period == it }
                        maxMinutes = existing?.maxMinutes ?: if (it == LimitPeriod.HOURLY) 15 else 60
                    }
                )
                Text(
                    text = "Allowed duration: $maxMinutes min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = maxMinutes == mins,
                            onClick = { maxMinutes = mins },
                            label = {
                                val h = mins / 60
                                val m = mins % 60
                                Text(
                                    when {
                                        h > 0 && m > 0 -> "${h}h ${m}m"
                                        h > 0 -> "${h}h"
                                        else -> "${m}m"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ScreenTimeLimitTrigger(period = period, maxMinutes = maxMinutes))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureOpenLimitDialog(
    initialPeriod: LimitPeriod,
    existingLimits: List<OpenLimitTrigger> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (OpenLimitTrigger) -> Unit
) {
    var period by remember { mutableStateOf(initialPeriod) }
    var maxLaunches by remember(initialPeriod, existingLimits) {
        val existing = existingLimits.firstOrNull { it.period == initialPeriod }
        mutableIntStateOf(existing?.maxLaunches ?: if (initialPeriod == LimitPeriod.HOURLY) 3 else 10)
    }
    val presets = if (period == LimitPeriod.HOURLY) {
        listOf(1, 2, 3, 5, 10)
    } else {
        listOf(3, 5, 10, 15, 20, 30)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (period == LimitPeriod.HOURLY) "Hourly open limit"
                else "Daily open limit"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HourlyDailySegmentedToggle(
                    selectedPeriod = period,
                    onSelectPeriod = {
                        period = it
                        val existing = existingLimits.firstOrNull { l -> l.period == it }
                        maxLaunches = existing?.maxLaunches ?: if (it == LimitPeriod.HOURLY) 3 else 10
                    }
                )
                Text(
                    text = "Max app opens: ${maxLaunches}x",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { count ->
                        FilterChip(
                            selected = maxLaunches == count,
                            onClick = { maxLaunches = count },
                            label = { Text("${count}x") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(OpenLimitTrigger(period = period, maxLaunches = maxLaunches))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigurePauseOptionDialog(
    type: PauseOptionType,
    currentConfig: PauseOptionConfig,
    onDismiss: () -> Unit,
    onRequestBarcodeScan: () -> Unit,
    onSave: (PauseOptionConfig) -> Unit
) {
    var delaySeconds by remember(type, currentConfig) {
        mutableIntStateOf(currentConfig.delaySeconds.coerceIn(5, 3600))
    }
    var maxPauseMins by remember(type, currentConfig) {
        mutableIntStateOf(currentConfig.maxPauseDurationMinutes.coerceIn(5, 240))
    }
    var customText by remember(type, currentConfig) {
        mutableStateOf(
            currentConfig.customOrRandomText.ifBlank { PauseOptionConfig.DEFAULT_CHALLENGE_TEXT }
        )
    }
    var protectOutsideWindow by remember(type, currentConfig) {
        mutableStateOf(currentConfig.protectOutsideScheduleWindow)
    }
    var barcodeVal by remember(type, currentConfig.registeredCodeValue) {
        mutableStateOf(currentConfig.registeredCodeValue)
    }
    var nfcHexVal by remember(type, currentConfig.registeredTagIdHex) {
        mutableStateOf(currentConfig.registeredTagIdHex)
    }
    var pauseConfigError by remember(type) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (type) {
                    PauseOptionType.DELAY -> "Configure Delay"
                    PauseOptionType.TYPE_OVER_TEXT -> "Configure Type Over Text"
                    PauseOptionType.SCAN_QR_OR_BARCODE -> "Register QR or Barcode"
                    PauseOptionType.SCAN_NFC_TAG -> "Register NFC Tag"
                    PauseOptionType.STRICT_NO_PAUSING -> "Strict Mode"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (type) {
                    PauseOptionType.DELAY -> {
                        Text(
                            text = "Countdown wait before pausing",
                            style = MaterialTheme.typography.labelLarge
                        )
                        val delayPresets = listOf(10, 30, 60, 120, 300)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            delayPresets.forEach { sec ->
                                FilterChip(
                                    selected = delaySeconds == sec,
                                    onClick = { delaySeconds = sec },
                                    label = {
                                        Text(if (sec >= 60) "${sec / 60}m" else "${sec}s")
                                    }
                                )
                            }
                        }
                    }
                    PauseOptionType.TYPE_OVER_TEXT -> {
                        Text(
                            text = "Passage required to pause or edit this block (minimum ${OverrideChallengeValidator.MIN_PASSAGE_LENGTH} characters):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customText,
                            onValueChange = {
                                customText = it
                                pauseConfigError = null
                            },
                            minLines = 2,
                            maxLines = 4,
                            isError = pauseConfigError != null,
                            supportingText = pauseConfigError?.let { err -> { Text(err) } },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PauseOptionType.SCAN_QR_OR_BARCODE -> {
                        Text(
                            text = "Scan any physical barcode or QR code (e.g. a book or toothpaste) or enter a code value:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onRequestBarcodeScan,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan with Camera")
                        }
                        OutlinedTextField(
                            value = barcodeVal,
                            onValueChange = {
                                barcodeVal = it
                                pauseConfigError = null
                            },
                            label = { Text("Code value") },
                            isError = pauseConfigError != null,
                            supportingText = pauseConfigError?.let { err -> { Text(err) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PauseOptionType.SCAN_NFC_TAG -> {
                        Text(
                            text = "Enter your NFC tag UID hex (colons/spaces auto-corrected) or leave blank to accept any physical NFC tag tap:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = nfcHexVal,
                            onValueChange = {
                                nfcHexVal = it.uppercase()
                                pauseConfigError = null
                            },
                            placeholder = { Text(OverrideChallengeValidator.ANY_NFC_TAG) },
                            isError = pauseConfigError != null,
                            supportingText = pauseConfigError?.let { err -> { Text(err) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PauseOptionType.STRICT_NO_PAUSING -> {}
                }

                if (type != PauseOptionType.STRICT_NO_PAUSING) {
                    Text(
                        text = "Max pause duration",
                        style = MaterialTheme.typography.labelLarge
                    )
                    val maxPausePresets = listOf(15, 30, 60, 120)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        maxPausePresets.forEach { mins ->
                            FilterChip(
                                selected = maxPauseMins == mins,
                                onClick = { maxPauseMins = mins },
                                label = {
                                    Text(if (mins >= 60) "${mins / 60} hr" else "$mins min")
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { protectOutsideWindow = !protectOutsideWindow },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Protect outside schedule window",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Enforce this challenge even outside active schedules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = protectOutsideWindow,
                            onCheckedChange = { protectOutsideWindow = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (type == PauseOptionType.SCAN_QR_OR_BARCODE && barcodeVal.trim().isEmpty()) {
                        pauseConfigError = "Scan or enter a QR/barcode value first."
                        return@Button
                    }
                    if (type == PauseOptionType.TYPE_OVER_TEXT) {
                        val normalized = OverrideChallengeValidator.normalizeOverrideText(customText)
                        if (normalized.length < OverrideChallengeValidator.MIN_PASSAGE_LENGTH) {
                            pauseConfigError = "Enter at least ${OverrideChallengeValidator.MIN_PASSAGE_LENGTH} characters."
                            return@Button
                        }
                    }
                    if (type == PauseOptionType.SCAN_NFC_TAG &&
                        !OverrideChallengeValidator.isValidNfcTagIdHex(nfcHexVal)
                    ) {
                        pauseConfigError = "Enter a valid hex UID (4-32 hex characters) or leave blank for any tag."
                        return@Button
                    }
                    val sanitizedPassage = OverrideChallengeValidator.normalizeOverrideText(customText)
                        .ifBlank { PauseOptionConfig.DEFAULT_CHALLENGE_TEXT }
                    val sanitizedNfc = if (type == PauseOptionType.SCAN_NFC_TAG) {
                        OverrideChallengeValidator.sanitizeNfcTagIdHex(nfcHexVal)
                    } else {
                        nfcHexVal.trim()
                    }
                    onSave(
                        currentConfig.copy(
                            type = type,
                            delaySeconds = delaySeconds,
                            maxPauseDurationMinutes = maxPauseMins,
                            customOrRandomText = sanitizedPassage,
                            registeredCodeValue = barcodeVal.trim(),
                            registeredTagIdHex = sanitizedNfc,
                            protectOutsideScheduleWindow = protectOutsideWindow
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CopyTargetsFromRuleDialog(
    rules: List<UnifiedBlockRule>,
    onDismiss: () -> Unit,
    onSelectRule: (UnifiedBlockRule) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy targets from") },
        text = {
            if (rules.isEmpty()) {
                Text(
                    text = "No other block rules with configured targets found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rules.forEach { rule ->
                        Surface(
                            onClick = { onSelectRule(rule) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${rule.appTargets.size} apps • ${rule.blockedDomains.size} sites",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigureWarningStyleDialog(
    currentInterceptionStyle: BlockInterceptionStyle,
    currentShortformStyle: ShortformInterceptionStyle,
    currentMessage: String,
    currentCloseDelaySeconds: Int,
    onDismiss: () -> Unit,
    onSave: (BlockInterceptionStyle, ShortformInterceptionStyle, String, Int) -> Unit
) {
    var selectedStyle by remember { mutableStateOf(currentInterceptionStyle) }
    var selectedShortformStyle by remember { mutableStateOf(currentShortformStyle) }
    var message by remember { mutableStateOf(currentMessage) }
    var closeDelay by remember { mutableIntStateOf(currentCloseDelaySeconds) }

    val delayOptions = listOf(0, 3, 5, 10, 15)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warning & Interception Style") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Apps, Websites & Keywords reaction",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                BlockInterceptionStyle.values().forEach { style ->
                    val isSelected = selectedStyle == style
                    Surface(
                        onClick = { selectedStyle = style },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = style.titleLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = style.subtitleLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedStyle == BlockInterceptionStyle.STANDARD_WARNING_DIALOG) {
                    Text(
                        text = "Block screen close delay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.forEach { sec ->
                            FilterChip(
                                selected = closeDelay == sec,
                                onClick = { closeDelay = sec },
                                label = { Text(if (sec == 0) "Instant (0s)" else "${sec}s") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Motivational message") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Text(
                    text = "Short Videos (Reels / Shorts) reaction",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                ShortformInterceptionStyle.values().forEach { sfStyle ->
                    val isSelected = selectedShortformStyle == sfStyle
                    Surface(
                        onClick = { selectedShortformStyle = sfStyle },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = sfStyle.titleLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = sfStyle.subtitleLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        selectedStyle,
                        selectedShortformStyle,
                        message.trim().ifBlank { UnifiedBlockRule.DEFAULT_WARNING_MESSAGE },
                        closeDelay
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedTargetChipsFlowRow(
    context: Context,
    appTargets: Map<String, AppInterceptionLevel>,
    blockedDomains: List<String>,
    blockedKeywords: List<String>,
    includeInBrowserShortform: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pm = remember(context) { context.packageManager }
    val maxDomainChips = 5
    val maxKeywordChips = 5

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Full App icons & Shortform pills
        appTargets.entries.forEach { (pkg, level) ->
            val iconDrawable: Drawable? = remember(pkg) {
                runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            }
            if (level == AppInterceptionLevel.BLOCK_SHORTFORM_ONLY) {
                val shortLabel = UnifiedBlockRule.shortformLabelForPackage(pkg)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppDrawableIcon(
                            drawable = iconDrawable,
                            contentDescription = shortLabel,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = shortLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                AppDrawableIcon(
                    drawable = iconDrawable,
                    contentDescription = pkg,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (includeInBrowserShortform) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Browser Shorts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 2. Website domain chips (capped with +N more summary)
        blockedDomains.take(maxDomainChips).forEach { domain ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (blockedDomains.size > maxDomainChips) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "+${blockedDomains.size - maxDomainChips} sites",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }

        // 3. Keyword chips (capped with +N more summary)
        blockedKeywords.take(maxKeywordChips).forEach { keyword ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "ABC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = keyword,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (blockedKeywords.size > maxKeywordChips) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "+${blockedKeywords.size - maxKeywordChips} keywords",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

fun pauseOptionIcon(type: PauseOptionType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        PauseOptionType.STRICT_NO_PAUSING -> Icons.Outlined.Lock
        PauseOptionType.DELAY -> Icons.Outlined.Timer
        PauseOptionType.TYPE_OVER_TEXT -> Icons.Outlined.Keyboard
        PauseOptionType.SCAN_QR_OR_BARCODE -> Icons.Outlined.QrCodeScanner
        PauseOptionType.SCAN_NFC_TAG -> Icons.Outlined.Nfc
    }
}
