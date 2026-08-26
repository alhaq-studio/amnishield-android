package com.alhaq.amnshield.services

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.alhaq.amnshield.utils.SavedPreferencesLoader

abstract class BaseBlockingService : AccessibilityService() {

    companion object {
        const val DEFAULT_GLOBAL_ACTION_DEBOUNCE_MS = 1000L
    }

    val savedPreferencesLoader: SavedPreferencesLoader by lazy {
        SavedPreferencesLoader(this)
    }

    // Tracks the last execution of any system navigation action to prevent UI thrashing
    private var lastGlobalActionTimestamp: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Implemented by child service
    }

    override fun onInterrupt() {
        // Implemented by child service
    }

    /**
     * Checks if enough time has elapsed since the last global action.
     */
    fun isDelayOver(delayMs: Long = DEFAULT_GLOBAL_ACTION_DEBOUNCE_MS): Boolean {
        val now = SystemClock.uptimeMillis()
        return (now - lastGlobalActionTimestamp) >= delayMs
    }

    /**
     * Executes GLOBAL_ACTION_HOME with automatic debouncing.
     */
    fun pressHome(debounceMs: Long = DEFAULT_GLOBAL_ACTION_DEBOUNCE_MS): Boolean {
        if (isDelayOver(debounceMs)) {
            val success = performGlobalAction(GLOBAL_ACTION_HOME)
            if (success) {
                lastGlobalActionTimestamp = SystemClock.uptimeMillis()
            }
            return success
        }
        return false
    }

    /**
     * Executes GLOBAL_ACTION_BACK with automatic debouncing.
     */
    fun pressBack(debounceMs: Long = DEFAULT_GLOBAL_ACTION_DEBOUNCE_MS): Boolean {
        if (isDelayOver(debounceMs)) {
            val success = performGlobalAction(GLOBAL_ACTION_BACK)
            if (success) {
                lastGlobalActionTimestamp = SystemClock.uptimeMillis()
            }
            return success
        }
        return false
    }
}