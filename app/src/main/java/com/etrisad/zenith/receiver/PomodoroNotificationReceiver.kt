package com.etrisad.zenith.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.PomodoroNotificationHelper
import com.etrisad.zenith.service.SharedMonitoringState
import com.etrisad.zenith.service.ZenithNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PomodoroNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefsRepo = UserPreferencesRepository(appContext)
                when (action) {
                    PomodoroNotificationHelper.ACTION_PAUSE -> {
                        val prefs = prefsRepo.userPreferencesFlow.first()
                        val now = System.currentTimeMillis()
                        if (prefs.pomodoroEnabled &&
                            prefs.pomodoroSessionEndTimestamp > now &&
                            prefs.pomodoroPauseable &&
                            !SharedMonitoringState.isPomodoroPaused
                        ) {
                            SharedMonitoringState.isPomodoroPaused = true
                            SharedMonitoringState.pomodoroPauseTimestamp = now
                            SharedMonitoringState.isPomodoroBlockingActive = false
                        }
                    }
                    PomodoroNotificationHelper.ACTION_RESUME -> {
                        if (SharedMonitoringState.isPomodoroPaused) {
                            val prefs = prefsRepo.userPreferencesFlow.first()
                            val now = System.currentTimeMillis()
                            val pausedAt = SharedMonitoringState.pomodoroPauseTimestamp
                            val pauseDuration = if (pausedAt > 0L) (now - pausedAt).coerceAtLeast(0L) else 0L
                            if (pauseDuration > 0L) {
                                if (prefs.pomodoroSessionEndTimestamp > 0L) {
                                    prefsRepo.setPomodoroSessionEndTimestamp(
                                        prefs.pomodoroSessionEndTimestamp + pauseDuration
                                    )
                                }
                                if (prefs.pomodoroBreakEndTimestamp > 0L) {
                                    prefsRepo.setPomodoroBreakEndTimestamp(
                                        prefs.pomodoroBreakEndTimestamp + pauseDuration
                                    )
                                }
                            }
                            SharedMonitoringState.isPomodoroPaused = false
                            SharedMonitoringState.pomodoroPauseTimestamp = 0L
                            SharedMonitoringState.isPomodoroBlockingActive =
                                !SharedMonitoringState.isPomodoroRelaxedMode
                        }
                    }
                    PomodoroNotificationHelper.ACTION_STOP -> {
                        prefsRepo.setPomodoroEnabled(false)
                        prefsRepo.setPomodoroSessionEndTimestamp(0L)
                        prefsRepo.setPomodoroBreakEndTimestamp(0L)
                        SharedMonitoringState.isPomodoroActive = false
                        SharedMonitoringState.isPomodoroBlockingActive = false
                        SharedMonitoringState.isPomodoroRelaxedMode = false
                        SharedMonitoringState.isPomodoroBreakActive = false
                        SharedMonitoringState.isPomodoroPaused = false
                        SharedMonitoringState.pomodoroPauseTimestamp = 0L
                        PomodoroNotificationHelper.cancelNotification(appContext)
                        ZenithNotificationListener.restorePomodoroNotifications(appContext)
                        return@launch
                    }
                }

                val prefs = prefsRepo.userPreferencesFlow.first()
                PomodoroNotificationHelper.updateFromPreferences(appContext, prefs)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
