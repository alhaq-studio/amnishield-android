package com.alhaq.amnshield.data.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.alhaq.amnshield.utils.SavedPreferencesLoader

@OptIn(FlowPreview::class)
class PlaystoreSyncProvider(private val context: Context) : SyncProvider {

    private val gson = Gson()
    private val rest by lazy { SupabaseRest() }
    private val keys by lazy { SecureKeyStore(context) }
    private val savedPrefs by lazy { SavedPreferencesLoader(context) }
    
    private val _status = MutableStateFlow(SyncStatus())
    override val status: StateFlow<SyncStatus> get() = _status

    override val isAvailable: Boolean
        get() = keys.accessToken != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun refresh() {
        val session = getOrRefreshSession() ?: return
        _status.value = SyncStatus(isSyncing = true)
        try {
            val rows = rest.pull(session, keys.cursor)
            if (rows.isNotEmpty()) {
                val latest = rows.maxOf { it.updatedAt }
                keys.cursor = latest
            }
            _status.value = SyncStatus(isSyncing = false, lastSyncTime = System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = SyncStatus(isSyncing = false, error = e.message)
        }
    }

    override suspend fun pushNow() {
        val session = getOrRefreshSession() ?: return
        _status.value = SyncStatus(isSyncing = true)
        try {
            // Push active rules configuration to Supabase sync_records
            val rulesJson = gson.toJson(savedPrefs.loadAppBlockerScheduleRules())
            val ciphertext = keys.dekB64 ?: "plain:$rulesJson"
            rest.upsertRecord(
                session = session,
                namespace = "amnshield_config",
                recordKey = "rules",
                deviceId = keys.deviceId,
                ciphertextB64 = ciphertext,
                version = System.currentTimeMillis()
            )
            _status.value = SyncStatus(isSyncing = false, lastSyncTime = System.currentTimeMillis())
        } catch (e: Exception) {
            _status.value = SyncStatus(isSyncing = false, error = e.message)
        }
    }

    override fun wake() {
        scope.launch {
            refresh()
            pushNow()
        }
    }

    fun onFcmToken(token: String) {
        keys.fcmToken = token
        scope.launch {
            val session = getOrRefreshSession() ?: return@launch
            rest.upsertDevice(session, keys.deviceId, "android", keys.deviceName, token)
        }
    }

    private fun getOrRefreshSession(): SupabaseRest.Session? {
        val token = keys.accessToken
        val refresh = keys.refreshToken
        if (token.isNullOrEmpty() || refresh.isNullOrEmpty()) return null
        return try {
            val session = rest.refresh(refresh)
            keys.accessToken = session.accessToken
            keys.refreshToken = session.refreshToken
            session
        } catch (e: Exception) {
            null
        }
    }
}
