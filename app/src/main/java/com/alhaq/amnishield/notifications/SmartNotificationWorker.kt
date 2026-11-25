package com.alhaq.amnishield.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alhaq.amnishield.utils.NotificationHelper
import java.util.Calendar

class SmartNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val notificationHelper = NotificationHelper.getInstance(context)
            val brain = SmartBehavioralBrain(context)
            val scheduler = SmartNotificationScheduler(context)

            // 1. Evaluate Smart Behavioral Brain (goal progress, protection status, streaks, peak insights)
            val smartPayload = brain.evaluateAndSelectNotification()
            if (smartPayload.type != SmartBehavioralBrain.NotificationType.NONE) {
                scheduler.fireNotification(smartPayload)
                brain.recordNotificationSent()
            }

            // 2. Check Doomscroll Spike
            val doomscrollNudge = SmartBehavioralBrain.checkDoomscrollSpike(context)
            if (doomscrollNudge != null) {
                notificationHelper.showDoomscrollAlert(doomscrollNudge.title, doomscrollNudge.message)
            }

            // 3. Check Pre-block Schedule Warning
            val preBlockNudge = SmartBehavioralBrain.checkPreBlockWarning(context)
            if (preBlockNudge != null) {
                notificationHelper.showPreBlockWarning(preBlockNudge.title, preBlockNudge.message)
            }

            // 4. Check Productivity Achievements
            notificationHelper.checkAndNotifyAchievements()

            // 5. Check Daily Digital Wellbeing Tip (midday between 11 AM and 5 PM)
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (currentHour in 11..17 && smartPayload.type == SmartBehavioralBrain.NotificationType.NONE) {
                val tip = SmartBehavioralBrain.getRandomWellnessTip()
                notificationHelper.showWellnessTipNotification(tip.first, tip.second)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
