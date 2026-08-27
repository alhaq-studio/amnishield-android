package com.alhaq.amnishield.data.db

import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthType

/**
 * Entity representing a Screen Time or App Launch limit rule.
 *
 * @param id Unique identifier (UUID)
 * @param packageName Target package name
 * @param maxLaunches Maximum allowed launches per period
 * @param timePeriod Period: "HOURLY", "DAILY", or "WEEKLY"
 * @param dayOfWeek Calendar day of week for weekly rules
 * @param limitMinutes Allowed screen time in minutes (0 if not applicable)
 * @param createdAt Creation timestamp
 * @param isEnabled Whether the limit is active
 * @param authType Rule protection level (NONE, GLOBAL_PIN, RULE_PIN)
 * @param rulePasswordHash Salted PBKDF2 hash (if protected by RULE_PIN)
 * @param rulePasswordSalt Random salt (if protected by RULE_PIN)
 */
data class ScreenTimeLimitRule(
    override val id: String,
    val packageName: String,
    val maxLaunches: Int = 0,
    val timePeriod: String = "DAILY",
    val dayOfWeek: Int = 0,
    val limitMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isEnabled: Boolean = true,
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule
