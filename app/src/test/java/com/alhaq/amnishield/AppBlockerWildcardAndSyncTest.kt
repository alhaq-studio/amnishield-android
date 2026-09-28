package com.alhaq.amnishield

import android.content.Context
import android.content.SharedPreferences
import com.alhaq.amnishield.blockers.AppBlocker
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppBlockerWildcardAndSyncTest {

    private lateinit var fakePrefs: InMemorySharedPreferences
    private lateinit var context: Context
    private lateinit var loader: SavedPreferencesLoader
    private lateinit var appBlocker: AppBlocker

    @Before
    fun setup() {
        fakePrefs = InMemorySharedPreferences()
        context = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.alhaq.amnishield"
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = fakePrefs
            override fun getSystemService(name: String): Any? = null
        }
        loader = SavedPreferencesLoader(
            context = context,
            injectedCompassionatePrefs = fakePrefs,
            injectedPremiumPrefs = fakePrefs
        )
        appBlocker = AppBlocker()
    }

    @Test
    fun testAppBlockerMatchesWildcardAllRule() {
        val wildcardRule = AppBlockScheduleRule(
            id = "cloud_sync_schedule",
            title = "Cloud Sync Schedule",
            packageName = "all",
            blockerType = BlockerType.APP,
            targets = listOf("all"),
            type = AppBlockScheduleRule.RuleType.BLOCK,
            recurrence = AppBlockScheduleRule.Recurrence.ALWAYS,
            isEnabled = true
        )
        loader.saveAppBlockerScheduleRules(mutableListOf(wildcardRule))

        // Normal third-party applications must be blocked by the 'all' rule
        val instaResult = appBlocker.doesAppNeedToBeBlocked("com.instagram.android", loader, context)
        assertTrue("Instagram must be blocked by wildcard 'all' rule", instaResult.isBlocked)

        val gameResult = appBlocker.doesAppNeedToBeBlocked("com.supercell.clashofclans", loader, context)
        assertTrue("Game must be blocked by wildcard 'all' rule", gameResult.isBlocked)

        // Essential emergency / framework packages must still remain exempt
        val systemUiResult = appBlocker.doesAppNeedToBeBlocked("com.android.systemui", loader, context)
        assertFalse("System UI must remain exempt despite 'all' rule", systemUiResult.isBlocked)
    }

    @Test
    fun testPolicySyncUpsertPreservesExistingLocalRules() {
        val localRule1 = AppBlockScheduleRule(
            id = "user_rule_insta",
            title = "Block Instagram",
            packageName = "com.instagram.android",
            blockerType = BlockerType.APP,
            targets = listOf("com.instagram.android"),
            isEnabled = true
        )
        val localRule2 = AppBlockScheduleRule(
            id = "user_rule_tiktok",
            title = "Block TikTok",
            packageName = "com.zhiliaoapp.musically",
            blockerType = BlockerType.APP,
            targets = listOf("com.zhiliaoapp.musically"),
            isEnabled = true
        )
        loader.saveAppBlockerScheduleRules(mutableListOf(localRule1, localRule2))

        val cloudRule = AppBlockScheduleRule(
            id = "cloud_sync_schedule",
            title = "Cloud Sync Schedule",
            packageName = "all",
            blockerType = BlockerType.APP,
            targets = listOf("all"),
            startMinute = 540,
            endMinute = 1020,
            isEnabled = true
        )

        // Additive upsert
        loader.upsertAppBlockerScheduleRule(cloudRule)

        val updatedRules = loader.loadAppBlockerScheduleRules()
        assertEquals("Must retain both local rules and the new cloud rule", 3, updatedRules.size)
        assertTrue(updatedRules.any { it.id == "user_rule_insta" })
        assertTrue(updatedRules.any { it.id == "user_rule_tiktok" })
        assertTrue(updatedRules.any { it.id == "cloud_sync_schedule" })

        // Updating cloud rule does not duplicate or wipe
        val updatedCloudRule = cloudRule.copy(startMinute = 600)
        loader.upsertAppBlockerScheduleRule(updatedCloudRule)

        val finalRules = loader.loadAppBlockerScheduleRules()
        assertEquals("Upserting existing rule must not duplicate rules", 3, finalRules.size)
        val fetchedCloud = finalRules.first { it.id == "cloud_sync_schedule" }
        assertEquals(600, fetchedCloud.startMinute)
    }
}
