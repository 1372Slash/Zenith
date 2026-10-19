package com.etrisad.zenith.crash

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.R
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.service.AppStateHolder
object CrashHandler {
    private const val ALARM_REQUEST_CODE = 9931
    private const val NOTIFICATION_REQUEST_CODE = 9932
    private const val NOTIFICATION_ID = 9933
    const val CRASH_CHANNEL_ID = "zenith_crash_reports"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            var handled = false
            try {
                val record = CrashStore.record(app, thread, throwable)
                if (record != null) {
                    handled = true
                    val looping = CrashStore.isRapidCrashLoop(app)
                    if (looping) {
                        DbLogBuffer.w(
                            "ZenithCrash",
                            "CRASH_LOOP: suppressing relaunch after repeated crashes"
                        )
                    } else {
                        openCrashScreen(app)
                    }
                    val visible = try {
                        AppStateHolder.isAppVisible
                    } catch (_: Throwable) {
                        false
                    }
                    if (looping || !visible) {
                        // The activity start above can be blocked for background
                        // apps on some devices; the notification is the guaranteed
                        // way back to the report.
                        postCrashNotification(app)
                    }
                }
            } catch (_: Throwable) {
            }
            if (!handled) {
                // Could not write anything: let the platform handle it as usual.
                try {
                    previous?.uncaughtException(thread, throwable)
                } catch (_: Throwable) {
                }
            }
            SafeMode.killProcess()
        }
    }

    private fun openCrashScreen(context: Context) {
        val intent = crashActivityIntent(context)
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
        // Backup launch: the direct start is issued right before the process
        // dies, which some OEMs drop. CrashActivity cancels this alarm on open.
        try {
            val pending = PendingIntent.getActivity(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                val triggerAt = SystemClock.elapsedRealtime() + 1200L
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                } catch (_: Throwable) {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAt, pending)
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun cancelBackupLaunch(context: Context) {
        try {
            val pending = PendingIntent.getActivity(
                context,
                ALARM_REQUEST_CODE,
                crashActivityIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pending)
        } catch (_: Throwable) {
        }
    }

    private fun crashActivityIntent(context: Context): Intent =
        Intent(context, CrashActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
        )

    private fun postCrashNotification(context: Context) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            try {
                val channel = NotificationChannel(
                    CRASH_CHANNEL_ID,
                    "Crash reports",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shown when Zenith stops unexpectedly"
                }
                manager.createNotificationChannel(channel)
            } catch (_: Throwable) {
            }
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_REQUEST_CODE,
                crashActivityIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CRASH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_zenith)
                .setContentTitle("Zenith stopped unexpectedly")
                .setContentText("Tap to view the error details and send a report")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
