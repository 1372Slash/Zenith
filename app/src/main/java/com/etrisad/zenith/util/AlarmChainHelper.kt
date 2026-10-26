package com.etrisad.zenith.util

import com.etrisad.zenith.data.model.AlarmItem
object AlarmChainHelper {
    fun alarmKey(alarmId: Long, alarmTime: String): String =
        if (alarmId > 0L) "id:$alarmId" else "time:$alarmTime"

    /**
     * Finds the alarm backing a firing intent. With a known id the match is by
     * id only (never by time, so two alarms sharing one HH:mm can't be
     * confused); with id = 0 it falls back to the time string.
     */
    fun findAlarm(alarms: List<AlarmItem>, alarmId: Long, alarmTime: String): AlarmItem? {
        if (alarmId > 0L) {
            return alarms.find { it.id == alarmId }
        }
        return alarms.find { it.timeString == alarmTime }
    }

    enum class UsageCheckOutcome {
        /** Same alarm still ringing on screen: keep waiting, re-check later. */
        KEEP_WAITING_SAME_ALARM,
        /** User idle since dismiss: ring again. */
        RE_TRIGGER,
        /** User demonstrably awake: stop the smart-repeat chain. */
        COMPLETE,
    }

    /**
     * Decides a smart-repeat usage check. The [awakeProvider] (a UsageStats
     * query) is only invoked when the outcome actually depends on it — never
     * when the same alarm is still on screen, and never for a different alarm
     * being mistaken for this one.
     */
    fun decideUsageCheck(
        isShowing: Boolean,
        showingKey: String?,
        checkKey: String,
        awakeProvider: () -> Boolean
    ): UsageCheckOutcome {
        if (isShowing && showingKey != null && showingKey == checkKey) {
            return UsageCheckOutcome.KEEP_WAITING_SAME_ALARM
        }
        return if (awakeProvider()) UsageCheckOutcome.COMPLETE
        else UsageCheckOutcome.RE_TRIGGER
    }

    /**
     * Adds one closed foreground window to the preserved wake-up offset.
     * Windows are always [start, end) slices, so a preempted alarm's saved
     * offset plus the new window never double-counts the same second.
     */
    fun mergeWakeUpOffset(savedOffsetMs: Long, windowMs: Long): Long =
        savedOffsetMs + windowMs.coerceAtLeast(0L)

    fun wakeUpTotalSeconds(offsetMs: Long, windowMs: Long): Int =
        ((offsetMs + windowMs.coerceAtLeast(0L)) / 1000).toInt()

    fun isWakeUpComplete(totalMs: Long, requiredSeconds: Int): Boolean =
        requiredSeconds > 0 && totalMs >= requiredSeconds * 1000L
}
