package com.etrisad.zenith.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.etrisad.zenith.receiver.MidnightResetReceiver
import java.util.Calendar

object AlarmTasksSchedulingHelper {
    fun scheduleMidnightResetTask(context: Context, dayStartHour: Int = 0, dayStartMinute: Int = 0) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, dayStartHour)
            set(Calendar.MINUTE, dayStartMinute)
            set(Calendar.SECOND, 3)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, MidnightResetReceiver::class.java).apply {
            action = MidnightResetReceiver.ACTION_START_MIDNIGHT_RESET
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }
}
