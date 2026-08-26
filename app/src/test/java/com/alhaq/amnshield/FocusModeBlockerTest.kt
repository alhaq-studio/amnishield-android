package com.alhaq.amnshield

import com.alhaq.amnshield.blockers.FocusModeBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeBlockerTest {

    @Test
    fun testEssentialSystemAppsAreExempted() {
        val essentialApps = FocusModeBlocker.ESSENTIAL_SYSTEM_APPS
        assertTrue("Dialer must be in essential apps", essentialApps.contains("com.android.dialer"))
        assertTrue("Phone must be in essential apps", essentialApps.contains("com.android.phone"))
        assertTrue("Settings must be in essential apps", essentialApps.contains("com.android.settings"))
        assertTrue("System UI must be in essential apps", essentialApps.contains("com.android.systemui"))
        assertTrue("AmnShield must be in essential apps", essentialApps.contains("com.alhaq.amnshield"))
        assertTrue("Play Services must be in essential apps", essentialApps.contains("com.google.android.gms"))
    }

    @Test
    fun testStrictnessConstants() {
        assertEquals(0, FocusModeBlocker.STRICTNESS_MINDFUL_PAUSE)
        assertEquals(1, FocusModeBlocker.STRICTNESS_HARD_LOCK)
    }

    @Test
    fun testFocusModeDataModel() {
        val data = FocusModeBlocker.FocusModeData(
            isTurnedOn = true,
            endTime = 123456789L,
            modeType = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED,
            selectedApps = hashSetOf("com.example.app")
        )
        assertTrue(data.isTurnedOn)
        assertEquals(123456789L, data.endTime)
        assertEquals(Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED, data.modeType)
        assertTrue(data.selectedApps.contains("com.example.app"))
    }

    @Test
    fun testFocusModeResultModel() {
        val result = FocusModeBlocker.FocusModeResult(
            isBlocked = true,
            focusModeEndTime = 9999L,
            isRequestingToUpdateSPData = false,
            isStrict = true
        )
        assertTrue(result.isBlocked)
        assertEquals(9999L, result.focusModeEndTime)
        assertFalse(result.isRequestingToUpdateSPData)
        assertTrue(result.isStrict)
    }
}
