package com.alhaq.amnshield.premium

import com.google.gson.Gson
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object LicenseValidator {
    // NIST P-256 (secp256r1) Public Key keyring in X.509 format (Base64)
    private val KEYRING = mapOf(
        1 to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE7EFR1qxpfZTMeR52M1+04+tPb6ItmVmhPbRCIJYje3jtglTdBbcct+/xvc1D1NZtXuvSb4Egtdqm/EJ6H67fEA=="
    )

    fun verifyLicense(licenseString: String): LicensePayload? {
        try {
            val cleanString = licenseString.trim().trim('"', '\'').replace("\r", "").replace("\n", "")
            val parts = cleanString.split(".")
            if (parts.size != 2) return null

            val payloadBase64 = parts[0]
            val signatureBase64 = parts[1]

            val payloadBytes = decodeBase64Safe(payloadBase64)
            val payloadJson = String(payloadBytes, Charsets.UTF_8)

            // Parse payload
            val gson = Gson()
            val payload = gson.fromJson(payloadJson, LicensePayload::class.java) ?: return null

            // Check expiration
            if (payload.expires < System.currentTimeMillis()) {
                return null
            }

            // Retrieve the public key corresponding to the payload schema version
            val publicKeyBase64 = KEYRING[payload.version] ?: return null
            val publicKeyBytes = decodeBase64Safe(publicKeyBase64)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(keySpec)

            val rawSigBytes = decodeBase64Safe(signatureBase64)
            val derSigBytes = if (rawSigBytes.size == 64) p1363ToDer(rawSigBytes) else rawSigBytes

            // Verify against decoded JSON payload bytes
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(payloadBytes)

            if (sig.verify(derSigBytes)) {
                return payload
            }

            // Fallback: Verify against raw payloadBase64 ASCII string bytes
            val sigFallback = Signature.getInstance("SHA256withECDSA")
            sigFallback.initVerify(publicKey)
            sigFallback.update(payloadBase64.toByteArray(Charsets.UTF_8))

            if (sigFallback.verify(derSigBytes)) {
                return payload
            }
        } catch (_: Exception) {
            // Invalid key or signature failure
        }
        return null
    }

    /**
     * Decodes standard Base64, URL-safe Base64 (- and _), with automatic padding.
     */
    private fun decodeBase64Safe(str: String): ByteArray {
        val sanitized = str.trim()
            .replace('-', '+')
            .replace('_', '/')
        val remainder = sanitized.length % 4
        val padded = if (remainder > 0) {
            sanitized + "=".repeat(4 - remainder)
        } else {
            sanitized
        }
        return Base64.getDecoder().decode(padded)
    }

    /**
     * Converts IEEE P1363 64-byte raw signature (r[32] || s[32]) from WebCrypto/Deno
     * into ASN.1 DER format expected by Java's SHA256withECDSA Signature verifier.
     */
    private fun p1363ToDer(p1363: ByteArray): ByteArray {
        if (p1363.size != 64) return p1363

        val r = BigInteger(1, p1363.copyOfRange(0, 32)).toByteArray()
        val s = BigInteger(1, p1363.copyOfRange(32, 64)).toByteArray()

        val totalLen = 2 + r.size + 2 + s.size
        val der = ByteArray(2 + totalLen)
        der[0] = 0x30.toByte() // ASN.1 SEQUENCE
        der[1] = totalLen.toByte()

        var offset = 2
        der[offset++] = 0x02.toByte() // ASN.1 INTEGER
        der[offset++] = r.size.toByte()
        System.arraycopy(r, 0, der, offset, r.size)
        offset += r.size

        der[offset++] = 0x02.toByte() // ASN.1 INTEGER
        der[offset++] = s.size.toByte()
        System.arraycopy(s, 0, der, offset, s.size)

        return der
    }

    // Return the default public key for debug and backward compatibility
    val debugPublicKey: String
        get() = KEYRING[1] ?: ""
}

