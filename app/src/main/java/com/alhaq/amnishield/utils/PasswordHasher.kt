package com.alhaq.amnishield.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Enterprise-grade cryptographic PIN and password hasher for AmniShield.
 * Uses PBKDF2 with HMAC-SHA256, 10,000 iterations, and a 16-byte secure random salt.
 *
 * Supported formats:
 * - v3: "v3$<iterations>$<base64-salt>$<base64-hash>" (Default & Recommended)
 * - v2: "v2$<base64-salt>$<base64-sha256>" (Legacy fallback)
 * - v1: Plaintext (Legacy fallback, upgraded automatically)
 */
object PasswordHasher {

    private const val PREFIX_V3 = "v3$"
    private const val PREFIX_V2 = "v2$"
    private const val SALT_BYTES = 16
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private fun encodeB64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeB64(str: String): ByteArray {
        return try {
            Base64.getUrlDecoder().decode(str)
        } catch (e: Exception) {
            Base64.getDecoder().decode(str)
        }
    }

    /**
     * Hashes a PIN or password string into a self-contained v3 PBKDF2 string.
     */
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = encodeB64(salt)
        val hashB64 = encodeB64(hash)
        return "$PREFIX_V3$ITERATIONS\$$saltB64\$$hashB64"
    }

    /**
     * Generates a separate (hash, salt) pair using PBKDF2 for per-rule schema storage.
     */
    fun hashWithSalt(password: String): Pair<String, String> {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val saltB64 = encodeB64(salt)
        val hashB64 = encodeB64(hash)
        return Pair(hashB64, saltB64)
    }

    /**
     * Verifies a raw PIN against a stored separate (hash, salt) pair using PBKDF2.
     */
    fun verifyWithSalt(input: String, storedHash: String?, storedSalt: String?): Boolean {
        if (storedHash.isNullOrEmpty() || storedSalt.isNullOrEmpty()) return false
        val salt = runCatching { decodeB64(storedSalt) }.getOrNull() ?: return false
        val expectedHash = runCatching { decodeB64(storedHash) }.getOrNull() ?: return false

        val actualHash = pbkdf2(input.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    /**
     * Verifies an input PIN/password against a stored string representation (v3, v2, or legacy plaintext).
     */
    fun verify(input: String, stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false

        return when {
            stored.startsWith(PREFIX_V3) -> {
                val parts = stored.removePrefix(PREFIX_V3).split('$')
                if (parts.size != 3) return false
                val iterations = parts[0].toIntOrNull() ?: ITERATIONS
                val salt = runCatching { decodeB64(parts[1]) }.getOrNull() ?: return false
                val expected = runCatching { decodeB64(parts[2]) }.getOrNull() ?: return false

                val actual = pbkdf2(input.toCharArray(), salt, iterations, expected.size * 8)
                MessageDigest.isEqual(expected, actual)
            }
            stored.startsWith(PREFIX_V2) -> {
                val parts = stored.removePrefix(PREFIX_V2).split('$')
                if (parts.size != 2) return false
                val salt = runCatching { decodeB64(parts[0]) }.getOrNull() ?: return false
                val expected = runCatching { decodeB64(parts[1]) }.getOrNull() ?: return false

                val actual = sha256(salt + input.toByteArray(Charsets.UTF_8))
                MessageDigest.isEqual(expected, actual)
            }
            else -> {
                // Legacy plaintext path
                MessageDigest.isEqual(
                    stored.toByteArray(Charsets.UTF_8),
                    input.toByteArray(Charsets.UTF_8)
                )
            }
        }
    }

    fun isPlainText(stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        return !stored.startsWith(PREFIX_V3) && !stored.startsWith(PREFIX_V2)
    }

    fun needsRehash(stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        return !stored.startsWith(PREFIX_V3)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val skf = SecretKeyFactory.getInstance(ALGORITHM)
        return skf.generateSecret(spec).encoded
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}
