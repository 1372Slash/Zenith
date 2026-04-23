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
import com.etrisad.zenith.data.local.database.ZenithDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
            val dbName = "zenith_database"
            val dbFile = applicationContext.getDatabasePath(dbName)
            val dbShm = File(dbFile.path + "-shm")
            val dbWal = File(dbFile.path + "-wal")

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val backupFolder = DocumentFile.fromTreeUri(applicationContext, directoryUri) ?: return@withContext Result.failure()
            
            // Nama folder menggunakan prefix "AutoBackup_" untuk diferensiasi dengan manual backup
            val targetDir = backupFolder.createDirectory("AutoBackup_$timestamp") ?: return@withContext Result.failure()

            copyFileToDocument(dbFile, targetDir, dbName)
            if (dbShm.exists()) copyFileToDocument(dbShm, targetDir, "$dbName-shm")
            if (dbWal.exists()) copyFileToDocument(dbWal, targetDir, "$dbName-wal")

            // OPTIMASI MEMORI: Hapus backup lama jika sudah lebih dari 10 (Hanya untuk folder AutoBackup)
            cleanupOldBackups(backupFolder)

            sendNotification("Backup Successful", "Your data has been automatically backed up to $timestamp")

            Result.success()
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
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // Icon standard Android
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cleanupOldBackups(rootFolder: DocumentFile) {
        val backups = rootFolder.listFiles()
            .filter { it.isDirectory && it.name?.startsWith("AutoBackup_") == true }
            .sortedByDescending { it.name } // Urutkan dari yang terbaru (Lexicographical order works for yyyyMMdd)

        if (backups.size > 10) {
            // Hapus folder yang ke-11 dan seterusnya
            for (i in 10 until backups.size) {
                backups[i].delete()
            }
        }
    }

    private fun copyFileToDocument(sourceFile: File, targetDir: DocumentFile, displayName: String) {
        val targetFile = targetDir.createFile("application/octet-stream", displayName) ?: return
        applicationContext.contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
