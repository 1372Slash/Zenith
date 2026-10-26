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
import androidx.core.app.NotificationCompat
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.model.AlarmItem
import com.etrisad.zenith.service.AlarmOverlayActivity
import com.etrisad.zenith.util.AlarmChainHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_ALARM -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                cancelSmartWakeReminder(context)
                cancelAutoRepeatComplete(context)
                cancelMissedAlarmNotification(context)
                recordAlarmFire(context, alarmTime)
                scheduleReTrigger(context, alarmTime, 0, alarmId)
                showAlarm(context, alarmTime, intent)
            }
            ACTION_RE_TRIGGER -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                val attempt = intent.getIntExtra(EXTRA_RETRIGGER_COUNT, 0)
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
                cancelSmartWakeReminder(context)
                cancelAutoRepeatComplete(context)
                cancelMissedAlarmNotification(context)
                recordAlarmFire(context, alarmTime)
                if (!isSnooze) {
                    scheduleReTrigger(context, alarmTime, attempt, alarmId)
                }
                showAlarm(context, alarmTime, intent)
            }
            ACTION_CHECK_USAGE -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        handleCheckUsage(context, intent)
                    } catch (_: Exception) {
                    } finally {
                        try { pending.finish() } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    private suspend fun handleCheckUsage(context: Context, intent: Intent) {
        val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
        val isOnce = intent.getBooleanExtra(EXTRA_IS_ONCE, false)
        val attempt = intent.getIntExtra(EXTRA_RETRIGGER_COUNT, 0)
        val waitCount = intent.getIntExtra(EXTRA_WAIT_COUNT, 0)
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        val dismissAt = intent.getLongExtra(EXTRA_DISMISS_AT, 0L)
        val checkKey = AlarmChainHelper.alarmKey(alarmId, alarmTime)
        when (
            AlarmChainHelper.decideUsageCheck(
                AlarmOverlayActivity.isShowing,
                AlarmOverlayActivity.showingAlarmKey,
                checkKey
            ) {
                isUserAwake(context, dismissAt)
            }
        ) {
            AlarmChainHelper.UsageCheckOutcome.KEEP_WAITING_SAME_ALARM -> {
                if (waitCount < MAX_KEEP_WAITING_CHECKS) {
                    scheduleUsageCheck(context, alarmTime, isOnce, attempt, alarmId, dismissAt, waitCount + 1)
                }
                return
            }
            AlarmChainHelper.UsageCheckOutcome.RE_TRIGGER -> {
                scheduleReTrigger(context, alarmTime, attempt, alarmId)
            }
            AlarmChainHelper.UsageCheckOutcome.COMPLETE -> {
                cancelSmartWakeReminder(context)
                sendAutoRepeatCompleteNotification(context, alarmTime, alarmId)
                if (isOnce) disableAlarm(context, alarmTime, alarmId)
            }
        }
    }

    private fun showAlarm(context: Context, alarmTime: String, intent: Intent) {
        val snoozeCount = intent.getIntExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, 0)
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)

        val activityIntent = Intent(context, AlarmOverlayActivity::class.java).apply {
            putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            if (snoozeCount > 0) {
                putExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try {
            com.etrisad.zenith.service.AlarmPlaybackService.start(context, alarmTime, alarmId)
        } catch (e: Exception) {
        }
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
        }
        fireAlarmNotification(context, alarmTime, snoozeCount, activityIntent, alarmId)
    }

    private fun fireAlarmNotification(context: Context, alarmTime: String, snoozeCount: Int, activityIntent: Intent, alarmId: Long = 0L) {
        try {
            val channelId = CHANNEL_ID_FIRING
            val manager = context.getSystemService(NotificationManager::class.java)
            ensureFiringChannel(context)
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    if (manager != null && !manager.canUseFullScreenIntent()) {
                    }
                } catch (_: Exception) { }
            }
            val contentRequestCode = notificationRequestCode(alarmTime, alarmId, 11)
            val pendingIntent = PendingIntent.getActivity(
                context, contentRequestCode, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle(if (snoozeCount > 0) "Snoozed Alarm" else "Alarm")
                .setContentText("It's $alarmTime!")
                .setSmallIcon(R.drawable.ic_flag)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(30_000L)
            }

            manager.notify(NOTIFICATION_ID_FIRING, builder.build())
        } catch (e: Exception) {
        }
    }

    private suspend fun sendAutoRepeatCompleteNotification(context: Context, alarmTime: String, alarmId: Long = 0L) {
        try {
            val alarmName = try {
                val app = context.applicationContext as ZenithApplication
                AlarmChainHelper.findAlarm(
                    app.userPreferencesRepository.getAlarmsSnapshot(), alarmId, alarmTime
                )?.name
            } catch (_: Exception) { null }
            val display = alarmName?.let { "$it at $alarmTime" } ?: alarmTime
            ensureInfoChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
                .setContentTitle("Alarm auto-repeat stopped")
                .setContentText("$display will not fire again today.")
                .setSmallIcon(R.drawable.ic_alarm_off)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOngoing(false)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(120_000L)
            }
            manager.notify(NOTIFICATION_ID_COMPLETE, builder.build())
        } catch (_: Exception) { }
    }
    internal fun hasRecentUsage(context: Context, dismissAtMs: Long): Boolean {
        return isUserAwake(context, dismissAtMs)
    }

    internal fun isUserAwake(context: Context, dismissAtMs: Long): Boolean {
        try {
            if (!hasUsageStatsPermission(context)) {
                return true
            }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isInteractive) {
                return false
            }
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (keyguardManager.isKeyguardLocked) {
                return false
            }
            return hasGenuineInteraction(context, dismissAtMs)
        } catch (e: SecurityException) {
            return true
        } catch (e: Exception) {
            return true
        }
    }

    internal fun hasGenuineInteraction(context: Context, dismissAtMs: Long): Boolean {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val since = if (dismissAtMs > 0L) dismissAtMs.coerceAtMost(now) else now - 60_000L
            val windowStart = since + USAGE_GRACE_MS
            if (windowStart >= now) return false
            val events = usm.queryEvents(since, now)
            val ownPackage = context.packageName
            val launcherPackage = try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val ttsEnginePackage = try {
                val ttsIntent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                context.packageManager.resolveActivity(ttsIntent, 0)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val ignored = setOfNotNull(ownPackage, "com.android.systemui", "android", ttsEnginePackage)

            val activeStart = mutableMapOf<String, Long>()
            val entersAfterGrace = mutableMapOf<String, Int>()
            val foregroundInWindow = mutableMapOf<String, Long>()
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (ignored.contains(event.packageName)) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!activeStart.containsKey(event.packageName)) {
                            activeStart[event.packageName] = event.timeStamp
                        }
                        if (event.timeStamp >= windowStart) {
                            entersAfterGrace[event.packageName] =
                                (entersAfterGrace[event.packageName] ?: 0) + 1
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = activeStart.remove(event.packageName) ?: continue
                        val overlapStart = maxOf(start, windowStart)
                        val dur = (event.timeStamp - overlapStart).coerceAtLeast(0L)
                        if (dur > 0L) {
                            foregroundInWindow[event.packageName] =
                                (foregroundInWindow[event.packageName] ?: 0L) + dur
                        }
                    }
                }
            }
            for ((pkg, start) in activeStart) {
                val overlapStart = maxOf(start, windowStart)
                val dur = (now - overlapStart).coerceAtLeast(0L)
                if (dur > 0L) {
                    foregroundInWindow[pkg] = (foregroundInWindow[pkg] ?: 0L) + dur
                }
            }
            for ((pkg, enters) in entersAfterGrace) {
                if (enters >= 1 && pkg != launcherPackage) {
                    return true
                }
            }
            for ((pkg, total) in foregroundInWindow) {
                if (pkg != launcherPackage && total >= MIN_SUSTAINED_FOREGROUND_MS) {
                    return true
                }
            }
            val launcherEnters = launcherPackage?.let { entersAfterGrace[it] ?: 0 } ?: 0
            if (launcherEnters >= 2) {
                return true
            }
            return false
        } catch (e: Exception) {
            return true
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        const val ACTION_FIRE_ALARM = "com.etrisad.zenith.action.FIRE_ALARM"
        const val ACTION_CHECK_USAGE = "com.etrisad.zenith.action.CHECK_USAGE"
        const val ACTION_RE_TRIGGER = "com.etrisad.zenith.action.RE_TRIGGER_ALARM"
        const val EXTRA_IS_ONCE = "extra_is_once"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_DISMISS_AT = "extra_dismiss_at"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val NOTIFICATION_ID_FIRING = 2001
        const val NOTIFICATION_ID_COMPLETE = 2002
        const val NOTIFICATION_ID_MISSED = 2003
        const val NOTIFICATION_ID_SMART_WAKE_REMINDER = 2004
        const val NOTIFICATION_ID_TEST_GOAL = 2005
        const val CHANNEL_ID_FIRING = "zenith_alarm_channel"
        const val CHANNEL_ID_INFO = "zenith_alarm_info_channel"
        internal const val USAGE_GRACE_MS = 5_000L
        internal const val MIN_SUSTAINED_FOREGROUND_MS = 20_000L
        private const val STATE_PREFS = "zenith_alarm_smart_state"
        private const val KEY_LAST_DISMISS_AT = "last_dismiss_at"

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
        private fun parseHourMinute(alarmTime: String): Pair<Int, Int>? {
            val parts = alarmTime.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour to minute
        }

        private fun requestCodeForId(base: Int, alarmTime: String, alarmId: Long): Int {
            val parts = alarmTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val idPart = ((alarmId % 100000L + 100000L) % 100000L).toInt() * 3000
            return base + idPart + hour * 100 + minute
        }

        private fun notificationRequestCode(alarmTime: String, alarmId: Long, salt: Int): Int {
            val base = (alarmTime.hashCode() and 0x7fffffff) % 10000
            val idPart = (((alarmId % 1000L + 1000L) % 1000L).toInt() * 100) % 100000
            return 20000 + salt * 100000 + base + idPart
        }
        private fun cancelPendingIfExists(context: Context, requestCode: Int, action: String) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                this.action = action
            }
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
            try {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
            } catch (_: Exception) { }
            try { pi.cancel() } catch (_: Exception) { }
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
            }
        }

        fun scheduleAlarm(context: Context, alarmTime: String, days: Set<Int> = emptySet(), alarmId: Long = 0L) {
            val (hour, minute) = parseHourMinute(alarmTime) ?: return
            val requestCode = if (alarmId > 0L) requestCodeForId(REQUEST_CODE_ALARM_BASE, alarmTime, alarmId)
            else REQUEST_CODE_ALARM_BASE + hour * 100 + minute

            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_FIRE_ALARM
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = calculateTriggerMillis(alarmTime, days)

            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun cancelAlarm(context: Context) {
            cancelAlarm(context, null)
        }

        fun cancelAlarm(context: Context, alarmTime: String?, alarmId: Long = 0L) {
            if (alarmTime != null) {
                val parsed = parseHourMinute(alarmTime)
                if (parsed != null) {
                    val (hour, minute) = parsed
                    cancelPendingIfExists(
                        context, REQUEST_CODE_ALARM_BASE + hour * 100 + minute, ACTION_FIRE_ALARM
                    )
                }
                if (alarmId > 0L) {
                    cancelPendingIfExists(
                        context, requestCodeForId(REQUEST_CODE_ALARM_BASE, alarmTime, alarmId), ACTION_FIRE_ALARM
                    )
                    // Migrasi: batalkan juga kode lama ((id % 200000) * 2400) agar tidak
                    // ada exact alarm hantu yang lolos setelah update aplikasi.
                    try {
                        val parts = alarmTime.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val legacyIdCode = REQUEST_CODE_ALARM_BASE + h * 100 + m +
                            ((alarmId % 200000L) * 2400L).toInt()
                        cancelPendingIfExists(context, legacyIdCode, ACTION_FIRE_ALARM)
                    } catch (_: Exception) { }
                }
                cancelReTrigger(context, alarmTime, alarmId)
                cancelUsageCheck(context, alarmTime, alarmId)
                // Alarm dimatikan/dihapus -> reminder "akan bunyi lagi" wajib hilang.
                // Dulu notif 2004 dibiarkan basi menumpuk di shade.
                cancelSmartWakeReminder(context)
                cancelFiringNotification(context)
            } else {
                for (h in 0..23) {
                    for (m in 0..59) {
                        cancelPendingIfExists(
                            context, REQUEST_CODE_ALARM_BASE + h * 100 + m, ACTION_FIRE_ALARM
                        )
                        cancelPendingIfExists(
                            context, REQUEST_CODE_RE_TRIGGER_BASE + h * 100 + m, ACTION_RE_TRIGGER
                        )
                        cancelPendingIfExists(
                            context, REQUEST_CODE_CHECK_BASE + h * 100 + m, ACTION_CHECK_USAGE
                        )
                    }
                }
                cancelAllAlarmNotifications(context)
            }
        }

        @JvmOverloads
        fun scheduleUsageCheck(
            context: Context,
            alarmTime: String,
            isOnce: Boolean = false,
            retriggerAttempt: Int = 0,
            alarmId: Long = 0L,
            dismissAtMs: Long = System.currentTimeMillis(),
            waitCount: Int = 0
        ) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_IS_ONCE, isOnce)
                putExtra(EXTRA_RETRIGGER_COUNT, retriggerAttempt)
                putExtra(EXTRA_DISMISS_AT, dismissAtMs)
                putExtra(EXTRA_WAIT_COUNT, waitCount)
                if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            }

            // Id-aware so two different alarms don't overwrite each other's usage-check.
            // Re-schedule the legacy variant? No — id-aware + legacy is enough
            // cancelled first so there's no stale double check.
            cancelUsageCheck(context, alarmTime, alarmId)
            val requestCode = if (alarmId > 0L) requestCodeForId(REQUEST_CODE_CHECK_BASE, alarmTime, alarmId)
            else requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 60_000L
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        @JvmOverloads
        fun scheduleSnoozeAlarm(
            context: Context,
            alarmTime: String,
            snoozeDurationMinutes: Int,
            snoozeCount: Int,
            alarmId: Long = 0L
        ) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(AlarmOverlayActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
                putExtra(EXTRA_IS_SNOOZE, true)
                if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            }

            val base = if (alarmId > 0L) requestCodeForId(REQUEST_CODE_SNOOZE_BASE, alarmTime, alarmId)
            else REQUEST_CODE_SNOOZE_BASE + (alarmTime.hashCode() and 0x7fffffff) % 1000
            val requestCode = base + (snoozeCount % 50)

            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // IMPORTANT: clean first, THEN schedule. cancelReTrigger sweeps
            // snooze codes 0..50 — if the order is reversed, the newly installed
            // snooze gets cancelled too and NEVER fires.
            // Snooze replaces smart-repeat: clean the chain + stale reminder.
            cancelReTrigger(context, alarmTime, alarmId)
            cancelUsageCheck(context, alarmTime, alarmId)
            cancelSmartWakeReminder(context)
            cancelFiringNotification(context)
            val triggerAtMillis = System.currentTimeMillis() + (snoozeDurationMinutes * 60_000L)
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        @JvmOverloads
        fun cancelUsageCheck(context: Context, alarmTime: String, alarmId: Long = 0L) {
            cancelPendingIfExists(
                context, requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime), ACTION_CHECK_USAGE
            )
            if (alarmId > 0L) {
                cancelPendingIfExists(
                    context, requestCodeForId(REQUEST_CODE_CHECK_BASE, alarmTime, alarmId), ACTION_CHECK_USAGE
                )
            }
        }

        const val EXTRA_RETRIGGER_COUNT = "retrigger_count"
        private const val MAX_RE_TRIGGER_ATTEMPTS = 12
        const val EXTRA_WAIT_COUNT = "wait_count"
        /**
         * Cap for KEEP_WAITING re-checks (60s apart ~= overlay 10-min
         * auto-dismiss). Without this a stuck overlay would schedule exact
         * alarms forever with an unchanged attempt counter.
         */
        private const val MAX_KEEP_WAITING_CHECKS = 10

        @JvmOverloads
        fun scheduleReTrigger(context: Context, alarmTime: String, attempt: Int = 0, alarmId: Long = 0L) {
            if (attempt >= MAX_RE_TRIGGER_ATTEMPTS) {
                // Rantai habis: once-alarm harus dimatikan agar tidak nyangkut enabled
                // tanpa jadwal; reminder basi juga dibersihkan.
                cancelSmartWakeReminder(context)
                return
            }
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_RETRIGGER_COUNT, attempt + 1)
                putExtra(EXTRA_IS_SNOOZE, false)
                if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            }

            val requestCode = if (alarmId > 0L) requestCodeForId(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime, alarmId)
            else requestCodeFor(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 300_000L
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        @JvmOverloads
        fun cancelReTrigger(context: Context, alarmTime: String, alarmId: Long = 0L) {
            cancelPendingIfExists(
                context, requestCodeFor(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime), ACTION_RE_TRIGGER
            )
            if (alarmId > 0L) {
                cancelPendingIfExists(
                    context, requestCodeForId(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime, alarmId), ACTION_RE_TRIGGER
                )
            }
            // Snooze memakai action yang sama dengan base berbeda; sapu juga agar
            // tidak ada snooze basi yang ikut berbunyi setelah repeat dibatalkan.
            for (i in 0..50) {
                cancelPendingIfExists(
                    context,
                    REQUEST_CODE_SNOOZE_BASE + (alarmTime.hashCode() and 0x7fffffff) % 1000 + i,
                    ACTION_RE_TRIGGER
                )
                if (alarmId > 0L) {
                    cancelPendingIfExists(
                        context,
                        requestCodeForId(REQUEST_CODE_SNOOZE_BASE, alarmTime, alarmId) + i,
                        ACTION_RE_TRIGGER
                    )
                }
            }
        }

        private fun setExactAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (!alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                        return
                    }
                } catch (_: Exception) {}
            }
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (e2: Exception) {
                try {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (e3: Exception) {
                }
            }
        }

        suspend fun disableAlarm(context: Context, alarmTime: String, alarmId: Long = 0L) {
            try {
                val app = context.applicationContext as ZenithApplication
                val repo = app.userPreferencesRepository
                val alarm = AlarmChainHelper.findAlarm(repo.getAlarmsSnapshot(), alarmId, alarmTime) ?: return
                repo.updateAlarm(alarm.copy(enabled = false))
                cancelAlarm(context, alarmTime, alarm.id)
            } catch (e: Exception) {
            }
        }

        // ---- Higiene notifikasi: satu pintu cancel agar tidak menumpuk ----
        fun cancelFiringNotification(context: Context) {
            try {
                (context.getSystemService(NotificationManager::class.java))?.cancel(NOTIFICATION_ID_FIRING)
            } catch (_: Exception) { }
        }

        fun cancelSmartWakeReminder(context: Context) {
            try {
                (context.getSystemService(NotificationManager::class.java))?.cancel(NOTIFICATION_ID_SMART_WAKE_REMINDER)
            } catch (_: Exception) { }
        }

        fun cancelAutoRepeatComplete(context: Context) {
            try {
                (context.getSystemService(NotificationManager::class.java))?.cancel(NOTIFICATION_ID_COMPLETE)
            } catch (_: Exception) { }
        }

        fun cancelMissedAlarmNotification(context: Context) {
            try {
                (context.getSystemService(NotificationManager::class.java))?.cancel(NOTIFICATION_ID_MISSED)
            } catch (_: Exception) { }
        }

        fun cancelAllAlarmNotifications(context: Context) {
            cancelFiringNotification(context)
            cancelSmartWakeReminder(context)
            cancelAutoRepeatComplete(context)
            cancelMissedAlarmNotification(context)
            try {
                // Foreground service playback (2010) hanya bisa hilang via stopService,
                // tapi sapu notifnya juga agar tidak nyangkut bila service sudah mati.
                (context.getSystemService(NotificationManager::class.java))?.cancel(2010)
            } catch (_: Exception) { }
        }

        private fun ensureFiringChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                val existing = manager.getNotificationChannel(CHANNEL_ID_FIRING)
                if (existing == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID_FIRING, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                            description = "Alarm alerts"
                            // Alarm harus tembus Do Not Disturb dan terlihat di lockscreen,
                            // kalau tidak full-screen intent tidak di-fire sistem.
                            setBypassDnd(true)
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                        }
                    )
                } else if (!existing.canBypassDnd() ||
                    existing.lockscreenVisibility != android.app.Notification.VISIBILITY_PUBLIC
                ) {
                    // Channel dibuat versi lama tanpa bypass-DND/visibility: patch agar
                    // alarm lama yang update aplikasi ikut tembus lockscreen + DND.
                    existing.setBypassDnd(true)
                    existing.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    manager.createNotificationChannel(existing)
                }
            } catch (_: Exception) { }
        }

        private fun ensureInfoChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL_ID_INFO) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID_INFO, "Alarm Info", NotificationManager.IMPORTANCE_LOW).apply {
                            description = "Silent alarm reminders"
                            setSound(null, null)
                            enableVibration(false)
                        }
                    )
                }
            } catch (_: Exception) { }
        }

        // ---- State kecil untuk watchdog agar tidak false "missed" ----
        fun recordAlarmFire(context: Context, alarmTime: String) {
            try {
                context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("last_fire_$alarmTime", System.currentTimeMillis())
                    .putLong("last_fire_at", System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) { }
        }

        fun recordAlarmDismiss(context: Context, alarmTime: String) {
            try {
                context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).edit()
                    .putLong("last_dismiss_$alarmTime", System.currentTimeMillis())
                    .putLong(KEY_LAST_DISMISS_AT, System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) { }
        }

        fun lastDismissAt(context: Context): Long {
            return try {
                context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                    .getLong(KEY_LAST_DISMISS_AT, 0L)
            } catch (_: Exception) { 0L }
        }

        @JvmOverloads
        suspend fun showAutoRepeatReminderNotification(context: Context, alarmTime: String, alarmId: Long = 0L) {
            try {
                ensureInfoChannel(context)
                val manager = context.getSystemService(NotificationManager::class.java)

                val reTriggerAt = System.currentTimeMillis() + 300_000L
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val nextTime = java.time.Instant.ofEpochMilli(reTriggerAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
                    .format(formatter)

                val alarmName = try {
                    val app = context.applicationContext as ZenithApplication
                    AlarmChainHelper.findAlarm(
                        app.userPreferencesRepository.getAlarmsSnapshot(), alarmId, alarmTime
                    )?.name
                } catch (_: Exception) { null }
                val namePart = alarmName?.let { "\"$it\" " } ?: ""

                val contentIntent = PendingIntent.getActivity(
                    context, notificationRequestCode(alarmTime, alarmId, 24),
                    context.packageManager.getLaunchIntentForPackage(context.packageName)
                        ?: Intent(context, com.etrisad.zenith.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ID_INFO)
                    .setContentTitle("Smart Wake Reminder")
                    .setContentText("${namePart}Next alarm ~$nextTime. Wake up and use your phone!")
                    .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("${namePart}Alarm will ring again in ~5 minutes (~$nextTime).\n\n" +
                                "Wake up now and use your phone so the alarm doesn't need to ring again!"))
                    .setSmallIcon(R.drawable.ic_alarm_smart_wake)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setContentIntent(contentIntent)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Auto-dismiss dalam ~6 menit bila rantai dibatalkan (mis. alarm
                    // dimatikan manual) sehingga tidak jadi notif basi.
                    builder.setTimeoutAfter(360_000L)
                }

                manager.notify(NOTIFICATION_ID_SMART_WAKE_REMINDER, builder.build())
            } catch (e: Exception) {
            }
        }

        /**
         * Batalkan seluruh rantai smart (retrigger + usage-check) beserta notifnya.
         * Dipakai oleh tombol "Stop Alarm", snooze, dan wake-up-verified agar tidak ada
         * alarm susulan basi yang tetap berbunyi.
         */
        @JvmOverloads
        fun cancelSmartChain(context: Context, alarmTime: String, alarmId: Long = 0L) {
            cancelReTrigger(context, alarmTime, alarmId)
            cancelUsageCheck(context, alarmTime, alarmId)
            cancelSmartWakeReminder(context)
            cancelFiringNotification(context)
        }

        fun rescheduleAllAlarms(context: Context, enabledAlarms: List<AlarmItem>) {
            cancelAlarm(context)
            for (alarm in enabledAlarms) {
                cancelAlarm(context, alarm.timeString, alarm.id)
                scheduleAlarm(context, alarm.timeString, alarm.days, alarm.id)
            }
        }

        private fun calculateTriggerMillis(alarmTime: String, days: Set<Int> = emptySet()): Long {
            val parsed = try {
                LocalTime.parse(alarmTime, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) {
                return System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            }
            val time = parsed
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

            return calendar.timeInMillis
        }
    }
}
