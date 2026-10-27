package com.etrisad.zenith.util

import com.etrisad.zenith.data.model.AlarmItem
object AlarmChainHelper {
    fun alarmKey(alarmId: Long, alarmTime: String): String =
        if (alarmId > 0L) "id:$alarmId" else "time:$alarmTime"
    fun findAlarm(alarms: List<AlarmItem>, alarmId: Long, alarmTime: String): AlarmItem? {
        if (alarmId > 0L) {
            return alarms.find { it.id == alarmId }
        }
        return alarms.find { it.timeString == alarmTime }
    }

    enum class UsageCheckOutcome {
        KEEP_WAITING_SAME_ALARM,
        RE_TRIGGER,
        COMPLETE,
    }
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
    fun mergeWakeUpOffset(savedOffsetMs: Long, windowMs: Long): Long =
        savedOffsetMs + windowMs.coerceAtLeast(0L)

    fun wakeUpTotalSeconds(offsetMs: Long, windowMs: Long): Int =
        ((offsetMs + windowMs.coerceAtLeast(0L)) / 1000).toInt()

    fun isWakeUpComplete(totalMs: Long, requiredSeconds: Int): Boolean =
        requiredSeconds > 0 && totalMs >= requiredSeconds * 1000L
}
