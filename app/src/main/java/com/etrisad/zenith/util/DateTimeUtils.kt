package com.etrisad.zenith.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private val dateFormatTL = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    fun getDateFormat(): SimpleDateFormat = dateFormatTL.get()!!

    fun getDayStartTime(
        now: Long = System.currentTimeMillis(),
        dayStartHour: Int = 0,
        dayStartMinute: Int = 0
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, dayStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayDayStart = cal.timeInMillis
        return if (now >= todayDayStart) todayDayStart
        else {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cal.timeInMillis
        }
    }

    fun getDayStartDateString(
        now: Long = System.currentTimeMillis(),
        dayStartHour: Int = 0,
        dayStartMinute: Int = 0
    ): String {
        val dayStart = getDayStartTime(now, dayStartHour, dayStartMinute)
        return getDateFormat().format(Date(dayStart))
    }

    fun isBeforeDayStart(
        now: Long = System.currentTimeMillis(),
        dayStartHour: Int = 0,
        dayStartMinute: Int = 0
    ): Boolean {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, dayStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return now < cal.timeInMillis
    }

    fun getDayStartForDate(
        dateMillis: Long,
        dayStartHour: Int = 0,
        dayStartMinute: Int = 0
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, dayStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getOffsetsFromDayStart(
        now: Long = System.currentTimeMillis(),
        dayStartHour: Int = 0,
        dayStartMinute: Int = 0
    ): Long {
        val dayStart = getDayStartTime(now, dayStartHour, dayStartMinute)
        return (now - dayStart).coerceAtLeast(0L)
    }
}
