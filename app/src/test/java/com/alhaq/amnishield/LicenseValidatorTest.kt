package com.alhaq.amnishield

import com.alhaq.amnishield.premium.LicensePayload
import com.alhaq.amnishield.premium.LicenseValidator
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class LicenseValidatorTest {

    private val privateKeyBase64 = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCADPX9tuEQ27m1lup+nj/tar6XV7sqbp3IHCWE/7Yh1qQ=="

    @Test
    fun testLicenseVerificationSuccess() {
        val email = "valid-user@alhaq.org"
        val expires = System.currentTimeMillis() + 1000 * 60 * 60 // 1 hour in future
        val type = "lifetime"
        val payload = LicensePayload(email, type, expires, version = 1)

        val licenseString = generateLicenseString(payload)
        val verifiedPayload = LicenseValidator.verifyLicense(licenseString)

        assertNotNull(verifiedPayload)
        assertEquals(email, verifiedPayload?.email)
        assertEquals(type, verifiedPayload?.type)
        assertEquals(expires, verifiedPayload?.expires)
        assertEquals(1, verifiedPayload?.version)
    }

    @Test
    fun testLicenseVerificationExpired() {
        val email = "expired-user@alhaq.org"
        val expires = System.currentTimeMillis() - 1000 * 60 * 60 // 1 hour in past
        val type = "yearly"
        val payload = LicensePayload(email, type, expires, version = 1)

        val licenseString = generateLicenseString(payload)
        val verifiedPayload = LicenseValidator.verifyLicense(licenseString)

        assertNull("Expired licenses must return null", verifiedPayload)
    }

    @Test
    fun testLicenseVerificationInvalidSignature() {
        val email = "hacker@evil.com"
        val expires = System.currentTimeMillis() + 1000 * 60 * 60
        val type = "lifetime"
        val payload = LicensePayload(email, type, expires, version = 1)

        val licenseString = generateLicenseString(payload)
        
        // Corrupt the signature part (after the dot)
        val parts = licenseString.split(".")
        val corruptedLicense = parts[0] + "." + parts[1].reversed()

        val verifiedPayload = LicenseValidator.verifyLicense(corruptedLicense)
        assertNull("Corrupted signatures must fail verification", verifiedPayload)
    }

    @Test
    fun testLicenseVerificationInvalidPayload() {
        // Tamper with the payload data
        val payloadJson = """{"email":"user@alhaq.org","type":"lifetime","expires":2815412196091,"version":1}"""
        val payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))

        // Create a signature for this payload
        val sig = Signature.getInstance("SHA256withECDSA")
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(keySpec)
        
        sig.initSign(privateKey)
        sig.update(Base64.getDecoder().decode(payloadBase64))
        val signatureBase64 = Base64.getEncoder().encodeToString(sig.sign())

        val licenseString = "$payloadBase64.$signatureBase64"

        // Verification of untampered license
        assertNotNull(LicenseValidator.verifyLicense(licenseString))

        // Tamper with payload text
        val tamperedPayloadJson = """{"email":"hacker@alhaq.org","type":"lifetime","expires":2815412196091,"version":1}"""
        val tamperedPayloadBase64 = Base64.getEncoder().encodeToString(tamperedPayloadJson.toByteArray(Charsets.UTF_8))
        val tamperedLicenseString = "$tamperedPayloadBase64.$signatureBase64"

        assertNull("Tampered payloads must fail signature verification", LicenseValidator.verifyLicense(tamperedLicenseString))
    }

    @Test
    fun testLicenseVerificationInvalidVersion() {
        val email = "user@alhaq.org"
        val expires = System.currentTimeMillis() + 1000 * 60 * 60
        val type = "lifetime"
        // Version 2 is NOT present in the keyring yet!
        val payload = LicensePayload(email, type, expires, version = 2)

        val licenseString = generateLicenseString(payload)
        val verifiedPayload = LicenseValidator.verifyLicense(licenseString)

        assertNull("Payload with unsupported version must return null", verifiedPayload)
    }

    @Test
    fun testLicenseVerificationRejectsBypassKey() {
        val userKey = "eyJlbWFpbCI6ImhhYmlibXVraGxpczIwMDZAZ21haWwuY29tIiwidXNlcl9pZCI6IjU3Y2E1MTg4LTM0ZjYtNDc5Zi05MTZlLWEzNDRmOGMxYmU5OCIsInR5cGUiOiJwcmVtaXVtIiwiZXhwaXJlcyI6NDkzOTY3Nzg1MTQ4OCwidmVyc2lvbiI6MX0=.ECDSA_SIGNED_PRO_KEY"
        val payload = LicenseValidator.verifyLicense(userKey)
        assertNull("Bypass string .ECDSA_SIGNED_PRO_KEY must be rejected unconditionally", payload)
    }

    @Test
    fun testLicenseVerificationUserKey() {
        val userPayload = LicensePayload(
            email = "habibmukhlis2006@gmail.com",
            user_id = "57ca5188-34f6-479f-916e-a344f8c1be98",
            type = "premium",
            expires = 4939677851488L,
            version = 1
        )
        val validUserKey = generateLicenseString(userPayload)
        val payload = LicenseValidator.verifyLicense(validUserKey)

        assertNotNull(payload)
        assertEquals("habibmukhlis2006@gmail.com", payload?.email)
        assertEquals("57ca5188-34f6-479f-916e-a344f8c1be98", payload?.user_id)
        assertEquals("premium", payload?.type)
        assertEquals(4939677851488L, payload?.expires)
        assertEquals(1, payload?.version)
    }

    @Test
    fun testLicenseVerificationWithWhitespaceAndQuotes() {
        val userPayload = LicensePayload(
            email = "habibmukhlis2006@gmail.com",
            user_id = "57ca5188-34f6-479f-916e-a344f8c1be98",
            type = "premium",
            expires = 4939677851488L,
            version = 1
        )
        val validUserKey = generateLicenseString(userPayload)
        val rawKeyWithWhitespace = "  \"$validUserKey\"\n "
        val payload = LicenseValidator.verifyLicense(rawKeyWithWhitespace)

        assertNotNull(payload)
        assertEquals("habibmukhlis2006@gmail.com", payload?.email)
    }

    @Test
    fun testCloudGeneratedLicense() {
        val cloudKey = "eyJlbWFpbCI6InRlc3RAZXhhbXBsZS5jb20iLCJ0eXBlIjoibGlmZXRpbWUiLCJleHBpcmVzIjo0OTQwODU4ODQ4MTg4LCJ2ZXJzaW9uIjoxfQ==.TeTV1qsULJtzgqzzO8t7lMKhByJfngXfHVAHgTGwiEjaAoWuIZkk9CdyRANiw/fO3q/I1gnGB+Vnd57RmSZ6pQ=="
        val payload = LicenseValidator.verifyLicense(cloudKey)
        assertNotNull("Cloud generated license should verify!", payload)
    }

    private fun generateLicenseString(payload: LicensePayload): String {
        val gson = Gson()
        val payloadJson = gson.toJson(payload)
        val payloadBase64 = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))

        val sig = Signature.getInstance("SHA256withECDSA")
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyBase64)
        val keySpec = PKCS8EncodedKeySpec(privateKeyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(keySpec)

        sig.initSign(privateKey)
        sig.update(Base64.getDecoder().decode(payloadBase64))
        val signatureBytes = sig.sign()
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)

        return "$payloadBase64.$signatureBase64"
    }
}
