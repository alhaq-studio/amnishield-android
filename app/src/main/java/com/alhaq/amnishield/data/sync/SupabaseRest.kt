package com.alhaq.amnishield.data.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SupabaseRest(
    private val baseUrl: String = "https://jrgpmcomvibgklmvnxud.supabase.co",
    private val anonKey: String = ANON_KEY,
) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    /** Thrown when the auth endpoint itself rejects a request, carrying the HTTP status. */
    class AuthHttpException(val code: Int, message: String) : IOException(message)

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
        val email: String? = null,
        val expiresAt: Long = System.currentTimeMillis() + 3600_000L,
    )

    data class VaultRow(val saltB64: String, val paramsJson: String, val wrappedB64: String)

    data class SyncRow(
        val namespace: String,
        val recordKey: String,
        val deviceId: String?,
        val ciphertext: String,
        val version: Long,
        val deleted: Boolean,
        val updatedAt: String,
    )

    data class DeviceRow(
        val id: String,
        val platform: String,
        val label: String,
        val lastSeen: String?,
        val deviceName: String = label,
        val ownerId: String? = null,
        val userId: String? = null,
        val isManaged: Boolean = false,
        val isOnline: Boolean = false,
        val syncEnabled: Boolean = true,
        val lastHeartbeat: String? = lastSeen,
        val policyPayload: JsonObject? = null
    )

    private fun parseSession(body: JsonObject): Session? {
        val token = body.get("access_token")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val user = body.getAsJsonObject("user")
        val expiresIn = body.get("expires_in")?.asLong ?: 3600
        return Session(
            accessToken = token,
            refreshToken = body.get("refresh_token").asString,
            userId = user.get("id").asString,
            email = user.get("email")?.takeIf { !it.isJsonNull }?.asString,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000,
        )
    }

    private fun postAuth(path: String, payload: JsonObject): JsonObject {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/$path")
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val obj = if (text.isBlank()) JsonObject() else {
                try {
                    gson.fromJson(text, JsonObject::class.java)
                } catch (_: Exception) {
                    JsonObject()
                }
            }
            if (!resp.isSuccessful) {
                val msg = obj.get("error_description")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("msg")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("message")?.takeIf { !it.isJsonNull }?.asString
                    ?: obj.get("error")?.takeIf { !it.isJsonNull }?.asString
                    ?: "Request failed (${resp.code})"
                throw AuthHttpException(resp.code, msg)
            }
            return obj
        }
    }

    data class UserProfile(
        val id: String,
        val email: String?,
        val isPremium: Boolean,
        val role: String,
        val licenseKey: String?,
    )

    fun signUp(email: String, password: String): Session? {
        val body = JsonObject().apply { addProperty("email", email); addProperty("password", password) }
        return parseSession(postAuth("signup", body))
    }

    fun signIn(email: String, password: String): Session {
        val body = JsonObject().apply { addProperty("email", email); addProperty("password", password) }
        return parseSession(postAuth("token?grant_type=password", body))
            ?: throw IOException("sign in did not return a session")
    }

    fun signInWithGoogleIdToken(idToken: String): Session? {
        val body = JsonObject().apply {
            addProperty("provider", "google")
            addProperty("id_token", idToken.trim())
        }
        return parseSession(postAuth("token?grant_type=id_token", body))
    }

    fun refresh(refreshToken: String): Session {
        val body = JsonObject().apply { addProperty("refresh_token", refreshToken) }
        return parseSession(postAuth("token?grant_type=refresh_token", body))
            ?: throw IOException("could not refresh session")
    }

    fun sendEmailOtp(email: String, emailRedirectTo: String = "https://app.amnishield.com/activate") {
        val trimmedEmail = email.trim().lowercase()
        val body = JsonObject().apply {
            addProperty("email", trimmedEmail)
            addProperty("create_user", true)
            val options = JsonObject().apply {
                addProperty("email_redirect_to", emailRedirectTo)
            }
            add("options", options)
            addProperty("email_redirect_to", emailRedirectTo)
        }
        postAuth("otp", body)
    }

    fun verifyOtp(email: String, token: String, type: String = "email"): Session {
        val cleanToken = token.replace("\\s+".toRegex(), "").trim()
        val cleanEmail = email.trim().lowercase()

        // 1. Primary verification attempt with requested type (standard Supabase GoTrue OTP)
        try {
            val body = JsonObject().apply {
                addProperty("email", cleanEmail)
                addProperty("token", cleanToken)
                addProperty("type", type)
            }
            val session = parseSession(postAuth("verify", body))
            if (session != null) return session
        } catch (e: Exception) {
            // If primary type failed and was "email", try fallback to "signup" or "magiclink"
            if (type == "email") {
                val fallbacks = listOf("signup", "magiclink", "recovery")
                for (fb in fallbacks) {
                    try {
                        val body = JsonObject().apply {
                            addProperty("email", cleanEmail)
                            addProperty("token", cleanToken)
                            addProperty("type", fb)
                        }
                        val session = parseSession(postAuth("verify", body))
                        if (session != null) return session
                    } catch (_: Exception) {
                        // ignore and try next fallback
                    }
                }
            }
            throw e
        }
        throw IOException("Invalid or expired verification code.")
    }

    fun fetchProfile(session: Session): UserProfile? {
        val text = authedGet(session, "profiles?id=eq.${session.userId}&select=id,email,is_premium,role,license_key")
        val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
        if (arr.size() == 0) return null
        val o = arr[0].asJsonObject
        return UserProfile(
            id = o.get("id").asString,
            email = o.get("email")?.takeIf { !it.isJsonNull }?.asString,
            isPremium = o.get("is_premium")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
            role = o.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "user",
            licenseKey = o.get("license_key")?.takeIf { !it.isJsonNull }?.asString,
        )
    }

    fun fetchProfileByEmail(email: String, accessToken: String? = null): UserProfile? {
        val cleanEmail = email.trim().lowercase()
        val reqBuilder = Request.Builder()
            .url("$baseUrl/rest/v1/profiles?email=eq.$cleanEmail&select=id,email,is_premium,role,license_key")
            .header("apikey", anonKey)
            .header("Accept", "application/json")
        if (accessToken != null) {
            reqBuilder.header("Authorization", "Bearer $accessToken")
        }
        val req = reqBuilder.build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || text.isBlank()) return null
            val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
            if (arr == null || arr.size() == 0) return null
            val o = arr[0].asJsonObject
            return UserProfile(
                id = o.get("id")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                email = o.get("email")?.takeIf { !it.isJsonNull }?.asString ?: cleanEmail,
                isPremium = o.get("is_premium")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                role = o.get("role")?.takeIf { !it.isJsonNull }?.asString ?: "user",
                licenseKey = o.get("license_key")?.takeIf { !it.isJsonNull }?.asString,
            )
        }
    }

    fun resend(email: String, type: String) {
        val body = JsonObject().apply { addProperty("email", email); addProperty("type", type) }
        postAuth("resend", body)
    }

    fun recover(email: String) {
        postAuth("recover", JsonObject().apply { addProperty("email", email) })
    }

    fun updatePassword(session: Session, newPassword: String) {
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/user")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .put(JsonObject().apply { addProperty("password", newPassword) }.toString().toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("could not update password (${resp.code})")
        }
    }

    private fun authedGet(session: Session, path: String): String {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("read failed (${resp.code}): $text")
            return text
        }
    }

    private fun authedPost(session: Session, path: String, jsonBody: String, prefer: String) {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", prefer)
            .post(jsonBody.toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("write failed (${resp.code}): ${resp.body?.string()}")
        }
    }

    fun getVault(session: Session): VaultRow? {
        val text = authedGet(session, "vault?user_id=eq.${session.userId}&select=kdf_salt,kdf_params,wrapped_dek")
        val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
        if (arr.size() == 0) return null
        val row = arr[0].asJsonObject
        return VaultRow(
            saltB64 = row.get("kdf_salt").asString,
            paramsJson = row.get("kdf_params").toString(),
            wrappedB64 = row.get("wrapped_dek").asString,
        )
    }

    fun insertVault(session: Session, saltB64: String, paramsJson: String, wrappedB64: String) {
        val obj = JsonObject().apply {
            addProperty("user_id", session.userId)
            addProperty("kdf_salt", saltB64)
            add("kdf_params", gson.fromJson(paramsJson, JsonObject::class.java))
            addProperty("wrapped_dek", wrappedB64)
        }
        authedPost(session, "vault", obj.toString(), "return=minimal")
    }

    fun upsertDevice(
        session: Session,
        id: String,
        platform: String,
        label: String,
        fcmToken: String? = null,
        policyPayload: JsonObject? = null
    ) {
        val nowIso = java.time.Instant.now().toString()
        val obj = JsonObject().apply {
            addProperty("id", id)
            addProperty("user_id", session.userId)
            addProperty("owner_id", session.userId)
            addProperty("platform", platform)
            addProperty("label", label)
            addProperty("device_name", label)
            addProperty("last_seen", nowIso)
            addProperty("last_heartbeat", nowIso)
            addProperty("is_online", true)
            addProperty("sync_enabled", true)
            if (fcmToken != null) addProperty("fcm_token", fcmToken)
            if (policyPayload != null) add("policy_payload", policyPayload)
        }
        authedPost(session, "devices?on_conflict=id", "[$obj]", "resolution=merge-duplicates,return=minimal")
    }

    fun devices(session: Session): List<DeviceRow> {
        val selectCols = "id,owner_id,user_id,device_name,label,platform,is_managed,policy_payload,last_heartbeat,last_seen,is_online,sync_enabled"
        val text = try {
            authedGet(session, "devices?or=(user_id.eq.${session.userId},owner_id.eq.${session.userId})&select=$selectCols&order=last_heartbeat.desc.nullslast")
        } catch (_: Exception) {
            authedGet(session, "devices?user_id=eq.${session.userId}&select=id,platform,label,last_seen&order=last_seen.desc")
        }
        return gson.fromJson(text, com.google.gson.JsonArray::class.java).map { el ->
            val o = el.asJsonObject
            val labelVal = o.get("label")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("device_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            val devNameVal = o.get("device_name")?.takeIf { !it.isJsonNull }?.asString ?: labelVal
            val lastSeenVal = o.get("last_seen")?.takeIf { !it.isJsonNull }?.asString
                ?: o.get("last_heartbeat")?.takeIf { !it.isJsonNull }?.asString
            val heartbeatVal = o.get("last_heartbeat")?.takeIf { !it.isJsonNull }?.asString ?: lastSeenVal
            val policyObj = if (o.has("policy_payload") && !o.get("policy_payload").isJsonNull && o.get("policy_payload").isJsonObject) {
                o.getAsJsonObject("policy_payload")
            } else null
            DeviceRow(
                id = o.get("id").asString,
                platform = o.get("platform")?.takeIf { !it.isJsonNull }?.asString ?: "device",
                label = labelVal,
                lastSeen = lastSeenVal,
                deviceName = devNameVal,
                ownerId = o.get("owner_id")?.takeIf { !it.isJsonNull }?.asString,
                userId = o.get("user_id")?.takeIf { !it.isJsonNull }?.asString,
                isManaged = o.get("is_managed")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                isOnline = o.get("is_online")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                syncEnabled = o.get("sync_enabled")?.takeIf { !it.isJsonNull }?.asBoolean ?: true,
                lastHeartbeat = heartbeatVal,
                policyPayload = policyObj
            )
        }
    }

    fun clearDeviceToken(session: Session, id: String) {
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/devices?id=eq.$id")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .patch(JsonObject().apply { add("fcm_token", com.google.gson.JsonNull.INSTANCE) }.toString().toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { }
    }

    fun fetchDevicePolicy(deviceId: String, accessToken: String? = null): JsonObject? {
        if (deviceId.isBlank()) return null
        val encodedId = java.net.URLEncoder.encode(deviceId.trim(), "UTF-8")
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: anonKey
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/devices?id=eq.$encodedId&select=id,owner_id,user_id,device_name,label,is_managed,policy_payload,sync_enabled")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val array = gson.fromJson(body, com.google.gson.JsonArray::class.java)
            if (array == null || array.size() == 0) return null
            val devObj = array[0].asJsonObject
            return if (devObj.has("policy_payload") && !devObj.get("policy_payload").isJsonNull && devObj.get("policy_payload").isJsonObject) {
                devObj.getAsJsonObject("policy_payload")
            } else {
                null
            }
        }
    }

    fun updateDevicePolicy(
        deviceId: String,
        policyPayload: JsonObject,
        deviceName: String? = null,
        accessToken: String? = null
    ): Boolean {
        if (deviceId.isBlank()) return false
        val encodedId = java.net.URLEncoder.encode(deviceId.trim(), "UTF-8")
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: anonKey
        val nowIso = java.time.Instant.now().toString()
        val updateObj = JsonObject().apply {
            add("policy_payload", policyPayload)
            addProperty("last_heartbeat", nowIso)
            addProperty("last_seen", nowIso)
            addProperty("is_online", true)
            addProperty("sync_enabled", true)
            addProperty("platform", "android")
            if (!deviceName.isNullOrBlank()) {
                addProperty("device_name", deviceName)
                addProperty("label", deviceName)
            }
        }
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/devices?id=eq.$encodedId")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .patch(updateObj.toString().toRequestBody(jsonType))
            .build()
        client.newCall(req).execute().use { resp ->
            return resp.isSuccessful
        }
    }

    fun fetchPolicyByOwner(ownerId: String, accessToken: String? = null): JsonObject? {
        if (ownerId.isBlank()) return null
        val encodedOwner = java.net.URLEncoder.encode(ownerId.trim(), "UTF-8")
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: anonKey
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/devices?or=(owner_id.eq.$encodedOwner,user_id.eq.$encodedOwner)&select=id,policy_payload,last_heartbeat&order=last_heartbeat.desc.nullslast")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            val array = gson.fromJson(body, com.google.gson.JsonArray::class.java) ?: return null
            for (elem in array) {
                val obj = elem.asJsonObject
                if (obj.has("policy_payload") && !obj.get("policy_payload").isJsonNull && obj.get("policy_payload").isJsonObject) {
                    val payload = obj.getAsJsonObject("policy_payload")
                    if (payload.entrySet().isNotEmpty()) {
                        return payload
                    }
                }
            }
            return null
        }
    }

    fun upsertDevicePolicyForOwner(
        ownerId: String,
        deviceId: String?,
        deviceName: String,
        policyPayload: JsonObject,
        accessToken: String? = null
    ): Boolean {
        if (ownerId.isBlank()) return false
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: anonKey
        val nowIso = java.time.Instant.now().toString()
        if (!deviceId.isNullOrBlank()) {
            if (updateDevicePolicy(deviceId, policyPayload, deviceName, bearer)) {
                return true
            }
        }
        val encodedOwner = java.net.URLEncoder.encode(ownerId.trim(), "UTF-8")
        val updateObj = JsonObject().apply {
            add("policy_payload", policyPayload)
            addProperty("last_heartbeat", nowIso)
            addProperty("last_seen", nowIso)
            addProperty("is_online", true)
            addProperty("sync_enabled", true)
        }
        val patchReq = Request.Builder()
            .url("$baseUrl/rest/v1/devices?or=(owner_id.eq.$encodedOwner,user_id.eq.$encodedOwner)&platform=eq.android")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $bearer")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=minimal")
            .patch(updateObj.toString().toRequestBody(jsonType))
            .build()
        client.newCall(patchReq).execute().use { resp ->
            if (resp.isSuccessful) return true
        }
        return false
    }

    fun fetchUserCloudPolicy(session: Session, preferredDeviceId: String? = null): JsonObject? {
        // 1. Check preferred device ID if provided
        if (!preferredDeviceId.isNullOrBlank()) {
            runCatching { fetchDevicePolicy(preferredDeviceId, session.accessToken) }.getOrNull()?.let {
                if (it.entrySet().isNotEmpty()) return it
            }
        }

        // 2. Check user's devices in `devices` table
        runCatching { fetchPolicyByOwner(session.userId, session.accessToken) }.getOrNull()?.let {
            if (it.entrySet().isNotEmpty()) return it
        }

        // 3. Check `sync_records` table (`policy_v2` or `amnishield_config`)
        runCatching {
            val path = "sync_records?user_id=eq.${session.userId}&namespace=in.(policy_v2,amnishield_config)" +
                "&deleted=eq.false&order=updated_at.desc&limit=5&select=namespace,record_key,ciphertext,updated_at"
            val text = authedGet(session, path)
            val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
            if (arr != null) {
                for (el in arr) {
                    val row = el.asJsonObject
                    val rawCipher = row.get("ciphertext")?.takeIf { !it.isJsonNull }?.asString ?: continue
                    val rawJson = if (rawCipher.startsWith("plain:")) rawCipher.removePrefix("plain:") else rawCipher
                    val parsed = runCatching { gson.fromJson(rawJson, JsonObject::class.java) }.getOrNull()
                    if (parsed != null && parsed.entrySet().isNotEmpty()) {
                        return parsed
                    }
                }
            }
        }
        return null
    }

    fun pushUserCloudPolicy(
        session: Session,
        deviceId: String,
        deviceName: String,
        policyPayload: JsonObject
    ): Boolean {
        var anySuccess = false
        val plainCipher = "plain:${policyPayload}"
        val nowVersion = System.currentTimeMillis()

        runCatching {
            upsertRecord(
                session = session,
                namespace = "policy_v2",
                recordKey = "default",
                deviceId = deviceId,
                ciphertextB64 = plainCipher,
                version = nowVersion,
                deleted = false
            )
            anySuccess = true
        }

        runCatching {
            upsertRecord(
                session = session,
                namespace = "amnishield_config",
                recordKey = "rules",
                deviceId = deviceId,
                ciphertextB64 = plainCipher,
                version = nowVersion,
                deleted = false
            )
            anySuccess = true
        }

        runCatching {
            if (upsertDevicePolicyForOwner(session.userId, deviceId, deviceName, policyPayload, session.accessToken)) {
                anySuccess = true
            }
        }

        return anySuccess
    }

    fun pull(session: Session, cursor: String): List<SyncRow> {
        val encodedCursor = java.net.URLEncoder.encode(cursor, "UTF-8")
        val path = "sync_records?user_id=eq.${session.userId}&updated_at=gt.$encodedCursor" +
            "&order=updated_at.asc&select=namespace,record_key,device_id,ciphertext,version,deleted,updated_at"
        val text = authedGet(session, path)
        val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
        return arr.map { el ->
            val o = el.asJsonObject
            SyncRow(
                namespace = o.get("namespace").asString,
                recordKey = o.get("record_key").asString,
                deviceId = o.get("device_id")?.takeIf { !it.isJsonNull }?.asString,
                ciphertext = o.get("ciphertext").asString,
                version = o.get("version").asLong,
                deleted = o.get("deleted").asBoolean,
                updatedAt = o.get("updated_at").asString,
            )
        }
    }

    fun upsertRecord(session: Session, namespace: String, recordKey: String, deviceId: String, ciphertextB64: String, version: Long, deleted: Boolean = false) {
        val obj = JsonObject().apply {
            addProperty("user_id", session.userId)
            addProperty("namespace", namespace)
            addProperty("record_key", recordKey)
            addProperty("device_id", deviceId)
            addProperty("ciphertext", ciphertextB64)
            addProperty("version", version)
            addProperty("deleted", deleted)
        }
        authedPost(
            session,
            "sync_records?on_conflict=user_id,namespace,record_key",
            "[$obj]",
            "resolution=merge-duplicates,return=minimal",
        )
    }

    data class PairingResult(
        val success: Boolean,
        val deviceId: String?,
        val ownerId: String?,
        val isManaged: Boolean,
        val policyPayload: JsonObject?,
        val message: String
    )

    /**
     * Claims an ephemeral 6-digit pairing token generated by the Web Admin Console.
     * Updates the pending device record with real hardware model, clears token, and retrieves the initial policy payload.
     */
    fun claimPairingToken(token: String, deviceName: String, platform: String = "android"): PairingResult {
        val cleanToken = token.trim()
        if (cleanToken.length < 4) {
            return PairingResult(false, null, null, false, null, "Invalid PIN format")
        }

        try {
            val encodedToken = java.net.URLEncoder.encode(cleanToken, "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/devices?pairing_token=eq.$encodedToken&select=id,owner_id,user_id,device_name,label,is_managed,policy_payload,pairing_token_expires_at")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return PairingResult(false, null, null, false, null, "Server error (${resp.code})")
                }
                val arr = gson.fromJson(text, com.google.gson.JsonArray::class.java)
                if (arr == null || arr.size() == 0) {
                    return PairingResult(false, null, null, false, null, "Invalid or expired pairing PIN ($cleanToken)")
                }
                val dev = arr[0].asJsonObject
                val deviceId = dev.get("id").asString
                val ownerId = dev.get("owner_id")?.takeIf { !it.isJsonNull }?.asString
                    ?: dev.get("user_id")?.takeIf { !it.isJsonNull }?.asString
                val isManaged = dev.get("is_managed")?.takeIf { !it.isJsonNull }?.asBoolean ?: true
                val policyPayload = if (dev.has("policy_payload") && !dev.get("policy_payload").isJsonNull && dev.get("policy_payload").isJsonObject) {
                    dev.getAsJsonObject("policy_payload")
                } else null

                // Check expiry
                val expiresAtStr = dev.get("pairing_token_expires_at")?.takeIf { !it.isJsonNull }?.asString
                if (expiresAtStr != null) {
                    try {
                        val expiresInstant = if (expiresAtStr.endsWith("Z", ignoreCase = true)) {
                            java.time.Instant.parse(expiresAtStr)
                        } else {
                            java.time.OffsetDateTime.parse(expiresAtStr).toInstant()
                        }
                        if (java.time.Instant.now().isAfter(expiresInstant)) {
                            return PairingResult(false, null, null, false, null, "Pairing PIN has expired. Please generate a new code.")
                        }
                    } catch (_: Exception) {}
                }

                // Claim the device
                val nowIso = java.time.Instant.now().toString()
                val updateObj = JsonObject().apply {
                    addProperty("device_name", deviceName)
                    addProperty("label", deviceName)
                    addProperty("platform", platform)
                    add("pairing_token", com.google.gson.JsonNull.INSTANCE)
                    addProperty("last_heartbeat", nowIso)
                    addProperty("last_seen", nowIso)
                    addProperty("is_online", true)
                    addProperty("is_managed", true)
                    addProperty("sync_enabled", true)
                }

                val patchReq = Request.Builder()
                    .url("$baseUrl/rest/v1/devices?id=eq.$deviceId")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $anonKey")
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(updateObj.toString().toRequestBody(jsonType))
                    .build()

                client.newCall(patchReq).execute().use { patchResp ->
                    if (patchResp.isSuccessful) {
                        return PairingResult(true, deviceId, ownerId, isManaged, policyPayload, "Device paired successfully!")
                    } else {
                        return PairingResult(false, deviceId, ownerId, isManaged, policyPayload, "Failed to update device (${patchResp.code})")
                    }
                }
            }
        } catch (e: Exception) {
            return PairingResult(false, null, null, false, null, e.message ?: "Connection error")
        }
    }

    data class CommunityAccessResult(
        val success: Boolean,
        val status: String? = null,
        val appId: String? = null,
        val licenseKey: String? = null,
        val tempExpiresAt: Long? = null,
        val verifiedExpiresAt: Long? = null,
        val message: String? = null,
    )

    fun registerCommunityAccess(
        userName: String,
        email: String,
        appId: String,
        deviceIdentifier: String? = null
    ): CommunityAccessResult {
        return try {
            val payload = JsonObject().apply {
                addProperty("action", "request")
                addProperty("name", userName)
                addProperty("email", email)
                addProperty("app_id", appId)
                if (!deviceIdentifier.isNullOrBlank()) {
                    addProperty("device_identifier", deviceIdentifier)
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/community-access")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonType))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = if (text.isNotBlank()) {
                    try {
                        gson.fromJson(text, JsonObject::class.java)
                    } catch (_: Exception) {
                        JsonObject()
                    }
                } else JsonObject()

                if (resp.isSuccessful) {
                    CommunityAccessResult(
                        success = true,
                        status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString ?: "pending_review",
                        appId = obj.get("app_id")?.takeIf { !it.isJsonNull }?.asString ?: appId,
                        licenseKey = obj.get("license_key")?.takeIf { !it.isJsonNull }?.asString,
                        tempExpiresAt = obj.get("temp_expires_at")?.takeIf { !it.isJsonNull }?.asLong,
                        verifiedExpiresAt = obj.get("verified_expires_at")?.takeIf { !it.isJsonNull }?.asLong,
                        message = obj.get("message")?.takeIf { !it.isJsonNull }?.asString
                    )
                } else {
                    val errMsg = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
                        ?: "Request failed (${resp.code})"
                    val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString
                    CommunityAccessResult(
                        success = false,
                        status = status,
                        message = errMsg
                    )
                }
            }
        } catch (e: Exception) {
            CommunityAccessResult(
                success = false,
                message = e.message ?: "Network error"
            )
        }
    }

    fun cancelCommunityAccess(email: String): CommunityAccessResult {
        return try {
            val payload = JsonObject().apply {
                addProperty("action", "cancel")
                addProperty("email", email)
            }
            val req = Request.Builder()
                .url("$baseUrl/functions/v1/community-access")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonType))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val obj = if (text.isNotBlank()) {
                    try {
                        gson.fromJson(text, JsonObject::class.java)
                    } catch (_: Exception) {
                        JsonObject()
                    }
                } else JsonObject()

                if (resp.isSuccessful) {
                    CommunityAccessResult(
                        success = true,
                        status = "cancelled",
                        message = obj.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "Cancelled"
                    )
                } else {
                    val errMsg = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
                        ?: "Cancellation failed (${resp.code})"
                    CommunityAccessResult(
                        success = false,
                        message = errMsg
                    )
                }
            }
        } catch (e: Exception) {
            CommunityAccessResult(
                success = false,
                message = e.message ?: "Network error"
            )
        }
    }

    companion object {
        const val ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpyZ3BtY29tdmliZ2tsbXZueHVkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODM4NzE0MjQsImV4cCI6MjA5OTQ0NzQyNH0.JvPyt8ye2OCBsLXMBUoIOXUz1E5UHDHOY55oDH6Ce10"
    }
}
