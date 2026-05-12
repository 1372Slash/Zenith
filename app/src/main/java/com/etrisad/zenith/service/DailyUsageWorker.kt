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
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyUsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isBackup = inputData.getBoolean("is_backup", false)
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        if (isBackup && currentHour != 23) {
            return Result.success()
        }

        val database = ZenithDatabase.getDatabase(applicationContext)
        val dailyUsageDao = database.dailyUsageDao()
        val hourlyUsageDao = database.hourlyUsageDao()
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = applicationContext.packageManager

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        if (!isBackup && currentHour < 9) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val dateString = dateFormat.format(calendar.time)

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

        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val excludePackages = setOfNotNull(applicationContext.packageName, launcherPackage)

        val stats = usm.queryAndAggregateUsageStats(startTime, System.currentTimeMillis().coerceAtMost(endTime))
        
        val usages = mutableListOf<DailyUsageEntity>()
        var totalUsage = 0L
        val timeSinceStart = System.currentTimeMillis().coerceAtMost(endTime) - startTime

        stats.forEach { (pkg, stat) ->
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach

            var time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > timeSinceStart + 30000) {
                time = timeSinceStart
            }
            if (time > 0) {
                usages.add(DailyUsageEntity(date = dateString, packageName = pkg, usageTimeMillis = time))
                totalUsage += time
            }
        }

        val userPrefsRepo = UserPreferencesRepository(applicationContext)
        var finalTotalUsage = totalUsage

        for (i in 1..5) {
            val lastKnown = userPrefsRepo.userPreferencesFlow.first()
            if (lastKnown.lastKnownDailyUsageDate == dateString && lastKnown.lastKnownDailyUsage > finalTotalUsage) {
                // Ensure last known usage doesn't exceed possible time in the day
                val cappedLastKnown = if (lastKnown.lastKnownDailyUsage > timeSinceStart + 60000) {
                    timeSinceStart
                } else {
                    lastKnown.lastKnownDailyUsage
                }
                finalTotalUsage = maxOf(finalTotalUsage, cappedLastKnown)
                break 
            }
            if (i < 5) delay(2000)
        }

        usages.add(DailyUsageEntity(date = dateString, packageName = "TOTAL", usageTimeMillis = finalTotalUsage))

        val allShields = database.shieldDao().getAllShields().first()
        val shieldPkgs = allShields.filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
        val goalPkgs = allShields.filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()

        var sUsage = 0L
        var gUsage = 0L
        usages.filter { it.packageName != "TOTAL" }.forEach { 
            if (it.packageName in shieldPkgs) sUsage += it.usageTimeMillis
            else if (it.packageName in goalPkgs) gUsage += it.usageTimeMillis
        }
        val oUsage = (finalTotalUsage - (sUsage + gUsage)).coerceAtLeast(0L)

        usages.add(DailyUsageEntity(date = dateString, packageName = "SHIELD_TOTAL", usageTimeMillis = sUsage))
        usages.add(DailyUsageEntity(date = dateString, packageName = "GOAL_TOTAL", usageTimeMillis = gUsage))
        usages.add(DailyUsageEntity(date = dateString, packageName = "OTHER_TOTAL", usageTimeMillis = oUsage))

        dailyUsageDao.insertAll(usages)

        val hourlyUsages = mutableListOf<HourlyUsageEntity>()
        val hourlyMap = mutableMapOf<Int, Long>()
        val hourlyAppUsage = mutableMapOf<Int, MutableMap<String, Long>>()
        val events = usm.queryEvents(startTime, System.currentTimeMillis().coerceAtMost(endTime))
        val event = android.app.usage.UsageEvents.Event()
        val lastEventTime = mutableMapOf<String, Long>()
        val cal = Calendar.getInstance()
        
        var isScreenOn = false 

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            val type = event.eventType

            when (type) {
                android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    lastEventTime.forEach { (p, sTime) ->
                        val duration = time - sTime
                        if (duration > 0) {
                            cal.timeInMillis = sTime
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + duration
                            if (p !in excludePackages && p in launcherApps) {
                                val pkgMap = hourlyAppUsage.getOrPut(hour) { mutableMapOf() }
                                pkgMap[p] = (pkgMap[p] ?: 0L) + duration
                            }
                        }
                    }
                    lastEventTime.clear()
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (isScreenOn) {
                        val className = event.className ?: ""
                        if (!className.contains("Notification", ignoreCase = true) &&
                            !className.contains("Toast", ignoreCase = true)) {
                            lastEventTime[pkg] = time
                        }
                    }
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val sTime = lastEventTime.remove(pkg)
                    if (sTime != null) {
                        val duration = time - sTime
                        if (duration > 0) {
                            cal.timeInMillis = sTime
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + duration

                            if (pkg !in excludePackages && pkg in launcherApps) {
                                val pkgMap = hourlyAppUsage.getOrPut(hour) { mutableMapOf() }
                                pkgMap[pkg] = (pkgMap[pkg] ?: 0L) + duration
                            }
                        }
                    }
                }
            }
        }

        hourlyMap.forEach { (hour, time) ->
            hourlyUsages.add(HourlyUsageEntity(date = dateString, hour = hour, packageName = "TOTAL", usageTimeMillis = time))
        }

        hourlyAppUsage.forEach { (hour, appMap) ->
            appMap.forEach { (pkg, time) ->
                hourlyUsages.add(HourlyUsageEntity(date = dateString, hour = hour, packageName = pkg, usageTimeMillis = time))
            }
        }

        if (hourlyUsages.isNotEmpty()) {
            hourlyUsageDao.insertAll(hourlyUsages)
        }
        
        if (!isBackup) {
            sendDataSavedNotification()
            val cleanupCal = Calendar.getInstance()
            cleanupCal.add(Calendar.DAY_OF_YEAR, -21)
            val threshold = dateFormat.format(cleanupCal.time)
            dailyUsageDao.deleteOldUsage(threshold)
            hourlyUsageDao.deleteOldUsage(threshold)
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
