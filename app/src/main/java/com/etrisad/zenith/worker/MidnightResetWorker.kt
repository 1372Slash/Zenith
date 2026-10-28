package com.etrisad.zenith.worker

import android.content.Context
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
        if (com.etrisad.zenith.crash.SafeMode.isEnabled(applicationContext)) return Result.success()
        SharedPrefsHelper.setShortsScreenTimeMs(applicationContext, 0L)

        val prefsRepo = UserPreferencesRepository(applicationContext)
        val prefs = prefsRepo.userPreferencesFlow.first()
        if (prefs.reelsShieldEnabled || prefs.reelsShieldTodayCount > 0) {
            val dayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(
                System.currentTimeMillis(), prefs.dayStartHour, prefs.dayStartMinute
            )
            val todayKey = com.etrisad.zenith.util.DateTimeUtils.getDateFormat().format(java.util.Date(dayStart))
            // If the live controller already rolled the day and wrote a fresh
            // count, do not clobber it from this stale worker snapshot.
            if (prefs.reelsShieldTodayDate != todayKey) {
                val history = HashMap(prefs.reelsShieldHistory)
                if (prefs.reelsShieldTodayDate.isNotEmpty()) {
                    history[prefs.reelsShieldTodayDate] = prefs.reelsShieldTodayCount
                }
                val trimmed = history.entries
                    .sortedByDescending { it.key }
                    .take(30)
                    .associate { it.key to it.value }
                val historyByApp = HashMap(prefs.reelsShieldHistoryByApp.mapValues { HashMap(it.value) })
                if (prefs.reelsShieldTodayDate.isNotEmpty() && prefs.reelsShieldTodayByApp.isNotEmpty()) {
                    historyByApp[prefs.reelsShieldTodayDate] = HashMap(prefs.reelsShieldTodayByApp)
                }
                val trimmedByApp = historyByApp.filterKeys { it in trimmed.keys }
                prefsRepo.setReelsShieldCounters(
                    todayDate = todayKey,
                    todayCount = 0,
                    todayByApp = emptyMap(),
                    totalCount = prefs.reelsShieldTotalCount,
                    history = trimmed,
                    historyByApp = trimmedByApp
                )
            }
        }
        val needsService = prefs.alarmMasterEnabled || prefs.bedtimeEnabled ||
            prefs.eyeCareEnabled || prefs.usageGlimpseEnabled || prefs.pomodoroEnabled
        if (needsService) {
            com.etrisad.zenith.service.AppUsageMonitorService.startMonitorService(
                applicationContext,
                ACTION_MIDNIGHT_RESET_SERVICE
            )
        }
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
