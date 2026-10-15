package com.etrisad.zenith.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.etrisad.zenith.ZenithApplication
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
                } else {
                }
                val hasWidgets = try {
                    androidx.glance.appwidget.GlanceAppWidgetManager(context)
                        .getGlanceIds(com.etrisad.zenith.ui.widget.TotalScreenTimeWidget::class.java).isNotEmpty() ||
                    androidx.glance.appwidget.GlanceAppWidgetManager(context)
                        .getGlanceIds(com.etrisad.zenith.ui.widget.GlobalStreakWidget::class.java).isNotEmpty()
                } catch (_: Exception) { false }
                try {
                    if (prefs.alarmMasterEnabled || prefs.bedtimeEnabled) {
                        com.etrisad.zenith.util.AlarmTasksSchedulingHelper.scheduleMidnightResetTask(
                            context,
                            prefs.dayStartHour,
                            prefs.dayStartMinute
                        )
                    }
                } catch (_: Exception) {}
                try {
                    if (prefs.alarmMasterEnabled) {
                        com.etrisad.zenith.worker.AlarmWatchdogWorker.enqueue(context)
                    } else {
                        com.etrisad.zenith.worker.AlarmWatchdogWorker.cancel(context)
                    }
                } catch (_: Exception) {}
                try {
                    if (prefs.bedtimeEnabled && prefs.bedtimeDndEnabled) {
                        com.etrisad.zenith.service.DndStateManager.reconcileWithPrefs(
                            context,
                            prefs.bedtimeEnabled,
                            prefs.bedtimeDndEnabled,
                            prefs.bedtimeStartTime,
                            prefs.bedtimeEndTime,
                            prefs.bedtimeDays
                        )
                    }
                } catch (_: Exception) {}
                try {
                    if (hasWidgets) {
                        com.etrisad.zenith.ui.widget.GlobalStreakWidget().updateAll(context)
                        com.etrisad.zenith.ui.widget.AppStreakWidget().updateAll(context)
                        com.etrisad.zenith.ui.widget.TotalScreenTimeWidget().updateAll(context)
                        com.etrisad.zenith.ui.widget.RemainingTargetWidget().updateAll(context)
                        com.etrisad.zenith.ui.widget.PhoneFreeTimeWidget().updateAll(context)
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }
}
