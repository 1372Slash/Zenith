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
import com.etrisad.zenith.util.AlarmChainHelper
import com.etrisad.zenith.ui.components.AlarmOverlayContent
import com.etrisad.zenith.ui.components.pausepoint.AlarmDismissTasks
import com.etrisad.zenith.ui.components.pausepoint.PausePointEngine
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
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
    private var autoDismissJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var isAlarmActive = false

    private var alarmTime: String = "07:00"
    private var alarmId: Long = 0L
    private var snoozeCount: Int = 0
    private var testMathChallenge: Boolean = false
    private var testAlarmJson: String? = null
    private var testGradualVolume: Boolean = false
    private var restartKey by mutableIntStateOf(0)

    /**
     * Progres wake-up per alarm. Satu-satunya state yang bertahan saat alarm
     * lain preempt overlay (singleInstance): offsetMs menampung akumulasi yang
     * sudah dikunci dari window sebelumnya, windowStartMs menandai awal window
     * yang sedang berjalan (0 = tidak ada window terbuka), packages dipakai
     * untuk mengunci window yang tertunda saat preempt terjadi.
     */
    private data class WakeUpSlot(
        var offsetMs: Long = 0L,
        var windowStartMs: Long = 0L,
        var packages: List<String> = emptyList(),
        var complete: Boolean = false
    )
    private val wakeUpSlots = mutableMapOf<String, WakeUpSlot>()

    private var wakeUpTrackingActive by mutableStateOf(false)
    private var wakeUpStartTime by mutableLongStateOf(0L)
    private var wakeUpAccumulatedSeconds by mutableIntStateOf(0)
    private var wakeUpComplete by mutableStateOf(false)
    private var wakeUpNeedsPermission by mutableStateOf(false)
    private var wakeUpJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        isShowing = true
        isAlarmActive = true

        alarmTime = intent?.getStringExtra(EXTRA_ALARM_TIME) ?: "07:00"
        alarmId = intent?.getLongExtra(AlarmBroadcastReceiver.EXTRA_ALARM_ID, 0L) ?: 0L
        showingAlarmKey = AlarmChainHelper.alarmKey(alarmId, alarmTime)
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
        testMathChallenge = intent?.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false) ?: false
        testAlarmJson = intent?.getStringExtra(EXTRA_TEST_ALARM_JSON)
        testGradualVolume = intent?.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false) ?: false

        val testJson = testAlarmJson
        if (testJson != null) AlarmPlaybackService.startTest(this, alarmTime, testJson)
        else AlarmPlaybackService.start(this, alarmTime, alarmId)
        bindService(Intent(this, AlarmPlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "Zenith:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
        startWakeLockRenewal()
        scheduleAutoDismiss()

        setContent {
            key(restartKey) {
                val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
                val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
                    initial = UserPreferences()
                )

                val testAlarm = remember(testAlarmJson) {
                    testAlarmJson?.let { json ->
                        runCatching { userPreferencesRepository.parseAlarms(json).firstOrNull() }.getOrNull()
                    }
                }

                val currentAlarm = remember(userPreferences.alarmsJson, testAlarm, alarmId, alarmTime) {
                    testAlarm ?: AlarmChainHelper.findAlarm(
                        userPreferencesRepository.parseAlarms(userPreferences.alarmsJson),
                        alarmId, alarmTime
                    )
                }
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
                val dismissTaskTypes = remember(currentAlarm, testMathChallenge) {
                    if (testMathChallenge) setOf(PausePointTaskType.MATH)
                    else AlarmDismissTasks.effectiveTypes(
                        currentAlarm?.dismissTaskTypeNames ?: emptySet(),
                        currentAlarm?.mathChallengeEnabled ?: false
                    )
                }
                val dismissTask = remember(dismissTaskTypes, alarmTime, userPreferences.pausePointConfig) {
                    if (dismissTaskTypes.isEmpty()) null
                    else PausePointEngine.generateTask(
                        enabledTypes = dismissTaskTypes,
                        config = userPreferences.pausePointConfig,
                        qrCodes = userPreferences.pausePointQrCodes,
                        nfcTagIds = userPreferences.pausePointNfcTagIds
                    )
                }

                val darkTheme = when (userPreferences.themeConfig) {
                    ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    ThemeConfig.LIGHT -> false
                    ThemeConfig.DARK -> true
                }
                androidx.compose.runtime.LaunchedEffect(wakeUpAppPackageNames, alarmTime, alarmId) {
                    wakeUpNeedsPermission = wakeUpAppPackageNames.isNotEmpty() && !hasUsageAccess()
                    if (wakeUpAppPackageNames.isNotEmpty() && !wakeUpTrackingActive && !wakeUpComplete) {
                        resumeWakeUpTracking(wakeUpAppPackageNames, wakeUpAppDurationSeconds)
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
                        onStopAlarm = {
                            dismissWithAutoRepeat()
                        },
                        onSnooze = {
                            snooze()
                        },
                        snoozeCount = snoozeCount,
                        snoozeMaxCount = currentAlarm?.snoozeMaxCount ?: 3,
                        dismissTask = dismissTask,
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
        if (wakeUpNeedsPermission && hasUsageAccess()) {
            wakeUpNeedsPermission = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newAlarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: return
        val newAlarmId = intent.getLongExtra(AlarmBroadcastReceiver.EXTRA_ALARM_ID, 0L)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val newKey = AlarmChainHelper.alarmKey(newAlarmId, newAlarmTime)
        if (newKey != AlarmChainHelper.alarmKey(alarmId, alarmTime)) {
            // Alarm lain preempt overlay: kunci window wake-up yang berjalan ke
            // offset agar progres tidak hilang, slot dipertahankan per alarm.
            suspendWakeUpForSwitch()
        }
        alarmTime = newAlarmTime
        alarmId = newAlarmId
        showingAlarmKey = newKey
        isShowing = true
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        testMathChallenge = intent.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false)
        testAlarmJson = intent.getStringExtra(EXTRA_TEST_ALARM_JSON)
        testGradualVolume = intent.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false)
        // Prefill UI dari slot yang dipertahankan agar progres tidak berkedip ke nol;
        // LaunchedEffect setelah recompose akan me-resume window tracking-nya.
        val slot = wakeUpSlots[newKey]
        wakeUpAccumulatedSeconds = ((slot?.offsetMs ?: 0L) / 1000).toInt()
        wakeUpComplete = slot?.complete ?: false
        wakeUpTrackingActive = false
        restartKey++

        isAlarmActive = true
        scheduleAutoDismiss()
        val retestJson = testAlarmJson
        if (retestJson != null) AlarmPlaybackService.startTest(this, alarmTime, retestJson)
        else AlarmPlaybackService.start(this, alarmTime, alarmId)
    }

    private fun dismissWithAutoRepeat() {
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
        val dismissAt = System.currentTimeMillis()
        val wantId = alarmId
        val wantTime = alarmTime
        lifecycleScope.launch(Dispatchers.IO) {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
            val currentAlarm = AlarmChainHelper.findAlarm(alarms, wantId, wantTime)
            val resolvedId = currentAlarm?.id ?: wantId

            val autoRepeat = currentAlarm?.autoRepeatEnabled ?: prefs.alarmAutoRepeatEnabled
            val isOnce = currentAlarm?.days?.isEmpty() ?: true
            val isRecurring = !isOnce

            AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, wantTime)
            AlarmBroadcastReceiver.cancelFiringNotification(this@AlarmOverlayActivity)
            AlarmBroadcastReceiver.cancelReTrigger(this@AlarmOverlayActivity, wantTime, resolvedId)
            if (isRecurring && currentAlarm != null) {
                AlarmBroadcastReceiver.scheduleAlarm(
                    this@AlarmOverlayActivity, currentAlarm.timeString, currentAlarm.days, currentAlarm.id
                )
            }

            if (autoRepeat) {
                AlarmBroadcastReceiver.scheduleUsageCheck(
                    this@AlarmOverlayActivity, wantTime, isOnce, 0, resolvedId, dismissAt
                )
                AlarmBroadcastReceiver.showAutoRepeatReminderNotification(
                    this@AlarmOverlayActivity, wantTime, resolvedId
                )
            } else {
                AlarmBroadcastReceiver.cancelUsageCheck(this@AlarmOverlayActivity, wantTime, resolvedId)
                AlarmBroadcastReceiver.cancelSmartWakeReminder(this@AlarmOverlayActivity)
                if (isOnce && currentAlarm != null) {
                    try {
                        userPreferencesRepository.updateAlarm(currentAlarm.copy(enabled = false))
                    } catch (_: Exception) { }
                    AlarmBroadcastReceiver.cancelAlarm(this@AlarmOverlayActivity, wantTime, resolvedId)
                }
            }

            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }
    private fun dismissAfterWakeUpVerified() {
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
        val wantId = alarmId
        val wantTime = alarmTime
        lifecycleScope.launch(Dispatchers.IO) {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val alarms = userPreferencesRepository.parseAlarms(
                userPreferencesRepository.userPreferencesFlow.first().alarmsJson
            )
            val currentAlarm = AlarmChainHelper.findAlarm(alarms, wantId, wantTime)
            val resolvedId = currentAlarm?.id ?: wantId
            val isOnce = currentAlarm?.days?.isEmpty() ?: true

            AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, wantTime)
            AlarmBroadcastReceiver.cancelSmartChain(this@AlarmOverlayActivity, wantTime, resolvedId)

            if (!isOnce && currentAlarm != null) {
                AlarmBroadcastReceiver.scheduleAlarm(
                    this@AlarmOverlayActivity, currentAlarm.timeString, currentAlarm.days, currentAlarm.id
                )
            } else if (isOnce && currentAlarm != null) {
                try {
                    userPreferencesRepository.updateAlarm(currentAlarm.copy(enabled = false))
                } catch (_: Exception) { }
                AlarmBroadcastReceiver.cancelAlarm(this@AlarmOverlayActivity, wantTime, resolvedId)
            }

            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }

    private fun snooze() {
        // Test mode: snooze only closes the test, doesn't schedule a real alarm.
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
        val wantId = alarmId
        val wantTime = alarmTime
        val wantSnoozeCount = snoozeCount
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                val alarm = AlarmChainHelper.findAlarm(alarms, wantId, wantTime)
                val resolvedId = alarm?.id ?: wantId
                val snoozeDuration = alarm?.snoozeDurationMinutes ?: 5
                val snoozeMax = alarm?.snoozeMaxCount ?: 3

                AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, wantTime)

                if (snoozeMax == Int.MAX_VALUE || wantSnoozeCount < snoozeMax) {
                    AlarmBroadcastReceiver.scheduleSnoozeAlarm(
                        this@AlarmOverlayActivity,
                        wantTime,
                        snoozeDuration,
                        wantSnoozeCount + 1,
                        resolvedId
                    )
                } else {
                    // Snooze quota spent: don't hang; fall back to the normal smart chain.
                    AlarmBroadcastReceiver.cancelSmartChain(this@AlarmOverlayActivity, wantTime, resolvedId)
                }
            } catch (_: Exception) { }

            withContext(Dispatchers.Main) {
                stopAlarmAndFinish()
            }
        }
    }

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = lifecycleScope.launch {
            delay(ALARM_AUTO_DISMISS_MS)
            if (isAlarmActive) {
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
                @Suppress("DEPRECATION")
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "Zenith:AlarmWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        }
    }

    private fun stopAlarmAndFinish() {
        isAlarmActive = false
        autoDismissJob?.cancel()
        autoDismissJob = null
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        wakeUpJob?.cancel()
        wakeUpJob = null
        try {
            AlarmBroadcastReceiver.cancelFiringNotification(this)
        } catch (_: Exception) { }
        if (bound) {
            playbackService?.stopPlayback()
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        } else {
            playbackService?.stopPlayback()
            // Service may not have bound yet (e.g. overlay opened via notif);
            // make sure sound stops with an explicit stopService.
            try { stopService(Intent(this, AlarmPlaybackService::class.java)) } catch (_: Exception) { }
        }
        if (wakeLock?.isHeld == true) {
            try { wakeLock?.release() } catch (_: Exception) { }
        }
        wakeLock = null
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        isAlarmActive = false
        isShowing = false
        showingAlarmKey = null
        clearAllWakeUpState()

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

    /**
     * Ask the system to show the unlock screen so startActivity to another
     * app/Settings can run from above the lockscreen. Only called from explicit
     * user action — never automatically when the alarm fires.
     */
    private fun requestUnlockForExternalLaunch() {
        try {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isKeyguardLocked) {
                keyguardManager.requestDismissKeyguard(this, null)
            }
        } catch (_: Exception) { }
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
        // Opening Settings needs the device unlocked: request unlock ONLY for this
        // explicit user action, not automatically when the alarm fires.
        requestUnlockForExternalLaunch()
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
                resumeWakeUpTracking(appPackages, durationSeconds)
            }
            // Opening another app from above the lockscreen needs an unlock first.
            requestUnlockForExternalLaunch()
            try {
                startActivity(intent)
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "Couldn't open app", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            // Package gone between setting and firing: don't stay silent; route to
            // app info so the user understands, not a dead button.
            android.widget.Toast.makeText(this, "App couldn't be opened", android.widget.Toast.LENGTH_SHORT).show()
            requestUnlockForExternalLaunch()
            try {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { }
        }
    }

    /**
     * (Re)starts wake-up tracking for the currently displayed alarm, resuming
     * from the preserved per-alarm offset instead of zero. Called from the
     * compose LaunchedEffect and from explicit "open app" taps.
     */
    private fun resumeWakeUpTracking(appPackages: List<String>, durationSeconds: Int) {
        val key = AlarmChainHelper.alarmKey(alarmId, alarmTime)
        val slot = wakeUpSlots.getOrPut(key) { WakeUpSlot() }
        if (slot.complete) {
            wakeUpAccumulatedSeconds = (slot.offsetMs / 1000).toInt()
            wakeUpComplete = true
            wakeUpTrackingActive = false
            return
        }
        // Kunci window yang mungkin masih terbuka (mis. retrigger alarm yang
        // sama me-recompose) sebelum membuka window baru, agar tidak hilang.
        rollWakeUpWindowIntoOffset()
        val now = System.currentTimeMillis()
        slot.packages = appPackages
        slot.windowStartMs = now
        wakeUpStartTime = now
        wakeUpAccumulatedSeconds = (slot.offsetMs / 1000).toInt()
        wakeUpComplete = false
        wakeUpTrackingActive = true
        startWakeUpTracking(appPackages, durationSeconds)
    }

    /**
     * Preempt oleh alarm lain: hentikan job, kunci window yang berjalan ke
     * offset via query [start, now) yang disjoint dengan window berikutnya.
     * Slot dipertahankan sehingga kembali ke alarm ini akan me-resume.
     */
    private fun suspendWakeUpForSwitch() {
        wakeUpJob?.cancel()
        wakeUpJob = null
        wakeUpTrackingActive = false
        rollWakeUpWindowIntoOffset()
    }

    private fun rollWakeUpWindowIntoOffset() {
        val key = AlarmChainHelper.alarmKey(alarmId, alarmTime)
        val slot = wakeUpSlots[key] ?: return
        val start = slot.windowStartMs
        val pkgs = slot.packages.toSet()
        if (start <= 0L || pkgs.isEmpty()) return
        // Tutup window secara sinkron; snapshot async di bawah hanya menambahkan
        // window yang persis ini satu kali, tidak peduli kapan ia selesai.
        slot.windowStartMs = 0L
        val end = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            val windowMs = getAccumulatedForegroundMs(pkgs, start, end)
            if (windowMs > 0L) {
                slot.offsetMs = AlarmChainHelper.mergeWakeUpOffset(slot.offsetMs, windowMs)
            }
        }
    }

    private fun clearAllWakeUpState() {
        wakeUpJob?.cancel()
        wakeUpJob = null
        wakeUpSlots.clear()
        wakeUpTrackingActive = false
        wakeUpAccumulatedSeconds = 0
        wakeUpComplete = false
        wakeUpStartTime = 0L
    }

    private fun startWakeUpTracking(appPackages: List<String> = emptyList(), durationSeconds: Int = 120) {
        val wantId = alarmId
        val wantTime = alarmTime
        val key = AlarmChainHelper.alarmKey(wantId, wantTime)
        val slot = wakeUpSlots.getOrPut(key) { WakeUpSlot() }
        if (slot.complete) {
            if (AlarmChainHelper.alarmKey(alarmId, alarmTime) == key) {
                wakeUpComplete = true
                wakeUpTrackingActive = false
            }
            return
        }
        wakeUpJob?.cancel()
        wakeUpJob = lifecycleScope.launch(Dispatchers.IO) {
            val repo = (application as ZenithApplication).userPreferencesRepository
            val snapshot = try {
                repo.getAlarmsSnapshot()
            } catch (_: Exception) { emptyList() }
            val configured = AlarmChainHelper.findAlarm(snapshot, wantId, wantTime)
            val packages = appPackages.ifEmpty { configured?.wakeUpAppPackageNames ?: emptyList() }
            val duration = if (durationSeconds != 120 || appPackages.isNotEmpty()) durationSeconds
            else configured?.wakeUpAppDurationSeconds ?: 120
            if (packages.isEmpty() || duration <= 0) {
                // Invalid config (e.g. all apps uninstalled): don't gate.
                withContextForMainSync {
                    if (AlarmChainHelper.alarmKey(alarmId, alarmTime) == key) {
                        wakeUpComplete = true
                        wakeUpTrackingActive = false
                    }
                }
                return@launch
            }
            slot.packages = packages
            if (slot.windowStartMs <= 0L) {
                slot.windowStartMs = System.currentTimeMillis()
            }
            while (true) {
                delay(3000)
                if (!hasUsageAccess()) {
                    withContext(Dispatchers.Main) { wakeUpNeedsPermission = true }
                    continue
                }
                val windowMs = getAccumulatedForegroundMs(
                    packages.toSet(),
                    slot.windowStartMs
                )
                val totalMs = AlarmChainHelper.mergeWakeUpOffset(slot.offsetMs, windowMs)
                withContext(Dispatchers.Main) {
                    // Abaikan hasil basi: overlay sudah pindah ke alarm lain.
                    if (AlarmChainHelper.alarmKey(alarmId, alarmTime) != key) return@withContext
                    wakeUpAccumulatedSeconds = (totalMs / 1000).toInt()
                    if (AlarmChainHelper.isWakeUpComplete(totalMs, duration)) {
                        slot.complete = true
                        slot.offsetMs = totalMs
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
        // Called from LaunchedEffect (Main) or IO; write state on Main.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            lifecycleScope.launch(Dispatchers.Main) { block() }
        }
    }

    private fun getAccumulatedForegroundMs(
        packageNames: Set<String>,
        sinceMs: Long,
        endMs: Long = System.currentTimeMillis()
    ): Long {
        return try {
            if (sinceMs >= endMs) return 0L
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(sinceMs, endMs)
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
            val now = endMs
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
        const val EXTRA_TEST_ALARM_JSON = "extra_test_alarm_json"
        const val EXTRA_TEST_GRADUAL_VOLUME = "extra_test_gradual_volume"
        const val ALARM_AUTO_DISMISS_MS = 10 * 60 * 1000L

        @Volatile
        var isShowing = false

        /**
         * Identitas alarm yang sedang ditampilkan overlay
         * ([AlarmChainHelper.alarmKey]), agar usage-check bisa membedakan
         * "alarm yang sama masih bunyi" vs "alarm lain yang preempt".
         */
        @Volatile
        var showingAlarmKey: String? = null
            private set

        fun start(context: Context, alarmTime: String, alarmId: Long = 0L) {
            val intent = Intent(context, AlarmOverlayActivity::class.java).apply {
                putExtra(EXTRA_ALARM_TIME, alarmTime)
                if (alarmId > 0L) putExtra(AlarmBroadcastReceiver.EXTRA_ALARM_ID, alarmId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }

        /**
         * Fires the overlay immediately to preview a draft alarm setup without
         * saving anything: the draft JSON is used instead of stored alarms,
         * and dismissing/snoozing only stops the test (no reschedule, no
         * smart-repeat, no stored-state writes).
         */
        fun startTest(context: Context, alarmTime: String, alarmJson: String) {
            val intent = Intent(context, AlarmOverlayActivity::class.java).apply {
                putExtra(EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_TEST_ALARM_JSON, alarmJson)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }
    }
}
