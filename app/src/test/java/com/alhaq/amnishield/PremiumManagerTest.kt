package com.alhaq.amnishield

import android.content.Context
import com.alhaq.amnishield.premium.LicensePayload
import com.alhaq.amnishield.premium.LicenseValidator
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Comprehensive unit test suite for PremiumManager and the Al-Haq Community Access Program.
 * Covers:
 * - Anti-clock manipulation guard (drift, rollback detection, monotonic elapsed hardware uptime).
 * - Monotonic hardware uptime accumulation across simulated device reboots.
 * - 7-day review grace window and automated graduation to 1-year verified pass.
 * - Voluntary cancellation ("Amanah" return) and status resets.
 * - Flagged disposable email revocation.
 * - Offline NIST P-256 ECDSA license validation integration.
 */
class PremiumManagerTest {

    private lateinit var fakeCompassionatePrefs: InMemorySharedPreferences
    private lateinit var fakePremiumPrefs: InMemorySharedPreferences
    private lateinit var loader: SavedPreferencesLoader
    private lateinit var premiumManager: PremiumManager

    private var simulatedWallClock = 1_700_000_000_000L
    private var simulatedElapsedRealtime = 100_000L

    private val privateKeyBase64 =
        "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCADPX9tuEQ27m1lup+nj/tar6XV7sqbp3IHCWE/7Yh1qQ=="

    @Before
    fun setup() {
        fakeCompassionatePrefs = InMemorySharedPreferences()
        fakePremiumPrefs = InMemorySharedPreferences()
        simulatedWallClock = 1_700_000_000_000L
        simulatedElapsedRealtime = 100_000L

        val dummyContext = FakeTestContext(fakeCompassionatePrefs)
        loader = SavedPreferencesLoader(
            context = dummyContext,
            injectedCompassionatePrefs = fakeCompassionatePrefs,
            injectedPremiumPrefs = fakePremiumPrefs,
            elapsedRealtimeProvider = { simulatedElapsedRealtime },
            wallClockProvider = { simulatedWallClock }
        )

        premiumManager = PremiumManager(loader)
    }

