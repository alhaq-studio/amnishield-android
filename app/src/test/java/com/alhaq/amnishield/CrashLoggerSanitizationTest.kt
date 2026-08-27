package com.alhaq.amnishield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * Unit tests verifying PII and credential sanitization regex rules.
 */
class CrashLoggerSanitizationTest {

    private val passwordPattern = Pattern.compile("(?i)(password|pin|pass|secret|token|license_key|auth_token|salt|jwt)[\"':\\s=]+([^\\s,;}\\]\"]+)")
    private val emailPattern = Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
    private val bearerTokenPattern = Pattern.compile("(?i)(bearer\\s+|token[=:]|authorization:\\s*)([a-zA-Z0-9_\\-\\.]{15,})")
    private val jwtPattern = Pattern.compile("eyJ[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]{10,}\\.[a-zA-Z0-9_\\-]{10,}")
    private val supabaseKeyPattern = Pattern.compile("sbp_[a-zA-Z0-9]+")

    private fun sanitize(input: String): String {
        var sanitized = input
        sanitized = passwordPattern.matcher(sanitized).replaceAll("$1=[REDACTED]")
        sanitized = bearerTokenPattern.matcher(sanitized).replaceAll("$1[REDACTED_TOKEN]")
        sanitized = jwtPattern.matcher(sanitized).replaceAll("[REDACTED_JWT]")
        sanitized = supabaseKeyPattern.matcher(sanitized).replaceAll("[REDACTED_KEY]")
        sanitized = emailPattern.matcher(sanitized).replaceAll("[REDACTED_EMAIL]")
        return sanitized
    }

    @Test
    fun testPasswordAndPinSanitization() {
        val rawLog = "User failed to enter password=mySecretPassword123 with pin: 4891"
        val sanitized = sanitize(rawLog)

        assertFalse(sanitized.contains("mySecretPassword123"))
        assertFalse(sanitized.contains("4891"))
        assertTrue(sanitized.contains("password=[REDACTED]"))
        assertTrue(sanitized.contains("pin=[REDACTED]"))
    }

    @Test
    fun testEmailSanitization() {
        val rawLog = "Sync error occurred for user john.doe+support@example.com during request"
        val sanitized = sanitize(rawLog)

        assertFalse(sanitized.contains("john.doe+support@example.com"))
        assertTrue(sanitized.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun testJwtAndBearerTokenSanitization() {
        val rawLog = "Auth failed: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.doNotLeakThisSignature"
        val sanitized = sanitize(rawLog)

        assertFalse(sanitized.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(sanitized.contains("doNotLeakThisSignature"))
        assertTrue(sanitized.contains("[REDACTED_TOKEN]") || sanitized.contains("[REDACTED_JWT]"))
    }

    @Test
    fun testSupabaseKeySanitization() {
        val rawLog = "Failed to connect using apiKey: sbp_9481948194819481948194819481"
        val sanitized = sanitize(rawLog)

        assertFalse(sanitized.contains("sbp_9481948194819481948194819481"))
        assertTrue(sanitized.contains("[REDACTED_KEY]"))
    }
}
