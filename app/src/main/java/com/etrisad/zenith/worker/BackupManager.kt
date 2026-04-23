package com.etrisad.zenith.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BackupManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun scheduleBackup(intervalHours: Int, directoryUri: String) {
        val backupData = Data.Builder()
            .putString("directory_uri", directoryUri)
            .build()

        val backupRequest = PeriodicWorkRequestBuilder<DatabaseBackupWorker>(
            intervalHours.toLong(), TimeUnit.HOURS
        )
            .setInputData(backupData)
            .addTag(BACKUP_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BACKUP_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE, // Update if already exists to reflect new interval/uri
            backupRequest
        )
    }

    fun cancelBackup() {
        workManager.cancelUniqueWork(BACKUP_WORK_TAG)
    }

    companion object {
        private const val BACKUP_WORK_TAG = "zenith_database_backup"
    }
}
