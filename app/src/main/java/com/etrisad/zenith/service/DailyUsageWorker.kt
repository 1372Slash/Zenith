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
        val database = ZenithDatabase.getDatabase(applicationContext)
        val dailyUsageDao = database.dailyUsageDao()
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = applicationContext.packageManager

        // Get yesterday's date string
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(calendar.time)

        // Set range for yesterday [00:00:00, 23:59:59]
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

        // Query usage stats
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        
        val usages = mutableListOf<DailyUsageEntity>()
        var totalUsage = 0L

        // Aggregation per package
        val aggregatedStats = mutableMapOf<String, Long>()
        stats.forEach { stat ->
            val pkg = stat.packageName
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                aggregatedStats[pkg] = maxOf(aggregatedStats[pkg] ?: 0L, time)
            }
        }

        aggregatedStats.forEach { (pkg, time) ->
            usages.add(DailyUsageEntity(date = dateString, packageName = pkg, usageTimeMillis = time))
            totalUsage += time
        }

        // Save total global usage
        usages.add(DailyUsageEntity(date = dateString, packageName = "TOTAL", usageTimeMillis = totalUsage))

        dailyUsageDao.insertAll(usages)
        sendDataSavedNotification()

        // Clean up old data (> 30 days to be safe, though 21 is required)
        val cleanupCal = Calendar.getInstance()
        cleanupCal.add(Calendar.DAY_OF_YEAR, -30)
        dailyUsageDao.deleteOldUsage(dateFormat.format(cleanupCal.time))

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
        private const val WORK_NAME = "DailyUsageSyncWorker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            // Calculate delay until next midnight
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            
            // Set to 23:59:50 to ensure it runs before actual midnight
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 50)
            
            var delay = calendar.timeInMillis - now
            if (delay < 0) {
                // If it's already past 23:59:50, schedule for next day
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                delay = calendar.timeInMillis - now
            }

            val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyUsageWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyWorkRequest
            )
        }
    }
}
