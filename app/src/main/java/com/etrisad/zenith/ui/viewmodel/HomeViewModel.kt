package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.UsageSyncManager
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
    val isLive: Boolean = false,
    val sessionCount: Int = 0
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
    val averageUsage: Long = 0L,
    val totalSessions: Int = 0,
    val peakHour: Int = -1,
    val percentageChange: Float = 0f,
    val usageHistory: List<DailyUsage> = emptyList(),
    val hourlyUsage: List<Long> = emptyList(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val shieldEntity: ShieldEntity? = null,
    val isPaused: Boolean = false,
    val pauseEndTimestamp: Long = 0L,
    val isSettingsSheetOpen: Boolean = false
)

data class HourlyUsageInfo(
    val hour: Int,
    val usageTimeMillis: Long,
    val apps: List<AppUsageInfo> = emptyList(),
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isLive: Boolean = false
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
    val weeklyAvgTime: Long = 0L,
    val weeklyTopApps: List<AppUsageInfo> = emptyList(),
    val shieldUsage: Long = 0L,
    val goalUsage: Long = 0L,
    val otherUsage: Long = 0L,
    val bedtimeEnabled: Boolean = false,
    val bedtimeStartTime: String = "22:00",
    val bedtimeEndTime: String = "07:00",
    val bedtimeDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val selectedDateMillis: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis,
    val isLoading: Boolean = true
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
    val hasHourlyUsage: Boolean = false,
    val hasPiechartData: Boolean = false,
    val systemTotalMillis: Long = 0L,
    val shieldTotalMillis: Long = 0L,
    val goalTotalMillis: Long = 0L,
    val otherTotalMillis: Long = 0L
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
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

        val earliestDateStr = dbList.lastOrNull()?.date ?: dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time)
        val earliestCal = Calendar.getInstance().apply {
            time = dateFormat.parse(earliestDateStr) ?: time
        }
        
        val groups = mutableListOf<UsageHistoryGroup>()
        val cal = Calendar.getInstance()

        while (!cal.before(earliestCal)) {
            val dateStr = dateFormat.format(cal.time)
            val isToday = dateStr == todayStr
            
            val dbRecords = dbList.filter { it.date == dateStr }
            val dbTotal = dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
            val systemRecords = if (preferSystemUsageHistory || isToday) globalFallbackMap[dateStr] ?: emptyList() else emptyList()
            val systemTotal = if (preferSystemUsageHistory || isToday) systemRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L else 0L
            
            val hasHourly = hourlyDates.contains(dateStr)
            val hasSnap = dbRecords.any { it.packageName != "TOTAL" && it.packageName != "SHIELD_TOTAL" && it.packageName != "GOAL_TOTAL" && it.packageName != "OTHER_TOTAL" }
            val hasPiechart = dbRecords.any { it.packageName == "SHIELD_TOTAL" || it.packageName == "GOAL_TOTAL" || it.packageName == "OTHER_TOTAL" }
            
            val dbShieldTotal = dbRecords.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: 0L
            val dbGoalTotal = dbRecords.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: 0L
            val dbOtherTotal = dbRecords.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis ?: 0L

            if (isToday) {
                val liveRecords = mutableListOf<UsageRecord>()
                dbRecords.forEach { liveRecords.add(UsageRecord.Database(it)) }
                
                val liveTotal = uiState.value.totalScreenTime
                val liveShield = uiState.value.shieldUsage
                val liveGoal = uiState.value.goalUsage
                val liveOther = uiState.value.otherUsage

                if (dbRecords.none { it.packageName == "TOTAL" } || 
                    (dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L) < liveTotal - 60000) {
                    liveRecords.add(UsageRecord.Live("TOTAL", liveTotal))
                }

                if (dbRecords.none { it.packageName == "SHIELD_TOTAL" }) liveRecords.add(UsageRecord.Live("SHIELD_TOTAL", liveShield))
                if (dbRecords.none { it.packageName == "GOAL_TOTAL" }) liveRecords.add(UsageRecord.Live("GOAL_TOTAL", liveGoal))
                if (dbRecords.none { it.packageName == "OTHER_TOTAL" }) liveRecords.add(UsageRecord.Live("OTHER_TOTAL", liveOther))
                
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = liveRecords, 
                    totalTimeMillis = maxOf(dbTotal, liveTotal),
                    hasDatabaseRecord = dbRecords.isNotEmpty(),
                    hasSystemData = true,
                    isLive = true,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly,
                    hasPiechartData = true,
                    systemTotalMillis = liveTotal,
                    shieldTotalMillis = maxOf(dbShieldTotal, liveShield),
                    goalTotalMillis = maxOf(dbGoalTotal, liveGoal),
                    otherTotalMillis = maxOf(dbOtherTotal, liveOther)
                ))
            } else if (dbRecords.isEmpty() && systemRecords.isEmpty()) {
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = emptyList(), 
                    totalTimeMillis = 0L,
                    isMissing = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly,
                    systemTotalMillis = 0L
                ))
            } else if (dbRecords.isEmpty() && systemRecords.isNotEmpty()) {
                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = systemRecords,
                    totalTimeMillis = systemTotal,
                    hasSystemData = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly,
                    systemTotalMillis = systemTotal
                ))
            } else {
                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = dbRecords.map { UsageRecord.Database(it) },
                    totalTimeMillis = dbTotal,
                    hasDatabaseRecord = true,
                    hasSystemData = systemTotal > 0L,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly,
                    hasPiechartData = hasPiechart,
                    systemTotalMillis = systemTotal,
                    shieldTotalMillis = dbShieldTotal,
                    goalTotalMillis = dbGoalTotal,
                    otherTotalMillis = dbOtherTotal
                ))
            }
            
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        groups
    }

    val repairableData: Flow<List<UsageHistoryGroup>> = fullUsageHistory.map { groups ->
        groups.filter { (it.isMissing || !it.hasDatabaseRecord) && it.hasSystemData && !it.isLive }
    }

    private val _isRepairing = MutableStateFlow(false)
    val isRepairing = _isRepairing.asStateFlow()

    fun repairData(date: String) {
        val systemRecords = globalFallbackMap[date] ?: return
        viewModelScope.launch {
            _isRepairing.value = true
            try {
                val shieldPkgs = allShields.filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
                val goalPkgs = allShields.filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()
                
                var sUsage = 0L
                var gUsage = 0L
                var total = 0L

                systemRecords.forEach { record ->
                    shieldRepository.insertDailyUsage(
                        DailyUsageEntity(
                            date = date,
                            packageName = record.packageName,
                            usageTimeMillis = record.usageTimeMillis
                        )
                    )
                    
                    if (record.packageName == "TOTAL") {
                        total = record.usageTimeMillis
                    } else if (record.packageName in shieldPkgs) {
                        sUsage += record.usageTimeMillis
                    } else if (record.packageName in goalPkgs) {
                        gUsage += record.usageTimeMillis
                    }
                }
                
                val oUsage = (total - (sUsage + gUsage)).coerceAtLeast(0L)
                shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "SHIELD_TOTAL", usageTimeMillis = sUsage))
                shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "GOAL_TOTAL", usageTimeMillis = gUsage))
                shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "OTHER_TOTAL", usageTimeMillis = oUsage))
            } finally {
                delay(500)
                _isRepairing.value = false
            }
        }
    }

    private var allShields: List<ShieldEntity> = emptyList()

    private val _appDetailUiState = MutableStateFlow(AppDetailUiState())
    val appDetailUiState: StateFlow<AppDetailUiState> = _appDetailUiState.asStateFlow()

    private var appDetailJob: Job? = null

    private var globalHistory: List<DailyUsageEntity> = emptyList()
    private var allHistory: List<DailyUsageEntity> = emptyList()
    private var globalFallbackMap: Map<String, List<UsageRecord.Live>> = emptyMap()
    private var detailFallbackMap: Map<String, Long> = emptyMap()
    private var currentTargetMinutes: Int = 0
    private var prefGlobalBestStreak: Int = 0
    private var preferSystemUsageHistory: Boolean = true

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
                refreshUsageStats(showLoading = false)
            }
        }

        viewModelScope.launch {
            shieldRepository.getLastNDaysGlobalUsage(60).collect { history ->
                globalHistory = history
                updateGlobalFallback()
                refreshUsageStats(showLoading = false)
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                currentTargetMinutes = prefs.screenTimeTargetMinutes
                prefGlobalBestStreak = prefs.globalBestStreak
                preferSystemUsageHistory = prefs.preferSystemUsageHistory
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }
                refreshUsageStats(showLoading = false)
            }
        }

        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -21)
            val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            shieldRepository.deleteOldHourlyUsage(thresholdDate)
        }

        // Perform initial load and then sync
        startRealTimeUpdates()
        syncDataNow()
    }

    fun syncDataNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val syncManager = UsageSyncManager(context, shieldRepository, userPreferencesRepository)
            syncManager.syncUsageData()
            refreshUsageStats()
        }
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

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val resultMap = mutableMapOf<String, List<UsageRecord.Live>>()

        val now = System.currentTimeMillis()
        val todayStart = getMidnight(0)

        for (i in 0..30) {
            val start = getMidnight(i)
            val end = if (i == 0) now else getMidnight(i - 1)
            
            val dateStr = dateFormat.format(Date(start))
            val stats = usm.queryAndAggregateUsageStats(start, end)
            
            val dayRecords = mutableListOf<UsageRecord.Live>()
            var dayTotal = 0L
            val timeSinceStart = end - start

            stats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                var time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (i == 0 && time > timeSinceStart + 10000) {
                    time = timeSinceStart
                }
                if (time > 0) {
                    dayTotal += time
                    dayRecords.add(UsageRecord.Live(pkg, time))
                }
            }
            if (dayTotal > 0) {
                dayRecords.add(UsageRecord.Live("TOTAL", dayTotal))
                resultMap[dateStr] = dayRecords.sortedByDescending { it.usageTimeMillis }
            }
        }

        globalFallbackMap = resultMap
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
        refreshUsageStats(showLoading = true)
    }

    private fun refreshUsageStats(showLoading: Boolean = true) {
        if (showLoading) _uiState.update { it.copy(isLoading = true) }
        
        // If a refresh is already running, don't cancel it if we don't want to show loading
        // because we want the current "loading" one to finish its job.
        if (!showLoading && refreshJob?.isActive == true) return

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
            val timeSinceMidnight = now - todayStart
            
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

            val trueTodayStats = usm.queryAndAggregateUsageStats(todayStart, now)
            var totalToday = 0L
            trueTodayStats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                var time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (isSelectedToday && time > timeSinceMidnight + 10000) {
                    time = timeSinceMidnight
                }
                if (time > 0) totalToday += time
            }

            val appTotals = mutableMapOf<String, Long>()
            val appSessionCounts = mutableMapOf<String, Int>()
            val hourlyAppUsage = mutableMapOf<Int, MutableMap<String, Long>>()

            if (isSelectedToday) {
                // Use the unified helper to ensure consistency
                val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true)
                
                detailedUsage.appUsageMap.forEach { (pkg, time) ->
                    if (pkg !in excludePackages && pkg in launcherApps) {
                        val cappedTime = time.coerceAtMost(timeSinceMidnight + 5000)
                        if (cappedTime > 0) appTotals[pkg] = cappedTime
                    }
                }
                
                detailedUsage.sessionCounts.forEach { (pkg, count) ->
                    appSessionCounts[pkg] = count
                }
                
                detailedUsage.hourlyUsageMap.forEach { (hour, pkgMap) ->
                    val filteredPkgMap = pkgMap.filter { it.key in launcherApps && it.key !in excludePackages }
                    if (filteredPkgMap.isNotEmpty()) {
                        hourlyAppUsage[hour] = filteredPkgMap.toMutableMap()
                    }
                }

                totalToday = appTotals.values.sum().coerceAtMost(timeSinceMidnight)
            } else {
                val dayStats = usm.queryAndAggregateUsageStats(dayStart, dayEnd)
                dayStats.forEach { (pkg, stat) ->
                    if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                    if (time > 0) appTotals[pkg] = time
                }
            }


            val appList = appTotals.mapNotNull { (pkg, time) ->
                val sessions = appSessionCounts[pkg] ?: (if (time > 4000) 1 else 0)
                val cached = appInfoCache[pkg]
                if (cached != null) {
                    AppUsageInfo(pkg, cached.first, time, cached.second, sessionCount = sessions)
                } else {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        appInfoCache[pkg] = label to icon
                        AppUsageInfo(pkg, label, time, icon, sessionCount = sessions)
                    } catch (_: Exception) { null }
                }
            }

            val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5)
            val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }

            val rawTotals = appTotals.keys.associateWith { pkg ->
                (0..23).sumOf { h -> hourlyAppUsage[h]?.get(pkg) ?: 0L }
            }

            val selectedDateStr = dateFormat.format(Date(selectedDate))
            
            val selectedDayHistory = allHistory.filter { it.date == selectedDateStr }
            val storedShieldUsage = selectedDayHistory.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis
            val storedGoalUsage = selectedDayHistory.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis
            val storedOtherUsage = selectedDayHistory.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis

            val liveShields = allShields.map { shield ->
                val usage = if (isSelectedToday) (appTotals[shield.packageName] ?: 0L) else trueTodayStats.getUsageTime(shield.packageName)
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                shield.copy(remainingTimeMillis = (limitMillis - usage).coerceAtLeast(0L))
            }

            val (finalShieldUsage, finalGoalUsage, finalOtherUsage) = if (isSelectedToday || (storedShieldUsage == null && storedGoalUsage == null)) {
                val shieldPkgs = liveShields.filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
                val goalPkgs = liveShields.filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()
                
                var s = 0L
                var g = 0L
                allAppsUsage.forEach { app ->
                    if (app.packageName in shieldPkgs) s += app.totalTimeVisible
                    else if (app.packageName in goalPkgs) g += app.totalTimeVisible
                }
                val currentTotal = if (isSelectedToday) totalToday else (selectedDayHistory.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: allAppsUsage.sumOf { it.totalTimeVisible })
                val o = (currentTotal - (s + g)).coerceAtLeast(0L)
                Triple(s, g, o)
            } else {
                Triple(storedShieldUsage ?: 0L, storedGoalUsage ?: 0L, storedOtherUsage ?: 0L)
            }

            val dbHourly = shieldRepository.getHourlyUsageForDate(selectedDateStr).first()
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val newlyLocked = mutableListOf<HourlyUsageEntity>()

            if (isSelectedToday) {
                val lockedHours = dbHourly.map { it.hour }.distinct().toSet()
                for (h in 0 until currentHour) {
                    if (h !in lockedHours) {
                        appTotals.keys.forEach { pkg ->
                            val diff = hourlyAppUsage[h]?.get(pkg) ?: 0L
                            
                            if (diff > 0) {
                                newlyLocked.add(HourlyUsageEntity(
                                    date = selectedDateStr,
                                    hour = h,
                                    packageName = pkg,
                                    usageTimeMillis = diff,
                                    lastUpdated = System.currentTimeMillis()
                                ))
                            }
                        }
                        
                        // Also add the TOTAL entry for this hour
                        val hourTotal = hourlyAppUsage[h]?.values?.sum() ?: 0L
                        if (hourTotal > 0) {
                            newlyLocked.add(HourlyUsageEntity(
                                date = selectedDateStr,
                                hour = h,
                                packageName = "TOTAL",
                                usageTimeMillis = hourTotal,
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    }
                }
                if (newlyLocked.isNotEmpty()) {
                    shieldRepository.insertHourlyUsage(newlyLocked)
                }
            }

            val allHourlyData = dbHourly + newlyLocked

            val hourlyUsage = (0..23).map { hour ->
                val appsInHour = appTotals.mapNotNull { (pkg, trueTotal) ->
                    val dbEntry = allHourlyData.find { it.hour == hour && it.packageName == pkg }
                    
                    val durationForThisHour = when {
                        dbEntry != null -> dbEntry.usageTimeMillis
                        isSelectedToday && hour == currentHour -> {
                            val sumLocked = allHourlyData.filter { it.packageName == pkg && it.hour < currentHour }.sumOf { it.usageTimeMillis }
                            (trueTotal - sumLocked).coerceAtLeast(0L)
                        }
                        else -> {
                            val rawTotal = rawTotals[pkg] ?: 0L
                            val rawHourUsage = hourlyAppUsage[hour]?.get(pkg) ?: 0L
                            if (rawTotal > 0) {
                                (trueTotal * (rawHourUsage.toDouble() / rawTotal)).toLong()
                            } else if (!isSelectedToday && hour == 23) {
                                trueTotal
                            } else {
                                0L
                            }
                        }
                    }

                    if (durationForThisHour > 0) {
                        val cached = appInfoCache[pkg]
                        if (cached != null) {
                            AppUsageInfo(pkg, cached.first, durationForThisHour, cached.second)
                        } else {
                            try {
                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                val icon = pm.getApplicationIcon(appInfo)
                                appInfoCache[pkg] = label to icon
                                AppUsageInfo(pkg, label, durationForThisHour, icon)
                            } catch (_: Exception) { null }
                        }
                    } else null
                }.sortedByDescending { it.totalTimeVisible }

                val hourUsageTotal = appsInHour.sumOf { it.totalTimeVisible }
                HourlyUsageInfo(
                    hour = hour,
                    usageTimeMillis = hourUsageTotal,
                    apps = appsInHour,
                    hasDatabaseRecord = dbHourly.any { it.hour == hour },
                    hasSystemData = hourUsageTotal > 0,
                    isLive = isSelectedToday && hour == currentHour
                )
            }

            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayDateStr = dateFormat.format(yesterdayCal.time)

            val totalYesterday = globalHistory.find { it.date == yesterdayDateStr }?.usageTimeMillis
                ?: globalFallbackMap[yesterdayDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
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
                        ?: if (preferSystemUsageHistory) {
                             globalFallbackMap[dateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
                        } else 0L
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

            val currentSnapshotStamps = _uiState.value.snapshotStamps
            val snapshotStamps = if (currentSnapshotStamps.isEmpty() || isSelectedToday) {
                (0..20).map { i ->
                    val dStart = getMidnight(i)
                    val dEnd = if (i == 0) now else dStart + (24 * 60 * 60 * 1000L)
                    val dateStr = dateFormat.format(Date(dStart))
                    
                    val topEntry = if (i == 0 && isSelectedToday) {
                        appTotals.filter { (pkg, _) -> 
                            pkg !in excludePackages && pkg in launcherApps 
                        }.maxByOrNull { it.value }?.let { it.key to it.value }
                    } else {
                        val statsMap = if (i == 0) trueTodayStats else usm.queryAndAggregateUsageStats(dStart, dEnd)
                        statsMap.filter { (pkg, _) -> 
                            pkg !in excludePackages && pkg in launcherApps 
                        }.maxByOrNull { (_, stat) -> 
                            stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground) 
                        }?.let { it.key to it.value.totalTimeVisible.coerceAtLeast(it.value.totalTimeInForeground) }
                    }
                    
                    val topPackage = topEntry?.first
                    var usageTime = topEntry?.second ?: 0L
                    
                    if (i == 0 && usageTime > timeSinceMidnight + 10000) {
                        usageTime = timeSinceMidnight
                    }

                    val hasDb = allHistory.any { it.date == dateStr && it.packageName != "TOTAL" }
                    val hasSys = globalFallbackMap[dateStr] != null
                    
                    val shouldShow = i == 0 || hasDb || preferSystemUsageHistory

                    if (topPackage != null && shouldShow) {
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
                        val usage = globalHistory.find { it.date == dStr }?.usageTimeMillis 
                            ?: if (preferSystemUsageHistory) {
                                globalFallbackMap[dStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
                            } else null
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
                    val usage = if (i == 0) totalToday else {
                        globalHistory.find { it.date == dStr }?.usageTimeMillis 
                            ?: if (preferSystemUsageHistory) {
                                globalFallbackMap[dStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
                            } else null
                    }
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
                shieldUsage          = finalShieldUsage,
                goalUsage            = finalGoalUsage,
                otherUsage           = finalOtherUsage,
                activeShields = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.SHIELD }, state.shieldSortType),
                activeGoals   = sortShields(liveShields.filter { it.type == com.etrisad.zenith.data.local.entity.FocusType.GOAL }, state.goalSortType),
                globalCurrentStreak = liveStreak,
                globalBestStreak = maxOf(prefGlobalBestStreak, bestStreakFromHistory),
                targetMillis = targetMillis,
                isLoading = false
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
                
                // Use the unified helper to ensure consistency with Home screen
                val detailedUsage = com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true)
                val sessionCount = detailedUsage.sessionCounts[packageName] ?: 0
                val appHourlyUsage = MutableList(24) { hour -> 
                    detailedUsage.hourlyUsageMap[hour]?.get(packageName) ?: 0L 
                }
                val currentTodayUsage = detailedUsage.appUsageMap[packageName] ?: 0L
                val peakHour = appHourlyUsage.indices.maxByOrNull { appHourlyUsage[it] } ?: -1


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
                            ?: if (preferSystemUsageHistory) {
                                detailFallbackMap[dateStr] ?: 0L
                            } else 0L
                    }
                    DailyUsage(
                        date = dStart, 
                        totalTime = dayTotal, 
                        hasDatabaseRecord = dbEntry != null,
                        hasSystemData = hasSystemData,
                        isLive = i == 0
                    )
                }

                val avgUsage = if (history.any { it.totalTime > 0 }) {
                    history.filter { it.totalTime > 0 }.map { it.totalTime }.average().toLong()
                } else 0L

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
                    averageUsage     = avgUsage,
                    totalSessions    = sessionCount.coerceAtLeast(if (currentTodayUsage > 0) 1 else 0),
                    peakHour         = peakHour,
                    percentageChange = percentageChange,
                    usageHistory     = history.reversed(),
                    hourlyUsage      = appHourlyUsage,
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
        val stats = this[packageName] ?: return 0L
        val time = stats.totalTimeVisible.coerceAtLeast(stats.totalTimeInForeground)
        
        val now = System.currentTimeMillis()
        val todayStart = getMidnight(0)
        val timeSinceMidnight = now - todayStart
        
        // If the query is for today and it returns more than elapsed time, cap it
        if (stats.firstTimeStamp >= todayStart && time > timeSinceMidnight + 10000) {
            return timeSinceMidnight
        }
        
        return time
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
        refreshUsageStats(showLoading = false)
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
            var lastUpdateDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            while (true) {
                // Perform updates first, then wait
                refreshUsageStats(showLoading = false)
                refreshCurrentAppDetailUsage()

                delay(15000) // Lowered refresh rate to 15 seconds
                
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (currentDay != lastUpdateDay) {
                    val today = getMidnight(0)
                    val yesterday = getMidnight(1)
                    if (_uiState.value.selectedDateMillis == yesterday) {
                        _uiState.update { it.copy(selectedDateMillis = today) }
                    }
                    lastUpdateDay = currentDay
                }
            }
        }
    }

    private fun refreshCurrentAppDetailUsage() {
        val currentState = _appDetailUiState.value
        val packageName = currentState.packageName

        if (packageName.isEmpty() || currentState.appName == packageName) return

        viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val todayStart = getMidnight(0)
            val currentNow = System.currentTimeMillis()
            
            val events = usm.queryEvents(todayStart, currentNow)
            val event = android.app.usage.UsageEvents.Event()
            val lastEventTime = mutableMapOf<String, Long>()
            var totalTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                
                val time = event.timeStamp
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND || 
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastEventTime[packageName] = time
                } else if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND ||
                           event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    val startTime = lastEventTime.remove(packageName) ?: continue
                    val segmentStart = maxOf(startTime, todayStart)
                    val segmentEnd = minOf(time, currentNow)
                    if (segmentStart < segmentEnd) {
                        totalTime += (segmentEnd - segmentStart)
                    }
                }
            }
            
            lastEventTime[packageName]?.let { startTime ->
                val segmentStart = maxOf(startTime, todayStart)
                val segmentEnd = currentNow
                if (segmentStart < segmentEnd) {
                    totalTime += (segmentEnd - segmentStart)
                }
            }

            val currentTodayUsage = totalTime.coerceAtMost(currentNow - todayStart)

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
        if (_appDetailUiState.value.packageName == packageName) {
            appDetailJob?.cancel()
        }
    }

    fun openSettingsSheet() {
        _appDetailUiState.update { it.copy(isSettingsSheetOpen = true) }
    }

    fun closeSettingsSheet() {
        _appDetailUiState.update { it.copy(isSettingsSheetOpen = false) }
    }

    fun saveFocus(
        timeLimitMinutes: Int,
        maxEmergencyUses: Int,
        isRemindersEnabled: Boolean,
        isStrictModeEnabled: Boolean,
        isAutoQuitEnabled: Boolean,
        maxUsesPerPeriod: Int,
        refreshPeriodMinutes: Int,
        goalReminderPeriodMinutes: Int,
        isDelayAppEnabled: Boolean,
        isGoalCallerEnabled: Boolean = false,
        isGoalCallerSoundEnabled: Boolean = true,
        goalCallerSoundUri: String? = null
    ) {
        val state = _appDetailUiState.value
        val packageName = state.packageName
        val appName = state.appName
        val type = state.type ?: com.etrisad.zenith.data.local.entity.FocusType.SHIELD

        viewModelScope.launch {
            val existingShield = shieldRepository.getShieldByPackageName(packageName)
            val shield = existingShield?.copy(
                timeLimitMinutes = timeLimitMinutes,
                maxEmergencyUses = maxEmergencyUses,
                isRemindersEnabled = isRemindersEnabled,
                isStrictModeEnabled = isStrictModeEnabled,
                isAutoQuitEnabled = isAutoQuitEnabled,
                maxUsesPerPeriod = maxUsesPerPeriod,
                refreshPeriodMinutes = refreshPeriodMinutes,
                goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                isDelayAppEnabled = isDelayAppEnabled,
                isGoalCallerEnabled = isGoalCallerEnabled,
                isGoalCallerSoundEnabled = isGoalCallerSoundEnabled,
                goalCallerSoundUri = goalCallerSoundUri
            ) ?: ShieldEntity(
                packageName = packageName,
                appName = appName,
                type = type,
                timeLimitMinutes = timeLimitMinutes,
                maxEmergencyUses = maxEmergencyUses,
                isRemindersEnabled = isRemindersEnabled,
                isStrictModeEnabled = isStrictModeEnabled,
                isAutoQuitEnabled = isAutoQuitEnabled,
                maxUsesPerPeriod = maxUsesPerPeriod,
                refreshPeriodMinutes = refreshPeriodMinutes,
                goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                isDelayAppEnabled = isDelayAppEnabled,
                isGoalCallerEnabled = isGoalCallerEnabled,
                isGoalCallerSoundEnabled = isGoalCallerSoundEnabled,
                goalCallerSoundUri = goalCallerSoundUri
            )
            shieldRepository.insertShield(shield)
            closeSettingsSheet()
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
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        val seconds = (millis / 1000) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun onVisibleWeekChanged(pageIndex: Int) {
        viewModelScope.launch {
            val history = _uiState.value.dailyUsageHistory
            if (history.isEmpty()) return@launch

            val pages = history.chunked(7)
            if (pageIndex !in pages.indices) return@launch

            val weekDays = pages[pageIndex]
            val avg = if (weekDays.isNotEmpty()) weekDays.map { it.totalTime }.average().toLong() else 0L

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val appUsageMap = mutableMapOf<String, Long>()

            weekDays.forEach { day ->
                val dateStr = dateFormat.format(Date(day.date))
                globalFallbackMap[dateStr]?.forEach { record ->
                    if (record.packageName != "TOTAL") {
                        appUsageMap[record.packageName] = (appUsageMap[record.packageName] ?: 0L) + record.usageTimeMillis
                    }
                }
            }

            val pm = context.packageManager
            val topApps = appUsageMap.entries.sortedByDescending { it.value }.take(3).map { (pkg, time) ->
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
                    } catch (_: Exception) {
                        AppUsageInfo(pkg, pkg, time, null)
                    }
                }
            }

            _uiState.update { it.copy(weeklyAvgTime = avg, weeklyTopApps = topApps) }
        }
    }
}
