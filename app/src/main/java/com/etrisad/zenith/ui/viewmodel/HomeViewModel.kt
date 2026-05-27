package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
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
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.service.UsageSyncManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val sessionCount: Int = 0,
    val lastTimeUsed: Long = 0L
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
    val isSettingsSheetOpen: Boolean = false,
    val isLoading: Boolean = false
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
    val hourlySortType: HourlySortType = HourlySortType.USAGE_TIME,
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

    private val appInfoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, android.graphics.drawable.Drawable?>>()
    private var refreshJob: Job? = null

    private var launcherAppsCache: Set<String>? = null
    private var launcherPackageCache: String? = null
    private var lastLauncherCheck = 0L

    private suspend fun getLauncherInfo(): Pair<Set<String>, String?> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cachedApps = launcherAppsCache
        if (cachedApps != null && now - lastLauncherCheck < 120000) {
            return@withContext cachedApps to launcherPackageCache
        }
        val pm = context.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.activityInfo.packageName }.toSet()
        
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val lPkg = pm.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            
        launcherAppsCache = apps
        launcherPackageCache = lPkg
        lastLauncherCheck = now
        apps to lPkg
    }

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
        
        val dbRecordsByDate = dbList.groupBy { it.date }
        val hourlyDatesSet = hourlyDates.toSet()

        while (!cal.before(earliestCal)) {
            val dateStr = dateFormat.format(cal.time)
            val isToday = dateStr == todayStr
            
            val dbRecords = dbRecordsByDate[dateStr] ?: emptyList()
            val dbTotal = dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
            val systemRecords = if (preferSystemUsageHistory || isToday) globalFallbackMap[dateStr] ?: emptyList() else emptyList()
            val systemTotal = if (preferSystemUsageHistory || isToday) systemRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L else 0L
            
            val hasHourly = hourlyDatesSet.contains(dateStr)
            val hasSnap = dbRecords.any { it.packageName != "TOTAL" && it.packageName != "SHIELD_TOTAL" && it.packageName != "GOAL_TOTAL" && it.packageName != "OTHER_TOTAL" }
            val hasPiechart = dbRecords.any { it.packageName == "SHIELD_TOTAL" || it.packageName == "GOAL_TOTAL" || it.packageName == "OTHER_TOTAL" }
            
            val dbShieldTotal = dbRecords.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: 0L
            val dbGoalTotal = dbRecords.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: 0L
            val dbOtherTotal = dbRecords.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis ?: 0L

            if (isToday) {
                val liveRecords = mutableListOf<UsageRecord>()
                dbRecords.forEach { liveRecords.add(UsageRecord.Database(it)) }
                
                if (systemRecords.isNotEmpty()) {
                    liveRecords.addAll(systemRecords)
                }

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
                val combinedRecords = mutableListOf<UsageRecord>()
                combinedRecords.addAll(dbRecords.map { UsageRecord.Database(it) })
                if (systemRecords.isNotEmpty()) {
                    combinedRecords.addAll(systemRecords)
                }

                groups.add(UsageHistoryGroup(
                    date = dateStr, 
                    records = combinedRecords,
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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    val repairableData: Flow<List<UsageHistoryGroup>> = combine(
        fullUsageHistory,
        userPreferencesRepository.userPreferencesFlow
    ) { groups, prefs ->
        if (prefs.allowRepairNonUnavailable) {
            groups.filter { it.hasSystemData }
        } else {
            groups.filter { (it.isMissing || !it.hasDatabaseRecord) && it.hasSystemData && !it.isLive }
        }
    }

    private val _isRepairing = MutableStateFlow(false)
    val isRepairing = _isRepairing.asStateFlow()

    val userPreferences: Flow<UserPreferences> = userPreferencesRepository.userPreferencesFlow

    private val _systemOnlyUsageHistory = MutableStateFlow<List<DailyUsage>>(emptyList())
    val systemOnlyUsageHistory: StateFlow<List<DailyUsage>> = _systemOnlyUsageHistory.asStateFlow()

    fun fetchSystemOnlyUsageHistory() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val usm = context!!.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)
            
            val now = System.currentTimeMillis()
            val history = mutableListOf<DailyUsage>()

            for (i in 0 until 21) {
                val start = getMidnight(i)
                val end = if (i == 0) now else getMidnight(i - 1)
                
                val events = usm.queryEvents(start - 1800000L, end)
                val event = UsageEvents.Event()
                
                val usageMap = mutableMapOf<String, Long>()
                var activePkg: String? = null
                var activeStartTime = 0L
                var isScreenOn = true

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName
                    val time = event.timeStamp
                    
                    when (event.eventType) {
                        UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                            isScreenOn = false
                            activePkg?.let { p ->
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    usageMap[p] = (usageMap[p] ?: 0L) + (segmentEnd - segmentStart)
                                }
                            }
                            activePkg = null
                            activeStartTime = 0L
                        }
                        UsageEvents.Event.ACTIVITY_RESUMED,
                        UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            if (isScreenOn) {
                                if (activePkg != null) {
                                    val segmentStart = maxOf(activeStartTime, start)
                                    val segmentEnd = minOf(time, end)
                                    if (segmentStart < segmentEnd) {
                                        usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
                                    }
                                }
                                activePkg = pkg
                                activeStartTime = time
                            }
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED,
                        UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (activePkg == pkg) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
                                }
                                activePkg = null
                                activeStartTime = 0L
                            }
                        }
                    }
                }
                
                if (activePkg != null && isScreenOn) {
                    val segmentStart = maxOf(activeStartTime, start)
                    val segmentEnd = minOf(now, end)
                    if (segmentStart < segmentEnd) {
                        usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
                    }
                }

                var dayTotal = 0L
                usageMap.forEach { (pkg, time) ->
                    if (pkg !in excludePackages && pkg in launcherApps) {
                        dayTotal += time
                    }
                }
                
                history.add(DailyUsage(
                    date = start,
                    totalTime = dayTotal,
                    hasDatabaseRecord = false,
                    hasSystemData = true,
                    isLive = i == 0
                ))
            }
            _systemOnlyUsageHistory.value = history.reversed()
        }
    }

    suspend fun setAllowRepairNonUnavailable(enabled: Boolean) {
        userPreferencesRepository.setAllowRepairNonUnavailable(enabled)
    }

    suspend fun repairData(date: String) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())
        
        val dbRecords = shieldRepository.getDailyUsagesForDateSync(date)
        val dbAppRecords = dbRecords.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }
        val systemRecords = globalFallbackMap[date] ?: (if (prefs.allowRepairNonUnavailable) dbAppRecords.map { UsageRecord.Live(it.packageName, it.usageTimeMillis) } else null)
        
        if (systemRecords == null) return

        _isRepairing.value = true
        try {
            delay(800)
            
            val allShields = shieldRepository.allShields.first()
            val shieldPkgs = allShields.filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
            val goalPkgs = allShields.filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()
            
            var sUsage = 0L
            var gUsage = 0L
            var appSum = 0L

            systemRecords.forEach { record ->
                if (record.packageName != "TOTAL") {
                    val existing = dbAppRecords.find { it.packageName == record.packageName }?.usageTimeMillis ?: 0L
                    val finalUsage = maxOf(record.usageTimeMillis, existing)
                    
                    shieldRepository.insertDailyUsage(
                        DailyUsageEntity(
                            date = date,
                            packageName = record.packageName,
                            usageTimeMillis = finalUsage,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                    appSum += finalUsage
                    if (record.packageName in shieldPkgs) sUsage += finalUsage
                    else if (record.packageName in goalPkgs) gUsage += finalUsage
                }
            }

            val systemTotal = systemRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: appSum
            val existingTotal = dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
            var finalTotal = maxOf(systemTotal, appSum, existingTotal)

            if (date == todayStr) {
                val cal = Calendar.getInstance()
                val timeSinceMidnight = (cal.get(Calendar.HOUR_OF_DAY) * 3600000L) + 
                                       (cal.get(Calendar.MINUTE) * 60000L) + 
                                       (cal.get(Calendar.SECOND) * 1000L) + 120000L
                finalTotal = finalTotal.coerceAtMost(timeSinceMidnight)
            } else {
                finalTotal = finalTotal.coerceAtMost(86400000L)
            }
            
            val existingShieldTotal = dbRecords.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: 0L
            val existingGoalTotal = dbRecords.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: 0L
            
            val finalShieldTotal = maxOf(sUsage, existingShieldTotal)
            val finalGoalTotal = maxOf(gUsage, existingGoalTotal)
            val oUsage = (finalTotal - (finalShieldTotal + finalGoalTotal)).coerceAtLeast(0L)
            
            shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "TOTAL", usageTimeMillis = finalTotal))
            shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "SHIELD_TOTAL", usageTimeMillis = finalShieldTotal))
            shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "GOAL_TOTAL", usageTimeMillis = finalGoalTotal))
            shieldRepository.insertDailyUsage(DailyUsageEntity(date = date, packageName = "OTHER_TOTAL", usageTimeMillis = oUsage))

            userPreferencesRepository.refreshGlobalStreak(shieldRepository)
        } catch (e: Exception) {
            android.util.Log.e("HomeVM", "Error repairing data: ${e.message}")
        } finally {
            _isRepairing.value = false
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
            combine(
                shieldRepository.allShields,
                shieldRepository.getAllUsage(),
                shieldRepository.getLastNDaysGlobalUsage(60),
                userPreferencesRepository.userPreferencesFlow
            ) { shields, usage, global, prefs ->
                allShields = shields
                allHistory = usage
                globalHistory = global
                
                currentTargetMinutes = prefs.screenTimeTargetMinutes
                prefGlobalBestStreak = prefs.globalBestStreak
                preferSystemUsageHistory = prefs.preferSystemUsageHistory
                
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }

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
                Unit
            }.debounce(250).collect {
                updateGlobalFallback()
                refreshUsageStats(showLoading = false)
            }
        }

        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -21)
            val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            shieldRepository.deleteOldHourlyUsage(thresholdDate)
        }

        startRealTimeUpdates()
        syncDataNow()
    }

    val todayHourlyUsage: Flow<List<HourlyUsageEntity>> = allDatabaseUsage.flatMapLatest {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        shieldRepository.getHourlyUsageForDate(today)
    }

    fun onRefresh() {
        triggerServiceRefresh()
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.smartRepairOnRefresh) {
                resetCarryover()
            } else {
                syncDataNow()
            }
        }
    }

    fun syncDataNow() {
        triggerServiceRefresh()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                com.etrisad.zenith.util.ScreenUsageHelper.clearCache()

                updateGlobalFallback()
                repairData(todayStr)

                val syncManager = UsageSyncManager(context!!, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Sync failed: ${e.message}")
            } finally {
                refreshUsageStats(showLoading = false)
            }
        }
    }

    fun resetCarryover() {
        triggerServiceRefresh()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                
                userPreferencesRepository.setLastSyncTimestamp(getMidnight(0))
                
                shieldRepository.deleteHourlyUsageForDate(today)
                
                val syncManager = UsageSyncManager(context, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Reset carryover failed: ${e.message}")
            } finally {
                refreshUsageStats(showLoading = false)
            }
        }
    }

    fun deleteHourlyPackageUsageToday(packageName: String) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            shieldRepository.deleteHourlyUsageForPackage(today, packageName)
            refreshUsageStats()
        }
    }

    fun deleteHourlyUsageAtHour(hour: Int, packageName: String) {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            shieldRepository.deleteHourlyUsageAtHour(today, hour, packageName)
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

    private var lastFullFallbackRefresh = 0L
    private suspend fun updateGlobalFallback(forceFull: Boolean = false) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val isFullNeeded = forceFull || globalFallbackMap.isEmpty() || (now - lastFullFallbackRefresh > 1800000)

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (launcherApps, launcherPackage) = getLauncherInfo()
        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val resultMap = globalFallbackMap.toMutableMap()

        val daysToRefresh = if (isFullNeeded) 30 else 1

        for (i in 0..daysToRefresh) {
            val start = getMidnight(i)
            val end = if (i == 0) now else getMidnight(i - 1)
            val dateStr = dateFormat.format(Date(start))
            
            val usageMap = mutableMapOf<String, Long>()
            val events = usm.queryEvents(start - 1800000L, end)
            val event = UsageEvents.Event()
            
            var activePkg: String? = null
            var activeStartTime = 0L
            var isScreenOn = true 

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val time = event.timeStamp
                
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        isScreenOn = false
                        activePkg?.let { p ->
                            val segmentStart = maxOf(activeStartTime, start)
                            val segmentEnd = minOf(time, end)
                            if (segmentStart < segmentEnd) {
                                usageMap[p] = (usageMap[p] ?: 0L) + (segmentEnd - segmentStart)
                            }
                        }
                        activePkg = null
                        activeStartTime = 0L
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (isScreenOn) {
                            if (activePkg != null) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
                                }
                            }
                            activePkg = pkg
                            activeStartTime = time
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (activePkg == pkg) {
                            val segmentStart = maxOf(activeStartTime, start)
                            val segmentEnd = minOf(time, end)
                            if (segmentStart < segmentEnd) {
                                usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
                            }
                            activePkg = null
                            activeStartTime = 0L
                        }
                    }
                }
            }
            
            if (activePkg != null && isScreenOn) {
                val segmentStart = maxOf(activeStartTime, start)
                val segmentEnd = minOf(now, end)
                if (segmentStart < segmentEnd) {
                    usageMap[activePkg!!] = (usageMap[activePkg!!] ?: 0L) + (segmentEnd - segmentStart)
                }
            }

            val dayRecords = mutableListOf<UsageRecord.Live>()
            var dayTotal = 0L

            usageMap.forEach { (pkg, time) ->
                if (pkg in excludePackages || pkg !in launcherApps) return@forEach
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
        if (isFullNeeded) lastFullFallbackRefresh = now
    }

    private suspend fun updatePackageFallback(packageName: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()

        for (i in 0..30) {
            val start = getMidnight(i)
            val end = if (i == 0) now else getMidnight(i - 1)
            val dateStr = dateFormat.format(Date(start))

            val events = usm.queryEvents(start - 1800000L, end)
            val event = UsageEvents.Event()
            
            var activePkg: String? = null
            var activeStartTime = 0L
            var isScreenOn = true
            var dayUsage = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                val time = event.timeStamp
                
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        isScreenOn = false
                        activePkg?.let { p ->
                            if (p == packageName) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    dayUsage += (segmentEnd - segmentStart)
                                }
                            }
                        }
                        activePkg = null
                        activeStartTime = 0L
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (isScreenOn) {
                            if (activePkg == packageName) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    dayUsage += (segmentEnd - segmentStart)
                                }
                            }
                            activePkg = pkg
                            activeStartTime = time
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (activePkg == pkg) {
                            if (pkg == packageName) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) {
                                    dayUsage += (segmentEnd - segmentStart)
                                }
                            }
                            activePkg = null
                            activeStartTime = 0L
                        }
                    }
                }
            }
            
            if (activePkg == packageName && isScreenOn) {
                val segmentStart = maxOf(activeStartTime, start)
                val segmentEnd = minOf(now, end)
                if (segmentStart < segmentEnd) {
                    dayUsage += (segmentEnd - segmentStart)
                }
            }

            if (dayUsage > 0) {
                result[dateStr] = dayUsage
            }
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

        if (!showLoading && refreshJob?.isActive == true && !_uiState.value.isLoading) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            val (launcherApps, launcherPackage) = getLauncherInfo()
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

            val todayDetailed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = isSelectedToday)
            }
            val filteredTodayUsage = todayDetailed.appUsageMap.filter { (pkg, _) -> 
                pkg !in excludePackages && pkg in launcherApps 
            }.mapValues { (_, usage) -> 
                usage.coerceAtMost(timeSinceMidnight)
            }

            val appTotals = mutableMapOf<String, Long>()
            val appSessionCounts = mutableMapOf<String, Int>()
            val hourlyAppUsage = mutableMapOf<Int, MutableMap<String, Long>>()
            val lastUsedMap = mutableMapOf<String, Long>()

            if (isSelectedToday) {
                val selectedDayHistory = allHistory.filter { it.date == dateFormat.format(Date(todayStart)) }
                
                filteredTodayUsage.forEach { (pkg, time) ->
                    val dbTime = selectedDayHistory.find { it.packageName == pkg }?.usageTimeMillis ?: 0L
                    appTotals[pkg] = maxOf(time, dbTime)
                }
                
                selectedDayHistory.forEach { record ->
                    if (record.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") && 
                        !appTotals.containsKey(record.packageName)) {
                        appTotals[record.packageName] = record.usageTimeMillis
                    }
                }
                
                todayDetailed.sessionCounts.forEach { (pkg, count) ->
                    appSessionCounts[pkg] = count
                }
                
                todayDetailed.hourlyUsageMap.forEach { (hour, pkgMap) ->
                    val filteredPkgMap = pkgMap.filter { it.key in launcherApps && it.key !in excludePackages }
                    if (filteredPkgMap.isNotEmpty()) {
                        hourlyAppUsage[hour] = filteredPkgMap.toMutableMap()
                    }
                }
                lastUsedMap.putAll(todayDetailed.lastUsedMap)
            } else {
                val selectedDateStr = dateFormat.format(Date(selectedDate))
                val selectedDayHistory = allHistory.filter { it.date == selectedDateStr }
                
                val dbAppRecords = selectedDayHistory.filter { 
                    it.packageName != "TOTAL" && 
                    it.packageName != "SHIELD_TOTAL" && 
                    it.packageName != "GOAL_TOTAL" && 
                    it.packageName != "OTHER_TOTAL" 
                }

                val dayStats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    usm.queryAndAggregateUsageStats(dayStart, dayEnd)
                }

                if (dbAppRecords.isNotEmpty()) {
                    dbAppRecords.forEach { record ->
                        appTotals[record.packageName] = record.usageTimeMillis
                        lastUsedMap[record.packageName] = dayStats[record.packageName]?.lastTimeUsed ?: 0L
                    }
                } else {
                    dayStats.forEach { (pkg, stat) ->
                        if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                        val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                        if (time > 0) {
                            appTotals[pkg] = time
                            lastUsedMap[pkg] = stat.lastTimeUsed
                        }
                    }
                }
            }

            val totalToday = if (isSelectedToday) {
                appTotals.values.sum().coerceAtMost(timeSinceMidnight)
            } else {
                0L
            }

            val appList = appTotals.mapNotNull { (pkg, time) ->
                val sessions = appSessionCounts[pkg] ?: (if (time > 4000) 1 else 0)
                val lastUsed = lastUsedMap[pkg] ?: 0L
                val cached = appInfoCache[pkg]
                if (cached != null) {
                    AppUsageInfo(pkg, cached.first, time, cached.second, sessionCount = sessions, lastTimeUsed = lastUsed)
                } else {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        appInfoCache[pkg] = label to icon
                        AppUsageInfo(pkg, label, time, icon, sessionCount = sessions, lastTimeUsed = lastUsed)
                    } catch (_: Exception) { null }
                }
            }

            val topApps = appList.sortedByDescending { it.totalTimeVisible }.take(5)
            val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }

            val selectedDateStr = dateFormat.format(Date(selectedDate))
            
            val selectedDayHistory = allHistory.filter { it.date == selectedDateStr }
            val storedShieldUsage = selectedDayHistory.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis
            val storedGoalUsage = selectedDayHistory.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis
            val storedOtherUsage = selectedDayHistory.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis

            val liveShields = allShields.map { shield ->
                val usage = filteredTodayUsage[shield.packageName] ?: 0L
                val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                shield.copy(remainingTimeMillis = (limitMillis - usage).coerceAtLeast(0L))
            }

            val selectedDayTotal = if (isSelectedToday) {
                val dbTotalToday = selectedDayHistory.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
                maxOf(totalToday, dbTotalToday)
            } else {
                selectedDayHistory.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: appTotals.values.sum()
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
                val currentTotal = selectedDayTotal
                val o = (currentTotal - (s + g)).coerceAtLeast(0L)
                Triple(s, g, o)
            } else {
                Triple(storedShieldUsage ?: 0L, storedGoalUsage ?: 0L, storedOtherUsage ?: 0L)
            }

            val dbHourly = shieldRepository.getHourlyUsageForDate(selectedDateStr).first()
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val newlyLocked = mutableListOf<HourlyUsageEntity>()
            var carryOverFromLocked = 0L
            val carryOverChunksByPkg = mutableMapOf<String, Long>()

            if (isSelectedToday) {
                val dbHourlyMap = dbHourly.groupBy { it.hour }
                val hourLimit = 3600000L
                
                for (h in 0 until currentHour) {
                    val existingHourRecs = dbHourlyMap[h] ?: emptyList()
                    val hasExistingTotal = existingHourRecs.any { it.packageName == "TOTAL" }
                    
                    if (!hasExistingTotal) {
                        val currentHourAppState = mutableMapOf<String, Long>()
                        appTotals.keys.forEach { pkg ->
                            val diff = (hourlyAppUsage[h]?.get(pkg) ?: 0L) + (carryOverChunksByPkg[pkg] ?: 0L)
                            if (diff > 0) currentHourAppState[pkg] = diff
                        }
                        carryOverChunksByPkg.clear()

                        var totalInHour = currentHourAppState.values.sum()
                        if (totalInHour > hourLimit) {
                            var excess = totalInHour - hourLimit
                            val sortedPkgs = currentHourAppState.keys.sortedByDescending { currentHourAppState[it] }
                            for (pkg in sortedPkgs) {
                                if (excess <= 0) break
                                val currentVal = currentHourAppState[pkg] ?: 0L
                                val toMove = minOf(currentVal, excess)
                                currentHourAppState[pkg] = currentVal - toMove
                                if (toMove > 0) carryOverChunksByPkg[pkg] = toMove
                                excess -= toMove
                            }
                            totalInHour = hourLimit
                        }

                        currentHourAppState.forEach { (pkg, duration) ->
                            if (duration > 0) {
                                newlyLocked.add(HourlyUsageEntity(
                                    date = selectedDateStr, hour = h, packageName = pkg,
                                    usageTimeMillis = duration, lastUpdated = System.currentTimeMillis()
                                ))
                            }
                        }
                        
                        val finalTotalInHour = minOf(totalInHour, hourLimit)
                        if (finalTotalInHour > 0) {
                            newlyLocked.add(HourlyUsageEntity(
                                date = selectedDateStr, hour = h, packageName = "TOTAL",
                                usageTimeMillis = finalTotalInHour, lastUpdated = System.currentTimeMillis()
                            ))
                        }
                    } else {
                        carryOverChunksByPkg.clear()
                    }
                }
                
                if (newlyLocked.isNotEmpty()) {
                    shieldRepository.insertHourlyUsage(newlyLocked)
                    userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())
                }
            }

            val allHourlyData = dbHourly + newlyLocked

            val hourlyUsage = (0..23).map { hour ->
                val appsInHour = if (isSelectedToday && hourlyAppUsage.containsKey(hour)) {
                    val pkgMap = hourlyAppUsage[hour] ?: emptyMap()
                    pkgMap.mapNotNull { (pkg, duration) ->
                        if (duration > 0) {
                            val lastUsed = lastUsedMap[pkg] ?: 0L
                            val cached = appInfoCache[pkg]
                            if (cached != null) {
                                AppUsageInfo(pkg, cached.first, duration, cached.second, lastTimeUsed = lastUsed)
                            } else {
                                try {
                                    val appInfo = pm.getApplicationInfo(pkg, 0)
                                    val label = pm.getApplicationLabel(appInfo).toString()
                                    val icon = pm.getApplicationIcon(appInfo)
                                    appInfoCache[pkg] = label to icon
                                    AppUsageInfo(pkg, label, duration, icon, lastTimeUsed = lastUsed)
                                } catch (_: Exception) { null }
                            }
                        } else null
                    }
                } else {
                    appTotals.mapNotNull { (pkg, trueTotal) ->
                        val dbEntry = allHourlyData.find { it.hour == hour && it.packageName == pkg }
                        
                        val durationForThisHour = if (dbEntry != null) {
                            dbEntry.usageTimeMillis
                        } else {
                            val lockedHours = allHourlyData.filter { it.packageName == pkg }.map { it.hour }.toSet()
                            if (hour in lockedHours) {
                                0L
                            } else {
                                val sumLocked = allHourlyData.filter { it.packageName == pkg }.sumOf { it.usageTimeMillis }
                                val remainingToDistribute = (trueTotal - sumLocked).coerceAtLeast(0L)
                                val rawUnlockedTotal = (0..23).filter { it !in lockedHours }.sumOf { h -> hourlyAppUsage[h]?.get(pkg) ?: 0L }
                                val rawHourUsage = hourlyAppUsage[hour]?.get(pkg) ?: 0L
                                
                                if (rawUnlockedTotal > 0) {
                                    (remainingToDistribute * (rawHourUsage.toDouble() / rawUnlockedTotal)).toLong()
                                } else {
                                    0L
                                }
                            }
                        }

                        if (durationForThisHour > 0) {
                            val lastUsed = lastUsedMap[pkg] ?: 0L
                            val cached = appInfoCache[pkg]
                            if (cached != null) {
                                AppUsageInfo(pkg, cached.first, durationForThisHour, cached.second, lastTimeUsed = lastUsed)
                            } else {
                                try {
                                    val appInfo = pm.getApplicationInfo(pkg, 0)
                                    val label = pm.getApplicationLabel(appInfo).toString()
                                    val icon = pm.getApplicationIcon(appInfo)
                                    appInfoCache[pkg] = label to icon
                                    AppUsageInfo(pkg, label, durationForThisHour, icon, lastTimeUsed = lastUsed)
                                } catch (_: Exception) { null }
                            }
                        } else null
                    }
                }.let { list ->
                    when (_uiState.value.hourlySortType) {
                        HourlySortType.USAGE_TIME -> list.sortedByDescending { it.totalTimeVisible }
                        HourlySortType.RECENTLY_USED -> list.sortedByDescending { it.lastTimeUsed }
                    }
                }

                val dbHourTotal = allHourlyData.find { it.hour == hour && it.packageName == "TOTAL" }?.usageTimeMillis
                val hourUsageTotal = if (isSelectedToday && hourlyAppUsage.containsKey(hour)) {
                    appsInHour.sumOf { it.totalTimeVisible }
                } else {
                    dbHourTotal ?: appsInHour.sumOf { it.totalTimeVisible }
                }

                HourlyUsageInfo(
                    hour = hour,
                    usageTimeMillis = minOf(hourUsageTotal, 3600000L),
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

            val todayDateStr = dateFormat.format(Date(todayStart))
            val actualTodayDbTotal = allHistory.find { it.date == todayDateStr && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L

            val actualTodayTotal = maxOf(totalToday, actualTodayDbTotal).coerceAtMost(timeSinceMidnight)

            val history = (0 until 21).map { i ->
                val dStart = getMidnight(i)
                val dateStr = dateFormat.format(Date(dStart))
                
                val dbEntry = globalHistory.find { it.date == dateStr }
                val hasSystemData = globalFallbackMap[dateStr] != null
                val dayTotal = if (i == 0) {
                    actualTodayTotal
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
                totalYesterday > 0 -> ((actualTodayTotal - totalYesterday).toFloat() / totalYesterday) * 100
                actualTodayTotal > 0     -> 100f
                else               -> 0f
            }

            val excludedPkgs = setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL")
            val historyByDate = allHistory.filter { it.packageName !in excludedPkgs }.groupBy { it.date }

            val snapshotStamps = (0..20).map { i ->
                val dStart = getMidnight(i)
                val dateStr = dateFormat.format(Date(dStart))
                
                val dbDayApps = historyByDate[dateStr] ?: emptyList()

                val topEntry = if (dbDayApps.isNotEmpty()) {
                    val dbMax = dbDayApps.maxByOrNull { it.usageTimeMillis }!!
                    if (i == 0) {
                        val liveTop = filteredTodayUsage.maxByOrNull { it.value }
                        if (liveTop != null && liveTop.value > dbMax.usageTimeMillis) {
                            liveTop.key to liveTop.value
                        } else {
                            dbMax.packageName to dbMax.usageTimeMillis
                        }
                    } else {
                        dbMax.packageName to dbMax.usageTimeMillis
                    }
                } else {
                    if (i == 0) {
                        filteredTodayUsage.maxByOrNull { it.value }?.let { it.key to it.value }
                    } else {
                        globalFallbackMap[dateStr]?.filter { it.packageName != "TOTAL" }
                            ?.maxByOrNull { it.usageTimeMillis }
                            ?.let { it.packageName to it.usageTimeMillis }
                    }
                }
                
                val topPackage = topEntry?.first
                var usageTime = topEntry?.second ?: 0L
                
                if (i == 0 && usageTime > timeSinceMidnight + 10000) {
                    usageTime = timeSinceMidnight
                }

                val hasDb = dbDayApps.isNotEmpty()
                val hasSys = globalFallbackMap[dateStr] != null
                
                val shouldShow = i == 0 || hasDb || (preferSystemUsageHistory && hasSys)

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
                            AppUsageInfo(topPackage, topPackage, usageTime, null, hasDb, hasSys, i == 0)
                        }
                    }
                } else {
                    AppUsageInfo("", "", 0, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0)
                }
            }.reversed()

            val targetMillis = currentTargetMinutes * 60 * 1000L

            val (liveStreak, finalBestStreak) = userPreferencesRepository.refreshGlobalStreak(shieldRepository)
            
            _uiState.update { state -> state.copy(
                totalScreenTime      = selectedDayTotal,
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
                globalBestStreak = finalBestStreak,
                targetMillis = targetMillis,
                isLoading = false
            ) }

            viewModelScope.launch {
                userPreferencesRepository.setLastKnownDailyUsage(actualTodayTotal, dateFormat.format(Date(now)))
            }
        }
    }

    fun loadAppDetail(packageName: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _appDetailUiState.value.packageName == packageName && appDetailJob?.isActive == true) return
        
        val isNewPackage = _appDetailUiState.value.packageName != packageName
        appDetailJob?.cancel()
        
        if (isNewPackage) {
            _appDetailUiState.value = AppDetailUiState(packageName = packageName, isLoading = true)
        } else {
            _appDetailUiState.update { it.copy(isLoading = true) }
        }

        appDetailJob = viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm  = context.packageManager
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            var appName = packageName
            var icon: android.graphics.drawable.Drawable? = null
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon    = pm.getApplicationIcon(appInfo)
            } catch (_: Exception) {}

            _appDetailUiState.update { it.copy(appName = appName, icon = icon) }
            
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (detailFallbackMap.isEmpty() || forceRefresh || isNewPackage) {
                    updatePackageFallback(packageName)
                }
            }

            combine(
                shieldRepository.getLastNDaysUsageForPackage(packageName, 21),
                shieldRepository.getShieldByPackageNameFlow(packageName),
                userPreferencesRepository.userPreferencesFlow
            ) { historyFromDb, shield, prefs ->
                val currentNow = System.currentTimeMillis()

                val detailedUsage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    kotlinx.coroutines.withTimeoutOrNull(5000) {
                        com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true)
                    }
                }
                
                val currentTodayUsage = detailedUsage?.appUsageMap?.get(packageName) ?: 0L
                val sessionCount = detailedUsage?.sessionCounts?.get(packageName) ?: 0
                val appHourlyUsage = MutableList(24) { hour -> 
                    detailedUsage?.hourlyUsageMap?.get(hour)?.get(packageName) ?: 0L 
                }
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
                            ?: if (prefs.preferSystemUsageHistory) {
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
                    pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                    isLoading        = false
                ) }
            }.collect()
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

    fun onHourlySortTypeChange(sortType: HourlySortType) {
        _uiState.update { it.copy(hourlySortType = sortType) }
        refreshUsageStats(showLoading = false)
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
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                if (currentDay != lastUpdateDay) {
                    appInfoCache.clear()
                    globalFallbackMap = emptyMap()
                    detailFallbackMap = emptyMap()
                    
                    val today = getMidnight(0)
                    val yesterday = getMidnight(1)
                    if (_uiState.value.selectedDateMillis == yesterday) {
                        _uiState.update { it.copy(selectedDateMillis = today) }
                    }
                    lastUpdateDay = currentDay
                }
                
                refreshUsageStats(showLoading = false)
                refreshCurrentAppDetailUsage()

                delay(30000)
            }
        }
    }

    private fun refreshCurrentAppDetailUsage() {
        val currentState = _appDetailUiState.value
        val packageName = currentState.packageName

        if (packageName.isEmpty()) return

        viewModelScope.launch {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val todayStart = getMidnight(0)
            
            val detailedUsage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true)
                }
            }
            val currentTodayUsage = detailedUsage?.appUsageMap?.get(packageName) ?: 0L
            
            val appHourlyUsage = MutableList(24) { hour -> 
                detailedUsage?.hourlyUsageMap?.get(hour)?.get(packageName) ?: 0L
            }
            val peakHour = appHourlyUsage.indices.maxByOrNull { appHourlyUsage[it] } ?: -1

            val yesterdayUsage = currentState.yesterdayUsage
            val percentageChange = when {
                yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
                currentTodayUsage > 0 -> 100f
                else -> 0f
            }

            _appDetailUiState.update { it.copy(
                todayUsage = currentTodayUsage,
                percentageChange = percentageChange,
                totalSessions = detailedUsage?.sessionCounts?.get(packageName)?.coerceAtLeast(if (currentTodayUsage > 0) 1 else 0) ?: it.totalSessions,
                hourlyUsage = appHourlyUsage,
                peakHour = peakHour
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

    private fun triggerServiceRefresh() {
        val intent = Intent("com.etrisad.zenith.action.REFRESH_SERVICES").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
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
