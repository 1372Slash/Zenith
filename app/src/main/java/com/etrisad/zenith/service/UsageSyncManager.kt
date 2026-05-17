package com.etrisad.zenith.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class UsageSyncManager(
    private val context: Context,
    private val repository: ShieldRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private data class UsageChunk(val packageName: String, var duration: Long)

    suspend fun syncUsageData() {
        val lastSyncTime = preferencesRepository.userPreferencesFlow.first().lastSyncTimestamp
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastSyncTime < 30000) return 

        val pm = context.packageManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Start query from 1 hour before lastSyncTime to catch the active session at that time
        val queryStart = maxOf(startOfToday, lastSyncTime - (60 * 60 * 1000L))
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

        var isScreenOn = powerManager.isInteractive

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            val type = event.eventType

            when (type) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
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
                    if (isScreenOn && pkg !in excludePackages && pkg in launcherApps) {
                        val className = event.className ?: ""
                        if (!className.contains("Notification", ignoreCase = true) &&
                            !className.contains("Toast", ignoreCase = true)) {
                            
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
            
            val nextHourStart = (cal.clone() as Calendar).apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val chunkEnd = minOf(end, nextHourStart)
            val duration = chunkEnd - current

            if (duration > 0) {
                buckets.getOrPut(dateStr) { mutableMapOf() }
                    .getOrPut(hour) { mutableListOf() }
                    .add(UsageChunk(pkg, duration))
            }
            current = chunkEnd
        }
    }

    private suspend fun saveBucketsToDatabase(
        buckets: MutableMap<String, MutableMap<Int, MutableList<UsageChunk>>>
    ) {
        val now = System.currentTimeMillis()
        val limit = 3600000L
        val finalEntities = mutableListOf<HourlyUsageEntity>()
        val carryOver = mutableListOf<UsageChunk>()
        val sortedDates = buckets.keys.sorted().toMutableList()
        
        var dateIdx = 0
        while (dateIdx < sortedDates.size || carryOver.isNotEmpty()) {
            val date = if (dateIdx < sortedDates.size) {
                sortedDates[dateIdx]
            } else {
                val lastDate = sortedDates.lastOrNull() ?: break
                val cal = Calendar.getInstance()
                cal.time = try { dateFormat.parse(lastDate) } catch (_: Exception) { null } ?: break
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val nextDate = dateFormat.format(cal.time)
                sortedDates.add(nextDate)
                nextDate
            }
            dateIdx++

            val existingRecords = repository.getHourlyUsageForDate(date).first()
            val dayBuckets = buckets[date] ?: mutableMapOf()

            for (hour in 0..23) {
                val newChunks = dayBuckets[hour] ?: mutableListOf()
                val combined = mutableListOf<UsageChunk>()
                combined.addAll(carryOver)
                carryOver.clear()
                combined.addAll(newChunks)

                val existingHourTotal = existingRecords
                    .filter { it.hour == hour && it.packageName != "TOTAL" }
                    .sumOf { it.usageTimeMillis }
                
                val currentNewTotal = combined.sumOf { it.duration }
                
                if (existingHourTotal + currentNewTotal > limit) {
                    var excess = (existingHourTotal + currentNewTotal) - limit
                    while (excess > 0 && combined.isNotEmpty()) {
                        val last = combined.last()
                        if (last.duration <= excess) {
                            excess -= last.duration
                            carryOver.add(0, combined.removeAt(combined.size - 1))
                        } else {
                            val move = excess
                            last.duration -= move
                            carryOver.add(0, UsageChunk(last.packageName, move))
                            excess = 0
                        }
                    }
                }

                val appsToSave = mutableMapOf<String, Long>()
                combined.forEach { 
                    appsToSave[it.packageName] = (appsToSave[it.packageName] ?: 0L) + it.duration 
                }

                var hourTotalIncrement = 0L
                appsToSave.forEach { (pkg, duration) ->
                    if (duration <= 0) return@forEach
                    val existing = existingRecords.find { it.hour == hour && it.packageName == pkg }
                    val totalDuration = (existing?.usageTimeMillis ?: 0L) + duration
                    hourTotalIncrement += duration

                    finalEntities.add(
                        HourlyUsageEntity(
                            id = existing?.id ?: 0,
                            date = date,
                            hour = hour,
                            packageName = pkg,
                            usageTimeMillis = totalDuration,
                            lastUpdated = now
                        )
                    )
                }

                if (hourTotalIncrement > 0) {
                    val existingTotalRec = existingRecords.find { it.hour == hour && it.packageName == "TOTAL" }
                    val newTotal = (existingTotalRec?.usageTimeMillis ?: 0L) + hourTotalIncrement
                    finalEntities.add(
                        HourlyUsageEntity(
                            id = existingTotalRec?.id ?: 0,
                            date = date,
                            hour = hour,
                            packageName = "TOTAL",
                            usageTimeMillis = newTotal,
                            lastUpdated = now
                        )
                    )
                }
            }
            carryOver.clear()
            if (dateIdx > 100) break 
        }

        if (finalEntities.isNotEmpty()) {
            repository.insertHourlyUsage(finalEntities)
        }
    }
}
