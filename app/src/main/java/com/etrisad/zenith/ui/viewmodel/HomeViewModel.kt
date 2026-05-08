package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeVisible: Long,
    val icon: android.graphics.drawable.Drawable? = null,
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isLive: Boolean = false
)

data class DailyUsage(
    val date: Long,
    val totalTime: Long,
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isLive: Boolean = false
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
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val shieldEntity: ShieldEntity? = null,
    val isPaused: Boolean = false,
    val pauseEndTimestamp: Long = 0L
)

data class HourlyUsageInfo(
    val hour: Int,
    val usageTimeMillis: Long,
    val apps: List<AppUsageInfo> = emptyList()
)

data class HomeUiState(
    val totalScreenTime: Long = 0L,
    val yesterdayScreenTime: Long = 0L,
    val percentageChange: Float = 0f,
    val dailyUsageHistory: List<DailyUsage> = emptyList(),
    val hourlyUsage: List<HourlyUsageInfo> = emptyList(),
    val snapshotStamps: List<AppUsageInfo> = emptyList(),
    val topApps: List<AppUsageInfo> = emptyList(),
    val allAppsUsage: List<AppUsageInfo> = emptyList(),
    val activeShields: List<ShieldEntity> = emptyList(),
    val activeGoals: List<ShieldEntity> = emptyList(),
    val shieldSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val goalSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val globalCurrentStreak: Int = 0,
    val globalBestStreak: Int = 0,
    val targetMillis: Long = 0L,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStartTime: String = "22:00",
    val bedtimeEndTime: String = "07:00",
    val bedtimeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val selectedDateMillis: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
)

sealed class UsageRecord {
    data class Database(val entity: DailyUsageEntity) : UsageRecord()
    data class Live(val packageName: String, val usageTimeMillis: Long) : UsageRecord()
}

data class UsageHistoryGroup(
    val date: String,
    val records: List<UsageRecord>,
    val totalTimeMillis: Long,
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isMissing: Boolean = false,
    val isLive: Boolean = false,
    val hasSnapshot: Boolean = false,
    val hasHourlyUsage: Boolean = false
)

