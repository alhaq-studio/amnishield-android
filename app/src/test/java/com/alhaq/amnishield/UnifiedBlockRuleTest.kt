package com.alhaq.amnishield

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.ui.screens.resolveBlockRuleForDraftOrSave
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class UnifiedBlockRuleTest {

    @Test
    fun `sanitize normalizes domains keywords and default pause config`() {
        val raw = UnifiedBlockRule(
            id = "",
            name = "   ",
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY),
            blockedDomains = listOf("  YouTube.com ", "youtube.com"),
            blockedKeywords = listOf("  GAMING ", "gaming"),
            pauseOption = PauseOptionConfig(delaySeconds = 1, maxPauseDurationMinutes = 0)
        )
        val clean = raw.sanitize()
        assertTrue(clean.id.isNotBlank())
        assertEquals("Block", clean.name)
        assertEquals(listOf("youtube.com"), clean.blockedDomains)
        assertEquals(listOf("gaming"), clean.blockedKeywords)
        assertEquals(3, clean.pauseOption.delaySeconds)
        assertEquals(1, clean.pauseOption.maxPauseDurationMinutes)
        assertEquals("Reels", UnifiedBlockRule.shortformLabelForPackage("com.instagram.android"))
        assertEquals("Shorts", UnifiedBlockRule.shortformLabelForPackage("com.google.android.youtube"))
        assertEquals("TikTok Feed", UnifiedBlockRule.shortformLabelForPackage("com.zhiliaoapp.musically"))
        assertFalse(UnifiedBlockRule.isShortformCapable("com.whatsapp"))
    }

    @Test
    fun `monotonic pause verification resists wall clock rollback`() {
        val startElapsed = 100_000L
        val startWall = 1_700_000_000_000L
        val durationMs = 15 * 60_000L

        val pausedRule = UnifiedBlockRule(
            id = "rule_sleep",
            name = "Sleep",
            status = BlockRuleStatus.PAUSED_UNTIL,
            pausedUntilElapsedRealtime = startElapsed + durationMs,
            pausedUntilWallClockMillis = startWall + durationMs,
            appTargets = mapOf("com.google.android.youtube" to AppInterceptionLevel.BLOCK_ENTIRE_APP)
        )

        assertTrue(pausedRule.isPaused(startElapsed, startWall))
        assertEquals(15, pausedRule.remainingPauseMinutes(startElapsed, startWall))
        assertFalse(pausedRule.isUnconditionalOrScheduledActiveNow(startElapsed, startWall))

        // Once monotonic clock advances past pause window, rule is no longer paused even if wall clock is rolled back
        val afterElapsed = startElapsed + 16 * 60_000L
        val rolledBackWall = startWall - 60_000L
        assertFalse(pausedRule.isPaused(afterElapsed, rolledBackWall))
        assertEquals(0, pausedRule.remainingPauseMinutes(afterElapsed, rolledBackWall))
    }

    @Test
    fun `overnight weekly schedule window evaluates evening and morning accurately`() {
        val overnightTrigger = WeeklyScheduleTrigger(
            daysOfWeek = setOf(Calendar.MONDAY),
            startTimeMinutes = 22 * 60, // 22:00
            endTimeMinutes = 7 * 60     // 07:00
        )

        val mondayEvening = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 15)
        }.timeInMillis

        val tuesdayMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
        }.timeInMillis

        val tuesdayAfternoon = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        assertTrue(overnightTrigger.isCurrentlyInWindow(mondayEvening))
        assertTrue(overnightTrigger.isCurrentlyInWindow(tuesdayMorning))
        assertFalse(overnightTrigger.isCurrentlyInWindow(tuesdayAfternoon))
    }

    @Test
    fun `statusSubtitle formats manual and scheduled states`() {
        val manualInactive = UnifiedBlockRule(
            name = "Block",
            status = BlockRuleStatus.INACTIVE_MANUAL
        )
        assertEquals("Activate manually", manualInactive.statusSubtitle(1000L, 1000L))

        val scheduledInactive = UnifiedBlockRule(
            name = "Sleep",
            status = BlockRuleStatus.INACTIVE_MANUAL,
            weeklySchedules = listOf(
                WeeklyScheduleTrigger(startTimeMinutes = 22 * 60, endTimeMinutes = 7 * 60)
            )
        )
        assertEquals("Activating 22:00", scheduledInactive.statusSubtitle(1000L, 1000L))
    }

    @Test
    fun `warning style fields and in-browser shortform sanitize and summarize accurately`() {
        val rule = UnifiedBlockRule(
            name = "Focus Block",
            includeInBrowserShortform = true,
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY),
            interceptionStyle = BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE,
            shortformInterceptionStyle = ShortformInterceptionStyle.EXIT_TO_HOME,
            customWarningMessage = "  Back to deep work!  ",
            closeDelaySeconds = 99
        ).sanitize()

        assertTrue(rule.includeInBrowserShortform)
        assertEquals(BlockInterceptionStyle.MINDFUL_BREATHING_PAUSE, rule.interceptionStyle)
        assertEquals(ShortformInterceptionStyle.EXIT_TO_HOME, rule.shortformInterceptionStyle)
        assertEquals("Back to deep work!", rule.customWarningMessage)
        assertEquals(60, rule.closeDelaySeconds)
        assertTrue(rule.warningSummarySubtitle().contains("Mindful Breathing"))
        assertTrue(rule.warningSummarySubtitle().contains("Exit to Android Home"))
    }

    @Test
    fun `preloaded website and keyword packs match exact built-in counts`() {
        val adultSitesPack = PreloadedPacks.websitePacks.first { it.id == "web_adult_blocklist" }
        val gamblingSitesPack = PreloadedPacks.websitePacks.first { it.id == "web_gambling_blocklist" }
        val adultKeywordsPack = PreloadedPacks.keywordPacks.first { it.id == "kw_adult" }

        assertEquals(51, adultSitesPack.items.size)
        assertEquals(40, gamblingSitesPack.items.size)
        assertEquals(26, adultKeywordsPack.items.size)
    }

    @Test
    fun `expired PAUSED_UNTIL rule resumes active enforcement without disk reload`() {
        val startElapsed = 100_000L
        val startWall = 1_700_000_000_000L
        val pauseEndElapsed = startElapsed + 15 * 60_000L
        val pauseEndWall = startWall + 15 * 60_000L

        val manualPausedRule = UnifiedBlockRule(
            id = "rule_manual_paused",
            name = "Manual Block",
            status = BlockRuleStatus.PAUSED_UNTIL,
            pausedUntilElapsedRealtime = pauseEndElapsed,
            pausedUntilWallClockMillis = pauseEndWall,
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_ENTIRE_APP),
            blockedDomains = listOf("example.com"),
            blockedKeywords = listOf("distraction")
        )

        assertFalse(manualPausedRule.isEffectivelyActive(startElapsed, startWall))
        assertFalse(manualPausedRule.isUnconditionalOrScheduledActiveNow(startElapsed, startWall))
        assertFalse(manualPausedRule.isActiveOrScheduledForContentNow(startElapsed, startWall))
        assertEquals("Paused (15m remaining)", manualPausedRule.statusSubtitle(startElapsed, startWall))

        // After monotonic pause expires, engine and subtitle must treat it as ACTIVE immediately
        val afterElapsed = pauseEndElapsed + 1_000L
        val afterWall = pauseEndWall + 1_000L
        assertTrue(manualPausedRule.isEffectivelyActive(afterElapsed, afterWall))
        assertTrue(manualPausedRule.isUnconditionalOrScheduledActiveNow(afterElapsed, afterWall))
        assertTrue(manualPausedRule.isActiveOrScheduledForContentNow(afterElapsed, afterWall))
        assertEquals("Blocking now (Manual)", manualPausedRule.statusSubtitle(afterElapsed, afterWall))
    }

    @Test
    fun `syncUnifiedRulesToLegacyEngines includes expired PAUSED_UNTIL rules in legacy engine targets`() {
        val prefsMap = mutableMapOf<String, InMemorySharedPreferences>()
        val testContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.deenshield"
            @Synchronized
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }
        }
        val expiredRule = UnifiedBlockRule(
            id = "expired_pause_rule",
            name = "Social Shield",
            status = BlockRuleStatus.PAUSED_UNTIL,
            pausedUntilElapsedRealtime = 50_000L,
            pausedUntilWallClockMillis = 1_700_000_050_000L,
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_ENTIRE_APP),
            blockedDomains = listOf("reddit.com"),
            blockedKeywords = listOf("spoilers")
        )
        val loader = SavedPreferencesLoader(
            context = testContext,
            elapsedRealtimeProvider = { 100_000L },
            wallClockProvider = { 1_700_000_100_000L }
        )

        loader.syncUnifiedRulesToLegacyEngines(listOf(expiredRule))

        assertTrue(loader.loadBlockedApps().contains("com.instagram.android"))
        assertTrue(loader.loadBlockedWebsites().contains("reddit.com"))
        assertTrue(loader.loadBlockedKeywords().contains("spoilers"))
    }

    @Test
    fun `resolveBlockRuleForDraftOrSave keeps blank name during draft and activates scheduled rule on save`() {
        // Step 1: User opens SelectTargets before naming or scheduling -> draft keeps blank name
        val draftBeforeTargets = resolveBlockRuleForDraftOrSave(
            initialRule = null,
            existingRulesForCopy = emptyList(),
            ruleName = "",
            appTargets = emptyMap(),
            blockedDomains = emptyList(),
            blockedKeywords = emptyList(),
            blockNewlyInstalled = false,
            includeInBrowserShortform = false,
            blockAppLaunch = true,
            blockNotifications = false,
            weeklySchedules = emptyList(),
            screenTimeLimits = emptyList(),
            openLimits = emptyList(),
            pauseOption = PauseOptionConfig(),
            interceptionStyle = BlockInterceptionStyle.STANDARD_WARNING_DIALOG,
            shortformInterceptionStyle = ShortformInterceptionStyle.REDIRECT_TO_FEED,
            customWarningMessage = UnifiedBlockRule.DEFAULT_WARNING_MESSAGE,
            closeDelaySeconds = 3,
            isFinalSave = false
        )
        assertEquals("", draftBeforeTargets.name)
        assertEquals(BlockRuleStatus.INACTIVE_MANUAL, draftBeforeTargets.status)

        // Step 2: Targets return, user adds weekly schedule and taps Done with blank name
        val returnedDraft = draftBeforeTargets.copy(
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_ENTIRE_APP)
        )
        val schedule = WeeklyScheduleTrigger(startTimeMinutes = 22 * 60, endTimeMinutes = 7 * 60)
        val savedRule = resolveBlockRuleForDraftOrSave(
            initialRule = returnedDraft,
            existingRulesForCopy = emptyList(),
            ruleName = returnedDraft.name,
            appTargets = returnedDraft.appTargets,
            blockedDomains = emptyList(),
            blockedKeywords = emptyList(),
            blockNewlyInstalled = false,
            includeInBrowserShortform = false,
            blockAppLaunch = true,
            blockNotifications = false,
            weeklySchedules = listOf(schedule),
            screenTimeLimits = emptyList(),
            openLimits = emptyList(),
            pauseOption = PauseOptionConfig(),
            interceptionStyle = BlockInterceptionStyle.STANDARD_WARNING_DIALOG,
            shortformInterceptionStyle = ShortformInterceptionStyle.REDIRECT_TO_FEED,
            customWarningMessage = UnifiedBlockRule.DEFAULT_WARNING_MESSAGE,
            closeDelaySeconds = 3,
            isFinalSave = true
        )
        assertEquals("Block", savedRule.name)
        assertEquals(BlockRuleStatus.ACTIVE, savedRule.status)

        // Step 3: Editing an already persisted INACTIVE_MANUAL rule preserves INACTIVE_MANUAL
        val persistedDisabledRule = savedRule.copy(status = BlockRuleStatus.INACTIVE_MANUAL)
        val reSavedPersisted = resolveBlockRuleForDraftOrSave(
            initialRule = persistedDisabledRule,
            existingRulesForCopy = listOf(persistedDisabledRule),
            ruleName = "Night Block",
            appTargets = persistedDisabledRule.appTargets,
            blockedDomains = emptyList(),
            blockedKeywords = emptyList(),
            blockNewlyInstalled = false,
            includeInBrowserShortform = false,
            blockAppLaunch = true,
            blockNotifications = false,
            weeklySchedules = listOf(schedule),
            screenTimeLimits = emptyList(),
            openLimits = emptyList(),
            pauseOption = PauseOptionConfig(),
            interceptionStyle = BlockInterceptionStyle.STANDARD_WARNING_DIALOG,
            shortformInterceptionStyle = ShortformInterceptionStyle.REDIRECT_TO_FEED,
            customWarningMessage = UnifiedBlockRule.DEFAULT_WARNING_MESSAGE,
            closeDelaySeconds = 3,
            isFinalSave = true
        )
        assertEquals("Night Block", reSavedPersisted.name)
        assertEquals(BlockRuleStatus.INACTIVE_MANUAL, reSavedPersisted.status)
    }

    @Test
    fun `weekly schedule presets and all day 23_59 boundary work accurately`() {
        val presetIds = WeeklyScheduleTrigger.PRESETS.map { it.id }
        assertTrue(presetIds.containsAll(listOf("sleep", "study", "work", "morning", "evening", "school")))

        val sleepPreset = WeeklyScheduleTrigger.PRESETS.first { it.id == "sleep" }.toTrigger()
        assertEquals(WeeklyScheduleTrigger.ALL_DAYS, sleepPreset.daysOfWeek)
        assertEquals("22:00 - 07:00", sleepPreset.formatTimeRange())
        assertTrue(sleepPreset.isOvernight())
        assertEquals(9 * 60, sleepPreset.durationMinutes())

        // Legacy 00:00..23:59 range must be treated as All Day including minute 23:59
        val legacyAllDay = WeeklyScheduleTrigger(
            daysOfWeek = setOf(Calendar.WEDNESDAY),
            startTimeMinutes = 0,
            endTimeMinutes = 1439
        )
        assertTrue(legacyAllDay.isAllDay())
        val wed2359 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis
        assertTrue(legacyAllDay.isCurrentlyInWindow(wed2359))
    }

    @Test
    fun `weekly schedule normalizer merges identical overlapping overnight and cross day subsets`() {
        // 1. Identical time window across weekdays + weekends combines into Every day
        val combinedDays = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 9 * 60, endTimeMinutes = 12 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKENDS, startTimeMinutes = 9 * 60, endTimeMinutes = 12 * 60)
            )
        )
        assertEquals(1, combinedDays.schedules.size)
        assertEquals(WeeklyScheduleTrigger.ALL_DAYS, combinedDays.schedules.first().daysOfWeek)
        assertTrue(combinedDays.adjustments.isNotEmpty())

        // 2. Overlapping/touching daytime windows on same days merge
        val mergedDaytime = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 9 * 60, endTimeMinutes = 12 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 11 * 60, endTimeMinutes = 15 * 60)
            )
        )
        assertEquals(1, mergedDaytime.schedules.size)
        assertEquals(9 * 60, mergedDaytime.schedules.first().startTimeMinutes)
        assertEquals(15 * 60, mergedDaytime.schedules.first().endTimeMinutes)

        // 3. Overnight + morning overlap merges cleanly into extended overnight window
        val mergedOvernight = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 22 * 60, endTimeMinutes = 7 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 6 * 60, endTimeMinutes = 9 * 60)
            )
        )
        assertEquals(1, mergedOvernight.schedules.size)
        assertEquals(22 * 60, mergedOvernight.schedules.first().startTimeMinutes)
        assertEquals(9 * 60, mergedOvernight.schedules.first().endTimeMinutes)

        // 4. Full 24h overlap collapses to All day
        val fullDay = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 20 * 60, endTimeMinutes = 10 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 9 * 60, endTimeMinutes = 21 * 60)
            )
        )
        assertEquals(1, fullDay.schedules.size)
        assertTrue(fullDay.schedules.first().isAllDay())

        // 5. Cross-day subset window is removed when fully covered by broader window
        val crossDayTrim = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 22 * 60, endTimeMinutes = 7 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 23 * 60, endTimeMinutes = 6 * 60)
            )
        )
        assertEquals(1, crossDayTrim.schedules.size)
        assertEquals(WeeklyScheduleTrigger.ALL_DAYS, crossDayTrim.schedules.first().daysOfWeek)

        // 6. Disjoint multiple windows in one block remain preserved
        val multiWindow = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 9 * 60, endTimeMinutes = 12 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.ALL_DAYS, startTimeMinutes = 22 * 60, endTimeMinutes = 7 * 60)
            )
        )
        assertEquals(2, multiWindow.schedules.size)
    }

    @Test
    fun `multi window statusSubtitle picks furthest active end and earliest upcoming start`() {
        val multiWindowRule = UnifiedBlockRule(
            name = "Focus & Sleep",
            status = BlockRuleStatus.ACTIVE,
            weeklySchedules = listOf(
                WeeklyScheduleTrigger(
                    daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS,
                    startTimeMinutes = 9 * 60,
                    endTimeMinutes = 12 * 60
                ),
                WeeklyScheduleTrigger(
                    daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS,
                    startTimeMinutes = 14 * 60,
                    endTimeMinutes = 17 * 60
                ),
                WeeklyScheduleTrigger(
                    daysOfWeek = setOf(Calendar.MONDAY),
                    startTimeMinutes = 10 * 60,
                    endTimeMinutes = 13 * 60
                )
            )
        )

        // Monday 10:30: both 09:00-12:00 and 10:00-13:00 are active -> should pick furthest end 13:00
        val mon1030 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
        assertEquals("Blocking until 13:00", multiWindowRule.statusSubtitle(1_000L, mon1030))

        // Monday 13:30: next window is 14:00 today (within 24h)
        val mon1330 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
        assertEquals("Activating 14:00", multiWindowRule.statusSubtitle(1_000L, mon1330))

        // Saturday 10:00: next window is Monday 09:00 (> 24h away -> includes day label)
        val sat1000 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        assertEquals("Activating Mon 09:00", multiWindowRule.statusSubtitle(1_000L, sat1000))
    }

    @Test
    fun `override challenge validator handles smart punctuation paste detection and nfc auto correction`() {
        val target = "I won't scroll -- stay focused."
        val typedWithSmartChars = "I won\u2019t scroll \u2014- stay focused.  "
        val evalMatch = OverrideChallengeValidator.evaluateTypeOverText(typedWithSmartChars, target)
        assertTrue(evalMatch.isComplete)
        assertNull(evalMatch.firstMismatchIndex)

        val evalMismatch = OverrideChallengeValidator.evaluateTypeOverText("I won't scrolx", target)
        assertFalse(evalMismatch.isComplete)
        assertEquals(13, evalMismatch.firstMismatchIndex)

        // Normal single-character typing is allowed
        assertFalse(OverrideChallengeValidator.isPasteAttempt("hello", "hello "))
        // Direct multi-char paste is blocked
        assertTrue(OverrideChallengeValidator.isPasteAttempt("he", "hello world"))
        // Select-and-replace paste (same length!) is also blocked
        assertTrue(OverrideChallengeValidator.isPasteAttempt("abcdefghij", "I choose i"))

        // NFC hex auto-correction prevents blank lockout and strips delimiters
        assertEquals(OverrideChallengeValidator.ANY_NFC_TAG, OverrideChallengeValidator.sanitizeNfcTagIdHex("   "))
        assertEquals("04A1B2C3", OverrideChallengeValidator.sanitizeNfcTagIdHex(" 04:a1-b2 c3 "))
        assertTrue(OverrideChallengeValidator.isValidNfcTagIdHex("04:a1:b2:c3"))
        assertTrue(OverrideChallengeValidator.isValidNfcTagIdHex(""))
        assertFalse(OverrideChallengeValidator.isValidNfcTagIdHex("ZZ1234"))
        assertFalse(OverrideChallengeValidator.isValidNfcTagIdHex("AB"))

        // Rule.sanitize() auto-corrects blank NFC tag to ANY_NFC_TAG
        val nfcRule = UnifiedBlockRule(
            name = "NFC Protected",
            pauseOption = PauseOptionConfig(
                type = PauseOptionType.SCAN_NFC_TAG,
                registeredTagIdHex = "   "
            )
        ).sanitize()
        assertEquals(OverrideChallengeValidator.ANY_NFC_TAG, nfcRule.pauseOption.registeredTagIdHex)
    }
}
