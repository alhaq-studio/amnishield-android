package com.alhaq.amnishield.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Manages broadcast receiver registrations and dispatches settings, cooldown,
 * and authentication refresh events to their respective service handlers.
 */
class ServiceBroadcastManager(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onRefreshAppBlocker()
        fun onRefreshBlockedKeywordList()
        fun onRefreshViewBlocker()
        fun onRefreshViewBlockerCooldown(resultId: String?, interval: Int)
        fun onRefreshReelBlocker()
        fun onRefreshReelBlockerCooldown(resultId: String?, interval: Int)
        fun onRefreshKeywordBlockerCooldown(resultId: String?, interval: Int)
        fun onRefreshUnifiedFeatureSchedules()
        fun onRefreshAppBlockerCooldown(resultId: String?, interval: Int)
        fun onRefreshFocusMode()
        fun onRefreshAntiUninstall()
        fun onPasswordVerified()
    }

    private var isRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action ?: return
            when (intent.action) {
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER -> callbacks.onRefreshAppBlocker()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST -> callbacks.onRefreshBlockedKeywordList()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER -> callbacks.onRefreshViewBlocker()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN -> {
                    val resultId = intent.getStringExtra("result_id")
                    val interval = intent.getIntExtra("selected_time", 0)
                    callbacks.onRefreshViewBlockerCooldown(resultId, interval)
                }
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER -> callbacks.onRefreshReelBlocker()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER_COOLDOWN -> {
                    val resultId = intent.getStringExtra("result_id")
                    val interval = intent.getIntExtra("selected_time", 0)
                    callbacks.onRefreshReelBlockerCooldown(resultId, interval)
                }
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN -> {
                    val resultId = intent.getStringExtra("result_id")
                    val interval = intent.getIntExtra("selected_time", 0)
                    callbacks.onRefreshAppBlockerCooldown(resultId, interval)
                }
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN -> {
                    val resultId = intent.getStringExtra("result_id")
                    val interval = intent.getIntExtra("selected_time", 0)
                    callbacks.onRefreshKeywordBlockerCooldown(resultId, interval)
                }
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES -> callbacks.onRefreshUnifiedFeatureSchedules()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE -> callbacks.onRefreshFocusMode()
                AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL -> callbacks.onRefreshAntiUninstall()
                AmniShieldAccessibilityService.INTENT_ACTION_PASSWORD_VERIFIED -> callbacks.onPasswordVerified()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_BLOCKED_KEYWORD_LIST)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_VIEW_BLOCKER_COOLDOWN)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_REEL_BLOCKER_COOLDOWN)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_KEYWORD_BLOCKER_COOLDOWN)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_UNIFIED_FEATURE_SCHEDULES)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
            addAction(AmniShieldAccessibilityService.INTENT_ACTION_PASSWORD_VERIFIED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        isRegistered = false
    }
}
