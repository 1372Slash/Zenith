package com.etrisad.zenith.service

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
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
import androidx.core.net.toUri
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
import com.etrisad.zenith.data.model.AlarmItem

class AlarmOverlayActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var gradualVolumeJob: kotlinx.coroutines.Job? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsLoopJob: kotlinx.coroutines.Job? = null

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

        playAlarmSound()

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
            alarmTime = newAlarmTime
            snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
            testMathChallenge = intent.getBooleanExtra(EXTRA_TEST_MATH_CHALLENGE, false)
            testGradualVolume = intent.getBooleanExtra(EXTRA_TEST_GRADUAL_VOLUME, false)
            restartKey++
        }

        isAlarmActive = true
        playAlarmSound()
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

    private fun playAlarmSound() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userPreferencesRepository = (application as ZenithApplication).userPreferencesRepository
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val alarms = userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                val currentAlarm = alarms.find { it.timeString == alarmTime }
                val gradualVolume = if (testGradualVolume) testGradualVolume
                else currentAlarm?.gradualVolumeEnabled ?: false

                if (!prefs.alarmSoundEnabled) return@launch

                val soundUri = if (prefs.alarmSoundUri != null) {
                    prefs.alarmSoundUri!!.toUri()
                } else {
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                }

                val ttsEnabled = currentAlarm?.ttsEnabled ?: false
                val ttsPhrase = currentAlarm?.ttsCustomPhrase
                val ttsLanguage = currentAlarm?.ttsLanguage

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(this@AlarmOverlayActivity, soundUri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            isLooping = true
                            if (gradualVolume) {
                                setVolume(0f, 0f)
                            } else {
                                setVolume(1.0f, 1.0f)
                            }
                            setOnPreparedListener {
                                if (!isAlarmActive) return@setOnPreparedListener
                                it.start()
                                if (gradualVolume) {
                                    rampUpVolume()
                                }
                                if (ttsEnabled) {
                                    speakAlarmTime(ttsPhrase, ttsLanguage)
                                }
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun speakAlarmTime(customPhrase: String?, language: String?) {
        try {
            val hour = alarmTime.split(":").getOrNull(0)?.toIntOrNull() ?: 7
            val minute = alarmTime.split(":").getOrNull(1)?.toIntOrNull() ?: 0
            val hourFormatted = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            val minuteStr = minute.toString().padStart(2, '0')
            val amPm = if (hour < 12) "AM" else "PM"
            val timeText = if (minute == 0) "$hourFormatted $amPm" else "$hourFormatted:$minuteStr $amPm"
            val defaultPhrase = "Wake up, it's $timeText"
            val text = customPhrase?.replace("{time}", timeText) ?: defaultPhrase

            val locale = language?.let { l ->
                val parts = l.split("_")
                if (parts.size == 2) java.util.Locale(parts[0], parts[1])
                else java.util.Locale(l)
            } ?: java.util.Locale.US

            tts?.stop()
            tts?.shutdown()
            tts = android.speech.tts.TextToSpeech(this@AlarmOverlayActivity) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS && isAlarmActive) {
                    tts?.language = locale
                    mediaPlayer?.setVolume(0.3f, 0.3f)
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "alarm_tts")

                    ttsLoopJob?.cancel()
                    ttsLoopJob = lifecycleScope.launch {
                        while (isAlarmActive) {
                            delay(3000L)
                            if (!isAlarmActive) break
                            mediaPlayer?.setVolume(1.0f, 1.0f)
                            delay(3000L)
                            if (!isAlarmActive) break
                            mediaPlayer?.setVolume(0.3f, 0.3f)
                            try {
                                val result = tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "alarm_tts")
                                if (result == android.speech.tts.TextToSpeech.ERROR) {
                                    Log.w("ZenithAlarm", "TTS speak failed, reinitializing")
                                    tts?.stop()
                                    tts?.shutdown()
                                    withContext(Dispatchers.Main) {
                                        speakAlarmTime(customPhrase, language)
                                    }
                                    return@launch
                                }
                            } catch (e: Exception) {
                                Log.w("ZenithAlarm", "TTS speak exception: ${e.message}")
                                tts?.stop()
                                tts?.shutdown()
                                withContext(Dispatchers.Main) {
                                    speakAlarmTime(customPhrase, language)
                                }
                                return@launch
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("ZenithAlarm", "TTS failed: ${e.message}")
        }
    }

    private fun rampUpVolume() {
        gradualVolumeJob = lifecycleScope.launch(Dispatchers.IO) {
            val rampDuration = 30_000L
            val steps = 100
            val stepDelay = rampDuration / steps
            for (i in 1..steps) {
                delay(stepDelay)
                val volume = i.toFloat() / steps
                withContext(Dispatchers.Main) {
                    mediaPlayer?.setVolume(volume, volume)
                }
            }
        }
    }

    private fun stopAlarmAndFinish() {
        isAlarmActive = false
        ttsLoopJob?.cancel()
        ttsLoopJob = null
        gradualVolumeJob?.cancel()
        gradualVolumeJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        wakeLock?.release()
        wakeLock = null
        finish()
    }

    override fun onDestroy() {
        Log.d("ZenithAlarm", "AlarmOverlayActivity.onDestroy")
        isAlarmActive = false
        isShowing = false
        stopWakeUpTracking()

        ttsLoopJob?.cancel()
        ttsLoopJob = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
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