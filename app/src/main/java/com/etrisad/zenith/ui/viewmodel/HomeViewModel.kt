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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val shieldEntity: ShieldEntity? = null,
    val todos: List<com.etrisad.zenith.data.local.entity.TodoEntity> = emptyList()
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

    private var todoCollectionJob: Job? = null

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
                delay(10000) // Refresh every 10 seconds
                refreshUsageStats()
                // Refresh detail if active
                val currentDetailPkg = _appDetailUiState.value.packageName
                if (currentDetailPkg.isNotEmpty()) {
                    loadAppDetail(currentDetailPkg)
                }
            }
        }
    }

    private fun refreshUsageStats() {
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) {
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

                // 1. GET TODAY'S STATS
                val todayStatsMap = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
                var totalToday = 0L
                val appList = mutableListOf<AppUsageInfo>()

                todayStatsMap.forEach { (packageName, usageStat) ->
                    if (packageName == context.packageName || packageName == launcherPackage) return@forEach
                    if (!launcherApps.contains(packageName)) return@forEach

                    val time = usageStat.totalTimeVisible.coerceAtLeast(usageStat.totalTimeInForeground)
                    if (time > 0) {
                        totalToday += time
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            val appName = pm.getApplicationLabel(appInfo).toString()
                            // Note: Icon is NOT fetched here anymore to avoid redundant loads
                            appList.add(AppUsageInfo(packageName, appName, time))
                        } catch (e: Exception) {}
                    }
                }

                // 2. GET YESTERDAY'S STATS
                val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
                val yesterdayStatsMap = usageStatsManager.queryAndAggregateUsageStats(yesterdayStart, todayStart)
                var totalYesterday = 0L
                yesterdayStatsMap.forEach { (packageName, usageStat) ->
                    if (packageName == context.packageName || packageName == launcherPackage) return@forEach
                    if (!launcherApps.contains(packageName)) return@forEach
                    totalYesterday += usageStat.totalTimeVisible.coerceAtLeast(usageStat.totalTimeInForeground)
                }

                // 3. GET HISTORICAL STATS
                val historyRangeStart = todayStart - (21 * 24 * 60 * 60 * 1000L)
                val allDailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    historyRangeStart,
                    yesterdayStart
                )

                val dailyTotals = mutableMapOf<Long, Long>()
                allDailyStats.forEach { stat ->
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

                        if (dayKey < yesterdayStart) {
                            dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
                        }
                    }
                }

                val history = mutableListOf<DailyUsage>()
                for (i in 0 until 21) {
                    val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
                    val dayTotal = when (i) {
                        0 -> totalToday
                        1 -> totalYesterday
                        else -> dailyTotals[dStart] ?: 0L
                    }
                    history.add(DailyUsage(dStart, dayTotal))
                }

                val percentageChange = if (totalYesterday > 0) {
                    ((totalToday - totalYesterday).toFloat() / totalYesterday) * 100
                } else if (totalToday > 0) { 100f } else { 0f }

                val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5)
                val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }

                Triple(totalToday, totalYesterday, Triple(percentageChange, history.reversed(), Triple(topApps, allAppsUsage, Unit)))
            }

            _uiState.update { currentState ->
                // Check if anything actually changed to avoid unnecessary recompositions
                // We compare total screen time and top apps count/names
                val (totalToday, totalYesterday, rest1) = newState
                val (percentageChange, history, rest2) = rest1
                val (topApps, allAppsUsage, _) = rest2

                if (currentState.totalScreenTime == totalToday && 
                    currentState.yesterdayScreenTime == totalYesterday &&
                    currentState.topApps.size == topApps.size &&
                    currentState.dailyUsageHistory == history) {
                    currentState
                } else {
                    currentState.copy(
                        totalScreenTime = totalToday,
                        yesterdayScreenTime = totalYesterday,
                        percentageChange = percentageChange,
                        dailyUsageHistory = history,
                        topApps = topApps,
                        allAppsUsage = allAppsUsage
                    )
                }
            }
        }
    }

    fun loadAppDetail(packageName: String) {
        // Handle Todo collection separately
        if (_appDetailUiState.value.packageName != packageName) {
            todoCollectionJob?.cancel()
            todoCollectionJob = viewModelScope.launch {
                shieldRepository.getTodosForApp(packageName).collect { todos ->
                    _appDetailUiState.update { it.copy(todos = todos) }
                }
            }
        }

        viewModelScope.launch {
            val detailData = withContext(Dispatchers.IO) {
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

                val todayStats = usageStatsManager.queryAndAggregateUsageStats(todayStart, now)
                val todayUsage = todayStats[packageName]?.let { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) } ?: 0L

                val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)
                val yesterdayStats = usageStatsManager.queryAndAggregateUsageStats(yesterdayStart, todayStart)
                val yesterdayUsage = yesterdayStats[packageName]?.let { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) } ?: 0L

                val historyRangeStart = todayStart - (21 * 24 * 60 * 60 * 1000L)
                val allDailyStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, historyRangeStart, yesterdayStart)

                val dailyTotals = mutableMapOf<Long, Long>()
                allDailyStats.forEach { stat ->
                    if (stat.packageName == packageName) {
                        val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                        if (time > 0) {
                            cal.timeInMillis = stat.firstTimeStamp
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            val dayKey = cal.timeInMillis
                            if (dayKey < yesterdayStart) {
                                dailyTotals[dayKey] = (dailyTotals[dayKey] ?: 0L) + time
                            }
                        }
                    }
                }

                val history = mutableListOf<DailyUsage>()
                for (i in 0 until 21) {
                    val dStart = todayStart - (i * 24 * 60 * 60 * 1000L)
                    val dayTotal = when (i) {
                        0 -> todayUsage
                        1 -> yesterdayUsage
                        else -> dailyTotals[dStart] ?: 0L
                    }
                    history.add(DailyUsage(dStart, dayTotal))
                }

                val percentageChange = if (yesterdayUsage > 0) {
                    ((todayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                } else if (todayUsage > 0) { 100f } else { 0f }

                val shield = allShields.find { it.packageName == packageName }

                var appName = packageName
                var icon: android.graphics.drawable.Drawable? = null
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                    icon = pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {}

                Triple(todayUsage, yesterdayUsage, Triple(percentageChange, history.reversed(), Triple(shield, appName, icon)))
            }

            val (todayUsage, yesterdayUsage, rest1) = detailData
            val (percentageChange, history, rest2) = rest1
            val (shield, appName, icon) = rest2

            _appDetailUiState.update { currentState ->
                if (currentState.packageName == packageName) {
                    currentState.copy(
                        todayUsage = todayUsage,
                        yesterdayUsage = yesterdayUsage,
                        percentageChange = percentageChange,
                        usageHistory = history,
                        shieldEntity = shield,
                        type = shield?.type,
                        appName = appName,
                        icon = icon ?: currentState.icon
                    )
                } else {
                    AppDetailUiState(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        type = shield?.type,
                        todayUsage = todayUsage,
                        yesterdayUsage = yesterdayUsage,
                        percentageChange = percentageChange,
                        usageHistory = history,
                        shieldEntity = shield
                    )
                }
            }
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

    // Todo actions
    fun addTodo(packageName: String, content: String) {
        viewModelScope.launch {
            val currentTodos = _appDetailUiState.value.todos
            val nextOrder = (currentTodos.maxOfOrNull { it.order } ?: -1) + 1
            shieldRepository.insertTodo(
                com.etrisad.zenith.data.local.entity.TodoEntity(
                    packageName = packageName,
                    content = content,
                    order = nextOrder
                )
            )
        }
    }

    fun toggleTodo(todo: com.etrisad.zenith.data.local.entity.TodoEntity) {
        viewModelScope.launch {
            shieldRepository.updateTodo(todo.copy(isDone = !todo.isDone))
        }
    }

    fun deleteTodo(todo: com.etrisad.zenith.data.local.entity.TodoEntity) {
        viewModelScope.launch {
            shieldRepository.deleteTodo(todo)
        }
    }

    fun reorderTodos(packageName: String, todos: List<com.etrisad.zenith.data.local.entity.TodoEntity>) {
        viewModelScope.launch {
            todos.forEachIndexed { index, todo ->
                shieldRepository.updateTodo(todo.copy(order = index))
            }
        }
    }
}
