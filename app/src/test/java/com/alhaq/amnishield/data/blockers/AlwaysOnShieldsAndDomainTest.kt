package com.alhaq.amnishield.data.blockers

import com.alhaq.amnishield.blockers.KeywordBlocker
import com.alhaq.amnishield.blockers.ReelBlocker
import com.alhaq.amnishield.blockers.WebsiteBlockerDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlwaysOnShieldsAndDomainTest {

    private fun requiresWeakeningGate(
        isShieldEnabled: Boolean,
        currentLock: ShieldLockConfig,
        newLock: ShieldLockConfig,
        hasGlobalPin: Boolean
    ): Boolean {
        val isShorteningPauseDelay = currentLock.mode == ShieldLockMode.PAUSE_DELAY &&
            newLock.mode == ShieldLockMode.PAUSE_DELAY &&
            newLock.delaySeconds < currentLock.delaySeconds
        return isShieldEnabled &&
            currentLock.isActivelyLocked(hasGlobalPin) &&
            (newLock.strengthRank(hasGlobalPin) < currentLock.strengthRank(hasGlobalPin) ||
                newLock.mode != currentLock.mode ||
                isShorteningPauseDelay)
    }

    @Test
    fun shieldLockConfig_weakeningAndPauseDelayShorteningRequireGate_lengtheningDoesNot() {
        val delay30 = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 30)
        val delay10 = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 10)
        val delay60 = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 60)
        val noLock = ShieldLockConfig(mode = ShieldLockMode.NONE)
        val customPin = ShieldLockConfig(
            mode = ShieldLockMode.CUSTOM_PIN,
            customPinHash = "hash",
            customPinSalt = "salt"
        )

        assertTrue(delay30.isActivelyLocked(hasGlobalPin = false))
        assertEquals(1, delay30.strengthRank(hasGlobalPin = false))
        assertEquals(3, customPin.strengthRank(hasGlobalPin = false))
        assertEquals(0, noLock.strengthRank(hasGlobalPin = false))

        // Shortening PAUSE_DELAY weakens friction -> must gate
        assertTrue(requiresWeakeningGate(true, delay30, delay10, hasGlobalPin = false))
        // Lengthening PAUSE_DELAY increases friction -> must NOT gate
        assertFalse(requiresWeakeningGate(true, delay30, delay60, hasGlobalPin = false))
        // Keeping identical PAUSE_DELAY -> must NOT gate
        assertFalse(requiresWeakeningGate(true, delay30, delay30, hasGlobalPin = false))
        // Downgrading Custom PIN to Pause Delay or None -> must gate
        assertTrue(requiresWeakeningGate(true, customPin, delay30, hasGlobalPin = false))
        assertTrue(requiresWeakeningGate(true, customPin, noLock, hasGlobalPin = false))
        // Upgrading Pause Delay -> Custom PIN also changes mode -> currently gated, disabled shield never gates
        assertFalse(requiresWeakeningGate(false, customPin, noLock, hasGlobalPin = false))
    }

    @Test
    fun shieldLockConfig_unconfiguredGlobalPinAndProExpiryNeverTrapDowngradeLogic() {
        val inheritPin = ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN)
        assertFalse(inheritPin.isActivelyLocked(hasGlobalPin = false))
        assertTrue(inheritPin.isActivelyLocked(hasGlobalPin = true))
        assertEquals(2, inheritPin.strengthRank(hasGlobalPin = true))
        assertEquals(0, inheritPin.strengthRank(hasGlobalPin = false))
    }

    @Test
    fun websiteGuardConfig_normalizeDomainInputIsolatesValidDomainsFromKeywords() {
        assertEquals("youtube.com", WebsiteGuardConfig.normalizeDomainInput("https://www.YouTube.com/shorts/abc"))
        assertEquals("news.ycombinator.com", WebsiteGuardConfig.normalizeDomainInput("http://news.ycombinator.com/?p=1"))
        assertEquals("reddit.com", WebsiteGuardConfig.normalizeDomainInput("  reddit.com/r/all#top "))

        // Plain keywords or malformed inputs must be rejected
        assertNull(WebsiteGuardConfig.normalizeDomainInput("shorts"))
        assertNull(WebsiteGuardConfig.normalizeDomainInput("blocked keyword phrase"))
        assertNull(WebsiteGuardConfig.normalizeDomainInput("a."))
        assertNull(WebsiteGuardConfig.normalizeDomainInput(""))
    }

    @Test
    fun unifiedBlockRule_shortformCapableLimitedToSupportedPlatforms() {
        assertTrue(UnifiedBlockRule.isShortformCapable("com.instagram.android"))
        assertTrue(UnifiedBlockRule.isShortformCapable("com.google.android.youtube"))
        assertTrue(UnifiedBlockRule.isShortformCapable("com.zhiliaoapp.musically"))
        assertTrue(UnifiedBlockRule.isShortformCapable("com.ss.android.ugc.trill"))

        assertEquals("IG", UnifiedBlockRule.shortformBadgePrefixForPackage("com.instagram.android"))
        assertEquals("YT", UnifiedBlockRule.shortformBadgePrefixForPackage("com.google.android.youtube"))
        assertEquals("TT", UnifiedBlockRule.shortformBadgePrefixForPackage("com.zhiliaoapp.musically"))

        assertFalse(UnifiedBlockRule.isShortformCapable("com.snapchat.android"))
        assertFalse(UnifiedBlockRule.isShortformCapable("com.reddit.frontpage"))
        assertFalse(UnifiedBlockRule.isShortformCapable("com.android.chrome"))
    }

    @Test
    fun browserUrlBarIds_coverSamsungInternetAndModernBrowsers() {
        val samsungIds = WebsiteBlockerDetector.BROWSER_URL_BAR_IDS["com.sec.android.app.sbrowser"].orEmpty()
        assertTrue(samsungIds.contains("location_bar_edit_text"))
        assertTrue(samsungIds.contains("url_bar"))

        assertTrue(KeywordBlocker.URL_BAR_ID_LIST.containsKey("com.sec.android.app.sbrowser"))
        assertTrue(KeywordBlocker.URL_BAR_ID_LIST.containsKey("com.microsoft.emmx"))

        assertTrue(ReelBlocker.TIKTOK_PACKAGE_NAMES.contains("com.zhiliao.musically.go"))
        assertTrue(ReelBlocker.isBrowserPackage("com.sec.android.app.sbrowser"))
        assertTrue(ReelBlocker.isBrowserPackage("com.google.android.apps.chrome"))
    }
}
