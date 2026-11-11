package com.alhaq.amnishield.ui.viewmodel

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import com.alhaq.amnishield.InMemorySharedPreferences
import com.alhaq.amnishield.data.datastore.AmniShieldDataStore
import com.alhaq.amnishield.data.db.AmniShieldRuleDatabase
import com.alhaq.amnishield.security.AuthType
import com.alhaq.amnishield.ui.state.ScheduleRule
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class AmniShieldViewModelPersistenceTest {

    private lateinit var prefsMap: ConcurrentHashMap<String, InMemorySharedPreferences>
    private lateinit var broadcastActions: MutableList<String>
    private lateinit var fakeContext: Context
    private lateinit var loader: SavedPreferencesLoader
    private lateinit var dataStore: AmniShieldDataStore
    private lateinit var ruleDb: AmniShieldRuleDatabase

    @Before
    fun setUp() {
        AmniShieldRuleDatabase.clearInstances()
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        prefsMap = ConcurrentHashMap()
        broadcastActions = mutableListOf()
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "com.alhaq.deenshield"
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                return prefsMap.getOrPut(name ?: "default") { InMemorySharedPreferences() }
            }
            override fun sendBroadcast(intent: Intent?) {
                intent?.action?.let { broadcastActions.add(it) }
            }
        }
        loader = SavedPreferencesLoader(fakeContext)
        dataStore = AmniShieldDataStore.getInstance(fakeContext)
        ruleDb = AmniShieldRuleDatabase.getInstance(fakeContext)
    }

    @After
    fun tearDown() {
        SavedPreferencesLoader.invalidateScheduleRulesCache()
        AmniShieldRuleDatabase.clearInstances()
    }

    @Test
    fun bindPersistence_persistsScheduleRuleCrudAndRestoresAcrossViewModelInstances() {
        val viewModel1 = AmniShieldViewModel()
        viewModel1.bindPersistence(fakeContext, loader, dataStore, ruleDb)

        val rule = ScheduleRule(
            id = "vm_rule_1",
            name = "Focus Coding",
            appOrCategory = "com.twitter.android",
            selectedApps = listOf("com.twitter.android"),
            isAlwaysBlockEnabled = true,
            isActive = true
        )
        viewModel1.addScheduleRule(rule)

        assertEquals(1, viewModel1.state.value.scheduleRules.size)
        assertEquals(1, loader.loadAppBlockerScheduleRules().size)
        assertEquals(1, ruleDb.appBlockRulesFlow.value.size)

        // Toggle active state
        viewModel1.toggleScheduleRuleActive("vm_rule_1")
        assertFalse(viewModel1.state.value.scheduleRules.first().isActive)
        assertFalse(loader.loadAppBlockerScheduleRules().first().isRuleEnabled)

        // Update rule auth
        viewModel1.updateRuleAuth("vm_rule_1", AuthType.GLOBAL_PIN, null, null)
        assertEquals(AuthType.GLOBAL_PIN, loader.loadAppBlockerScheduleRules().first().authType)

        // Create a second ViewModel instance and verify state hydration from persistence
        val viewModel2 = AmniShieldViewModel()
        viewModel2.bindPersistence(fakeContext, loader, dataStore, ruleDb)
        assertEquals(1, viewModel2.state.value.scheduleRules.size)
        assertEquals("vm_rule_1", viewModel2.state.value.scheduleRules.first().id)
        assertFalse(viewModel2.state.value.scheduleRules.first().isActive)
        assertEquals(AuthType.GLOBAL_PIN, viewModel2.state.value.scheduleRules.first().authType)

        // Delete rule
        viewModel2.deleteScheduleRule("vm_rule_1")
        assertTrue(viewModel2.state.value.scheduleRules.isEmpty())
        assertTrue(loader.loadAppBlockerScheduleRules().isEmpty())
        assertTrue(ruleDb.appBlockRulesFlow.value.isEmpty())
    }

    @Test
    fun bindPersistence_persistsKeywordsWebsitesAndFeatureToggles() {
        val viewModel = AmniShieldViewModel()
        viewModel.bindPersistence(fakeContext, loader, dataStore, ruleDb)

        viewModel.addKeyword("casino")
        viewModel.addBlockedWebsite("Example.com")

        assertTrue(loader.loadBlockedKeywords().contains("casino"))
        assertTrue(loader.loadBlockedWebsites().contains("example.com"))

        val initialAppBlocker = viewModel.state.value.isAppBlockerEnabled
        viewModel.toggleAppBlocker()
        assertEquals(!initialAppBlocker, loader.isAppBlockerFeatureEnabled(true))

        viewModel.removeKeyword("casino")
        viewModel.removeBlockedWebsite("example.com")
        assertFalse(loader.loadBlockedKeywords().contains("casino"))
        assertFalse(loader.loadBlockedWebsites().contains("example.com"))
    }
}
