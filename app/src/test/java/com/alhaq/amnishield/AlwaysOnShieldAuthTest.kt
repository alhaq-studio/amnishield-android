package com.alhaq.amnishield

import com.alhaq.amnishield.data.blockers.ShieldLockConfig
import com.alhaq.amnishield.data.blockers.ShieldLockMode
import com.alhaq.amnishield.security.AuthResolver
import com.alhaq.amnishield.security.AuthResult
import com.alhaq.amnishield.security.ShieldAuthTarget
import com.alhaq.amnishield.utils.PasswordHasher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AlwaysOnShieldAuthTest {

    private lateinit var fakeSecurityPrefs: InMemorySharedPreferences
    private lateinit var fakeAntiUninstallPrefs: InMemorySharedPreferences
    private lateinit var authResolver: AuthResolver

    @Before
    fun setup() {
        fakeSecurityPrefs = InMemorySharedPreferences()
        fakeAntiUninstallPrefs = InMemorySharedPreferences()
        authResolver = AuthResolver(
            injectedSecurityPrefs = fakeSecurityPrefs,
            injectedAntiUninstallPrefs = fakeAntiUninstallPrefs
        )
    }

    @Test
    fun `resolveShieldChallenge handles all five lock modes accurately`() {
        // 1. NONE -> PassThrough
        val noneTarget = authResolver.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.NONE)
        )
        assertTrue(noneTarget is ShieldAuthTarget.PassThrough)

        // 2. INHERIT_GLOBAL_PIN without Global PIN set -> PassThrough
        val inheritWithoutMaster = authResolver.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN)
        )
        assertTrue(inheritWithoutMaster is ShieldAuthTarget.PassThrough)

        // 2b. INHERIT_GLOBAL_PIN with Global PIN set -> RequireGlobalPin
        val masterPin = "4321"
        fakeAntiUninstallPrefs.edit().putString("password", PasswordHasher.hash(masterPin)).apply()
        val inheritWithMaster = authResolver.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN)
        )
        assertTrue(inheritWithMaster is ShieldAuthTarget.RequireGlobalPin)
        assertTrue(authResolver.verifyShieldPin(inheritWithMaster, masterPin) is AuthResult.Success)

        // 3. PAUSE_DELAY -> RequireDelay with clamped bounds
        val delayTarget = authResolver.resolveShieldChallenge(
            ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY, delaySeconds = 45)
        )
        assertTrue(delayTarget is ShieldAuthTarget.RequireDelay)
        assertEquals(45, (delayTarget as ShieldAuthTarget.RequireDelay).delaySeconds)

        // 4. CUSTOM_PIN -> RequireCustomPin (PBKDF2)
        val (customHash, customSalt) = authResolver.createRulePin("9876")
        val customPinTarget = authResolver.resolveShieldChallenge(
            ShieldLockConfig(
                mode = ShieldLockMode.CUSTOM_PIN,
                customPinHash = customHash,
                customPinSalt = customSalt
            )
        )
        assertTrue(customPinTarget is ShieldAuthTarget.RequireCustomPin)
        assertTrue(authResolver.verifyShieldPin(customPinTarget, "9876") is AuthResult.Success)
        assertTrue(authResolver.verifyShieldPin(customPinTarget, "0000") is AuthResult.InvalidPin)

        // 5. QR_CODE -> RequireQr
        val qrTarget = authResolver.resolveShieldChallenge(
            ShieldLockConfig(
                mode = ShieldLockMode.QR_CODE,
                registeredQrValue = "AMNISHIELD-QR-SECRET"
            )
        )
        assertTrue(qrTarget is ShieldAuthTarget.RequireQr)
        assertEquals("AMNISHIELD-QR-SECRET", (qrTarget as ShieldAuthTarget.RequireQr).expectedValue)
    }

    @Test
    fun `shield custom pin verification triggers five attempt brute force lockout`() {
        val (hash, salt) = authResolver.createRulePin("2468")
        val lockConfig = ShieldLockConfig(
            mode = ShieldLockMode.CUSTOM_PIN,
            customPinHash = hash,
            customPinSalt = salt
        )
        val target = authResolver.resolveShieldChallenge(lockConfig)
        assertTrue(target is ShieldAuthTarget.RequireCustomPin)

        for (attempt in 1..4) {
            val result = authResolver.verifyShieldPin(target, "wrong_$attempt")
            assertTrue(result is AuthResult.InvalidPin)
            assertFalse((result as AuthResult.InvalidPin).isLockedOut)
            assertEquals(5 - attempt, result.attemptsRemaining)
        }

        val fifth = authResolver.verifyShieldPin(target, "wrong_5")
        assertTrue(fifth is AuthResult.InvalidPin)
        assertTrue((fifth as AuthResult.InvalidPin).isLockedOut)
        assertEquals(0, fifth.attemptsRemaining)

        val nextTarget = authResolver.resolveShieldChallenge(lockConfig)
        assertTrue("Must return LockedOut after 5 failed attempts", nextTarget is ShieldAuthTarget.LockedOut)
    }

    @Test
    fun `pro expiry safety allows unlocking custom pin and qr code without premium subscription`() {
        // AuthResolver intentionally does NOT consult PremiumManager during unlock/verification.
        // Even when Pro has expired, users can always pass their challenge to unlock or downgrade.
        val (hash, salt) = authResolver.createRulePin("7777")
        val customConfig = ShieldLockConfig(
            mode = ShieldLockMode.CUSTOM_PIN,
            customPinHash = hash,
            customPinSalt = salt
        )
        val resolvedCustom = authResolver.resolveShieldChallenge(customConfig)
        assertTrue(resolvedCustom is ShieldAuthTarget.RequireCustomPin)
        assertTrue(authResolver.verifyShieldPin(resolvedCustom, "7777") is AuthResult.Success)

        val qrConfig = ShieldLockConfig(
            mode = ShieldLockMode.QR_CODE,
            registeredQrValue = "MY-KITCHEN-QR"
        )
        val resolvedQr = authResolver.resolveShieldChallenge(qrConfig)
        assertTrue(resolvedQr is ShieldAuthTarget.RequireQr)
        assertEquals("MY-KITCHEN-QR", (resolvedQr as ShieldAuthTarget.RequireQr).expectedValue)
    }

    @Test
    fun `lock strength rank enforces asymmetric friction hierarchy`() {
        val (hash, salt) = authResolver.createRulePin("1234")
        val none = ShieldLockConfig(mode = ShieldLockMode.NONE).strengthRank(hasGlobalPin = true)
        val delay = ShieldLockConfig(mode = ShieldLockMode.PAUSE_DELAY).strengthRank(hasGlobalPin = true)
        val globalPin = ShieldLockConfig(mode = ShieldLockMode.INHERIT_GLOBAL_PIN).strengthRank(hasGlobalPin = true)
        val customPin = ShieldLockConfig(
            mode = ShieldLockMode.CUSTOM_PIN,
            customPinHash = hash,
            customPinSalt = salt
        ).strengthRank(hasGlobalPin = true)
        val qr = ShieldLockConfig(
            mode = ShieldLockMode.QR_CODE,
            registeredQrValue = "QR-1"
        ).strengthRank(hasGlobalPin = true)

        assertTrue(none < delay)
        assertTrue(delay < globalPin)
        assertTrue(globalPin < customPin)
        assertEquals(customPin, qr)
    }
}
