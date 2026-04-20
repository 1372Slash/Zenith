package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

data class AppDetailUiState(
    val packageName: String = "",
    val appName: String = "",
    val icon: android.graphics.drawable.Drawable? = null,
    val type: com.etrisad.zenith.data.local.entity.FocusType? = null,
    val todayUsage: Long = 0L,
    val yesterdayUsage: Long = 0L,
    val percentageChange: Float = 0f,
    val usageHistory: List<DailyUsage> = emptyList(),
    val shieldEntity: ShieldEntity? = null
)

data class HomeUiState(
    val totalScreenTime: Long = 0L,
    val yesterdayScreenTime: Long = 0L,
    val percentageChange: Float = 0f,
    val dailyUsageHistory: List<DailyUsage> = emptyList(),
    val topApps: List<AppUsageInfo> = emptyList(),
    val allAppsUsage: List<AppUsageInfo> = emptyList(),
    val activeShields: List<ShieldEntity> = emptyList(),
    val activeGoals: List<ShieldEntity> = emptyList(),
    val shieldSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val goalSortType: ShieldSortType = ShieldSortType.ALPHABETICAL
)

class HomeViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allShields: List<ShieldEntity> = emptyList()

    private val _appDetailUiState = MutableStateFlow(AppDetailUiState())
    val appDetailUiState: StateFlow<AppDetailUiState> = _appDetailUiState.asStateFlow()

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                updateShieldedLists()
            }
        }
        refreshUsageStats()
        startRealTimeUpdates()
    }

    fun onShieldSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(shieldSortType = sortType)
        updateShieldedLists()
    }

    fun onGoalSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(goalSortType = sortType)
        updateShieldedLists()
    }

    private fun updateShieldedLists() {
        val shields = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }
        val goals = allShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }

        _uiState.value = _uiState.value.copy(
            activeShields = sortShields(shields, _uiState.value.shieldSortType),
            activeGoals = sortShields(goals, _uiState.value.goalSortType)
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
                delay(15000) // Refresh every 15 seconds
            }
        }
    }

    private fun refreshUsageStats() {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        
        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        ).map { it.activityInfo.packageName }.toSet()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        
        // Today midnight
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis

        // Store daily totals by day-start timestamp
        val dailyTotals = mutableMapOf<Long, Long>()
        val appList = mutableListOf<AppUsageInfo>()

        // 1. Get TODAY'S stats (Real-time aggregation)
        val todayStatsMap = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
        var totalToday = 0L
        todayStatsMap.forEach { (packageName, usageStat) ->
            if (packageName == context.packageName || packageName == launcherPackage) return@forEach
            if (!launcherApps.contains(packageName)) return@forEach

            val time = usageStat.totalTimeVisible.coerceAtLeast(usageStat.totalTimeInForeground)
            if (time > 0) {
                totalToday += time
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    appList.add(AppUsageInfo(packageName, appName, time))
                } catch (e: Exception) {}
            }
        }
        dailyTotals[todayStart] = totalToday

        // 2. Get HISTORICAL stats (Days before today)
        // Use queryUsageStats with INTERVAL_DAILY for cleaner bucket separation
        val historyRangeStart = todayStart - (21 * 24 * 60 * 60 * 1000L)
        val historicalStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            historyRangeStart,
            todayStart // Query up to today's start to avoid mixing with today's real-time data
        )

        historicalStats?.forEach { stat ->
            if (stat.packageName == context.packageName || stat.packageName == launcherPackage) return@forEach
            if (!launcherApps.contains(stat.packageName)) return@forEach
            
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                cal.timeInMillis = stat.firstTimeStamp
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayKey = cal.timeInMillis

                // Only accumulate if it belongs to a day BEFORE today
                if (dayKey < todayStart) {
                    dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
                }
            }
        }

        // 3. Assemble the 21-day history
        val history = mutableListOf<DailyUsage>()
        for (i in 0 until 21) {
            val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
            history.add(DailyUsage(dStart, dailyTotals[dStart] ?: 0L))
        }

        val totalYesterday = dailyTotals[todayStart - 24 * 60 * 60 * 1000L] ?: 0L
        val percentageChange = if (totalYesterday > 0) {
            ((totalToday - totalYesterday).toFloat() / totalYesterday) * 100
        } else if (totalToday > 0) {
            100f
        } else {
            0f
        }

        val sortedApps = appList.sortedByDescending { it.totalTimeVisible }
        
        val topApps = sortedApps.take(5).map { app ->
            try {
                app.copy(icon = pm.getApplicationIcon(app.packageName))
            } catch (e: PackageManager.NameNotFoundException) {
                app
            }
        }

        val allAppsUsage = sortedApps.map { app ->
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
            topApps = topApps,
            allAppsUsage = allAppsUsage
        )
    }

    fun loadAppDetail(packageName: String) {
        viewModelScope.launch {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()

            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis

            val dailyTotals = mutableMapOf<Long, Long>()

            // 1. Today's usage for this app
            val todayStats = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
            val todayUsage = todayStats[packageName]?.let { 
                it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) 
            } ?: 0L
            dailyTotals[todayStart] = todayUsage

            // 2. Historical usage for this app
            val historyRangeStart = todayStart - (21 * 24 * 60 * 60 * 1000L)
            val historicalStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                historyRangeStart,
                todayStart
            )

            historicalStats?.forEach { stat ->
                if (stat.packageName == packageName) {
                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                    if (time > 0) {
                        cal.timeInMillis = stat.firstTimeStamp
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val dayKey = cal.timeInMillis
                        if (dayKey < todayStart) {
                            dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
                        }
                    }
                }
            }

            val history = mutableListOf<DailyUsage>()
            for (i in 0 until 21) {
                val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
                history.add(DailyUsage(dStart, dailyTotals[dStart] ?: 0L))
            }

            val yesterdayUsage = dailyTotals[todayStart - 24 * 60 * 60 * 1000L] ?: 0L
            val percentageChange = if (yesterdayUsage > 0) {
                ((todayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
            } else if (todayUsage > 0) {
                100f
            } else {
                0f
            }

            val shield = allShields.find { it.packageName == packageName }
            
            var appName = packageName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon = pm.getApplicationIcon(appInfo)
            } catch (e: Exception) {}

            _appDetailUiState.value = AppDetailUiState(
                packageName = packageName,
                appName = appName,
                icon = icon,
                type = shield?.type,
                todayUsage = todayUsage,
                yesterdayUsage = yesterdayUsage,
                percentageChange = percentageChange,
                usageHistory = history.reversed(),
                shieldEntity = shield
            )
        }
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
            _appDetailUiState.value = _appDetailUiState.value.copy(
                type = null,
                shieldEntity = null
            )
        }
    }

    fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
