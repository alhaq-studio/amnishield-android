package com.alhaq.amnishield.ui.screens

import com.alhaq.amnishield.data.blockers.AlwaysOnShieldsConfig
import com.alhaq.amnishield.data.blockers.WebsiteGuardConfig

/**
 * Pure state helpers for AmniShield's multi-step onboarding flow.
 * Bridges the Step 2 "Quick Shields" starters with [AlwaysOnShieldsConfig],
 * validates Step 3 PIN setup, and formats daily screen-time goals.
 */
data class OnboardingQuickShieldState(
    val blockInstagramReels: Boolean = false,
    val blockYoutubeShorts: Boolean = false,
    val blockTikTok: Boolean = false,
    val blockBrowserReels: Boolean = false,
    val blockAdultSites: Boolean = false,
    val blockGamblingSites: Boolean = false
) {
    fun hasAnyReelsSelected(): Boolean =
        blockInstagramReels || blockYoutubeShorts || blockTikTok || blockBrowserReels

    fun activeShieldCount(): Int {
        var count = 0
        if (blockInstagramReels) count++
        if (blockYoutubeShorts) count++
        if (blockTikTok) count++
        if (blockBrowserReels) count++
        if (blockAdultSites) count++
        if (blockGamblingSites) count++
        return count
    }

    fun summaryText(): String {
        val parts = buildList {
            if (blockInstagramReels) add("Instagram Reels")
            if (blockYoutubeShorts) add("YouTube Shorts")
            if (blockTikTok) add("TikTok")
            if (blockBrowserReels) add("Browser Shorts")
            if (blockAdultSites) add("Adult Web Guard")
            if (blockGamblingSites) add("Gambling Web Guard")
        }
        return if (parts.isEmpty()) {
            "Configure rules anytime in Blocks"
        } else {
            parts.joinToString(", ")
        }
    }

    /**
     * Merges the user's onboarding quick-shield selections into [current] without clobbering
     * existing custom domains, custom keywords, or friction lock preferences.
     */
    fun applyToConfig(current: AlwaysOnShieldsConfig): AlwaysOnShieldsConfig {
        val reelsAny = hasAnyReelsSelected()
        val updatedReels = current.reels.copy(
            enabled = reelsAny,
            blockInstagramReels = blockInstagramReels,
            blockYoutubeShorts = blockYoutubeShorts,
            blockTikTok = blockTikTok,
            blockInBrowserShorts = blockBrowserReels
        )

        val updatedPackIds = current.website.enabledPackIds.toMutableSet()
        if (blockAdultSites) {
            updatedPackIds.add(WEB_ADULT_PACK_ID)
        } else {
            updatedPackIds.remove(WEB_ADULT_PACK_ID)
        }
        if (blockGamblingSites) {
            updatedPackIds.add(WEB_GAMBLING_PACK_ID)
        } else {
            updatedPackIds.remove(WEB_GAMBLING_PACK_ID)
        }

        val hasOtherWebsiteSignals = current.website.customDomains
            .any { WebsiteGuardConfig.normalizeDomainInput(it) != null } ||
            current.website.blockUnsupportedBrowsers ||
            current.website.blockIncognitoMode

        val updatedWebsite = current.website.copy(
            enabled = updatedPackIds.isNotEmpty() || hasOtherWebsiteSignals,
            enabledPackIds = updatedPackIds
        )

        return current.copy(
            reels = updatedReels,
            website = updatedWebsite
        ).sanitize()
    }

    companion object {
        const val WEB_ADULT_PACK_ID = "web_adult_blocklist"
        const val WEB_GAMBLING_PACK_ID = "web_gambling_blocklist"

        fun fromConfig(config: AlwaysOnShieldsConfig): OnboardingQuickShieldState {
            val reelsOn = config.reels.enabled
            return OnboardingQuickShieldState(
                blockInstagramReels = reelsOn && config.reels.blockInstagramReels,
                blockYoutubeShorts = reelsOn && config.reels.blockYoutubeShorts,
                blockTikTok = reelsOn && config.reels.blockTikTok,
                blockBrowserReels = reelsOn && config.reels.blockInBrowserShorts,
                blockAdultSites = config.website.enabled && WEB_ADULT_PACK_ID in config.website.enabledPackIds,
                blockGamblingSites = config.website.enabled && WEB_GAMBLING_PACK_ID in config.website.enabledPackIds
            )
        }
    }
}

data class OnboardingPinDraft(
    val pin: String = "",
    val confirmPin: String = "",
    val cooldownMinutes: Int = 5,
    val lockSettingsWithPin: Boolean = true
) {
    fun isPinValidFormat(): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun isMatching(): Boolean = isPinValidFormat() && pin == confirmPin

    fun hasMismatchError(): Boolean = confirmPin.length == 4 && pin != confirmPin
}

fun formatDailyGoalDuration(minutes: Int): String {
    val safe = minutes.coerceIn(30, 480)
    val hours = safe / 60
    val mins = safe % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

fun resolveDailyGoalMood(minutes: Int): String {
    return when {
        minutes <= 90 -> "Deep Focus Mode"
        minutes <= 180 -> "Balanced Digital Life"
        minutes <= 300 -> "Moderate Routine"
        else -> "Flexible Schedule"
    }
}
