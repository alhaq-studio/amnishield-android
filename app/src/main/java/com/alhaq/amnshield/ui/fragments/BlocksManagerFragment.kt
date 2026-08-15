package com.alhaq.amnshield.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnshield.R
import com.alhaq.amnshield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnshield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnshield.premium.PremiumManager
import com.alhaq.amnshield.services.AmnShieldAccessibilityService
import com.alhaq.amnshield.ui.screens.BlocksManagerScreen
import com.alhaq.amnshield.ui.screens.CreateRuleScreen
import com.alhaq.amnshield.ui.screens.CreateKeywordBlockerRuleScreen
import com.alhaq.amnshield.ui.screens.CreateWebsiteBlockerRuleScreen
import com.alhaq.amnshield.ui.screens.CreateReelsBlockerRuleScreen
import com.alhaq.amnshield.ui.screens.CreateFocusModeRuleScreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import com.alhaq.amnshield.ui.theme.AmnShieldTheme
import com.alhaq.amnshield.ui.viewmodel.AmnShieldViewModel
import com.alhaq.amnshield.utils.SavedPreferencesLoader
import java.util.Calendar
import java.util.UUID

class BlocksManagerFragment : Fragment() {

    private lateinit var viewModel: AmnShieldViewModel
    private val savedPreferencesLoader by lazy { SavedPreferencesLoader(requireContext().applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AmnShieldViewModel::class.java]
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? com.alhaq.amnshield.ui.activity.MainActivity)?.setBottomNavVisible(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val action = remember { arguments?.getString("action") }
                val prefillTarget = remember { arguments?.getString("prefill_target") }
                val prefillType = remember { arguments?.getString("prefill_type") }
                val prefillApp = remember { arguments?.getString("prefill_app") }

                val defaultScreen = remember(action, prefillTarget) {
                    if (action == "create") {
                        when (prefillTarget) {
                            "KEYWORD_BLOCKER" -> "create_keyword"
                            "WEBSITE_BLOCKER" -> "create_website"
                            "REEL_BLOCKER" -> "create_reels"
                            else -> "create_app"
                        }
                    } else {
                        "manage"
                    }
                }

                val activeTheme = com.alhaq.amnshield.utils.ThemeUtils.resolveAppTheme(requireContext())
                viewModel.updateTheme(activeTheme)
                val state by viewModel.state.collectAsState()

                var currentScreen by remember(defaultScreen) { mutableStateOf(defaultScreen) } // "manage", "create_app", "create_keyword", "create_website", "create_reels"
                var initialType by remember { mutableStateOf(prefillType ?: "Block Schedule") }
                var editingRule by remember { mutableStateOf<com.alhaq.amnshield.ui.state.ScheduleRule?>(null) }
                var showSelectBlockerDialog by remember { mutableStateOf(false) }

                LaunchedEffect(currentScreen) {
                    (activity as? com.alhaq.amnshield.ui.activity.MainActivity)?.setBottomNavVisible(currentScreen == "manage")
                }

                AmnShieldTheme(appTheme = activeTheme) {
                    when (currentScreen) {
                        "manage" -> {
                            BlocksManagerScreen(
                                state = state,
                                viewModel = viewModel,
                                onNavigateToCreateRule = { type ->
                                    editingRule = null
                                    initialType = type
                                    showSelectBlockerDialog = true
                                },
                                onEditRule = { rule ->
                                    editingRule = rule
                                    initialType = rule.restrictionType
                                    val firstBlocker = rule.selectedBlockers.firstOrNull() ?: rule.targetBlockerType
                                    currentScreen = when (firstBlocker) {
                                        "Keyword Blocker" -> "create_keyword"
                                        "Website Blocker" -> "create_website"
                                        "Reels Blocker" -> "create_reels"
                                        "Focus Mode" -> "create_focus"
                                        else -> "create_app"
                                    }
                                },
                                onToggleRule = { id -> toggleScheduleRuleActive(id) },
                                onDeleteRule = { id -> deleteScheduleRule(id) },
                                onBack = {
                                    safeOnBack()
                                }
                            )

                            if (showSelectBlockerDialog) {
                                SelectBlockerBottomSheet(
                                    onDismissRequest = { showSelectBlockerDialog = false },
                                    onSelectOption = { option ->
                                        showSelectBlockerDialog = false
                                        currentScreen = when (option) {
                                            "App Blocker" -> "create_app"
                                            "Keyword Blocker" -> "create_keyword"
                                            "Website Blocker" -> "create_website"
                                            "Reels Blocker" -> "create_reels"
                                            "Focus Mode" -> "create_focus"
                                            else -> "create_app"
                                        }
                                    }
                                )
                            }
                        }
                        "create_app" -> {
                            CreateRuleScreen(
                                state = state,
                                prefillTarget = prefillTarget ?: "APP_BLOCKER",
                                prefillApp = prefillApp,
                                editingRule = editingRule,
                                onSaveRule = { rule ->
                                    if (editingRule != null) {
                                        deleteScheduleRule(editingRule!!.id)
                                    }
                                    saveScheduleRule(rule)
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                },
                                onBack = {
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                }
                            )
                        }
                        "create_keyword" -> {
                            CreateKeywordBlockerRuleScreen(
                                state = state,
                                viewModel = viewModel,
                                initialType = initialType,
                                editingRule = editingRule,
                                onSaveRule = { rule ->
                                    if (editingRule != null) {
                                        deleteScheduleRule(editingRule!!.id)
                                    }
                                    saveScheduleRule(rule)
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                },
                                onBack = {
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                }
                            )
                        }
                        "create_website" -> {
                            CreateWebsiteBlockerRuleScreen(
                                state = state,
                                viewModel = viewModel,
                                initialType = initialType,
                                editingRule = editingRule,
                                onSaveRule = { rule ->
                                    if (editingRule != null) {
                                        deleteScheduleRule(editingRule!!.id)
                                    }
                                    saveScheduleRule(rule)
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                },
                                onBack = {
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                }
                            )
                        }
                        "create_reels" -> {
                            CreateReelsBlockerRuleScreen(
                                state = state,
                                viewModel = viewModel,
                                initialType = initialType,
                                editingRule = editingRule,
                                onSaveRule = { rule ->
                                    if (editingRule != null) {
                                        deleteScheduleRule(editingRule!!.id)
                                    }
                                    saveScheduleRule(rule)
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                },
                                onBack = {
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                }
                            )
                        }
                        "create_focus" -> {
                            CreateFocusModeRuleScreen(
                                state = state,
                                editingRule = editingRule,
                                onSaveRule = { rule ->
                                    if (editingRule != null) {
                                        deleteScheduleRule(editingRule!!.id)
                                    }
                                    saveScheduleRule(rule)
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                },
                                onBack = {
                                    editingRule = null
                                    if (action == "create") {
                                        safeOnBack()
                                    } else {
                                        currentScreen = "manage"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun safeOnBack() {
        if (!parentFragmentManager.popBackStackImmediate()) {
            if (activity is com.alhaq.amnshield.ui.activity.FragmentActivity) {
                requireActivity().finish()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            loadSchedulesAndLimits()
        } catch (e: Throwable) {
            android.util.Log.e("BlocksManagerFragment", "Failed to load schedules in onViewCreated", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            loadSchedulesAndLimits()
        } catch (e: Throwable) {
            android.util.Log.e("BlocksManagerFragment", "Failed to load schedules in onResume", e)
        }
    }

    private fun loadSchedulesAndLimits() {
        val appSchedules = try {
            savedPreferencesLoader.loadAppBlockerScheduleRules()
        } catch (e: Throwable) {
            emptyList()
        }
        val launchLimits = try {
            savedPreferencesLoader.loadAppLaunchLimitRules()
        } catch (e: Throwable) {
            emptyList()
        }

        val rulesList = mutableListOf<com.alhaq.amnshield.ui.state.ScheduleRule>()

        try {
            val allGroupIds = appSchedules.mapNotNull { item ->
                val idStr = item.groupId ?: item.id
                idStr.takeIf { it.isNotBlank() }
            }.distinct()

            allGroupIds.forEach { groupId ->
                val associatedApps = appSchedules.filter { item ->
                    item.groupId == groupId || item.id == groupId
                }

                if (associatedApps.isNotEmpty()) {
                    val firstApp = associatedApps.first()

                    val name = firstApp.groupTitle ?: firstApp.title
                    val isEnabled = firstApp.isRuleEnabled

                    val targetBlocker = when {
                        associatedApps.any { it.packageName == "keyword_blocker" } -> "Keyword Blocker"
                        associatedApps.any { it.packageName == "website_blocker" } -> "Website Blocker"
                        associatedApps.any { it.packageName == "reel_blocker" } -> "Reels Blocker"
                        associatedApps.any { it.packageName == "FOCUS_MODE" || it.packageName == "focus_mode" } -> "Focus Mode"
                        else -> "App Blocker"
                    }

                    val apps = associatedApps.mapNotNull { it.packageName }.filter {
                        it != "keyword_blocker" && it != "website_blocker" && it != "reel_blocker" && it != "FOCUS_MODE" && it != "focus_mode"
                    }.distinct()

                    val appOrCategory = when (targetBlocker) {
                        "Keyword Blocker" -> "Keywords Blocker"
                        "Website Blocker" -> "Website Blocker"
                        "Reels Blocker" -> "Reels Blocker"
                        "Focus Mode" -> "Focus Mode Schedules"
                        else -> {
                            if (apps.size == 1) {
                                try {
                                    requireContext().packageManager.getApplicationLabel(
                                        requireContext().packageManager.getApplicationInfo(apps.first(), 0)
                                    ).toString()
                                } catch (_: Throwable) {
                                    apps.first()
                                }
                            } else if (apps.size > 1) {
                                "${apps.size} Apps"
                            } else {
                                "App Blocker"
                            }
                        }
                    }

                    // 1. Standard Schedule & Always Block
                    val blockRules = associatedApps.filter { (it.type == AppBlockScheduleRule.RuleType.BLOCK) && it.durationHours <= 0 }
                    val isAlwaysBlockEnabled = blockRules.any { it.recurrence == AppBlockScheduleRule.Recurrence.ALWAYS }
                    val isScheduleEnabled = blockRules.isNotEmpty() && !isAlwaysBlockEnabled
                    val firstBlock = blockRules.firstOrNull()

                    val startMin = (firstBlock?.startMinute ?: 540).coerceAtLeast(0)
                    val endMin = (firstBlock?.endMinute ?: 1020).coerceAtLeast(0)
                    val scheduleStartTime = String.format("%02d:%02d", startMin / 60, startMin % 60)
                    val scheduleEndTime = String.format("%02d:%02d", endMin / 60, endMin % 60)

                    val scheduleDays = if (firstBlock != null && firstBlock.selectedDays.isNotEmpty()) {
                        firstBlock.selectedDays.map { calendarIntToDay(it) }
                    } else {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri")
                    }

                    // 2. Cheat Window
                    val cheatRules = associatedApps.filter { it.type == AppBlockScheduleRule.RuleType.CHEAT }
                    val isCheatEnabled = cheatRules.isNotEmpty()
                    val firstCheat = cheatRules.firstOrNull()
                    val cheatStartMin = (firstCheat?.startMinute ?: 720).coerceAtLeast(0)
                    val cheatEndMin = (firstCheat?.endMinute ?: 780).coerceAtLeast(0)
                    val cheatStartTime = String.format("%02d:%02d", cheatStartMin / 60, cheatStartMin % 60)
                    val cheatEndTime = String.format("%02d:%02d", cheatEndMin / 60, cheatEndMin % 60)
                    val cheatDays = if (firstCheat != null && firstCheat.selectedDays.isNotEmpty()) {
                        firstCheat.selectedDays.map { calendarIntToDay(it) }
                    } else {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    }

                    // 3. Usage Limit
                    val usageRules = associatedApps.filter { (it.type == AppBlockScheduleRule.RuleType.BLOCK) && it.durationHours > 0 }
                    val isUsageLimitEnabled = usageRules.isNotEmpty()
                    val usageLimitHours = if (isUsageLimitEnabled) usageRules.first().durationHours else 0

                    // 4. Launch Limit
                    val associatedLaunchLimit = launchLimits.firstOrNull { apps.contains(it.packageName) }
                    val isLaunchLimitEnabled = associatedLaunchLimit != null
                    val launchLimitCount = associatedLaunchLimit?.maxLaunches ?: 0

                    val isFocusLengthEnabled = targetBlocker == "Focus Mode" && isUsageLimitEnabled
                    val focusProtectionMode = if (targetBlocker == "Focus Mode") savedPreferencesLoader.getFocusModeData().modeType else com.alhaq.amnshield.Constants.FOCUS_MODE_BLOCK_SELECTED

                    val restrictionTypeStr = when {
                        targetBlocker == "Focus Mode" && isFocusLengthEnabled -> "Focus Length (${usageLimitHours}h/day)"
                        targetBlocker == "Focus Mode" -> "Auto Focus Schedule"
                        isAlwaysBlockEnabled -> "Always Block (24/7)"
                        isScheduleEnabled -> "Block Schedule"
                        isUsageLimitEnabled -> "Usage Limit"
                        isLaunchLimitEnabled -> "Launch Limit"
                        isCheatEnabled -> "Cheat Window"
                        else -> "Block Schedule"
                    }

                    val periods = mutableListOf<com.alhaq.amnshield.ui.state.SchedulePeriod>()
                    associatedApps.forEach { item ->
                        val sMin = item.startMinute.coerceAtLeast(0)
                        val eMin = item.endMinute.coerceAtLeast(0)
                        val start = String.format("%02d:%02d", sMin / 60, sMin % 60)
                        val end = String.format("%02d:%02d", eMin / 60, eMin % 60)
                        val daysList = item.selectedDays.map { calendarIntToDay(it) }
                        periods.add(com.alhaq.amnshield.ui.state.SchedulePeriod(start, end, daysList))
                    }
                    val distinctPeriods = periods.distinctBy { Triple(it.startTime, it.endTime, it.days.sorted()) }

                    rulesList.add(
                        com.alhaq.amnshield.ui.state.ScheduleRule(
                            id = groupId,
                            name = name,
                            appOrCategory = appOrCategory,
                            restrictionType = restrictionTypeStr,
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays,
                            limitValue = if (isFocusLengthEnabled) usageLimitHours else if (isUsageLimitEnabled) usageLimitHours else launchLimitCount,
                            isActive = isEnabled,
                            periods = distinctPeriods,
                            targetBlockerType = targetBlocker,
                            selectedApps = apps,
                            selectedBlockers = listOf(targetBlocker),
                            
                            isAlwaysBlockEnabled = isAlwaysBlockEnabled,
                            isScheduleEnabled = isScheduleEnabled,
                            isCheatEnabled = isCheatEnabled,
                            cheatStartTime = cheatStartTime,
                            cheatEndTime = cheatEndTime,
                            cheatDays = cheatDays,
                            isUsageLimitEnabled = isUsageLimitEnabled,
                            usageLimitHours = usageLimitHours,
                            isLaunchLimitEnabled = isLaunchLimitEnabled,
                            launchLimitCount = launchLimitCount,

                            focusProtectionMode = focusProtectionMode,
                            isFocusLengthEnabled = isFocusLengthEnabled,
                            focusLengthHours = usageLimitHours
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("BlocksManagerFragment", "Error processing schedule rules", e)
        }

        try {
            val groupAppPkgs = rulesList.flatMap { it.selectedApps }.toSet()
            launchLimits.filter { !groupAppPkgs.contains(it.packageName) }.forEach { limit ->
                if (limit.packageName.isBlank()) return@forEach
                val appName = try {
                    requireContext().packageManager.getApplicationLabel(
                        requireContext().packageManager.getApplicationInfo(limit.packageName, 0)
                    ).toString()
                } catch (_: Throwable) {
                    limit.packageName
                }
                rulesList.add(
                    com.alhaq.amnshield.ui.state.ScheduleRule(
                        id = "limit::${limit.packageName}",
                        name = "Limit: $appName",
                        appOrCategory = appName,
                        restrictionType = "Launch Limit",
                        startTime = "00:00",
                        endTime = "23:59",
                        days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                        limitValue = limit.maxLaunches,
                        isActive = true,
                        targetBlockerType = "App Blocker",
                        selectedApps = listOf(limit.packageName),
                        selectedBlockers = listOf("App Blocker"),
                        
                        isLaunchLimitEnabled = true,
                        launchLimitCount = limit.maxLaunches
                    )
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("BlocksManagerFragment", "Error processing launch limits", e)
        }

        val isAdvanced = true

        viewModel.loadState(
            viewModel.state.value.copy(
                scheduleRules = rulesList,
                isAdvancedMode = isAdvanced
            )
        )
    }

    private fun saveScheduleRule(rule: com.alhaq.amnshield.ui.state.ScheduleRule) {
        // Delete any existing rules with the same groupId/id to avoid conflicts
        savedPreferencesLoader.removeAppBlockerScheduleGroup(rule.id)
        savedPreferencesLoader.removeAppBlockerScheduleRule(rule.id)

        // Clean up launch limits for all of the group's packages
        rule.selectedApps?.forEach { appPkg ->
            savedPreferencesLoader.removeAppLaunchLimitRule(appPkg)
        }

        val groupId = rule.id
        val groupTitle = rule.name

        // Determine target packages for database entries
        val targetPackages = when (rule.targetBlockerType) {
            "Keyword Blocker" -> listOf("keyword_blocker")
            "Website Blocker" -> listOf("website_blocker")
            "Reels Blocker" -> listOf("reel_blocker")
            "Focus Mode" -> listOf("FOCUS_MODE")
            else -> rule.selectedApps ?: emptyList()
        }

        // 1. Save Block Schedule Rules if enabled
        if (rule.isAlwaysBlockEnabled || rule.isScheduleEnabled) {
            val startMin = if (rule.isAlwaysBlockEnabled) 0 else timeToMinutes(rule.startTime)
            val endMin = if (rule.isAlwaysBlockEnabled) 0 else timeToMinutes(rule.endTime)
            val calendarDays = if (rule.isAlwaysBlockEnabled) emptySet() else rule.days.map { dayToCalendarInt(it) }.toSet()
            val recurrence = if (rule.isAlwaysBlockEnabled) {
                AppBlockScheduleRule.Recurrence.ALWAYS
            } else if (rule.days.size == 7) {
                AppBlockScheduleRule.Recurrence.DAILY
            } else {
                AppBlockScheduleRule.Recurrence.WEEKLY
            }

            targetPackages.forEach { pkg ->
                val appRule = AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = if (rule.isAlwaysBlockEnabled) "Block Always • $groupTitle" else "Block • $groupTitle",
                    packageName = pkg,
                    type = AppBlockScheduleRule.RuleType.BLOCK,
                    recurrence = recurrence,
                    startMinute = startMin,
                    endMinute = endMin,
                    selectedDays = calendarDays,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive
                )
                savedPreferencesLoader.upsertAppBlockerScheduleRule(appRule)
            }
        }

        // 2. Save Cheat Hours Rules if enabled
        if (rule.isCheatEnabled) {
            val startMin = timeToMinutes(rule.cheatStartTime)
            val endMin = timeToMinutes(rule.cheatEndTime)
            val calendarDays = rule.cheatDays.map { dayToCalendarInt(it) }.toSet()
            val recurrence = if (rule.cheatDays.size == 7) {
                AppBlockScheduleRule.Recurrence.DAILY
            } else {
                AppBlockScheduleRule.Recurrence.WEEKLY
            }

            targetPackages.forEach { pkg ->
                val appRule = AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = "Cheat • $groupTitle",
                    packageName = pkg,
                    type = AppBlockScheduleRule.RuleType.CHEAT,
                    recurrence = recurrence,
                    startMinute = startMin,
                    endMinute = endMin,
                    selectedDays = calendarDays,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive
                )
                savedPreferencesLoader.upsertAppBlockerScheduleRule(appRule)
            }
        }

        // 3. Save Usage Limit Rules if App Blocker and enabled
        if (rule.targetBlockerType == "App Blocker" && rule.isUsageLimitEnabled) {
            targetPackages.forEach { pkg ->
                val appRule = AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = "Usage Limit • $groupTitle • ${rule.usageLimitHours}h",
                    packageName = pkg,
                    type = AppBlockScheduleRule.RuleType.BLOCK,
                    recurrence = AppBlockScheduleRule.Recurrence.DAILY,
                    startMinute = 0,
                    endMinute = 0,
                    selectedDays = emptySet(),
                    durationHours = rule.usageLimitHours,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive
                )
                savedPreferencesLoader.upsertAppBlockerScheduleRule(appRule)
            }
        }

        // 3b. Focus Mode Specific Saving
        if (rule.targetBlockerType == "Focus Mode") {
            savedPreferencesLoader.saveFocusModeSelectedApps(rule.selectedApps ?: emptyList())
            val currentData = savedPreferencesLoader.getFocusModeData()
            val updatedData = currentData.copy(
                modeType = rule.focusProtectionMode,
                selectedApps = HashSet(rule.selectedApps ?: emptyList())
            )
            savedPreferencesLoader.saveFocusModeData(updatedData)

            if (rule.isFocusLengthEnabled) {
                val appRule = AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = "Focus Length • $groupTitle • ${rule.focusLengthHours}h/day",
                    packageName = "FOCUS_MODE",
                    type = AppBlockScheduleRule.RuleType.BLOCK,
                    recurrence = AppBlockScheduleRule.Recurrence.DAILY,
                    startMinute = 0,
                    endMinute = 0,
                    selectedDays = emptySet(),
                    durationHours = rule.focusLengthHours,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive
                )
                savedPreferencesLoader.upsertAppBlockerScheduleRule(appRule)
            }
        }

        // 4. Save Launch Limit Rules if App Blocker and enabled
        if (rule.targetBlockerType == "App Blocker" && rule.isLaunchLimitEnabled) {
            targetPackages.forEach { pkg ->
                val limitRule = AppLaunchLimitRule(
                    id = "limit::$pkg",
                    packageName = pkg,
                    maxLaunches = rule.launchLimitCount,
                    timePeriod = AppLaunchLimitRule.TimePeriod.DAILY,
                    createdAt = System.currentTimeMillis()
                )
                savedPreferencesLoader.addAppLaunchLimitRule(limitRule)
            }
        }

        sendRefreshRequest()
        loadSchedulesAndLimits()
        Toast.makeText(requireContext(), "Rule applied successfully", Toast.LENGTH_SHORT).show()
    }

    // Callbacks from ManageSchedulesScreen
    fun toggleScheduleRuleActive(id: String) {
        viewModel.toggleScheduleRuleActive(id)
        
        if (id.startsWith("limit::")) {
            // Launch Limits don't have built-in switch toggles in legacy backend, they are deleted
        } else {
            val appRules = savedPreferencesLoader.loadAppBlockerScheduleRules()
            var appModified = false
            var targetState: Boolean? = null
            val affectedPackages = mutableListOf<String>()
            
            appRules.forEachIndexed { idx, item ->
                if (item.groupId == id || item.id == id) {
                    if (targetState == null) {
                        targetState = !(item.isEnabled ?: true)
                    }
                    appRules[idx] = item.copy(isEnabled = targetState)
                    appModified = true
                    affectedPackages.add(item.packageName)
                }
            }
            if (appModified) {
                savedPreferencesLoader.saveAppBlockerScheduleRules(appRules)

                // Sync blocked_apps in SharedPreferences
                val currentBlocked = savedPreferencesLoader.loadBlockedApps().toMutableSet()
                if (targetState == true) {
                    affectedPackages.forEach { pkg ->
                        if (pkg != "reel_blocker" && pkg != "website_blocker" && pkg != "keyword_blocker" && pkg != "FOCUS_MODE" && pkg != "focus_mode") {
                            currentBlocked.add(pkg)
                        }
                    }
                } else {
                    val remainingEnabledPackages = appRules.filter { it.isRuleEnabled }.map { it.packageName }.toSet()
                    affectedPackages.forEach { pkg ->
                        if (!remainingEnabledPackages.contains(pkg)) {
                            currentBlocked.remove(pkg)
                        }
                    }
                }
                savedPreferencesLoader.saveBlockedApps(currentBlocked)
            }
        }
        sendRefreshRequest()
        loadSchedulesAndLimits()
    }

    fun deleteScheduleRule(id: String) {
        viewModel.deleteScheduleRule(id)
        
        if (id.startsWith("limit::")) {
            val pkg = id.removePrefix("limit::")
            savedPreferencesLoader.removeAppLaunchLimitRule(pkg)

            val appRules = savedPreferencesLoader.loadAppBlockerScheduleRules()
            val remainingEnabledPackages = appRules.filter { it.isRuleEnabled }.map { it.packageName }.toSet()
            val currentBlocked = savedPreferencesLoader.loadBlockedApps().toMutableSet()
            if (!remainingEnabledPackages.contains(pkg)) {
                currentBlocked.remove(pkg)
                savedPreferencesLoader.saveBlockedApps(currentBlocked)
            }
        } else {
            val appSchedules = savedPreferencesLoader.loadAppBlockerScheduleRules()
            val associatedApps = appSchedules.filter { (it.groupId ?: it.id) == id }.map { it.packageName }
            associatedApps.forEach { pkg ->
                savedPreferencesLoader.removeAppLaunchLimitRule(pkg)
            }
            savedPreferencesLoader.removeAppBlockerScheduleGroup(id)
            savedPreferencesLoader.removeAppBlockerScheduleRule(id)

            // Remove associated apps from blocked_apps if no other enabled rule uses them
            val updatedRules = savedPreferencesLoader.loadAppBlockerScheduleRules()
            val remainingEnabledPackages = updatedRules.filter { it.isRuleEnabled }.map { it.packageName }.toSet()
            val currentBlocked = savedPreferencesLoader.loadBlockedApps().toMutableSet()
            associatedApps.forEach { pkg ->
                if (!remainingEnabledPackages.contains(pkg)) {
                    currentBlocked.remove(pkg)
                }
            }
            savedPreferencesLoader.saveBlockedApps(currentBlocked)
        }
        sendRefreshRequest()
        loadSchedulesAndLimits()
        Toast.makeText(requireContext(), "Rule deleted", Toast.LENGTH_SHORT).show()
    }

    private fun sendRefreshRequest() {
        val context = context ?: return
        val appIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        context.sendBroadcast(appIntent.setPackage(context.packageName))

        val unifiedIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
        context.sendBroadcast(unifiedIntent.setPackage(context.packageName))

        val focusIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        context.sendBroadcast(focusIntent.setPackage(context.packageName))

        val reelIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER)
        context.sendBroadcast(reelIntent.setPackage(context.packageName))

        val keywordIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
        context.sendBroadcast(keywordIntent.setPackage(context.packageName))

        val viewIntent = Intent(AmnShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER)
        context.sendBroadcast(viewIntent.setPackage(context.packageName))
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun dayToCalendarInt(day: String): Int {
        return when (day.lowercase()) {
            "sun" -> Calendar.SUNDAY
            "mon" -> Calendar.MONDAY
            "tue" -> Calendar.TUESDAY
            "wed" -> Calendar.WEDNESDAY
            "thu" -> Calendar.THURSDAY
            "fri" -> Calendar.FRIDAY
            "sat" -> Calendar.SATURDAY
            else -> Calendar.MONDAY
        }
    }

    private fun calendarIntToDay(dayInt: Int): String {
        return when (dayInt) {
            Calendar.SUNDAY -> "Sun"
            Calendar.MONDAY -> "Mon"
            Calendar.TUESDAY -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY -> "Thu"
            Calendar.FRIDAY -> "Fri"
            Calendar.SATURDAY -> "Sat"
            else -> "Mon"
        }
    }

    companion object {
        private const val FEATURE_RULE_PREFIX = "ufs::"
        private const val APP_GROUP_RULE_PREFIX = "app_group::"
        private const val FEATURE_GROUP_RULE_PREFIX = "feature_group::"
        const val FRAGMENT_ID = "blocks_manager_fragment"
    }
}

private data class SelectBlockerOptionItem(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconBgColor: Color,
    val iconTintColor: Color,
    val key: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectBlockerBottomSheet(
    onDismissRequest: () -> Unit,
    onSelectOption: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val options = remember {
        listOf(
            SelectBlockerOptionItem(
                title = "App Blocker",
                description = "Block specific apps or entire app categories",
                icon = Icons.Default.Apps,
                iconBgColor = Color(0xFF10B981).copy(alpha = 0.15f),
                iconTintColor = Color(0xFF059669),
                key = "App Blocker"
            ),
            SelectBlockerOptionItem(
                title = "Keyword Blocker",
                description = "Block explicit keywords, search queries & titles",
                icon = Icons.Default.VpnKey,
                iconBgColor = Color(0xFF3B82F6).copy(alpha = 0.15f),
                iconTintColor = Color(0xFF2563EB),
                key = "Keyword Blocker"
            ),
            SelectBlockerOptionItem(
                title = "Website Blocker",
                description = "Block website URLs, adult sites & custom domains",
                icon = Icons.Default.Language,
                iconBgColor = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                iconTintColor = Color(0xFF7C3AED),
                key = "Website Blocker"
            ),
            SelectBlockerOptionItem(
                title = "Reels Blocker",
                description = "Block addictive short-form video feeds & reels",
                icon = Icons.Default.Movie,
                iconBgColor = Color(0xFFEC4899).copy(alpha = 0.15f),
                iconTintColor = Color(0xFFDB2777),
                key = "Reels Blocker"
            ),
            SelectBlockerOptionItem(
                title = "Focus Mode",
                description = "Set strict scheduled focus windows with custom restrictions",
                icon = Icons.Default.Timer,
                iconBgColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                iconTintColor = Color(0xFFD97706),
                key = "Focus Mode"
            )
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select Protection Blocker",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Choose which type of blocker rule you would like to schedule:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            options.forEach { item ->
                Surface(
                    onClick = { onSelectOption(item.key) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(item.iconBgColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.iconTintColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
