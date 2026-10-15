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
    private var snoozeCount: Int = 0
    private var testMathChallenge: Boolean = false
    private var testAlarmJson: String? = null
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
        snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, 0) ?: 0
        testMathChallenge = intent?.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false) ?: false
        testAlarmJson = intent?.getStringExtra(EXTRA_TEST_ALARM_JSON)
        testGradualVolume = intent?.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false) ?: false

        val testJson = testAlarmJson
        if (testJson != null) AlarmPlaybackService.startTest(this, alarmTime, testJson)
        else AlarmPlaybackService.start(this, alarmTime)
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
        autoDismissJob?.cancel()
        autoDismissJob = lifecycleScope.launch {
            delay(ALARM_AUTO_DISMISS_MS)
            if (isAlarmActive) {
                stopAlarmAndFinish()
            }
        }

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

                val currentAlarm = remember(userPreferences.alarmsJson, testAlarm) {
                    testAlarm ?: userPreferencesRepository.parseAlarms(userPreferences.alarmsJson)
                        .find { it.timeString == alarmTime }
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
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        if (newAlarmTime != alarmTime) {
            stopWakeUpTracking()
        }
        alarmTime = newAlarmTime
        snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        testMathChallenge = intent.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false)
        testAlarmJson = intent.getStringExtra(EXTRA_TEST_ALARM_JSON)
        testGradualVolume = intent.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false)
        restartKey++

        isAlarmActive = true
        val retestJson = testAlarmJson
        if (retestJson != null) AlarmPlaybackService.startTest(this, alarmTime, retestJson)
        else AlarmPlaybackService.start(this, alarmTime)
    }

    private fun dismissWithAutoRepeat() {
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
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

            AlarmBroadcastReceiver.recordAlarmDismiss(this@AlarmOverlayActivity, alarmTime)
            AlarmBroadcastReceiver.cancelFiringNotification(this@AlarmOverlayActivity)
            AlarmBroadcastReceiver.cancelReTrigger(this@AlarmOverlayActivity, alarmTime, alarmId)
            if (isRecurring && currentAlarm != null) {
                AlarmBroadcastReceiver.scheduleAlarm(
                    this@AlarmOverlayActivity, currentAlarm.timeString, currentAlarm.days, currentAlarm.id
                )
            }

            if (autoRepeat) {
                AlarmBroadcastReceiver.scheduleUsageCheck(
                    this@AlarmOverlayActivity, alarmTime, isOnce, 0, alarmId, dismissAt
                )
                AlarmBroadcastReceiver.showAutoRepeatReminderNotification(
                    this@AlarmOverlayActivity, alarmTime, alarmId
                )
            } else {
                AlarmBroadcastReceiver.cancelUsageCheck(this@AlarmOverlayActivity, alarmTime, alarmId)
                AlarmBroadcastReceiver.cancelSmartWakeReminder(this@AlarmOverlayActivity)
                if (isOnce && currentAlarm != null) {
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
    private fun dismissAfterWakeUpVerified() {
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
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
        // Test mode: snooze only closes the test, doesn't schedule a real alarm.
        if (testAlarmJson != null) {
            stopAlarmAndFinish()
            return
        }
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
                    AlarmBroadcastReceiver.scheduleSnoozeAlarm(
                        this@AlarmOverlayActivity,
                        alarmTime,
                        snoozeDuration,
                        snoozeCount + 1,
                        alarmId
                    )
                } else {
                    // Snooze quota spent: don't hang; fall back to the normal smart chain.
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
                wakeUpStartTime = System.currentTimeMillis()
                wakeUpTrackingActive = true
                startWakeUpTracking(appPackages, durationSeconds)
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

    private fun startWakeUpTracking(appPackages: List<String> = emptyList(), durationSeconds: Int = 120) {
        wakeUpJob?.cancel()
        wakeUpJob = lifecycleScope.launch(Dispatchers.IO) {
            val repo = (application as ZenithApplication).userPreferencesRepository
            val packages = appPackages.ifEmpty {
                try {
                    repo.getAlarmsSnapshot()
                        .find { it.timeString == alarmTime }
                        ?.wakeUpAppPackageNames ?: emptyList()
                } catch (_: Exception) { emptyList() }
            }
            val duration = if (durationSeconds != 120 || appPackages.isNotEmpty()) durationSeconds else {
                try {
                    repo.getAlarmsSnapshot()
                        .find { it.timeString == alarmTime }
                        ?.wakeUpAppDurationSeconds ?: 120
                } catch (_: Exception) { 120 }
            }
            if (packages.isEmpty() || duration <= 0) {
                // Invalid config (e.g. all apps uninstalled): don't gate.
                withContextForMainSync {
                    wakeUpComplete = true
                    wakeUpTrackingActive = false
                }
                return@launch
            }
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
        // Called from LaunchedEffect (Main) or IO; write state on Main.
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
        const val EXTRA_TEST_ALARM_JSON = "extra_test_alarm_json"
        const val EXTRA_TEST_GRADUAL_VOLUME = "extra_test_gradual_volume"
        const val ALARM_AUTO_DISMISS_MS = 10 * 60 * 1000L

        @Volatile
        var isShowing = false

        fun start(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmOverlayActivity::class.java).apply {
                putExtra(EXTRA_ALARM_TIME, alarmTime)
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