class HomeViewModel(
    context: Context,
    private val shieldRepository: ShieldRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val context = context.applicationContext

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val appInfoCache = mutableMapOf<String, Pair<String, android.graphics.drawable.Drawable?>>()
    private var refreshJob: Job? = null

    val allDatabaseUsage: Flow<List<DailyUsageEntity>> = shieldRepository.getAllUsage()

    val fullUsageHistory: Flow<List<UsageHistoryGroup>> = combine(
        allDatabaseUsage,
        shieldRepository.getDatesWithHourlyUsage()
    ) { dbList, hourlyDates ->
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Calendar.getInstance()
        val todayStr = dateFormat.format(today.time)
        
        // Find earliest date in DB or default to 7 days ago if empty
        val earliestDateStr = dbList.lastOrNull()?.date ?: dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time)
        val earliestCal = Calendar.getInstance().apply {
            time = dateFormat.parse(earliestDateStr) ?: time
        }
        
        val groups = mutableListOf<UsageHistoryGroup>()
        val cal = Calendar.getInstance() // Start from today and go backwards
        
        while (!cal.before(earliestCal)) {
            val dateStr = dateFormat.format(cal.time)
            val isToday = dateStr == todayStr
            
            val dbRecords = dbList.filter { it.date == dateStr }
            val dbTotal = dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
            val systemTotal = globalFallbackMap[dateStr] ?: 0L
            
            val hasHourly = hourlyDates.contains(dateStr)
            val hasSnap = dbRecords.any { it.packageName != "TOTAL" }

            if (isToday) {
                val liveRecords = mutableListOf<UsageRecord>()
                dbRecords.forEach { liveRecords.add(UsageRecord.Database(it)) }
                
                val liveTotal = uiState.value.totalScreenTime
                if (dbRecords.none { it.packageName == "TOTAL" } || 
                    (dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L) < liveTotal - 60000) {
                    liveRecords.add(UsageRecord.Live("TOTAL", liveTotal))
                }
                
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = liveRecords, 
                    totalTimeMillis = maxOf(dbTotal, liveTotal),
                    hasDatabaseRecord = dbRecords.isNotEmpty(),
                    hasSystemData = true,
                    isLive = true,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly
                ))
            } else if (dbRecords.isEmpty() && systemTotal == 0L) {
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = emptyList(), 
                    totalTimeMillis = 0L,
                    isMissing = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly
                ))
            } else if (dbRecords.isEmpty() && systemTotal > 0L) {
                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = listOf(UsageRecord.Live("TOTAL", systemTotal)),
                    totalTimeMillis = systemTotal,
                    hasSystemData = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly
                ))
            } else {
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = dbRecords.map { UsageRecord.Database(it) },
                    totalTimeMillis = dbTotal,
                    hasDatabaseRecord = true,
                    hasSystemData = systemTotal > 0L,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly
                ))
            }
            
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        groups
    }

    private var allShields: List<ShieldEntity> = emptyList()

    private val _appDetailUiState = MutableStateFlow(AppDetailUiState())
    val appDetailUiState: StateFlow<AppDetailUiState> = _appDetailUiState.asStateFlow()

    private var appDetailJob: Job? = null

    private var globalHistory: List<DailyUsageEntity> = emptyList()
    private var allHistory: List<DailyUsageEntity> = emptyList()
    private var globalFallbackMap: Map<String, Long> = emptyMap()
    private var detailFallbackMap: Map<String, Long> = emptyMap()
    private var currentTargetMinutes: Int = 0
    private var prefGlobalBestStreak: Int = 0

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                updateShieldedLists()

                val currentPkg = _appDetailUiState.value.packageName
                if (currentPkg.isNotEmpty()) {
                    val shield = shields.find { it.packageName == currentPkg }
                    _appDetailUiState.update { it.copy(
                        shieldEntity = shield,
                        type = shield?.type,
                        isPaused = shield?.isPaused ?: false,
                        pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                        currentStreak = shield?.currentStreak ?: 0,
                        bestStreak = shield?.bestStreak ?: 0
                    ) }
                }
            }
        }

        viewModelScope.launch {
            shieldRepository.getAllUsage().collect { history ->
                allHistory = history
                refreshUsageStats()
            }
        }

        viewModelScope.launch {
            shieldRepository.getLastNDaysGlobalUsage(60).collect { history ->
                globalHistory = history
                updateGlobalFallback()
                refreshUsageStats()
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                currentTargetMinutes = prefs.screenTimeTargetMinutes
                prefGlobalBestStreak = prefs.globalBestStreak
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }
                refreshUsageStats()
            }
        }

        refreshUsageStats()
        startRealTimeUpdates()
    }

    private fun getMidnight(offsetDaysFromToday: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -offsetDaysFromToday)
        return cal.timeInMillis
    }

    private fun updateGlobalFallback() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val launcherApps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val fallbackStart = getMidnight(30)
        val now = System.currentTimeMillis()
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, fallbackStart, now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dailyData = mutableMapOf<String, MutableMap<String, Long>>()

        stats.forEach { stat ->
            val pkg = stat.packageName
            if (pkg in excludePackages || pkg !in launcherApps) return@forEach
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time <= 0) return@forEach

            val dateStr = dateFormat.format(Date(stat.firstTimeStamp))
            val pkgMap = dailyData.getOrPut(dateStr) { mutableMapOf() }
            pkgMap[pkg] = maxOf(pkgMap[pkg] ?: 0L, time)
        }

        globalFallbackMap = dailyData.mapValues { it.value.values.sumOf { time -> time } }
    }

    private fun updatePackageFallback(packageName: String) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val fallbackStart = getMidnight(30)
        val now = System.currentTimeMillis()
        
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, fallbackStart, now)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableMapOf<String, Long>()

        stats.forEach { stat ->
            if (stat.packageName != packageName) return@forEach
            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
            if (time <= 0) return@forEach

            val dateStr = dateFormat.format(Date(stat.firstTimeStamp))
            result[dateStr] = maxOf(result[dateStr] ?: 0L, time)
        }
        detailFallbackMap = result
    }

    fun selectDate(dateMillis: Long?) {
        val cal = Calendar.getInstance()
        if (dateMillis != null) cal.timeInMillis = dateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        val date = cal.timeInMillis
        if (_uiState.value.selectedDateMillis == date && refreshJob?.isActive == true) return

        _uiState.update { it.copy(selectedDateMillis = date) }
        refreshUsageStats()
    }

    private fun refreshUsageStats() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launcherPackage = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName

            val launcherApps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).map { it.activityInfo.packageName }.toSet()

            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

            val now = System.currentTimeMillis()
            val selectedDate = _uiState.value.selectedDateMillis
            val todayStart = getMidnight(0)
            
            val isSelectedToday = selectedDate == todayStart
            
            val calDate = Calendar.getInstance()
            calDate.timeInMillis = selectedDate
            calDate.set(Calendar.HOUR_OF_DAY, 0)
            calDate.set(Calendar.MINUTE, 0)
            calDate.set(Calendar.SECOND, 0)
            calDate.set(Calendar.MILLISECOND, 0)
            val dayStart = calDate.timeInMillis
            
            calDate.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = if (dayStart + (24 * 60 * 60 * 1000L) > now) now else calDate.timeInMillis
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // 1. Calculate Summary (Only if needed or periodically)
            val trueTodayStats = usm.queryAndAggregateUsageStats(todayStart, now)
            var totalToday = 0L
            trueTodayStats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (time > 0) totalToday += time
            }

            // 2. Calculate Top Apps for Selected Day
            val appList = mutableListOf<AppUsageInfo>()
            val dayStats = if (isSelectedToday) trueTodayStats else usm.queryAndAggregateUsageStats(dayStart, dayEnd)
            
            dayStats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (time > 0) {
                    val cached = appInfoCache[pkg]
                    if (cached != null) {
                        appList.add(AppUsageInfo(pkg, cached.first, time, cached.second))
                    } else {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(appInfo)
                            appInfoCache[pkg] = label to icon
                            appList.add(AppUsageInfo(pkg, label, time, icon))
                        } catch (_: Exception) {}
                    }
                }
            }

            val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5)
            val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }

            // 3. Calculate Hourly for Selected Day
            val hourlyMap = mutableMapOf<Int, Long>()
            val hourlyAppUsage = mutableMapOf<Int, MutableMap<String, Long>>()
            val events = usm.queryEvents(dayStart, dayEnd)
            val event = android.app.usage.UsageEvents.Event()
            val lastEventTime = mutableMapOf<String, Long>()
            val cal = Calendar.getInstance()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val time = event.timeStamp
                
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND || 
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastEventTime[pkg] = time
                } else if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND || 
                           event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    val startTime = lastEventTime.remove(pkg)
                    if (startTime != null) {
                        val duration = time - startTime
                        if (duration > 0) {
                            cal.timeInMillis = startTime
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            hourlyMap[hour] = (hourlyMap[hour] ?: 0L) + duration

                            val pkgMap = hourlyAppUsage.getOrPut(hour) { mutableMapOf() }
                            pkgMap[pkg] = (pkgMap[pkg] ?: 0L) + duration
                        }
                    }
                }
            }

            val hourlyUsage = (0..23).map { hour ->
                val appsInHour = hourlyAppUsage[hour]?.mapNotNull { (pkg, time) ->
                    if (pkg in excludePackages || pkg !in launcherApps) return@mapNotNull null
                    val cached = appInfoCache[pkg]
                    if (cached != null) {
                        AppUsageInfo(pkg, cached.first, time, cached.second)
                    } else {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val icon = pm.getApplicationIcon(appInfo)
                            appInfoCache[pkg] = label to icon
                            AppUsageInfo(pkg, label, time, icon)
                        } catch (_: Exception) { null }
                    }
                }?.sortedByDescending { it.totalTimeVisible } ?: emptyList()

                HourlyUsageInfo(hour, hourlyMap[hour] ?: 0L, appsInHour)
            }

            // 4. Calculate Trends/History (Periodically or on init)
            // To be even lighter, we only recalculate these if they are empty or "today" changed
            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayDateStr = dateFormat.format(yesterdayCal.time)

            val totalYesterday = globalHistory.find { it.date == yesterdayDateStr }?.usageTimeMillis
                ?: globalFallbackMap[yesterdayDateStr]
                ?: 0L

            val history = (0 until 21).map { i ->
                val dStart = getMidnight(i)
                val dateStr = dateFormat.format(Date(dStart))
                
                val dbEntry = globalHistory.find { it.date == dateStr }
                val hasSystemData = globalFallbackMap[dateStr] != null
                val dayTotal = if (i == 0) {
                    totalToday
                } else {
                    dbEntry?.usageTimeMillis 
                        ?: globalFallbackMap[dateStr] 
                        ?: 0L
                }
                DailyUsage(
                    date = dStart, 
                    totalTime = dayTotal, 
                    hasDatabaseRecord = dbEntry != null,
                    hasSystemData = hasSystemData,
                    isLive = i == 0
                )
            }

            val percentageChange = when {
                totalYesterday > 0 -> ((totalToday - totalYesterday).toFloat() / totalYesterday) * 100
                totalToday > 0     -> 100f
                else               -> 0f
            }

            // 5. Calculate Snapshot Stamps (Only if not already set for today)
            val currentSnapshotStamps = _uiState.value.snapshotStamps
            val snapshotStamps = if (currentSnapshotStamps.isEmpty() || isSelectedToday) {
                (0..20).map { i ->
                    val dStart = getMidnight(i)
                    val dEnd = if (i == 0) now else dStart + (24 * 60 * 60 * 1000L)
                    val dateStr = dateFormat.format(Date(dStart))
                    
                    val dayStatsMap = if (i == 0) trueTodayStats else usm.queryAndAggregateUsageStats(dStart, dEnd)
                    val topEntry = dayStatsMap.filter { (pkg, _) -> 
                        pkg !in excludePackages && pkg in launcherApps 
                    }.maxByOrNull { (_, stat) -> 
                        stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground) 
                    }
                    
                    val topPackage = topEntry?.key
                    val usageTime = topEntry?.value?.let { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) } ?: 0L

                    val hasDb = allHistory.any { it.date == dateStr && it.packageName != "TOTAL" }
                    val hasSys = globalFallbackMap[dateStr] != null

                    if (topPackage != null) {
                        val cached = appInfoCache[topPackage]
                        if (cached != null) {
                            AppUsageInfo(topPackage, cached.first, usageTime, cached.second, hasDb, hasSys, i == 0)
                        } else {
                            try {
                                val appInfo = pm.getApplicationInfo(topPackage, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                val icon = pm.getApplicationIcon(appInfo)
                                appInfoCache[topPackage] = label to icon
                                AppUsageInfo(topPackage, label, usageTime, icon, hasDb, hasSys, i == 0)
                            } catch (_: Exception) {
                                AppUsageInfo("", "", 0, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0)
                            }
                        }
                    } else {
                        AppUsageInfo("", "", 0, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0)
                    }
                }.reversed()
            } else {
                currentSnapshotStamps
            }

            val liveShields = allShields.map { shield ->
                val usage = trueTodayStats.getUsageTime(shield.packageName)
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                shield.copy(remainingTimeMillis = (limitMillis - usage).coerceAtLeast(0L))
            }

            val targetMillis = currentTargetMinutes * 60 * 1000L
            var liveStreak = 0
            var bestStreakFromHistory = 0

            if (targetMillis > 0) {
                if (totalToday <= targetMillis) {
                    liveStreak = 1
                    val c = Calendar.getInstance()
                    for (i in 1..60) {
                        c.time = Date()
                        c.add(Calendar.DAY_OF_YEAR, -i)
                        val dStr = dateFormat.format(c.time)
                        val usage = globalHistory.find { it.date == dStr }?.usageTimeMillis ?: globalFallbackMap[dStr]
                        if (usage != null && usage <= targetMillis) {
                            liveStreak++
                        } else if (usage != null) {
                            break
                        } else {
                            break
                        }
                    }
                }

                var currentTempStreak = 0
                for (i in 60 downTo 0) {
                    val dStart = getMidnight(i)
                    val dStr = dateFormat.format(Date(dStart))
                    val usage = if (i == 0) totalToday else (globalHistory.find { it.date == dStr }?.usageTimeMillis ?: globalFallbackMap[dStr])
                    if (usage != null && usage <= targetMillis) {
                        currentTempStreak++
                    } else {
                        bestStreakFromHistory = maxOf(bestStreakFromHistory, currentTempStreak)
                        currentTempStreak = 0
                    }
                }
                bestStreakFromHistory = maxOf(bestStreakFromHistory, currentTempStreak)
            }

            _uiState.update { state -> state.copy(
                totalScreenTime      = totalToday,
                yesterdayScreenTime  = totalYesterday,
                percentageChange     = percentageChange,
                dailyUsageHistory    = history.reversed(),
                hourlyUsage          = hourlyUsage,
                snapshotStamps       = snapshotStamps,
                topApps              = topApps,
                allAppsUsage         = allAppsUsage,
                activeShields = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }, state.shieldSortType),
                activeGoals   = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }, state.goalSortType),
                globalCurrentStreak = liveStreak,
                globalBestStreak = maxOf(prefGlobalBestStreak, bestStreakFromHistory),
                targetMillis = targetMillis
            ) }

            viewModelScope.launch {
                userPreferencesRepository.setLastKnownDailyUsage(totalToday, dateFormat.format(Date(now)))
            }
        }
    }

    fun loadAppDetail(packageName: String) {
        if (_appDetailUiState.value.packageName == packageName && appDetailJob?.isActive == true) return
        
        val isNewPackage = _appDetailUiState.value.packageName != packageName
        appDetailJob?.cancel()
        
        if (isNewPackage) {
            _appDetailUiState.value = AppDetailUiState(packageName = packageName)
        }

        appDetailJob = viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm  = context.packageManager
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStart = getMidnight(0)

            var appName = packageName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon    = pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {}

            _appDetailUiState.update { it.copy(appName = appName, icon = icon) }
            updatePackageFallback(packageName)

            shieldRepository.getLastNDaysUsageForPackage(packageName, 21).collect { historyFromDb ->
                val currentNow = System.currentTimeMillis()
                val currentTodayUsage = usm.queryAndAggregateUsageStats(todayStart, currentNow)
                    .getUsageTime(packageName)

                val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val yesterdayDateStr = dateFormat.format(yesterdayCal.time)

                val yesterdayUsage = historyFromDb.find { it.date == yesterdayDateStr }?.usageTimeMillis
                    ?: detailFallbackMap[yesterdayDateStr]
                    ?: 0L

                val history = (0 until 21).map { i ->
                    val dStart = getMidnight(i)
                    val dateStr = dateFormat.format(Date(dStart))
                    
                    val dbEntry = historyFromDb.find { it.date == dateStr }
                    val hasSystemData = detailFallbackMap[dateStr] != null
                    val dayTotal = if (i == 0) {
                        currentTodayUsage
                    } else {
                        dbEntry?.usageTimeMillis 
                            ?: detailFallbackMap[dateStr]
                            ?: 0L
                    }
                    DailyUsage(
                        date = dStart, 
                        totalTime = dayTotal, 
                        hasDatabaseRecord = dbEntry != null,
                        hasSystemData = hasSystemData,
                        isLive = i == 0
                    )
                }

                val percentageChange = when {
                    yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                    currentTodayUsage > 0     -> 100f
                    else               -> 0f
                }

                val shield = allShields.find { it.packageName == packageName }

                _appDetailUiState.update { it.copy(
                    packageName      = packageName,
                    appName          = appName,
                    icon             = icon,
                    type             = shield?.type,
                    todayUsage       = currentTodayUsage,
                    yesterdayUsage   = yesterdayUsage,
                    percentageChange = percentageChange,
                    usageHistory     = history.reversed(),
                    currentStreak    = shield?.currentStreak ?: 0,
                    bestStreak       = shield?.bestStreak ?: 0,
                    shieldEntity     = shield,
                    isPaused         = shield?.isPaused ?: false,
                    pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L
                ) }
            }
        }
    }

    fun pauseShield(durationHours: Int?) {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val pauseEndTimestamp = if (durationHours != null) {
            System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)
        } else {
            0L
        }

        val updatedShield = shield.copy(
            isPaused = true,
            pauseEndTimestamp = pauseEndTimestamp
        )

        _appDetailUiState.update { it.copy(
            shieldEntity = updatedShield,
            isPaused = true,
            pauseEndTimestamp = pauseEndTimestamp
        ) }

        viewModelScope.launch {
            shieldRepository.updateShield(updatedShield)
        }
    }

    fun resumeShield() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val updatedShield = shield.copy(
            isPaused = false,
            pauseEndTimestamp = 0L
        )

        _appDetailUiState.update { it.copy(
            shieldEntity = updatedShield,
            isPaused = false,
            pauseEndTimestamp = 0L
        ) }

        viewModelScope.launch {
            shieldRepository.updateShield(updatedShield)
        }
    }

    private fun Map<String, android.app.usage.UsageStats>.getUsageTime(packageName: String): Long {
        return this[packageName]?.let {
            it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground)
        } ?: 0L
    }

    fun onShieldSortTypeChange(sortType: ShieldSortType) {
        _uiState.update { currentState ->
            currentState.copy(
                shieldSortType = sortType,
                activeShields = sortShields(currentState.activeShields, sortType)
            )
        }
    }

    fun onGoalSortTypeChange(sortType: ShieldSortType) {
        _uiState.update { currentState ->
            currentState.copy(
                goalSortType = sortType,
                activeGoals = sortShields(currentState.activeGoals, sortType)
            )
        }
    }

    private fun updateShieldedLists() {
        refreshUsageStats()
    }

    private fun sortShields(shields: List<ShieldEntity>, sortType: ShieldSortType): List<ShieldEntity> {
        return when (sortType) {
            ShieldSortType.ALPHABETICAL  -> shields.sortedBy { it.appName.lowercase() }
            ShieldSortType.REMAINING_TIME -> shields.sortedBy {
                if (it.timeLimitMinutes > 0) it.remainingTimeMillis.toDouble() / (it.timeLimitMinutes * 60 * 1000L) else 0.0
            }
        }
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(10000)
                refreshUsageStats()
                refreshCurrentAppDetailUsage()
            }
        }
    }

    private fun refreshCurrentAppDetailUsage() {
        val currentState = _appDetailUiState.value
        val packageName = currentState.packageName
        
        // Jangan update jika packageName kosong atau data dasar (appName) belum dimuat
        if (packageName.isEmpty() || currentState.appName == packageName) return

        viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val todayStart = getMidnight(0)
            val currentNow = System.currentTimeMillis()
            val currentTodayUsage = usm.queryAndAggregateUsageStats(todayStart, currentNow)
                .getUsageTime(packageName)

            val yesterdayUsage = currentState.yesterdayUsage
            val percentageChange = when {
                yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                currentTodayUsage > 0 -> 100f
                else -> 0f
            }

            _appDetailUiState.update { it.copy(
                todayUsage = currentTodayUsage,
                percentageChange = percentageChange
            ) }
        }
    }

    fun clearAppDetail(packageName: String) {
        // Hanya hentikan job, jangan reset state ke kosong agar tidak menyebabkan data hilang tiba-tiba di UI
        if (_appDetailUiState.value.packageName == packageName) {
            appDetailJob?.cancel()
        }
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
            _appDetailUiState.update { it.copy(type = null, shieldEntity = null) }
        }
    }

    fun formatDuration(millis: Long): String {
        val hours   = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
