package com.alhaq.amnishield

import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.data.blockers.UniversalScheduleRule
import org.junit.Assert.*
import org.junit.Test

class UnifiedScheduleRuleTest {

    @Test
    fun testUniversalScheduleRuleDomainIsolation() {
        val appRule: UniversalScheduleRule = UniversalScheduleRule(
            id = "rule_app",
            title = "Block Social",
            blockerType = BlockerType.APP,
            targets = listOf("com.instagram.android", "com.tiktok.android")
        ).sanitize()

        val websiteRule: UniversalScheduleRule = UniversalScheduleRule(
            id = "rule_web",
            title = "Block Video Sites",
            blockerType = BlockerType.WEBSITE,
            targets = listOf("youtube.com", "twitch.tv")
        ).sanitize()

        val keywordRule: UniversalScheduleRule = UniversalScheduleRule(
            id = "rule_kw",
            title = "Block Adult Terms",
            blockerType = BlockerType.KEYWORD,
            targets = listOf("explicit_word_1", "explicit_word_2")
        ).sanitize()

        assertEquals(BlockerType.APP, appRule.blockerType)
        assertEquals(listOf("com.instagram.android", "com.tiktok.android"), appRule.targets)

        assertEquals(BlockerType.WEBSITE, websiteRule.blockerType)
        assertEquals(listOf("youtube.com", "twitch.tv"), websiteRule.targets)
        assertEquals(listOf("youtube.com", "twitch.tv"), websiteRule.targetWebsites)

        assertEquals(BlockerType.KEYWORD, keywordRule.blockerType)
        assertEquals(listOf("explicit_word_1", "explicit_word_2"), keywordRule.targets)
        assertEquals(listOf("explicit_word_1", "explicit_word_2"), keywordRule.targetKeywords)
    }

    @Test
    fun testAppBlockScheduleRuleTypealiasInterchangeability() {
        // Confirm that AppBlockScheduleRule and UniversalScheduleRule can be used interchangeably
        val rule: AppBlockScheduleRule = UniversalScheduleRule(
            id = "test_alias",
            title = "Alias Test",
            blockerType = BlockerType.KEYWORD,
            targets = listOf("distraction")
        )

        val sanitized: UniversalScheduleRule = rule.sanitize()
        assertEquals(BlockerType.KEYWORD, sanitized.blockerType)
        assertEquals(listOf("distraction"), sanitized.targets)
    }
}
