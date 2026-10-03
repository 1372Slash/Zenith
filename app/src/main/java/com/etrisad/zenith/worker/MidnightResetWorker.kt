package com.etrisad.zenith.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.util.AlarmTasksSchedulingHelper
import com.etrisad.zenith.util.SharedPrefsHelper
import androidx.glance.appwidget.updateAll
import com.etrisad.zenith.ui.widget.GlobalStreakWidget
import com.etrisad.zenith.ui.widget.AppStreakWidget
import com.etrisad.zenith.ui.widget.TotalScreenTimeWidget
import com.etrisad.zenith.ui.widget.RemainingTargetWidget
import com.etrisad.zenith.ui.widget.PhoneFreeTimeWidget
import kotlinx.coroutines.flow.first

class MidnightResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SharedPrefsHelper.setShortsScreenTimeMs(applicationContext, 0L)

        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.userPreferencesFlow.first()

        // Battery: only wake the monitor service when there is real work.
        // Previously midnight always forced a foreground start + reschedule.
        val needsService = prefs.alarmMasterEnabled || prefs.bedtimeEnabled ||
            prefs.eyeCareEnabled || prefs.usageGlimpseEnabled || prefs.pomodoroEnabled
        if (needsService) {
            val serviceIntent = Intent(applicationContext, com.etrisad.zenith.service.AppUsageMonitorService::class.java).apply {
                action = ACTION_MIDNIGHT_RESET_SERVICE
            }
            try {
                applicationContext.startForegroundService(serviceIntent)
            } catch (_: Exception) {}
        }

        // Only keep the daily chain alive when needed; otherwise let it die.
        if (needsService) {
            AlarmTasksSchedulingHelper.scheduleMidnightResetTask(applicationContext, prefs.dayStartHour, prefs.dayStartMinute)
        }
        
        GlobalStreakWidget().updateAll(applicationContext)
        AppStreakWidget().updateAll(applicationContext)
        TotalScreenTimeWidget().updateAll(applicationContext)
        RemainingTargetWidget().updateAll(applicationContext)
        PhoneFreeTimeWidget().updateAll(applicationContext)
        
        return Result.success()
    }

    companion object {
        const val ACTION_MIDNIGHT_RESET_SERVICE = "com.etrisad.zenith.action.MIDNIGHT_RESET_SERVICE"
    }
}
