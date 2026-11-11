package com.alhaq.amnishield.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.alhaq.amnishield.api.ApiAuthStore
import com.alhaq.amnishield.blockers.FocusModeBlocker
import com.alhaq.amnishield.contract.GuardianContract
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.BlockingStatsManager
import com.alhaq.amnishield.utils.GuardianStatsEngine
import com.alhaq.amnishield.utils.NotificationTimerManager
import com.alhaq.amnishield.utils.ReelsStatsManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.time.LocalDate

open class GuardianContentProvider(
    private val injectedContext: Context? = null
) : ContentProvider() {

    private val effectiveContext: Context?
        get() = injectedContext ?: context

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isCallerAuthorized()) {
            throw SecurityException("Caller is not authorized to invoke GuardianContentProvider method: $method")
        }

        val ctx = effectiveContext ?: return null

        return when (method) {
            GuardianContract.METHOD_START_FOCUS -> {
                val minutes = extras?.getInt(GuardianContract.ARG_MINUTES, 0)?.takeIf { it > 0 }
                    ?: arg?.toIntOrNull()?.takeIf { it > 0 }
                    ?: 25
                val result = startFocusSession(ctx, minutes)
                Bundle().apply {
                    putBoolean(GuardianContract.KEY_FOCUS_ACTIVE, result.isActive)
                    putInt(GuardianContract.KEY_FOCUS_REMAINING_MINUTES, result.remainingMinutes)
                }
            }

            GuardianContract.METHOD_STOP_FOCUS -> {
                val result = stopFocusSession(ctx)
                Bundle().apply {
                    putBoolean(GuardianContract.KEY_FOCUS_ACTIVE, result.isActive)
                    putInt(GuardianContract.KEY_FOCUS_REMAINING_MINUTES, result.remainingMinutes)
                }
            }

            GuardianContract.METHOD_GET_SNAPSHOT -> {
                val snapshot = computeSnapshot(ctx)
                Bundle().apply {
                    putInt(GuardianContract.KEY_API_VERSION, snapshot.apiVersion)
                    putBoolean(GuardianContract.KEY_FOCUS_ACTIVE, snapshot.isFocusActive)
                    putInt(GuardianContract.KEY_FOCUS_REMAINING_MINUTES, snapshot.focusRemainingMinutes)
                    putInt(GuardianContract.KEY_FOCUS_TODAY_MINUTES, snapshot.focusTodayMinutes)
                    putInt(GuardianContract.KEY_FOCUS_SESSIONS_COUNT, snapshot.focusSessionsCount)
                    putInt(GuardianContract.KEY_REELS_SCROLLED_TODAY, snapshot.reelsScrolledToday)
                    putLong(GuardianContract.KEY_REELS_WATCH_SECONDS_TODAY, snapshot.reelsWatchSecondsToday)
                    putInt(GuardianContract.KEY_STREAK_DAYS, snapshot.streakDays)
                    putInt(GuardianContract.KEY_SHIELD_SCORE, snapshot.shieldScore)
                    putInt(GuardianContract.KEY_TOTAL_THREATS_BLOCKED, snapshot.totalThreatsBlocked)
                    putInt(GuardianContract.KEY_TODAY_THREATS_BLOCKED, snapshot.todayThreatsBlocked)
                }
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uri) {
            GuardianContract.URI_FOCUS -> "vnd.android.cursor.item/vnd.com.alhaq.deenshield.guardian.focus"
            GuardianContract.URI_REELS -> "vnd.android.cursor.item/vnd.com.alhaq.deenshield.guardian.reels"
            GuardianContract.URI_STATS -> "vnd.android.cursor.item/vnd.com.alhaq.deenshield.guardian.stats"
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    protected open fun isCallerAuthorized(): Boolean {
        val ctx = effectiveContext ?: return false
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return true

        val pm = ctx.packageManager
        if (pm.checkSignatures(callingUid, Process.myUid()) == PackageManager.SIGNATURE_MATCH) {
            return true
        }

        val packages = pm.getPackagesForUid(callingUid)
        if (packages != null && (
            packages.contains("com.alhaq.amniquest") ||
            packages.contains("com.alhaq.amniquest.debug")
        )) {
            return true
        }

        return ApiAuthStore.isAnyGranted(ctx, packages)
    }

    companion object {
        fun startFocusSession(ctx: Context, minutes: Int): FocusResult {
            val loader = SavedPreferencesLoader(ctx)
            val durationMillis = minutes * 60_000L
            val startTime = System.currentTimeMillis()
            val endTime = startTime + durationMillis

            loader.setFocusModeFeatureEnabled(true)
            val currentData = loader.getFocusModeData()
            loader.saveFocusModeData(
                FocusModeBlocker.FocusModeData(
                    isTurnedOn = true,
                    endTime = endTime,
                    modeType = currentData.modeType,
                    selectedApps = currentData.selectedApps
                )
            )
            loader.saveFocusSessionStartTime(startTime, endTime)

            val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                setPackage(ctx.packageName)
            }
            ctx.sendBroadcast(refreshIntent)

            try {
                NotificationTimerManager(ctx).startTimer(durationMillis)
            } catch (e: Exception) {
            }

            try {
                ctx.contentResolver.notifyChange(GuardianContract.URI_FOCUS, null)
            } catch (e: Exception) {
            }

            return FocusResult(isActive = true, remainingMinutes = minutes)
        }

        fun stopFocusSession(ctx: Context): FocusResult {
            val loader = SavedPreferencesLoader(ctx)
            loader.stopFocusSession(isCancelled = true)

            val refreshIntent = Intent(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                setPackage(ctx.packageName)
            }
            ctx.sendBroadcast(refreshIntent)

            try {
                NotificationTimerManager(ctx).stopTimer()
            } catch (e: Exception) {
            }

            try {
                ctx.contentResolver.notifyChange(GuardianContract.URI_FOCUS, null)
            } catch (e: Exception) {
            }

            return FocusResult(isActive = false, remainingMinutes = 0)
        }

        fun computeSnapshot(ctx: Context): GuardianSnapshot {
            val loader = SavedPreferencesLoader(ctx)
            val focusData = loader.getFocusModeData()
            val isFocusActive = focusData.isTurnedOn && (focusData.endTime <= 0L || System.currentTimeMillis() < focusData.endTime)
            val remainingMinutes = if (isFocusActive && focusData.endTime > System.currentTimeMillis()) {
                ((focusData.endTime - System.currentTimeMillis()) / 60000L).toInt().coerceAtLeast(1)
            } else {
                0
            }

            val guardianStats = GuardianStatsEngine.computeGuardianStats(ctx)
            val blockingStats = BlockingStatsManager.getInstance(ctx)
            val todaySummary = blockingStats.getStatsSummaryForDate(LocalDate.now())
            val reelsStats = ReelsStatsManager.getInstance(ctx)
            val todayReels = reelsStats.loadDailyRecord(LocalDate.now().toString())

            return GuardianSnapshot(
                apiVersion = 1,
                isFocusActive = isFocusActive,
                focusRemainingMinutes = remainingMinutes,
                focusTodayMinutes = guardianStats.todayFocusMinutes.toInt(),
                focusSessionsCount = todaySummary.focusSessionsCount,
                reelsScrolledToday = todayReels.totalScrolled,
                reelsWatchSecondsToday = todayReels.totalWatchTimeSeconds,
                streakDays = guardianStats.focusStreakDays,
                shieldScore = guardianStats.focusShieldScore,
                totalThreatsBlocked = guardianStats.totalThreatsBlocked,
                todayThreatsBlocked = guardianStats.todayThreatsBlocked
            )
        }
    }
}

data class FocusResult(
    val isActive: Boolean,
    val remainingMinutes: Int
)

data class GuardianSnapshot(
    val apiVersion: Int = 1,
    val isFocusActive: Boolean = false,
    val focusRemainingMinutes: Int = 0,
    val focusTodayMinutes: Int = 0,
    val focusSessionsCount: Int = 0,
    val reelsScrolledToday: Int = 0,
    val reelsWatchSecondsToday: Long = 0L,
    val streakDays: Int = 0,
    val shieldScore: Int = 0,
    val totalThreatsBlocked: Int = 0,
    val todayThreatsBlocked: Int = 0
)
