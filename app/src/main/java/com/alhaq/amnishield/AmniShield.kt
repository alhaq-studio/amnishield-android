/**
 * ============================================================================
 * AmniShield Application Entrypoint
 * ============================================================================
 * Responsibility:
 * Initializes foundational singletons, installs the global CrashLogger handler,
 * configures notification channels, and tracks foreground activity lifecycle.
 * 
 * Execution Context:
 * Application Main Process startup.
 * ============================================================================
 */
package com.alhaq.amnishield

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alhaq.amnishield.utils.NotificationHelper

class AmniShield : Application(), Application.ActivityLifecycleCallbacks {
    private var startedActivitiesCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var lockRunnable: Runnable? = null

    companion object {
        private const val TAG = "AmniShield"
        const val PIN_SESSION_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

        @Volatile
        var isAppUnlocked = false

        @Volatile
        private var bypassUnlockedTimestamp: Long = 0L

        var isBypassUnlocked: Boolean
            get() = isBypassSessionActive()
            set(value) {
                if (value) {
                    unlockBypassSession()
                } else {
                    bypassUnlockedTimestamp = 0L
                }
            }

        fun isBypassSessionActive(): Boolean {
            val now = System.currentTimeMillis()
            return (now - bypassUnlockedTimestamp) in 0..PIN_SESSION_TIMEOUT_MS
        }

        fun unlockBypassSession() {
            bypassUnlockedTimestamp = System.currentTimeMillis()
        }

        fun lockSession() {
            isAppUnlocked = false
            bypassUnlockedTimestamp = 0L
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            CrashLogger.install(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to install CrashLogger", e)
        }
        try {
            NotificationHelper.getInstance(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create notification channels", e)
        }
        try {
            registerActivityLifecycleCallbacks(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register activity lifecycle callbacks", e)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {
        startedActivitiesCount++
        // Cancel any pending lock task when an activity is started / foregrounded
        lockRunnable?.let { handler.removeCallbacks(it) }
        lockRunnable = null
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        startedActivitiesCount = (startedActivitiesCount - 1).coerceAtLeast(0)
        if (startedActivitiesCount == 0) {
            // Give a 1.5s grace period for inter-activity transitions (e.g. MainActivity -> FragmentActivity)
            // If no activity starts within this window, the user has truly exited/minimized the app.
            lockRunnable?.let { handler.removeCallbacks(it) }
            val task = Runnable {
                if (startedActivitiesCount == 0) {
                    isAppUnlocked = false
                    isBypassUnlocked = false
                }
            }
            lockRunnable = task
            handler.postDelayed(task, 1500L)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
