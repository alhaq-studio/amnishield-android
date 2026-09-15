package com.alhaq.amnishield

import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class LegacyRuleDeserializationSafetyTest {

    @Test
    fun `test legacy AppBlockScheduleRule json without authType or selectedDays sanitizes safely and allows copy without NPE`() {
        val legacyJson = """
            [
                {
                    "id": "rule_123",
                    "title": "Legacy Instagram Rule",
                    "packageName": "com.instagram.android",
                    "startMinute": 540,
                    "endMinute": 1020
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<AppBlockScheduleRule>>() {}.type
        val rawList: List<AppBlockScheduleRule> = Gson().fromJson(legacyJson, type)

        assertEquals(1, rawList.size)
        val rawRule = rawList.first()

        // Test that sanitize() creates a safe, completely non-null object
        val sanitized = rawRule.sanitize()
        assertEquals("rule_123", sanitized.id)
        assertEquals("Legacy Instagram Rule", sanitized.title)
        assertEquals("com.instagram.android", sanitized.packageName)
        assertEquals(AppBlockScheduleRule.RuleType.BLOCK, sanitized.type)
        assertEquals(AppBlockScheduleRule.Recurrence.DAILY, sanitized.recurrence)
        assertEquals(AuthType.NONE, sanitized.authType)
        assertNotNull(sanitized.selectedDays)

        // Verify copy() does not throw NPE
        val updated = sanitized.copy(isEnabled = false)
        assertFalse(updated.isRuleEnabled)
        assertEquals(AuthType.NONE, updated.authType)
    }

    @Test
    fun `test legacy AppLaunchLimitRule json without authType sanitizes safely and allows copy without NPE`() {
        val legacyJson = """
            [
                {
                    "id": "limit_456",
                    "packageName": "com.tiktok.android",
                    "maxLaunches": 5
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<AppLaunchLimitRule>>() {}.type
        val rawList: List<AppLaunchLimitRule> = Gson().fromJson(legacyJson, type)

        assertEquals(1, rawList.size)
        val rawRule = rawList.first()

        val sanitized = rawRule.sanitize()
        assertEquals("limit_456", sanitized.id)
        assertEquals("com.tiktok.android", sanitized.packageName)
        assertEquals(5, sanitized.maxLaunches)
        assertEquals(AppLaunchLimitRule.TimePeriod.DAILY, sanitized.timePeriod)
        assertEquals(AuthType.NONE, sanitized.authType)

        val updated = sanitized.copy(isEnabled = false)
        assertFalse(updated.isEnabled)
    }

    @Test
    fun `test legacy ScheduleRule json sanitizes safely and allows copy without NPE`() {
        val legacyJson = """
            {
                "id": "sched_789",
                "name": "Old Focus Rule"
            }
        """.trimIndent()

        val rawRule: ScheduleRule = Gson().fromJson(legacyJson, ScheduleRule::class.java)

        val sanitized = rawRule.sanitize()
        assertEquals("sched_789", sanitized.id)
        assertEquals("Old Focus Rule", sanitized.name)
        assertEquals(AuthType.NONE, sanitized.authType)
        assertNotNull(sanitized.days)
        assertNotNull(sanitized.periods)

        val updated = sanitized.copy(isActive = false)
        assertFalse(updated.isActive)
    }

    @Test
    fun `test legacy rules automatically map to BlockerType and targets during sanitization`() {
        val legacyRulesJson = """
            [
                {
                    "id": "web_1",
                    "title": "Block Social Sites",
                    "packageName": "website_blocker",
                    "targetWebsites": ["reddit.com", "x.com"]
                },
                {
                    "id": "kw_1",
                    "title": "Block Explicit Terms",
                    "packageName": "keyword_blocker"
                },
                {
                    "id": "app_1",
                    "title": "Block Instagram",
                    "packageName": "com.instagram.android"
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<AppBlockScheduleRule>>() {}.type
        val rawList: List<AppBlockScheduleRule> = Gson().fromJson(legacyRulesJson, type)

        assertEquals(3, rawList.size)

        // 1. Website Blocker mapping
        val webRule = rawList[0].sanitize()
        assertEquals(com.alhaq.amnishield.data.blockers.BlockerType.WEBSITE, webRule.blockerType)
        assertEquals(listOf("reddit.com", "x.com"), webRule.targets)
        assertEquals(listOf("reddit.com", "x.com"), webRule.targetWebsites)

        // 2. Keyword Blocker mapping
        val kwRule = rawList[1].sanitize()
        assertEquals(com.alhaq.amnishield.data.blockers.BlockerType.KEYWORD, kwRule.blockerType)
        assertTrue(kwRule.targets.isEmpty())

        // 3. App Blocker mapping
        val appRule = rawList[2].sanitize()
        assertEquals(com.alhaq.amnishield.data.blockers.BlockerType.APP, appRule.blockerType)
        assertEquals(listOf("com.instagram.android"), appRule.targets)
    }

    @Test
    fun `test modern UniversalScheduleRule with BlockerType and targets initializes and sanitizes cleanly`() {
        val modernRule = com.alhaq.amnishield.data.blockers.UniversalScheduleRule(
            id = "kw_modern_1",
            title = "Strict Keyword Shield",
            blockerType = com.alhaq.amnishield.data.blockers.BlockerType.KEYWORD,
            targets = listOf("casino", "betting"),
            recurrence = AppBlockScheduleRule.Recurrence.ALWAYS
        )

        val sanitized = modernRule.sanitize()
        assertEquals(com.alhaq.amnishield.data.blockers.BlockerType.KEYWORD, sanitized.blockerType)
        assertEquals(listOf("casino", "betting"), sanitized.targets)
        assertEquals(listOf("casino", "betting"), sanitized.targetKeywords)
        assertEquals("keyword_blocker", sanitized.packageName)
    }
}
