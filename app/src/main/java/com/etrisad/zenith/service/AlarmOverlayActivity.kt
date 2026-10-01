package com.etrisad.zenith.service

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.receiver.AlarmBroadcastReceiver
import com.etrisad.zenith.ui.components.AlarmOverlayContent
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmOverlayActivity : ComponentActivity() {

    private var playbackService: AlarmPlaybackService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            playbackService = (service as AlarmPlaybackService.LocalBinder).getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            bound = false
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockRenewalJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var isAlarmActive = false

    private var alarmTime: String = "07:00"
    private var snoozeCount: Int = 0
    private var testMathChallenge: Boolean = false
    private var testGradualVolume: Boolean = false
    private var restartKey by mutableIntStateOf(0)

    private var wakeUpTrackingActive by mutableStateOf(false)
    private var wakeUpStartTime by mutableLongStateOf(0L)
    private var wakeUpAccumulatedSeconds by mutableIntStateOf(0)
    private var wakeUpComplete by mutableStateOf(false)
    private var wakeUpNeedsPermission by mutableStateOf(false)
    private var wakeUpJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d("ZenithAlarm", "AlarmOverlayActivity.onCreate STARTED")
        isShowing = true
        isAlarmActive = true

        alarmTime = intent?.getStringExtra(EXTRA_ALARM_TIME) ?: "07:00"
        Log.d("ZenithAlarm", "AlarmOverlayActivity.onCreate alarmTime=$alarmTime snoozeCount=$snoozeCount")
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
        testMathChallenge = intent?.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false) ?: false
        testGradualVolume = intent?.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false) ?: false

        AlarmPlaybackService.start(this, alarmTime)
        bindService(Intent(this, AlarmPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Zenith:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
        startWakeLockRenewal()

        setContent {
            key(restartKey) {
                val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
                val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
                    initial = UserPreferences()
                )

                val currentAlarm = remember(userPreferences.alarmsJson) {
                    userPreferencesRepository.parseAlarms(userPreferences.alarmsJson)
                        .find { it.timeString == alarmTime }
                }

                // Hanya paket yang masih terinstal + bisa dibuka yang dihitung.
                // Dulu paket yang di-uninstall ikut tersimpan sehingga progress
                // stuck selamanya (soft-lock saat alarm berbunyi).
                val wakeUpAppPackageNames = remember(currentAlarm) {
                    val raw = currentAlarm?.wakeUpAppPackageNames ?: emptyList()
                    filterLaunchablePackages(raw)
                }

                val wakeUpAppNames = remember(wakeUpAppPackageNames) {
                    wakeUpAppPackageNames.associateWith { pkg ->
                        try {
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(pkg, 0)
                            ).toString()
                        } catch (_: Exception) { pkg }
                    }
                }

                val wakeUpAppDurationSeconds = remember(currentAlarm, wakeUpAppPackageNames) {
                    if (wakeUpAppPackageNames.isEmpty()) 0
                    else (currentAlarm?.wakeUpAppDurationSeconds ?: 120).coerceIn(5, 600)
                }

                val darkTheme = when (userPreferences.themeConfig) {
                    ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    ThemeConfig.LIGHT -> false
                    ThemeConfig.DARK -> true
                }

                // Wake-up verification harus mulai menghitung sejak overlay tampil,
                // bukan hanya setelah user menekan tombol di sheet. Dulu tracking baru
                // jalan kalau dibuka via tombol, sehingga pemakaian via launcher tidak kehitung.
                androidx.compose.runtime.LaunchedEffect(wakeUpAppPackageNames, alarmTime) {
                    wakeUpNeedsPermission = wakeUpAppPackageNames.isNotEmpty() && !hasUsageAccess()
                    if (wakeUpAppPackageNames.isNotEmpty() && !wakeUpTrackingActive && !wakeUpComplete) {
                        wakeUpStartTime = System.currentTimeMillis()
                        wakeUpTrackingActive = true
                        startWakeUpTracking(wakeUpAppPackageNames, wakeUpAppDurationSeconds)
                    }
                }

                ZenithTheme(
                    darkTheme = darkTheme,
                    dynamicColor = userPreferences.dynamicColor,
                    fontOption = userPreferences.fontOption,
                    expressiveColors = userPreferences.expressiveColors,
                    gsFlexSettings = userPreferences.gsFlexSettings
                ) {
                    AlarmOverlayContent(
                        alarmTime = alarmTime,
                        alarmName = currentAlarm?.name ?: "Alarm",
                        snoozeDurationMinutes = currentAlarm?.snoozeDurationMinutes ?: 5,
                        onDismiss = {
                            dismissWithAutoRepeat()
                        },
                        // SENGAJA sama dengan onDismiss: user setengah sadar asal pencet
                        // tombol tercepat (biasanya Stop merah). Kalau Stop mematikan
                        // total, smart-repeat tidak pernah jalan dan app yang disalahkan.
                        // Smart yang memutuskan: dipakai -> setop, ditaruh -> ulangi.
                        onStopAlarm = {
                            dismissWithAutoRepeat()
                        },
                        onSnooze = {
                            snooze()
                        },
                        snoozeCount = snoozeCount,
                        snoozeMaxCount = currentAlarm?.snoozeMaxCount ?: 3,
                        mathChallengeEnabled = if (testMathChallenge) testMathChallenge
                        else currentAlarm?.mathChallengeEnabled ?: false,
                        wakeUpAppPackageNames = wakeUpAppPackageNames,
                        wakeUpAppNames = wakeUpAppNames,
                        wakeUpAppDurationSeconds = wakeUpAppDurationSeconds,
                        wakeUpAccumulatedSeconds = wakeUpAccumulatedSeconds,
                        wakeUpComplete = wakeUpComplete,
                        onWakeUpAppOpened = { pkg ->
                            handleWakeUpAppOpened(pkg, wakeUpAppPackageNames, wakeUpAppDurationSeconds)
                        },
                        onWakeUpDismiss = {
                            dismissAfterWakeUpVerified()
                        },
                        wakeUpNeedsPermission = wakeUpNeedsPermission,
                        onOpenUsageSettings = {
                            openUsageAccessSettings()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User bisa grant Usage Access dari sheet lalu kembali: segarkan flag
        // agar warning hilang dan tracking yang sudah jalan mulai menghitung.
        if (wakeUpNeedsPermission && hasUsageAccess()) {
            wakeUpNeedsPermission = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newAlarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: return

        if (newAlarmTime != alarmTime) {
            stopWakeUpTracking()
        }
        alarmTime = newAlarmTime
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        testMathChallenge = intent.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false)
        testGradualVolume = intent.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false)
        restartKey++

        isAlarmActive = true
        AlarmPlaybackService.start(this, alarmTime)
    }

    private fun dismissWithAutoRepeat() {
        Log.d("ZenithAlarm", "dismissWithAutoRepeat: alarmTime=$alarmTime")
        val dismissAt = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
            val currentAlarm = alarms.find {
                it.timeString == alarmTime
            }
            val alarmId = currentAlarm?.id ?: 0L

            val autoRepeat = currentAlarm?.autoRepeatEnabled ?: prefs.alarmAutoRepeatEnabled
            val isOnce = currentAlarm?.days?.isEmpty() ?: true
            val isRecurring = !isOnce
            Log.d("ZenithAlarm", "dismissWithAutoRepeat: autoRepeat=$autoRepeat isOnce=$isOnce alarmId=$alarmId")

            AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, alarmTime)
            AlarmBroadcastReceiver.cancelFiringNotification(this@AlarmOverlayActivity)
            AlarmBroadcastReceiver.cancelReTrigger(this@AlarmOverlayActivity, alarmTime, alarmId)

            // Alarm berulang WAJIB dijadwalkan ulang setiap habis bunyi (one-shot exact).
            // Dulu ini hanya terjadi bila autoRepeat=true, sehingga alarm berulang
            // non-smart hanya berbunyi sekali lalu mati selamanya.
            if (isRecurring && currentAlarm != null) {
                AlarmBroadcastReceiver.scheduleAlarm(
                    this@AlarmOverlayActivity, currentAlarm.timeString, currentAlarm.days, currentAlarm.id
                )
            }

            if (autoRepeat) {
                Log.d("ZenithAlarm", "dismissWithAutoRepeat: scheduling usage check")
                AlarmBroadcastReceiver.scheduleUsageCheck(
                    this@AlarmOverlayActivity, alarmTime, isOnce, 0, alarmId, dismissAt
                )
                AlarmBroadcastReceiver.showAutoRepeatReminderNotification(
                    this@AlarmOverlayActivity, alarmTime, alarmId
                )
            } else {
                Log.d("ZenithAlarm", "dismissWithAutoRepeat: autoRepeat disabled, clearing stale smart state")
                AlarmBroadcastReceiver.cancelUsageCheck(this@AlarmOverlayActivity, alarmTime, alarmId)
                AlarmBroadcastReceiver.cancelSmartWakeReminder(this@AlarmOverlayActivity)
                if (isOnce && currentAlarm != null) {
                    // Once tanpa smart-repeat: matikan agar tidak nyangkut enabled tanpa jadwal.
                    try {
                        userPreferencesRepository.updateAlarm(currentAlarm.copy(enabled = false))
                    } catch (_: Exception) { }
                    AlarmBroadcastReceiver.cancelAlarm(this@AlarmOverlayActivity, alarmTime, alarmId)
                }
            }

            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }

    /**
     * Wake-up verification selesai = user terbukti bangun, jadi tidak perlu
     * smart-repeat. Dulu jalur ini hanya stop tanpa reschedule sehingga alarm
     * berulang berikutnya hilang.
     */
    private fun dismissAfterWakeUpVerified() {
        Log.d("ZenithAlarm", "dismissAfterWakeUpVerified: alarmTime=$alarmTime")
        lifecycleScope.launch(Dispatchers.IO) {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val alarms = userPreferencesRepository.parseAlarms(
                userPreferencesRepository.userPreferencesFlow.first().alarmsJson
            )
            val currentAlarm = alarms.find { it.timeString == alarmTime }
            val alarmId = currentAlarm?.id ?: 0L
            val isOnce = currentAlarm?.days?.isEmpty() ?: true

            AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, alarmTime)
            AlarmBroadcastReceiver.cancelSmartChain(this@AlarmOverlayActivity, alarmTime, alarmId)

            if (!isOnce && currentAlarm != null) {
                AlarmBroadcastReceiver.scheduleAlarm(
                    this@AlarmOverlayActivity, currentAlarm.timeString, currentAlarm.days, currentAlarm.id
                )
            } else if (isOnce && currentAlarm != null) {
                try {
                    userPreferencesRepository.updateAlarm(currentAlarm.copy(enabled = false))
                } catch (_: Exception) { }
                AlarmBroadcastReceiver.cancelAlarm(this@AlarmOverlayActivity, alarmTime, alarmId)
            }

            withContext(Dispatchers.Main) {
                stopWakeUpTracking()
                stopAlarmAndFinish()
            }
        }
    }

    private fun snooze() {
        Log.d("ZenithAlarm", "snooze: alarmTime=$alarmTime snoozeCount=$snoozeCount")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                val alarm = alarms.find { it.timeString == alarmTime }
                val alarmId = alarm?.id ?: 0L
                val snoozeDuration = alarm?.snoozeDurationMinutes ?: 5
                val snoozeMax = alarm?.snoozeMaxCount ?: 3

                AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, alarmTime)

                if (snoozeMax == Int.MAX_VALUE || snoozeCount < snoozeMax) {
                    Log.d("ZenithAlarm", "snooze: scheduling snooze alarm duration=$snoozeDuration min")
                    AlarmBroadcastReceiver.scheduleSnoozeAlarm(
                        this@AlarmOverlayActivity,
                        alarmTime,
                        snoozeDuration,
                        snoozeCount + 1,
                        alarmId
                    )
                } else {
                    // Kuota snooze habis: jangan gantung; kembalikan ke smart chain biasa.
                    AlarmBroadcastReceiver.cancelSmartChain(this@AlarmOverlayActivity, alarmTime, alarmId)
                }
            } catch (_: Exception) { }

            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }

    private fun startWakeLockRenewal() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = lifecycleScope.launch {
            while (isAlarmActive) {
                delay(8 * 60 * 1000L)
                if (!isAlarmActive) break
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Zenith:AlarmWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L)
                Log.d("ZenithAlarm", "Wake lock renewed")
            }
        }
    }

    private fun stopAlarmAndFinish() {
        isAlarmActive = false
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        try {
            AlarmBroadcastReceiver.cancelFiringNotification(this)
        } catch (_: Exception) { }
        if (bound) {
            playbackService?.stopPlayback()
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        } else {
            playbackService?.stopPlayback()
            // Service mungkin belum sempat bind (mis. overlay dibuka via notif);
            // pastikan suara berhenti dengan stopService eksplisit.
            try { stopService(Intent(this, AlarmPlaybackService::class.java)) } catch (_: Exception) { }
        }
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) { }
        }
        wakeLock = null
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        Log.d("ZenithAlarm", "AlarmOverlayActivity.onDestroy")
        isAlarmActive = false
        isShowing = false
        stopWakeUpTracking()

        if (bound) {
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        }
        super.onDestroy()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun hasUsageAccess(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            true
        }
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    private fun filterLaunchablePackages(packages: List<String>): List<String> {
        if (packages.isEmpty()) return emptyList()
        return packages.filter { pkg ->
            try {
                packageManager.getApplicationInfo(pkg, 0)
                packageManager.getLaunchIntentForPackage(pkg) != null
            } catch (_: Exception) {
                Log.w("ZenithAlarm", "Wake-up app $pkg tidak valid/terinstal, dilewati")
                false
            }
        }
    }

    private fun handleWakeUpAppOpened(packageName: String, appPackages: List<String>, durationSeconds: Int) {
        val intent = try {
            packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) { null }
        if (intent != null) {
            if (!wakeUpTrackingActive) {
                wakeUpStartTime = System.currentTimeMillis()
                wakeUpTrackingActive = true
                startWakeUpTracking(appPackages, durationSeconds)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w("ZenithAlarm", "Gagal membuka $packageName: ${e.message}")
                android.widget.Toast.makeText(this, "Tidak dapat membuka aplikasi", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            // Paket hilang di antara setting dan firing: jangan diam; arahkan ke
            // info aplikasi agar user paham, bukan tombol mati.
            Log.w("ZenithAlarm", "Wake-up app $packageName tidak bisa dibuka")
            android.widget.Toast.makeText(this, "Aplikasi tidak dapat dibuka", android.widget.Toast.LENGTH_SHORT).show()
            try {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    private fun startWakeUpTracking(appPackages: List<String> = emptyList(), durationSeconds: Int = 120) {
        val packages = appPackages.ifEmpty {
            val repo = (application as ZenithApplication).userPreferencesRepository
            try {
                val prefs = kotlinx.coroutines.runBlocking { repo.userPreferencesFlow.first() }
                repo.parseAlarms(prefs.alarmsJson)
                    .find { it.timeString == alarmTime }
                    ?.wakeUpAppPackageNames ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
        val duration = if (durationSeconds != 120 || appPackages.isNotEmpty()) durationSeconds else {
            val repo = (application as ZenithApplication).userPreferencesRepository
            try {
                val prefs = kotlinx.coroutines.runBlocking { repo.userPreferencesFlow.first() }
                repo.parseAlarms(prefs.alarmsJson)
                    .find { it.timeString == alarmTime }
                    ?.wakeUpAppDurationSeconds ?: 120
            } catch (_: Exception) { 120 }
        }
        if (packages.isEmpty() || duration <= 0) {
            // Konfigurasi tidak valid (mis. semua app di-uninstall): jangan gate.
            withContextForMainSync {
                wakeUpComplete = true
                wakeUpTrackingActive = false
            }
            return
        }
        wakeUpJob?.cancel()
        wakeUpJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(3000)
                if (!hasUsageAccess()) {
                    withContext(Dispatchers.Main) { wakeUpNeedsPermission = true }
                    continue
                }
                val accumulated = getAccumulatedForegroundMs(
                    packages.toSet(),
                    wakeUpStartTime
                )
                withContext(Dispatchers.Main) {
                    wakeUpAccumulatedSeconds = (accumulated / 1000).toInt()
                    if (wakeUpAccumulatedSeconds >= duration) {
                        wakeUpComplete = true
                        wakeUpTrackingActive = false
                        wakeUpNeedsPermission = false
                        wakeUpJob?.cancel()
                        return@withContext
                    }
                }
            }
        }
    }

    private fun withContextForMainSync(block: () -> Unit) {
        // Dipanggil dari LaunchedEffect (Main) maupun IO; tulis state di Main.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            lifecycleScope.launch(Dispatchers.Main) { block() }
        }
    }

    private fun stopWakeUpTracking() {
        wakeUpJob?.cancel()
        wakeUpJob = null
        wakeUpTrackingActive = false
        wakeUpAccumulatedSeconds = 0
        wakeUpComplete = false
        wakeUpStartTime = 0L
    }

    private fun getAccumulatedForegroundMs(packageNames: Set<String>, sinceMs: Long): Long {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(sinceMs, System.currentTimeMillis())
            val activeStart = mutableMapOf<String, Long>()
            var total = 0L
            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                if (!packageNames.contains(event.packageName)) continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (!activeStart.containsKey(event.packageName)) {
                            activeStart[event.packageName] = event.timeStamp
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val start = activeStart.remove(event.packageName) ?: continue
                        total += (event.timeStamp - start).coerceAtLeast(0L)
                    }
                }
            }
            val now = System.currentTimeMillis()
            for (start in activeStart.values) {
                total += now - start
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
        const val EXTRA_TEST_MATH_CHALLENGE = "extra_test_math_challenge"
        const val EXTRA_TEST_GRADUAL_VOLUME = "extra_test_gradual_volume"

        @Volatile
        var isShowing = false

        fun start(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmOverlayActivity::class.java).apply {
                putExtra(EXTRA_ALARM_TIME, alarmTime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }
    }
}
