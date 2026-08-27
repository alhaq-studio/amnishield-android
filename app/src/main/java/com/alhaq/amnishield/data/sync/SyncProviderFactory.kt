package com.alhaq.amnishield.data.sync

import android.content.Context

fun createSyncProvider(context: Context): SyncProvider = PlaystoreSyncProvider(context)
