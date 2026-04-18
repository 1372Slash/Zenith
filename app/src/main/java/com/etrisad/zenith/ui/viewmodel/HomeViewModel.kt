package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeVisible: Long,
    val icon: android.graphics.drawable.Drawable? = null
)

data class DailyUsage(
    val date: Long,
    val totalTime: Long
)

data class HomeUiState(
    val totalScreenTime: Long = 0L,
    val yesterdayScreenTime: Long = 0L,
    val percentageChange: Float = 0f,
    val dailyUsageHistory: List<DailyUsage> = emptyList(),
    val topApps: List<AppUsageInfo> = emptyList(),
    val shieldedApps: List<ShieldEntity> = emptyList(),
    val sortType: ShieldSortType = ShieldSortType.ALPHABETICAL
)

class HomeViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                _uiState.value = _uiState.value.copy(
                    shieldedApps = sortShields(shields, _uiState.value.sortType)
                )
            }
        }
        refreshUsageStats()
        startRealTimeUpdates()
    }

    fun onSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(
            sortType = sortType,
            shieldedApps = sortShields(_uiState.value.shieldedApps, sortType)
        )
    }

    private fun sortShields(shields: List<ShieldEntity>, sortType: ShieldSortType): List<ShieldEntity> {
        return when (sortType) {
            ShieldSortType.ALPHABETICAL -> shields.sortedBy { it.appName.lowercase() }
            ShieldSortType.REMAINING_TIME -> shields.sortedBy { 
                if (it.timeLimitMinutes > 0) it.remainingTimeMillis.toDouble() / (it.timeLimitMinutes * 60 * 1000L) else 0.0 
            }
        }
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                refreshUsageStats()
                delay(10000) // Refresh every 10 seconds
            }
        }
    }

    private fun refreshUsageStats() {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        
        // Today's stats
        val todayCalendar = Calendar.getInstance()
        todayCalendar.set(Calendar.HOUR_OF_DAY, 0)
        todayCalendar.set(Calendar.MINUTE, 0)
        todayCalendar.set(Calendar.SECOND, 0)
        todayCalendar.set(Calendar.MILLISECOND, 0)
        val todayStart = todayCalendar.timeInMillis
        val now = System.currentTimeMillis()

        val todayStats = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
        var totalToday = 0L
        val appList = mutableListOf<AppUsageInfo>()

        todayStats.forEach { (packageName, usageStat) ->
            val time = usageStat.totalTimeInForeground // totalTimeVisible might be more accurate but let's stick to foreground for consistency with standard usage apps
            if (time > 0) {
                totalToday += time
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    appList.add(AppUsageInfo(packageName, appName, time))
                } catch (e: PackageManager.NameNotFoundException) {}
            }
        }

        // Yesterday's stats
        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
        val yesterdayEnd = todayStart - 1
        val yesterdayStats = usageStatsManager.queryAndAggregateUsageStats(yesterdayStart, yesterdayEnd)
        var totalYesterday = 0L
        yesterdayStats.forEach { (_, usageStat) ->
            totalYesterday += usageStat.totalTimeInForeground
        }

        // 21 days history
        val history = mutableListOf<DailyUsage>()
        val queryCalendar = Calendar.getInstance()
        for (i in 0 until 21) {
            queryCalendar.timeInMillis = todayStart
            queryCalendar.add(Calendar.DAY_OF_YEAR, -i)
            val start = queryCalendar.timeInMillis
            
            val queryEndCalendar = Calendar.getInstance()
            queryEndCalendar.timeInMillis = start
            queryEndCalendar.set(Calendar.HOUR_OF_DAY, 23)
            queryEndCalendar.set(Calendar.MINUTE, 59)
            queryEndCalendar.set(Calendar.SECOND, 59)
            val end = queryEndCalendar.timeInMillis
            
            val dailyStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                start,
                if (i == 0) now else end
            )
            
            var dayTotal = 0L
            dailyStats.forEach { stat ->
                // Check if the stat's last time used is within the day we're querying
                // and use totalTimeInForeground for more reliable daily breakdown
                if (stat.lastTimeUsed >= start) {
                    dayTotal += stat.totalTimeInForeground
                }
            }
            history.add(DailyUsage(start, dayTotal))
        }

        val percentageChange = if (totalYesterday > 0) {
            ((totalToday - totalYesterday).toFloat() / totalYesterday) * 100
        } else if (totalToday > 0) {
            100f
        } else {
            0f
        }

        val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5).map { app ->
            try {
                app.copy(icon = pm.getApplicationIcon(app.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                app
            }
        }

        _uiState.value = _uiState.value.copy(
            totalScreenTime = totalToday,
            yesterdayScreenTime = totalYesterday,
            percentageChange = percentageChange,
            dailyUsageHistory = history.reversed(),
            topApps = topApps
        )
    }

    fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
