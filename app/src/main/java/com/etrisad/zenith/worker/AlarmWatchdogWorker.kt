package com.etrisad.zenith.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.receiver.AlarmBroadcastReceiver
import com.etrisad.zenith.service.AlarmOverlayActivity
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class AlarmWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("AlarmWatchdog", "doWork: checking for missed alarms")
        return try {
            val app = applicationContext as ZenithApplication
            val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
            if (!prefs.alarmMasterEnabled) return Result.success()

            val alarms = app.userPreferencesRepository.parseAlarms(prefs.alarmsJson)
            val enabledAlarms = alarms.filter { it.enabled }
            if (enabledAlarms.isEmpty()) return Result.success()

            val now = System.currentTimeMillis()
            // Kalau user baru saja dismiss (<15 mnt), itu BUKAN missed — jangan spam.
            // Dulu watchdog hanya cek isShowing sehingga alarm yang sudah didismiss
            // normal tetap dituduh "Missed" di tiap run 15-menitan.
            val lastDismiss = AlarmBroadcastReceiver.lastDismissAt(applicationContext)
            if (lastDismiss > 0L && now - lastDismiss < 15 * 60 * 1000L) {
                Log.d("AlarmWatchdog", "skip: dismissed recently (${now - lastDismiss}ms ago)")
                return Result.success()
            }
            for (alarm in enabledAlarms) {
                val parts = alarm.timeString.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: continue

                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val alarmTriggerMs = cal.timeInMillis
                val windowMs = 15 * 60 * 1000L

                if (now > alarmTriggerMs && now < alarmTriggerMs + windowMs) {
                    if (!AlarmOverlayActivity.isShowing) {
                        // Cek state per-alarm juga: kalau baru fire/dismiss, lewati.
                        val prefs = applicationContext.getSharedPreferences(
                            "zenith_alarm_smart_state", Context.MODE_PRIVATE
                        )
                        val lastFire = prefs.getLong("last_fire_${alarm.timeString}", 0L)
                        val lastDismissPerAlarm = prefs.getLong("last_dismiss_${alarm.timeString}", 0L)
                        val lastHandled = maxOf(lastFire, lastDismissPerAlarm)
                        if (lastHandled > 0L && now - lastHandled < windowMs) {
                            Log.d("AlarmWatchdog", "skip missed ${alarm.timeString}: handled ${now - lastHandled}ms ago")
                            continue
                        }
                        Log.w("AlarmWatchdog", "Missed alarm ${alarm.timeString}, firing now")
                        fireMissedAlarmNotification(alarm.timeString, alarm.id)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("AlarmWatchdog", "doWork failed: ${e.message}", e)
            Result.success()
        }
    }

    private fun fireMissedAlarmNotification(alarmTime: String, alarmId: Long = 0L) {
        try {
            val channelId = "zenith_alarm_channel"
            val manager = applicationContext.getSystemService(NotificationManager::class.java)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId, "Alarm", NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Alarm alerts" }
                    manager.createNotificationChannel(channel)
                }
            }

            val activityIntent = Intent(applicationContext, AlarmOverlayActivity::class.java).apply {
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                if (alarmId > 0L) putExtra(AlarmBroadcastReceiver.EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val requestCode = 30000 + ((alarmTime.hashCode() and 0x7fffffff) % 10000) +
                (((alarmId % 1000L + 1000L) % 1000L).toInt())
            val pendingIntent = PendingIntent.getActivity(
                applicationContext, requestCode, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(applicationContext, channelId)
                .setContentTitle("Missed Alarm")
                .setContentText("It's $alarmTime!")
                .setSmallIcon(R.drawable.ic_flag)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(60_000L)
            }

            manager.notify(2003, builder.build())
            Log.d("AlarmWatchdog", "Posted missed-alarm notification for $alarmTime")
        } catch (e: Exception) {
            Log.e("AlarmWatchdog", "fireMissedAlarmNotification failed: ${e.message}", e)
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "AlarmWatchdogWorker"

        fun enqueue(context: Context) {
            // Tanpa RequiresBatteryNotLow: watchdog alarm harus jalan justru saat
            // baterai rendah (momen kritis alarm). Dulu constraint ini membuat
            // watchdog mati total saat baterai low.
            val request = PeriodicWorkRequestBuilder<AlarmWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d("AlarmWatchdog", "Watchdog worker enqueued (15-min interval)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            Log.d("AlarmWatchdog", "Watchdog worker cancelled")
        }
    }
}
