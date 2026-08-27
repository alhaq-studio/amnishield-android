package com.alhaq.amnishield.utils

import android.content.Context
import java.time.LocalDate

data class GuardianStats(
    val focusStreakDays: Int,
    val focusShieldScore: Int,
    val totalThreatsBlocked: Int,
    val todayFocusMinutes: Long,
    val todayThreatsBlocked: Int,
    val totalReelsScrolled: Int
)

/**
 * Production-ready stats calculation engine.
 * Computes live focus streaks, algorithmic shield scores (0-100%), and aggregated threats blocked.
 */
object GuardianStatsEngine {

    fun computeGuardianStats(context: Context): GuardianStats {
        val blockingStatsManager = BlockingStatsManager.getInstance(context)
        val reelsStatsManager = ReelsStatsManager.getInstance(context)
        val savedPreferencesLoader = SavedPreferencesLoader(context)

        val today = LocalDate.now()
        val todayStats = blockingStatsManager.getStatsSummaryForDate(today)
        val todayReelsRecord = reelsStatsManager.loadDailyRecord(today.toString())

        // 1. Calculate Total Filtered Hits / Threats Blocked (Lifetime)
        val totalAppAndKeywordBlocks = blockingStatsManager.getTotalBlocksCount()
        val totalReelsBlocked = reelsStatsManager.getRecordsForLastDays(30).sumOf { it.totalScrolled }
        val totalThreats = (totalAppAndKeywordBlocks + totalReelsBlocked).coerceAtLeast(0)

        // 2. Calculate Real Focus Streak Days (consecutive days with focus or blocker activity)
        val streakDays = computeFocusStreak(blockingStatsManager, reelsStatsManager)

        // 3. Calculate Dynamic Shield Score (0-100%)
        val targetMinutes = savedPreferencesLoader.getProfileGoalMinutes(default = 120)
        val shieldScore = computeShieldScore(
            todayFocusMinutes = todayStats.totalFocusMinutes,
            targetGoalMinutes = targetMinutes,
            isAppBlockerOn = savedPreferencesLoader.isAppBlockerFeatureEnabled(),
            isKeywordBlockerOn = savedPreferencesLoader.isKeywordBlockerFeatureEnabled(),
            isReelsBlockerOn = savedPreferencesLoader.isReelBlockerEnabled(),
            isFocusModeOn = savedPreferencesLoader.isFocusModeFeatureEnabled()
        )

        return GuardianStats(
            focusStreakDays = streakDays,
            focusShieldScore = shieldScore,
            totalThreatsBlocked = totalThreats,
            todayFocusMinutes = todayStats.totalFocusMinutes,
            todayThreatsBlocked = todayStats.appBlocksCount + todayStats.keywordBlocksCount + todayStats.viewBlocksCount,
            totalReelsScrolled = todayReelsRecord.totalScrolled
        )
    }

    private fun computeFocusStreak(
        blockingStats: BlockingStatsManager,
        reelsStats: ReelsStatsManager
    ): Int {
        var streak = 0
        var checkDate = LocalDate.now()
        val maxLookbackDays = 60

        for (i in 0 until maxLookbackDays) {
            val stats = blockingStats.getStatsSummaryForDate(checkDate)
            val reels = reelsStats.loadDailyRecord(checkDate.toString())
            val hasActivity = stats.focusSessionsCount > 0 ||
                    stats.totalFocusMinutes > 0 ||
                    stats.appBlocksCount > 0 ||
                    stats.keywordBlocksCount > 0 ||
                    reels.totalScrolled > 0

            if (hasActivity) {
                streak++
            } else if (i > 0) {
                // If previous day had zero activity, the consecutive streak ends
                break
            }
            checkDate = checkDate.minusDays(1)
        }

        // For a new install or active user today, minimum streak starts at 1
        return if (streak == 0) 1 else streak
    }

    private fun computeShieldScore(
        todayFocusMinutes: Long,
        targetGoalMinutes: Int,
        isAppBlockerOn: Boolean,
        isKeywordBlockerOn: Boolean,
        isReelsBlockerOn: Boolean,
        isFocusModeOn: Boolean
    ): Int {
        // Component 1: Focus Goal Compliance (Up to 40 pts)
        val focusTargetRatio = if (targetGoalMinutes > 0) {
            (todayFocusMinutes.toFloat() / targetGoalMinutes.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        val focusPoints = (focusTargetRatio * 40f).toInt()

        // Component 2: Active Protection Shields (Up to 40 pts - 10 pts per enabled module)
        var shieldPoints = 0
        if (isAppBlockerOn) shieldPoints += 10
        if (isKeywordBlockerOn) shieldPoints += 10
        if (isReelsBlockerOn) shieldPoints += 10
        if (isFocusModeOn) shieldPoints += 10

        // Component 3: Base Integrity Baseline (20 pts)
        val baseIntegrityPoints = 20

        val totalScore = (focusPoints + shieldPoints + baseIntegrityPoints).coerceIn(10, 100)
        return totalScore
    }
}
