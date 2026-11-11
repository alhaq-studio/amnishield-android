package com.alhaq.amnishield.provider

import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import com.alhaq.amnishield.InMemorySharedPreferences
import com.alhaq.amnishield.contract.GuardianContract
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GuardianContentProviderTest {

    private lateinit var prefsMap: MutableMap<String, InMemorySharedPreferences>
    private lateinit var broadcastsSent: MutableList<Intent>
    private lateinit var notifiedUris: MutableList<Uri>
    private lateinit var testContext: Context

    @Before
    fun setUp() {
        prefsMap = mutableMapOf()
        broadcastsSent = mutableListOf()
        notifiedUris = mutableListOf()

        val mockResolver = object : ContentResolver(null) {
            override fun notifyChange(uri: Uri, observer: android.database.ContentObserver?) {
                notifiedUris.add(uri)
            }
        }

        testContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.amnishield"

            @Synchronized
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }

            override fun sendBroadcast(intent: Intent?) {
                if (intent != null) broadcastsSent.add(intent)
            }

            override fun sendBroadcast(intent: Intent?, receiverPermission: String?) {
                if (intent != null) broadcastsSent.add(intent)
            }

            override fun getContentResolver(): ContentResolver = mockResolver

            override fun getPackageManager(): PackageManager {
                throw UnsupportedOperationException("Not implemented")
            }
        }
    }

    private fun createTestProvider(authorized: Boolean = true): GuardianContentProvider {
        return object : GuardianContentProvider(testContext) {
            override fun isCallerAuthorized(): Boolean = authorized
        }
    }

    @Test
    fun testStartFocusSession_activatesFocusModeAndNotifies() {
        val result = GuardianContentProvider.startFocusSession(testContext, 45)
        assertTrue(result.isActive)
        assertEquals(45, result.remainingMinutes)

        // Verify broadcast sent
        assertTrue(broadcastsSent.isNotEmpty())

        // Verify state in SavedPreferencesLoader
        val loader = SavedPreferencesLoader(testContext)
        val focusData = loader.getFocusModeData()
        assertTrue(focusData.isTurnedOn)
        assertTrue(focusData.endTime > System.currentTimeMillis())
    }

    @Test
    fun testStopFocusSession_deactivatesFocusModeAndNotifies() {
        // Start 60 min session first
        GuardianContentProvider.startFocusSession(testContext, 60)
        broadcastsSent.clear()

        // Stop session
        val stopResult = GuardianContentProvider.stopFocusSession(testContext)
        assertFalse(stopResult.isActive)
        assertEquals(0, stopResult.remainingMinutes)

        // Verify broadcast sent and state cleared
        assertTrue(broadcastsSent.isNotEmpty())
        val loader = SavedPreferencesLoader(testContext)
        val focusData = loader.getFocusModeData()
        assertFalse(focusData.isTurnedOn)
    }

    @Test
    fun testComputeSnapshot_returnsValidData() {
        val snapshot = GuardianContentProvider.computeSnapshot(testContext)
        assertNotNull(snapshot)
        assertEquals(1, snapshot.apiVersion)
        assertFalse(snapshot.isFocusActive)
        assertEquals(0, snapshot.focusRemainingMinutes)
        assertEquals(0, snapshot.focusTodayMinutes)
        assertEquals(0, snapshot.focusSessionsCount)
        assertEquals(0, snapshot.reelsScrolledToday)
        assertEquals(0L, snapshot.reelsWatchSecondsToday)
        assertEquals(1, snapshot.streakDays)
        assertEquals(20, snapshot.shieldScore)
    }

    @Test
    fun testCall_authorized_returnsNonNullBundle() {
        val provider = createTestProvider(authorized = true)
        val startResult = provider.call(GuardianContract.METHOD_START_FOCUS, null, Bundle())
        assertNotNull(startResult)

        val stopResult = provider.call(GuardianContract.METHOD_STOP_FOCUS, null, null)
        assertNotNull(stopResult)

        val snapResult = provider.call(GuardianContract.METHOD_GET_SNAPSHOT, null, null)
        assertNotNull(snapResult)
    }

    @Test
    fun testUnauthorizedCaller_throwsSecurityException() {
        val provider = createTestProvider(authorized = false)

        try {
            provider.call(GuardianContract.METHOD_GET_SNAPSHOT, null, null)
            fail("Expected SecurityException for unauthorized caller")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("Caller is not authorized") == true)
        }
    }
}
