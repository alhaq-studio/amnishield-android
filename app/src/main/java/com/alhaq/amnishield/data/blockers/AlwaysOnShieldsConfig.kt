package com.alhaq.amnishield.data.blockers

import java.util.Locale

enum class ShieldType(val displayName: String, val pillLabel: String) {
    REELS(displayName = "Short Video & Reels Guard", pillLabel = "Reels Guard"),
    WEBSITE(displayName = "Permanent Website Guard", pillLabel = "Website Guard"),
    KEYWORD(displayName = "Keyword & Search Filter", pillLabel = "Keyword Filter")
}

enum class ShieldLockMode(val label: String, val isProOnly: Boolean) {
    INHERIT_GLOBAL_PIN(label = "Global PIN", isProOnly = false),
    NONE(label = "No Lock", isProOnly = false),
    PAUSE_DELAY(label = "Pause Delay", isProOnly = false),
    CUSTOM_PIN(label = "Custom PIN", isProOnly = true),
    QR_CODE(label = "QR Code", isProOnly = true)
}

enum class ReelsLimitMode {
    ALWAYS_BLOCK_24_7,
    LIMIT_BY_COUNT
}

data class ShieldLockConfig(
    val mode: ShieldLockMode = ShieldLockMode.INHERIT_GLOBAL_PIN,
    val delaySeconds: Int = 30,
    val customPinHash: String? = null,
    val customPinSalt: String? = null,
    val registeredQrValue: String? = null
) {
    fun sanitize(): ShieldLockConfig {
        return copy(
            delaySeconds = delaySeconds.coerceIn(5, 300),
            customPinHash = customPinHash?.trim()?.takeIf { it.isNotEmpty() },
            customPinSalt = customPinSalt?.trim()?.takeIf { it.isNotEmpty() },
            registeredQrValue = registeredQrValue?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Badge text rendered in the Always-On Shield card header when protection is active.
     * Returns null when [mode] is [ShieldLockMode.NONE] or when inheriting a Global PIN that is not yet configured.
     */
    fun activeBadgeLabel(hasGlobalPin: Boolean): String? {
        return when (mode) {
            ShieldLockMode.NONE -> null
            ShieldLockMode.INHERIT_GLOBAL_PIN -> if (hasGlobalPin) "Global PIN" else null
            ShieldLockMode.PAUSE_DELAY -> "${delaySeconds.coerceIn(5, 300)}s Delay"
            ShieldLockMode.CUSTOM_PIN -> if (!customPinHash.isNullOrBlank()) "Custom PIN" else null
            ShieldLockMode.QR_CODE -> if (!registeredQrValue.isNullOrBlank()) "QR" else null
        }
    }

    /**
     * Effective friction strength rank used for asymmetric friction checks when changing lock modes.
     */
    fun strengthRank(hasGlobalPin: Boolean): Int {
        return when (mode) {
            ShieldLockMode.NONE -> 0
            ShieldLockMode.INHERIT_GLOBAL_PIN -> if (hasGlobalPin) 2 else 0
            ShieldLockMode.PAUSE_DELAY -> 1
            ShieldLockMode.CUSTOM_PIN -> if (!customPinHash.isNullOrBlank()) 3 else 0
            ShieldLockMode.QR_CODE -> if (!registeredQrValue.isNullOrBlank()) 3 else 0
        }
    }

    fun isActivelyLocked(hasGlobalPin: Boolean): Boolean = strengthRank(hasGlobalPin) > 0
}

data class ReelsGuardConfig(
    val enabled: Boolean = false,
    val blockYoutubeShorts: Boolean = true,
    val blockInstagramReels: Boolean = true,
    val blockTikTok: Boolean = true,
    val blockInBrowserShorts: Boolean = false,
    val limitMode: ReelsLimitMode = ReelsLimitMode.ALWAYS_BLOCK_24_7,
    val dailyReelLimit: Int = 25,
    val interceptionStyle: ShortformInterceptionStyle = ShortformInterceptionStyle.REDIRECT_TO_FEED,
    val lockConfig: ShieldLockConfig = ShieldLockConfig()
) {
    fun sanitize(): ReelsGuardConfig {
        return copy(
            dailyReelLimit = dailyReelLimit.coerceIn(5, 100),
            lockConfig = lockConfig.sanitize()
        )
    }

    fun activePlatformCount(): Int {
        var count = 0
        if (blockYoutubeShorts) count++
        if (blockInstagramReels) count++
        if (blockTikTok) count++
        if (blockInBrowserShorts) count++
        return count
    }

    fun summarySubtitle(): String {
        if (!enabled) return "Off - Tap to configure short-form protection"
        val platforms = buildList {
            if (blockYoutubeShorts) add("Shorts")
            if (blockInstagramReels) add("Reels")
            if (blockTikTok) add("TikTok")
            if (blockInBrowserShorts) add("Browser")
        }
        val platformText = if (platforms.isEmpty()) "No platforms selected" else platforms.joinToString(", ")
        val modeText = when (limitMode) {
            ReelsLimitMode.ALWAYS_BLOCK_24_7 -> "24/7 Block"
            ReelsLimitMode.LIMIT_BY_COUNT -> "Limit $dailyReelLimit/day"
        }
        return "$platformText - $modeText"
    }
}

data class WebsiteGuardConfig(
    val enabled: Boolean = false,
    val enabledPackIds: Set<String> = emptySet(),
    val customDomains: List<String> = emptyList(),
    val blockUnsupportedBrowsers: Boolean = false,
    val blockIncognitoMode: Boolean = false,
    val interceptionStyle: BlockInterceptionStyle = BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE,
    val lockConfig: ShieldLockConfig = ShieldLockConfig()
) {
    fun sanitize(): WebsiteGuardConfig {
        val validPackIds = PreloadedPacks.websitePacks.map { it.id }.toSet()
        val cleanedPacks = enabledPackIds.filter { it in validPackIds }.toSet()
        val cleanedDomains = customDomains
            .mapNotNull { normalizeDomainInput(it) }
            .distinct()
        return copy(
            enabledPackIds = cleanedPacks,
            customDomains = cleanedDomains,
            lockConfig = lockConfig.sanitize()
        )
    }

    fun effectiveDomains(): Set<String> {
        val result = linkedSetOf<String>()
        PreloadedPacks.websitePacks.forEach { pack ->
            if (pack.id in enabledPackIds) {
                pack.items.forEach { item ->
                    normalizeDomainInput(item)?.let { result.add(it) }
                }
            }
        }
        customDomains.forEach { item ->
            normalizeDomainInput(item)?.let { result.add(it) }
        }
        return result
    }

    fun summarySubtitle(): String {
        if (!enabled) return "Off - Tap to block harmful or distracting sites"
        val packCount = enabledPackIds.size
        val customCount = customDomains.mapNotNull { normalizeDomainInput(it) }.distinct().size
        val parts = buildList {
            if (packCount > 0) add("$packCount blocklist pack${if (packCount == 1) "" else "s"}")
            if (customCount > 0) add("$customCount custom domain${if (customCount == 1) "" else "s"}")
            if (blockUnsupportedBrowsers) add("Unsupported browsers")
            if (blockIncognitoMode) add("Incognito")
        }
        return if (parts.isEmpty()) "On - Tap to add blocklists or domains" else parts.joinToString(" - ")
    }

    companion object {
        fun normalizeDomainInput(raw: String): String? {
            val cleaned = raw.trim()
                .lowercase(Locale.ROOT)
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
            return if (cleaned.length >= 3 && cleaned.contains(".") && !cleaned.contains(" ")) {
                cleaned
            } else {
                null
            }
        }
    }
}

data class KeywordGuardConfig(
    val enabled: Boolean = false,
    val enabledPackIds: Set<String> = emptySet(),
    val customKeywords: List<String> = emptyList(),
    val ignoredPackages: Set<String> = emptySet(),
    val wholeWordOnly: Boolean = true,
    val scanAllTextFields: Boolean = false,
    val interceptionStyle: BlockInterceptionStyle = BlockInterceptionStyle.STANDARD_WARNING_DIALOG,
    val lockConfig: ShieldLockConfig = ShieldLockConfig()
) {
    fun sanitize(): KeywordGuardConfig {
        val validPackIds = PreloadedPacks.keywordPacks.map { it.id }.toSet()
        val cleanedPacks = enabledPackIds.filter { it in validPackIds }.toSet()
        val cleanedKeywords = customKeywords
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.length >= 2 }
            .distinct()
        val cleanedIgnored = ignoredPackages
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return copy(
            enabledPackIds = cleanedPacks,
            customKeywords = cleanedKeywords,
            ignoredPackages = cleanedIgnored,
            lockConfig = lockConfig.sanitize()
        )
    }

    fun effectiveKeywords(): Set<String> {
        val result = linkedSetOf<String>()
        PreloadedPacks.keywordPacks.forEach { pack ->
            if (pack.id in enabledPackIds) {
                pack.items.forEach { item ->
                    val cleaned = item.trim().lowercase(Locale.ROOT)
                    if (cleaned.length >= 2) result.add(cleaned)
                }
            }
        }
        customKeywords.forEach { item ->
            val cleaned = item.trim().lowercase(Locale.ROOT)
            if (cleaned.length >= 2) result.add(cleaned)
        }
        return result
    }

    fun summarySubtitle(): String {
        if (!enabled) return "Off - Tap to filter blocked search terms"
        val kwCount = effectiveKeywords().size
        val packCount = enabledPackIds.size
        return buildString {
            append("$kwCount keyword${if (kwCount == 1) "" else "s"}")
            if (packCount > 0) {
                append(" ($packCount pack${if (packCount == 1) "" else "s"})")
            }
            if (ignoredPackages.isNotEmpty()) {
                append(" - ${ignoredPackages.size} whitelisted")
            }
        }
    }
}

data class AlwaysOnShieldsConfig(
    val reels: ReelsGuardConfig = ReelsGuardConfig(),
    val website: WebsiteGuardConfig = WebsiteGuardConfig(),
    val keyword: KeywordGuardConfig = KeywordGuardConfig()
) {
    fun sanitize(): AlwaysOnShieldsConfig {
        return copy(
            reels = reels.sanitize(),
            website = website.sanitize(),
            keyword = keyword.sanitize()
        )
    }

    fun activeShieldTypes(): List<ShieldType> {
        return buildList {
            if (reels.enabled) add(ShieldType.REELS)
            if (website.enabled) add(ShieldType.WEBSITE)
            if (keyword.enabled) add(ShieldType.KEYWORD)
        }
    }

    fun activeCount(): Int = activeShieldTypes().size

    fun lockConfigFor(type: ShieldType): ShieldLockConfig {
        return when (type) {
            ShieldType.REELS -> reels.lockConfig
            ShieldType.WEBSITE -> website.lockConfig
            ShieldType.KEYWORD -> keyword.lockConfig
        }
    }
}
