package com.alhaq.amnishield.contract

import android.net.Uri

object GuardianContract {
    const val AUTHORITY = "com.alhaq.deenshield.guardian.provider"
    val BASE_URI: Uri by lazy { Uri.parse("content://$AUTHORITY") }
    val URI_FOCUS: Uri by lazy { Uri.parse("content://$AUTHORITY/focus") }
    val URI_REELS: Uri by lazy { Uri.parse("content://$AUTHORITY/reels") }
    val URI_STATS: Uri by lazy { Uri.parse("content://$AUTHORITY/stats") }

    // Call Methods
    const val METHOD_START_FOCUS = "start_focus"
    const val METHOD_STOP_FOCUS = "stop_focus"
    const val METHOD_GET_SNAPSHOT = "get_snapshot"

    // Call Argument Keys
    const val ARG_MINUTES = "minutes"

    // Snapshot Bundle Keys
    const val KEY_API_VERSION = "api_version"
    const val KEY_FOCUS_ACTIVE = "focus_active"
    const val KEY_FOCUS_REMAINING_MINUTES = "focus_remaining_minutes"
    const val KEY_FOCUS_TODAY_MINUTES = "focus_today_minutes"
    const val KEY_FOCUS_SESSIONS_COUNT = "focus_sessions_count"
    const val KEY_REELS_SCROLLED_TODAY = "reels_scrolled_today"
    const val KEY_REELS_WATCH_SECONDS_TODAY = "reels_watch_seconds_today"
    const val KEY_STREAK_DAYS = "streak_days"
    const val KEY_SHIELD_SCORE = "shield_score"
    const val KEY_TOTAL_THREATS_BLOCKED = "total_threats_blocked"
    const val KEY_TODAY_THREATS_BLOCKED = "today_threats_blocked"

    // Broadcast Actions & Extras
    const val ACTION_FOCUS_COMPLETED = "com.alhaq.amnishield.ACTION_FOCUS_COMPLETED"
    const val ACTION_FOCUS_ABORTED = "com.alhaq.amnishield.ACTION_FOCUS_ABORTED"
    const val ACTION_REELS_THRESHOLD = "com.alhaq.amnishield.ACTION_REELS_THRESHOLD"
    const val EXTRA_DURATION_MINUTES = "duration_minutes"
    const val PERMISSION_GUARDIAN_IPC = "com.alhaq.deenshield.permission.GUARDIAN_IPC"
}
