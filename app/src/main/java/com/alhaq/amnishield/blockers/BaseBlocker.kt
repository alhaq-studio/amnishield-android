package com.alhaq.amnishield.blockers

import android.os.SystemClock

abstract class BaseBlocker {

    companion object {
        const val DEFAULT_COOLDOWN_MS = 30_000L
    }

    /**
     * In-memory cooldown tracker mapping keys (e.g. package name, keyword, view ID) 
     * to expiration timestamps in System.currentTimeMillis().
     */
    protected val cooldownMap = mutableMapOf<String, Long>()

    /**
     * Checks if the time elapsed since last detection exceeds the specified delay.
     * Uses 64-bit Long to prevent precision loss during long device uptimes.
     */
    fun isDelayOver(lastTimestampMs: Long, delayMs: Long = DEFAULT_COOLDOWN_MS): Boolean {
        val currentTime = SystemClock.uptimeMillis()
        return (currentTime - lastTimestampMs) > delayMs
    }

    /**
     * Checks whether a specific package, keyword, or view ID is currently in a cooldown state.
     */
    open fun isUnderCooldown(key: String): Boolean {
        val expireTime = cooldownMap[key] ?: return false
        val now = System.currentTimeMillis()
        if (now >= expireTime) {
            cooldownMap.remove(key)
            return false
        }
        return true
    }

    /**
     * Adds an item to cooldown until a specific future timestamp.
     */
    open fun applyCooldown(key: String, expireTimestampMs: Long) {
        cooldownMap[key] = expireTimestampMs
    }

    /**
     * Restores saved cooldown entries from SharedPreferences/DataStore.
     */
    open fun restoreCooldowns(savedCooldowns: Map<String, Long>) {
        val now = System.currentTimeMillis()
        cooldownMap.clear()
        cooldownMap.putAll(savedCooldowns.filter { it.value > now })
    }

    /**
     * Returns an active snapshot of cooldowns to persist into storage.
     */
    open fun getCooldownSnapshot(): Map<String, Long> {
        val now = System.currentTimeMillis()
        return cooldownMap.filter { it.value > now }
    }
}
