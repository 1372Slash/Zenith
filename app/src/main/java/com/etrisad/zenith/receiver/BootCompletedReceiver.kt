package com.etrisad.zenith.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.etrisad.zenith.ZenithApplication
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // TIME_SET / TIMEZONE_CHANGED: exact alarm bisa geser; jadwalkan ulang.
        // Dulu hanya BOOT + PACKAGE_REPLACED sehingga alarm meleset setelah user
        // mengubah jam/timezone.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as ZenithApplication
                val prefs = app.userPreferencesRepository.userPreferencesFlow.first()
                if (prefs.alarmMasterEnabled) {
                    val enabledAlarms = app.userPreferencesRepository
                        .parseAlarms(prefs.alarmsJson)
                        .filter { it.enabled }
                    AlarmBroadcastReceiver.rescheduleAllAlarms(context, enabledAlarms)
                    Log.d("BootReceiver", "Rescheduled ${enabledAlarms.size} alarms after boot/update")
                } else {
                    Log.d("BootReceiver", "Master switch OFF - skipping alarm reschedule after boot/update")
                }
                try {
                    com.etrisad.zenith.util.AlarmTasksSchedulingHelper.scheduleMidnightResetTask(
                        context,
                        prefs.dayStartHour,
                        prefs.dayStartMinute
                    )
                } catch (_: Exception) {}
                try {
                    com.etrisad.zenith.worker.AlarmWatchdogWorker.enqueue(context)
                } catch (_: Exception) {}
                try {
                    com.etrisad.zenith.service.DndStateManager.reconcileWithPrefs(
                        context,
                        prefs.bedtimeEnabled,
                        prefs.bedtimeDndEnabled,
                        prefs.bedtimeStartTime,
                        prefs.bedtimeEndTime,
                        prefs.bedtimeDays
                    )
                } catch (_: Exception) {}
                try {
                    com.etrisad.zenith.ui.widget.GlobalStreakWidget().updateAll(context)
                    com.etrisad.zenith.ui.widget.AppStreakWidget().updateAll(context)
                    com.etrisad.zenith.ui.widget.TotalScreenTimeWidget().updateAll(context)
                    com.etrisad.zenith.ui.widget.RemainingTargetWidget().updateAll(context)
                    com.etrisad.zenith.ui.widget.PhoneFreeTimeWidget().updateAll(context)
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to reschedule alarms: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
