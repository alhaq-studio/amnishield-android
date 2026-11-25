package com.alhaq.amnishield.ui.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.AmniSpaceActivity
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.activity.SelectAppsActivity
import com.alhaq.amnishield.ui.fragments.features.BaseFeatureFragment
import com.alhaq.amnishield.ui.dialogs.StartFocusMode
import com.alhaq.amnishield.ui.screens.FocusScreen
import com.alhaq.amnishield.ui.screens.FocusAppItem
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.state.AppTheme
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.ui.screens.CreateFocusModeRuleScreen
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.NotificationTimerManager
import androidx.lifecycle.ViewModelProvider
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class FocusFragment : BaseFeatureFragment() {

    private val premiumManager by lazy { PremiumManager.getInstance(requireContext().applicationContext) }
    private val loader by lazy { SavedPreferencesLoader(requireContext()) }
    private lateinit var viewModel: AmniShieldViewModel

    private val isServiceEnabled = mutableStateOf(false)
    private val isFocusModeActive = mutableStateOf(false)
    private val focusModeEndTime = mutableStateOf(0L)
    private val allowedAppsCount = mutableStateOf(0)

    private val installedAppsList = mutableStateListOf<FocusAppItem>()
    private val preSelectedApps = mutableStateOf<Set<String>>(emptySet())
    private val defaultMode = mutableStateOf(1)
    private val focusSchedules = mutableStateListOf<ScheduleRule>()
    private val showScheduleEditor = mutableStateOf(false)
    private val editingFocusSchedule = mutableStateOf<ScheduleRule?>(null)

    private data class PendingSession(
        val durationMinutes: Int,
        val mode: Int,
        val selectedApps: Set<String>
    )
    private var pendingSession: PendingSession? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingSession?.let {
                startFocusSession(it.durationMinutes, it.mode, it.selectedApps)
            }
        } else {
            Toast.makeText(requireContext(), "Notification permission is required for focus timers.", Toast.LENGTH_SHORT).show()
        }
        pendingSession = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[AmniShieldViewModel::class.java]
    }

    private val selectAppsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
            selectedApps?.let {
                loader.saveFocusModeSelectedApps(it)
                refreshFocusState()
                val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                    setPackage(requireContext().packageName)
                }
                requireContext().sendBroadcast(intent)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = com.alhaq.amnishield.utils.ThemeUtils.resolveAppTheme(requireContext())
                viewModel.updateTheme(activeTheme)
                val state by viewModel.state.collectAsState()
                AmniShieldTheme(appTheme = activeTheme) {
                    if (showScheduleEditor.value) {
                        CreateFocusModeRuleScreen(
                            state = state,
                            editingRule = editingFocusSchedule.value,
                            onSaveRule = { rule ->
                                saveFocusScheduleRule(rule)
                                showScheduleEditor.value = false
                                editingFocusSchedule.value = null
                            },
                            onBack = {
                                showScheduleEditor.value = false
                                editingFocusSchedule.value = null
                            },
                            onDeleteRule = { id ->
                                deleteFocusScheduleRule(id)
                                showScheduleEditor.value = false
                                editingFocusSchedule.value = null
                            },
                            onNavigateToPremium = { openPremiumScreen() }
                        )
                    } else {
                        FocusScreen(
                            isServiceEnabled = isServiceEnabled.value,
                            isFocusModeActive = isFocusModeActive.value,
                            focusModeEndTime = focusModeEndTime.value,
                            installedApps = installedAppsList,
                            preSelectedApps = preSelectedApps.value,
                            defaultMode = defaultMode.value,
                            onStartFocusSession = { durationMinutes, mode, selectedApps ->
                                checkNotificationPermissionAndStart(durationMinutes, mode, selectedApps)
                            },
                            onStopFocusSession = { stopFocusSession() },
                            onOpenFocusConfig = { openFocusConfig() },
                            onConfigureSchedules = {
                                editingFocusSchedule.value = null
                                showScheduleEditor.value = true
                            },
                            onEnableService = { enableService() },
                            focusSchedules = focusSchedules,
                            onCreateFocusSchedule = {
                                editingFocusSchedule.value = null
                                showScheduleEditor.value = true
                            },
                            onEditFocusSchedule = { schedule ->
                                editingFocusSchedule.value = schedule
                                showScheduleEditor.value = true
                            },
                            onToggleFocusSchedule = { id, enabled ->
                                toggleFocusScheduleRule(id, enabled)
                            },
                            onDeleteFocusSchedule = { id ->
                                deleteFocusScheduleRule(id)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun stopFocusSession() {
        loader.stopFocusSession()
        val timer = NotificationTimerManager(requireContext())
        timer.stopTimer()
        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(requireContext().packageName)
        }
        requireContext().sendBroadcast(intent)
        refreshFocusState()
    }

    override fun onResume() {
        super.onResume()
        refreshFocusState()
        loadInstalledApps()
    }

    private fun refreshFocusState() {
        isServiceEnabled.value = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java)
        val focusData = loader.getFocusModeData()
        isFocusModeActive.value = focusData.isTurnedOn
        focusModeEndTime.value = focusData.endTime
        allowedAppsCount.value = loader.getFocusModeSelectedApps().size
        preSelectedApps.value = loader.getFocusModeSelectedApps().toSet()
        defaultMode.value = focusData.modeType
        loadFocusSchedules()
    }

    private fun checkNotificationPermissionAndStart(durationMinutes: Int, mode: Int, selectedApps: Set<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                pendingSession = PendingSession(durationMinutes, mode, selectedApps)
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startFocusSession(durationMinutes, mode, selectedApps)
    }

    private fun startFocusSession(durationMinutes: Int, mode: Int, selectedApps: Set<String>) {
        val durationMillis = durationMinutes * 60_000L
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMillis
        
        loader.saveFocusModeSelectedApps(selectedApps.toList())
        loader.saveFocusModeData(
            FocusModeBlocker.FocusModeData(
                isTurnedOn = true,
                endTime = endTime,
                modeType = mode,
                selectedApps = HashSet(selectedApps)
            )
        )
        loader.saveFocusSessionStartTime(startTime, endTime)
        
        val intent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(requireContext().packageName)
        }
        requireContext().sendBroadcast(intent)
        
        val timer = NotificationTimerManager(requireContext())
        timer.startTimer(
            totalMillis = durationMillis,
            onFinishCallback = {
                stopFocusSession()
            }
        )
        
        refreshFocusState()

        if (loader.getFocusModeInterceptionStyle() == Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE) {
            val workspaceIntent = Intent(requireContext(), AmniSpaceActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_FOCUS_LAUNCHER)
                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Focus Session Active")
            }
            startActivity(workspaceIntent)
        }
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pm = requireContext().packageManager
                val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val profiles = launcherApps.profiles
                val appList = mutableListOf<FocusAppItem>()
                val installedPackages = mutableSetOf<String>()

                for (profile in profiles) {
                    val apps = launcherApps.getActivityList(null, profile)
                        .map { it.applicationInfo }
                        .filter { it.packageName != requireContext().packageName }
                    
                    apps.forEach { appInfo ->
                        if (!installedPackages.contains(appInfo.packageName)) {
                            installedPackages.add(appInfo.packageName)
                            val appLabel = appInfo.loadLabel(pm).toString()
                            val bitmap = getAppIconBitmap(requireContext(), appInfo.packageName)
                            appList.add(FocusAppItem(appInfo.packageName, appLabel, bitmap))
                        }
                    }
                }
                appList.sortBy { it.label.lowercase() }
                withContext(Dispatchers.Main) {
                    installedAppsList.clear()
                    installedAppsList.addAll(appList)
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusFragment", "Error loading installed apps", e)
            }
        }
    }

    private fun getAppIconBitmap(context: Context, packageName: String): android.graphics.Bitmap? {
        return try {
            val pm = context.packageManager
            val iconDrawable = pm.getApplicationIcon(packageName)
            val size = 96
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            iconDrawable.setBounds(0, 0, size, size)
            iconDrawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun openFocusConfig() {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("fragment", com.alhaq.amnishield.ui.fragments.features.FocusModeConfigFragment.FRAGMENT_ID)
        }
        startActivity(intent, activityOptions.toBundle())
    }

    private fun openPremiumScreen() {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("feature_type", "premium_features")
        }
        startActivity(intent, activityOptions.toBundle())
    }

    private fun loadFocusSchedules() {
        val rawRules = loader.loadAppBlockerScheduleRules().filter {
            it.blockerType == BlockerType.FOCUS_MODE || it.packageName.equals("FOCUS_MODE", ignoreCase = true)
        }
        val grouped = rawRules.groupBy { it.groupId ?: it.id }
        val loaded = grouped.map { (groupId, entries) ->
            val blockEntry = entries.firstOrNull { it.type == AppBlockScheduleRule.RuleType.BLOCK } ?: entries.first()
            val cheatEntry = entries.firstOrNull { it.type == AppBlockScheduleRule.RuleType.CHEAT }
            val isAlways = blockEntry.recurrence == AppBlockScheduleRule.Recurrence.ALWAYS
            val mappedDays = if (blockEntry.selectedDays.isEmpty()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            } else {
                blockEntry.selectedDays.map { calendarIntToDay(it) }.distinct()
            }
            val mappedCheatDays = if (cheatEntry == null || cheatEntry.selectedDays.isEmpty()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            } else {
                cheatEntry.selectedDays.map { calendarIntToDay(it) }.distinct()
            }
            ScheduleRule(
                id = groupId,
                name = (blockEntry.groupTitle ?: blockEntry.title)
                    .removePrefix("Block Always • ")
                    .removePrefix("Block • ")
                    .ifBlank { "Auto Focus Schedule" },
                targetBlockerType = "Focus Mode",
                startTime = minutesToTime(blockEntry.startMinute),
                endTime = minutesToTime(blockEntry.endMinute),
                days = mappedDays,
                isActive = blockEntry.isRuleEnabled,
                isAlwaysBlockEnabled = isAlways,
                isScheduleEnabled = !isAlways && blockEntry.type == AppBlockScheduleRule.RuleType.BLOCK,
                isCheatEnabled = cheatEntry != null,
                cheatStartTime = cheatEntry?.let { minutesToTime(it.startMinute) } ?: "12:00",
                cheatEndTime = cheatEntry?.let { minutesToTime(it.endMinute) } ?: "13:00",
                cheatDays = mappedCheatDays,
                selectedApps = blockEntry.targets.filter {
                    !it.equals("FOCUS_MODE", ignoreCase = true)
                },
                focusProtectionMode = blockEntry.focusProtectionMode,
                authType = blockEntry.authType,
                rulePasswordHash = blockEntry.rulePasswordHash,
                rulePasswordSalt = blockEntry.rulePasswordSalt
            )
        }
        focusSchedules.clear()
        focusSchedules.addAll(loaded)
    }

    private fun saveFocusScheduleRule(rule: ScheduleRule) {
        val groupId = rule.id.ifBlank { UUID.randomUUID().toString() }
        loader.removeAppBlockerScheduleGroup(groupId)
        loader.removeAppBlockerScheduleRule(groupId)

        val groupTitle = rule.name.ifBlank { "Auto Focus Schedule" }
        val targets = rule.selectedApps.ifEmpty { loader.getFocusModeSelectedApps() }

        if (rule.isAlwaysBlockEnabled || rule.isScheduleEnabled) {
            val startMin = if (rule.isAlwaysBlockEnabled) 0 else timeToMinutes(rule.startTime)
            val endMin = if (rule.isAlwaysBlockEnabled) 0 else timeToMinutes(rule.endTime)
            val calendarDays = if (rule.isAlwaysBlockEnabled) emptySet() else rule.days.map { dayToCalendarInt(it) }.toSet()
            val recurrence = when {
                rule.isAlwaysBlockEnabled -> AppBlockScheduleRule.Recurrence.ALWAYS
                rule.days.size == 7 -> AppBlockScheduleRule.Recurrence.DAILY
                else -> AppBlockScheduleRule.Recurrence.WEEKLY
            }
            loader.upsertAppBlockerScheduleRule(
                AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = if (rule.isAlwaysBlockEnabled) "Block Always • $groupTitle" else "Block • $groupTitle",
                    packageName = "FOCUS_MODE",
                    type = AppBlockScheduleRule.RuleType.BLOCK,
                    recurrence = recurrence,
                    startMinute = startMin,
                    endMinute = endMin,
                    selectedDays = calendarDays,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive,
                    blockerType = BlockerType.FOCUS_MODE,
                    targets = targets,
                    focusProtectionMode = rule.focusProtectionMode,
                    authType = rule.authType,
                    rulePasswordHash = rule.rulePasswordHash,
                    rulePasswordSalt = rule.rulePasswordSalt
                )
            )
        }

        if (rule.isCheatEnabled) {
            val calendarDays = rule.cheatDays.map { dayToCalendarInt(it) }.toSet()
            val recurrence = if (rule.cheatDays.size == 7) {
                AppBlockScheduleRule.Recurrence.DAILY
            } else {
                AppBlockScheduleRule.Recurrence.WEEKLY
            }
            loader.upsertAppBlockerScheduleRule(
                AppBlockScheduleRule(
                    id = UUID.randomUUID().toString(),
                    title = "Cheat • $groupTitle",
                    packageName = "FOCUS_MODE",
                    type = AppBlockScheduleRule.RuleType.CHEAT,
                    recurrence = recurrence,
                    startMinute = timeToMinutes(rule.cheatStartTime),
                    endMinute = timeToMinutes(rule.cheatEndTime),
                    selectedDays = calendarDays,
                    createdAt = System.currentTimeMillis(),
                    groupId = groupId,
                    groupTitle = groupTitle,
                    isEnabled = rule.isActive,
                    blockerType = BlockerType.FOCUS_MODE,
                    targets = targets,
                    focusProtectionMode = rule.focusProtectionMode,
                    authType = rule.authType,
                    rulePasswordHash = rule.rulePasswordHash,
                    rulePasswordSalt = rule.rulePasswordSalt
                )
            )
        }

        broadcastFocusScheduleRefresh()
        loadFocusSchedules()
    }

    private fun toggleFocusScheduleRule(id: String, enabled: Boolean) {
        val current = loader.loadAppBlockerScheduleRules()
        val updated = current.map { entry ->
            if ((entry.groupId ?: entry.id) == id) entry.copy(isEnabled = enabled) else entry
        }.toMutableList()
        loader.saveAppBlockerScheduleRules(updated)
        broadcastFocusScheduleRefresh()
        loadFocusSchedules()
    }

    private fun deleteFocusScheduleRule(id: String) {
        loader.removeAppBlockerScheduleGroup(id)
        loader.removeAppBlockerScheduleRule(id)
        broadcastFocusScheduleRefresh()
        loadFocusSchedules()
    }

    private fun broadcastFocusScheduleRefresh() {
        val ctx = requireContext()
        ctx.sendBroadcast(
            Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                setPackage(ctx.packageName)
            }
        )
        ctx.sendBroadcast(
            Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER).apply {
                setPackage(ctx.packageName)
            }
        )
    }

    private fun timeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size != 2) return 0
        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        return (hours * 60 + minutes).coerceIn(0, 1439)
    }

    private fun minutesToTime(totalMinutes: Int): String {
        val clamped = totalMinutes.coerceIn(0, 1439)
        return String.format(Locale.US, "%02d:%02d", clamped / 60, clamped % 60)
    }

    private fun dayToCalendarInt(day: String): Int {
        return when (day.lowercase(Locale.ROOT)) {
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

    private fun enableService() {
        showAccessibilityInfoDialog("AmniShield Accessibility Service", AmniShieldAccessibilityService::class.java)
    }
}
