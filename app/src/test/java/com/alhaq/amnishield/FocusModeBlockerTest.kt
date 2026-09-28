package com.alhaq.amnishield

import com.alhaq.amnishield.blockers.FocusModeBlocker
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
        assertTrue("AmniShield must be in essential apps", essentialApps.contains("com.alhaq.amnishield"))
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

    @Test
    fun testScheduledFocusModeBlocksWhenManualQuickFocusIsOff() {
        val fakePrefs = InMemorySharedPreferences()
        val context = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.alhaq.amnishield"
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = fakePrefs
            override fun getSystemService(name: String): Any? = null
        }
        val loader = com.alhaq.amnishield.utils.SavedPreferencesLoader(
            context = context,
            injectedCompassionatePrefs = fakePrefs,
            injectedPremiumPrefs = fakePrefs
        )

        // User configured Focus Mode to Block All Except Selected ("com.allowed.work")
        loader.saveFocusModeSelectedApps(listOf("com.allowed.work"))
        loader.saveFocusModeData(
            FocusModeBlocker.FocusModeData(
                isTurnedOn = false,
                modeType = Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED,
                selectedApps = hashSetOf("com.allowed.work")
            )
        )

        val blocker = FocusModeBlocker()
        blocker.focusModeData.isTurnedOn = false

        // 1. Without active schedule, no blocking occurs
        val inactiveResult = blocker.doesAppNeedToBeBlocked(
            context = context,
            packageName = "com.instagram.android",
            savedPreferencesLoader = loader,
            defaultLauncher = "com.android.launcher",
            isScheduleActive = false
        )
        assertFalse("Without active schedule or manual session, app must NOT be blocked", inactiveResult.isBlocked)

        // 2. With active schedule, non-whitelisted app MUST be blocked
        val scheduledBlockResult = blocker.doesAppNeedToBeBlocked(
            context = context,
            packageName = "com.instagram.android",
            savedPreferencesLoader = loader,
            defaultLauncher = "com.android.launcher",
            isScheduleActive = true
        )
        assertTrue("During scheduled focus session, non-whitelisted app MUST be blocked", scheduledBlockResult.isBlocked)

        // 3. With active schedule, whitelisted app must NOT be blocked
        val whitelistedResult = blocker.doesAppNeedToBeBlocked(
            context = context,
            packageName = "com.allowed.work",
            savedPreferencesLoader = loader,
            defaultLauncher = "com.android.launcher",
            isScheduleActive = true
        )
        assertFalse("During scheduled focus session, whitelisted app must NOT be blocked", whitelistedResult.isBlocked)
    }
}
