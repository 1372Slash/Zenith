package com.etrisad.zenith.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etrisad.zenith.util.BackupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val directoryUriString = inputData.getString("directory_uri") ?: return@withContext Result.failure()
        val directoryUri = Uri.parse(directoryUriString)
        
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFolder = DocumentFile.fromTreeUri(applicationContext, directoryUri) ?: return@withContext Result.failure()

            val fileName = "AutoBackup_$timestamp.zip"
            val targetFile = backupFolder.createFile("application/zip", fileName) ?: return@withContext Result.failure()

            val result = BackupUtils.backupDatabase(applicationContext, targetFile.uri)

            if (result.isSuccess) {
                cleanupOldBackups(backupFolder)
                sendNotification("Backup Successful", "Your data has been automatically backed up as $fileName")
                Result.success()
            } else {
                targetFile.delete()
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendNotification("Backup Failed", "An error occurred during automatic backup.")
            Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "zenith_backup_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "Database Backups",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifications for automatic database backups"
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cleanupOldBackups(rootFolder: DocumentFile) {
        val backups = rootFolder.listFiles()
            .filter { it.isFile && it.name?.startsWith("AutoBackup_") == true && it.name?.endsWith(".zip") == true }
            .sortedByDescending { it.name }

        if (backups.size > 10) {
            for (i in 10 until backups.size) {
                backups[i].delete()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
