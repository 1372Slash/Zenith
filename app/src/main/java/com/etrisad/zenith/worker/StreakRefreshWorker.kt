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
            app.userPreferencesRepository.refreshAppStreaks(app.shieldRepository)
            app.userPreferencesRepository.refreshWebStreaks(app.shieldRepository)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<StreakRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
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
