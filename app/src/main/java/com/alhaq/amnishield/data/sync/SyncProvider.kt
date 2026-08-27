package com.alhaq.amnishield.data.sync

import kotlinx.coroutines.flow.StateFlow

interface SyncProvider {
    val isAvailable: Boolean
    val status: StateFlow<SyncStatus>
    suspend fun refresh()
    suspend fun pushNow()
    fun wake()
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val error: String? = null
)
