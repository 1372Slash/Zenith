package com.etrisad.zenith.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.util.Calendar

object ScreenUsageHelper {
    fun fetchAppUsageTodayTillNow(usageStatsManager: UsageStatsManager): Map<String, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        val end = System.currentTimeMillis()

        val usageMap = mutableMapOf<String, Long>()

        var activePkg: String? = null
        var activeStartTime: Long = 0L

        val events = usageStatsManager.queryEvents(start - 1800000L, end)
        val event = UsageEvents.Event()
        
        var isScreenOn = true 

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName
            val time = event.timeStamp
            
            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    isScreenOn = true
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    isScreenOn = false
                    if (activePkg != null) {
                        val segmentStart = maxOf(activeStartTime, start)
                        val segmentEnd = minOf(time, end)
                        if (segmentStart < segmentEnd) {
                            val duration = segmentEnd - segmentStart
                            if (duration > 1000) {
                                usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
                            }
                        }
                        activePkg = null
                        activeStartTime = 0L
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (isScreenOn) {
                        val className = event.className ?: ""
                        if (className.contains("Notification", ignoreCase = true) || 
                            className.contains("Toast", ignoreCase = true) ||
                            className.contains("ResolverActivity", ignoreCase = true)) {
                            continue
                        }

                        if (activePkg != null && activePkg != pkg) {
                            val segmentStart = maxOf(activeStartTime, start)
                            val segmentEnd = minOf(time, end)
                            if (segmentStart < segmentEnd) {
                                val duration = segmentEnd - segmentStart
                                if (duration > 1000) {
                                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
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
                            if (duration > 1000) {
                                usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
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
            val segmentEnd = end
            if (segmentStart < segmentEnd) {
                val duration = segmentEnd - segmentStart
                if (duration > 1000) {
                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + duration
                }
            }
        }

        return usageMap
    }
}
