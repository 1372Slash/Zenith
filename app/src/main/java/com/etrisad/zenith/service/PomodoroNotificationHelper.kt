package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.MainActivity
import com.etrisad.zenith.R
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.receiver.PomodoroNotificationReceiver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PomodoroNotificationHelper {
    const val CHANNEL_ID = "zenith_pomodoro_channel"
    const val NOTIFICATION_ID = 8810

    const val ACTION_PAUSE = "com.etrisad.zenith.action.POMODORO_PAUSE"
    const val ACTION_RESUME = "com.etrisad.zenith.action.POMODORO_RESUME"
    const val ACTION_STOP = "com.etrisad.zenith.action.POMODORO_STOP"

    private const val END_GRACE_MILLIS = 2_000L

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the remaining time of a running Pomodoro session"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun updateNotification(
        context: Context,
        isSessionActive: Boolean,
        isBreakActive: Boolean,
        isPaused: Boolean,
        remainingMillis: Long,
        currentSession: Int,
        totalSessions: Int,
        pauseable: Boolean
    ) {
        if (!isSessionActive) {
            cancelNotification(context)
            return
        }

        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val remaining = remainingMillis.coerceAtLeast(0L)
        val endsAtMillis = System.currentTimeMillis() + remaining
        val sessionLabel = "Session $currentSession of $totalSessions"

        val title = when {
            isPaused -> "Pomodoro paused"
            isBreakActive -> "Pomodoro break"
            else -> "Pomodoro focus"
        }

        val text = when {
            isPaused -> "${formatClock(remaining)} left • $sessionLabel"
            isBreakActive -> "$sessionLabel • break ends at ${formatEnd(endsAtMillis)}"
            else -> "$sessionLabel • ends at ${formatEnd(endsAtMillis)}"
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(context, PomodoroNotificationReceiver::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, PomodoroNotificationReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snooze)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setShowWhen(!isPaused)
            .setWhen(endsAtMillis)
            .setUsesChronometer(!isPaused)
            .setChronometerCountDown(!isPaused)
            .setTimeoutAfter(if (isPaused) 0L else remaining + END_GRACE_MILLIS)

        builder.addAction(0, "Open app", openPendingIntent)
        if (pauseable) {
            builder.addAction(0, if (isPaused) "Resume" else "Pause", pauseResumePendingIntent)
        }
        builder.addAction(0, "Stop", stopPendingIntent)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun updateFromPreferences(context: Context, preferences: UserPreferences): Boolean {
        val now = System.currentTimeMillis()
        val sessionEnd = preferences.pomodoroSessionEndTimestamp
        if (!preferences.pomodoroEnabled || sessionEnd <= now) {
            cancelNotification(context)
            return false
        }

        val breakEnd = preferences.pomodoroBreakEndTimestamp
        val isBreak = breakEnd > now
        val isPaused = SharedMonitoringState.isPomodoroPaused
        val endsAt = if (isBreak) breakEnd else sessionEnd
        val reference = if (isPaused && SharedMonitoringState.pomodoroPauseTimestamp > 0L) {
            SharedMonitoringState.pomodoroPauseTimestamp
        } else {
            now
        }

        updateNotification(
            context = context,
            isSessionActive = true,
            isBreakActive = isBreak,
            isPaused = isPaused,
            remainingMillis = (endsAt - reference).coerceAtLeast(0L),
            currentSession = preferences.pomodoroCurrentSessionNumber,
            totalSessions = preferences.pomodoroSessionCount,
            pauseable = preferences.pomodoroPauseable
        )
        return true
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun formatClock(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    private fun formatEnd(timestampMillis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}
