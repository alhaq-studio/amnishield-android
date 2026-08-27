package com.alhaq.amnishield

import com.alhaq.amnishield.data.sync.SupabaseRest
import com.alhaq.amnishield.premium.LicensePayload
import com.alhaq.amnishield.premium.LicenseValidator
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Comprehensive Unit Tests for the In-App Passwordless 6-Digit OTP Sign-in & Activation Flow.
 */
class OtpSignInAndActivationTest {

    private val privateKeyBase64 = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCADPX9tuEQ27m1lup+nj/tar6XV7sqbp3IHCWE/7Yh1qQ=="

    private fun generateValidEcdsaLicense(email: String, type: String = "lifetime", expires: Long = System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365): String {
        val payload = LicensePayload(email, type, expires, version = 1)
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
    fun testUserProfileParsingWithEcdsaLicense() {
        val email = "supporter@alhaq.org"
        val validLicense = generateValidEcdsaLicense(email, type = "lifetime")

        val profileJson = """
            [
              {
                "id": "usr-12345-abcde",
                "email": "$email",
                "is_premium": true,
                "role": "user",
                "license_key": "$validLicense"
              }
            ]
        """.trimIndent()

        val gson = Gson()
        val arr = gson.fromJson(profileJson, com.google.gson.JsonArray::class.java)
        assertEquals(1, arr.size())

        val o = arr[0].asJsonObject
        val profile = SupabaseRest.UserProfile(
            id = o.get("id").asString,
            email = o.get("email")?.takeIf { !it.isJsonNull }?.asString,
            isPremium = o.get("is_premium")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            role = o.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "user",
            licenseKey = o.get("license_key")?.takeIf { !it.isJsonNull }?.asString
        )

        assertEquals("usr-12345-abcde", profile.id)
        assertEquals(email, profile.email)
        assertTrue(profile.isPremium)
        assertEquals("user", profile.role)
        assertEquals(validLicense, profile.licenseKey)

        // Verify that the extracted license key validates successfully via offline ECDSA validator
        val validatedPayload = LicenseValidator.verifyLicense(profile.licenseKey!!)
        assertNotNull("Offline ECDSA validator must accept genuine signed license from profile", validatedPayload)
        assertEquals(email, validatedPayload?.email)
        assertEquals("lifetime", validatedPayload?.type)
    }

    @Test
    fun testOtpRequestBodyStructure() {
        val email = "Buyer@Example.COM "
        val cleanedEmail = email.trim().lowercase()

        val otpBody = JsonObject().apply {
            addProperty("email", cleanedEmail)
            addProperty("create_user", true)
        }

        assertEquals("buyer@example.com", otpBody.get("email").asString)
        assertTrue(otpBody.get("create_user").asBoolean)

        val verifyBody6 = JsonObject().apply {
            addProperty("email", cleanedEmail)
            addProperty("token", "123456")
            addProperty("type", "email")
        }

        assertEquals("buyer@example.com", verifyBody6.get("email").asString)
        assertEquals("123456", verifyBody6.get("token").asString)
        assertEquals("email", verifyBody6.get("type").asString)

        val verifyBody8 = JsonObject().apply {
            addProperty("email", cleanedEmail)
            addProperty("token", "48139226")
            addProperty("type", "email")
        }

        assertEquals("buyer@example.com", verifyBody8.get("email").asString)
        assertEquals("48139226", verifyBody8.get("token").asString)
        assertEquals("email", verifyBody8.get("type").asString)
    }

    @Test
    fun testAutomatedActivationRejectsTamperedOrForgedLicense() {
        val attackerEmail = "attacker@evil.com"
        // Try bypass signature string that was previously tested
        val fakePayload = LicensePayload(attackerEmail, "lifetime", System.currentTimeMillis() + 1000000L, version = 1)
        val fakeBase64 = Base64.getEncoder().encodeToString(Gson().toJson(fakePayload).toByteArray(Charsets.UTF_8))
        val forgedLicense = "$fakeBase64.ECDSA_SIGNED_PRO_KEY"

        val verified = LicenseValidator.verifyLicense(forgedLicense)
        assertNull("Forged license must be rejected unconditionally", verified)
    }

    @Test
    fun testSessionParsingFromSupabaseAuth() {
        val sampleResponse = """
            {
              "access_token": "mock-access-token-123",
              "token_type": "bearer",
              "expires_in": 3600,
              "refresh_token": "mock-refresh-token-456",
              "user": {
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "email": "subscriber@alhaq.uk"
              }
            }
        """.trimIndent()

        val gson = Gson()
        val body = gson.fromJson(sampleResponse, JsonObject::class.java)

        val token = body.get("access_token")?.takeIf { !it.isJsonNull }?.asString
        val user = body.getAsJsonObject("user")
        val expiresIn = body.get("expires_in")?.asLong ?: 3600
        val session = SupabaseRest.Session(
            accessToken = token!!,
            refreshToken = body.get("refresh_token").asString,
            userId = user.get("id").asString,
            email = user.get("email")?.takeIf { !it.isJsonNull }?.asString,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000
        )

        assertEquals("mock-access-token-123", session.accessToken)
        assertEquals("mock-refresh-token-456", session.refreshToken)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", session.userId)
        assertEquals("subscriber@alhaq.uk", session.email)
        assertTrue(session.expiresAt > System.currentTimeMillis())
    }

    @Test
    fun testSendEmailOtpRequestBodyWithOptionsRedirect() {
        val email = "User@Domain.com "
        val cleanedEmail = email.trim().lowercase()
        val redirectUrl = "https://app.amnishield.com/activate"

        val body = JsonObject().apply {
            addProperty("email", cleanedEmail)
            addProperty("create_user", true)
            val options = JsonObject().apply {
                addProperty("email_redirect_to", redirectUrl)
            }
            add("options", options)
            addProperty("email_redirect_to", redirectUrl)
        }

        assertEquals("user@domain.com", body.get("email").asString)
        assertTrue(body.get("create_user").asBoolean)
        assertEquals(redirectUrl, body.get("email_redirect_to").asString)
        assertEquals(redirectUrl, body.getAsJsonObject("options").get("email_redirect_to").asString)
    }

    @Test
    fun testDeepLinkUriSchemeAndParameterResolution() {
        // 1. App Scheme: amnishield://activate?token=847291&email=parent@shield.org
        val uri1 = java.net.URI("amnishield://activate?token=847291&email=parent@shield.org")
        assertEquals("amnishield", uri1.scheme?.lowercase())
        assertEquals("activate", uri1.host?.lowercase())
        val params1 = uri1.query.split("&").associate {
            val parts = it.split("=")
            parts[0] to parts[1]
        }
        assertEquals("847291", params1["token"])
        assertEquals("parent@shield.org", params1["email"])

        // 2. Web App Link: https://app.amnishield.com/activate?key=VALID_KEY_XYZ
        val uri2 = java.net.URI("https://app.amnishield.com/activate?key=VALID_KEY_XYZ")
        assertEquals("https", uri2.scheme?.lowercase())
        assertEquals("app.amnishield.com", uri2.host?.lowercase())
        assertEquals("/activate", uri2.path?.lowercase())
        val params2 = uri2.query.split("&").associate {
            val parts = it.split("=")
            parts[0] to parts[1]
        }
        assertEquals("VALID_KEY_XYZ", params2["key"])

        // 3. Fallback Scheme: amnishield://license?key=SAMPLE_KEY
        val uri3 = java.net.URI("amnishield://license?key=SAMPLE_KEY")
        assertEquals("amnishield", uri3.scheme?.lowercase())
        val params3 = uri3.query.split("&").associate {
            val parts = it.split("=")
            parts[0] to parts[1]
        }
        assertEquals("SAMPLE_KEY", params3["key"])
    }

    @Test
    fun testOtpCodeRegexValidation() {
        val otpPattern = "^[0-9]{6}$".toRegex()
        assertTrue("123456 must match valid 6-digit OTP", otpPattern.matches("123456"))
        assertTrue("000000 must match valid 6-digit OTP", otpPattern.matches("000000"))
        assertTrue("987654 must match valid 6-digit OTP", otpPattern.matches("987654"))

        assertFalse("5 digits must fail", otpPattern.matches("12345"))
        assertFalse("7 digits must fail", otpPattern.matches("1234567"))
        assertFalse("Alphanumeric must fail", otpPattern.matches("12345A"))
        assertFalse("Spaces must fail", otpPattern.matches(" 12345"))
    }
}
