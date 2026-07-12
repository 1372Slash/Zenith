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

                val wakeUpAppPackageNames = remember(currentAlarm) {
                    currentAlarm?.wakeUpAppPackageNames ?: emptyList()
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

                val wakeUpAppDurationSeconds = remember(currentAlarm) {
                    currentAlarm?.wakeUpAppDurationSeconds ?: 120
                }

                val darkTheme = when (userPreferences.themeConfig) {
                    ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    ThemeConfig.LIGHT -> false
                    ThemeConfig.DARK -> true
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
                            stopWakeUpTracking()
                            stopAlarmAndFinish()
                        }
                    )
                }
            }
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
        lifecycleScope.launch(Dispatchers.IO) {
            val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
            val prefs = userPreferencesRepository.userPreferencesFlow.first()

            val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
            val currentAlarm = alarms.find {
                it.timeString == alarmTime
            }

            val autoRepeat = currentAlarm?.autoRepeatEnabled ?: prefs.alarmAutoRepeatEnabled
            val isOnce = currentAlarm?.days?.isEmpty() ?: true
            Log.d("ZenithAlarm", "dismissWithAutoRepeat: autoRepeat=$autoRepeat isOnce=$isOnce")

            AlarmBroadcastReceiver.cancelReTrigger(this@AlarmOverlayActivity, alarmTime)

            if (autoRepeat) {
                Log.d("ZenithAlarm", "dismissWithAutoRepeat: scheduling usage check + tomorrow's alarm")
                AlarmBroadcastReceiver.scheduleUsageCheck(this@AlarmOverlayActivity, alarmTime, isOnce)
                val nextAlarmTime = if (currentAlarm != null) currentAlarm.timeString else alarmTime
                val nextDays = currentAlarm?.days ?: emptySet()
                AlarmBroadcastReceiver.scheduleAlarm(this@AlarmOverlayActivity, nextAlarmTime, nextDays)
                AlarmBroadcastReceiver.showAutoRepeatReminderNotification(this@AlarmOverlayActivity, alarmTime)
            } else {
                Log.d("ZenithAlarm", "dismissWithAutoRepeat: autoRepeat disabled")
            }

            withContext(Dispatchers.Main) {
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
                val snoozeDuration = alarm?.snoozeDurationMinutes ?: 5
                val snoozeMax = alarm?.snoozeMaxCount ?: 3

                AlarmBroadcastReceiver.cancelReTrigger(this@AlarmOverlayActivity, alarmTime)

                if (snoozeMax == Int.MAX_VALUE || snoozeCount < snoozeMax) {
                    Log.d("ZenithAlarm", "snooze: scheduling snooze alarm duration=$snoozeDuration min")
                    AlarmBroadcastReceiver.scheduleSnoozeAlarm(
                        this@AlarmOverlayActivity,
                        alarmTime,
                        snoozeDuration,
                        snoozeCount + 1
                    )
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
        if (bound) {
            playbackService?.stopPlayback()
            try { unbindService(connection) } catch (_: Exception) {}
            bound = false
        } else {
            playbackService?.stopPlayback()
        }
        wakeLock?.release()
        wakeLock = null
        finish()
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

        wakeLock?.release()
        wakeLock = null
    }

    private fun handleWakeUpAppOpened(packageName: String, appPackages: List<String>, durationSeconds: Int) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            if (!wakeUpTrackingActive) {
                wakeUpStartTime = System.currentTimeMillis()
                wakeUpTrackingActive = true
                startWakeUpTracking(appPackages, durationSeconds)
            }
            startActivity(intent)
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
        wakeUpJob?.cancel()
        wakeUpJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(3000)
                val accumulated = getAccumulatedForegroundMs(
                    packages.toSet(),
                    wakeUpStartTime
                )
                withContext(Dispatchers.Main) {
                    wakeUpAccumulatedSeconds = (accumulated / 1000).toInt()
                    if (wakeUpAccumulatedSeconds >= duration) {
                        wakeUpComplete = true
                        wakeUpTrackingActive = false
                        wakeUpJob?.cancel()
                        return@withContext
                    }
                }
            }
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
                        activeStart[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND,
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val start = activeStart.remove(event.packageName) ?: continue
                        total += event.timeStamp - start
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