    private class FakeTestContext(
        private val prefs: android.content.SharedPreferences
    ) : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
    }

    private fun generateValidEcdsaCommunityLicense(
        email: String,
        appId: String,
        expires: Long
    ): String {
        val payload = LicensePayload(
            email = email,
            type = "community_pass",
            expires = expires,
            app_id = appId,
            version = 1
        )
        val payloadJson = Gson().toJson(payload)
        val payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))

        val sig = Signature.getInstance("SHA256withECDSA")
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(keySpec)

        sig.initSign(privateKey)
        sig.update(Base64.getDecoder().decode(payloadBase64))
        val signatureBase64 = Base64.getEncoder().encodeToString(sig.sign())

        return "$payloadBase64.$signatureBase64"
    }

    @Test
    fun testDefaultStateIsFreeUser() {
        assertEquals(PremiumManager.UserType.FREE, premiumManager.getUserType())
        assertEquals("Free", premiumManager.getUserTypeLabel())
        assertFalse(premiumManager.isPremium())
        assertFalse(premiumManager.isCompassionateAccessActive())
    }

    @Test
    fun testImmediateSevenDayGraceAccessGranted() {
        val grantedAt = simulatedWallClock
        val gracePeriodMs = 7L * 24 * 60 * 60 * 1000L
        val tempExpiry = grantedAt + gracePeriodMs

        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-001",
            userName = "Test User",
            email = "user@example.com",
            grantedAt = grantedAt,
            expiresAt = tempExpiry,
            status = "pending_review"
        )

        assertTrue("Immediate 7-day grace access must be active", premiumManager.isCompassionateAccessActive())
        assertTrue("User must have premium privileges", premiumManager.isPremium())
        assertEquals(PremiumManager.UserType.COMPASSIONATE, premiumManager.getUserType())
        assertEquals("Community Access (Review)", premiumManager.getUserTypeLabel())
        assertEquals("pending_review", loader.getCompassionateAccessStatus())
    }

    @Test
    fun testMonotonicUptimeAccumulationAcrossReboots() {
        val grantedAt = simulatedWallClock
        val gracePeriodMs = 7L * 24 * 60 * 60 * 1000L
        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-002",
            userName = "Reboot User",
            email = "reboot@example.com",
            grantedAt = grantedAt,
            expiresAt = grantedAt + gracePeriodMs,
            status = "pending_review"
        )

        // Advance hardware uptime by 2 hours
        simulatedElapsedRealtime += 2 * 60 * 60 * 1000L
        simulatedWallClock += 2 * 60 * 60 * 1000L
        assertTrue(loader.updateCompassionateUptimeAndVerify())

        // Simulate device reboot: elapsedRealtime drops from ~7,300,000ms down to 5,000ms
        simulatedElapsedRealtime = 5_000L
        simulatedWallClock += 60_000L // 1 minute passed during reboot
        assertTrue(loader.updateCompassionateUptimeAndVerify())

        // Monotonic accumulated uptime must not have lost the previous 2 hours
        val accumulated = fakeCompassionatePrefs.getLong("accumulated_uptime_ms", 0L)
        assertTrue("Accumulated uptime must retain pre-reboot duration", accumulated >= 2 * 60 * 60 * 1000L)
    }

    @Test
    fun testClockRollbackDetectionPreventsGraceWindowTampering() {
        val grantedAt = simulatedWallClock
        val gracePeriodMs = 7L * 24 * 60 * 60 * 1000L
        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-003",
            userName = "Clock Hacker",
            email = "hacker@example.com",
            grantedAt = grantedAt,
            expiresAt = grantedAt + gracePeriodMs,
            status = "pending_review"
        )

        // Advance 3 days normally
        simulatedWallClock += 3L * 24 * 60 * 60 * 1000L
        simulatedElapsedRealtime += 3L * 24 * 60 * 60 * 1000L
        assertTrue(loader.updateCompassionateUptimeAndVerify())

        // User manually winds back the system wall clock by 5 days (attempting to freeze in past)
        simulatedWallClock -= 5L * 24 * 60 * 60 * 1000L

        // Verification must detect the clock rollback (> 5 min backwards drift)
        assertFalse(
            "Clock rollback must be flagged and rejected by anti-clock manipulation guard",
            loader.updateCompassionateUptimeAndVerify()
        )
    }

    @Test
    fun testAutomatedGraduationToOneYearPassAfterSevenDays() {
        val grantedAt = simulatedWallClock
        val gracePeriodMs = 7L * 24 * 60 * 60 * 1000L
        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-004",
            userName = "Faithful User",
            email = "faithful@example.com",
            grantedAt = grantedAt,
            expiresAt = grantedAt + gracePeriodMs,
            status = "pending_review"
        )

        // Simulate 7 full days passing with periodic device usage
        simulatedWallClock += 7L * 24 * 60 * 60 * 1000L + 1000L
        simulatedElapsedRealtime += 7L * 24 * 60 * 60 * 1000L + 1000L

        // Trigger verification (called automatically on app launch and screen view)
        val isActive = premiumManager.isCompassionateAccessActive()

        assertTrue("Pass must graduate and remain active", isActive)
        assertEquals("verified_active", loader.getCompassionateAccessStatus())
        assertEquals("Al-Haq Community Pass", premiumManager.getUserTypeLabel())

        val fullExpectedExpiry = grantedAt + (372L * 24 * 60 * 60 * 1000L)
        assertEquals(fullExpectedExpiry, loader.getCompassionateAccessExpiry())
    }

    @Test
    fun testVoluntaryCancellationResetsAccountToFreeTier() {
        val grantedAt = simulatedWallClock
        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-005",
            userName = "Honest Member",
            email = "honest@example.com",
            grantedAt = grantedAt,
            expiresAt = grantedAt + (7L * 24 * 60 * 60 * 1000L),
            status = "pending_review"
        )

        assertTrue(premiumManager.isCompassionateAccessActive())

        // User voluntarily cancels grant (returns to community pool)
        loader.cancelCompassionateAccess()

        assertEquals("cancelled", loader.getCompassionateAccessStatus())
        assertEquals(0L, loader.getCompassionateAccessExpiry())
        assertFalse("Cancelled grant must not be active", premiumManager.isCompassionateAccessActive())
        assertFalse("Premium status must be revoked", premiumManager.isPremium())
        assertEquals(PremiumManager.UserType.FREE, premiumManager.getUserType())
        assertEquals("Free", premiumManager.getUserTypeLabel())
    }

    @Test
    fun testFlaggedEmailImmediatelyRevokesAccess() {
        val grantedAt = simulatedWallClock
        loader.saveCompassionateAccessGrant(
            appId = "CAP-TEST-006",
            userName = "Spam User",
            email = "disposable@tempmail.com",
            grantedAt = grantedAt,
            expiresAt = grantedAt + (7L * 24 * 60 * 60 * 1000L),
            status = "pending_review"
        )

        assertTrue(premiumManager.isCompassionateAccessActive())

        // Backend flags disposable or invalid domain
        loader.setCompassionateAccessStatus("flagged_email")

        assertFalse("Flagged grant must immediately lose active status", premiumManager.isCompassionateAccessActive())
        assertFalse(premiumManager.isPremium())
    }

    @Test
    fun testCryptographicEcdsaCommunityPassVerification() {
        val email = "community.member@alhaq.org"
        val appId = "CAP-CRYPTO-999"
        val expiry = simulatedWallClock + 365L * 24 * 60 * 60 * 1000L

        val validKey = generateValidEcdsaCommunityLicense(email, appId, expiry)

        loader.saveCompassionateAccessGrant(
            appId = appId,
            userName = "Cryptographic User",
            email = email,
            grantedAt = simulatedWallClock,
            expiresAt = expiry,
            status = "verified_active",
            licenseKey = validKey
        )

        assertTrue(
            "Valid cryptographic ECDSA key must be recognized offline",
            premiumManager.isCompassionateAccessActive()
        )
        assertEquals("Al-Haq Community Pass", premiumManager.getUserTypeLabel())

        // Tamper with the cryptographic key signature
        val tamperedKey = validKey.substring(0, validKey.length - 8) + "AAAAAAAA"
        loader.setCompassionateAccessLicenseKey(tamperedKey)

        // Set expiry to past to ensure fallback to cryptographic key fails
        loader.setCompassionateAccessExpiry(simulatedWallClock - 1000L)
        assertFalse(
            "Tampered ECDSA key must fail signature verification",
            premiumManager.isCompassionateAccessActive()
        )
    }
}
