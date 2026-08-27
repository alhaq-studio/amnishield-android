package com.alhaq.amnishield.data.db

import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthType

/**
 * Entity representing an App Blocking rule (Schedule, Always Block, Cheat Window).
 *
 * @param id Unique identifier (UUID)
 * @param title Human-readable rule title
 * @param packageName Target package name
 * @param type Rule type: "BLOCK" or "CHEAT"
 * @param recurrence "ALWAYS", "DAILY", "WEEKLY", or "HOURLY"
 * @param startMinute Start minute of the day (0..1439)
 * @param endMinute End minute of the day (0..1439)
 * @param selectedDays Comma-separated active days of the week (e.g., "1,2,3,4,5")
 * @param durationHours Duration in hours for timed blocks
 * @param activeUntilMillis Timestamp until rule is active
 * @param createdAt Creation timestamp
 * @param groupId Optional group ID for batch rules
 * @param groupTitle Optional group title
 * @param isEnabled Whether the rule is currently active
 * @param authType Rule protection level (NONE, GLOBAL_PIN, RULE_PIN)
 * @param rulePasswordHash Salted PBKDF2 hash (if protected by RULE_PIN)
 * @param rulePasswordSalt Random salt (if protected by RULE_PIN)
 */
data class AppBlockRule(
    override val id: String,
    val title: String,
    val packageName: String,
    val type: String = "BLOCK",
    val recurrence: String = "ALWAYS",
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val selectedDays: String = "",
    val durationHours: Int = 0,
    val activeUntilMillis: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: String? = null,
    val groupTitle: String? = null,
    val isEnabled: Boolean = true,
    override val authType: AuthType = AuthType.NONE,
    override val rulePasswordHash: String? = null,
    override val rulePasswordSalt: String? = null
) : BaseRule
