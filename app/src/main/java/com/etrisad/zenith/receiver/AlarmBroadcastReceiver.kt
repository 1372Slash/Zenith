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
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                Log.d("AlarmReceiver", "onReceive: ACTION_FIRE_ALARM alarmTime=$alarmTime alarmId=$alarmId")
                // Alarm berbunyi lagi -> reminder "akan bunyi lagi" sudah basi, notif complete lama juga basi.
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
                Log.d("AlarmReceiver", "onReceive: ACTION_RE_TRIGGER alarmTime=$alarmTime attempt=$attempt alarmId=$alarmId isSnooze=$isSnooze")
                cancelSmartWakeReminder(context)
                cancelAutoRepeatComplete(context)
                cancelMissedAlarmNotification(context)
                recordAlarmFire(context, alarmTime)
                // Snooze adalah one-shot: JANGAN rangkai auto-repeat di belakangnya.
                // Dulu snooze memakai action yang sama sehingga tiap snooze memicu rantai
                // auto-repeat tak berujung (spam + bunyi ganda).
                if (!isSnooze) {
                    scheduleReTrigger(context, alarmTime, attempt, alarmId)
                }
                showAlarm(context, alarmTime, intent)
            }
            ACTION_CHECK_USAGE -> {
                val alarmTime = intent.getStringExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME) ?: "07:00"
                val isOnce = intent.getBooleanExtra(EXTRA_IS_ONCE, false)
                val attempt = intent.getIntExtra(EXTRA_RETRIGGER_COUNT, 0)
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
                val dismissAt = intent.getLongExtra(EXTRA_DISMISS_AT, 0L)
                val recentUsage = hasRecentUsage(context, dismissAt)
                Log.d("AlarmReceiver", "ACTION_CHECK_USAGE: alarmTime=$alarmTime, recentUsage=$recentUsage, isOnce=$isOnce attempt=$attempt alarmId=$alarmId dismissAt=$dismissAt")
                if (!recentUsage) {
                    scheduleReTrigger(context, alarmTime, attempt, alarmId)
                    // Biarkan reminder yang dipasang saat dismiss tetap hidup sampai
                    // re-trigger berikutnya berbunyi (lalu di-cancel di onReceive).
                } else {
                    Log.d("AlarmReceiver", "ACTION_CHECK_USAGE: user awake, auto-repeat completed")
                    cancelSmartWakeReminder(context)
                    sendAutoRepeatCompleteNotification(context, alarmTime)
                    if (isOnce) disableAlarm(context, alarmTime)
                }
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
        Log.d("AlarmReceiver", "showAlarm: trying startActivity")
        try {
            context.startActivity(activityIntent)
            Log.d("AlarmReceiver", "showAlarm: startActivity succeeded")
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "showAlarm: startActivity failed: ${e.message}")
        }

        fireAlarmNotification(context, alarmTime, snoozeCount, activityIntent, alarmId)
        // NOTE: scheduleWakeAlarm (+1s via AlarmManager.getActivity) sengaja dihapus.
        // Itu memakai requestCode tetap 3002 untuk semua alarm (tabrakan), memicu
        // onNewIntent ganda, dan di Android 12+ background activity launch diblokir
        // sehingga hanya menambah churn. Full-screen intent pada notif 2001 sudah
        // menjadi jalur wake yang benar.
    }

    private fun fireAlarmNotification(context: Context, alarmTime: String, snoozeCount: Int, activityIntent: Intent, alarmId: Long = 0L) {
        try {
            val channelId = CHANNEL_ID_FIRING
            val manager = context.getSystemService(NotificationManager::class.java)
            ensureFiringChannel(context)

            // Request code unik per alarm agar tap notif membuka jam yang benar.
            // Dulu requestCode=0 untuk semua alarm sehingga PendingIntent saling timpa.
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
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(false)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                builder.setTimeoutAfter(30_000L)
            }

            manager.notify(NOTIFICATION_ID_FIRING, builder.build())
            Log.d("AlarmReceiver", "fireAlarmNotification: notification posted")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "fireAlarmNotification failed: ${e.message}", e)
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
                // Relevan sesaat saja; jangan menumpuk di shade.
                builder.setTimeoutAfter(120_000L)
            }
            manager.notify(NOTIFICATION_ID_COMPLETE, builder.build())
        } catch (_: Exception) { }
    }

    /**
     * Smart-wake check yang diperbaiki.
     *
     * Masalah lama:
     * - Window selalu [now-60s, now] dihitung saat CHECK_USAGE berjalan, bukan sejak
     *   user men-dismiss. Event SystemUI/keyguard/launcher saat dismiss ikut kehitung.
     * - Cukup SATU event MOVE_TO_FOREGROUND apa pun (termasuk SystemUI) langsung
     *   dianggap "sudah memakai HP" -> false positive, alarm tidak mengulang padahal
     *   HP ditaruh lagi.
     * - Launcher justru di-skip -> false negative kalau user cuma buka home.
     *
     * Perbaikan:
     * - Window dijangkar ke [dismissAt, now] (dismissAt dikirim via intent).
     * - Menghitung TOTAL durasi foreground aplikasi non-sistem, bukan cuma ada/tidak.
     *   Butuh >= MIN_AWAKE_FOREGROUND_MS (15 detik) agar dianggap benar-benar bangun.
     *   Sekali buka-tutup SystemUI/keyguard (<2 detik) tidak cukup.
     * - Launcher DIHITUNG sebagai pemakaian (user membuka home = bangun).
     *   Yang dikecualikan hanya: paket sendiri, SystemUI, framework android, dan
     *   mesin TTS (TTS Zenith bisa memicu event TTS engine).
     * - Kalau izin PACKAGE_USAGE_STATS belum diberikan, fail-safe = anggap bangun
     *   (hentikan pengulangan) supaya tidak spam 12x dan user tidak terjebak.
     */
    internal fun hasRecentUsage(context: Context, dismissAtMs: Long): Boolean {
        return hasRecentUsageInternal(context, dismissAtMs, MIN_AWAKE_FOREGROUND_MS)
    }

    internal fun hasRecentUsageInternal(context: Context, dismissAtMs: Long, minForegroundMs: Long): Boolean {
        try {
            if (!hasUsageStatsPermission(context)) {
                Log.w("AlarmReceiver", "hasRecentUsage: no usage-stats permission, assuming awake (stop repeat to avoid spam)")
                return true
            }
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            // Jangkar ke momen dismiss; fallback 60s ke belakang untuk intent lama.
            val since = if (dismissAtMs > 0L) dismissAtMs.coerceAtMost(now) else now - 60_000L
            val events = usm.queryEvents(since, now)
            val ownPackage = context.packageName
            val ttsEnginePackage = try {
                val ttsIntent = Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                context.packageManager.resolveActivity(ttsIntent, 0)?.activityInfo?.packageName
            } catch (_: Exception) { null }
            val ignored = setOfNotNull(ownPackage, "com.android.systemui", "android", ttsEnginePackage)

            val activeStart = mutableMapOf<String, Long>()
            var totalForegroundMs = 0L
            var countedPackages = mutableSetOf<String>()
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (ignored.contains(event.packageName)) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // Hanya catat start pertama per paket agar pasangan akurat.
                        if (!activeStart.containsKey(event.packageName)) {
                            activeStart[event.packageName] = event.timeStamp
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = activeStart.remove(event.packageName) ?: continue
                        val dur = (event.timeStamp - start).coerceAtLeast(0L)
                        // Abaikan blip <1 detik (transisi SystemUI/keyguard).
                        if (dur >= 1_000L) {
                            totalForegroundMs += dur
                            countedPackages.add(event.packageName)
                        }
                    }
                }
            }
            // Aplikasi yang masih foreground saat check berjalan.
            for ((pkg, start) in activeStart) {
                val dur = (now - start).coerceAtLeast(0L)
                if (dur >= 1_000L) {
                    totalForegroundMs += dur
                    countedPackages.add(pkg)
                }
            }
            val awake = totalForegroundMs >= minForegroundMs
            Log.d("AlarmReceiver", "hasRecentUsage: window=[$since, $now] total=${totalForegroundMs}ms min=${minForegroundMs}ms awake=$awake pkgs=$countedPackages")
            return awake
        } catch (e: SecurityException) {
            Log.w("AlarmReceiver", "hasRecentUsage permission denied: ${e.message}, assuming awake")
            return true
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "hasRecentUsage check failed: ${e.message}, assuming awake to avoid repeat-spam")
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
            // Kalau tidak bisa dicek, biarkan queryEvents yang menentukan (fail-safe di atas).
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

        // Satu ID per jenis notif -> tidak menumpuk, tiap jenis saling menggantikan.
        const val NOTIFICATION_ID_FIRING = 2001
        const val NOTIFICATION_ID_COMPLETE = 2002
        const val NOTIFICATION_ID_MISSED = 2003
        const val NOTIFICATION_ID_SMART_WAKE_REMINDER = 2004

        // Channel dipisah: firing/missed = HIGH (boleh bunyi + full-screen),
        // info (reminder/complete) = LOW (silent, tidak heads-up, tidak ramai).
        // Dulu reminder+complete menumpang channel HIGH sehingga notif info ikut
        // bunyi/heads-up dan channel LOW tak pernah terpakai (dibuat setelah HIGH ada).
        const val CHANNEL_ID_FIRING = "zenith_alarm_channel"
        const val CHANNEL_ID_INFO = "zenith_alarm_info_channel"

        // Ambang "benar-benar memakai HP": total foreground >= 15 detik sejak dismiss.
        internal const val MIN_AWAKE_FOREGROUND_MS = 15_000L
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

        private fun requestCodeForId(base: Int, alarmTime: String, alarmId: Long): Int {
            val parts = alarmTime.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
            // Aman dari overflow: id dipadatkan ke 0..99999 lalu digeser 3000.
            // Dulu (id % 200000) * 2400 bisa mendekati/melampaui batas int dan
            // menabrak base lain untuk id besar.
            val idPart = ((alarmId % 100000L + 100000L) % 100000L).toInt() * 3000
            return base + idPart + hour * 100 + minute
        }

        private fun notificationRequestCode(alarmTime: String, alarmId: Long, salt: Int): Int {
            val base = (alarmTime.hashCode() and 0x7fffffff) % 10000
            val idPart = (((alarmId % 1000L + 1000L) % 1000L).toInt() * 100) % 100000
            return 20000 + salt * 100000 + base + idPart
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

        fun scheduleAlarm(context: Context, alarmTime: String, days: Set<Int> = emptySet(), alarmId: Long = 0L) {
            val (hour, minute) = alarmTime.split(":").map { it.toInt() }
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
            Log.d("AlarmReceiver", "scheduleAlarm: $alarmTime at $triggerAtMillis (now=${System.currentTimeMillis()})")

            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        fun cancelAlarm(context: Context) {
            cancelAlarm(context, null)
        }

        fun cancelAlarm(context: Context, alarmTime: String?, alarmId: Long = 0L) {
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
                if (alarmId > 0L) {
                    val idIntent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                        action = ACTION_FIRE_ALARM
                    }
                    val idPendingIntent = PendingIntent.getBroadcast(
                        context, requestCodeForId(REQUEST_CODE_ALARM_BASE, alarmTime, alarmId), idIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(idPendingIntent)
                    idPendingIntent.cancel()
                    // Migrasi: batalkan juga kode lama ((id % 200000) * 2400) agar tidak
                    // ada exact alarm hantu yang lolos setelah update aplikasi.
                    try {
                        val parts = alarmTime.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val legacyIdCode = REQUEST_CODE_ALARM_BASE + h * 100 + m +
                            ((alarmId % 200000L) * 2400L).toInt()
                        val legacyPi = PendingIntent.getBroadcast(
                            context, legacyIdCode, idIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(legacyPi)
                        legacyPi.cancel()
                    } catch (_: Exception) { }
                }
                cancelReTrigger(context, alarmTime, alarmId)
                cancelUsageCheck(context, alarmTime, alarmId)
                // Alarm dimatikan/dihapus -> reminder "akan bunyi lagi" wajib hilang.
                // Dulu notif 2004 dibiarkan basi menumpuk di shade.
                cancelSmartWakeReminder(context)
                cancelFiringNotification(context)
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
            dismissAtMs: Long = System.currentTimeMillis()
        ) {
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
                putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_IS_ONCE, isOnce)
                putExtra(EXTRA_RETRIGGER_COUNT, retriggerAttempt)
                putExtra(EXTRA_DISMISS_AT, dismissAtMs)
                if (alarmId > 0L) putExtra(EXTRA_ALARM_ID, alarmId)
            }

            // Id-aware agar dua alarm beda tidak saling menimpa usage-check.
            // Varian legacy tetap dijadwalkan ulang? Tidak — cukup id-aware + legacy
            // dibatalkan dulu supaya tidak ada check ganda basi.
            cancelUsageCheck(context, alarmTime, alarmId)
            val requestCode = if (alarmId > 0L) requestCodeForId(REQUEST_CODE_CHECK_BASE, alarmTime, alarmId)
            else requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAtMillis = System.currentTimeMillis() + 60_000L
            Log.d("AlarmReceiver", "scheduleUsageCheck: $alarmTime +60s (requestCode=$requestCode dismissAt=$dismissAtMs)")
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

            val triggerAtMillis = System.currentTimeMillis() + (snoozeDurationMinutes * 60_000L)
            setExactAlarm(context, triggerAtMillis, pendingIntent)
            // Snooze menggantikan smart-repeat: bersihkan rantai + reminder basi.
            cancelReTrigger(context, alarmTime, alarmId)
            cancelUsageCheck(context, alarmTime, alarmId)
            cancelSmartWakeReminder(context)
            cancelFiringNotification(context)
        }

        @JvmOverloads
        fun cancelUsageCheck(context: Context, alarmTime: String, alarmId: Long = 0L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_CHECK_USAGE
            }
            val requestCode = requestCodeFor(REQUEST_CODE_CHECK_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            if (alarmId > 0L) {
                val idPi = PendingIntent.getBroadcast(
                    context, requestCodeForId(REQUEST_CODE_CHECK_BASE, alarmTime, alarmId), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(idPi)
                idPi.cancel()
            }
        }

        const val EXTRA_RETRIGGER_COUNT = "retrigger_count"
        private const val MAX_RE_TRIGGER_ATTEMPTS = 12

        @JvmOverloads
        fun scheduleReTrigger(context: Context, alarmTime: String, attempt: Int = 0, alarmId: Long = 0L) {
            if (attempt >= MAX_RE_TRIGGER_ATTEMPTS) {
                Log.d("AlarmReceiver", "scheduleReTrigger: max attempts reached for $alarmTime, stopping chain")
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
            Log.d("AlarmReceiver", "scheduleReTrigger: $alarmTime +5min (requestCode=$requestCode attempt=${attempt + 1})")
            setExactAlarm(context, triggerAtMillis, pendingIntent)
        }

        @JvmOverloads
        fun cancelReTrigger(context: Context, alarmTime: String, alarmId: Long = 0L) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
                action = ACTION_RE_TRIGGER
            }
            val requestCode = requestCodeFor(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            if (alarmId > 0L) {
                val idPi = PendingIntent.getBroadcast(
                    context, requestCodeForId(REQUEST_CODE_RE_TRIGGER_BASE, alarmTime, alarmId), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(idPi)
                idPi.cancel()
            }
            // Snooze memakai action yang sama dengan base berbeda; sapu juga agar
            // tidak ada snooze basi yang ikut berbunyi setelah repeat dibatalkan.
            for (i in 0..50) {
                try {
                    val snoozeLegacy = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_SNOOZE_BASE + (alarmTime.hashCode() and 0x7fffffff) % 1000 + i,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(snoozeLegacy)
                    snoozeLegacy.cancel()
                } catch (_: Exception) { }
                if (alarmId > 0L) {
                    try {
                        val snoozeId = PendingIntent.getBroadcast(
                            context,
                            requestCodeForId(REQUEST_CODE_SNOOZE_BASE, alarmTime, alarmId) + i,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(snoozeId)
                        snoozeId.cancel()
                    } catch (_: Exception) { }
                }
            }
        }

        private fun setExactAlarm(context: Context, triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (e2: Exception) {
                Log.e("AlarmReceiver", "setExactAndAllowWhileIdle failed: ${e2.message}")
                try {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (e3: Exception) {
                    Log.e("AlarmReceiver", "setAndAllowWhileIdle failed: ${e3.message}")
                }
            }
        }

        fun disableAlarm(context: Context, alarmTime: String) {
            try {
                val app = context.applicationContext as ZenithApplication
                var alarmId = 0L
                runBlocking {
                    val repo = app.userPreferencesRepository
                    val prefs = repo.userPreferencesFlow.first()
                    val alarms = repo.parseAlarms(prefs.alarmsJson)
                    val alarm = alarms.find { it.timeString == alarmTime } ?: return@runBlocking
                    alarmId = alarm.id
                    repo.updateAlarm(alarm.copy(enabled = false))
                }
                cancelAlarm(context, alarmTime, alarmId)
                Log.d("AlarmReceiver", "disableAlarm: $alarmTime disabled")
            } catch (e: Exception) {
                Log.w("AlarmReceiver", "disableAlarm failed: ${e.message}")
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
                if (manager.getNotificationChannel(CHANNEL_ID_FIRING) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID_FIRING, "Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                            description = "Alarm alerts"
                        }
                    )
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
        fun showAutoRepeatReminderNotification(context: Context, alarmTime: String, alarmId: Long = 0L) {
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
                    runBlocking {
                        val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                        app.userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                            .find { it.timeString == alarmTime }?.name
                    }
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
                Log.d("AlarmReceiver", "showAutoRepeatReminderNotification: posted for $alarmTime, next ~$nextTime")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "showAutoRepeatReminderNotification failed: ${e.message}", e)
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