package com.alhaq.amnishield.blockers

import com.alhaq.amnishield.data.blockers.BlockInterceptionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlocklistEngineTest {

    @Test
    fun findMatchingSuffix_matchesExactAndSubdomainsWithoutFalsePositives() {
        val engine = DomainBlocklistEngine()
        engine.loadDomainsForTest(
            listOf(
                "sex.com",
                "*.pornhub.com",
                "www.bet365.com",
                "xvideos.com"
            )
        )

        assertTrue(engine.isLoaded())
        assertEquals(4, engine.domainCount())

        // Exact and subdomain matches
        assertEquals("sex.com", engine.findMatchingSuffix("sex.com"))
        assertEquals("sex.com", engine.findMatchingSuffix("www.sex.com"))
        assertEquals("sex.com", engine.findMatchingSuffix("m.cdn.sex.com"))
        assertEquals("pornhub.com", engine.findMatchingSuffix("pornhub.com"))
        assertEquals("pornhub.com", engine.findMatchingSuffix("de.pornhub.com"))
        assertEquals("bet365.com", engine.findMatchingSuffix("mobile.bet365.com"))

        // Legitimate words/domains that contain a blocked substring must NOT false-positive
        assertNull(engine.findMatchingSuffix("sussex.com"))
        assertNull(engine.findMatchingSuffix("essex.co.uk"))
        assertNull(engine.findMatchingSuffix("middlesex.edu"))
        assertNull(engine.findMatchingSuffix("wikipedia.org"))
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
