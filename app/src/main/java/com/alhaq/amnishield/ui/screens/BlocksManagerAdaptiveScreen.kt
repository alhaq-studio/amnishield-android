package com.alhaq.amnishield.ui.screens

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.data.blockers.BlockRuleStatus
import com.alhaq.amnishield.data.blockers.UnifiedBlockRule
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.activity.FragmentActivity
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.UUID

/**
 * Adaptive Blocks workspace hosted inside [com.alhaq.amnishield.ui.AmniShieldAdaptiveApp].
 * Uses the unified All-in-One Block Engine (`UnifiedBlocksDashboardScreen`,
 * `CreateOrEditBlockScreen`, `SelectTargetsScreen`, and `WeeklyUsageReportScreen`) so the
 * adaptive Compose shell maintains parity with the standard fragment shell.
 */
@Composable
fun BlocksManagerAdaptiveScreen(
    state: AmniShieldState,
    viewModel: AmniShieldViewModel,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val loader = remember { SavedPreferencesLoader(context.applicationContext) }
    val authResolver = remember { AuthResolver.getInstance(context.applicationContext) }

    var currentSubScreen by remember { mutableStateOf("dashboard") }
    var unifiedRules by remember { mutableStateOf(loader.loadUnifiedBlockRules().toList()) }
    var alwaysOnConfig by remember { mutableStateOf(loader.loadAlwaysOnShieldsConfig()) }
    var reelsScrolledToday by remember { mutableIntStateOf(loader.getReelsScrolledToday()) }
    var hasGlobalPin by remember { mutableStateOf(authResolver.hasGlobalPin()) }

    var editingUnifiedRule by remember { mutableStateOf<UnifiedBlockRule?>(null) }
    var pickerApps by remember { mutableStateOf<Map<String, AppInterceptionLevel>>(emptyMap()) }
    var pickerDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickerKeywords by remember { mutableStateOf<List<String>>(emptyList()) }
    var pickerBlockNewlyInstalled by remember { mutableStateOf(false) }
    var pickerIncludeInBrowserShortform by remember { mutableStateOf(false) }

    fun sendUnifiedRefreshBroadcasts() {
        val actions = listOf(
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ALWAYS_ON_SHIELDS,
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES,
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER,
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER,
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER,
            AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST
        )
        actions.forEach { action ->
            runCatching {
                context.sendBroadcast(Intent(action).setPackage(context.packageName))
            }
        }
    }

    fun reloadUnifiedState() {
        unifiedRules = loader.loadUnifiedBlockRules().toList()
        alwaysOnConfig = loader.loadAlwaysOnShieldsConfig()
        reelsScrolledToday = loader.getReelsScrolledToday()
        hasGlobalPin = authResolver.hasGlobalPin()
    }

    when (currentSubScreen) {
        "dashboard" -> {
            UnifiedBlocksDashboardScreen(
                rules = unifiedRules,
                alwaysOnShieldsConfig = alwaysOnConfig,
                reelsScrolledToday = reelsScrolledToday,
                isPremiumUser = state.isPremiumUser,
                hasGlobalPinOverride = hasGlobalPin,
                onUpdateAlwaysOnShieldsConfig = { updated ->
                    loader.saveAlwaysOnShieldsConfig(updated)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onNavigateToPremium = {
                    val intent = Intent(context, FragmentActivity::class.java).apply {
                        putExtra("feature_type", "premium_features")
                    }
                    context.startActivity(intent)
                },
                onCreateNewBlock = {
                    editingUnifiedRule = null
                    currentSubScreen = "create_edit"
                },
                onEditBlock = { rule ->
                    editingUnifiedRule = rule
                    currentSubScreen = "create_edit"
                },
                onActivateBlockNow = { rule ->
                    loader.setUnifiedBlockRuleStatus(rule.id, BlockRuleStatus.ACTIVE)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onDeactivateManualBlock = { rule ->
                    loader.setUnifiedBlockRuleStatus(rule.id, BlockRuleStatus.INACTIVE_MANUAL)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onPauseBlockForMinutes = { rule, mins ->
                    loader.pauseUnifiedBlockRule(rule.id, mins)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onArchiveBlock = { rule ->
                    loader.setUnifiedBlockRuleStatus(rule.id, BlockRuleStatus.ARCHIVED)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onRestoreArchivedBlock = { rule ->
                    loader.setUnifiedBlockRuleStatus(rule.id, BlockRuleStatus.ACTIVE)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onDuplicateBlock = { rule ->
                    val copyRule = rule.copy(
                        id = UUID.randomUUID().toString(),
                        name = "${rule.name} (Copy)",
                        status = BlockRuleStatus.INACTIVE_MANUAL,
                        pausedUntilElapsedRealtime = 0L,
                        pausedUntilWallClockMillis = 0L,
                        createdAt = System.currentTimeMillis()
                    )
                    loader.upsertUnifiedBlockRule(copyRule)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onDeleteBlock = { rule ->
                    loader.deleteUnifiedBlockRule(rule.id)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                },
                onOpenWeeklyReport = {
                    currentSubScreen = "weekly_report"
                }
            )
        }
        "create_edit" -> {
            CreateOrEditBlockScreen(
                initialRule = editingUnifiedRule,
                existingRulesForCopy = unifiedRules,
                onDraftUpdated = { draft -> editingUnifiedRule = draft },
                onOpenTargetPicker = { apps, domains, keywords, blockNew, browserShortform, _ ->
                    pickerApps = apps
                    pickerDomains = domains
                    pickerKeywords = keywords
                    pickerBlockNewlyInstalled = blockNew
                    pickerIncludeInBrowserShortform = browserShortform
                    currentSubScreen = "select_targets"
                },
                onSave = { savedRule ->
                    loader.upsertUnifiedBlockRule(savedRule)
                    reloadUnifiedState()
                    sendUnifiedRefreshBroadcasts()
                    editingUnifiedRule = null
                    currentSubScreen = "dashboard"
                },
                onClose = {
                    editingUnifiedRule = null
                    currentSubScreen = "dashboard"
                }
            )
        }
        "select_targets" -> {
            SelectTargetsScreen(
                initialAppTargets = pickerApps,
                initialBlockedDomains = pickerDomains,
                initialBlockedKeywords = pickerKeywords,
                initialBlockNewlyInstalled = pickerBlockNewlyInstalled,
                initialIncludeInBrowserShortform = pickerIncludeInBrowserShortform,
                onConfirm = { newApps, newDomains, newKeywords, newBlockNew, newBrowserShortform ->
                    val base = editingUnifiedRule ?: UnifiedBlockRule(name = "")
                    editingUnifiedRule = base.copy(
                        appTargets = newApps,
                        blockedDomains = newDomains,
                        blockedKeywords = newKeywords,
                        blockNewlyInstalledApps = newBlockNew,
                        includeInBrowserShortform = newBrowserShortform
                    )
                    currentSubScreen = "create_edit"
                },
                onBack = {
                    currentSubScreen = "create_edit"
                }
            )
        }
        "weekly_report" -> {
            WeeklyUsageReportScreen(
                savedPreferencesLoader = loader,
                onBack = {
                    currentSubScreen = "dashboard"
                }
            )
        }
    }
}
