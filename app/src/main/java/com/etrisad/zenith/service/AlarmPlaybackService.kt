package com.etrisad.zenith.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.etrisad.zenith.R
import com.etrisad.zenith.ZenithApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlarmPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsLoopJob: Job? = null
    private var gradualVolumeJob: Job? = null
    private var ttsRetryCount = 0

    @Volatile
    private var isAlarmActive = false

    private var alarmTime: String = "07:00"

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    inner class LocalBinder : Binder() {
        fun getService(): AlarmPlaybackService = this@AlarmPlaybackService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        _isPlaying.value = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newAlarmTime = intent?.getStringExtra(EXTRA_ALARM_TIME) ?: alarmTime
        alarmTime = newAlarmTime

        startForeground(NOTIFICATION_ID, buildForegroundNotification(alarmTime))

        isAlarmActive = true
        _isPlaying.value = true
        forceAlarmVolume()
        playAlarmSound()

        return START_STICKY
    }

    private fun buildForegroundNotification(alarmTime: String): android.app.Notification {
        val channelId = "zenith_alarm_playback_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId, "Alarm Playback", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps alarm sound running in the background"
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val openAlarmIntent = Intent(this, AlarmOverlayActivity::class.java).apply {
            putExtra(AlarmOverlayActivity.EXTRA_ALARM_TIME, alarmTime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 4001, openAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Alarm playing")
            .setContentText("It's $alarmTime")
            .setSmallIcon(R.drawable.ic_flag)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    private fun playAlarmSound() {
        serviceScope.launch {
            try {
                val app = applicationContext as ZenithApplication
                val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                val alarms = app.userPreferencesRepository.parseAlarms(prefs.alarmsJson)
                val currentAlarm = alarms.find { it.timeString == alarmTime }
                val gradualVolume = currentAlarm?.gradualVolumeEnabled ?: false

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
                            setDataSource(this@AlarmPlaybackService, soundUri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                            )
                            isLooping = true
                            if (gradualVolume) setVolume(0f, 0f) else setVolume(1.0f, 1.0f)

                            setOnErrorListener { mp, what, extra ->
                                Log.e("AlarmPlayback", "MediaPlayer error: what=$what extra=$extra")
                                try {
                                    mp.reset()
                                    mp.setDataSource(
                                        this@AlarmPlaybackService,
                                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                                    )
                                    mp.prepareAsync()
                                } catch (e: Exception) {
                                    Log.e("AlarmPlayback", "Fallback sound also failed: ${e.message}")
                                }
                                true
                            }
                            setOnPreparedListener {
                                if (!isAlarmActive) return@setOnPreparedListener
                                it.start()
                                if (gradualVolume) rampUpVolume()
                                if (ttsEnabled) speakAlarmTime(ttsPhrase, ttsLanguage)
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        Log.e("AlarmPlayback", "playAlarmSound failed: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("AlarmPlayback", "playAlarmSound outer failed: ${e.message}", e)
            }
        }
    }

    private fun speakAlarmTime(customPhrase: String?, language: String?, isRetry: Boolean = false) {
        if (!isRetry) ttsRetryCount = 0
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
            val text = customPhrase?.replace("{time}", timeText) ?: "Wake up, it's $timeText"

            val locale = language?.let { l ->
                val parts = l.split("_")
                if (parts.size == 2) java.util.Locale(parts[0], parts[1]) else java.util.Locale(l)
            } ?: java.util.Locale.US

            tts?.stop()
            tts?.shutdown()
            tts = android.speech.tts.TextToSpeech(this@AlarmPlaybackService) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS && isAlarmActive) {
                    tts?.language = locale
                    mediaPlayer?.setVolume(0.3f, 0.3f)
                    tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "alarm_tts")

                    ttsLoopJob?.cancel()
                    ttsLoopJob = serviceScope.launch {
                        while (isAlarmActive) {
                            delay(3000L)
                            if (!isAlarmActive) break
                            withContext(Dispatchers.Main) { mediaPlayer?.setVolume(1.0f, 1.0f) }
                            delay(3000L)
                            if (!isAlarmActive) break
                            withContext(Dispatchers.Main) { mediaPlayer?.setVolume(0.3f, 0.3f) }
                            try {
                                val result = tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "alarm_tts")
                                if (result == android.speech.tts.TextToSpeech.ERROR) {
                                    ttsRetryCount++
                                    if (ttsRetryCount >= 3) {
                                        Log.e("AlarmPlayback", "TTS failed after 3 retries, giving up")
                                        return@launch
                                    }
                                    tts?.stop(); tts?.shutdown()
                                    withContext(Dispatchers.Main) { speakAlarmTime(customPhrase, language, isRetry = true) }
                                    return@launch
                                }
                            } catch (e: Exception) {
                                ttsRetryCount++
                                if (ttsRetryCount >= 3) {
                                    Log.e("AlarmPlayback", "TTS exception after 3 retries, giving up")
                                    return@launch
                                }
                                tts?.stop(); tts?.shutdown()
                                withContext(Dispatchers.Main) { speakAlarmTime(customPhrase, language, isRetry = true) }
                                return@launch
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("AlarmPlayback", "TTS failed: ${e.message}")
        }
    }

    private fun rampUpVolume() {
        gradualVolumeJob = serviceScope.launch {
            val steps = 100
            val stepDelay = 30_000L / steps
            for (i in 1..steps) {
                delay(stepDelay)
                val volume = i.toFloat() / steps
                withContext(Dispatchers.Main) { mediaPlayer?.setVolume(volume, volume) }
            }
        }
    }

    private fun forceAlarmVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol < maxVol / 2) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVol / 2, 0)
            }
        } catch (e: Exception) {
            Log.w("AlarmPlayback", "Failed to force alarm volume: ${e.message}")
        }
    }

    fun stopPlayback() {
        isAlarmActive = false
        _isPlaying.value = false
        ttsLoopJob?.cancel(); ttsLoopJob = null
        gradualVolumeJob?.cancel(); gradualVolumeJob = null
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        tts?.stop(); tts?.shutdown(); tts = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isAlarmActive = false
        _isPlaying.value = false
        ttsLoopJob?.cancel()
        gradualVolumeJob?.cancel()
        mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        tts?.stop(); tts?.shutdown(); tts = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        private const val NOTIFICATION_ID = 2010

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying

        fun start(context: Context, alarmTime: String) {
            val intent = Intent(context, AlarmPlaybackService::class.java).apply {
                putExtra(EXTRA_ALARM_TIME, alarmTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
