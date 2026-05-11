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
    val percentageChange: Float = 0f,
    val usageHistory: List<DailyUsage> = emptyList(),
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
    val hasHourlyUsage: Boolean = false,
    val systemTotalMillis: Long = 0L
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
                    hasHourlyUsage = hasHourly,
                    systemTotalMillis = liveTotal
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
                    systemTotalMillis = systemTotal
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
                systemRecords.forEach { record ->
                    shieldRepository.insertDailyUsage(
                        DailyUsageEntity(
                            date = date,
                            packageName = record.packageName,
                            usageTimeMillis = record.usageTimeMillis
                        )
                    )
                }
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
                preferSystemUsageHistory = prefs.preferSystemUsageHistory
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }
                refreshUsageStats()
            }
        }

        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -21)
            val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            shieldRepository.deleteOldHourlyUsage(thresholdDate)
        }

        refreshUsageStats()
        startRealTimeUpdates()
        syncDataNow()
    }

    fun syncDataNow() {
        viewModelScope.launch {
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
        for (i in 0..30) {
            val start = getMidnight(i)
            val end = if (i == 0) now else getMidnight(i - 1)
            
            val dateStr = dateFormat.format(Date(start))
            val stats = usm.queryAndAggregateUsageStats(start, end)
            
            val dayRecords = mutableListOf<UsageRecord.Live>()
            var dayTotal = 0L
            stats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
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

            val trueTodayStats = usm.queryAndAggregateUsageStats(todayStart, now)
            var totalToday = 0L
            trueTodayStats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (time > 0) totalToday += time
            }

            val appTotals = mutableMapOf<String, Long>()
            val appSessionCounts = mutableMapOf<String, Int>()
            val dayStats = if (isSelectedToday) trueTodayStats else usm.queryAndAggregateUsageStats(dayStart, dayEnd)
            
            dayStats.forEach { (pkg, stat) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                if (time > 0) appTotals[pkg] = time
            }

            val hourlyAppUsage = mutableMapOf<Int, MutableMap<String, Long>>()
            val eventBuffer = 24 * 60 * 60 * 1000L
            val events = usm.queryEvents(dayStart - eventBuffer, dayEnd)
            val event = android.app.usage.UsageEvents.Event()
            val lastEventTime = mutableMapOf<String, Long>()
            val cal = Calendar.getInstance()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg in excludePackages || pkg !in launcherApps) continue
                
                val time = event.timeStamp
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastEventTime[pkg] = time
                    if (time in dayStart..dayEnd) {
                        appSessionCounts[pkg] = (appSessionCounts[pkg] ?: 0) + 1
                    }
                } else if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastEventTime[pkg] = time
                } else if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND || 
                           event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    val startTime = lastEventTime.remove(pkg) ?: continue
                    
                    val segmentStart = maxOf(startTime, dayStart)
                    val segmentEnd = minOf(time, dayEnd)
                    
                    if (segmentStart < segmentEnd) {
                        var current = segmentStart
                        while (current < segmentEnd) {
                            cal.timeInMillis = current
                            val hour = cal.get(Calendar.HOUR_OF_DAY)
                            val nextHourStart = (cal.clone() as Calendar).apply {
                                add(Calendar.HOUR_OF_DAY, 1)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                            
                            val end = minOf(segmentEnd, nextHourStart)
                            val duration = end - current
                            if (duration > 0) {
                                val pkgMap = hourlyAppUsage.getOrPut(hour) { mutableMapOf() }
                                pkgMap[pkg] = (pkgMap[pkg] ?: 0L) + duration
                            }
                            current = end
                        }
                    }
                }
            }

            lastEventTime.forEach { (pkg, startTime) ->
                val segmentStart = maxOf(startTime, dayStart)
                val segmentEnd = dayEnd
                if (segmentStart < segmentEnd) {
                    var current = segmentStart
                    while (current < segmentEnd) {
                        cal.timeInMillis = current
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val nextHourStart = (cal.clone() as Calendar).apply {
                            add(Calendar.HOUR_OF_DAY, 1)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val end = minOf(segmentEnd, nextHourStart)
                        val duration = end - current
                        if (duration > 0) {
                            val pkgMap = hourlyAppUsage.getOrPut(hour) { mutableMapOf() }
                            pkgMap[pkg] = (pkgMap[pkg] ?: 0L) + duration
                        }
                        current = end
                    }
                }
            }

            if (isSelectedToday) {
                totalToday = appTotals.values.sum()
            }

            val appList = appTotals.mapNotNull { (pkg, time) ->
                val sessions = appSessionCounts[pkg]?.coerceAtLeast(1) ?: 1
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
            val dbHourly = shieldRepository.getHourlyUsageForDate(selectedDateStr).first()
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val newlyLocked = mutableListOf<HourlyUsageEntity>()

            if (isSelectedToday) {
                val lockedHours = dbHourly.map { it.hour }.distinct().toSet()
                for (h in 0 until currentHour) {
                    if (h !in lockedHours) {
                        val startOfH = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val endOfH = Calendar.getInstance().apply {
                            timeInMillis = selectedDate
                            set(Calendar.HOUR_OF_DAY, h)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis

                        val statsUntilH = usm.queryAndAggregateUsageStats(dayStart, endOfH)
                        val statsUntilPrevH = if (h == 0) emptyMap() else usm.queryAndAggregateUsageStats(dayStart, startOfH - 1)

                        appTotals.keys.forEach { pkg ->
                            val totalUntilH = statsUntilH[pkg]?.let { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) } ?: 0L
                            val totalUntilPrevH = statsUntilPrevH[pkg]?.let { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) } ?: 0L
                            val diff = (totalUntilH - totalUntilPrevH).coerceAtLeast(0L)
                            
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
