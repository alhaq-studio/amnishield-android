package com.alhaq.amnishield

import com.alhaq.amnishield.utils.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class PasswordHasherPBKDF2Test {

    @Test
    fun testPBKDF2HashGenerationAndVerification() {
        val pin = "123456"
        val hash = PasswordHasher.hash(pin)

        assertTrue("Hash should use v3 PBKDF2 format", hash.startsWith("v3$10000$"))
        assertTrue("Password verification should succeed with correct PIN", PasswordHasher.verify(pin, hash))
        assertFalse("Password verification should fail with wrong PIN", PasswordHasher.verify("654321", hash))
        assertFalse("Password verification should fail with empty PIN", PasswordHasher.verify("", hash))
    }

    @Test
    fun testHashWithSaltAndVerifyWithSalt() {
        val pin = "9876"
        val (hash, salt) = PasswordHasher.hashWithSalt(pin)

        assertNotNull("Generated hash should not be null", hash)
        assertNotNull("Generated salt should not be null", salt)
        assertTrue("Hash should not be blank", hash.isNotBlank())
        assertTrue("Salt should not be blank", salt.isNotBlank())

        assertTrue(
            "verifyWithSalt should succeed for matching PIN, hash, and salt",
            PasswordHasher.verifyWithSalt(pin, hash, salt)
        )
        assertFalse(
            "verifyWithSalt should fail for wrong PIN",
            PasswordHasher.verifyWithSalt("0000", hash, salt)
        )
        assertFalse(
            "verifyWithSalt should fail for wrong salt",
            PasswordHasher.verifyWithSalt(pin, hash, "d3Jvbmc=")
        )
    }

    @Test
    fun testLegacyV2SHA256FallbackVerification() {
        val pin = "4321"
        val saltBytes = "randomsalt123456".toByteArray(Charsets.UTF_8)
        val saltB64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes)
        val sha256Bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(saltBytes + pin.toByteArray(Charsets.UTF_8))
        val sha256B64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sha256Bytes)
        val legacyHash = "v2$$saltB64$$sha256B64"

        assertTrue("Legacy v2 SHA-256 hash should verify correctly", PasswordHasher.verify(pin, legacyHash))
        assertFalse("Legacy v2 SHA-256 hash should fail for incorrect PIN", PasswordHasher.verify("9999", legacyHash))
    }

    @Test
    fun testLegacyV1PlaintextFallbackVerification() {
        val pin = "5555"
        assertTrue("Legacy v1 plaintext hash should verify correctly", PasswordHasher.verify(pin, pin))
        assertFalse("Legacy v1 plaintext hash should fail for incorrect PIN", PasswordHasher.verify("1234", pin))
    }

    @Test
    fun testUniqueSaltsForIdenticalPins() {
        val pin = "7777"
        val hash1 = PasswordHasher.hash(pin)
        val hash2 = PasswordHasher.hash(pin)

        assertNotEquals("Hashing same PIN twice must produce different salts and hashes", hash1, hash2)
        assertTrue(PasswordHasher.verify(pin, hash1))
        assertTrue(PasswordHasher.verify(pin, hash2))
    }
}
