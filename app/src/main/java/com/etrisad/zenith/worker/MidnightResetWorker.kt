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
import kotlinx.coroutines.flow.first

class MidnightResetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        SharedPrefsHelper.setShortsScreenTimeMs(applicationContext, 0L)

        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.userPreferencesFlow.first()

        val serviceIntent = Intent(applicationContext, com.etrisad.zenith.service.AppUsageMonitorService::class.java).apply {
            action = ACTION_MIDNIGHT_RESET_SERVICE
        }
        try {
            applicationContext.startForegroundService(serviceIntent)
        } catch (_: Exception) {}

        AlarmTasksSchedulingHelper.scheduleMidnightResetTask(applicationContext, prefs.dayStartHour, prefs.dayStartMinute)
        
        GlobalStreakWidget().updateAll(applicationContext)
        AppStreakWidget().updateAll(applicationContext)
        
        return Result.success()
    }

    companion object {
        const val ACTION_MIDNIGHT_RESET_SERVICE = "com.etrisad.zenith.action.MIDNIGHT_RESET_SERVICE"
    }
}
