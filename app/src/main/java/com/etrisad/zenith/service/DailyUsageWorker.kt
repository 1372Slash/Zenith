package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.etrisad.zenith.R
import com.etrisad.zenith.data.local.database.ZenithDatabase
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DailyUsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isBackup = inputData.getBoolean("is_backup", false)
        val nowCal = Calendar.getInstance()
        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = nowCal.get(Calendar.MINUTE)
        val calendar = nowCal.clone() as Calendar

        if (isBackup) {
            val isLateNight = currentHour == 23
            val isEarlyMorning = currentHour == 0 && currentMinute <= 30
            if (!isLateNight && !isEarlyMorning) return Result.success()
        }

        val database = ZenithDatabase.getDatabase(applicationContext)
        val dailyUsageDao = database.dailyUsageDao()
        val hourlyUsageDao = database.hourlyUsageDao()
        val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = applicationContext.packageManager
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val isProcessingYesterday = (!isBackup && currentHour < 9) || (isBackup && currentHour == 0)
        if (isProcessingYesterday) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        if (currentHour == 0 && currentMinute < 15) {
            try {
                database.shieldDao().resetAllRemainingTimes()
            } catch (_: Exception) {}
        }
        val dateString = dateFormat.format(calendar.time)
        val isDateToday = dateString == dateFormat.format(nowCal.time)

        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val endTime = calendar.timeInMillis

        val existingDaily = dailyUsageDao.getUsagesForDate(dateString).associateBy { it.packageName }
        val existingTotalVal = existingDaily["TOTAL"]?.usageTimeMillis ?: 0L

        if (!isDateToday && existingTotalVal > 0) {
            return Result.success()
        }

        val allShields = database.shieldDao().getAllShields().first()
        val finalAppUsages = mutableMapOf<String, Long>()
        
        val launcherApps = try {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (_: Exception) { emptySet() }
        val launcherPackage = try {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) { null }
        val excludePackages = setOfNotNull(applicationContext.packageName, launcherPackage)

        val timeSinceMidnight = if (isDateToday) (System.currentTimeMillis() - startTime).coerceAtLeast(0L) else (24 * 60 * 60 * 1000L)
        
        if (isDateToday) {
            val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm)
            detailedUsage.appUsageMap.forEach { (pkg, time) ->
                if (pkg !in excludePackages && pkg in launcherApps && time > 0) {
                    finalAppUsages[pkg] = time.coerceAtMost(timeSinceMidnight)
                }
            }
        } else {
            val stats = try {
                usm.queryAndAggregateUsageStats(startTime, endTime)
            } catch (e: Exception) {
                null
            }
            stats?.forEach { (pkg, stat) ->
                if (pkg !in excludePackages && pkg in launcherApps) {
                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                    if (time > 0) finalAppUsages[pkg] = time.coerceAtMost(timeSinceMidnight)
                }
            }
        }

        val events = try {
            usm.queryEvents(startTime - 1800000L, System.currentTimeMillis().coerceAtMost(endTime))
        } catch (e: Exception) {
            null
        }
        val event = android.app.usage.UsageEvents.Event()
        val androidTotals = mutableMapOf<String, Long>()
        var activePkg: String? = null
        var activeStartTime = 0L
        var isScreenOn = true

        while (events?.hasNextEvent() == true) {
            events.getNextEvent(event)
            val time = event.timeStamp
            when (event.eventType) {
                android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    activePkg?.let { pkg ->
                        val segmentStart = maxOf(activeStartTime, startTime)
                        val segmentEnd = minOf(time, endTime)
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > 1500) androidTotals[pkg] = (androidTotals[pkg] ?: 0L) + duration
                        }
                    }
                    activePkg = null
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (isScreenOn) {
                        activePkg?.let { pkg ->
                            val segmentStart = maxOf(activeStartTime, startTime)
                            val segmentEnd = minOf(time, endTime)
                            if (segmentStart < segmentEnd) {
                                val duration = segmentEnd - segmentStart
                                if (duration > 1500) androidTotals[pkg] = (androidTotals[pkg] ?: 0L) + duration
                            }
                        }
                        activePkg = event.packageName
                        activeStartTime = time
                    }
                }
                android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                    activePkg?.let { pkg ->
                        if (pkg == event.packageName) {
                            val segmentStart = maxOf(activeStartTime, startTime)
                            val segmentEnd = minOf(time, endTime)
                            if (segmentStart < segmentEnd) {
                                val duration = segmentEnd - segmentStart
                                if (duration > 1500) androidTotals[pkg] = (androidTotals[pkg] ?: 0L) + duration
                            }
                            activePkg = null
                        }
                    }
                }
            }
        }
        
        activePkg?.let { pkg ->
            val segmentStart = maxOf(activeStartTime, startTime)
            val segmentEnd = minOf(System.currentTimeMillis(), endTime)
            if (segmentStart < segmentEnd) {
                val duration = segmentEnd - segmentStart
                if (duration > 1500) androidTotals[pkg] = (androidTotals[pkg] ?: 0L) + duration
            }
        }

        androidTotals.forEach { (pkg, time) ->
            if (pkg !in excludePackages && pkg in launcherApps) {
                finalAppUsages[pkg] = maxOf(finalAppUsages[pkg] ?: 0L, time)
            }
        }

        if (isDateToday) {
            val todayStart = calendar.timeInMillis
            allShields.forEach { shield ->
                if (shield.lastUsedTimestamp >= todayStart) {
                    val usageFromShield = (shield.timeLimitMinutes * 60 * 1000L - shield.remainingTimeMillis).coerceAtLeast(0L)
                    if (usageFromShield > 0) {
                        finalAppUsages[shield.packageName] = maxOf(finalAppUsages[shield.packageName] ?: 0L, usageFromShield)
                    }
                }
            }
        }

        val userPrefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = userPrefsRepo.userPreferencesFlow.first()

        finalAppUsages.keys.forEach { pkg ->
            finalAppUsages[pkg] = (finalAppUsages[pkg] ?: 0L).coerceAtMost(timeSinceMidnight)
        }

        val calculatedSum = finalAppUsages.values.sum()

        var totalUsage = calculatedSum.coerceAtMost(timeSinceMidnight)

        val usagesToInsert = mutableListOf<DailyUsageEntity>()
        finalAppUsages.forEach { (pkg, time) ->
            usagesToInsert.add(DailyUsageEntity(date = dateString, packageName = pkg, usageTimeMillis = time.coerceAtMost(timeSinceMidnight)))
        }

        usagesToInsert.add(DailyUsageEntity(date = dateString, packageName = "TOTAL", usageTimeMillis = totalUsage))

        val shieldPkgs = allShields.asSequence().filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
        val goalPkgs = allShields.asSequence().filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()
        var sUsage = 0L; var gUsage = 0L
        finalAppUsages.forEach { (pkg, time) ->
            if (pkg in shieldPkgs) sUsage += time else if (pkg in goalPkgs) gUsage += time
        }
        
        usagesToInsert.add(DailyUsageEntity(date = dateString, packageName = "SHIELD_TOTAL", usageTimeMillis = sUsage))
        usagesToInsert.add(DailyUsageEntity(date = dateString, packageName = "GOAL_TOTAL", usageTimeMillis = gUsage))
        usagesToInsert.add(DailyUsageEntity(date = dateString, packageName = "OTHER_TOTAL", usageTimeMillis = (totalUsage - (sUsage + gUsage)).coerceAtLeast(0L)))

        dailyUsageDao.insertAll(usagesToInsert)

        if (!isBackup) {
            sendDataSavedNotification()
            dailyUsageDao.deleteOldUsage(dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -180) }.time))
        }

        return Result.success()
    }

    private fun sendDataSavedNotification() {
        val channelId = "zenith_usage_sync"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, "Usage Sync", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        manager.notify(999, NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Daily Report Prepared").setContentText("Your usage has been safely saved.")
            .setSmallIcon(R.drawable.ic_calendar).setPriority(NotificationCompat.PRIORITY_LOW).setAutoCancel(true).setGroup("zenith_system").build())
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 58); calendar.set(Calendar.SECOND, 0)
            if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("DailyUsageSyncWorker", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailyUsageWorker>(1, TimeUnit.DAYS).setInitialDelay(calendar.timeInMillis - now, TimeUnit.MILLISECONDS).setConstraints(constraints).setInputData(workDataOf("is_backup" to false)).build())
        }
    }
}
