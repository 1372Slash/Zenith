package com.etrisad.zenith.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ScreenUsageHelper {
    data class UsageResult(
        val appUsageMap: Map<String, Long>,
        val hourlyUsageMap: Map<Int, Map<String, Long>>,
        val sessionCounts: Map<String, Int>,
        val lastUsedMap: Map<String, Long> = emptyMap(),
        val totalGlobalUsage: Long = 0L
    )

    private var lastResult: UsageResult? = null
    private var lastQueryTime = 0L
    private const val CACHE_DURATION = 5000L

    private const val MIDNIGHT_LOOKBACK_MS = 1800000L
    private const val SESSION_MIN_DURATION = 4000L
    private const val MIN_SEGMENT_DURATION = 100L
    private const val SESSION_TIMEOUT_MS = 60000L

    fun fetchDetailedUsageToday(
        usageStatsManager: UsageStatsManager,
        includeHourly: Boolean = false
    ): UsageResult {
        val currentTime = System.currentTimeMillis()
        val cached = lastResult
        if (cached != null && currentTime - lastQueryTime < CACHE_DURATION &&
            (!includeHourly || cached.hourlyUsageMap.isNotEmpty())) {
            return cached
        }

        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = currentTime

        val usageMap = mutableMapOf<String, Long>()
        val hourlyMap = mutableMapOf<Int, MutableMap<String, Long>>()
        val sessionCounts = mutableMapOf<String, Int>()
        val lastUsedMap = mutableMapOf<String, Long>()

        val aggregatedStats = try {
            usageStatsManager.queryAndAggregateUsageStats(start, end)
        } catch (e: Exception) {
            null
        }
        aggregatedStats?.forEach { (pkg, stats) ->
            lastUsedMap[pkg] = stats.lastTimeUsed
        }

        var activePkg: String? = null
        var activeStartTime = 0L

        var isScreenOn = false
        // Pre-scan to find the last screen state before the main query range
        try {
            val preEvents = usageStatsManager.queryEvents(start - 12 * 3600000L, start - MIDNIGHT_LOOKBACK_MS)
            val tempEvent = UsageEvents.Event()
            while (preEvents?.hasNextEvent() == true) {
                preEvents.getNextEvent(tempEvent)
                if (tempEvent.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) isScreenOn = true
                else if (tempEvent.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE) isScreenOn = false
            }
        } catch (_: Exception) {}

        val events = try {
            usageStatsManager.queryEvents(start - MIDNIGHT_LOOKBACK_MS, end)
        } catch (e: Exception) {
            null
        }
        val event = UsageEvents.Event()

        var totalSequentialUsage = 0L

        while (events?.hasNextEvent() == true) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp.coerceAtMost(end)

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    activePkg?.let { p ->
                        val segmentStart = maxOf(activeStartTime, start)
                        val segmentEnd = minOf(time, end)
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > MIN_SEGMENT_DURATION) {
                                usageMap[p] = (usageMap[p] ?: 0L) + duration
                                totalSequentialUsage += duration
                                if (duration > SESSION_MIN_DURATION) {
                                    sessionCounts[p] = (sessionCounts[p] ?: 0) + 1
                                }

                                if (includeHourly) {
                                    addHourlyUsage(hourlyMap, p, segmentStart, segmentEnd, zoneId)
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
                        val className = event.className
                        if (className != null && (className.contains("Notification") ||
                                    className.contains("notification") ||
                                    className.contains("Toast") ||
                                    className.contains("toast"))) {
                            continue
                        }

                        if (activePkg != null) {
                            val segmentStart = if (activeStartTime > start) activeStartTime else start
                            val segmentEnd = if (time < end) time else end
                            if (segmentStart < segmentEnd) {
                                val duration = segmentEnd - segmentStart
                                if (duration > 0) {
                                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
                                    if (duration > SESSION_MIN_DURATION) {
                                        sessionCounts[activePkg!!] = (sessionCounts[activePkg!!] ?: 0) + 1
                                    }
                                    if (includeHourly) {
                                        addHourlyUsage(hourlyMap, activePkg!!, segmentStart, segmentEnd, zoneId)
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
                        val segmentStart = if (activeStartTime > start) activeStartTime else start
                        val segmentEnd = if (time < end) time else end
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > MIN_SEGMENT_DURATION) {
                                usageMap[pkg] = (usageMap[pkg] ?: 0L) + duration
                                if (duration > SESSION_MIN_DURATION) {
                                    sessionCounts[pkg] = (sessionCounts[pkg] ?: 0) + 1
                                }
                                if (includeHourly) {
                                    addHourlyUsage(hourlyMap, pkg, segmentStart, segmentEnd, zoneId)
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
            val segmentStart = if (activeStartTime > start) activeStartTime else start
            var segmentEnd = end

            val stats = aggregatedStats?.get(activePkg)
            if (stats != null) {
                val lastActivity = if (stats.lastTimeUsed > stats.lastTimeVisible) stats.lastTimeUsed else stats.lastTimeVisible
                if (lastActivity in (segmentStart + 1) until segmentEnd && (currentTime - lastActivity) > SESSION_TIMEOUT_MS) {
                    segmentEnd = lastActivity
                }
            }

            if (segmentStart < segmentEnd) {
                val duration = segmentEnd - segmentStart
                if (duration > MIN_SEGMENT_DURATION) {
                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
                    if (duration > SESSION_MIN_DURATION) {
                        sessionCounts[activePkg!!] = (sessionCounts[activePkg!!] ?: 0) + 1
                    }
                    if (includeHourly) {
                        addHourlyUsage(hourlyMap, activePkg!!, segmentStart, segmentEnd, zoneId)
                    }
                }
            }
        }

        val result = UsageResult(usageMap, hourlyMap, sessionCounts, lastUsedMap, totalSequentialUsage)
        lastResult = result
        lastQueryTime = currentTime
        return result
    }

    private fun addHourlyUsage(
        hourlyMap: MutableMap<Int, MutableMap<String, Long>>,
        packageName: String,
        start: Long,
        end: Long,
        zoneId: ZoneId
    ) {
        var currentMillis = start
        var currentZdt = Instant.ofEpochMilli(currentMillis).atZone(zoneId)

        var nextHourZdt = currentZdt.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        var nextHourMillis = nextHourZdt.toInstant().toEpochMilli()

        while (currentMillis < end) {
            val hour = currentZdt.hour
            val segmentEnd = if (nextHourMillis < end) nextHourMillis else end
            val duration = segmentEnd - currentMillis

            if (duration > 0) {
                val pkgMap = hourlyMap.getOrPut(hour) { mutableMapOf() }
                pkgMap[packageName] = (pkgMap[packageName] ?: 0L) + duration
            }

            currentMillis = segmentEnd
            if (currentMillis < end) {
                currentZdt = nextHourZdt
                nextHourZdt = currentZdt.plusHours(1)
                nextHourMillis = nextHourZdt.toInstant().toEpochMilli()
            }
        }
    }

    fun fetchAppUsageTodayTillNow(usageStatsManager: UsageStatsManager): Map<String, Long> {
        return fetchDetailedUsageToday(usageStatsManager).appUsageMap
    }

    fun fetchAppUsageSince(
        usageStatsManager: UsageStatsManager,
        startTime: Long
    ): Map<String, Long> {
        val currentTime = System.currentTimeMillis()
        if (startTime >= currentTime) return emptyMap()

        val usageMap = mutableMapOf<String, Long>()
        var activePkg: String? = null
        var activeStartTime = 0L
        var isScreenOn = false

        val events = try {
            usageStatsManager.queryEvents(startTime - 300000L, currentTime)
        } catch (e: Exception) {
            null
        }
        val event = UsageEvents.Event()

        while (events?.hasNextEvent() == true) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    activePkg?.let { p ->
                        val segmentStart = maxOf(activeStartTime, startTime)
                        val segmentEnd = minOf(time, currentTime)
                        if (segmentStart < segmentEnd) {
                            usageMap[p] = (usageMap[p] ?: 0L) + (segmentEnd - segmentStart)
                        }
                    }
                    activePkg = null
                    activeStartTime = 0L
                }
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (isScreenOn) {
                        if (activePkg != null) {
                            val segmentStart = maxOf(activeStartTime, startTime)
                            val segmentEnd = minOf(time, currentTime)
                            if (segmentStart < segmentEnd) {
                                usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
                            }
                        }
                        activePkg = pkg
                        activeStartTime = time
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (activePkg == pkg) {
                        val segmentStart = maxOf(activeStartTime, startTime)
                        val segmentEnd = minOf(time, currentTime)
                        if (segmentStart < segmentEnd) {
                            usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
                        }
                        activePkg = null
                        activeStartTime = 0L
                    }
                }
            }
        }

        if (isScreenOn && activePkg != null) {
            val segmentStart = maxOf(activeStartTime, startTime)
            val segmentEnd = currentTime
            if (segmentStart < segmentEnd) {
                usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
            }
        }

        return usageMap
    }

    fun clearCache() {
        lastResult = null
        lastQueryTime = 0L
    }
}