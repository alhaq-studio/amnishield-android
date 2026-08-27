package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.security.AuthType
import java.util.Calendar
import java.util.UUID

/**
 * Data model for per-app launch/opens limiting rules.
 * Tracks how many times an app can be launched within a specific time period.
 * When the limit is reached, the app is blocked by AppBlocker enforcement logic.
 *
 * @param id Unique identifier (UUID)
 * @param packageName Target app's package name
 * @param maxLaunches Maximum number of launches allowed per period
 * @param timePeriod Time period for limit reset (HOURLY, DAILY, WEEKLY)
 * @param dayOfWeek For WEEKLY: Calendar.MONDAY (2) through SUNDAY (1), ignored for others
 * @param createdAt Timestamp when this rule was created
 * @param isEnabled Whether the launch limit is currently active
 * @param authType Protection level (NONE, GLOBAL_PIN, RULE_PIN)
 * @param rulePasswordHash Salted PBKDF2 hash if protected by RULE_PIN
 * @param rulePasswordSalt Random salt if protected by RULE_PIN
 */
data class AppLaunchLimitRule(
    override val id: String = "",
    val packageName: String = "",
    val maxLaunches: Int = 0,
    val timePeriod: TimePeriod = TimePeriod.DAILY,
    val dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule {

    @Suppress("USELESS_ELVIS", "UNNECESSARY_NOT_NULL_ASSERTION")
    fun sanitize(): AppLaunchLimitRule {
        val safeId = if (id.isNullOrBlank()) UUID.randomUUID().toString() else id
        return AppLaunchLimitRule(
            id = safeId,
            packageName = packageName ?: "",
            maxLaunches = maxLaunches,
            timePeriod = timePeriod ?: TimePeriod.DAILY,
            dayOfWeek = dayOfWeek,
            createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
            isEnabled = isEnabled,
            authType = authType ?: AuthType.NONE,
            rulePasswordHash = rulePasswordHash,
            rulePasswordSalt = rulePasswordSalt
        )
    }

    enum class TimePeriod {
        HOURLY,
        DAILY,
        WEEKLY
    }

    /**
     * Get a human-readable description of this limit rule.
     * Example: "5 launches per day" or "10 launches per week"
     */
    @Suppress("USELESS_ELVIS")
    fun getDescription(): String {
        val period = timePeriod ?: TimePeriod.DAILY
        val periodText = when (period) {
            TimePeriod.HOURLY -> "hour"
            TimePeriod.DAILY -> "day"
            TimePeriod.WEEKLY -> "week"
        }
        return "$maxLaunches launch${if (maxLaunches != 1) "es" else ""} per $periodText"
    }
}
