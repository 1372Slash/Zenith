package com.etrisad.zenith.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.ui.widget.AppStreakWidget
import com.etrisad.zenith.ui.widget.GlobalStreakWidget
import com.etrisad.zenith.ui.widget.PhoneFreeTimeWidget
import com.etrisad.zenith.ui.widget.RemainingTargetWidget
import com.etrisad.zenith.ui.widget.TotalScreenTimeWidget
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
            GlobalStreakWidget().updateAll(applicationContext)
            AppStreakWidget().updateAll(applicationContext)
            TotalScreenTimeWidget().updateAll(applicationContext)
            RemainingTargetWidget().updateAll(applicationContext)
            PhoneFreeTimeWidget().updateAll(applicationContext)
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
