package com.alhaq.amnshield.data.sync

import android.content.Context

object SyncGateway {
    @Volatile
    private var instance: SyncProvider? = null

    val provider: SyncProvider
        get() = instance ?: throw IllegalStateException("SyncGateway not initialized")

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    instance = createSyncProvider(context)
                }
            }
        }
    }
}
