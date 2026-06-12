package com.etrisad.zenith.worker

import android.content.Context
import androidx.work.*
import com.etrisad.zenith.ZenithApplication
import java.util.concurrent.TimeUnit

class StreakRefreshWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as ZenithApplication
            app.userPreferencesRepository.refreshGlobalStreak(app.shieldRepository)
            app.userPreferencesRepository.refreshAllAppStreaks(app.shieldRepository)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<StreakRefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "StreakRefreshWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
