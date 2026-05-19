package com.etrisad.zenith.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import java.util.Calendar

object ScreenUsageHelper {
    data class UsageResult(
        val appUsageMap: Map<String, Long>,
        val hourlyUsageMap: Map<Int, Map<String, Long>>,
        val sessionCounts: Map<String, Int>,
        val lastUsedMap: Map<String, Long> = emptyMap()
    )

    private var lastResult: UsageResult? = null
    private var lastQueryTime = 0L
    private const val CACHE_DURATION = 3000L

    fun fetchDetailedUsageToday(
        usageStatsManager: UsageStatsManager,
        includeHourly: Boolean = false
    ): UsageResult {
        val currentTime = System.currentTimeMillis()
        if (lastResult != null && currentTime - lastQueryTime < CACHE_DURATION && (!includeHourly || lastResult!!.hourlyUsageMap.isNotEmpty())) {
            return lastResult!!
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        val end = currentTime

        val usageMap = mutableMapOf<String, Long>()
        val hourlyMap = mutableMapOf<Int, MutableMap<String, Long>>()
        val sessionCounts = mutableMapOf<String, Int>()
        val lastUsedMap = mutableMapOf<String, Long>()

        val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(start, end)
        aggregatedStats?.forEach { (pkg, stats) ->
            if (stats.totalTimeInForeground > 0) {
                usageMap[pkg] = stats.totalTimeInForeground
                lastUsedMap[pkg] = stats.lastTimeUsed
            }
        }

        var activePkg: String? = null
        var activeStartTime = 0L

        val events = usageStatsManager.queryEvents(start - (24 * 60 * 60 * 1000L), end)
        val event = UsageEvents.Event()
        
        var isScreenOn = true 
        val cal = Calendar.getInstance()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    activePkg?.let { p ->
                        val segmentStart = maxOf(activeStartTime, start)
                        val segmentEnd = minOf(time, end)
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > 100) {
                                if (duration > 4000) {
                                    sessionCounts[p] = (sessionCounts[p] ?: 0) + 1
                                }

                                if (includeHourly) {
                                    addHourlyUsage(hourlyMap, p, segmentStart, segmentEnd, cal)
                                }
                            }
                        }
                    }
                    activePkg = null
                    activeStartTime = 0L
                }
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (isScreenOn) {
                        val className = event.className ?: ""
                        if (className.contains("Notification", ignoreCase = true) || 
                            className.contains("Toast", ignoreCase = true)) {
                            continue
                        }

                        if (activePkg != null) {
                            val segmentStart = maxOf(activeStartTime, start)
                            val segmentEnd = minOf(time, end)
                            if (segmentStart < segmentEnd) {
                                val duration = segmentEnd - segmentStart
                                if (duration > 0) {
                                    if (duration > 4000) {
                                        sessionCounts[activePkg!!] = (sessionCounts[activePkg!!] ?: 0) + 1
                                    }
                                    if (includeHourly) {
                                        addHourlyUsage(hourlyMap, activePkg!!, segmentStart, segmentEnd, cal)
                                    }
                                }
                            }
                        }
                        activePkg = pkg
                        activeStartTime = time
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (activePkg == pkg) {
                        val segmentStart = maxOf(activeStartTime, start)
                        val segmentEnd = minOf(time, end)
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > 100) {
                                if (duration > 4000) {
                                    sessionCounts[pkg] = (sessionCounts[pkg] ?: 0) + 1
                                }
                                if (includeHourly) {
                                    addHourlyUsage(hourlyMap, pkg, segmentStart, segmentEnd, cal)
                                }
                            }
                        }
                        activePkg = null
                        activeStartTime = 0L
                    }
                }
            }
        }
        
        if (isScreenOn && activePkg != null) {
            val segmentStart = maxOf(activeStartTime, start)
            var segmentEnd = end
            
            val stats = aggregatedStats?.get(activePkg)
            if (stats != null) {
                val lastActivity = maxOf(stats.lastTimeUsed, stats.lastTimeVisible)
                
                if (lastActivity in segmentStart until segmentEnd && (currentTime - lastActivity) > 60000) {
                    segmentEnd = lastActivity
                }
            }

            if (segmentStart < segmentEnd) {
                val duration = segmentEnd - segmentStart
                if (duration > 100) {
                    if (duration > 4000) {
                        sessionCounts[activePkg!!] = (sessionCounts[activePkg!!] ?: 0) + 1
                    }
                    if (includeHourly) {
                        addHourlyUsage(hourlyMap, activePkg!!, segmentStart, segmentEnd, cal)
                    }
                }
            }
        }

        if (isScreenOn && activePkg != null) {
            val totalInAggr = usageMap[activePkg!!] ?: 0L
            val currentSessionExtra = (end - maxOf(activeStartTime, start)).coerceAtLeast(0L)
            if (currentSessionExtra > 0) {
                usageMap[activePkg!!] = maxOf(totalInAggr, currentSessionExtra)
            }
        }

        val result = UsageResult(usageMap, hourlyMap, sessionCounts, lastUsedMap)
        lastResult = result
        lastQueryTime = currentTime
        return result
    }

    private fun addHourlyUsage(
        hourlyMap: MutableMap<Int, MutableMap<String, Long>>,
        packageName: String,
        start: Long,
        end: Long,
        cal: Calendar
    ) {
        var current = start
        while (current < end) {
            cal.timeInMillis = current
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val nextHourStart = cal.timeInMillis
            
            val segmentEnd = minOf(end, nextHourStart)
            val duration = segmentEnd - current
            if (duration > 0) {
                val pkgMap = hourlyMap.getOrPut(hour) { mutableMapOf() }
                pkgMap[packageName] = (pkgMap[packageName] ?: 0L) + duration
            }
            current = segmentEnd
        }
    }

    fun fetchAppUsageTodayTillNow(usageStatsManager: UsageStatsManager): Map<String, Long> {
        return fetchDetailedUsageToday(usageStatsManager).appUsageMap
    }
}
