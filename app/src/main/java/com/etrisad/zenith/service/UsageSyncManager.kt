package com.etrisad.zenith.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

class UsageSyncManager(
    private val context: Context,
    private val repository: ShieldRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    companion object {
        private val syncMutex = Mutex()
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private data class UsageChunk(val packageName: String, var duration: Long)

    suspend fun syncUsageData() = syncMutex.withLock {
        val lastSyncTime = preferencesRepository.userPreferencesFlow.first().lastSyncTimestamp
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastSyncTime < 30000) return@withLock

        val pm = context.packageManager
        
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val queryStart = maxOf(startOfToday, lastSyncTime - (24 * 60 * 60 * 1000L))
        val events = usageStatsManager.queryEvents(queryStart, currentTime)
        val event = UsageEvents.Event()
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val activeSessions = mutableMapOf<String, Long>()
        val hourlyBuckets = mutableMapOf<String, MutableMap<Int, MutableList<UsageChunk>>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            val type = event.eventType

            when (type) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    activeSessions.forEach { (p, start) ->
                        val segmentStart = maxOf(start, lastSyncTime)
                        val segmentEnd = minOf(time, currentTime)
                        if (segmentStart < segmentEnd) {
                            processSession(p, segmentStart, segmentEnd, hourlyBuckets)
                        }
                    }
                    activeSessions.clear()
                }
                UsageEvents.Event.MOVE_TO_FOREGROUND, UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (pkg in excludePackages || pkg !in launcherApps) {
                        activeSessions.forEach { (p, start) ->
                            val segmentStart = maxOf(start, lastSyncTime)
                            val segmentEnd = minOf(time, currentTime)
                            if (segmentStart < segmentEnd) {
                                processSession(p, segmentStart, segmentEnd, hourlyBuckets)
                            }
                        }
                        activeSessions.clear()
                    } else {
                        val className = event.className ?: ""
                        if (!className.contains("Notification", ignoreCase = true) &&
                            !className.contains("Toast", ignoreCase = true)) {

                            activeSessions.keys.filter { it != pkg }.forEach { p ->
                                val start = activeSessions.remove(p) ?: return@forEach
                                val segmentStart = maxOf(start, lastSyncTime)
                                val segmentEnd = minOf(time, currentTime)
                                if (segmentStart < segmentEnd) {
                                    processSession(p, segmentStart, segmentEnd, hourlyBuckets)
                                }
                            }

                            val previousStart = activeSessions[pkg]
                            if (previousStart != null) {
                                val segmentStart = maxOf(previousStart, lastSyncTime)
                                val segmentEnd = minOf(time, currentTime)
                                if (segmentStart < segmentEnd) {
                                    processSession(pkg, segmentStart, segmentEnd, hourlyBuckets)
                                }
                            }
                            activeSessions[pkg] = time
                        }
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND, UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val startTime = activeSessions.remove(pkg) ?: continue
                    val segmentStart = maxOf(startTime, lastSyncTime)
                    val segmentEnd = minOf(time, currentTime)
                    if (segmentStart < segmentEnd) {
                        processSession(pkg, segmentStart, segmentEnd, hourlyBuckets)
                    }
                }
            }
        }

        activeSessions.forEach { (pkg, startTime) ->
            val segmentStart = maxOf(startTime, lastSyncTime)
            val segmentEnd = currentTime
            if (segmentStart < segmentEnd) {
                processSession(pkg, segmentStart, segmentEnd, hourlyBuckets)
            }
        }

        saveBucketsToDatabase(hourlyBuckets)
        preferencesRepository.setLastSyncTimestamp(currentTime)
    }

    private fun processSession(
        pkg: String,
        start: Long,
        end: Long,
        buckets: MutableMap<String, MutableMap<Int, MutableList<UsageChunk>>>
    ) {
        val cal = Calendar.getInstance()
        var current = start

        while (current < end) {
            cal.timeInMillis = current
            val dateStr = dateFormat.format(cal.time)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            
            val nextHourStartCal = (cal.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nextHourStart = nextHourStartCal.timeInMillis

            val chunkEnd = minOf(end, nextHourStart)
            val duration = chunkEnd - current

            if (duration > 0) {
                buckets.getOrPut(dateStr) { mutableMapOf() }
                    .getOrPut(hour) { mutableListOf() }
                    .add(UsageChunk(pkg, duration))
            }
            
            if (chunkEnd <= current) break
            current = chunkEnd
        }
    }

    private suspend fun saveBucketsToDatabase(
        buckets: MutableMap<String, MutableMap<Int, MutableList<UsageChunk>>>
    ) {
        val now = System.currentTimeMillis()
        val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
        val currentHour = calendarNow.get(Calendar.HOUR_OF_DAY)
        val currentDateStr = dateFormat.format(calendarNow.time)
        
        val limit = 3600000L
        val finalEntities = mutableListOf<HourlyUsageEntity>()
        val carryOver = mutableListOf<UsageChunk>()
        
        val sortedDates = buckets.keys.sorted().toMutableList()
        if (sortedDates.isEmpty() && carryOver.isEmpty()) return
        
        var dateIdx = 0
        while (dateIdx < sortedDates.size || (carryOver.isNotEmpty() && dateIdx < 3)) {
            val date = if (dateIdx < sortedDates.size) {
                sortedDates[dateIdx]
            } else {
                val lastDate = sortedDates.lastOrNull() ?: break
                val cal = Calendar.getInstance()
                cal.time = try { dateFormat.parse(lastDate) } catch (_: Exception) { null } ?: break
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val nextDate = dateFormat.format(cal.time)
                
                if (nextDate > currentDateStr) {
                    carryOver.clear()
                    break
                }
                
                sortedDates.add(nextDate)
                nextDate
            }
            dateIdx++

            val existingRecords = repository.getHourlyUsageForDateSync(date)
            val existingMap = existingRecords.associateBy { it.hour to it.packageName }
            val dayBuckets = buckets[date] ?: mutableMapOf()

            for (hour in 0..23) {
                if (date == currentDateStr && hour > currentHour) {
                    carryOver.clear()
                    break
                }

                val newChunks = dayBuckets[hour] ?: mutableListOf()
                val isPastHour = date < currentDateStr || (date == currentDateStr && hour < currentHour)

                if (newChunks.isEmpty() && carryOver.isEmpty()) {
                    continue
                }

                val combined = mutableListOf<UsageChunk>()
                combined.addAll(carryOver)
                carryOver.clear()
                combined.addAll(newChunks)

                val existingHourRecords = existingRecords.filter { it.hour == hour && it.packageName != "TOTAL" }
                val currentHourAppState = existingHourRecords.associate { it.packageName to it.usageTimeMillis }.toMutableMap()
                
                combined.forEach { chunk ->
                    currentHourAppState[chunk.packageName] = (currentHourAppState[chunk.packageName] ?: 0L) + chunk.duration
                }

                val totalInHour = currentHourAppState.values.sum()

                if (isPastHour && totalInHour > limit) {
                    var excess = totalInHour - limit
                    val sortedEntries = currentHourAppState.entries.sortedByDescending { it.value }
                    
                    for (entry in sortedEntries) {
                        if (excess <= 0) break
                        val pkg = entry.key
                        val currentValue = currentHourAppState[pkg] ?: 0L
                        val toMove = minOf(currentValue, excess)
                        
                        currentHourAppState[pkg] = currentValue - toMove
                        if (toMove > 0) {
                            carryOver.add(UsageChunk(pkg, toMove))
                        }
                        excess -= toMove
                    }
                }

                var finalHourTotal = 0L
                currentHourAppState.forEach { (pkg, duration) ->
                    val existing = existingMap[hour to pkg]
                    if (existing == null || existing.usageTimeMillis != duration) {
                        finalEntities.add(
                            HourlyUsageEntity(
                                id = existing?.id ?: 0,
                                date = date,
                                hour = hour,
                                packageName = pkg,
                                usageTimeMillis = duration,
                                lastUpdated = now
                            )
                        )
                    }
                    finalHourTotal += duration
                }

                val finalTotalCapped = minOf(finalHourTotal, limit)
                val existingTotalRec = existingMap[hour to "TOTAL"]
                if (existingTotalRec == null || existingTotalRec.usageTimeMillis != finalTotalCapped) {
                    finalEntities.add(
                        HourlyUsageEntity(
                            id = existingTotalRec?.id ?: 0,
                            date = date,
                            hour = hour,
                            packageName = "TOTAL",
                            usageTimeMillis = finalTotalCapped,
                            lastUpdated = now
                        )
                    )
                }
            }
            if (dateIdx > 50) break
        }

        if (finalEntities.isNotEmpty()) {
            repository.insertHourlyUsage(finalEntities)
        }

        syncDailyFromHourly(buckets.keys)
        preferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())
    }

    private suspend fun syncDailyFromHourly(dates: Set<String>) {
        val now = System.currentTimeMillis()
        val dailyEntities = mutableListOf<com.etrisad.zenith.data.local.entity.DailyUsageEntity>()

        val allShields = repository.allShields.first()
        val shieldPkgs = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }.map { it.packageName }.toSet()
        val goalPkgs = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }.map { it.packageName }.toSet()

        dates.forEach { date ->
            val hourlyData = repository.getHourlyUsageForDateSync(date)
            if (hourlyData.isEmpty()) return@forEach

            val appTotals = hourlyData.filter { it.packageName != "TOTAL" }
                .groupBy { it.packageName }
                .mapValues { it.value.sumOf { h -> h.usageTimeMillis } }

            val prefs = preferencesRepository.userPreferencesFlow.first()
            val todayDateStr = dateFormat.format(Date(now))
            var totalTime = appTotals.values.sum()

            if (date == todayDateStr) {
                val lastKnownDailyUsage = prefs.lastKnownDailyUsage
                val lastKnownDailyUsageDate = prefs.lastKnownDailyUsageDate
                if (lastKnownDailyUsageDate == todayDateStr && lastKnownDailyUsage > totalTime) {
                    totalTime = lastKnownDailyUsage
                }
            }

            var shieldTime = 0L
            var goalTime = 0L

            appTotals.forEach { (pkg, time) ->
                dailyEntities.add(
                    com.etrisad.zenith.data.local.entity.DailyUsageEntity(
                        date = date,
                        packageName = pkg,
                        usageTimeMillis = time,
                        lastUpdated = now
                    )
                )
                if (pkg in shieldPkgs) shieldTime += time
                else if (pkg in goalPkgs) goalTime += time
            }

            dailyEntities.add(com.etrisad.zenith.data.local.entity.DailyUsageEntity(date = date, packageName = "TOTAL", usageTimeMillis = totalTime, lastUpdated = now))
            dailyEntities.add(com.etrisad.zenith.data.local.entity.DailyUsageEntity(date = date, packageName = "SHIELD_TOTAL", usageTimeMillis = shieldTime, lastUpdated = now))
            dailyEntities.add(com.etrisad.zenith.data.local.entity.DailyUsageEntity(date = date, packageName = "GOAL_TOTAL", usageTimeMillis = goalTime, lastUpdated = now))
            dailyEntities.add(com.etrisad.zenith.data.local.entity.DailyUsageEntity(date = date, packageName = "OTHER_TOTAL", usageTimeMillis = (totalTime - (shieldTime + goalTime)).coerceAtLeast(0L), lastUpdated = now))
        }

        if (dailyEntities.isNotEmpty()) {
            dailyEntities.forEach { repository.insertDailyUsage(it) }
        }
    }

}
