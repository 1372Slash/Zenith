package com.etrisad.zenith.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.model.AlarmItem
import com.etrisad.zenith.service.AlarmOverlayActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_ALARM -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                Log.d("AlarmReceiver", "onReceive: ACTION_FIRE_ALARM alarmTime=$alarmTime")
                scheduleReTrigger(context, alarmTime)
                showAlarm(context, alarmTime, intent)
            }
            ACTION_RE_TRIGGER -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                Log.d("AlarmReceiver", "onReceive: ACTION_RE_TRIGGER alarmTime=$alarmTime")
                scheduleReTrigger(context, alarmTime)
                showAlarm(context, alarmTime, intent)
            }
            ACTION_CHECK_USAGE -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                val isOnce = intent.getBooleanExtra(EXTRA_IS_ONCE, false)
                val recentUsage = hasRecentUsage(context)
                Log.d("AlarmReceiver", "ACTION_CHECK_USAGE: alarmTime=$alarmTime, recentUsage=$recentUsage, isOnce=$isOnce")
                if (!recentUsage) {
                    scheduleReTrigger(context, alarmTime)
                } else {
                    Log.d("AlarmReceiver", "ACTION_CHECK_USAGE: user awake, auto-repeat completed")
                    sendAutoRepeatCompleteNotification(context, alarmTime)
                    if (isOnce) disableAlarm(context, alarmTime)
                }
            }
        }
    }

    private fun showAlarm(context: Context, alarmTime: String, intent: Intent) {
        val snoozeCount = intent.getIntExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, 0)

        val activityIntent = Intent(context, AlarmOverlayActivity::class.java).apply {
            putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            if (snoozeCount > 0) {
                putExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        Log.d("AlarmReceiver", "showAlarm: trying startActivity")
        try {
            context.startActivity(activityIntent)
            Log.d("AlarmReceiver", "showAlarm: startActivity succeeded")
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "showAlarm: startActivity failed: ${e.message}")
        }

        fireAlarmNotification(context, alarmTime, snoozeCount, activityIntent)
        scheduleWakeAlarm(context, alarmTime, snoozeCount)
    }

    private fun fireAlarmNotification(context: Context, alarmTime: String, snoozeCount: Int, activityIntent: Intent) {
        try {
            val channelId = "zenith_alarm_channel"
            val manager = context.getSystemService(NotificationManager::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alarm alerts"
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle(if (snoozeCount > 0) "Snoozed Alarm" else "Alarm")
                .setContentText("It's $alarmTime!")
                .setSmallIcon(R.drawable.ic_flag)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(false)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(5000L)
            }

            manager.notify(2001, builder.build())
            Log.d("AlarmReceiver", "fireAlarmNotification: notification posted")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "fireAlarmNotification failed: ${e.message}", e)
        }
    }

    private fun scheduleWakeAlarm(context: Context, alarmTime: String, snoozeCount: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val wakeIntent = Intent(context, AlarmOverlayActivity::class.java).apply {
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                if (snoozeCount > 0) {
                    putExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val wakePendingIntent = PendingIntent.getActivity(
                context, 3002, wakeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtMillis = System.currentTimeMillis() + 1000
            val showIntent = buildShowIntent(context)
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            alarmManager.setAlarmClock(alarmClockInfo, wakePendingIntent)
            Log.d("AlarmReceiver", "scheduleWakeAlarm: setAlarmClock for +1s")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "scheduleWakeAlarm failed: ${e.message}", e)
        }
    }

    private fun sendAutoRepeatCompleteNotification(context: Context, alarmTime: String) {
        try {
            val alarmName = try {
                val app = context.applicationContext as ZenithApplication
                runBlocking {
                    val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                    app.userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                        .find { it.timeString == alarmTime }?.name
                }
            } catch (_: Exception) { null }
            val display = alarmName?.let { "$it at $alarmTime" } ?: alarmTime
            val channelId = "zenith_alarm_channel"
            val manager = context.getSystemService(NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(channelId, "Alarm", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Alarm alerts"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle("Alarm auto-repeat stopped")
                .setContentText("$display will not fire again today.")
                .setSmallIcon(R.drawable.ic_alarm_off)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setOngoing(false)
            manager.notify(2002, builder.build())
        } catch (_: Exception) { }
    }

    private fun hasRecentUsage(context: Context): Boolean {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val oneMinuteAgo = now - 60_000L
            val events = usm.queryEvents(oneMinuteAgo, now)
            var hasEvent = false
            val ownPackage = context.packageName
            val launcherPackage = try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val ttsEnginePackage = try {
                val ttsIntent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                context.packageManager.resolveActivity(ttsIntent, 0)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (event.packageName == ownPackage) continue
                if (event.packageName == launcherPackage) continue
                if (ttsEnginePackage != null && event.packageName == ttsEnginePackage) continue
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                ) {
                    hasEvent = true
                    break
                }
            }
            Log.d("AlarmReceiver", "hasRecentUsage: window=[$oneMinuteAgo, $now], result=$hasEvent (launcher=$launcherPackage, tts=$ttsEnginePackage)")
            return hasEvent
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "hasRecentUsage check failed (permission?): ${e.message}")
            return false
        }
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.etrisad.zenith.action.FIRE_ALARM"
        const val ACTION_CHECK_USAGE = "com.etrisad.zenith.action.CHECK_USAGE"
        const val ACTION_RE_TRIGGER = "com.etrisad.zenith.action.RE_TRIGGER_ALARM"
        const val EXTRA_IS_ONCE = "extra_is_once"

        private const val REQUEST_CODE_ALARM_BASE = 1000
        private const val REQUEST_CODE_CHECK_BASE = 5000
        private const val REQUEST_CODE_RE_TRIGGER_BASE = 8000
        private const val REQUEST_CODE_SNOOZE_BASE = 3000
        private fun requestCodeFor(base: Int, alarmTime: String): Int {
            val parts = alarmTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return base + hour * 100 + minute
        }

        private fun buildShowIntent(context: Context): PendingIntent {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, com.etrisad.zenith.MainActivity::class.java)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun hasExactAlarmPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                return am.canScheduleExactAlarms()
            }
            return true
        }

        fun promptExactAlarmPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasExactAlarmPermission(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.w("AlarmReceiver", "Failed to open exact alarm settings: ${e.message}")
                }
            }
        }

        fun requestBatteryOptimizationExemption(context: Context) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "Failed to request battery optimization exemption: ${e.message}")
            }
        }

        fun scheduleAlarm(context: Context, alarmTime: String, days: Set<Int> = emptySet()) {
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

            val triggerAtMillis = calculateTriggerMillis(alarmTime, days)
            Log.d("AlarmReceiver", "scheduleAlarm: $alarmTime at $triggerAtMillis (now=${System.currentTimeMillis()})")

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
                // Also stop that alarm's own repeat/usage-check cycle, otherwise it
                // keeps re-firing every 5 minutes even after being "cancelled".
                cancelReTrigger(context, alarmTime)
                cancelUsageCheck(context, alarmTime)
            } else {
                Log.d("AlarmReceiver", "cancelAlarm: cancelling all alarm intents")
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

                        val reTriggerIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                            action = ACTION_RE_TRIGGER
                        }
                        val reTriggerCode = REQUEST_CODE_RE_TRIGGER_BASE + h * 100 + m
                        val reTriggerPi = PendingIntent.getBroadcast(
                            context, reTriggerCode, reTriggerIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(reTriggerPi)
                        reTriggerPi.cancel()

                        val usageIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                            action = ACTION_CHECK_USAGE
                        }
                        val usageCode = REQUEST_CODE_CHECK_BASE + h * 100 + m
                        val usagePi = PendingIntent.getBroadcast(
                            context, usageCode, usageIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(usagePi)
                        usagePi.cancel()
                    }
                }
            }
        }

        fun scheduleUsageCheck(context: Context, alarmTime: String, isOnce: Boolean = false) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_IS_ONCE, isOnce)
            }

            val requestCode = requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 60_000L
            Log.d("AlarmReceiver", "scheduleUsageCheck: $alarmTime +60s (requestCode=$requestCode)")
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun scheduleSnoozeAlarm(context: Context, alarmTime: String, snoozeDurationMinutes: Int, snoozeCount: Int) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
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

            // A snooze replaces the "nag every 5 min" safety net for this alarm — otherwise
            // the earlier scheduleReTrigger() call (from the fire/retrigger that led to this
            // snooze) is still pending and can fire the alarm again *before* the snooze
            // duration is up.
            cancelReTrigger(context, alarmTime)
        }

        fun cancelUsageCheck(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
            }
            val requestCode = requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
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

            val requestCode = requestCodeFor(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 300_000L
            Log.d("AlarmReceiver", "scheduleReTrigger: $alarmTime +5min (requestCode=$requestCode)")
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun cancelReTrigger(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
            }
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val requestCode = requestCodeFor(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        private fun setExactAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmReceiver", "setExactAlarm: SCHEDULE_EXACT_ALARM not granted, trying setAlarmClock anyway (Doze-exempt path)")
            }

            try {
                val showIntent = buildShowIntent(context)
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.d("AlarmReceiver", "setExactAlarm: setAlarmClock at $triggerAtMillis")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "setAlarmClock failed, falling back: ${e.message}")
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (e2: Exception) {
                    Log.e("AlarmReceiver", "setExactAndAllowWhileIdle fallback also failed: ${e2.message}")
                    try {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } catch (e3: Exception) {
                        Log.e("AlarmReceiver", "setAndAllowWhileIdle fallback also failed: ${e3.message}")
                    }
                }
            }
        }

        private fun disableAlarm(context: Context, alarmTime: String) {
            try {
                val app = context.applicationContext as ZenithApplication
                runBlocking {
                    val repo = app.userPreferencesRepository
                    val prefs = repo.userPreferencesFlow.first()
                    val alarms = repo.parseAlarms(prefs.alarmsJson)
                    val alarm = alarms.find { it.timeString == alarmTime } ?: return@runBlocking
                    repo.updateAlarm(alarm.copy(enabled = false))
                }
                cancelAlarm(context, alarmTime)
                Log.d("AlarmReceiver", "disableAlarm: $alarmTime disabled")
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "disableAlarm failed: ${e.message}")
            }
        }

        fun rescheduleAllAlarms(context: Context, enabledAlarms: List<AlarmItem>) {
            cancelAlarm(context)
            for (alarm in enabledAlarms) {
                scheduleAlarm(context, alarm.timeString, alarm.days)
            }
        }

        private fun calculateTriggerMillis(alarmTime: String, days: Set<Int> = emptySet()): Long {
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

            if (days.isNotEmpty()) {
                var attempts = 0
                while (attempts < 14) {
                    val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    if (currentDayOfWeek in days) break
                    calendar.add(Calendar.DATE, 1)
                    attempts++
                }
            }

            Log.d("AlarmReceiver", "calculateTriggerMillis: alarmTime=$alarmTime, now=$now, trigger=${calendar.timeInMillis}")
            return calendar.timeInMillis
        }
    }
}