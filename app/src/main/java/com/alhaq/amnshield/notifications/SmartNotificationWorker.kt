package com.alhaq.amnshield.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alhaq.amnshield.utils.NotificationHelper
import java.util.Calendar

class SmartNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val notificationHelper = NotificationHelper.getInstance(context)

            // 1. Check Doomscroll Spike
            val doomscrollNudge = SmartBehavioralBrain.checkDoomscrollSpike(context)
            if (doomscrollNudge != null) {
                notificationHelper.showDoomscrollAlert(doomscrollNudge.title, doomscrollNudge.message)
            }

            // 2. Check Pre-block Schedule Warning
            val preBlockNudge = SmartBehavioralBrain.checkPreBlockWarning(context)
            if (preBlockNudge != null) {
                notificationHelper.showPreBlockWarning(preBlockNudge.title, preBlockNudge.message)
            }

            // 3. Check Productivity Achievements
            notificationHelper.checkAndNotifyAchievements()

            // 4. Check Daily Digital Wellbeing Tip (midday between 11 AM and 5 PM)
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour in 11..17) {
                val tip = SmartBehavioralBrain.getRandomWellnessTip()
                notificationHelper.showWellnessTipNotification(tip.first, tip.second)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
