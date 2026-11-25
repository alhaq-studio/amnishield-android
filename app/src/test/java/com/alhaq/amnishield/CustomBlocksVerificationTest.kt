package com.alhaq.amnishield

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CustomBlocksVerificationTest {

    private class MemoryPrefs : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun createLoader(
        elapsedProvider: () -> Long = { 100_000L },
        wallProvider: () -> Long = { 1_700_000_000_000L }
    ): SavedPreferencesLoader {
        val prefsMap = mutableMapOf<String, MemoryPrefs>()
        val testContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.deenshield"
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { MemoryPrefs() }
            }
        }
        return SavedPreferencesLoader(
            context = testContext,
            elapsedRealtimeProvider = elapsedProvider,
            wallClockProvider = wallProvider
        )
    }

    @Test
    fun `all four rule statuses evaluate activation and visibility accurately`() {
        val nowElapsed = 500_000L
        val nowWall = 1_700_000_000_000L

        val activeRule = UnifiedBlockRule(
            name = "Always On Focus",
            status = BlockRuleStatus.ACTIVE
        )
        assertTrue(activeRule.isEffectivelyActive(nowElapsed, nowWall))
        assertTrue(activeRule.isUnconditionalOrScheduledActiveNow(nowElapsed, nowWall))
        assertTrue(activeRule.isActiveOrScheduledForContentNow(nowElapsed, nowWall))

        val inactiveManual = activeRule.copy(status = BlockRuleStatus.INACTIVE_MANUAL)
        assertFalse(inactiveManual.isEffectivelyActive(nowElapsed, nowWall))
        assertFalse(inactiveManual.isUnconditionalOrScheduledActiveNow(nowElapsed, nowWall))
        assertFalse(inactiveManual.isActiveOrScheduledForContentNow(nowElapsed, nowWall))

        val paused = activeRule.copy(
            status = BlockRuleStatus.PAUSED_UNTIL,
            pausedUntilElapsedRealtime = nowElapsed + 10 * 60_000L,
            pausedUntilWallClockMillis = nowWall + 10 * 60_000L
        )
        assertFalse(paused.isEffectivelyActive(nowElapsed, nowWall))
        assertEquals(10, paused.remainingPauseMinutes(nowElapsed, nowWall))

        val archived = activeRule.copy(status = BlockRuleStatus.ARCHIVED)
        assertFalse(archived.isEffectivelyActive(nowElapsed, nowWall))
        assertEquals("Archived", archived.statusSubtitle(nowElapsed, nowWall))
    }

    @Test
    fun `reboot safe pause falls back to wall clock when elapsedRealtime resets after reboot`() {
        // Prior boot had uptime ~800 minutes; user paused for 30m (maxPauseDurationMinutes = 60).
        val priorBootDeadlineElapsed = 800 * 60_000L
        val pauseStartWall = 1_700_000_000_000L
        val pauseEndWall = pauseStartWall + 30 * 60_000L

        val pausedRule = UnifiedBlockRule(
            name = "Reboot Test Rule",
            status = BlockRuleStatus.PAUSED_UNTIL,
            pausedUntilElapsedRealtime = priorBootDeadlineElapsed,
            pausedUntilWallClockMillis = pauseEndWall,
            pauseOption = PauseOptionConfig(maxPauseDurationMinutes = 60)
        )

        // Device rebooted: elapsedRealtime is now 2 minutes (120_000ms), far below priorBootDeadlineElapsed.
        // Only 10 minutes of wall time passed -> 20 minutes remaining.
        val rebootedElapsed = 120_000L
        val duringPauseWall = pauseStartWall + 10 * 60_000L
        assertTrue(pausedRule.isPaused(rebootedElapsed, duringPauseWall))
        assertEquals(20, pausedRule.remainingPauseMinutes(rebootedElapsed, duringPauseWall))

        // After 31 minutes of wall time post-reboot -> pause expired even though elapsedRealtime is still tiny.
        val expiredWall = pauseStartWall + 31 * 60_000L
        assertFalse(pausedRule.isPaused(rebootedElapsed + 60_000L, expiredWall))
        assertEquals(0, pausedRule.remainingPauseMinutes(rebootedElapsed + 60_000L, expiredWall))
        assertTrue(pausedRule.isEffectivelyActive(rebootedElapsed + 60_000L, expiredWall))
    }

    @Test
    fun `computePauseDurationOptions covers all edge limits including small maxPauseDuration`() {
        assertEquals(listOf(1), PauseOptionConfig.computePauseDurationOptions(1))
        assertEquals(listOf(1, 5), PauseOptionConfig.computePauseDurationOptions(5))
        assertEquals(listOf(1, 5, 10), PauseOptionConfig.computePauseDurationOptions(10))
        assertEquals(listOf(1, 5, 15), PauseOptionConfig.computePauseDurationOptions(15))
        assertEquals(listOf(1, 5, 15, 30, 45), PauseOptionConfig.computePauseDurationOptions(45))
        assertEquals(listOf(1, 5, 15, 30, 60), PauseOptionConfig.computePauseDurationOptions(60))
        assertEquals(listOf(1, 5, 15, 30, 60, 120), PauseOptionConfig.computePauseDurationOptions(120))
        assertEquals(listOf(1, 5, 15, 30, 60, 120, 720), PauseOptionConfig.computePauseDurationOptions(720))
        assertEquals(listOf(1), PauseOptionConfig.computePauseDurationOptions(0))
        assertEquals(listOf(1, 5, 15, 30, 60, 120, 720), PauseOptionConfig.computePauseDurationOptions(999))
    }

    @Test
    fun `all PauseOptionType and OverrideChallengeValidator behaviors validate accurately`() {
        PauseOptionType.entries.forEach { type ->
            val cfg = PauseOptionConfig(type = type)
            assertTrue(cfg.titleLabel().isNotBlank())
            assertTrue(cfg.subtitleLabel().isNotBlank())
        }

        // Smart quotes, curly apostrophes, em-dashes, multiple spaces
        val target = "I choose 'focus' -- not scrolling."
        val typed = "I choose \u2018focus\u2019   \u2014-  not scrolling. "
        val eval = OverrideChallengeValidator.evaluateTypeOverText(typed, target)
        assertTrue(eval.isComplete)
        assertNull(eval.firstMismatchIndex)

        // Mismatch reporting
        val mismatch = OverrideChallengeValidator.evaluateTypeOverText("I choose 'fox", target)
        assertFalse(mismatch.isComplete)
        assertEquals(12, mismatch.firstMismatchIndex)

        // Paste prevention
        assertFalse(OverrideChallengeValidator.isPasteAttempt("ab", "abc"))
        assertTrue(OverrideChallengeValidator.isPasteAttempt("ab", "abcdef"))

        // NFC sanitization
        assertEquals("04FF11AA", OverrideChallengeValidator.sanitizeNfcTagIdHex("04:ff-11 aa"))
        assertEquals(OverrideChallengeValidator.ANY_NFC_TAG, OverrideChallengeValidator.sanitizeNfcTagIdHex(""))
    }

    @Test
    fun `all trigger variants daytime overnight all-day normalizer and limits behave consistently`() {
        val mon0800 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val mon1500 = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val dayTrigger = WeeklyScheduleTrigger(
            daysOfWeek = setOf(Calendar.MONDAY),
            startTimeMinutes = 9 * 60,
            endTimeMinutes = 17 * 60
        )
        assertFalse(dayTrigger.isCurrentlyInWindow(mon0800))
        assertTrue(dayTrigger.isCurrentlyInWindow(mon1500))

        val allDayTrigger = WeeklyScheduleTrigger(
            daysOfWeek = setOf(Calendar.MONDAY),
            startTimeMinutes = 0,
            endTimeMinutes = 0
        )
        assertTrue(allDayTrigger.isAllDay())
        assertTrue(allDayTrigger.isCurrentlyInWindow(mon0800))

        // WeeklyScheduleNormalizer merges overlapping windows cleanly
        val overlap = WeeklyScheduleNormalizer.normalize(
            listOf(
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 9 * 60, endTimeMinutes = 13 * 60),
                WeeklyScheduleTrigger(daysOfWeek = WeeklyScheduleTrigger.WEEKDAYS, startTimeMinutes = 12 * 60, endTimeMinutes = 17 * 60)
            )
        )
        assertEquals(1, overlap.schedules.size)
        assertEquals(9 * 60, overlap.schedules.first().startTimeMinutes)
        assertEquals(17 * 60, overlap.schedules.first().endTimeMinutes)

        // Screen time & open limit summaries
        assertEquals("1h / hr", ScreenTimeLimitTrigger(period = LimitPeriod.HOURLY, maxMinutes = 60).formatSummary())
        assertEquals("45m / day", ScreenTimeLimitTrigger(period = LimitPeriod.DAILY, maxMinutes = 45).formatSummary())
        assertEquals("3x / hr", OpenLimitTrigger(period = LimitPeriod.HOURLY, maxLaunches = 3).formatSummary())
        assertEquals("12x / day", OpenLimitTrigger(period = LimitPeriod.DAILY, maxLaunches = 12).formatSummary())
    }

    @Test
    fun `all BlockInterceptionStyle and ShortformInterceptionStyle variants expose non-empty labels`() {
        assertTrue(BlockInterceptionStyle.entries.contains(BlockInterceptionStyle.SILENT_CLEAN))
        BlockInterceptionStyle.entries.forEach { style ->
            assertTrue(style.titleLabel().isNotBlank())
            assertTrue(style.subtitleLabel().isNotBlank())
        }
        ShortformInterceptionStyle.entries.forEach { style ->
            assertTrue(style.titleLabel().isNotBlank())
            assertTrue(style.subtitleLabel().isNotBlank())
        }

        val rule = UnifiedBlockRule(
            name = "Quiet Guard",
            appTargets = mapOf("com.instagram.android" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY),
            includeInBrowserShortform = true,
            interceptionStyle = BlockInterceptionStyle.SILENT_CLEAN,
            shortformInterceptionStyle = ShortformInterceptionStyle.MINDFUL_BREATHING_PAUSE
        )
        val summary = rule.warningSummarySubtitle()
        assertTrue(summary.contains("Silent Clean"))
        assertTrue(summary.contains("Mindful Breathing Pause"))
    }

    @Test
    fun `syncUnifiedRulesToLegacyEngines propagates app shortform domains keywords pause and status`() {
        var nowElapsed = 200_000L
        var nowWall = 1_700_000_000_000L
        val loader = createLoader(
            elapsedProvider = { nowElapsed },
            wallProvider = { nowWall }
        )

        val fullAppRule = UnifiedBlockRule(
            id = "r_full",
            name = "Full & Web",
            status = BlockRuleStatus.ACTIVE,
            appTargets = mapOf(
                "com.zhiliaoapp.musically" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY,
                "com.twitter.android" to AppInterceptionLevel.BLOCK_ENTIRE_APP
            ),
            blockedDomains = listOf("news.ycombinator.com"),
            blockedKeywords = listOf("spoiler alert"),
            includeInBrowserShortform = true,
            blockAppLaunch = true
        )

        loader.saveUnifiedBlockRules(listOf(fullAppRule), syncToEngines = true)

        assertTrue(loader.loadBlockedApps().contains("com.twitter.android"))
        assertFalse(loader.loadBlockedApps().contains("com.zhiliaoapp.musically"))
        assertTrue(loader.isReelBlockerEnabled())
        assertTrue(loader.isReelBlockerTiktokEnabled())
        assertTrue(loader.isReelBlockerBrowserEnabled())
        assertTrue(loader.loadBlockedWebsites().contains("news.ycombinator.com"))
        assertTrue(loader.loadBlockedKeywords().contains("spoiler alert"))

        // Pausing the rule immediately unblocks targets during pause window
        loader.pauseUnifiedBlockRule("r_full", 15)
        assertFalse(loader.loadBlockedApps().contains("com.twitter.android"))
        assertFalse(loader.isReelBlockerEnabled())
        assertTrue(loader.loadBlockedWebsites().isEmpty())
        assertTrue(loader.loadBlockedKeywords().isEmpty())

        // Deactivating to manual off keeps targets cleared
        loader.setUnifiedBlockRuleStatus("r_full", BlockRuleStatus.INACTIVE_MANUAL)
        assertEquals(BlockRuleStatus.INACTIVE_MANUAL, loader.loadUnifiedBlockRules().first().status)

        // Advancing time 20m after re-pausing expires cleanly on next load/sync
        loader.setUnifiedBlockRuleStatus("r_full", BlockRuleStatus.ACTIVE)
        loader.pauseUnifiedBlockRule("r_full", 15)
        nowElapsed += 20 * 60_000L
        nowWall += 20 * 60_000L
        val loaded = loader.loadUnifiedBlockRules()
        assertEquals(BlockRuleStatus.ACTIVE, loaded.first().status)
    }
}
