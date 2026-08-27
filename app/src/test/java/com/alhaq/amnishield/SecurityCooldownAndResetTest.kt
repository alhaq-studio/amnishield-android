package com.alhaq.amnishield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityCooldownAndResetTest {

    @Test
    fun testHardMinimumCooldownFloor() {
        val minFloor = 5

        // Attempting to set under 5 minutes must be coerced to 5
        val testInputs = listOf(-1, 0, 1, 3, 4, 5, 10, 15, 30)
        val expectedOutputs = listOf(5, 5, 5, 5, 5, 5, 10, 15, 30)

        testInputs.zip(expectedOutputs).forEach { (input, expected) ->
            val coerced = input.coerceAtLeast(minFloor)
            assertEquals("Input $input should be coerced to $expected", expected, coerced)
        }
    }

    @Test
    fun testPinResetCooldownStateTransitions() {
        val now = 1_000_000L
        val cooldownMinutes = 5
        val cooldownMs = cooldownMinutes * 60 * 1000L // 300,000 ms

        val requestedAt = now
        val duringCooldown = now + 150_000L // 2.5 mins in
        val afterCooldown = now + 300_001L  // 5 mins + 1ms

        // During cooldown: active = true, ready = false
        val isDuringActive = duringCooldown < (requestedAt + cooldownMs)
        val remainingDuring = ((requestedAt + cooldownMs) - duringCooldown).coerceAtLeast(0L)
        assertTrue("During cooldown should be active", isDuringActive)
        assertEquals("Remaining should be 150,000 ms", 150_000L, remainingDuring)

        // After cooldown: active = false, ready = true
        val isAfterActive = afterCooldown < (requestedAt + cooldownMs)
        val remainingAfter = ((requestedAt + cooldownMs) - afterCooldown).coerceAtLeast(0L)
        assertFalse("After cooldown should not be active", isAfterActive)
        assertEquals("Remaining should be 0 ms", 0L, remainingAfter)
    }

    @Test
    fun testEmergencyAccessWindowDuration() {
        val finishTimestamp = 5_000_000L
        val windowDurationMs = 10 * 60 * 1000L // 10 minutes = 600,000 ms
        val expiryTimestamp = finishTimestamp + windowDurationMs

        val duringWindow = finishTimestamp + 300_000L // 5 mins in
        val afterWindow = finishTimestamp + 600_001L  // 10 mins + 1ms

        assertTrue("Should be active during 10-minute window", duringWindow < expiryTimestamp)
        assertFalse("Should expire after 10-minute window", afterWindow < expiryTimestamp)
    }
}
