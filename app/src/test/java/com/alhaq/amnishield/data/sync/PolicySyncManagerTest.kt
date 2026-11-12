package com.alhaq.amnishield.data.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import com.alhaq.amnishield.InMemorySharedPreferences
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.data.blockers.BlockerType
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.google.gson.JsonParser
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolicySyncManagerTest {

    private lateinit var prefsMap: MutableMap<String, InMemorySharedPreferences>
    private lateinit var broadcastedActions: MutableList<String>
    private lateinit var context: Context
    private lateinit var savedPrefs: SavedPreferencesLoader

    @Before
    fun setUp() {
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        prefsMap = mutableMapOf()
        broadcastedActions = mutableListOf()
        PolicySyncManager.broadcastListenerForTesting = { action ->
            broadcastedActions.add(action)
        }
        context = createTestContext(prefsMap, broadcastedActions)
        savedPrefs = SavedPreferencesLoader(context)
    }

    private fun createTestContext(
        store: MutableMap<String, InMemorySharedPreferences>,
        broadcasts: MutableList<String>
    ): Context {
        return object : ContextWrapper(null) {
            override fun getPackageName(): String = "com.alhaq.amnishield"
            override fun getApplicationContext(): Context = this

            @Synchronized
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return store.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }

            override fun sendBroadcast(intent: Intent?) {
                intent?.action?.let { broadcasts.add(it) }
            }

            override fun getSystemService(name: String): Any? = null
        }
    }

    @Test
    fun `blocked_domains and blocked_keywords are saved to separate stores and broadcast respective intents`() {
        val payloadJson = """
            {
              "schema_version": 2,
              "blocked_domains": ["tiktok.com", "https://www.reddit.com/", "GAMBLING.COM"],
              "blocked_keywords": ["casino", "Betting", "poker"]
            }
        """.trimIndent()
        val payload = JsonParser.parseString(payloadJson).asJsonObject

        val summary = PolicySyncManager.applyPolicyPayload(
            context = context,
            payload = payload,
            mode = PolicySyncManager.ApplyMode.MERGE
        )

        assertTrue(summary.success)
        assertEquals(3, summary.websitesCount)
        assertEquals(3, summary.keywordsCount)

        val websites = savedPrefs.loadBlockedWebsites()
        assertEquals(setOf("tiktok.com", "reddit.com", "gambling.com"), websites)
        assertFalse("Websites must not contain keywords", websites.contains("casino"))

        val keywords = savedPrefs.loadBlockedKeywords()
        assertEquals(setOf("casino", "betting", "poker"), keywords)
        assertFalse("Keywords must not contain domains", keywords.contains("tiktok.com"))

        assertTrue(savedPrefs.isWebsiteBlockerEnabled())
        assertTrue(savedPrefs.isWebsiteBlockerFeatureEnabled())
        assertTrue(savedPrefs.isKeywordBlockerEnabled())
        assertTrue(savedPrefs.isKeywordBlockerFeatureEnabled())

        assertTrue(broadcastedActions.contains(AmniShieldAccessibilityService.INTENT_ACTION_REFRESH_WEBSITE_BLOCKER))
        assertTrue(broadcastedActions.contains(PolicySyncManager.INTENT_ACTION_REFRESH_KEYWORD_BLOCKER))
    }

    @Test
    fun `v2 multi-schedule array deserializes all blocker types and supports numeric and string days`() {
        val payloadJson = """
            {
              "schema_version": 2,
              "schedules": [
                {
                  "id": "sched_app_1",
                  "groupId": "grp_work",
                  "title": "Work Hours App Block",
                  "packageName": "com.instagram.android",
                  "blockerType": "APP",
                  "targets": ["com.instagram.android", "com.zhiliaoapp.musically"],
                  "type": "BLOCK",
                  "recurrence": "WEEKLY",
                  "startMinute": 540,
                  "endMinute": 1020,
                  "selectedDays": [2, 3, 4, 5, 6],
                  "isEnabled": true
                },
                {
                  "id": "sched_web_2",
                  "groupId": "grp_web",
                  "title": "Night Website Block",
                  "blockerType": "WEBSITE",
                  "targets": ["youtube.com", "reddit.com"],
                  "targetWebsites": ["youtube.com", "reddit.com"],
                  "type": "BLOCK",
                  "recurrence": "WEEKLY",
                  "startMinute": 1320,
                  "endMinute": 1439,
                  "selectedDays": ["SUN", "Monday", "FRI", "7"],
                  "isEnabled": true
                },
                {
                  "id": "sched_kw_3",
                  "groupId": "grp_kw",
                  "title": "Keyword Guard",
                  "blockerType": "KEYWORD",
                  "targetKeywords": ["nsfw", "bet"],
                  "type": "CHEAT",
                  "recurrence": "DAILY",
                  "startMinute": 720,
                  "endMinute": 780,
                  "selectedDays": [1, 2, 3, 4, 5, 6, 7],
                  "isEnabled": false
                },
                {
                  "id": "sched_reels_4",
                  "title": "Reels Evening Lock",
                  "blockerType": "REELS",
                  "type": "BLOCK",
                  "recurrence": "DAILY",
                  "startMinute": 1080,
                  "endMinute": 1380,
                  "isEnabled": true
                },
                {
                  "id": "sched_focus_5",
                  "title": "Morning Deep Focus",
                  "blockerType": "FOCUS_MODE",
                  "type": "BLOCK",
                  "recurrence": "WEEKLY",
                  "startMinute": 360,
                  "endMinute": 540,
                  "selectedDays": ["TUE", "THU"],
                  "isEnabled": true
                }
              ]
            }
        """.trimIndent()
        val payload = JsonParser.parseString(payloadJson).asJsonObject

        val summary = PolicySyncManager.applyPolicyPayload(
            context = context,
            payload = payload,
            mode = PolicySyncManager.ApplyMode.FORCE_REPLACE
        )

        assertTrue(summary.success)
        assertEquals(5, summary.schedulesCount)

        val rules = savedPrefs.loadAppBlockerScheduleRules()
        assertEquals(5, rules.size)

        val appRule = rules.first { it.id == "sched_app_1" }
        assertEquals(BlockerType.APP, appRule.blockerType)
        assertEquals(listOf("com.instagram.android", "com.zhiliaoapp.musically"), appRule.targets)
        assertEquals(setOf(2, 3, 4, 5, 6), appRule.selectedDays)
        assertEquals(540, appRule.startMinute)
        assertEquals(1020, appRule.endMinute)
        assertTrue(appRule.isRuleEnabled)

        val webRule = rules.first { it.id == "sched_web_2" }
        assertEquals(BlockerType.WEBSITE, webRule.blockerType)
        assertEquals(listOf("youtube.com", "reddit.com"), webRule.targetWebsites)
        assertEquals(
            setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.FRIDAY, Calendar.SATURDAY),
            webRule.selectedDays
        )

        val kwRule = rules.first { it.id == "sched_kw_3" }
        assertEquals(BlockerType.KEYWORD, kwRule.blockerType)
        assertEquals(AppBlockScheduleRule.RuleType.CHEAT, kwRule.type)
        assertEquals(AppBlockScheduleRule.Recurrence.DAILY, kwRule.recurrence)
        assertEquals(listOf("nsfw", "bet"), kwRule.targetKeywords)
        assertFalse(kwRule.isRuleEnabled)

        val reelsRule = rules.first { it.id == "sched_reels_4" }
        assertEquals(BlockerType.REELS, reelsRule.blockerType)
        assertTrue(reelsRule.isRuleEnabled)

        val focusRule = rules.first { it.id == "sched_focus_5" }
        assertEquals(BlockerType.FOCUS_MODE, focusRule.blockerType)
        assertEquals(setOf(Calendar.TUESDAY, Calendar.THURSDAY), focusRule.selectedDays)
    }

    @Test
    fun `legacy v1 schedule object creates cloud_sync_schedule when enabled and removes it when disabled`() {
        val localRule = AppBlockScheduleRule(
            id = "local_custom_rule",
            title = "Local Custom Rule",
            packageName = "com.twitter.android",
            blockerType = BlockerType.APP,
            targets = listOf("com.twitter.android"),
            startMinute = 60,
            endMinute = 120,
            isEnabled = true
        )
        savedPrefs.saveAppBlockerScheduleRules(mutableListOf(localRule))

        val enableV1Json = """
            {
              "schedules": {
                "enabled": true,
                "start": "08:30",
                "end": "16:45",
                "days": [2, 3, 4, 5, 6]
              }
            }
        """.trimIndent()
        PolicySyncManager.applyPolicyPayload(
            context = context,
            payload = JsonParser.parseString(enableV1Json).asJsonObject,
            mode = PolicySyncManager.ApplyMode.MERGE
        )

        val afterEnable = savedPrefs.loadAppBlockerScheduleRules()
        assertEquals(2, afterEnable.size)
        val syncedV1 = afterEnable.firstOrNull { it.id == "cloud_sync_schedule" }
        assertNotNull(syncedV1)
        assertEquals(8 * 60 + 30, syncedV1!!.startMinute)
        assertEquals(16 * 60 + 45, syncedV1.endMinute)
        assertEquals(setOf(2, 3, 4, 5, 6), syncedV1.selectedDays)
        assertTrue(syncedV1.isRuleEnabled)

        val disableV1Json = """
            {
              "schedules": {
                "enabled": false,
                "start": "08:30",
                "end": "16:45"
              }
            }
        """.trimIndent()
        PolicySyncManager.applyPolicyPayload(
            context = context,
            payload = JsonParser.parseString(disableV1Json).asJsonObject,
            mode = PolicySyncManager.ApplyMode.MERGE
        )

        val afterDisable = savedPrefs.loadAppBlockerScheduleRules()
        assertFalse(
            "Disabled v1 schedule must remove cloud_sync_schedule",
            afterDisable.any { it.id == "cloud_sync_schedule" }
        )
        assertTrue(
            "Unrelated local rule must remain intact in MERGE mode",
            afterDisable.any { it.id == "local_custom_rule" }
        )
    }

    @Test
    fun `cached_policy_payload and last_policy_sync_timestamp are updated on applyPolicyPayload`() {
        assertEquals(0L, savedPrefs.getLastPolicySyncTimestamp())

        val payloadJson = """{"schema_version":2,"blocked_domains":["example.com"]}"""
        val payload = JsonParser.parseString(payloadJson).asJsonObject

        val summary = PolicySyncManager.applyPolicyPayload(context, payload, PolicySyncManager.ApplyMode.MERGE)
        assertTrue(summary.success)
        assertTrue(savedPrefs.getLastPolicySyncTimestamp() > 0L)

        val cached = savedPrefs.getCachedPolicyPayload()
        assertNotNull(cached)
        val parsedCached = JsonParser.parseString(cached).asJsonObject
        assertEquals(2, parsedCached.get("schema_version").asInt)
    }

    @Test
    fun `MERGE preserves local rules while FORCE_REPLACE overwrites local rules and reconciles feature toggles`() {
        savedPrefs.saveBlockedApps(setOf("com.local.one"))
        savedPrefs.saveBlockedWebsites(setOf("local-site.com"))
        savedPrefs.saveBlockedKeywords(setOf("local-keyword"))
        savedPrefs.upsertAppBlockerScheduleRule(
            AppBlockScheduleRule(
                id = "local_sched_1",
                title = "Local Schedule",
                packageName = "com.local.one",
                blockerType = BlockerType.APP,
                targets = listOf("com.local.one"),
                isEnabled = true
            )
        )
        savedPrefs.addAppLaunchLimitRule(
            AppLaunchLimitRule(
                id = "local_limit_1",
                packageName = "com.local.one",
                maxLaunches = 3,
                timePeriod = AppLaunchLimitRule.TimePeriod.DAILY
            )
        )

        // 1. Apply in MERGE mode
        val mergePayload = JsonParser.parseString(
            """
            {
              "schema_version": 2,
              "blocked_apps": ["com.remote.two"],
              "blocked_domains": ["remote-site.org"],
              "blocked_keywords": ["remote-keyword"],
              "schedules": [
                {
                  "id": "remote_sched_1",
                  "title": "Remote Schedule",
                  "packageName": "com.remote.two",
                  "blockerType": "APP",
                  "targets": ["com.remote.two"],
                  "startMinute": 60,
                  "endMinute": 180,
                  "isEnabled": true
                }
              ],
              "launch_limits": [
                {
                  "id": "remote_limit_1",
                  "packageName": "com.remote.two",
                  "maxLaunches": 7,
                  "timePeriod": "DAILY"
                }
              ]
            }
            """.trimIndent()
        ).asJsonObject

        PolicySyncManager.applyPolicyPayload(context, mergePayload, PolicySyncManager.ApplyMode.MERGE)

        assertEquals(setOf("com.local.one", "com.remote.two"), savedPrefs.loadBlockedApps())
        assertEquals(setOf("local-site.com", "remote-site.org"), savedPrefs.loadBlockedWebsites())
        assertEquals(setOf("local-keyword", "remote-keyword"), savedPrefs.loadBlockedKeywords())
        assertEquals(2, savedPrefs.loadAppBlockerScheduleRules().size)
        assertEquals(2, savedPrefs.loadAppLaunchLimitRules().size)

        // 2. Apply in FORCE_REPLACE mode where websites and local items were removed in Console
        val replacePayload = JsonParser.parseString(
            """
            {
              "schema_version": 2,
              "blocked_apps": ["com.console.enforced"],
              "blocked_domains": [],
              "blocked_keywords": ["strict-kw"],
              "schedules": [],
              "launch_limits": []
            }
            """.trimIndent()
        ).asJsonObject

        val replaceSummary = PolicySyncManager.applyPolicyPayload(
            context,
            replacePayload,
            PolicySyncManager.ApplyMode.FORCE_REPLACE
        )

        assertTrue(replaceSummary.success)
        assertEquals(PolicySyncManager.ApplyMode.FORCE_REPLACE, replaceSummary.mode)
        assertEquals(setOf("com.console.enforced"), savedPrefs.loadBlockedApps())
        assertTrue(savedPrefs.loadBlockedWebsites().isEmpty())
        assertEquals(setOf("strict-kw"), savedPrefs.loadBlockedKeywords())
        assertTrue(savedPrefs.loadAppBlockerScheduleRules().isEmpty())
        assertTrue(savedPrefs.loadAppLaunchLimitRules().isEmpty())

        assertTrue(savedPrefs.isAppBlockerFeatureEnabled())
        assertFalse("Website blocker must be disabled when remote domains and web schedules are empty", savedPrefs.isWebsiteBlockerEnabled())
        assertTrue(savedPrefs.isKeywordBlockerEnabled())
        assertFalse(savedPrefs.isReelBlockerEnabled())
    }

    @Test
    fun `pre-sync snapshot is saved automatically before FORCE_REPLACE and restores full local state`() {
        savedPrefs.saveAppBlockLists(mutableMapOf("Work Apps" to mutableSetOf("com.slack")))
        savedPrefs.saveBlockedApps(setOf("com.slack", "com.discord"))
        savedPrefs.saveBlockedWebsites(setOf("news.ycombinator.com"))
        savedPrefs.saveBlockedKeywords(setOf("spoilers"))
        savedPrefs.setAppBlockerFeatureEnabled(true)
        savedPrefs.setWebsiteBlockerEnabled(true)
        savedPrefs.setKeywordBlockerEnabled(true)
        savedPrefs.setReelBlockerEnabled(true)
        savedPrefs.setReelBlockerMode(ReelBlocker.MODE_BLOCK_AFTER_DAILY_COUNT)
        savedPrefs.setReelBlockerDailyLimit(45)
        savedPrefs.setReelBlockerYoutubeEnabled(true)
        savedPrefs.setFocusModeStrictness(1)
        savedPrefs.setFocusModeEarlyStopAllowed(false)

        savedPrefs.upsertAppBlockerScheduleRule(
            AppBlockScheduleRule(
                id = "pre_sync_sched",
                title = "Pre Sync Schedule",
                packageName = "com.discord",
                blockerType = BlockerType.APP,
                targets = listOf("com.discord"),
                startMinute = 120,
                endMinute = 240,
                isEnabled = true
            )
        )
        savedPrefs.addAppLaunchLimitRule(
            AppLaunchLimitRule(
                id = "pre_sync_limit",
                packageName = "com.discord",
                maxLaunches = 4,
                timePeriod = AppLaunchLimitRule.TimePeriod.WEEKLY,
                dayOfWeek = Calendar.FRIDAY
            )
        )

        assertFalse(savedPrefs.hasPreSyncRuleSnapshot())

        // Destructive remote FORCE_REPLACE
        val emptyRemotePayload = JsonParser.parseString(
            """
            {
              "schema_version": 2,
              "strict_mode": false,
              "blocked_apps": [],
              "blocked_domains": [],
              "blocked_keywords": [],
              "schedules": [],
              "launch_limits": []
            }
            """.trimIndent()
        ).asJsonObject

        PolicySyncManager.applyPolicyPayload(
            context,
            emptyRemotePayload,
            PolicySyncManager.ApplyMode.FORCE_REPLACE
        )

        assertTrue("Pre-sync snapshot must exist after FORCE_REPLACE", savedPrefs.hasPreSyncRuleSnapshot())
        assertTrue(savedPrefs.getPreSyncRuleSnapshotTimestamp() > 0L)
        assertTrue(savedPrefs.loadBlockedApps().isEmpty())
        assertTrue(savedPrefs.loadBlockedWebsites().isEmpty())
        assertTrue(savedPrefs.loadAppBlockerScheduleRules().isEmpty())

        // Restore pre-sync snapshot
        val restored = PolicySyncManager.restorePreSyncSnapshot(context)
        assertTrue(restored)

        assertEquals(setOf("com.slack", "com.discord"), savedPrefs.loadBlockedApps())
        assertEquals(setOf("com.slack"), savedPrefs.loadAppBlockLists()["Work Apps"])
        assertEquals(setOf("news.ycombinator.com"), savedPrefs.loadBlockedWebsites())
        assertEquals(setOf("spoilers"), savedPrefs.loadBlockedKeywords())
        assertEquals(1, savedPrefs.loadAppBlockerScheduleRules().size)
        assertEquals("pre_sync_sched", savedPrefs.loadAppBlockerScheduleRules().first().id)
        assertEquals(1, savedPrefs.loadAppLaunchLimitRules().size)
        assertEquals(4, savedPrefs.loadAppLaunchLimitRules().first().maxLaunches)
        assertTrue(savedPrefs.isReelBlockerEnabled())
        assertEquals(ReelBlocker.MODE_BLOCK_AFTER_DAILY_COUNT, savedPrefs.getReelBlockerMode())
        assertEquals(45, savedPrefs.getReelBlockerDailyLimit())
        assertTrue(savedPrefs.isReelBlockerYoutubeEnabled())
        assertEquals(1, savedPrefs.getFocusModeStrictness())
        assertFalse(savedPrefs.isFocusModeEarlyStopAllowed())
    }

    @Test
    fun `strict_mode, reel_blocker, launch_limits and exportLocalPolicyPayload round-trip accurately`() {
        val payloadJson = """
            {
              "schema_version": 2,
              "strict_mode": true,
              "allow_unblur": false,
              "blocked_apps": ["com.zhiliaoapp.musically"],
              "named_block_lists": {
                "Social": ["com.instagram.android"]
              },
              "blocked_domains": ["tiktok.com"],
              "blocked_keywords": ["shorts"],
              "blocked_categories": ["adult"],
              "reel_blocker": {
                "enabled": true,
                "mode": 1,
                "daily_limit_minutes": 25,
                "youtube": true,
                "instagram": true,
                "tiktok": false,
                "browser": true
              },
              "focus_mode": {
                "strictness": 1,
                "allow_early_stop": false,
                "always_whitelisted_apps": ["com.google.android.dialer"]
              },
              "schedules": [
                {
                  "id": "roundtrip_sched",
                  "groupId": "grp_1",
                  "title": "Focus Window",
                  "packageName": "all",
                  "blockerType": "APP",
                  "targets": ["com.instagram.android"],
                  "type": "BLOCK",
                  "recurrence": "WEEKLY",
                  "startMinute": 600,
                  "endMinute": 900,
                  "selectedDays": [2, 4, 6],
                  "isEnabled": true
                }
              ],
              "launch_limits": [
                {
                  "id": "roundtrip_limit",
                  "packageName": "com.instagram.android",
                  "maxLaunches": 5,
                  "timePeriod": "WEEKLY",
                  "dayOfWeek": 2,
                  "isEnabled": true
                }
              ]
            }
        """.trimIndent()

        PolicySyncManager.applyPolicyPayload(
            context,
            JsonParser.parseString(payloadJson).asJsonObject,
            PolicySyncManager.ApplyMode.FORCE_REPLACE
        )

        assertEquals(1, savedPrefs.getFocusModeStrictness())
        assertFalse(savedPrefs.isFocusModeEarlyStopAllowed())
        assertTrue(savedPrefs.isKeywordBlockerAdultPackEnabled())
        assertTrue(savedPrefs.isReelBlockerEnabled())
        assertEquals(1, savedPrefs.getReelBlockerMode())
        assertEquals(25, savedPrefs.getReelBlockerDailyLimit())
        assertTrue(savedPrefs.isReelBlockerYoutubeEnabled())
        assertTrue(savedPrefs.isReelBlockerInstagramEnabled())
        assertFalse(savedPrefs.isReelBlockerTiktokEnabled())
        assertTrue(savedPrefs.isReelBlockerBrowserEnabled())

        val exported = PolicySyncManager.exportLocalPolicyPayload(context)
        assertEquals(2, exported.get("schema_version").asInt)
        assertTrue(exported.get("strict_mode").asBoolean)

        // Apply exported payload to a brand-new context and verify parity
        val freshStore = mutableMapOf<String, InMemorySharedPreferences>()
        val freshContext = createTestContext(freshStore, mutableListOf())
        val freshPrefs = SavedPreferencesLoader(freshContext)

        PolicySyncManager.applyPolicyPayload(
            freshContext,
            exported,
            PolicySyncManager.ApplyMode.FORCE_REPLACE
        )

        assertEquals(savedPrefs.loadBlockedApps(), freshPrefs.loadBlockedApps())
        assertEquals(savedPrefs.loadBlockedWebsites(), freshPrefs.loadBlockedWebsites())
        assertEquals(savedPrefs.loadBlockedKeywords(), freshPrefs.loadBlockedKeywords())
        assertEquals(savedPrefs.getFocusModeStrictness(), freshPrefs.getFocusModeStrictness())
        assertEquals(savedPrefs.isFocusModeEarlyStopAllowed(), freshPrefs.isFocusModeEarlyStopAllowed())
        assertEquals(savedPrefs.getReelBlockerDailyLimit(), freshPrefs.getReelBlockerDailyLimit())
        assertEquals(savedPrefs.loadAppBlockerScheduleRules().size, freshPrefs.loadAppBlockerScheduleRules().size)
        assertEquals(savedPrefs.loadAppLaunchLimitRules().size, freshPrefs.loadAppLaunchLimitRules().size)
    }
}
