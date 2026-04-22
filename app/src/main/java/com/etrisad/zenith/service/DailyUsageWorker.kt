package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyUsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isBackup = inputData.getBoolean("is_backup", false)
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Jika ini adalah tugas backup, hanya jalankan di jendela jam 23:00 - 23:59
        if (isBackup && currentHour != 23) {
            return Result.success()
        }

        val database = ZenithDatabase.getDatabase(applicationContext)
        val dailyUsageDao = database.dailyUsageDao()
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = applicationContext.packageManager

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // LOGIKA PENYELAMAT DATA:
        // Jika ini bukan backup, dan dijalankan antara jam 00:00 - 09:00 pagi,
        // kemungkinan besar ini adalah tugas semalam yang tertunda karena HP mati.
        // Maka kita harus menyimpan data untuk "KEMARIN".
        if (!isBackup && currentHour < 9) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val dateString = dateFormat.format(calendar.time)

        // Set range untuk hari target [00:00:00, 23:59:59]
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        // Get launcher apps to filter
        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val excludePackages = setOfNotNull(applicationContext.packageName, launcherPackage)

        // Query usage stats - gunakan queryAndAggregateUsageStats untuk konsistensi dengan UI aplikasi
        val stats = usm.queryAndAggregateUsageStats(startTime, System.currentTimeMillis().coerceAtMost(endTime))
        
        val usages = mutableListOf<DailyUsageEntity>()
        var totalUsage = 0L

        // Ambil data yang sudah diagregasi oleh sistem
        stats.forEach { (pkg, stat) ->
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach
            
            // Gunakan logika penentuan waktu yang sama dengan HomeViewModel
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                usages.add(DailyUsageEntity(date = dateString, packageName = pkg, usageTimeMillis = time))
                totalUsage += time
            }
        }

        // Save total global usage
        usages.add(DailyUsageEntity(date = dateString, packageName = "TOTAL", usageTimeMillis = totalUsage))

        dailyUsageDao.insertAll(usages)
        
        if (!isBackup) {
            sendDataSavedNotification()
            // Simpan data selama 21 hari sesuai kebutuhan
            val cleanupCal = Calendar.getInstance()
            cleanupCal.add(Calendar.DAY_OF_YEAR, -21)
            dailyUsageDao.deleteOldUsage(dateFormat.format(cleanupCal.time))
        }

        return Result.success()
    }

    private fun sendDataSavedNotification() {
        val channelId = "zenith_usage_sync"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channel = NotificationChannel(
            channelId,
            "Usage Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifies when daily usage data is saved"
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Daily Report Prepared")
            .setContentText("Your screen time usage for today has been safely saved.")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        manager.notify(999, notification)
    }

    companion object {
        private const val WORK_NAME_MAIN = "DailyUsageSyncWorker"
        private const val WORK_NAME_BACKUP = "DailyUsageSyncWorkerBackup"

        fun schedule(context: Context) {
            scheduleMainSync(context)
            scheduleBackupSync(context)
        }

        private fun scheduleMainSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Set ke 23:50 untuk menyimpan data sebelum hari berganti
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 50)
            calendar.set(Calendar.SECOND, 0)
            
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = calendar.timeInMillis - now

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyUsageWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .setInputData(workDataOf("is_backup" to false))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_MAIN,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        }

        private fun scheduleBackupSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // Jalankan setiap 20 menit. Worker hanya akan mengeksekusi logika jika jam berada di antara 23:00-23:59
            val backupWorkRequest = PeriodicWorkRequestBuilder<DailyUsageWorker>(20, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("is_backup" to true))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_BACKUP,
                ExistingPeriodicWorkPolicy.UPDATE,
                backupWorkRequest
            )
        }
    }
}
