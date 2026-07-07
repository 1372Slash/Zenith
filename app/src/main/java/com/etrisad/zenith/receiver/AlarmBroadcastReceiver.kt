package com.etrisad.zenith.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.etrisad.zenith.service.AlarmOverlayActivity
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_ALARM -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                AlarmOverlayActivity.start(context, alarmTime)
            }
            ACTION_CHECK_USAGE -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                if (!hasRecentUsage(context)) {
                    scheduleReTrigger(context, alarmTime)
                }
            }
            ACTION_RE_TRIGGER -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                AlarmOverlayActivity.start(context, alarmTime)
            }
        }
    }

    private fun hasRecentUsage(context: Context): Boolean {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val oneMinuteAgo = now - 60_000L
            val events = usm.queryEvents(oneMinuteAgo, now)
            var hasEvent = false
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    hasEvent = true
                    break
                }
            }
            return hasEvent
        } catch (_: Exception) {
            return false
        }
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.etrisad.zenith.action.FIRE_ALARM"
        const val ACTION_CHECK_USAGE = "com.etrisad.zenith.action.CHECK_USAGE"
        const val ACTION_RE_TRIGGER = "com.etrisad.zenith.action.RE_TRIGGER_ALARM"

        private const val REQUEST_CODE_ALARM_BASE = 1000
        private const val REQUEST_CODE_CHECK = 203
        private const val REQUEST_CODE_RE_TRIGGER = 204
        private const val REQUEST_CODE_SNOOZE_BASE = 3000

        fun scheduleAlarm(context: Context, alarmTime: String) {
            val (hour, minute) = alarmTime.split(":").map { it.toInt() }
            val requestCode = REQUEST_CODE_ALARM_BASE + hour * 100 + minute

            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_FIRE_ALARM
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = calculateTriggerMillis(alarmTime)

            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun cancelAlarm(context: Context) {
            cancelAlarm(context, null)
        }

        fun cancelAlarm(context: Context, alarmTime: String?) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmTime != null) {
                val (hour, minute) = alarmTime.split(":").map { it.toInt() }
                val requestCode = REQUEST_CODE_ALARM_BASE + hour * 100 + minute
                val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                    action = ACTION_FIRE_ALARM
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            } else {
                for (h in 0..23) {
                    for (m in 0..59) {
                        val requestCode = REQUEST_CODE_ALARM_BASE + h * 100 + m
                        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                            action = ACTION_FIRE_ALARM
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context, requestCode, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                }
            }
        }

        fun scheduleUsageCheck(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE_CHECK, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 60_000L
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun scheduleSnoozeAlarm(context: Context, alarmTime: String, snoozeDurationMinutes: Int, snoozeCount: Int) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_FIRE_ALARM
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
            }

            val requestCode = REQUEST_CODE_SNOOZE_BASE + (alarmTime.hashCode() and 0x7fffffff) % 1000 + snoozeCount

            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + (snoozeDurationMinutes * 60_000L)
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun cancelUsageCheck(context: Context) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE_CHECK, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        private fun scheduleReTrigger(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE_RE_TRIGGER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 300_000L
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        private fun setExactAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (_: Exception) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        private fun calculateTriggerMillis(alarmTime: String): Long {
            val time = LocalTime.parse(alarmTime, DateTimeFormatter.ofPattern("HH:mm"))
            val now = LocalTime.now()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (time.isBefore(now) || time.equals(now)) {
                calendar.add(Calendar.DATE, 1)
            }

            return calendar.timeInMillis
        }
    }
}
