package com.alhaq.amnishield.data.db

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alhaq.amnishield.InMemorySharedPreferences
import com.alhaq.amnishield.data.blockers.AppBlockScheduleRule
import com.alhaq.amnishield.data.blockers.AppLaunchLimitRule
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class AmniShieldRuleDatabaseTest {

    private lateinit var prefsMap: ConcurrentHashMap<String, InMemorySharedPreferences>
    private lateinit var fakeContext: Context
    private lateinit var loader: SavedPreferencesLoader
    private lateinit var ruleDb: AmniShieldRuleDatabase

    @Before
    fun setUp() {
        AmniShieldRuleDatabase.clearInstances()
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        prefsMap = ConcurrentHashMap()
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.deenshield"
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }
        }
        loader = SavedPreferencesLoader(fakeContext)
        ruleDb = AmniShieldRuleDatabase.getInstance(fakeContext)
    }

    @After
    fun tearDown() {
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        AmniShieldRuleDatabase.clearInstances()
    }

    @Test
    fun appBlockRule_roundTripConversionPreservesFieldsAndAuth() {
        val original = ScheduleRule(
            id = "rule_101",
            name = "Deep Work Block",
            appOrCategory = "com.instagram.android",
            restrictionType = "Block Schedule",
            startTime = "09:00",
            endTime = "17:00",
            days = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
            limitValue = 2,
            isActive = true,
            selectedApps = listOf("com.instagram.android"),
            isAlwaysBlockEnabled = false,
            isScheduleEnabled = true,
            authType = AuthType.RULE_PIN,
            rulePasswordHash = "pbkdf2_hash_xyz",
            rulePasswordSalt = "salt_abc"
        )

        val entity = AppBlockRule.fromScheduleRule(original)
        assertEquals("rule_101", entity.id)
        assertEquals("com.instagram.android", entity.packageName)
        assertEquals(540, entity.startMinute)
        assertEquals(1020, entity.endMinute)
        assertEquals(AuthType.RULE_PIN, entity.authType)
        assertEquals("pbkdf2_hash_xyz", entity.rulePasswordHash)

        val roundTrip = entity.toScheduleRule()
        assertEquals(original.id, roundTrip.id)
        assertEquals(original.name, roundTrip.name)
        assertEquals("09:00", roundTrip.startTime)
        assertEquals("17:00", roundTrip.endTime)
        assertEquals(original.authType, roundTrip.authType)
        assertEquals(original.rulePasswordHash, roundTrip.rulePasswordHash)
        assertEquals(original.rulePasswordSalt, roundTrip.rulePasswordSalt)
    }

    @Test
    fun screenTimeLimitRule_roundTripConversionPreservesFieldsAndAuth() {
        val original = AppLaunchLimitRule(
            id = "limit_202",
            packageName = "com.zhiliaoapp.musically",
            maxLaunches = 5,
            timePeriod = AppLaunchLimitRule.TimePeriod.DAILY,
            dayOfWeek = Calendar.MONDAY,
            createdAt = 1700001000L,
            authType = AuthType.GLOBAL_PIN,
            rulePasswordHash = null,
            rulePasswordSalt = null
        )

        val entity = ScreenTimeLimitRule.fromLaunchLimitRule(original)
        assertEquals("limit_202", entity.id)
        assertEquals("com.zhiliaoapp.musically", entity.packageName)
        assertEquals(5, entity.maxLaunches)
        assertEquals("DAILY", entity.timePeriod)
        assertEquals(AuthType.GLOBAL_PIN, entity.authType)

        val roundTrip = entity.toLaunchLimitRule()
        assertEquals(original.id, roundTrip.id)
        assertEquals(original.packageName, roundTrip.packageName)
        assertEquals(original.maxLaunches, roundTrip.maxLaunches)
        assertEquals(original.timePeriod, roundTrip.timePeriod)
        assertEquals(original.authType, roundTrip.authType)
    }

    @Test
    fun savedPreferencesLoader_syncsScheduleRulesAndLaunchLimitsToRelationalDb() {
        val rules = mutableListOf(
            AppBlockScheduleRule(
                id = "sync_rule_1",
                title = "YouTube Schedule",
                packageName = "com.google.android.youtube",
                type = AppBlockScheduleRule.RuleType.BLOCK,
                recurrence = AppBlockScheduleRule.Recurrence.ALWAYS
            ),
            AppBlockScheduleRule(
                id = "sync_rule_2",
                title = "Reddit Schedule",
                packageName = "com.reddit.frontpage",
                type = AppBlockScheduleRule.RuleType.BLOCK,
                recurrence = AppBlockScheduleRule.Recurrence.DAILY,
                startMinute = 60,
                endMinute = 180
            )
        )

        loader.saveAppBlockerScheduleRules(rules)

        val dbEntities = ruleDb.appBlockRulesFlow.value
        assertEquals(2, dbEntities.size)
        assertTrue(dbEntities.any { it.id == "sync_rule_1" && it.packageName == "com.google.android.youtube" })
        assertTrue(dbEntities.any { it.id == "sync_rule_2" && it.packageName == "com.reddit.frontpage" })

        val launchRules = listOf(
            AppLaunchLimitRule(
                id = "launch_1",
                packageName = "com.instagram.android",
                maxLaunches = 3,
                timePeriod = AppLaunchLimitRule.TimePeriod.DAILY
            )
        )
        loader.saveAppLaunchLimitRules(launchRules)

        val limitEntities = ruleDb.screenTimeLimitRulesFlow.value
        assertEquals(1, limitEntities.size)
        assertEquals("com.instagram.android", limitEntities.first().packageName)
        assertEquals(3, limitEntities.first().maxLaunches)
    }

    @Test
    fun databaseMigrations_sqlStatementsDefineV1ToV2Columns() {
        assertEquals(2, AmniShieldDatabaseMigrations.CURRENT_VERSION)
        assertEquals(6, AmniShieldDatabaseMigrations.MIGRATION_1_2_STATEMENTS.size)
        assertTrue(AmniShieldDatabaseMigrations.CREATE_TABLE_APP_BLOCK_RULES_V2.contains("authType TEXT NOT NULL DEFAULT 'NONE'"))
        assertTrue(AmniShieldDatabaseMigrations.CREATE_TABLE_SCREEN_TIME_LIMIT_RULES_V2.contains("rulePasswordSalt TEXT DEFAULT NULL"))
    }
}
