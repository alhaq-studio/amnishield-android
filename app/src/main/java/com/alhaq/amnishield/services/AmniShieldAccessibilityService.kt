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
import com.alhaq.amnishield.blockers.AppBlocker
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.blockers.KeywordBlocker
import com.alhaq.amnishield.blockers.HomeFeedNavigator
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.blockers.ViewBlocker
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.trackers.ReelDetectionEngine
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.activity.WarningActivity
import com.alhaq.amnishield.utils.BlockingStatsManager
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
        const val INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES = "amnishield.refresh.unified.feature.schedules"
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
    private lateinit var handGestureOverlayManager: com.alhaq.amnishield.ui.overlay.HandGestureOverlayManager
    private val homeFeedNavigator = HomeFeedNavigator()

    private var appBlockerWarningConfig = MainActivity.WarningData()
    private var keywordBlockerWarningConfig = MainActivity.WarningData()

    private lateinit var antiUninstallDetector: com.alhaq.amnishield.security.AntiUninstallDetector
    private lateinit var reelsSessionTracker: com.alhaq.amnishield.trackers.ReelsSessionTracker
    private lateinit var serviceBroadcastManager: ServiceBroadcastManager
    private lateinit var keywordActionHandler: com.alhaq.amnishield.blockers.KeywordActionHandler
    private lateinit var reelActionHandler: com.alhaq.amnishield.blockers.ReelActionHandler
    private lateinit var websiteBlockerDetector: com.alhaq.amnishield.blockers.WebsiteBlockerDetector

    private var cachedDefaultLauncher: String? = null
    private var lastTrackedLaunchPackage: String? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val eventChannel = Channel<AccessibilityEvent>(Channel.CONFLATED) { droppedEvent ->
        droppedEvent.recycle()
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
            handGestureOverlayManager = com.alhaq.amnishield.ui.overlay.HandGestureOverlayManager(this)

            antiUninstallDetector = com.alhaq.amnishield.security.AntiUninstallDetector(this)
            keywordActionHandler = com.alhaq.amnishield.blockers.KeywordActionHandler(
                context = this,
                savedPreferencesLoader = savedPreferencesLoader,
                keywordBlocker = keywordBlocker,
                handGestureOverlayManager = handGestureOverlayManager
            )
            reelActionHandler = com.alhaq.amnishield.blockers.ReelActionHandler(
                context = this,
                savedPreferencesLoader = savedPreferencesLoader,
                homeFeedNavigator = homeFeedNavigator
            )
            websiteBlockerDetector = com.alhaq.amnishield.blockers.WebsiteBlockerDetector(savedPreferencesLoader)

            runCatching { savedPreferencesLoader.migrateLegacySchedulesIfNeeded() }
                .onFailure { Log.e("AmniShieldService", "Failed schedule migration", it) }
            runCatching { setupAppBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupAppBlocker", it) }
            runCatching { setupFocusMode() }
                .onFailure { Log.e("AmniShieldService", "Failed setupFocusMode", it) }
            runCatching { setupKeywordBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupKeywordBlocker", it) }
            runCatching { setupReelBlocker() }
                .onFailure { Log.e("AmniShieldService", "Failed setupReelBlocker", it) }
            cachedDefaultLauncher = getDefaultLauncherPackage()

            reelsSessionTracker = com.alhaq.amnishield.trackers.ReelsSessionTracker(
                service = this,
                savedPreferencesLoader = savedPreferencesLoader,
                reelBlocker = reelBlocker,
                crashLogger = crashLogger,
                isFeatureActive = { isFeatureCurrentlyActive(it) }
            )
            reelsSessionTracker.start()

            serviceBroadcastManager = ServiceBroadcastManager(
                context = this,
                callbacks = object : ServiceBroadcastManager.Callbacks {
                    override fun onRefreshAppBlocker() = setupAppBlocker()
                    override fun onRefreshBlockedKeywordList() = setupKeywordBlocker()
                    override fun onRefreshViewBlocker() = setupReelBlocker()
                    override fun onRefreshViewBlockerCooldown(resultId: String?, interval: Int) {
                        val warningConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
                        val duration = if (interval > 0) interval else warningConfig.timeInterval
                        val endTime = System.currentTimeMillis() + duration
                        reelBlocker.applyCooldown(resultId ?: "xxxxxxxxxxxxxx", endTime)
                        savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
                    }
                    override fun onRefreshReelBlocker() = setupReelBlocker()
                    override fun onRefreshReelBlockerCooldown(resultId: String?, interval: Int) {
                        val warningConfig = savedPreferencesLoader.loadViewBlockerWarningInfo()
                        val duration = if (interval > 0) interval else warningConfig.timeInterval
                        val endTime = System.currentTimeMillis() + duration
                        reelBlocker.applyCooldown(resultId ?: "xxxxxxxxxxxxxx", endTime)
                        savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
                    }
                    override fun onRefreshKeywordBlockerCooldown(resultId: String?, interval: Int) {
                        val duration = if (interval > 0) interval else keywordBlockerWarningConfig.timeInterval
                        val endTime = System.currentTimeMillis() + duration
                        resultId?.let { keywordBlocker.applyCooldown(it, endTime) }
                    }
                    override fun onRefreshUnifiedFeatureSchedules() {
                        setupAppBlocker()
                        setupReelBlocker()
                        setupKeywordBlocker()
                    }
                    override fun onRefreshAppBlockerCooldown(resultId: String?, interval: Int) {
                        val duration = if (interval > 0) interval else appBlockerWarningConfig.timeInterval
                        val endTime = System.currentTimeMillis() + duration
                        appBlocker.putCooldownTo(resultId ?: "xxxxxxxxxxxxxx", endTime)
                        savedPreferencesLoader.saveAppBlockerCooldownData(appBlocker.getCooldownSnapshot())
                    }
                    override fun onRefreshFocusMode() = setupFocusMode()
                    override fun onRefreshAntiUninstall() = antiUninstallDetector.reloadConfig()
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
                packageName.equals("com.alhaq.amnishield", ignoreCase = true)
            ) {
                return
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
                    val launcher = cachedDefaultLauncher ?: getDefaultLauncherPackage()
                    if (packageName != launcher) {
                        trackAppLaunch(packageName)
                    }
                }
                cachedDefaultLauncher = getDefaultLauncherPackage()
            }

            rootNode = rootInActiveWindow
            if (rootNode != null && (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)) {
                reelsSessionTracker.onAccessibilityEvent(event, rootNode)
            }

            val rootPackage = rootNode?.packageName?.toString() ?: packageName

            if (rootPackage.equals(myPackageName, ignoreCase = true) ||
                rootPackage.equals("com.alhaq.amnishield", ignoreCase = true) ||
                rootPackage.equals("com.android.systemui", ignoreCase = true)
            ) {
                return
            }

            if (antiUninstallDetector.inspect(event, rootNode)) {
                return
            }

            val isPremiumUser = premiumManager.isPremium()

            val isManualFocusActive = focusModeBlocker.focusModeData.isTurnedOn
            val isAutoFocusScheduleActive = isFeatureCurrentlyActive("FOCUS_MODE") || isFeatureCurrentlyActive("focus_mode")
            val isFocusModeActive = isManualFocusActive || isAutoFocusScheduleActive
            val activeFocusModeType = if (isManualFocusActive) {
                focusModeBlocker.focusModeData.modeType
            } else if (isAutoFocusScheduleActive) {
                savedPreferencesLoader.getFocusModeData().modeType
            } else {
                -1
            }
            val isFocusBlockAllExSelectedActive = isFocusModeActive && activeFocusModeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED

            if (isFocusModeActive) {
                val focusModeResult = focusModeBlocker.doesAppNeedToBeBlocked(this, packageName, savedPreferencesLoader, cachedDefaultLauncher)
                if (focusModeResult.isRequestingToUpdateSPData) {
                    savedPreferencesLoader.completeFocusSession()
                    savedPreferencesLoader.saveFocusModeData(focusModeBlocker.focusModeData)
                }

                if (focusModeResult.isBlocked) {
                    blockingStatsManager.recordAppBlock(packageName, "Blocked by Focus Mode")
                    pressHome()
                    return
                }
            }

            if (savedPreferencesLoader.isWebsiteBlockerEnabled(false) && isFeatureCurrentlyActive("website_blocker")) {
                val blockedSocialApps = savedPreferencesLoader.loadBlockedWebsitesApps()
                if (blockedSocialApps.contains(packageName)) {
                    blockingStatsManager.recordAppBlock(packageName, "Blocked by Website Blocker")
                    val intent = Intent(this, WarningActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
                        putExtra("result_id", packageName)
                        putExtra("blocked_by_feature", "Website Blocker")
                    }
                    startActivity(intent)
                    return
                }
            }

            if (!isFocusBlockAllExSelectedActive) {
                if (savedPreferencesLoader.isAppBlockerFeatureEnabled(false) && isFeatureCurrentlyActive("app_blocker")) {
                    val appBlockerResult = appBlocker.doesAppNeedToBeBlocked(packageName, savedPreferencesLoader)
                    if (appBlockerResult.isBlocked) {
                        blockingStatsManager.recordAppBlock(packageName, "Blocked by App Blocker")
                        val intent = Intent(this, WarningActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
                            putExtra("result_id", packageName)
                            putExtra("blocked_by_feature", "App Blocker")
                        }
                        startActivity(intent)
                        return
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
                eventCopy.recycle()
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
                    event.recycle()
                }
            }
        }
    }

    private fun processDeferredChecks(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val rootNode = rootInActiveWindow ?: return
        try {
            val rootPackage = rootNode.packageName?.toString() ?: packageName

            val myPackageName = this.packageName
            if (packageName.equals(myPackageName, ignoreCase = true) ||
                packageName.equals("com.alhaq.amnishield", ignoreCase = true) ||
                rootPackage.equals(myPackageName, ignoreCase = true) ||
                rootPackage.equals("com.alhaq.amnishield", ignoreCase = true) ||
                packageName.equals("com.android.systemui", ignoreCase = true) ||
                rootPackage.equals("com.android.systemui", ignoreCase = true)
            ) {
                return
            }

            val isKeywordEnabled = savedPreferencesLoader.isKeywordBlockerFeatureEnabled(false)
            if (isKeywordEnabled && isFeatureCurrentlyActive("keyword_blocker")) {
                val manualKeywords = savedPreferencesLoader.loadBlockedKeywords()
                    .map { it.trim().lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }

                if (manualKeywords.isNotEmpty()) {
                    keywordBlocker.blockedKeyword = HashSet(manualKeywords)
                    try {
                        val keywordResult = keywordBlocker.checkIfUserGettingFreaky(rootNode, event)
                        if (keywordResult.resultDetectWord != null) {
                            blockingStatsManager.recordKeywordBlock(keywordResult.resultDetectWord, packageName)
                            keywordActionHandler.handleKeywordBlock(
                                result = keywordResult,
                                packageName = packageName,
                                rootNode = rootNode,
                                event = event,
                                onPressHome = { pressHome() }
                            )
                            return
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AmniShield", "Core Keyword blocker error", e)
                    }
                }
            }

            if (savedPreferencesLoader.isWebsiteBlockerEnabled(false) && isFeatureCurrentlyActive("website_blocker")) {
                try {
                    if (websiteBlockerDetector.checkBlockedWebsites(rootNode, packageName)) {
                        blockingStatsManager.recordAppBlock(packageName, "Website Blocked: $packageName")
                        pressHome()
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AmniShield", "Website blocker error", e)
                }
            }

            val isReelsEnabled = (reelBlocker.isEnabled || savedPreferencesLoader.isReelBlockerEnabled(false)) && isFeatureCurrentlyActive("reel_blocker")
            if (isReelsEnabled) {
                reelBlocker.isYoutubeEnabled = savedPreferencesLoader.isReelBlockerYoutubeEnabled()
                reelBlocker.isInstagramEnabled = savedPreferencesLoader.isReelBlockerInstagramEnabled()
                reelBlocker.isTiktokEnabled = savedPreferencesLoader.isReelBlockerTiktokEnabled()
                reelBlocker.isBrowserShortsEnabled = savedPreferencesLoader.isReelBlockerBrowserEnabled()
                reelBlocker.modeType = savedPreferencesLoader.getReelBlockerMode()
                reelBlocker.dailyReelLimit = savedPreferencesLoader.getReelBlockerDailyLimit()
                reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()

                try {
                    val rawResult = reelBlocker.doesReelNeedToBeBlocked(rootNode, packageName)
                    if (rawResult != null && rawResult.isBlocked) {
                        blockingStatsManager.recordViewBlock(packageName, rawResult.viewId)
                        val reelBlockerResult = rawResult.copy(
                            blockResponseMode = savedPreferencesLoader.getReelBlockerBlockResponseMode()
                        )
                        reelActionHandler.handleReelBlock(
                            result = reelBlockerResult,
                            onPressHome = { pressHome() },
                            onPressBack = { pressBack() },
                            getRootInActiveWindow = { rootInActiveWindow }
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

    private fun setupAppBlocker() {
        appBlockerWarningConfig = savedPreferencesLoader.loadAppBlockerWarningInfo()
        appBlocker.blockedApps = savedPreferencesLoader.loadBlockedApps().toHashSet()
        appBlocker.restoreCooldowns(savedPreferencesLoader.loadAppBlockerCooldownData())
        appBlocker.refreshScheduleRules(savedPreferencesLoader.loadAppBlockerScheduleRules())
        savedPreferencesLoader.saveAppBlockerCooldownData(appBlocker.getCooldownSnapshot())
    }

    private fun setupKeywordBlocker() {
        keywordBlockerWarningConfig = savedPreferencesLoader.loadKeywordBlockerWarningInfo()
        val userKeywords = savedPreferencesLoader.loadBlockedKeywords()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        keywordBlocker.blockedKeyword = HashSet(userKeywords)

        val keywordPrefs = getSharedPreferences("keyword_blocker_configs", Context.MODE_PRIVATE)
        keywordBlocker.isSearchAllTextFields = keywordPrefs.getBoolean("search_all_text_fields", false)
        keywordBlocker.isUnsupportedBrowserBlockingOn = keywordPrefs.getBoolean("block_all_except_supported", false)
        val configuredRedirect = keywordPrefs.getString("redirect_url", KeywordBlocker.DEFAULT_REDIRECT_URL).orEmpty()
        keywordBlocker.redirectUrl = configuredRedirect.ifBlank { KeywordBlocker.DEFAULT_REDIRECT_URL }
        keywordBlocker.resetDetectionCache()

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

        reelBlocker.isYoutubeEnabled = reelBlockerPrefs.getBoolean("is_youtube_enabled", false)
        reelBlocker.isInstagramEnabled = reelBlockerPrefs.getBoolean("is_instagram_enabled", false)
        reelBlocker.isTiktokEnabled = reelBlockerPrefs.getBoolean("is_tiktok_enabled", false)
        reelBlocker.isBrowserShortsEnabled = savedPreferencesLoader.isReelBlockerBrowserEnabled()
        reelBlocker.reelsScrolledToday = savedPreferencesLoader.getReelsScrolledToday()

        reelBlocker.restoreCooldowns(savedPreferencesLoader.loadReelBlockerCooldownData())
        savedPreferencesLoader.saveReelBlockerCooldownData(reelBlocker.getCooldownSnapshot())
    }

    private fun trackAppLaunch(packageName: String) {
        if (!savedPreferencesLoader.isAppUsageTrackingEnabled(true)) return
        try {
            savedPreferencesLoader.trackAppLaunch(packageName)
        } catch (e: Exception) {
            android.util.Log.e("AmniShield", "Error tracking app launch", e)
        }
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
        val isFocusTarget = featureKey.equals("FOCUS_MODE", ignoreCase = true) || featureKey.equals("focus_mode", ignoreCase = true)
        val isAppBlockerTarget = featureKey.equals("app_blocker", ignoreCase = true)

        val rawRules = savedPreferencesLoader.loadAppBlockerScheduleRules()

        val featureRules = when {
            isAppBlockerTarget -> {
                rawRules.filter {
                    it.packageName != "keyword_blocker" &&
                    it.packageName != "website_blocker" &&
                    it.packageName != "reel_blocker" &&
                    !it.packageName.equals("FOCUS_MODE", ignoreCase = true) &&
                    !it.packageName.equals("focus_mode", ignoreCase = true)
                }
            }
            else -> {
                rawRules.filter {
                    it.packageName.equals(featureKey, ignoreCase = true) ||
                    it.groupTitle?.equals(featureKey, ignoreCase = true) == true ||
                    it.title.equals(featureKey, ignoreCase = true)
                }
            }
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
        if (::serviceBroadcastManager.isInitialized) {
            serviceBroadcastManager.unregister()
        }
        try {
            if (::handGestureOverlayManager.isInitialized) {
                handGestureOverlayManager.dismissOverlay()
            }
        } catch (_: Exception) {
        }
        eventChannel.close()
        serviceScope.cancel()
    }
}
