package com.etrisad.zenith.service

import android.content.Context
import androidx.work.*
import com.etrisad.zenith.ZenithApplication
import java.util.concurrent.TimeUnit

class UsageSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val application = applicationContext as ZenithApplication
        val repository = application.shieldRepository
        val preferencesRepository = application.userPreferencesRepository
        val syncManager = UsageSyncManager(applicationContext, repository, preferencesRepository)
        
        return try {
            syncManager.syncUsageData()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "UsageSyncWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
