package com.alhaq.amnishield.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import com.alhaq.amnishield.R
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import android.content.ComponentName
import android.content.Context
import android.app.admin.DevicePolicyManager
import com.alhaq.amnishield.receivers.AdminReceiver
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.fragments.features.BaseFeatureFragment
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.utils.ScheduleUtils
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.screens.BlocksScreen
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.ui.state.AmniShieldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

class BlocksFragment : BaseFeatureFragment() {

    private val premiumManager by lazy { PremiumManager.getInstance(requireContext().applicationContext) }
    private val blocksLoader by lazy { SavedPreferencesLoader(requireContext()) }
    private lateinit var viewModel: AmniShieldViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AmniShieldViewModel::class.java]
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
                    BlocksScreen(
                        state = state,
                        viewModel = viewModel,
                        onNavigateToAppBlocker = { openFeatureConfig("app_blocker", requiresPremium = false) },
                        onNavigateToKeywordBlocker = { openFeatureConfig("keyword_blocker", requiresPremium = false) },
                        onNavigateToWebBlocker = { openFeatureConfig("social_media_blocker", requiresPremium = false) },
                        onNavigateToFocusMode = { openFeatureConfig("focus_mode", requiresPremium = false) },
                        onNavigateToCheatHours = { openCheatHours() },
                        onNavigateToSchedules = { openSchedules() },
                        onNavigateToLaunchLimits = { openLaunchLimits() },
                        onNavigateToAntiUninstall = { openFeatureConfig("anti_uninstall", requiresPremium = true) },
                        onNavigateToUsageTracker = { openFeatureConfig("usage_tracker", requiresPremium = false) },
                        onNavigateToReelsBlocker = { openFeatureConfig("reel_blocker", requiresPremium = false) },
                        onNavigateToPremium = { openFeatureConfig("premium_features", requiresPremium = false) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            refreshDashboard()
        } catch (e: Throwable) {
            android.util.Log.e("BlocksFragment", "refreshDashboard failed", e)
        }
    }

    private fun refreshDashboard() {
        val serviceEnabled = isAccessibilityServiceEnabled(AmniShieldAccessibilityService::class.java)
        val premiumEnabled = premiumManager.isPremium()

        val appBlockedApps = blocksLoader.loadBlockedApps().size
        val appActive = blocksLoader.isAppBlockerFeatureEnabled() && appBlockedApps > 0 && serviceEnabled

        val keywordCount = blocksLoader.loadBlockedKeywords().size
        val adultPackEnabled = blocksLoader.isKeywordBlockerAdultPackEnabled()
        val keywordActive = blocksLoader.isKeywordBlockerFeatureEnabled() && (keywordCount > 0 || adultPackEnabled) && serviceEnabled

        val focusData = blocksLoader.getFocusModeData()
        val focusActive = focusData.isTurnedOn && serviceEnabled

        val websiteActive = blocksLoader.isWebsiteBlockerEnabled() && serviceEnabled

        val allSchedules = blocksLoader.loadAppBlockerScheduleRules()
        val cheatCount = allSchedules.count { it.type == AppBlockScheduleRule.RuleType.CHEAT }
        val scheduleCount = allSchedules.count { it.type == AppBlockScheduleRule.RuleType.BLOCK }
        val launchLimitCount = blocksLoader.loadAppLaunchLimitRules().size

        val reelsMasterEnabled = blocksLoader.isReelBlockerEnabled()
        val reelsAnyPlatformEnabled = blocksLoader.isReelBlockerYoutubeEnabled() ||
                blocksLoader.isReelBlockerInstagramEnabled() ||
                blocksLoader.isReelBlockerTiktokEnabled() ||
                blocksLoader.isReelBlockerBrowserEnabled()
        val reelsActive = reelsMasterEnabled && reelsAnyPlatformEnabled && serviceEnabled

        val antiUninstallPrefs = requireContext().getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        val hasDeviceAdmin = isDeviceAdminEnabled(requireContext())
        val antiUninstallActive = antiUninstallPrefs.getBoolean("is_anti_uninstall_on", false) && hasDeviceAdmin

        val usageTrackerActive = blocksLoader.isUsageTrackerFeatureEnabled() && serviceEnabled

        val rulesList = loadSchedulesAndLimits()

        // Load actual live configurations to AmniShieldState
        viewModel.loadState(
            AmniShieldState(
                isMainServiceEnabled = serviceEnabled,
                isPremiumUser = premiumEnabled,
                isUsageTrackerEnabled = usageTrackerActive,
                isAntiUninstallEnabled = antiUninstallActive,
                isAppBlockerEnabled = appActive,
                isReelsBlockerEnabled = reelsActive,
                isKeywordBlockerEnabled = keywordActive,
                isWebFilterEnabled = websiteActive,
                isFocusModeActive = focusActive,
                isScheduleEnabled = scheduleCount > 0,
                isUsageLimitEnabled = launchLimitCount > 0,
                keywords = blocksLoader.loadBlockedKeywords().toList(),
                isAdvancedMode = blocksLoader.getEnforcementMode() == "ADVANCED",
                scheduleRules = rulesList
            )
        )
    }

    private fun loadSchedulesAndLimits(): List<com.alhaq.amnishield.ui.state.ScheduleRule> {
        val appSchedules = try {
            blocksLoader.loadAppBlockerScheduleRules()
        } catch (e: Throwable) {
            emptyList()
        }
        val launchLimits = try {
            blocksLoader.loadAppLaunchLimitRules()
        } catch (e: Throwable) {
            emptyList()
        }

        val rulesList = mutableListOf<com.alhaq.amnishield.ui.state.ScheduleRule>()

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
                                val pm = context?.packageManager
                                if (pm != null) {
                                    try {
                                        pm.getApplicationLabel(pm.getApplicationInfo(apps.first(), 0)).toString()
                                    } catch (_: Throwable) {
                                        apps.first()
                                    }
                                } else {
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
                        firstBlock.selectedDays.map { ScheduleUtils.calendarIntToDay(it) }
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
                        firstCheat.selectedDays.map { ScheduleUtils.calendarIntToDay(it) }
                    } else {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                    }

                    // 3. Usage Limit
                    val usageRules = associatedApps.filter { (it.type == AppBlockScheduleRule.RuleType.BLOCK) && it.durationHours > 0 }
                    val isUsageLimitEnabled = usageRules.isNotEmpty()
                    val usageLimitHours = if (isUsageLimitEnabled) usageRules.first().durationHours else 0

                    val restrictionTypeStr = when {
                        isAlwaysBlockEnabled -> "Always Block (24/7)"
                        isScheduleEnabled -> "Block Schedule"
                        isUsageLimitEnabled -> "Usage Limit"
                        isCheatEnabled -> "Cheat Window"
                        else -> "Block Schedule"
                    }

                    val periods = mutableListOf<com.alhaq.amnishield.ui.state.SchedulePeriod>()
                    associatedApps.forEach { item ->
                        val sMin = item.startMinute.coerceAtLeast(0)
                        val eMin = item.endMinute.coerceAtLeast(0)
                        val start = String.format("%02d:%02d", sMin / 60, sMin % 60)
                        val end = String.format("%02d:%02d", eMin / 60, eMin % 60)
                        val daysList = item.selectedDays.map { ScheduleUtils.calendarIntToDay(it) }
                        periods.add(com.alhaq.amnishield.ui.state.SchedulePeriod(start, end, daysList))
                    }
                    val distinctPeriods = periods.distinctBy { Triple(it.startTime, it.endTime, it.days.sorted()) }

                    rulesList.add(
                        com.alhaq.amnishield.ui.state.ScheduleRule(
                            id = groupId,
                            name = name,
                            appOrCategory = appOrCategory,
                            restrictionType = restrictionTypeStr,
                            startTime = scheduleStartTime,
                            endTime = scheduleEndTime,
                            days = scheduleDays,
                            limitValue = if (isUsageLimitEnabled) usageLimitHours else 0,
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
                            usageLimitHours = usageLimitHours
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("BlocksFragment", "Error processing schedule rules", e)
        }

        // 3. Launch Limits
        try {
            launchLimits.forEach { limit ->
                if (limit.packageName.isBlank()) return@forEach
                val pm = context?.packageManager
                val appName = if (pm != null) {
                    try {
                        pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
                    } catch (_: Throwable) {
                        limit.packageName
                    }
                } else {
                    limit.packageName
                }
                rulesList.add(
                    com.alhaq.amnishield.ui.state.ScheduleRule(
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
                        selectedBlockers = listOf("App Blocker")
                    )
                )
            }
        } catch (e: Throwable) {
            android.util.Log.e("BlocksFragment", "Error processing launch limits", e)
        }

        return rulesList
    }

    private fun openFeatureConfig(featureType: String, requiresPremium: Boolean) {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("feature_type", featureType)
        }
        startActivity(intent, activityOptions.toBundle())
    }

    private fun openCheatHours() {
        openSchedules()
    }

    private fun openSchedules() {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("fragment", BlocksManagerFragment.FRAGMENT_ID)
        }
        startActivity(intent, activityOptions.toBundle())
    }

    private fun openLaunchLimits() {
        val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
            putExtra("fragment", ManageLaunchLimitsFragment.FRAGMENT_ID)
        }
        startActivity(intent, activityOptions.toBundle())
    }

    private fun showPremiumUpsell() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.premium_required_title)
            .setMessage(getString(R.string.premium_required_message))
            .setPositiveButton(R.string.premium_view_plans) { _, _ ->
                val intent = Intent(requireContext(), FragmentActivity::class.java).apply {
                    putExtra("feature_type", "premium_features")
                }
                startActivity(intent, activityOptions.toBundle())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isDeviceAdminEnabled(ctx: Context): Boolean {
        val devicePolicyManager = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(ctx, AdminReceiver::class.java)
        return devicePolicyManager?.isAdminActive(adminComponent) == true
    }
}