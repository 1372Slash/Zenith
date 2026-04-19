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
                delay(10000) // Refresh every 10 seconds
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

        // 1. GET TODAY'S STATS (Real-time aggregation)
        val todayStatsMap = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
        var totalToday = 0L
        val appList = mutableListOf<AppUsageInfo>()

        todayStatsMap.forEach { (packageName, usageStat) ->
            if (packageName == context.packageName || packageName == launcherPackage) return@forEach
            if (!launcherApps.contains(packageName)) return@forEach

            // Use the maximum of totalTimeVisible and totalTimeInForeground for accuracy across Android versions
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

        // 2. GET HISTORICAL STATS (Last 21 days)
        // We query strictly before todayStart to avoid overlapping with our Today's aggregation
        val historyRangeStart = todayStart - (21 * 24 * 60 * 60 * 1000L)
        val allDailyStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            historyRangeStart,
            todayStart
        )

        val dailyTotals = mutableMapOf<Long, Long>()
        allDailyStats.forEach { stat ->
            if (stat.packageName == context.packageName || stat.packageName == launcherPackage) return@forEach
            if (!launcherApps.contains(stat.packageName)) return@forEach
            
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time > 0) {
                // Normalize the bucket's timestamp to find its day start
                cal.timeInMillis = stat.firstTimeStamp
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val dayKey = cal.timeInMillis
                
                // Safety: only buckets before today
                if (dayKey < todayStart) {
                    dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
                }
            }
        }

        // 3. ASSEMBLE RESULTS
        val history = mutableListOf<DailyUsage>()
        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
        val totalYesterday = dailyTotals[yesterdayStart] ?: 0L

        // Reconstruct the 21-day timeline
        for (i in 0 until 21) {
            val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
            val dayTotal = if (i == 0) totalToday else (dailyTotals[dStart] ?: 0L)
            history.add(DailyUsage(dStart, dayTotal))
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

        val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }.map { app ->
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

    fun formatDuration(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
