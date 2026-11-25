package com.alhaq.amnishield.blockers

import com.alhaq.amnishield.data.blockers.BlockInterceptionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainBlocklistEngineTest {

    @Test
    fun findMatchingSuffix_matchesExactAndSubdomainsWithoutFalsePositives() {
        val engine = DomainBlocklistEngine()
        engine.loadDomainsForTest(
            listOf(
                "\uFEFFpornhub.com\r",
                "sex.com",
                "*.xvideos.com",
                "www.bet365.com"
            )
        )

        assertTrue(engine.isLoaded())
        assertEquals(4, engine.domainCount())

        // Exact, BOM-prefixed first line, and subdomain matches
        assertEquals("pornhub.com", engine.findMatchingSuffix("pornhub.com"))
        assertEquals("pornhub.com", engine.findMatchingSuffix("de.pornhub.com"))
        assertEquals("sex.com", engine.findMatchingSuffix("sex.com"))
        assertEquals("sex.com", engine.findMatchingSuffix("www.sex.com"))
        assertEquals("sex.com", engine.findMatchingSuffix("m.cdn.sex.com"))
        assertEquals("xvideos.com", engine.findMatchingSuffix("xvideos.com"))
        assertEquals("bet365.com", engine.findMatchingSuffix("mobile.bet365.com"))

        // Legitimate words/domains that contain a blocked substring must NOT false-positive
        assertNull(engine.findMatchingSuffix("sussex.com"))
        assertNull(engine.findMatchingSuffix("essex.co.uk"))
        assertNull(engine.findMatchingSuffix("middlesex.edu"))
        assertNull(engine.findMatchingSuffix("wikipedia.org"))
    }

    @Test
    fun loadGzipStreams_parsesBundledAdultAndGamblingAssetsWithBomSafety() {
        val adultAsset = listOf(
            File("src/main/assets/blocklists/adult_domains.gz"),
            File("app/src/main/assets/blocklists/adult_domains.gz")
        ).firstOrNull { it.exists() }
        val gamblingAsset = listOf(
            File("src/main/assets/blocklists/gambling_domains.gz"),
            File("app/src/main/assets/blocklists/gambling_domains.gz")
        ).firstOrNull { it.exists() }

        assertTrue("adult_domains.gz asset must exist", adultAsset != null)
        assertTrue("gambling_domains.gz asset must exist", gamblingAsset != null)

        val engine = DomainBlocklistEngine()
        adultAsset!!.inputStream().use { aStream ->
            gamblingAsset!!.inputStream().use { gStream ->
                engine.loadGzipStreamsForTest(listOf(aStream, gStream))
            }
        }

        assertEquals(17547 + 278862, engine.domainCount())
        // First line of each asset has a UTF-8 BOM; both must still match cleanly
        assertEquals("pornhub.com", engine.findMatchingSuffix("www.pornhub.com"))
        assertEquals("stake.com", engine.findMatchingSuffix("stake.com"))
        assertEquals("bet365.com", engine.findMatchingSuffix("sports.bet365.com"))
        assertNull(engine.findMatchingSuffix("wikipedia.org"))
    }

    @Test
    fun loadDomains_stripsUtf8BomAndCrLfFromBlocklistEntries() {
        val engine = DomainBlocklistEngine()
        engine.loadDomainsForTest(
            listOf(
                "\uFEFFpornhub.com\r",
                "\uFEFFstake.com\r\n",
                "www.betway.com"
            )
        )

        assertEquals(3, engine.domainCount())
        assertEquals("pornhub.com", engine.findMatchingSuffix("pornhub.com"))
        assertEquals("pornhub.com", engine.findMatchingSuffix("m.pornhub.com"))
        assertEquals("stake.com", engine.findMatchingSuffix("stake.com"))
        assertEquals("betway.com", engine.findMatchingSuffix("betway.com"))
    }

    @Test
    fun findMatchingSuffix_handles50000DomainsFast() {
        val domains = (1..50_000).map { "blocked-domain-$it.example" } + listOf("sex.com")
        val engine = DomainBlocklistEngine()
        engine.loadDomainsForTest(domains)

        assertEquals(50_001, engine.domainCount())
        assertEquals("blocked-domain-49999.example", engine.findMatchingSuffix("sub.blocked-domain-49999.example"))
        assertEquals("sex.com", engine.findMatchingSuffix("www.sex.com"))
        assertNull(engine.findMatchingSuffix("google.com"))
    }

    @Test
    fun blockInterceptionStyle_silentCleanExposesClearLabels() {
        assertEquals("Silent Clean", BlockInterceptionStyle.SILENT_CLEAN.titleLabel())
        assertFalse(BlockInterceptionStyle.SILENT_CLEAN.subtitleLabel().isBlank())
    }
}
