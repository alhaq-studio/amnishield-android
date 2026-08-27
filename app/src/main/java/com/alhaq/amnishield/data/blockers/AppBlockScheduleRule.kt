package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.security.AuthType
import java.util.UUID

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
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule {
    val isRuleEnabled: Boolean
        get() = isEnabled ?: true

    /**
     * Sanitizes an instance that may have been deserialized with missing or null fields via Gson reflection.
     */
    @Suppress("USELESS_ELVIS", "UNNECESSARY_NOT_NULL_ASSERTION")
    fun sanitize(): AppBlockScheduleRule {
        val safeId = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id
        return AppBlockScheduleRule(
            id = safeId,
            title = title ?: "",
            packageName = packageName ?: "",
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
