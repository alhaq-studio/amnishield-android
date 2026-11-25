/**
 * ============================================================================
 * AmniShield Core Accessibility Engine - AmniShieldAccessibilityService
 * ============================================================================
 * Architecture: Central Accessibility Stream Orchestrator & Blocker Pipeline Host
 * 
 * Description:
 * Receives Android accessibility events, inspects active window trees, and orchestrates
 * the Chain of Responsibility interceptor pipeline (AntiUninstall, AppBlocker, FocusMode,
 * WebsiteBlocker, KeywordBlocker, ReelsBlocker).
 * 
 * Invariants & AI/Developer Guidance:
 * - NODE LIFECYCLE INVARIANT: AmniShieldAccessibilityService exclusively owns the lifecycle
 *   of rootNode (recycled once in the finally block of onAccessibilityEvent).
 * - Interceptors and detectors must NEVER call rootNode.recycle().
 * - Interceptors must only recycle child nodes they generate during internal stack traversals.
 * - STRICT SHORT-CIRCUITING: When a higher-priority interceptor blocks an action, the event
 *   stream must return early to avoid redundant processing.
 * ============================================================================
 */
package com.alhaq.amnishield.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.edit
import com.alhaq.amnishield.Constants
import com.alhaq.amnishield.CrashLogger
import com.alhaq.amnishield.R
import android.app.usage.UsageStatsManager
import android.os.SystemClock
import com.alhaq.amnishield.blockers.AppBlocker
import com.alhaq.amnishield.data.blockers.AlwaysOnShieldsConfig
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppInterceptionLevel
import com.alhaq.amnishield.data.blockers.BlockInterceptionStyle
import com.alhaq.amnishield.data.blockers.BlockRuleStatus
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.data.blockers.LimitPeriod
import com.alhaq.amnishield.data.blockers.ReelsLimitMode
import com.alhaq.amnishield.data.blockers.ShortformInterceptionStyle
import com.alhaq.amnishield.data.blockers.UnifiedBlockRule
import com.alhaq.amnishield.data.blockers.UniversalScheduleRule
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.blockers.KeywordBlocker
import com.alhaq.amnishield.blockers.HomeFeedNavigator
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.blockers.ViewBlocker
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.security.SystemExclusionManager
import com.alhaq.amnishield.trackers.ReelDetectionEngine
import com.alhaq.amnishield.ui.activity.AmniSpaceActivity
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.activity.WarningActivity
import com.alhaq.amnishield.utils.BlockingStatsManager
import com.alhaq.amnishield.utils.NotificationHelper
import com.alhaq.amnishield.utils.ScheduleUtils
import com.alhaq.amnishield.utils.TimeTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class AmniShieldAccessibilityService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_APP_BLOCKER = "amnishield.refresh.appblocker"
        const val INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN = "amnishield.refresh.appblocker.cooldown"
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "amnishield.refresh.focusmode"
        const val INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST = "amnishield.refresh.keywords"
        const val INTENT_ACTION_REFRESH_VIEW_BLOCKER = "amnishield.refresh.viewblocker"
        const val INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN = "amnishield.refresh.viewblocker.cooldown"
        const val INTENT_ACTION_REFRESH_REEL_BLOCKER = "amnishield.refresh.reelblocker"
        const val INTENT_ACTION_REFRESH_REEL_BLOCKER_COOLDOWN = "amnishield.refresh.reelblocker.cooldown"
        const val INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN = "amnishield.refresh.keywordblocker.cooldown"
        const val INTENT_ACTION_REFRESH_WEBSITE_BLOCKER = "amnishield.refresh.websiteblocker"
        const val INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES = "amnishield.refresh.unified.feature.schedules"
        const val INTENT_ACTION_REFRESH_ALWAYS_ON_SHIELDS = "amnishield.refresh.always_on_shields"
        const val INTENT_ACTION_REFRESH_ANTI_UNINSTALL = ".amnishield.refresh.anti_uninstall"
        const val INTENT_ACTION_PASSWORD_VERIFIED = "amnishield.password.verified"
    }

    private lateinit var appBlocker: AppBlocker
    private lateinit var focusModeBlocker: FocusModeBlocker
    private lateinit var keywordBlocker: KeywordBlocker
    private lateinit var reelBlocker: ReelBlocker
    private lateinit var blockingStatsManager: BlockingStatsManager
    private lateinit var premiumManager: PremiumManager
    private lateinit var crashLogger: CrashLogger
    private val homeFeedNavigator = HomeFeedNavigator()

    private var appBlockerWarningConfig = MainActivity.WarningData()
    private var keywordBlockerWarningConfig = MainActivity.WarningData()

    private lateinit var antiUninstallDetector: com.alhaq.amnishield.security.AntiUninstallDetector
    private lateinit var reelsSessionTracker: com.alhaq.amnishield.trackers.ReelsSessionTracker
    private lateinit var browserSessionTracker: com.alhaq.amnishield.trackers.BrowserSessionTracker
    private lateinit var serviceBroadcastManager: ServiceBroadcastManager
    private lateinit var keywordActionHandler: com.alhaq.amnishield.blockers.KeywordActionHandler
    private lateinit var reelActionHandler: com.alhaq.amnishield.blockers.ReelActionHandler
    private lateinit var websiteBlockerDetector: com.alhaq.amnishield.blockers.WebsiteBlockerDetector
    private lateinit var domainBlocklistEngine: com.alhaq.amnishield.blockers.DomainBlocklistEngine

    @Volatile
    private var cachedDefaultLauncher: String? = null
    private var lastTrackedLaunchPackage: String? = null
    private val lastLauncherRefreshElapsedMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val isRefreshingLauncher = java.util.concurrent.atomic.AtomicBoolean(false)
    private val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    private var unifiedRulesSnapshot: List<UnifiedBlockRule> = emptyList()
    @Volatile
    private var alwaysOnShieldsSnapshot: AlwaysOnShieldsConfig = AlwaysOnShieldsConfig()
    @Volatile
    private var legacyScheduleRulesSnapshot: List<AppBlockScheduleRule> = emptyList()
    @Volatile
    private var cachedBlockedWebsites: Set<String> = emptySet()
    @Volatile
    private var cachedBlockedSocialApps: Set<String> = emptySet()
    @Volatile
    private var cachedBlockedKeywords: Set<String> = emptySet()
    @Volatile
    private var isWebsiteBlockerMasterEnabled: Boolean = false
    @Volatile
    private var cachedUniversalDailyLaunches: Map<String, Int> = emptyMap()
    @Volatile
    private var cachedUniversalHourlyLaunches: Map<String, Int> = emptyMap()
    @Volatile
    private var cachedDailyUsageMinutesMap: Map<String, Int> = emptyMap()
    @Volatile
    private var cachedHourlyUsageMinutesMap: Map<String, Int> = emptyMap()
    @Volatile
    private var lastUsageCacheRefreshElapsedMs: Long = 0L
    @Volatile
    private var lastAppliedKeywordSignature: Int = 0
    @Volatile
    private var lastSilentCleanResultId: String = ""
    @Volatile
    private var lastSilentCleanElapsedMs: Long = 0L
    private val isRefreshingUsageStats = java.util.concurrent.atomic.AtomicBoolean(false)

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED) { droppedEvent ->
        try {
            @Suppress("DEPRECATION")
            droppedEvent.recycle()
        } catch (_: Throwable) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            appBlocker = AppBlocker()
            focusModeBlocker = FocusModeBlocker()
            keywordBlocker = KeywordBlocker(this)
            reelBlocker = ReelBlocker()
            blockingStatsManager = BlockingStatsManager.getInstance(this)
            premiumManager = PremiumManager.getInstance(this)
            crashLogger = CrashLogger(this)

            antiUninstallDetector = com.alhaq.amnishield.security.AntiUninstallDetector(this)
            keywordActionHandler = com.alhaq.amnishield.blockers.KeywordActionHandler(
                context = this,
                savedPreferencesLoader = savedPreferencesLoader,
                keywordBlocker = keywordBlocker
            )
            reelActionHandler = com.alhaq.amnishield.blockers.ReelActionHandler(
                context = this,
                savedPreferencesLoader = savedPreferencesLoader,
                homeFeedNavigator = homeFeedNavigator
            )
            domainBlocklistEngine = com.alhaq.amnishield.blockers.DomainBlocklistEngine()
            websiteBlockerDetector = com.alhaq.amnishield.blockers.WebsiteBlockerDetector(
                savedPreferencesLoader = savedPreferencesLoader,
                domainBlocklistEngine = domainBlocklistEngine
            )

            runCatching { savedPreferencesLoader.migrateLegacySchedulesIfNeeded() }
                .onFailure { Log.e("AmniShieldService", "Failed schedule migration", it) }
            runCatching { setupUnifiedBlockRules() }
                .onFailure { Log.e("AmniShieldService", "Failed setupUnifiedBlockRules", it) }
            runCatching { setupAlwaysOnShieldsSync() }
                .onFailure { Log.e("AmniShieldService", "Failed setupAlwaysOnShieldsSync", it) }
            serviceScope.launch(Dispatchers.IO) {
                runCatching { reloadBundledDomainBlocklists() }
                    .onFailure { Log.e("AmniShieldService", "Failed initial DomainBlocklistEngine load", it) }
            }
            runCatching { setupAppBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupAppBlocker", it) }
            runCatching { setupWebsiteBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupWebsiteBlocker", it) }
            runCatching { setupFocusMode() }
                .onFailure { Log.e("AmniShieldService", "Failed setupFocusMode", it) }
            runCatching { setupKeywordBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupKeywordBlocker", it) }
            runCatching { setupReelBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupReelBlocker", it) }
            refreshDefaultLauncherAsync(force = true)
            runCatching { syncActiveProtectionNotification() }

            reelsSessionTracker = com.alhaq.amnishield.trackers.ReelsSessionTracker(
                service = this,
                savedPreferencesLoader = savedPreferencesLoader,
                reelBlocker = reelBlocker,
                crashLogger = crashLogger,
                isFeatureActive = { isFeatureCurrentlyActive(it) || alwaysOnShieldsSnapshot.reels.enabled }
            )
            reelsSessionTracker.start()

            browserSessionTracker = com.alhaq.amnishield.trackers.BrowserSessionTracker(
                service = this,
                savedPreferencesLoader = savedPreferencesLoader,
                websiteBlockerDetector = websiteBlockerDetector,
                crashLogger = crashLogger
            )
            browserSessionTracker.start()

            serviceBroadcastManager = ServiceBroadcastManager(
                context = this,
                callbacks = object : ServiceBroadcastManager.Callbacks {
                    override fun onRefreshAppBlocker() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { setupAppBlocker() }
                                .onFailure { Log.e("AmniShieldService", "Failed setupAppBlocker", it) }
                        }
                    }
                    override fun onRefreshWebsiteBlocker() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                setupAlwaysOnShieldsSync()
                                setupWebsiteBlocker()
                                reloadBundledDomainBlocklists()
                            }.onFailure { Log.e("AmniShieldService", "Failed setupWebsiteBlocker", it) }
                        }
                    }
                    override fun onRefreshBlockedKeywordList() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { setupKeywordBlocker() }
                                .onFailure { Log.e("AmniShieldService", "Failed setupKeywordBlocker", it) }
                        }
                    }
                    override fun onRefreshViewBlocker() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { setupReelBlocker() }
                                .onFailure { Log.e("AmniShieldService", "Failed setupReelBlocker", it) }
                        }
                    }
                    override fun onRefreshViewBlockerCooldown(resultId: String?, interval: Int) {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                val warningConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
                                val duration = if (interval > 0) interval else warningConfig.timeInterval
                                val endTime = System.currentTimeMillis() + duration
                                reelBlocker.applyCooldown(resultId ?: "xxxxxxxxxxxxxx", endTime)
                                savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
                                resultId?.let { websiteBlockerDetector.applyCooldown(it.trim().lowercase(Locale.ROOT), endTime) }
                            }.onFailure { Log.e("AmniShieldService", "Failed onRefreshViewBlockerCooldown", it) }
                        }
                    }
                    override fun onRefreshReelBlocker() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { setupReelBlocker() }
                                .onFailure { Log.e("AmniShieldService", "Failed setupReelBlocker", it) }
                        }
                    }
                    override fun onRefreshReelBlockerCooldown(resultId: String?, interval: Int) {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                val warningConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
                                val duration = if (interval > 0) interval else warningConfig.timeInterval
                                val endTime = System.currentTimeMillis() + duration
                                reelBlocker.applyCooldown(resultId ?: "xxxxxxxxxxxxxx", endTime)
                                savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
                            }.onFailure { Log.e("AmniShieldService", "Failed onRefreshReelBlockerCooldown", it) }
                        }
                    }
                    override fun onRefreshKeywordBlockerCooldown(resultId: String?, interval: Int) {
                        val duration = if (interval > 0) interval else keywordBlockerWarningConfig.timeInterval
                        val endTime = System.currentTimeMillis() + duration
                        resultId?.let { keywordBlocker.applyCooldown(it, endTime) }
                    }
                    override fun onRefreshUnifiedFeatureSchedules() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                setupUnifiedBlockRules()
                                setupAlwaysOnShieldsSync()
                                setupAppBlocker()
                                setupWebsiteBlocker()
                                reloadBundledDomainBlocklists()
                                setupReelBlocker()
                                setupKeywordBlocker()
                                setupFocusMode()
                                syncActiveProtectionNotification()
                            }.onFailure { Log.e("AmniShieldService", "Failed unified refresh", it) }
                        }
                    }
                    override fun onRefreshAlwaysOnShields() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                val loaded = savedPreferencesLoader.loadAlwaysOnShieldsConfig()
                                alwaysOnShieldsSnapshot = loaded
                                setupWebsiteBlocker()
                                reloadBundledDomainBlocklists()
                                setupReelBlocker()
                                setupKeywordBlocker()
                                syncActiveProtectionNotification()
                            }.onFailure { Log.e("AmniShieldService", "Failed Always-On Shields refresh", it) }
                        }
                    }
                    override fun onRefreshAppBlockerCooldown(resultId: String?, interval: Int) {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching {
                                val duration = if (interval > 0) interval else appBlockerWarningConfig.timeInterval
                                val endTime = System.currentTimeMillis() + duration
                                appBlocker.putCooldownTo(resultId ?: "xxxxxxxxxxxxxx", endTime)
                                savedPreferencesLoader.saveAppBlockerCooldownData(appBlocker.getCooldownSnapshot())
                            }.onFailure { Log.e("AmniShieldService", "Failed onRefreshAppBlockerCooldown", it) }
                        }
                    }
                    override fun onRefreshFocusMode() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { setupFocusMode() }
                                .onFailure { Log.e("AmniShieldService", "Failed setupFocusMode", it) }
                        }
                    }
                    override fun onRefreshAntiUninstall() {
                        serviceScope.launch(Dispatchers.IO) {
                            runCatching { antiUninstallDetector.reloadConfig() }
                                .onFailure { Log.e("AmniShieldService", "Failed reloadConfig", it) }
                        }
                    }
                    override fun onPasswordVerified() = antiUninstallDetector.onPasswordVerified()
                }
            )
            serviceBroadcastManager.register()

            startBackgroundWorker()
        } catch (e: Throwable) {
            android.util.Log.e("AmniShieldService", "Critical failure during onServiceConnected initialization", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        var rootNode: AccessibilityNodeInfo? = null
        try {
            val myPackageName = this.packageName
            if (packageName.equals(myPackageName, ignoreCase = true) ||
                packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
                packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
                packageName.startsWith("com.alhaq.deenshield.", ignoreCase = true)
            ) {
                return
            }

            // Notification suppression via Accessibility shade/event interception
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                val suppressingRule = findNotificationSuppressingRule(packageName)
                if (suppressingRule != null) {
                    blockingStatsManager.recordAppBlock(
                        packageName,
                        "Notification suppressed (${suppressingRule.name})"
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                    }
                    return
                }
            }

            if (packageName.equals("com.android.systemui", ignoreCase = true)) {
                return
            }



            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val isSystemOrSelf = packageName.equals("com.android.systemui", ignoreCase = true) ||
                        packageName.equals("android", ignoreCase = true) ||
                        packageName.equals(this.packageName, ignoreCase = true) ||
                        packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
                        packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
                        packageName.startsWith("com.alhaq.deenshield.", ignoreCase = true)
                
                if (!isSystemOrSelf && packageName != lastTrackedLaunchPackage) {
                    lastTrackedLaunchPackage = packageName
                    val launcher = cachedDefaultLauncher
                    if (packageName != launcher) {
                        trackAppLaunch(packageName)
                    }
                }
                refreshDefaultLauncherAsync(force = false)
            }

            rootNode = rootInActiveWindow ?: runCatching { event.source }.getOrNull()
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                (rootNode != null && (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED))
            ) {
                reelsSessionTracker.onAccessibilityEvent(event, rootNode)
                browserSessionTracker.onAccessibilityEvent(event, rootNode)
            }

            val rootPackage = rootNode?.packageName?.toString() ?: packageName

            // 1. Authoritative safety fast-path: Never block emergency services, dialers, keyboards, or launcher
            if (SystemExclusionManager.isExempt(rootPackage, this, savedPreferencesLoader = savedPreferencesLoader, cachedDefaultLauncher = cachedDefaultLauncher)) {
                // If it is settings, package installer, or app store, allow AntiUninstallDetector to inspect sensitive sub-pages (if anti-uninstall enabled)
                if (SystemExclusionManager.isSettingsApp(rootPackage) || SystemExclusionManager.isPackageManagementApp(rootPackage)) {
                    antiUninstallDetector.inspect(event, rootNode)
                }
                return
            }

            if (rootPackage.equals(myPackageName, ignoreCase = true) ||
                rootPackage.equals("com.alhaq.amnishield", ignoreCase = true) ||
                rootPackage.equals("com.alhaq.deenshield", ignoreCase = true) ||
                rootPackage.startsWith("com.alhaq.deenshield.", ignoreCase = true) ||
                rootPackage.equals("com.android.systemui", ignoreCase = true)
            ) {
                return
            }

            if (antiUninstallDetector.inspect(event, rootNode)) {
                return
            }

            val isPremiumUser = premiumManager.isPremium()

            val isManualFocusActive = focusModeBlocker.focusModeData.isTurnedOn
            val activeFocusRule = if (!isManualFocusActive) getActiveFocusModeRule() else null
            val isAutoFocusScheduleActive = activeFocusRule != null || isFeatureCurrentlyActive("FOCUS_MODE") || isFeatureCurrentlyActive("focus_mode")
            val isFocusModeActive = isManualFocusActive || isAutoFocusScheduleActive
            val activeFocusModeType = if (isManualFocusActive) {
                focusModeBlocker.focusModeData.modeType
            } else if (activeFocusRule != null) {
                activeFocusRule.focusProtectionMode
            } else if (isAutoFocusScheduleActive) {
                savedPreferencesLoader.getFocusModeData().modeType
            } else {
                -1
            }
            val isFocusBlockAllExSelectedActive = isFocusModeActive && activeFocusModeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED

            if (isFocusModeActive) {
                val focusModeResult = focusModeBlocker.doesAppNeedToBeBlocked(
                    this,
                    rootPackage,
                    savedPreferencesLoader,
                    cachedDefaultLauncher,
                    isScheduleActive = isAutoFocusScheduleActive,
                    activeScheduleRule = activeFocusRule
                )
                if (focusModeResult.isRequestingToUpdateSPData) {
                    savedPreferencesLoader.completeFocusSession()
                    savedPreferencesLoader.saveFocusModeData(focusModeBlocker.focusModeData)
                }

                if (focusModeResult.isBlocked) {
                    blockingStatsManager.recordAppBlock(rootPackage, "Blocked by Focus Mode")
                    val focusStyle = savedPreferencesLoader.getFocusModeInterceptionStyle()
                    when (focusStyle) {
                        Constants.FOCUS_INTERCEPTION_STYLE_WORKSPACE -> {
                            val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_FOCUS_LAUNCHER)
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Focus Session Active")
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, rootPackage)
                            }
                            startActivity(intent)
                            return
                        }
                        Constants.FOCUS_INTERCEPTION_STYLE_SILENT -> {
                            pressHome()
                            return
                        }
                        else -> { // Constants.FOCUS_INTERCEPTION_STYLE_DIALOG (Default)
                            val intent = Intent(this, WarningActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("mode", Constants.WARNING_SCREEN_MODE_FOCUS_MODE)
                                putExtra("result_id", rootPackage)
                                putExtra("blocked_by_feature", "Focus Mode")
                            }
                            startActivity(intent)
                            return
                        }
                    }
                }
            }

            val alwaysOnSnapshot = alwaysOnShieldsSnapshot
            val websiteGuard = alwaysOnSnapshot.website

            // Always-On Website Guard: Unsupported Browser & Incognito Mode protection
            if (websiteGuard.enabled) {
                if (websiteGuard.blockUnsupportedBrowsers && websiteBlockerDetector.isUnsupportedBrowserPackage(rootPackage)) {
                    val attribution = "Blocked by Always-On Website Guard"
                    blockingStatsManager.recordAppBlock(rootPackage, "$attribution (Unsupported Browser)")
                    NotificationHelper.getInstance(this).showBlockingAlert("Always-On Website Guard", "Unsupported browser ($rootPackage)")
                    dispatchAlwaysOnInterception(
                        style = websiteGuard.interceptionStyle,
                        mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                        resultId = rootPackage,
                        attributionLabel = attribution,
                        triggerReason = "$attribution: Unsupported Browser"
                    )
                    return
                }
                if (websiteGuard.blockIncognitoMode && rootNode != null && websiteBlockerDetector.isIncognitoOrPrivateTab(rootNode, rootPackage)) {
                    val attribution = "Blocked by Always-On Website Guard"
                    blockingStatsManager.recordAppBlock(rootPackage, "$attribution (Incognito Tab)")
                    NotificationHelper.getInstance(this).showBlockingAlert("Always-On Website Guard", "Incognito tab in $rootPackage")
                    dispatchAlwaysOnInterception(
                        style = websiteGuard.interceptionStyle,
                        mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                        resultId = rootPackage,
                        attributionLabel = attribution,
                        triggerReason = "$attribution: Private / Incognito Mode"
                    )
                    return
                }
            }

            val activeWebsites = getActiveBlockedWebsites()
            val hasEngineDomains = websiteGuard.enabled && ::domainBlocklistEngine.isInitialized && domainBlocklistEngine.domainCount() > 0
            val hasUnifiedOrAlwaysOnWebsites = activeWebsites.isNotEmpty() || hasEngineDomains
            if ((isWebsiteBlockerMasterEnabled && isFeatureCurrentlyActive("website_blocker")) || hasUnifiedOrAlwaysOnWebsites) {
                if (activeWebsites.isNotEmpty() || hasEngineDomains) {
                    val blockedSocialApps = cachedBlockedSocialApps
                    if (blockedSocialApps.contains(rootPackage)) {
                        val fallbackWebAttribution = if (websiteGuard.enabled) "Blocked by Always-On Website Guard" else "Website Blocker"
                        blockingStatsManager.recordAppBlock(rootPackage, fallbackWebAttribution)
                        val webStyle = savedPreferencesLoader.getWebsiteBlockerWarningStyle()
                        if (webStyle == Constants.BLOCKER_WARNING_STYLE_AMNISPACE) {
                            val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, fallbackWebAttribution)
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, rootPackage)
                                putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 5)
                            }
                            startActivity(intent)
                            return
                        } else if (webStyle == Constants.BLOCKER_WARNING_STYLE_SILENT) {
                            pressHome()
                            return
                        } else {
                            val intent = Intent(this, WarningActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
                                putExtra("result_id", rootPackage)
                                putExtra("blocked_by_feature", fallbackWebAttribution)
                            }
                            startActivity(intent)
                            return
                        }
                    } else if (rootNode != null) {
                        val blockedSite = websiteBlockerDetector.findBlockedWebsite(rootNode, rootPackage, activeWebsites)
                        if (blockedSite != null) {
                            val matchingUnifiedWebRule = findUnifiedRuleForBlockedDomain(blockedSite)
                            if (matchingUnifiedWebRule != null) {
                                val attribution = "Blocked by Custom Rule: ${matchingUnifiedWebRule.name}"
                                blockingStatsManager.recordAppBlock(rootPackage, "Website Blocked: $blockedSite ($attribution)")
                                NotificationHelper.getInstance(this).showBlockingAlert("Custom Rule: ${matchingUnifiedWebRule.name}", blockedSite)
                                dispatchUnifiedRuleInterception(
                                    rule = matchingUnifiedWebRule,
                                    mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                                    resultId = blockedSite,
                                    triggerReason = "$attribution ($blockedSite)"
                                )
                                return
                            }
                            if (websiteGuard.enabled) {
                                val attribution = "Blocked by Always-On Website Guard"
                                blockingStatsManager.recordAppBlock(rootPackage, "Website Blocked: $blockedSite ($attribution)")
                                NotificationHelper.getInstance(this).showBlockingAlert("Always-On Website Guard", blockedSite)
                                dispatchAlwaysOnInterception(
                                    style = websiteGuard.interceptionStyle,
                                    mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                                    resultId = blockedSite,
                                    attributionLabel = attribution,
                                    triggerReason = "$attribution ($blockedSite)"
                                )
                                return
                            }
                            val webStyle = savedPreferencesLoader.getWebsiteBlockerWarningStyle()
                            when (webStyle) {
                                Constants.BLOCKER_WARNING_STYLE_AMNISPACE -> {
                                    val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                                        putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Restricted Web Domain: $blockedSite")
                                        putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, blockedSite)
                                        putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 5)
                                    }
                                    startActivity(intent)
                                    return
                                }
                                Constants.BLOCKER_WARNING_STYLE_DIALOG -> {
                                    val intent = Intent(this, WarningActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        putExtra("mode", Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER)
                                        putExtra("result_id", blockedSite)
                                        putExtra("blocked_by_feature", "Website Blocker")
                                    }
                                    startActivity(intent)
                                    return
                                }
                                else -> { // Constants.BLOCKER_WARNING_STYLE_SILENT (Default)
                                    pressHome()
                                    return
                                }
                            }
                        }
                    }
                }
            }

            if (!isFocusBlockAllExSelectedActive) {
                // Evaluate UnifiedBlockRules first (zero main-thread JSON deserialization)
                val matchedUnifiedRule = shouldBlockPackageByUnifiedRules(rootPackage)
                if (matchedUnifiedRule != null && !appBlocker.isUnderCooldown(rootPackage)) {
                    val attribution = "Blocked by Custom Rule: ${matchedUnifiedRule.name}"
                    blockingStatsManager.recordAppBlock(rootPackage, attribution)
                    NotificationHelper.getInstance(this).showBlockingAlert("Custom Rule: ${matchedUnifiedRule.name}", resolveAppLabel(rootPackage))
                    dispatchUnifiedRuleInterception(
                        rule = matchedUnifiedRule,
                        mode = Constants.WARNING_SCREEN_MODE_APP_BLOCKER,
                        resultId = rootPackage,
                        triggerReason = attribution
                    )
                    return
                }

                if (savedPreferencesLoader.isAppBlockerFeatureEnabled(false) && isFeatureCurrentlyActive("app_blocker")) {
                    val appBlockerResult = appBlocker.doesAppNeedToBeBlocked(rootPackage, savedPreferencesLoader, this)
                    if (appBlockerResult.isBlocked) {
                        blockingStatsManager.recordAppBlock(rootPackage, "Blocked by App Blocker")
                        val appStyle = savedPreferencesLoader.getAppBlockerWarningStyle()
                        if (appStyle == Constants.BLOCKER_WARNING_STYLE_AMNISPACE) {
                            val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, "Restricted by App Blocker")
                                putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, rootPackage)
                                putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 5)
                            }
                            startActivity(intent)
                            return
                        } else if (appStyle == Constants.BLOCKER_WARNING_STYLE_SILENT) {
                            pressHome()
                            return
                        } else {
                            val intent = Intent(this, WarningActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
                                putExtra("result_id", rootPackage)
                                putExtra("blocked_by_feature", "App Blocker")
                            }
                            startActivity(intent)
                            return
                        }
                    }
                }
            }

            val eventCopy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AccessibilityEvent(event)
            } else {
                @Suppress("DEPRECATION")
                AccessibilityEvent.obtain(event)
            }
            val sendResult = eventChannel.trySend(eventCopy)
            if (sendResult.isFailure) {
                try {
                    @Suppress("DEPRECATION")
                    eventCopy.recycle()
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            android.util.Log.e("AmniShield", "Accessibility pipeline error", t)
            crashLogger.logNonFatalError("AccessibilityService", "Pipeline error", Exception(t))
        } finally {
            rootNode?.recycle()
        }
    }

    private fun startBackgroundWorker() {
        serviceScope.launch {
            for (event in eventChannel) {
                try {
                    processDeferredChecks(event)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    android.util.Log.e("AmniShield", "Deferred blocker worker error", t)
                    crashLogger.logNonFatalError("AccessibilityService", "Deferred blocker error", Exception(t))
                } finally {
                    try {
                        @Suppress("DEPRECATION")
                        event.recycle()
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    private fun processDeferredChecks(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val rootNode = rootInActiveWindow ?: return
        try {
            val rootPackage = rootNode.packageName?.toString() ?: return
            val isImeEvent = packageName.contains("inputmethod", ignoreCase = true) ||
                packageName.contains("honeyboard", ignoreCase = true) ||
                packageName.contains("keyboard", ignoreCase = true) ||
                packageName.contains("swiftkey", ignoreCase = true) ||
                packageName.contains("gboard", ignoreCase = true)
            // Temporal Window Guard: abort if user already switched away from the event's package
            // Allow soft keyboard / IME typing events to inspect the foreground app's root window.
            if (!isImeEvent && !rootPackage.equals(packageName, ignoreCase = true)) {
                return
            }

            val myPackageName = this.packageName
            if (packageName.equals(myPackageName, ignoreCase = true) ||
                packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
                packageName.equals("com.alhaq.deenshield", ignoreCase = true) ||
                packageName.startsWith("com.alhaq.deenshield.", ignoreCase = true) ||
                rootPackage.equals(myPackageName, ignoreCase = true) ||
                rootPackage.equals("com.alhaq.amnishield", ignoreCase = true) ||
                rootPackage.equals("com.alhaq.deenshield", ignoreCase = true) ||
                rootPackage.startsWith("com.alhaq.deenshield.", ignoreCase = true) ||
                packageName.equals("com.android.systemui", ignoreCase = true) ||
                rootPackage.equals("com.android.systemui", ignoreCase = true)
            ) {
                return
            }

            if (SystemExclusionManager.isExempt(rootPackage, this, savedPreferencesLoader = savedPreferencesLoader, cachedDefaultLauncher = cachedDefaultLauncher)) {
                return
            }

            val alwaysOnSnapshot = alwaysOnShieldsSnapshot
            val keywordGuard = alwaysOnSnapshot.keyword
            val activeKeywords = getActiveBlockedKeywords()
            val hasActiveKeywords = activeKeywords.isNotEmpty()
            val isKeywordEnabled = savedPreferencesLoader.isKeywordBlockerFeatureEnabled(false)
            if (hasActiveKeywords && (keywordGuard.enabled || isKeywordEnabled || isFeatureCurrentlyActive("keyword_blocker"))) {
                if (keywordGuard.enabled) {
                    keywordBlocker.wholeWordOnly = keywordGuard.wholeWordOnly
                    keywordBlocker.isSearchAllTextFields = keywordGuard.scanAllTextFields
                }
                val kwSig = 31 * (31 * activeKeywords.hashCode() + keywordBlocker.wholeWordOnly.hashCode()) +
                    keywordBlocker.isSearchAllTextFields.hashCode()
                if (kwSig != lastAppliedKeywordSignature || keywordBlocker.blockedKeyword.size != activeKeywords.size) {
                    keywordBlocker.blockedKeyword = HashSet(activeKeywords)
                    keywordBlocker.resetDetectionCache()
                    lastAppliedKeywordSignature = kwSig
                }
                try {
                    val keywordResult = keywordBlocker.checkIfUserGettingFreaky(rootNode, event)
                    val detectedWord = keywordResult.resultDetectWord
                    if (detectedWord != null) {
                        blockingStatsManager.recordKeywordBlock(detectedWord, rootPackage)
                        val matchingUnifiedKwRule = findUnifiedRuleForBlockedKeyword(detectedWord)
                        if (matchingUnifiedKwRule != null) {
                            val attribution = "Blocked by Custom Rule: ${matchingUnifiedKwRule.name}"
                            NotificationHelper.getInstance(this).showBlockingAlert("Custom Rule: ${matchingUnifiedKwRule.name}", detectedWord)
                            runCatching { keywordBlocker.clearOffendingText(rootNode, event) }
                            dispatchUnifiedRuleInterception(
                                rule = matchingUnifiedKwRule,
                                mode = Constants.WARNING_SCREEN_MODE_KEYWORD_BLOCKER,
                                resultId = detectedWord,
                                triggerReason = "$attribution ($detectedWord)",
                                isPressHome = keywordResult.isHomePressRequested
                            )
                            return
                        }
                        val attribution = if (keywordGuard.enabled) "Blocked by Always-On Keyword Filter" else "Keyword Blocker"
                        NotificationHelper.getInstance(this).showBlockingAlert(
                            if (keywordGuard.enabled) "Always-On Keyword Filter" else "Keyword Blocker",
                            detectedWord
                        )
                        val feedbackOverride = if (keywordGuard.enabled) {
                            when (keywordGuard.interceptionStyle) {
                                BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE -> Constants.KEYWORD_FEEDBACK_AMNISPACE
                                BlockInterceptionStyle.SILENT_CLEAN,
                                BlockInterceptionStyle.INSTANT_HOME_OR_BACK -> Constants.KEYWORD_FEEDBACK_SILENT
                                BlockInterceptionStyle.STANDARD_WARNING_DIALOG -> Constants.KEYWORD_FEEDBACK_WARNING_SCREEN
                            }
                        } else null
                        keywordActionHandler.handleKeywordBlock(
                            result = keywordResult,
                            packageName = rootPackage,
                            rootNode = rootNode,
                            event = event,
                            onPressHome = { pressHome() },
                            attributionLabel = attribution,
                            feedbackModeOverride = feedbackOverride
                        )
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AmniShield", "Core Keyword blocker error", e)
                }
            }

            val reelsGuard = alwaysOnSnapshot.reels
            val matchedShortformRule = findShortformBlockingUnifiedRule(rootPackage)
            val isBrowserShortformByUnified = isInBrowserShortformEnabledByUnifiedRules()
            val isTargetedShortformByUnified = matchedShortformRule != null || isBrowserShortformByUnified
            val isReelsEnabled = isTargetedShortformByUnified ||
                reelsGuard.enabled ||
                ((reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)) && isFeatureCurrentlyActive("reel_blocker"))
            if (isReelsEnabled) {
                reelBlocker.isEnabled = true
                val hasConfiguredPlatforms = savedPreferencesLoader.hasAnyReelPlatformConfigured()
                val fallbackPlatformEnabled = !hasConfiguredPlatforms
                reelBlocker.isYoutubeEnabled = (matchedShortformRule != null) ||
                    (reelsGuard.enabled && reelsGuard.blockYoutubeShorts) ||
                    (!reelsGuard.enabled && savedPreferencesLoader.isReelBlockerYoutubeEnabled(fallbackPlatformEnabled))
                reelBlocker.isInstagramEnabled = (matchedShortformRule != null) ||
                    (reelsGuard.enabled && reelsGuard.blockInstagramReels) ||
                    (!reelsGuard.enabled && savedPreferencesLoader.isReelBlockerInstagramEnabled(fallbackPlatformEnabled))
                reelBlocker.isTiktokEnabled = (matchedShortformRule != null) ||
                    (reelsGuard.enabled && reelsGuard.blockTikTok) ||
                    (!reelsGuard.enabled && savedPreferencesLoader.isReelBlockerTiktokEnabled(fallbackPlatformEnabled))
                reelBlocker.isBrowserShortsEnabled = isBrowserShortformByUnified ||
                    (reelsGuard.enabled && reelsGuard.blockInBrowserShorts) ||
                    (!reelsGuard.enabled && savedPreferencesLoader.isReelBlockerBrowserEnabled())

                // Safeguard #1: Wire ReelsLimitMode.LIMIT_BY_COUNT directly to existing ReelBlocker counter
                if (matchedShortformRule != null || isBrowserShortformByUnified) {
                    reelBlocker.modeType = ReelBlocker.MODE_BLOCK_ALL
                } else if (reelsGuard.enabled) {
                    reelBlocker.modeType = when (reelsGuard.limitMode) {
                        ReelsLimitMode.ALWAYS_BLOCK_24_7 -> ReelBlocker.MODE_BLOCK_ALL
                        ReelsLimitMode.LIMIT_BY_COUNT -> ReelBlocker.MODE_BLOCK_AFTER_DAILY_COUNT
                    }
                    reelBlocker.dailyReelLimit = reelsGuard.dailyReelLimit
                } else {
                    reelBlocker.modeType = savedPreferencesLoader.getReelBlockerMode()
                    reelBlocker.dailyReelLimit = savedPreferencesLoader.getReelBlockerDailyLimit()
                }
                reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()

                try {
                    val rawResult = reelBlocker.doesReelNeedToBeBlocked(rootNode, rootPackage)
                    if (rawResult != null && rawResult.isBlocked) {
                        blockingStatsManager.recordViewBlock(rootPackage, rawResult.viewId)
                        val activeRule = matchedShortformRule ?: findBrowserShortformUnifiedRule()

                        fun styleRank(style: ShortformInterceptionStyle): Int = when (style) {
                            ShortformInterceptionStyle.EXIT_TO_HOME -> 3
                            ShortformInterceptionStyle.WARNING_SCREEN -> 2
                            ShortformInterceptionStyle.MINDFUL_BREATHING_PAUSE -> 1
                            ShortformInterceptionStyle.REDIRECT_TO_FEED -> 0
                        }

                        val reelsGuardWinsStrictness = reelsGuard.enabled && (
                            activeRule == null ||
                                styleRank(reelsGuard.interceptionStyle) > styleRank(activeRule.shortformInterceptionStyle)
                            )
                        val chosenStyle = when {
                            reelsGuardWinsStrictness -> reelsGuard.interceptionStyle
                            activeRule != null -> activeRule.shortformInterceptionStyle
                            else -> null
                        }
                        val attribution = when {
                            reelsGuardWinsStrictness -> "Blocked by Always-On Reels Guard"
                            activeRule != null -> "Blocked by Custom Rule: ${activeRule.name}"
                            else -> "Reels Blocker"
                        }
                        val alertHeader = when {
                            reelsGuardWinsStrictness -> "Always-On Reels Guard"
                            activeRule != null -> "Custom Rule: ${activeRule.name}"
                            else -> "Reels Blocker"
                        }
                        NotificationHelper.getInstance(this).showBlockingAlert(alertHeader, resolveAppLabel(rootPackage))

                        val mappedResponseMode = if (chosenStyle != null) {
                            when (chosenStyle) {
                                ShortformInterceptionStyle.REDIRECT_TO_FEED -> ReelBlocker.BlockResponseMode.HOME_FEED_REDIRECT
                                ShortformInterceptionStyle.EXIT_TO_HOME -> ReelBlocker.BlockResponseMode.ANDROID_HOME
                                ShortformInterceptionStyle.WARNING_SCREEN -> ReelBlocker.BlockResponseMode.HARD_BLOCK
                                ShortformInterceptionStyle.MINDFUL_BREATHING_PAUSE -> ReelBlocker.BlockResponseMode.MINDFUL_PAUSE
                            }
                        } else {
                            savedPreferencesLoader.getReelBlockerBlockResponseMode()
                        }
                        if (!reelsGuardWinsStrictness && activeRule != null && chosenStyle == ShortformInterceptionStyle.WARNING_SCREEN) {
                            pressBack()
                            dispatchUnifiedRuleInterception(
                                rule = activeRule,
                                mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                                resultId = rawResult.viewId,
                                triggerReason = attribution,
                                isReelBlocker = true,
                                styleOverride = BlockInterceptionStyle.STANDARD_WARNING_DIALOG
                            )
                            return
                        }
                        if (!reelsGuardWinsStrictness && activeRule != null && chosenStyle == ShortformInterceptionStyle.MINDFUL_BREATHING_PAUSE) {
                            dispatchUnifiedRuleInterception(
                                rule = activeRule,
                                mode = Constants.WARNING_SCREEN_MODE_VIEW_BLOCKER,
                                resultId = rawResult.viewId,
                                triggerReason = attribution,
                                isReelBlocker = true,
                                styleOverride = BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE
                            )
                            return
                        }
                        val reelBlockerResult = rawResult.copy(
                            blockResponseMode = mappedResponseMode
                        )
                        reelActionHandler.handleReelBlock(
                            result = reelBlockerResult,
                            rootNode = rootNode,
                            packageName = rootPackage,
                            onPressHome = { pressHome() },
                            onPressBack = { pressBack() },
                            attributionLabel = attribution
                        )
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AmniShield", "Reel blocker error", e)
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun setupUnifiedBlockRules() {
        unifiedRulesSnapshot = savedPreferencesLoader.loadUnifiedBlockRules()
        legacyScheduleRulesSnapshot = savedPreferencesLoader.loadAppBlockerScheduleRules()
        refreshUsageStatsCacheIfNeeded(force = true)
    }

    private fun setupAlwaysOnShieldsSync() {
        alwaysOnShieldsSnapshot = savedPreferencesLoader.loadAlwaysOnShieldsConfig()
    }

    private fun syncActiveProtectionNotification() {
        val alwaysOn = alwaysOnShieldsSnapshot
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val activeRulesCount = unifiedRulesSnapshot.count {
            it.isEffectivelyActive(nowElapsed, nowWall)
        }
        NotificationHelper.getInstance(this).updateActiveProtectionStatusNotification(
            activeAlwaysOnNames = alwaysOn.activeShieldTypes().map { it.pillLabel },
            activeCustomRuleCount = activeRulesCount
        )
    }

    private fun reloadBundledDomainBlocklists() {
        if (!::domainBlocklistEngine.isInitialized) return
        val websiteGuard = alwaysOnShieldsSnapshot.website
        val packIds = if (websiteGuard.enabled) websiteGuard.enabledPackIds else emptySet()
        domainBlocklistEngine.loadPacksFromAssets(this, packIds)
    }

    private fun performSilentClean(resultId: String) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val repeated = resultId.isNotEmpty() &&
            resultId == lastSilentCleanResultId &&
            (nowElapsed - lastSilentCleanElapsedMs) < 2000L
        lastSilentCleanResultId = resultId
        lastSilentCleanElapsedMs = nowElapsed
        if (repeated) {
            pressHome()
        } else {
            pressBack()
        }
    }

    private fun dispatchAlwaysOnInterception(
        style: BlockInterceptionStyle,
        mode: Int,
        resultId: String,
        attributionLabel: String,
        triggerReason: String
    ) {
        when (style) {
            BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE -> {
                val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, triggerReason)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, resultId)
                    putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, 5)
                }
                startActivity(intent)
            }
            BlockInterceptionStyle.SILENT_CLEAN -> {
                performSilentClean(resultId)
            }
            BlockInterceptionStyle.INSTANT_HOME_OR_BACK -> {
                pressHome()
            }
            BlockInterceptionStyle.STANDARD_WARNING_DIALOG -> {
                val intent = Intent(this, WarningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("mode", mode)
                    putExtra("result_id", resultId)
                    putExtra("blocked_by_feature", attributionLabel)
                }
                startActivity(intent)
            }
        }
    }

    private fun dispatchUnifiedRuleInterception(
        rule: UnifiedBlockRule,
        mode: Int,
        resultId: String,
        triggerReason: String,
        isPressHome: Boolean = false,
        isReelBlocker: Boolean = false,
        styleOverride: BlockInterceptionStyle? = null
    ) {
        val attributionLabel = "Blocked by Custom Rule: ${rule.name}"
        val effectiveStyle = styleOverride ?: rule.interceptionStyle
        when (effectiveStyle) {
            BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE -> {
                val intent = Intent(this, AmniSpaceActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(Constants.AMNISPACE_EXTRA_MODE, Constants.AMNISPACE_MODE_MINDFUL_BREATHING)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_REASON, triggerReason)
                    putExtra(Constants.AMNISPACE_EXTRA_TRIGGER_APP, resultId)
                    putExtra(Constants.AMNISPACE_EXTRA_DURATION_SECONDS, rule.closeDelaySeconds.coerceAtLeast(5))
                    putExtra("custom_warning_message", rule.customWarningMessage)
                }
                startActivity(intent)
            }
            BlockInterceptionStyle.SILENT_CLEAN -> {
                performSilentClean(resultId)
            }
            BlockInterceptionStyle.INSTANT_HOME_OR_BACK -> {
                pressHome()
            }
            BlockInterceptionStyle.STANDARD_WARNING_DIALOG -> {
                val intent = Intent(this, WarningActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("mode", mode)
                    putExtra("result_id", resultId)
                    putExtra("blocked_by_feature", attributionLabel)
                    putExtra("custom_warning_message", rule.customWarningMessage)
                    putExtra("custom_close_delay_seconds", rule.closeDelaySeconds)
                    putExtra("is_press_home", isPressHome)
                    putExtra("is_reel_blocker", isReelBlocker)
                }
                startActivity(intent)
            }
        }
    }

    private fun refreshUsageStatsCacheIfNeeded(force: Boolean = false) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && (nowElapsed - lastUsageCacheRefreshElapsedMs) < 45_000L) return
        if (!isRefreshingUsageStats.compareAndSet(false, true)) return
        lastUsageCacheRefreshElapsedMs = nowElapsed
        serviceScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@runCatching
                    val nowWall = System.currentTimeMillis()
                    val startOfDayCal = Calendar.getInstance().apply {
                        timeInMillis = nowWall
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val dailyStats = usm.queryAndAggregateUsageStats(startOfDayCal.timeInMillis, nowWall)
                    cachedDailyUsageMinutesMap = dailyStats.mapValues { (_, stat) ->
                        (stat.totalTimeInForeground / 60_000L).toInt()
                    }

                    val oneHourAgo = nowWall - 3_600_000L
                    val hourlyStats = usm.queryAndAggregateUsageStats(oneHourAgo, nowWall)
                    cachedHourlyUsageMinutesMap = hourlyStats.mapValues { (_, stat) ->
                        (stat.totalTimeInForeground / 60_000L).toInt()
                    }
                }
            } finally {
                isRefreshingUsageStats.set(false)
            }
        }
    }

    private fun isUnifiedRuleEnforcingForPackage(
        rule: UnifiedBlockRule,
        packageName: String,
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
        if (rule.screenTimeLimits.isNotEmpty()) {
            refreshUsageStatsCacheIfNeeded(force = false)
            val dailyUsed = cachedDailyUsageMinutesMap[packageName] ?: 0
            val hourlyUsed = cachedHourlyUsageMinutesMap[packageName] ?: 0
            val exceeded = rule.screenTimeLimits.any { limit ->
                when (limit.period) {
                    LimitPeriod.DAILY -> dailyUsed >= limit.maxMinutes
                    LimitPeriod.HOURLY -> hourlyUsed >= limit.maxMinutes
                }
            }
            if (exceeded) return true
        }
        if (rule.openLimits.isNotEmpty()) {
            val dailyOpens = cachedUniversalDailyLaunches[packageName] ?: 0
            val hourlyOpens = cachedUniversalHourlyLaunches[packageName] ?: 0
            val exceeded = rule.openLimits.any { limit ->
                when (limit.period) {
                    LimitPeriod.DAILY -> dailyOpens >= limit.maxLaunches
                    LimitPeriod.HOURLY -> hourlyOpens >= limit.maxLaunches
                }
            }
            if (exceeded) return true
        }
        return false
    }

    private fun shouldBlockPackageByUnifiedRules(packageName: String): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        for (rule in snapshot) {
            if (!rule.blockAppLaunch) continue
            if (rule.appTargets[packageName] != AppInterceptionLevel.BLOCK_ENTIRE_APP) continue
            if (isUnifiedRuleEnforcingForPackage(rule, packageName, nowElapsed, nowWall)) {
                return rule
            }
        }
        return null
    }

    private fun findShortformBlockingUnifiedRule(packageName: String): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        for (rule in snapshot) {
            if (rule.appTargets[packageName] != AppInterceptionLevel.BLOCK_SHORTFORM_ONLY) continue
            if (isUnifiedRuleEnforcingForPackage(rule, packageName, nowElapsed, nowWall)) {
                return rule
            }
        }
        return null
    }

    private fun isInBrowserShortformEnabledByUnifiedRules(): Boolean {
        return findBrowserShortformUnifiedRule() != null
    }

    private fun findBrowserShortformUnifiedRule(): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        return snapshot.firstOrNull { rule ->
            rule.includeInBrowserShortform && rule.isActiveOrScheduledForContentNow(nowElapsed, nowWall)
        }
    }

    private fun findUnifiedRuleForBlockedDomain(domain: String): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val clean = domain.lowercase(Locale.ROOT)
        return snapshot.firstOrNull { rule ->
            rule.isActiveOrScheduledForContentNow(nowElapsed, nowWall) &&
                rule.blockedDomains.any { clean == it || clean.endsWith(".$it") }
        }
    }

    private fun findUnifiedRuleForBlockedKeyword(keyword: String): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val clean = keyword.lowercase(Locale.ROOT)
        return snapshot.firstOrNull { rule ->
            rule.isActiveOrScheduledForContentNow(nowElapsed, nowWall) &&
                rule.blockedKeywords.any { it.equals(clean, ignoreCase = true) || clean.contains(it) }
        }
    }

    private fun findNotificationSuppressingRule(packageName: String): UnifiedBlockRule? {
        val snapshot = unifiedRulesSnapshot
        if (snapshot.isEmpty()) return null
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        for (rule in snapshot) {
            if (!rule.blockNotifications) continue
            if (!rule.appTargets.containsKey(packageName)) continue
            if (isUnifiedRuleEnforcingForPackage(rule, packageName, nowElapsed, nowWall)) {
                return rule
            }
        }
        return null
    }

    private fun setupAppBlocker() {
        appBlockerWarningConfig = savedPreferencesLoader.loadAppBlockerWarningInfo()
        appBlocker.blockedApps = savedPreferencesLoader.loadBlockedApps().toHashSet()
        appBlocker.restoreCooldowns(savedPreferencesLoader.loadAppBlockerCooldownData())
        val loadedSchedules = savedPreferencesLoader.loadAppBlockerScheduleRules()
        legacyScheduleRulesSnapshot = loadedSchedules
        appBlocker.refreshScheduleRules(loadedSchedules)
        savedPreferencesLoader.saveAppBlockerCooldownData(appBlocker.getCooldownSnapshot())
    }

    private fun setupWebsiteBlocker() {
        isWebsiteBlockerMasterEnabled = savedPreferencesLoader.isWebsiteBlockerEnabled(false)
        cachedBlockedWebsites = savedPreferencesLoader.loadBlockedWebsites()
        cachedBlockedSocialApps = savedPreferencesLoader.loadBlockedWebsitesApps()
    }

    private fun setupKeywordBlocker() {
        keywordBlockerWarningConfig = savedPreferencesLoader.loadKeywordBlockerWarningInfo()
        val userKeywords = savedPreferencesLoader.loadBlockedKeywords()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        cachedBlockedKeywords = userKeywords.toSet()
        keywordBlocker.blockedKeyword = HashSet(userKeywords)

        val keywordPrefs = getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE)
        val webGuard = savedPreferencesLoader.loadAlwaysOnShieldsConfig().website
        keywordBlocker.wholeWordOnly = keywordPrefs.getBoolean("whole_word_only", true)
        keywordBlocker.isSearchAllTextFields = keywordPrefs.getBoolean("search_all_text_fields", false)
        keywordBlocker.isUnsupportedBrowserBlockingOn = webGuard.enabled && webGuard.blockUnsupportedBrowsers
        val configuredRedirect = keywordPrefs.getString("redirect_url", KeywordBlocker.DEFAULT_REDIRECT_URL).orEmpty()
        keywordBlocker.redirectUrl = configuredRedirect.ifBlank { KeywordBlocker.DEFAULT_REDIRECT_URL }
        keywordBlocker.resetDetectionCache()
        lastAppliedKeywordSignature = 0

        val userIgnoredPackages = savedPreferencesLoader
            .getKeywordBlockerIgnoredApps()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableSet()

        userIgnoredPackages.add(this.packageName)
        userIgnoredPackages.add("com.alhaq.amnishield")
        userIgnoredPackages.add("com.android.settings")
        userIgnoredPackages.add("com.google.android.settings")
        userIgnoredPackages.add("com.samsung.android.settings")
        userIgnoredPackages.add("com.coloros.settings")
        userIgnoredPackages.add("com.android.systemui")
        userIgnoredPackages.add("android")
        userIgnoredPackages.add("com.android.launcher")
        userIgnoredPackages.add("com.google.android.apps.nexuslauncher")
        userIgnoredPackages.add("com.sec.android.app.launcher")
        
        // Add OEM system managers and security centers to ignored packages
        userIgnoredPackages.add("com.huawei.systemmanager")
        userIgnoredPackages.add("com.miui.securitycenter")
        userIgnoredPackages.add("com.iqoo.secure")
        userIgnoredPackages.add("com.oppo.safe")
        userIgnoredPackages.add("com.oneplus.security")
        userIgnoredPackages.add("com.vivo.permissionmanager")
        userIgnoredPackages.add("com.samsung.android.lool")
        userIgnoredPackages.add("com.samsung.android.sm")
        userIgnoredPackages.add("com.samsung.android.sm_cn")
        userIgnoredPackages.add("com.coloros.safecenter")
        if (cachedDefaultLauncher != null) {
            userIgnoredPackages.add(cachedDefaultLauncher!!)
        }

        keywordBlocker.ignoredPackages = userIgnoredPackages
    }

    private fun setupReelBlocker() {
        val viewBlockerPrefs = getSharedPreferences("view_blocker", Context.MODE_PRIVATE)
        val reelBlockerPrefs = getSharedPreferences("reel_blocker", Context.MODE_PRIVATE)
        val configReelsPrefs = getSharedPreferences("config_reels", Context.MODE_PRIVATE)

        reelBlocker.isEnabled = savedPreferencesLoader.isReelBlockerEnabled(
            viewBlockerPrefs.getBoolean("is_enabled", false)
        )
        reelBlocker.isIGInboxReelAllowed = configReelsPrefs.getBoolean("is_reel_inbox", false)
        reelBlocker.isFirstReelInFeedAllowed = configReelsPrefs.getBoolean("is_reel_first", false)
        reelBlocker.modeType = savedPreferencesLoader.getReelBlockerMode(ReelBlocker.MODE_BLOCK_ALL)
        reelBlocker.dailyReelLimit = savedPreferencesLoader.getReelBlockerDailyLimit(200)

        val hasConfiguredPlatforms = reelBlockerPrefs.contains("is_youtube_enabled") ||
                reelBlockerPrefs.contains("is_instagram_enabled") ||
                reelBlockerPrefs.contains("is_tiktok_enabled")

        reelBlocker.isYoutubeEnabled = if (hasConfiguredPlatforms) reelBlockerPrefs.getBoolean("is_youtube_enabled", false) else true
        reelBlocker.isInstagramEnabled = if (hasConfiguredPlatforms) reelBlockerPrefs.getBoolean("is_instagram_enabled", false) else true
        reelBlocker.isTiktokEnabled = if (hasConfiguredPlatforms) reelBlockerPrefs.getBoolean("is_tiktok_enabled", false) else true
        reelBlocker.isBrowserShortsEnabled = savedPreferencesLoader.isReelBlockerBrowserEnabled()
        reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()

        reelBlocker.restoreCooldowns(savedPreferencesLoader.loadReelBlockerCooldownData())
        savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
    }

    private fun trackAppLaunch(packageName: String) {
        if (!savedPreferencesLoader.isAppUsageTrackingEnabled(true)) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                savedPreferencesLoader.trackAppLaunch(packageName)
                val now = System.currentTimeMillis()
                val daily = savedPreferencesLoader.getUniversalDailyLaunchCount(packageName, now)
                val hourly = savedPreferencesLoader.getUniversalHourlyLaunchCount(packageName, now)
                cachedUniversalDailyLaunches = cachedUniversalDailyLaunches + (packageName to daily)
                cachedUniversalHourlyLaunches = cachedUniversalHourlyLaunches + (packageName to hourly)
            } catch (e: Exception) {
                android.util.Log.e("AmniShield", "Error tracking app launch", e)
            }
        }
    }

    private fun refreshDefaultLauncherAsync(force: Boolean = false) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && (nowElapsed - lastLauncherRefreshElapsedMs.get()) < 30_000L) return
        if (!isRefreshingLauncher.compareAndSet(false, true)) return
        lastLauncherRefreshElapsedMs.set(nowElapsed)
        serviceScope.launch(Dispatchers.IO) {
            try {
                cachedDefaultLauncher = getDefaultLauncherPackage()
            } finally {
                isRefreshingLauncher.set(false)
            }
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val resolved = runCatching {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: packageName
        appLabelCache[packageName] = resolved
        return resolved
    }

    private fun getDefaultLauncherPackage(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }
    }


    private fun setupFocusMode() {
        val focusModeData = savedPreferencesLoader.getFocusModeData()
        focusModeBlocker.update(focusModeData)
        com.alhaq.amnishield.ui.widgets.QuickFocusWidgetProvider.updateAllWidgets(this)
    }





    override fun onInterrupt() {
    }

    private fun isFeatureCurrentlyActive(featureKey: String): Boolean {
        val targetType = when {
            featureKey.equals("FOCUS_MODE", ignoreCase = true) || featureKey.equals("focus_mode", ignoreCase = true) -> BlockerType.FOCUS_MODE
            featureKey.equals("app_blocker", ignoreCase = true) -> BlockerType.APP
            featureKey.equals("website_blocker", ignoreCase = true) -> BlockerType.WEBSITE
            featureKey.equals("keyword_blocker", ignoreCase = true) -> BlockerType.KEYWORD
            featureKey.equals("reel_blocker", ignoreCase = true) -> BlockerType.REELS
            else -> BlockerType.fromPackageName(featureKey)
        }

        val rawRules = legacyScheduleRulesSnapshot

        val featureRules = rawRules.filter {
            it.blockerType == targetType ||
            (targetType == BlockerType.APP && (
                it.packageName != "keyword_blocker" &&
                it.packageName != "website_blocker" &&
                it.packageName != "reel_blocker" &&
                !it.packageName.equals("FOCUS_MODE", ignoreCase = true) &&
                !it.packageName.equals("focus_mode", ignoreCase = true)
            )) ||
            it.packageName.equals(featureKey, ignoreCase = true) ||
            it.groupTitle?.equals(featureKey, ignoreCase = true) == true ||
            it.title.equals(featureKey, ignoreCase = true)
        }

        if (featureRules.isEmpty()) {
            // Strict Opt-In Architecture: No rules configured means feature is INACTIVE.
            return false
        }

        val enabledRules = featureRules.filter { it.isRuleEnabled }
        if (enabledRules.isEmpty()) {
            // All rules for this feature are disabled; feature is INACTIVE.
            return false
        }

        // Check cheat hours (cheat window bypasses blocking)
        val cheatRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.CHEAT }
        val activeCheatEnd = getActiveRuleEndTimeLocal(cheatRules)
        if (activeCheatEnd != null) {
            return false // Bypassed during active cheat window
        }

        // Check block schedules
        val blockRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.BLOCK }
        if (blockRules.isNotEmpty()) {
            val timedRules = blockRules.filter { it.durationHours <= 0 }
            val lengthRules = blockRules.filter { it.durationHours > 0 }

            if (timedRules.isNotEmpty()) {
                val activeBlockEnd = getActiveRuleEndTimeLocal(timedRules)
                if (activeBlockEnd != null) return true
            }

            if (lengthRules.isNotEmpty()) {
                val targetHours = lengthRules.first().durationHours
                val currentHourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (currentHourOfDay < targetHours) {
                    return true
                }
            }

            return false // Active only inside scheduled block window or focus length hours
        }

        return true
    }

    private fun isRuleCurrentlyActive(rule: AppBlockScheduleRule, nowMillis: Long): Boolean {
        if (!rule.isRuleEnabled) return false
        val recurrence = rule.recurrence ?: AppBlockScheduleRule.Recurrence.DAILY
        return when (recurrence) {
            AppBlockScheduleRule.Recurrence.ALWAYS -> true
            AppBlockScheduleRule.Recurrence.HOURLY -> rule.activeUntilMillis > nowMillis
            AppBlockScheduleRule.Recurrence.DAILY -> ScheduleUtils.isDailyWindowActive(rule.startMinute, rule.endMinute, nowMillis)
            AppBlockScheduleRule.Recurrence.WEEKLY -> ScheduleUtils.isWeeklyWindowActive(rule.startMinute, rule.endMinute, rule.selectedDays ?: emptySet(), nowMillis)
        }
    }

    private fun getActiveBlockedWebsites(): Set<String> {
        val activeWebsites = mutableSetOf<String>()
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMillis = System.currentTimeMillis()

        // 0. Include user custom domains from Always-On Website Guard (@Volatile snapshot)
        // Large bundled packs are checked in O(1) via DomainBlocklistEngine without String allocation.
        val websiteGuard = alwaysOnShieldsSnapshot.website
        if (websiteGuard.enabled) {
            websiteGuard.customDomains.forEach { raw ->
                com.alhaq.amnishield.data.blockers.WebsiteGuardConfig.normalizeDomainInput(raw)
                    ?.let { activeWebsites.add(it) }
            }
        }

        // 1. Include active domains from UnifiedBlockRules (@Volatile snapshot)
        unifiedRulesSnapshot.forEach { rule ->
            if (rule.blockedDomains.isNotEmpty() &&
                rule.isActiveOrScheduledForContentNow(nowElapsed, nowMillis)
            ) {
                activeWebsites.addAll(rule.blockedDomains)
            }
        }

        // 2. Include legacy AppBlockScheduleRule websites from @Volatile snapshot
        val rawRules = legacyScheduleRulesSnapshot
        val websiteRules = rawRules.filter {
            it.blockerType == BlockerType.WEBSITE ||
            it.packageName.equals("website_blocker", ignoreCase = true) ||
            it.groupTitle?.equals("website_blocker", ignoreCase = true) == true ||
            it.title.contains("Website Blocker", ignoreCase = true)
        }

        if (websiteRules.isNotEmpty()) {
            val enabledRules = websiteRules.filter { it.isRuleEnabled }
            if (enabledRules.isNotEmpty()) {
                val cheatRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.CHEAT }
                val activeCheatEnd = getActiveRuleEndTimeLocal(cheatRules)
                if (activeCheatEnd == null) {
                    val blockRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.BLOCK }
                    for (rule in blockRules) {
                        if (isRuleCurrentlyActive(rule, nowMillis)) {
                            val targets = rule.targets.ifEmpty { rule.targetWebsites }
                            if (targets.isNotEmpty()) {
                                activeWebsites.addAll(targets)
                            } else {
                                activeWebsites.addAll(cachedBlockedWebsites)
                            }
                        }
                    }
                }
            }
        }

        return activeWebsites
    }

    private fun getActiveBlockedKeywords(): Set<String> {
        val activeKeywords = mutableSetOf<String>()
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMillis = System.currentTimeMillis()

        // 0. Include active keywords from Always-On Keyword Filter (@Volatile snapshot)
        val keywordGuard = alwaysOnShieldsSnapshot.keyword
        if (keywordGuard.enabled) {
            activeKeywords.addAll(keywordGuard.effectiveKeywords())
        }

        // 1. Include active keywords from UnifiedBlockRules (@Volatile snapshot)
        unifiedRulesSnapshot.forEach { rule ->
            if (rule.blockedKeywords.isNotEmpty() &&
                rule.isActiveOrScheduledForContentNow(nowElapsed, nowMillis)
            ) {
                activeKeywords.addAll(rule.blockedKeywords)
            }
        }

        // 2. Include legacy AppBlockScheduleRule keywords from @Volatile snapshot
        val rawRules = legacyScheduleRulesSnapshot
        val keywordRules = rawRules.filter {
            it.blockerType == BlockerType.KEYWORD ||
            it.packageName.equals("keyword_blocker", ignoreCase = true) ||
            it.groupTitle?.equals("keyword_blocker", ignoreCase = true) == true ||
            it.title.contains("Keyword Blocker", ignoreCase = true)
        }

        if (keywordRules.isNotEmpty()) {
            val enabledRules = keywordRules.filter { it.isRuleEnabled }
            if (enabledRules.isNotEmpty()) {
                val cheatRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.CHEAT }
                val activeCheatEnd = getActiveRuleEndTimeLocal(cheatRules)
                if (activeCheatEnd == null) {
                    val blockRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.BLOCK }
                    for (rule in blockRules) {
                        if (isRuleCurrentlyActive(rule, nowMillis)) {
                            val targets = rule.targets.ifEmpty { rule.targetKeywords }
                            if (targets.isNotEmpty()) {
                                activeKeywords.addAll(targets)
                            } else {
                                activeKeywords.addAll(cachedBlockedKeywords)
                            }
                        }
                    }
                }
            }
        }

        return activeKeywords
    }

    private fun getActiveFocusModeRule(): AppBlockScheduleRule? {
        val rawRules = legacyScheduleRulesSnapshot
        val focusRules = rawRules.filter {
            it.blockerType == BlockerType.FOCUS_MODE ||
            it.packageName.equals("FOCUS_MODE", ignoreCase = true) ||
            it.packageName.equals("focus_mode", ignoreCase = true) ||
            it.groupTitle?.equals("focus_mode", ignoreCase = true) == true ||
            it.title.contains("Focus Mode", ignoreCase = true)
        }
        val enabledRules = focusRules.filter { it.isRuleEnabled }
        if (enabledRules.isEmpty()) return null

        val cheatRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.CHEAT }
        if (getActiveRuleEndTimeLocal(cheatRules) != null) return null

        val blockRules = enabledRules.filter { it.type == AppBlockScheduleRule.RuleType.BLOCK }
        val nowMillis = System.currentTimeMillis()
        return blockRules.firstOrNull { isRuleCurrentlyActive(it, nowMillis) }
    }

    private fun getActiveRuleEndTimeLocal(rules: List<AppBlockScheduleRule>): Long? {
        if (rules.isEmpty()) return null
        val nowMillis = System.currentTimeMillis()
        var latestEnd: Long? = null
        rules.forEach { rule ->
            val recurrence = rule.recurrence ?: AppBlockScheduleRule.Recurrence.DAILY
            val candidateEnd = when (recurrence) {
                AppBlockScheduleRule.Recurrence.ALWAYS -> nowMillis + (24L * 60L * 60L * 1000L)
                AppBlockScheduleRule.Recurrence.HOURLY -> {
                    if (rule.activeUntilMillis > nowMillis) rule.activeUntilMillis else null
                }
                AppBlockScheduleRule.Recurrence.DAILY -> {
                    ScheduleUtils.getDailyWindowEndTime(rule.startMinute, rule.endMinute, nowMillis)
                }
                AppBlockScheduleRule.Recurrence.WEEKLY -> {
                    ScheduleUtils.getWeeklyWindowEndTime(rule.startMinute, rule.endMinute, rule.selectedDays ?: emptySet(), nowMillis)
                }
            }
            if (candidateEnd != null) {
                latestEnd = if (latestEnd == null) candidateEnd else maxOf(latestEnd, candidateEnd)
            }
        }
        return latestEnd
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::reelsSessionTracker.isInitialized) {
            reelsSessionTracker.stop()
        }
        if (::browserSessionTracker.isInitialized) {
            browserSessionTracker.stop()
        }
        if (::serviceBroadcastManager.isInitialized) {
            serviceBroadcastManager.unregister()
        }
        eventChannel.close()
        serviceScope.cancel()
    }
}
