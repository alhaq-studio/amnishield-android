package com.alhaq.amnishield.data.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alhaq.amnishield.utils.SavedPreferencesLoader
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val savedPrefs = SavedPreferencesLoader(applicationContext)
        if (savedPrefs.isStandaloneMode()) {
            cancel(applicationContext)
            return Result.success()
        }

        try {
            PolicySyncManager.syncNow(applicationContext)
        } catch (_: Exception) {}

        SyncGateway.init(applicationContext)
        val provider = SyncGateway.provider
        if (!provider.isAvailable) return Result.success()
        return try {
            provider.refresh()
            provider.pushNow()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "amnishield_sync"

        fun cancel(context: Context) {
            runCatching {
                val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
                if (WorkManager.isInitialized()) {
                    WorkManager.getInstance(appContext).cancelUniqueWork(NAME)
                }
            }
        }

        fun schedule(context: Context) {
            val appContext = runCatching { context.applicationContext }.getOrNull() ?: context
            val savedPrefs = SavedPreferencesLoader(appContext)
            if (savedPrefs.isStandaloneMode()) {
                cancel(appContext)
                return
            }
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(appContext, Configuration.Builder().build())
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(appContext)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
