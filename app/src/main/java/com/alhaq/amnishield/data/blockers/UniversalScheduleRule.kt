package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.security.AuthType
import java.util.UUID

/**
 * Universal schedule rule model for all blocker domains across the AmniShield Unified Rule System:
 * - Applications (APP)
 * - Websites & Web Domains (WEBSITE)
 * - Search & Content Keywords (KEYWORD)
 * - Short-form Video Feeds (REELS)
 * - Focus Mode Sessions (FOCUS_MODE)
 */
data class AppBlockScheduleRule(
    override val id: String = "",
    val title: String = "",
    val packageName: String = "",
    val type: RuleType = RuleType.BLOCK,
    val recurrence: Recurrence = Recurrence.DAILY,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val selectedDays: Set<Int> = emptySet(),
    val durationHours: Int = 0,
    val activeUntilMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: String? = null,
    val groupTitle: String? = null,
    val isEnabled: Boolean? = true,
    val targetWebsites: List<String> = emptyList(),
    val targetKeywords: List<String> = emptyList(),
    val blockerType: BlockerType = BlockerType.APP,
    val targets: List<String> = emptyList(),
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule {
    val isRuleEnabled: Boolean
        get() = isEnabled ?: true

    /**
     * Sanitizes an instance that may have been deserialized with missing or null fields via Gson reflection.
     * Guarantees 100% backward compatibility for legacy JSON records.
     */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_NOT_NULL_ASSERTION", "UNCHECKED_CAST")
    fun sanitize(): AppBlockScheduleRule {
        val safeId = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id
        val safePackage = packageName ?: ""

        val rawTargets = (targets as Any?) as? List<String>
        val rawWebsites = (targetWebsites as Any?) as? List<String>
        val rawKeywords = (targetKeywords as Any?) as? List<String>
        val rawBlockerType = (blockerType as Any?) as? BlockerType

        val inferredType = BlockerType.fromPackageName(safePackage)
        val safeBlockerType = when {
            rawBlockerType != null && rawBlockerType != BlockerType.APP -> rawBlockerType
            inferredType != BlockerType.APP -> inferredType
            else -> rawBlockerType ?: BlockerType.APP
        }

        // Deterministically resolve targets across unified and legacy fields
        val resolvedTargets: List<String> = when {
            !rawTargets.isNullOrEmpty() -> rawTargets
            safeBlockerType == BlockerType.WEBSITE && !rawWebsites.isNullOrEmpty() -> rawWebsites
            safeBlockerType == BlockerType.KEYWORD && !rawKeywords.isNullOrEmpty() -> rawKeywords
            safeBlockerType == BlockerType.APP && safePackage.isNotBlank() &&
                safePackage != "website_blocker" &&
                safePackage != "keyword_blocker" &&
                safePackage != "reel_blocker" &&
                !safePackage.equals("FOCUS_MODE", ignoreCase = true) &&
                !safePackage.equals("focus_mode", ignoreCase = true) -> listOf(safePackage)
            else -> rawTargets ?: emptyList()
        }

        // Keep backward-compatible properties synchronized
        val safeWebsites = if (safeBlockerType == BlockerType.WEBSITE) resolvedTargets else (rawWebsites ?: emptyList())
        val safeKeywords = if (safeBlockerType == BlockerType.KEYWORD) resolvedTargets else (rawKeywords ?: emptyList())
        val effectivePackage = if (safePackage.isBlank()) safeBlockerType.packageNameIdentifier else safePackage

        return AppBlockScheduleRule(
            id = safeId,
            title = title ?: "",
            packageName = effectivePackage,
            type = type ?: RuleType.BLOCK,
            recurrence = recurrence ?: Recurrence.DAILY,
            startMinute = startMinute,
            endMinute = endMinute,
            selectedDays = selectedDays ?: emptySet(),
            durationHours = durationHours,
            activeUntilMillis = activeUntilMillis,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
            groupId = groupId,
            groupTitle = groupTitle,
            isEnabled = isEnabled ?: true,
            targetWebsites = safeWebsites,
            targetKeywords = safeKeywords,
            blockerType = safeBlockerType,
            targets = resolvedTargets,
            authType = authType ?: AuthType.NONE,
            rulePasswordHash = rulePasswordHash,
            rulePasswordSalt = rulePasswordSalt
        )
    }

    enum class RuleType {
        BLOCK,
        CHEAT
    }

    enum class Recurrence {
        HOURLY,
        DAILY,
        WEEKLY,
        ALWAYS
    }
}

/**
 * Universal schedule rule alias representing all blocker domains (APP, WEBSITE, KEYWORD, REELS, FOCUS_MODE).
 */
typealias UniversalScheduleRule = AppBlockScheduleRule
