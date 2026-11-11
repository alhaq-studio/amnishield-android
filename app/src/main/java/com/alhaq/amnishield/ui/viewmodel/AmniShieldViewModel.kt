package com.alhaq.amnishield.ui.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.alhaq.amnishield.data.datastore.AmniShieldDataStore
import com.alhaq.amnishield.data.db.AmniShieldRuleDatabase
import com.alhaq.amnishield.data.db.AppBlockRule
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.ui.state.AmniShieldState
import com.alhaq.amnishield.ui.state.AppTheme
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AmniShieldViewModel : ViewModel() {
    private val _state = MutableStateFlow(AmniShieldState())
    val state: StateFlow<AmniShieldState> = _state.asStateFlow()

    private var boundContext: Context? = null
    private var boundLoader: SavedPreferencesLoader? = null
    private var boundDataStore: AmniShieldDataStore? = null
    private var boundRuleDb: AmniShieldRuleDatabase? = null

    fun bindPersistence(
        context: Context,
        loader: SavedPreferencesLoader = SavedPreferencesLoader(context),
        dataStore: AmniShieldDataStore = AmniShieldDataStore.getInstance(context),
        ruleDb: AmniShieldRuleDatabase = AmniShieldRuleDatabase.getInstance(context)
    ) {
        this.boundContext = context
        this.boundLoader = loader
        this.boundDataStore = dataStore
        this.boundRuleDb = ruleDb

        val keywords = loader.loadBlockedKeywords().toList()
        val websites = loader.loadBlockedWebsites().toList()
        val scheduleRules = ruleDb.loadScheduleRules().ifEmpty {
            loader.loadAppBlockerScheduleRules().map { AppBlockRule.fromAppBlockScheduleRule(it).toScheduleRule() }
        }

        val appBlocker = loader.isAppBlockerFeatureEnabled(true)
        val reelsBlocker = loader.isReelsBlockerFeatureEnabled(true)
        val keywordBlocker = loader.isKeywordBlockerFeatureEnabled(true)
        val webFilter = loader.isWebsiteBlockerFeatureEnabled(true)
        val usageLimit = loader.isAppUsageLimitEnabled()

        _state.update { current ->
            current.copy(
                keywords = if (keywords.isNotEmpty()) keywords else current.keywords,
                customBlockedWebsites = if (websites.isNotEmpty()) websites else current.customBlockedWebsites,
                scheduleRules = if (scheduleRules.isNotEmpty()) scheduleRules else current.scheduleRules,
                isAppBlockerEnabled = appBlocker,
                isReelsBlockerEnabled = reelsBlocker,
                isKeywordBlockerEnabled = keywordBlocker,
                isWebFilterEnabled = webFilter,
                isUsageLimitEnabled = usageLimit
            )
        }
    }

    private fun sendRefreshBroadcast(action: String) {
        val ctx = boundContext ?: return
        ctx.sendBroadcast(Intent(action).setPackage(ctx.packageName))
    }

    private fun syncRulesToPersistence() {
        val rules = _state.value.scheduleRules
        boundRuleDb?.syncScheduleRules(rules)
        boundLoader?.let { loader ->
            val appBlockRules = rules.map { AppBlockRule.fromScheduleRule(it).toAppBlockScheduleRule() }
            loader.saveAppBlockerScheduleRules(appBlockRules.toMutableList())
        }
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
    }

    fun loadState(newState: AmniShieldState) {
        _state.update { current ->
            newState.copy(
                userName = if (newState.userName == "Alhaq DST") current.userName else newState.userName,
                userEmail = if (newState.userEmail == "alhaq.dst@gmail.com") current.userEmail else newState.userEmail,
                userBio = if (newState.userBio == "Digital Wellbeing Guardian • Staying mindful & focused.") current.userBio else newState.userBio,
                userGoalMinutes = if (newState.userGoalMinutes == 120) current.userGoalMinutes else newState.userGoalMinutes,
                focusProfileType = if (newState.focusProfileType == "Deep Focus") current.focusProfileType else newState.focusProfileType
            )
        }
    }

    fun updatePinSettings(appLock: Boolean, bypassLock: Boolean) {
        _state.update {
            it.copy(
                isAppLockEnabled = appLock,
                isBypassPinLockEnabled = bypassLock
            )
        }
    }

    fun updatePinResetCooldown(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(SavedPreferencesLoader.MIN_COOLDOWN_MINUTES)
        _state.update { it.copy(pinResetCooldownMinutes = safeMinutes) }
    }

    fun updateEmergencyAccessCooldown(minutes: Int) {
        val safeMinutes = minutes.coerceAtLeast(SavedPreferencesLoader.MIN_COOLDOWN_MINUTES)
        _state.update { it.copy(emergencyAccessCooldownMinutes = safeMinutes) }
    }

    fun setPinResetRequested(timestamp: Long) {
        _state.update { it.copy(pinResetRequestedTimestamp = timestamp) }
    }

    fun setEmergencyOverrideRequested(timestamp: Long) {
        _state.update { it.copy(emergencyOverrideRequestedTimestamp = timestamp) }
    }

    fun setEmergencyOverrideActive(active: Boolean, windowActive: Boolean) {
        _state.update {
            it.copy(
                isEmergencyOverrideActive = active,
                isEmergencyWindowActive = windowActive
            )
        }
    }

    fun toggleAppBlocker() {
        val newEnabled = !_state.value.isAppBlockerEnabled
        _state.update { it.copy(isAppBlockerEnabled = newEnabled) }
        boundLoader?.setAppBlockerFeatureEnabled(newEnabled)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
    }

    fun toggleReelsBlocker() {
        val newEnabled = !_state.value.isReelsBlockerEnabled
        _state.update { it.copy(isReelsBlockerEnabled = newEnabled) }
        boundLoader?.setReelsBlockerFeatureEnabled(newEnabled)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER)
    }

    fun toggleKeywordBlocker() {
        val newEnabled = !_state.value.isKeywordBlockerEnabled
        _state.update { it.copy(isKeywordBlockerEnabled = newEnabled) }
        boundLoader?.setKeywordBlockerFeatureEnabled(newEnabled)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
    }

    fun toggleWebFilter() {
        val newEnabled = !_state.value.isWebFilterEnabled
        _state.update { it.copy(isWebFilterEnabled = newEnabled) }
        boundLoader?.setWebsiteBlockerFeatureEnabled(newEnabled)
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER)
    }

    fun toggleSchedule() {
        _state.update { it.copy(isScheduleEnabled = !it.isScheduleEnabled) }
    }

    fun toggleUsageLimit() {
        val newEnabled = !_state.value.isUsageLimitEnabled
        _state.update { it.copy(isUsageLimitEnabled = newEnabled) }
        boundLoader?.setAppUsageLimitEnabled(newEnabled)
    }

    fun setAppUsageTrackingEnabled(enabled: Boolean) {
        _state.update { it.copy(isAppUsageTrackingEnabled = enabled) }
    }

    fun setWebsiteUsageTrackingEnabled(enabled: Boolean) {
        _state.update { it.copy(isWebsiteUsageTrackingEnabled = enabled) }
    }

    fun setReelsTrackingEnabled(enabled: Boolean) {
        _state.update { it.copy(isReelsTrackingEnabled = enabled) }
    }

    fun toggleFocusMode() {
        _state.update {
            val newActive = !it.isFocusModeActive
            it.copy(
                isFocusModeActive = newActive,
                focusTimeMinutes = if (newActive) it.focusTimeMinutes + 1 else it.focusTimeMinutes
            )
        }
    }

    fun updateTheme(theme: AppTheme) {
        _state.update { it.copy(currentTheme = theme) }
    }

    fun completeSetup() {
        _state.update { it.copy(isSetupComplete = true) }
    }

    fun toggleAccessibilityPermission() {
        _state.update {
            val newValue = !it.permissionAccessibilityDone
            it.copy(permissionAccessibilityDone = newValue)
        }
    }

    fun toggleBackgroundPermission() {
        _state.update {
            val newValue = !it.permissionBackgroundDone
            it.copy(permissionBackgroundDone = newValue)
        }
    }

    fun toggleNotificationsPermission() {
        _state.update {
            val newValue = !it.permissionNotificationsDone
            it.copy(permissionNotificationsDone = newValue)
        }
    }

    fun addKeyword(keyword: String) {
        if (keyword.isBlank()) return
        val trimmed = keyword.trim()
        _state.update { it.copy(keywords = it.keywords + trimmed) }
        boundLoader?.let { loader ->
            loader.saveBlockedKeywords(_state.value.keywords.toSet())
        }
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
    }

    fun removeKeyword(keyword: String) {
        _state.update { it.copy(keywords = it.keywords.filter { kw -> kw != keyword }) }
        boundLoader?.let { loader ->
            loader.saveBlockedKeywords(_state.value.keywords.toSet())
        }
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
    }

    fun addBlockedWebsite(website: String) {
        if (website.isBlank()) return
        val normalized = website.trim().lowercase()
        _state.update { it.copy(customBlockedWebsites = it.customBlockedWebsites + normalized) }
        boundLoader?.let { loader ->
            loader.saveBlockedWebsites(_state.value.customBlockedWebsites.toSet())
        }
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER)
    }

    fun removeBlockedWebsite(website: String) {
        val normalized = website.trim().lowercase()
        _state.update { it.copy(customBlockedWebsites = it.customBlockedWebsites.filter { s -> s != normalized }) }
        boundLoader?.let { loader ->
            loader.saveBlockedWebsites(_state.value.customBlockedWebsites.toSet())
        }
        sendRefreshBroadcast(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER)
    }

    fun dismissNotification(id: String) {
        _state.update { it.copy(notifications = it.notifications.filter { item -> item.id != id }) }
    }

    fun clearAllNotifications() {
        _state.update { it.copy(notifications = emptyList()) }
    }

    // Modal toggles
    fun setShowAddKeywordModal(show: Boolean) {
        _state.update { it.copy(isShowingAddKeywordModal = show) }
    }

    fun setShowAddAppModal(show: Boolean) {
        _state.update { it.copy(isShowingAddAppModal = show) }
    }

    fun addScheduleRule(rule: ScheduleRule) {
        _state.update { it.copy(scheduleRules = it.scheduleRules + rule) }
        syncRulesToPersistence()
    }

    fun deleteScheduleRule(id: String) {
        _state.update { it.copy(scheduleRules = it.scheduleRules.filter { rule -> rule.id != id }) }
        syncRulesToPersistence()
    }

    fun toggleScheduleRuleActive(id: String) {
        _state.update {
            it.copy(
                scheduleRules = it.scheduleRules.map { rule ->
                    if (rule.id == id) rule.copy(isActive = !rule.isActive) else rule
                }
            )
        }
        syncRulesToPersistence()
    }

    fun updateRuleAuth(
        ruleId: String,
        authType: AuthType,
        rulePasswordHash: String?,
        rulePasswordSalt: String?
    ) {
        _state.update {
            it.copy(
                scheduleRules = it.scheduleRules.map { rule ->
                    if (rule.id == ruleId) {
                        rule.copy(
                            authType = authType,
                            rulePasswordHash = rulePasswordHash,
                            rulePasswordSalt = rulePasswordSalt
                        )
                    } else rule
                }
            )
        }
        syncRulesToPersistence()
    }

    fun updateProfile(
        name: String,
        email: String,
        bio: String,
        goalMinutes: Int,
        profileType: String,
        pinEnabled: Boolean,
        pin: String,
        imageUri: String? = _state.value.profileImageUri,
        avatarId: String = _state.value.profileAvatarId
    ) {
        _state.update {
            it.copy(
                userName = name,
                userEmail = email,
                userBio = bio,
                userGoalMinutes = goalMinutes,
                focusProfileType = profileType,
                isPinProtectionEnabled = pinEnabled,
                profilePin = pin,
                profileImageUri = imageUri,
                profileAvatarId = avatarId
            )
        }
    }

    fun updateProfilePicture(imageUri: String?, avatarId: String) {
        _state.update {
            it.copy(
                profileImageUri = imageUri,
                profileAvatarId = avatarId
            )
        }
    }

    fun setAdvancedMode(enabled: Boolean) {
        _state.update { it.copy(isAdvancedMode = enabled) }
    }

    fun toggleSyncRules() {
        _state.update { it.copy(syncRulesEnabled = !it.syncRulesEnabled) }
    }

    fun toggleSyncAppUsage() {
        _state.update { it.copy(syncAppUsageEnabled = !it.syncAppUsageEnabled) }
    }

    fun toggleSyncWebUsage() {
        _state.update { it.copy(syncWebUsageEnabled = !it.syncWebUsageEnabled) }
    }

    fun toggleSmartRecommendations() {
        _state.update { it.copy(smartRecommendationsEnabled = !it.smartRecommendationsEnabled) }
    }

    fun updateGuardianStats(streakDays: Int, shieldScore: Int, threatsBlocked: Int) {
        _state.update {
            it.copy(
                focusStreakDays = streakDays,
                focusShieldScore = shieldScore,
                totalThreatsBlocked = threatsBlocked
            )
        }
    }
}
