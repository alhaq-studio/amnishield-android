package com.alhaq.amnishield

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.data.blockers.*
import com.alhaq.amnishield.services.AmniShieldAccessibilityService
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AlwaysOnShieldsConfigTest {

    private lateinit var prefsByName: MutableMap<String, InMemorySharedPreferences>
    private lateinit var broadcastedIntents: MutableList<Intent>
    private lateinit var context: Context
    private lateinit var loader: SavedPreferencesLoader

    @Before
    fun setup() {
        prefsByName = mutableMapOf()
        broadcastedIntents = mutableListOf()
        context = object : android.content.ContextWrapper(null) {
            override fun getPackageName(): String = "com.alhaq.amnishield"
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                val key = name ?: "default"
                return prefsByName.getOrPut(key) { InMemorySharedPreferences() }
            }
            override fun getSystemService(name: String): Any? = null
            override fun sendBroadcast(intent: Intent?) {
                if (intent != null) {
                    broadcastedIntents.add(intent)
                }
            }
        }
        val defaultPrefs = prefsByName.getOrPut("default") { InMemorySharedPreferences() }
        loader = SavedPreferencesLoader(
            context = context,
            injectedCompassionatePrefs = defaultPrefs,
            injectedPremiumPrefs = defaultPrefs
        )
    }

    @Test
    fun `website guard effectiveDomains unions verified preloaded packs and normalizes custom domains`() {
        val adultCount = PreloadedPacks.websitePacks.first { it.id == "web_adult_blocklist" }.items.size
        val gamblingCount = PreloadedPacks.websitePacks.first { it.id == "web_gambling_blocklist" }.items.size
        assertEquals(51, adultCount)
        assertEquals(40, gamblingCount)

        val config = WebsiteGuardConfig(
            enabled = true,
            enabledPackIds = setOf("web_adult_blocklist", "web_gambling_blocklist"),
            customDomains = listOf(
                "https://www.Example.COM/path?q=1",
                "example.com",
                "  SUB.Site.org/test  ",
                ""
            )
        ).sanitize()

        assertEquals(listOf("example.com", "sub.site.org"), config.customDomains)

        val effective = config.effectiveDomains()
        assertTrue(effective.contains("example.com"))
        assertTrue(effective.contains("sub.site.org"))
        assertTrue(effective.size >= adultCount + gamblingCount)
    }

    @Test
    fun `keyword guard effectiveKeywords unions verified preloaded packs and deduplicates custom terms`() {
        val adultKeywordCount = PreloadedPacks.keywordPacks.first { it.id == "kw_adult" }.items.size
        assertEquals(26, adultKeywordCount)

        val config = KeywordGuardConfig(
            enabled = true,
            enabledPackIds = setOf("kw_adult"),
            customKeywords = listOf("  Spoiler ", "spoiler", "DISTRACTION", "")
        ).sanitize()

        assertEquals(listOf("spoiler", "distraction"), config.customKeywords)

        val effective = config.effectiveKeywords()
        assertTrue(effective.contains("spoiler"))
        assertTrue(effective.contains("distraction"))
        assertTrue(effective.size >= adultKeywordCount + 2)
    }

    @Test
    fun `always on shields config persists round trip and counts active shields accurately`() {
        val config = AlwaysOnShieldsConfig(
            reels = ReelsGuardConfig(
                enabled = true,
                blockYoutubeShorts = true,
                blockInstagramReels = true,
                blockTikTok = false,
                blockInBrowserShorts = true,
                limitMode = ReelsLimitMode.LIMIT_BY_COUNT,
                dailyReelLimit = 12,
                lockConfig = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 45)
            ),
            website = WebsiteGuardConfig(
                enabled = true,
                enabledPackIds = setOf("web_adult_blocklist"),
                blockIncognitoMode = true,
                blockUnsupportedBrowsers = true,
                customDomains = listOf("news.ycombinator.com")
            ),
            keyword = KeywordGuardConfig(
                enabled = false,
                customKeywords = listOf("rumors")
            )
        )

        loader.saveAlwaysOnShieldsConfig(config)
        val loaded = loader.loadAlwaysOnShieldsConfig()

        assertEquals(2, loaded.activeCount())
        assertEquals(listOf(ShieldType.REELS, ShieldType.WEBSITE), loaded.activeShieldTypes())
        assertEquals(ReelsLimitMode.LIMIT_BY_COUNT, loaded.reels.limitMode)
        assertEquals(12, loaded.reels.dailyReelLimit)
        assertTrue(loaded.reels.blockInBrowserShorts)
        assertEquals(45, loaded.reels.lockConfig.delaySeconds)
        assertTrue(loaded.website.enabledPackIds.contains("web_adult_blocklist"))
        assertTrue(loaded.website.blockIncognitoMode)
        assertTrue(loaded.website.blockUnsupportedBrowsers)
        assertEquals(listOf("news.ycombinator.com"), loaded.website.customDomains)
        assertFalse(loaded.keyword.enabled)
    }

    @Test
    fun `syncUnifiedRulesToLegacyEngines unions always on shields with custom rules and wires daily reel limit`() {
        val alwaysOn = AlwaysOnShieldsConfig(
            reels = ReelsGuardConfig(
                enabled = true,
                blockYoutubeShorts = false,
                blockInstagramReels = true,
                blockTikTok = false,
                blockInBrowserShorts = true,
                limitMode = ReelsLimitMode.LIMIT_BY_COUNT,
                dailyReelLimit = 8
            ),
            website = WebsiteGuardConfig(
                enabled = true,
                customDomains = listOf("alwaysblocked.com")
            ),
            keyword = KeywordGuardConfig(
                enabled = true,
                customKeywords = listOf("alwayskeyword"),
                ignoredPackages = setOf("com.whatsapp")
            )
        )
        val customRule = UnifiedBlockRule(
            id = "custom_1",
            name = "Deep Work",
            status = BlockRuleStatus.ACTIVE,
            appTargets = mapOf(
                "com.reddit.frontpage" to AppInterceptionLevel.BLOCK_ENTIRE_APP,
                "com.google.android.youtube" to AppInterceptionLevel.BLOCK_SHORTFORM_ONLY
            ),
            blockedDomains = listOf("reddit.com"),
            blockedKeywords = listOf("customkeyword")
        )

        loader.saveUnifiedBlockRules(listOf(customRule))
        loader.saveAlwaysOnShieldsConfig(alwaysOn)

        val blockedWebsites = loader.loadBlockedWebsites()
        assertTrue("Always-On domain must be unioned", blockedWebsites.contains("alwaysblocked.com"))
        assertTrue("Custom rule domain must be unioned", blockedWebsites.contains("reddit.com"))

        val blockedKeywords = loader.loadBlockedKeywords()
        assertTrue("Always-On keyword must be unioned", blockedKeywords.contains("alwayskeyword"))
        assertTrue("Custom rule keyword must be unioned", blockedKeywords.contains("customkeyword"))

        assertTrue(loader.isReelBlockerEnabled(false))
        assertEquals(ReelBlocker.MODE_BLOCK_AFTER_DAILY_COUNT, loader.getReelBlockerMode())
        assertEquals(8, loader.getReelBlockerDailyLimit())
        assertTrue(loader.isReelBlockerInstagramEnabled(false))
        assertTrue(loader.isReelBlockerYoutubeEnabled(false))
        assertTrue(loader.isReelBlockerBrowserEnabled(false))

        assertEquals(listOf("com.whatsapp"), loader.getKeywordBlockerIgnoredApps())

        // Verify all 5 engine refresh broadcasts (Always-On, Unified, App, Reel, Keyword) are dispatched
        assertTrue(
            "Expected at least 5 engine refresh broadcasts per sync, got ${broadcastedIntents.size}",
            broadcastedIntents.size >= 5
        )
    }

    @Test
    fun `always on targets remain active when custom rules list is empty`() {
        val alwaysOn = AlwaysOnShieldsConfig(
            website = WebsiteGuardConfig(
                enabled = true,
                customDomains = listOf("distracting.example")
            ),
            keyword = KeywordGuardConfig(
                enabled = true,
                customKeywords = listOf("gossip")
            )
        )
        loader.saveAlwaysOnShieldsConfig(alwaysOn)
        loader.saveUnifiedBlockRules(emptyList())

        assertTrue(loader.loadBlockedWebsites().contains("distracting.example"))
        assertTrue(loader.loadBlockedKeywords().contains("gossip"))
    }
}
