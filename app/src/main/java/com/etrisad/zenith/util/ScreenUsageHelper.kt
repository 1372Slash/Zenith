package com.etrisad.zenith.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.SparseArray
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

    @Volatile
    private var lastResult: UsageResult? = null
    @Volatile
    private var lastQueryTime = 0L
    @Volatile
    var cacheDuration = 60000L
        private set

    fun updateCacheDuration(durationMs: Long) {
        cacheDuration = durationMs
    }
    private val refreshLock = Any()

    private const val MIDNIGHT_LOOKBACK_MS = 600000L
    private const val SESSION_MIN_DURATION = 4000L
    private const val MIN_SEGMENT_DURATION = 100L
    private const val SESSION_TIMEOUT_MS = 60000L

    private val accumulatedUsageMap = mutableMapOf<String, Long>()
    private val accumulatedHourlyMap = SparseArray<HashMap<String, Long>>()
    private val accumulatedSessionCounts = mutableMapOf<String, Int>()
    private var lastParsedTimestamp = 0L
    private var currentActivePkg: String? = null
    private var currentActiveStartTime = 0L
    private var currentIsScreenOn = true
    private var lastTotalGlobalUsage = 0L
    private var lastParsedDate: LocalDate? = null

    fun fetchDetailedUsageToday(
        usageStatsManager: UsageStatsManager,
        includeHourly: Boolean = false
    ): UsageResult {
        val currentTime = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()

        val cached = lastResult
        if (cached != null && currentTime - lastQueryTime < cacheDuration &&
            (!includeHourly || cached.hourlyUsageMap.isNotEmpty()) &&
            lastParsedDate == today) {
            return cached
        }

        synchronized(refreshLock) {
            val reCached = lastResult
            if (reCached != null && currentTime - lastQueryTime < cacheDuration &&
                (!includeHourly || reCached.hourlyUsageMap.isNotEmpty()) &&
                lastParsedDate == today) {
                return reCached
            }

            if (lastParsedDate != today || lastParsedTimestamp < todayStart - MIDNIGHT_LOOKBACK_MS) {
                clearIncrementalState()
                lastParsedDate = today
            }

            val startQuery = if (lastParsedTimestamp == 0L) todayStart - MIDNIGHT_LOOKBACK_MS else lastParsedTimestamp
            val endQuery = currentTime

            if (endQuery > startQuery) {
                val events = try {
                    usageStatsManager.queryEvents(startQuery, endQuery)
                } catch (e: Exception) {
                    null
                }

                val event = UsageEvents.Event()
                while (events?.hasNextEvent() == true) {
                    events.getNextEvent(event)
                    val pkg = event.packageName
                    val time = event.timeStamp.coerceAtMost(endQuery)
                    if (time < lastParsedTimestamp) continue

                    processEvent(event, pkg, time, todayStart, endQuery, zoneId, includeHourly)
                    lastParsedTimestamp = time
                }
                lastParsedTimestamp = maxOf(lastParsedTimestamp, endQuery)
            }

            val aggregatedStats = try {
                usageStatsManager.queryAndAggregateUsageStats(todayStart, endQuery)
            } catch (e: Exception) {
                null
            }
            val lastUsedMap = mutableMapOf<String, Long>()
            aggregatedStats?.forEach { (pkg, stats) ->
                lastUsedMap[pkg] = stats.lastTimeUsed
            }

            val result = buildResult(currentTime, todayStart, zoneId, includeHourly, lastUsedMap, aggregatedStats)
            lastResult = result
            lastQueryTime = currentTime
            return result
        }
    }

    private fun processEvent(
        event: UsageEvents.Event,
        pkg: String,
        time: Long,
        todayStart: Long,
        endQuery: Long,
        zoneId: ZoneId,
        includeHourly: Boolean
    ) {
        when (event.eventType) {
            UsageEvents.Event.SCREEN_INTERACTIVE -> currentIsScreenOn = true
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                currentIsScreenOn = false
                currentActivePkg?.let { p ->
                    commitSegment(p, currentActiveStartTime, time, todayStart, endQuery, zoneId, includeHourly)
                }
                currentActivePkg = null
                currentActiveStartTime = 0L
            }
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                if (currentIsScreenOn) {
                    val className = event.className
                    if (className != null && (className.contains("Notification") ||
                                className.contains("notification") ||
                                className.contains("Toast") ||
                                className.contains("toast"))) {
                        return
                    }

                    if (currentActivePkg != null) {
                        commitSegment(currentActivePkg!!, currentActiveStartTime, time, todayStart, endQuery, zoneId, includeHourly)
                    }
                    currentActivePkg = pkg
                    currentActiveStartTime = time
                }
            }
            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                if (currentActivePkg == pkg) {
                    commitSegment(pkg, currentActiveStartTime, time, todayStart, endQuery, zoneId, includeHourly)
                    currentActivePkg = null
                    currentActiveStartTime = 0L
                }
            }
        }
    }

    private fun commitSegment(
        pkg: String,
        startTime: Long,
        endTime: Long,
        todayStart: Long,
        endQuery: Long,
        zoneId: ZoneId,
        includeHourly: Boolean
    ) {
        val segmentStart = maxOf(startTime, todayStart)
        val segmentEnd = minOf(endTime, endQuery)
        if (segmentStart < segmentEnd) {
            val duration = segmentEnd - segmentStart
            if (duration > MIN_SEGMENT_DURATION) {
                accumulatedUsageMap[pkg] = (accumulatedUsageMap[pkg] ?: 0L) + duration
                lastTotalGlobalUsage += duration
                if (duration > SESSION_MIN_DURATION) {
                    accumulatedSessionCounts[pkg] = (accumulatedSessionCounts[pkg] ?: 0) + 1
                }
                if (includeHourly) {
                    var currentMillis = segmentStart
                    var currentZdt = Instant.ofEpochMilli(currentMillis).atZone(zoneId)
                    var nextHourZdt = currentZdt.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                    var nextHourMillis = nextHourZdt.toInstant().toEpochMilli()
                    while (currentMillis < segmentEnd) {
                        val hour = currentZdt.hour
                        val segEnd = if (nextHourMillis < segmentEnd) nextHourMillis else segmentEnd
                        val duration = segEnd - currentMillis
                        if (duration > 0) {
                            var pkgMap = accumulatedHourlyMap.get(hour)
                            if (pkgMap == null) {
                                pkgMap = HashMap()
                                accumulatedHourlyMap.put(hour, pkgMap)
                            }
                            pkgMap[pkg] = (pkgMap[pkg] ?: 0L) + duration
                        }
                        currentMillis = segEnd
                        if (currentMillis < segmentEnd) {
                            currentZdt = nextHourZdt
                            nextHourZdt = currentZdt.plusHours(1)
                            nextHourMillis = nextHourZdt.toInstant().toEpochMilli()
                        }
                    }
                }
            }
        }
    }

    private fun buildResult(
        currentTime: Long,
        todayStart: Long,
        zoneId: ZoneId,
        includeHourly: Boolean,
        lastUsedMap: Map<String, Long>,
        aggregatedStats: Map<String, android.app.usage.UsageStats>?
    ): UsageResult {
        val finalUsageMap = accumulatedUsageMap.toMutableMap()
        val finalSessionCounts = accumulatedSessionCounts.toMutableMap()
        var finalTotalGlobalUsage = lastTotalGlobalUsage

        if (currentIsScreenOn && currentActivePkg != null) {
            val p = currentActivePkg!!
            val segmentStart = maxOf(currentActiveStartTime, todayStart)
            var segmentEnd = currentTime

            val stats = aggregatedStats?.get(p)
            if (stats != null) {
                val lastActivity = if (stats.lastTimeUsed > stats.lastTimeVisible) stats.lastTimeUsed else stats.lastTimeVisible
                if (lastActivity in (segmentStart + 1) until segmentEnd && (currentTime - lastActivity) > SESSION_TIMEOUT_MS) {
                    segmentEnd = lastActivity
                }
            }

            if (segmentStart < segmentEnd) {
                val duration = segmentEnd - segmentStart
                if (duration > MIN_SEGMENT_DURATION) {
                    finalUsageMap[p] = (finalUsageMap[p] ?: 0L) + duration
                    finalTotalGlobalUsage += duration
                    if (duration > SESSION_MIN_DURATION) {
                        finalSessionCounts[p] = (finalSessionCounts[p] ?: 0) + 1
                    }
                }
            }
        }

        val resultHourlyMap = if (includeHourly) {
            val hMap = mutableMapOf<Int, MutableMap<String, Long>>()
            for (i in 0 until accumulatedHourlyMap.size()) {
                val h = accumulatedHourlyMap.keyAt(i)
                hMap[h] = accumulatedHourlyMap.valueAt(i).toMutableMap()
            }
            if (currentIsScreenOn && currentActivePkg != null) {
                val p = currentActivePkg!!
                val segmentStart = maxOf(currentActiveStartTime, todayStart)
                var segmentEnd = currentTime
                val stats = aggregatedStats?.get(p)
                if (stats != null) {
                    val lastActivity = if (stats.lastTimeUsed > stats.lastTimeVisible) stats.lastTimeUsed else stats.lastTimeVisible
                    if (lastActivity in (segmentStart + 1) until segmentEnd && (currentTime - lastActivity) > SESSION_TIMEOUT_MS) {
                        segmentEnd = lastActivity
                    }
                }
                if (segmentStart < segmentEnd) {
                    addHourlyUsage(hMap, p, segmentStart, segmentEnd, zoneId)
                }
            }
            hMap
        } else emptyMap<Int, Map<String, Long>>()

        return UsageResult(finalUsageMap, resultHourlyMap, finalSessionCounts, lastUsedMap, finalTotalGlobalUsage)
    }

    private fun clearIncrementalState() {
        accumulatedUsageMap.clear()
        accumulatedHourlyMap.clear()
        accumulatedSessionCounts.clear()
        lastParsedTimestamp = 0L
        currentActivePkg = null
        currentActiveStartTime = 0L
        currentIsScreenOn = true
        lastTotalGlobalUsage = 0L
        lastResult = null
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
                val pkgMap = hourlyMap.getOrPut(hour) { hashMapOf() }
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
                            val pkg = activePkg
                            val segmentStart = maxOf(activeStartTime, startTime)
                            val segmentEnd = minOf(time, currentTime)
                            if (segmentStart < segmentEnd) {
                                usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
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

    @Synchronized
    fun clearCache() {
        clearIncrementalState()
    }
}