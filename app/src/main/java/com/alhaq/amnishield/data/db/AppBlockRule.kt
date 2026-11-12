package com.alhaq.amnishield.data.db

import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.BaseRule
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule

/**
 * Entity representing an App Blocking rule (Schedule, Always Block, Cheat Window).
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
) : BaseRule {

    fun toAppBlockScheduleRule(): AppBlockScheduleRule {
        return AppBlockScheduleRule(
            id = id,
            title = title,
            packageName = packageName,
            type = if (type == "CHEAT") AppBlockScheduleRule.RuleType.CHEAT else AppBlockScheduleRule.RuleType.BLOCK,
            recurrence = AppBlockScheduleRule.Recurrence.DAILY,
            startMinute = startMinute,
            endMinute = endMinute,
            selectedDays = if (selectedDays.isNotBlank()) selectedDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet() else emptySet(),
            durationHours = durationHours,
            activeUntilMillis = activeUntilMillis,
            createdAt = createdAt,
            groupId = groupId,
            groupTitle = groupTitle,
            isEnabled = isEnabled,
            authType = authType,
            rulePasswordHash = rulePasswordHash,
            rulePasswordSalt = rulePasswordSalt
        )
    }

    fun toScheduleRule(): ScheduleRule {
        val startH = startMinute / 60
        val startM = startMinute % 60
        val endH = endMinute / 60
        val endM = endMinute % 60
        val daysList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return ScheduleRule(
            id = id,
            name = title,
            appOrCategory = packageName,
            restrictionType = if (type == "CHEAT") "Cheat Window" else "Block Schedule",
            startTime = String.format("%02d:%02d", startH, startM),
            endTime = String.format("%02d:%02d", endH, endM),
            days = daysList,
            isActive = isEnabled,
            selectedApps = if (packageName.isNotBlank()) listOf(packageName) else emptyList(),
            authType = authType,
            rulePasswordHash = rulePasswordHash,
            rulePasswordSalt = rulePasswordSalt
        )
    }

    companion object {
        fun fromAppBlockScheduleRule(rule: AppBlockScheduleRule): AppBlockRule {
            return AppBlockRule(
                id = rule.id,
                title = rule.title,
                packageName = rule.packageName,
                type = rule.type.name,
                recurrence = rule.recurrence.name,
                startMinute = rule.startMinute,
                endMinute = rule.endMinute,
                selectedDays = rule.selectedDays.joinToString(","),
                durationHours = rule.durationHours,
                activeUntilMillis = rule.activeUntilMillis,
                createdAt = rule.createdAt,
                groupId = rule.groupId,
                groupTitle = rule.groupTitle,
                isEnabled = rule.isRuleEnabled,
                authType = rule.authType,
                rulePasswordHash = rule.rulePasswordHash,
                rulePasswordSalt = rule.rulePasswordSalt
            )
        }

        fun fromScheduleRule(rule: ScheduleRule): AppBlockRule {
            val startParts = rule.startTime.split(":")
            val startMin = if (startParts.size == 2) (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0) else 0
            val endParts = rule.endTime.split(":")
            val endMin = if (endParts.size == 2) (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0) else 0

            return AppBlockRule(
                id = rule.id,
                title = rule.name,
                packageName = if (rule.selectedApps.isNotEmpty()) rule.selectedApps.first() else rule.appOrCategory,
                type = if (rule.restrictionType == "Cheat Window") "CHEAT" else "BLOCK",
                recurrence = "DAILY",
                startMinute = startMin,
                endMinute = endMin,
                selectedDays = "1,2,3,4,5,6,7",
                durationHours = 0,
                activeUntilMillis = 0L,
                createdAt = System.currentTimeMillis(),
                groupId = null,
                groupTitle = null,
                isEnabled = rule.isActive,
                authType = rule.authType,
                rulePasswordHash = rule.rulePasswordHash,
                rulePasswordSalt = rule.rulePasswordSalt
            )
        }
    }
}
