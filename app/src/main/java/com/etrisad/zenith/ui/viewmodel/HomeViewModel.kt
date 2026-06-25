package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.service.UsageSyncManager
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*
import com.etrisad.zenith.util.ScreenUsageHelper

@Immutable
data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeVisible: Long,
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
    val type: FocusType? = null,
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
    val sinceLastChargeUsage: Long = 0L,
    val lastResetTimestamp: Long = 0L,
    val batteryStatsResetEnabled: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val isLoading: Boolean = true
)

data class HourlyUsageInfo(
    val hour: Int,
    val usageTimeMillis: Long,
    val apps: List<AppUsageInfo> = emptyList(),
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isLive: Boolean = false
)

@Immutable
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
    val isLoading: Boolean = true,
    val uninstalledShieldPackageNames: Set<String> = emptySet()
)

sealed class UsageRecord {
    data class Database(val entity: DailyUsageEntity) : UsageRecord()
    data class Live(val packageName: String, val usageTimeMillis: Long) : UsageRecord()
}

enum class RepairMode { SYSTEM, DATABASE_RECALC }

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
    val databaseAppSumMillis: Long = 0L,
    val shieldTotalMillis: Long = 0L,
    val goalTotalMillis: Long = 0L,
    val otherTotalMillis: Long = 0L
)

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    context: Context,
    private val shieldRepository: ShieldRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val context = context.applicationContext

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val appInfoCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var refreshJob: Job? = null
    private val refreshMutex = Mutex()

    private var launcherAppsCache: Set<String>? = null
    private var launcherPackageCache: String? = null
    private var lastLauncherCheck = 0L

    private suspend fun getLauncherInfo(): Pair<Set<String>, String?> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cachedApps = launcherAppsCache
        if (cachedApps != null && now - lastLauncherCheck < 120000) {
            return@withContext cachedApps to launcherPackageCache
        }
        val pm = this@HomeViewModel.context.packageManager
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

    val allDatabaseUsage: Flow<List<DailyUsageEntity>> = shieldRepository.getRecentUsage(30)
        .debounce(300)
        .distinctUntilChanged()

    private val _globalFallbackMap = MutableStateFlow<Map<String, List<UsageRecord.Live>>>(emptyMap())
    val globalFallbackMap: StateFlow<Map<String, List<UsageRecord.Live>>> = _globalFallbackMap.asStateFlow()

    val fullUsageHistory: Flow<List<UsageHistoryGroup>> = combine(
        allDatabaseUsage,
        shieldRepository.getDatesWithHourlyUsage(),
        _globalFallbackMap,
        userPreferencesRepository.userPreferencesFlow
    ) { dbList, hourlyDates, fallbackMap, prefs ->
        val dateFormat = getDateFormat()
        val todayStr = dateFormat.format(Date())
        val preferSystem = prefs.preferSystemUsageHistory

        val earliestDateStr = dbList.lastOrNull()?.date ?: dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time)
        val earliestCal = Calendar.getInstance().apply {
            time = dateFormat.parse(earliestDateStr) ?: time
        }

        val groups = ArrayList<UsageHistoryGroup>(31)
        val cal = Calendar.getInstance()

        val dbRecordsByDate = dbList.groupBy { it.date }
        val hourlyDatesSet = hourlyDates.toSet()

        var dayCount = 0
        while (!cal.before(earliestCal) && dayCount < 30) {
            dayCount++
            val dateStr = dateFormat.format(cal.time)
            val isToday = dateStr == todayStr

            val dbRecords = dbRecordsByDate[dateStr] ?: emptyList()
            val dbTotal = dbRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L

            val actualSystemRecords = fallbackMap[dateStr] ?: emptyList()
            val systemRecords = if (preferSystem) actualSystemRecords else emptyList()
            val systemTotal = actualSystemRecords.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L

            val hasHourly = hourlyDatesSet.contains(dateStr)
            val hasSnap = dbRecords.any { it.packageName != "TOTAL" && it.packageName != "SHIELD_TOTAL" && it.packageName != "GOAL_TOTAL" && it.packageName != "OTHER_TOTAL" }
            val hasPiechart = dbRecords.any { it.packageName == "SHIELD_TOTAL" || it.packageName == "GOAL_TOTAL" || it.packageName == "OTHER_TOTAL" }

            val dbShieldTotal = dbRecords.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: 0L
            val dbGoalTotal = dbRecords.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: 0L
            val dbOtherTotal = dbRecords.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis ?: 0L
            val dbAppSum = dbRecords.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }
                .sumOf { it.usageTimeMillis }

            if (isToday) {
                val liveRecords = mutableListOf<UsageRecord>()
                dbRecords.forEach { liveRecords.add(UsageRecord.Database(it)) }

                if (actualSystemRecords.isNotEmpty()) {
                    liveRecords.addAll(actualSystemRecords)
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
                    totalTimeMillis = if (preferSystem) liveTotal else dbTotal,
                    hasDatabaseRecord = dbRecords.isNotEmpty(),
                    hasSystemData = actualSystemRecords.isNotEmpty(),
                    isLive = true,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly,
                    hasPiechartData = true,
                    systemTotalMillis = liveTotal,
                    databaseAppSumMillis = dbAppSum,
                    shieldTotalMillis = if (preferSystem) liveShield else dbShieldTotal,
                    goalTotalMillis = if (preferSystem) liveGoal else dbGoalTotal,
                    otherTotalMillis = if (preferSystem) liveOther else dbOtherTotal
                ))
            } else if (dbRecords.isEmpty() && actualSystemRecords.isEmpty()) {
                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = emptyList(),
                    totalTimeMillis = 0L,
                    isMissing = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly,
                    systemTotalMillis = 0L,
                    databaseAppSumMillis = 0L
                ))
            } else if (dbRecords.isEmpty() && actualSystemRecords.isNotEmpty()) {
                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = actualSystemRecords,
                    totalTimeMillis = if (preferSystem) systemTotal else 0L,
                    hasSystemData = true,
                    hasSnapshot = false,
                    hasHourlyUsage = hasHourly,
                    systemTotalMillis = systemTotal,
                    databaseAppSumMillis = 0L
                ))
            } else {
                val combinedRecords = mutableListOf<UsageRecord>()
                combinedRecords.addAll(dbRecords.map { UsageRecord.Database(it) })
                if (actualSystemRecords.isNotEmpty()) {
                    combinedRecords.addAll(actualSystemRecords)
                }

                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = combinedRecords,
                    totalTimeMillis = if (preferSystem) systemTotal else dbTotal,
                    hasDatabaseRecord = true,
                    hasSystemData = actualSystemRecords.isNotEmpty(),
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly,
                    hasPiechartData = hasPiechart,
                    systemTotalMillis = systemTotal,
                    databaseAppSumMillis = dbAppSum,
                    shieldTotalMillis = dbShieldTotal,
                    goalTotalMillis = dbGoalTotal,
                    otherTotalMillis = dbOtherTotal
                ))
            }

            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        groups
    }.debounce(200)
        .flowOn(Dispatchers.Default)

    val repairableData: Flow<List<UsageHistoryGroup>> = combine(
        fullUsageHistory,
        userPreferencesRepository.userPreferencesFlow
    ) { groups, prefs ->
        if (prefs.allowRepairNonUnavailable) {
            groups.filter { (it.hasSystemData || it.hasDatabaseRecord) && !it.isLive }
        } else {
            groups.filter { (it.isMissing || !it.hasDatabaseRecord) && it.hasSystemData && !it.isLive }
        }
    }

    private val _isRepairing = MutableStateFlow(false)
    val isRepairing = _isRepairing.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val homeScreenPreferences: StateFlow<UserPreferences> = userPreferences
        .map { it }
        .distinctUntilChanged { old, new ->
            old.screenTimeTargetMinutes == new.screenTimeTargetMinutes &&
                    old.showDatabaseIndicator == new.showDatabaseIndicator &&
                    old.expressiveColors == new.expressiveColors &&
                    old.bedtimeEnabled == new.bedtimeEnabled &&
                    old.bedtimeStartTime == new.bedtimeStartTime &&
                    old.bedtimeEndTime == new.bedtimeEndTime &&
                    old.bedtimeDays == new.bedtimeDays
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _systemOnlyUsageHistory = MutableStateFlow<List<DailyUsage>>(emptyList())
    val systemOnlyUsageHistory: StateFlow<List<DailyUsage>> = _systemOnlyUsageHistory.asStateFlow()

    fun fetchSystemOnlyUsageHistory() {
        viewModelScope.launch(Dispatchers.Default) {
            val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(this@HomeViewModel.context.packageName, launcherPackage)

            val now = System.currentTimeMillis()
            val history = mutableListOf<DailyUsage>()

            for (i in 0 until 21) {
                val start = getMidnight(i)
                val end = if (i == 0) now else getMidnight(i - 1)
                val isToday = i == 0

                val usageMap = mutableMapOf<String, Long>()

                if (isToday) {
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
                                        val pkg = activePkg
                                        val segmentStart = maxOf(activeStartTime, start)
                                        val segmentEnd = minOf(time, end)
                                        if (segmentStart < segmentEnd) {
                                            usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
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
                            usageMap[activePkg] = (usageMap[activePkg] ?: 0L) + (segmentEnd - segmentStart)
                        }
                    }
                } else {
                    val stats = try { usm.queryAndAggregateUsageStats(start, end) } catch (e: Exception) { null }
                    stats?.forEach { (pkg, stat) ->
                        if (pkg !in excludePackages && pkg in launcherApps) {
                            val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                            if (time > 0) usageMap[pkg] = time
                        }
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

    suspend fun repairData(date: String, mode: RepairMode = RepairMode.SYSTEM) {
        refreshMutex.withLock {
            repairDataInternal(date, mode)
        }
    }

    private suspend fun repairDataInternal(date: String, mode: RepairMode = RepairMode.SYSTEM) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val dateFormat = getDateFormat()
        val todayStr = dateFormat.format(Date())

        val dbRecords = shieldRepository.getDailyUsagesForDateSync(date)
        val dbAppRecords = dbRecords.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }

        val recordsToUse = if (mode == RepairMode.SYSTEM) {
            _globalFallbackMap.value[date] ?: (if (prefs.allowRepairNonUnavailable) dbAppRecords.map { UsageRecord.Live(it.packageName, it.usageTimeMillis) } else null)
        } else {
            dbAppRecords.map { UsageRecord.Live(it.packageName, it.usageTimeMillis) }
        }

        if (recordsToUse.isNullOrEmpty()) return

        _isRepairing.value = true
        try {
            shieldRepository.isShieldsLoaded.first { it }

            val allShields = shieldRepository.allShields.first()
            val shieldPkgs = allShields.asSequence().filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
            val goalPkgs = allShields.asSequence().filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()

            var sUsage = 0L
            var gUsage = 0L
            var appSum = 0L
            val entitiesToInsert = mutableListOf<DailyUsageEntity>()

            recordsToUse.forEach { record ->
                if (record.packageName != "TOTAL") {
                    val existing = dbAppRecords.find { it.packageName == record.packageName }?.usageTimeMillis ?: 0L
                    val finalUsage = if (mode == RepairMode.SYSTEM) maxOf(record.usageTimeMillis, existing) else existing

                    entitiesToInsert.add(
                        DailyUsageEntity(
                            id = dbAppRecords.find { it.packageName == record.packageName }?.id ?: 0,
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

            val systemTotal = if (mode == RepairMode.SYSTEM) {
                recordsToUse.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: appSum
            } else appSum

            var finalTotal = if (mode == RepairMode.SYSTEM) maxOf(appSum, systemTotal) else appSum

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

            val finalShieldTotal = if (mode == RepairMode.SYSTEM) maxOf(sUsage, existingShieldTotal) else sUsage
            val finalGoalTotal = if (mode == RepairMode.SYSTEM) maxOf(gUsage, existingGoalTotal) else gUsage
            val oUsage = (finalTotal - (finalShieldTotal + finalGoalTotal)).coerceAtLeast(0L)

            entitiesToInsert.add(DailyUsageEntity(id = dbRecords.find { it.packageName == "TOTAL" }?.id ?: 0, date = date, packageName = "TOTAL", usageTimeMillis = finalTotal))
            entitiesToInsert.add(DailyUsageEntity(id = dbRecords.find { it.packageName == "SHIELD_TOTAL" }?.id ?: 0, date = date, packageName = "SHIELD_TOTAL", usageTimeMillis = finalShieldTotal))
            entitiesToInsert.add(DailyUsageEntity(id = dbRecords.find { it.packageName == "GOAL_TOTAL" }?.id ?: 0, date = date, packageName = "GOAL_TOTAL", usageTimeMillis = finalGoalTotal))
            entitiesToInsert.add(DailyUsageEntity(id = dbRecords.find { it.packageName == "OTHER_TOTAL" }?.id ?: 0, date = date, packageName = "OTHER_TOTAL", usageTimeMillis = oUsage))

            shieldRepository.insertAllDailyUsage(entitiesToInsert)
            userPreferencesRepository.refreshGlobalStreak(shieldRepository)
        } catch (e: Exception) {
            android.util.Log.e("HomeVM", "Error repairing data: ${e.message}")
        } finally {
            _isRepairing.value = false
            refreshUsageStats(showLoading = false)
        }
    }

    private var allShields: List<ShieldEntity> = emptyList()
    private var allHistory: List<DailyUsageEntity> = emptyList()
    private var globalHistory: List<DailyUsageEntity> = emptyList()
    private var dismissedUninstalledApps: Map<String, String> = emptyMap()

    private val _appDetailUiState = MutableStateFlow(AppDetailUiState())
    val appDetailUiState: StateFlow<AppDetailUiState> = _appDetailUiState.asStateFlow()

    private var appDetailJob: Job? = null
    private var detailFallbackMap: Map<String, Long> = emptyMap()
    private var currentTargetMinutes: Int = 0
    private var prefGlobalBestStreak: Int = 0
    private var preferSystemUsageHistory: Boolean = true

    init {
        viewModelScope.launch {
            try {
                shieldRepository.isShieldsLoaded.first { it }

                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -21)
                val thresholdDate = getDateFormat().format(cal.time)
                try {
                    shieldRepository.deleteOldHourlyUsage(thresholdDate)
                } catch (e: Exception) {
                    android.util.Log.e("HomeVM", "Failed to delete old hourly usage", e)
                }

                setupDataObservers()

                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        syncDataNowInternal(isInitial = true)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeVM", "Initial sync failed", e)
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }

                startRealTimeUpdates()
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "ViewModel init failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun updateUiStateFromDatabase(
        shields: List<ShieldEntity>,
        usage: List<DailyUsageEntity>,
        global: List<DailyUsageEntity>,
        prefs: UserPreferences,
        fallbackMap: Map<String, List<UsageRecord.Live>>
    ) {
        val dateFormat = getDateFormat()
        val todayStr = dateFormat.format(Date())
        val selectedDate = _uiState.value.selectedDateMillis
        val selectedDateStr = dateFormat.format(Date(selectedDate))
        val selectedDayUsage = usage.filter { it.date == selectedDateStr }
        val dbApps = selectedDayUsage.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }
        val fallbackDayApps = fallbackMap[selectedDateStr]?.filter { it.packageName != "TOTAL" } ?: emptyList()
        val mergedAppMap = mutableMapOf<String, Long>()
        dbApps.forEach { mergedAppMap[it.packageName] = it.usageTimeMillis }
        if (prefs.preferSystemUsageHistory) {
            fallbackDayApps.forEach { record ->
                mergedAppMap[record.packageName] = maxOf(mergedAppMap[record.packageName] ?: 0L, record.usageTimeMillis)
            }
        }

        val allApps = mergedAppMap.entries.sortedByDescending { it.value }.map { (pkg, time) ->
            val cached = appInfoCache[pkg]
            val existing = _uiState.value.allAppsUsage.find { it.packageName == pkg }
            AppUsageInfo(
                packageName = pkg,
                appName = cached ?: pkg,
                totalTimeVisible = time,
                hasDatabaseRecord = dbApps.any { it.packageName == pkg },
                hasSystemData = fallbackDayApps.any { it.packageName == pkg },
                sessionCount = existing?.sessionCount ?: (if (time > 4000) 1 else 0),
                lastTimeUsed = existing?.lastTimeUsed ?: 0L
            )
        }

        val totalFromDb = selectedDayUsage.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
        val totalFromFallback = if (prefs.preferSystemUsageHistory) {
            fallbackMap[selectedDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
        } else 0L
        val currentTotal = mergedAppMap.values.sum()

        val shieldTotal = selectedDayUsage.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: 0L
        val goalTotal = selectedDayUsage.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: 0L
        val otherTotal = selectedDayUsage.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis ?: 0L

        val isSelectedDayToday = selectedDateStr == todayStr

        val historyList = (0 until 21).map { i ->
            val dStart = getMidnight(i)
            val dStr = dateFormat.format(Date(dStart))
            val dbEntry = global.find { it.date == dStr }
            val hasSys = fallbackMap[dStr] != null
            val fallbackTotal = if (prefs.preferSystemUsageHistory) {
                fallbackMap[dStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
            } else null

            DailyUsage(
                date = dStart,
                totalTime = dbEntry?.usageTimeMillis ?: fallbackTotal ?: 0L,
                hasDatabaseRecord = dbEntry != null,
                hasSystemData = hasSys,
                isLive = i == 0
            )
        }

        val targetMillis = prefs.screenTimeTargetMinutes * 60 * 1000L
        val historyByDate = usage.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }.groupBy { it.date }
        val snapshotStamps = (0 until 21).map { i ->
            val dStart = getMidnight(i)
            val dStr = dateFormat.format(Date(dStart))
            val dbDayApps = historyByDate[dStr] ?: emptyList()

            val topPkgEntry = if (dbDayApps.isNotEmpty()) {
                val top = dbDayApps.maxByOrNull { it.usageTimeMillis }
                top?.packageName to top?.usageTimeMillis
            } else if (prefs.preferSystemUsageHistory) {
                val fallbackTop = fallbackMap[dStr]?.filter { it.packageName != "TOTAL" }?.maxByOrNull { it.usageTimeMillis }
                fallbackTop?.packageName to fallbackTop?.usageTimeMillis
            } else null

            val topPkg = topPkgEntry?.first
            val usageT = topPkgEntry?.second ?: 0L
            val hasDb = dbDayApps.isNotEmpty()
            val hasSys = fallbackMap[dStr] != null

            if (topPkg != null) {
                val cached = appInfoCache[topPkg]
                AppUsageInfo(
                    packageName = topPkg,
                    appName = cached ?: topPkg,
                    totalTimeVisible = usageT,
                    hasDatabaseRecord = hasDb,
                    hasSystemData = hasSys,
                    isLive = i == 0
                )
            } else {
                AppUsageInfo("", "", 0L, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0)
            }
        }

        _uiState.update { state ->
            val shouldStillLoad = state.isLoading && fallbackMap.isEmpty() && isSelectedDayToday

            state.copy(
                totalScreenTime = currentTotal,
                allAppsUsage = allApps,
                topApps = allApps.take(5),
                shieldUsage = maxOf(state.shieldUsage, shieldTotal),
                goalUsage = maxOf(state.goalUsage, goalTotal),
                otherUsage = maxOf(state.otherUsage, otherTotal),
                dailyUsageHistory = historyList.reversed(),
                snapshotStamps = snapshotStamps.reversed(),
                targetMillis = targetMillis,
                isLoading = shouldStillLoad
            )
        }
    }

    private fun setupDataObservers() {
        var lastPreferSystem: Boolean? = null
        var lastOnboardingCompleted: Boolean? = null

        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                val liveShields = withContext(Dispatchers.IO) {
                    try {
                        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                        val accurateUsageMap = ScreenUsageHelper.fetchAppUsageTodayTillNow(usm)
                        shields.map { shield ->
                            val usage = accurateUsageMap[shield.packageName] ?: 0L
                            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
                            val liveRemaining = (limitMillis - usage).coerceAtLeast(0L)
                            val finalRemaining = if (shield.remainingTimeMillis > 0 && liveRemaining > shield.remainingTimeMillis) {
                                shield.remainingTimeMillis
                            } else {
                                liveRemaining
                            }
                            shield.copy(remainingTimeMillis = finalRemaining)
                        }
                    } catch (e: Exception) {
                        shields
                    }
                }
                _uiState.update { it.copy(
                    activeShields = sortShields(liveShields.filter { it.type == FocusType.SHIELD }, it.shieldSortType),
                    activeGoals = sortShields(liveShields.filter { it.type == FocusType.GOAL }, it.goalSortType)
                ) }
                updateUninstalledShieldPackages()
                val currentPkg = _appDetailUiState.value.packageName
                if (currentPkg.isNotEmpty()) {
                    val shield = liveShields.find { it.packageName == currentPkg }
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
            combine(
                shieldRepository.getRecentUsage(21),
                shieldRepository.getLastNDaysGlobalUsage(60),
                userPreferencesRepository.userPreferencesFlow,
                _globalFallbackMap
            ) { usage, global, prefs, fallbackMap ->
                val forceUpdate = (lastPreferSystem != null && lastPreferSystem != prefs.preferSystemUsageHistory) ||
                        (lastOnboardingCompleted != null && lastOnboardingCompleted != prefs.onboardingStatsCompleted)

                lastPreferSystem = prefs.preferSystemUsageHistory
                lastOnboardingCompleted = prefs.onboardingStatsCompleted

                allHistory = usage
                globalHistory = global

                currentTargetMinutes = prefs.screenTimeTargetMinutes
                prefGlobalBestStreak = prefs.globalBestStreak
                preferSystemUsageHistory = prefs.preferSystemUsageHistory

                dismissedUninstalledApps = prefs.dismissedUninstalledApps
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }

                updateUiStateFromDatabase(allShields, usage, global, prefs, fallbackMap)
                forceUpdate
            }.debounce(2000).collect { forceUpdate ->
                try {
                    refreshMutex.withLock {
                        if (forceUpdate) _uiState.update { it.copy(isLoading = true) }
                        updateGlobalFallbackInternal(forceFull = forceUpdate)
                        performUsageStatsRefresh(showLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeVM", "Data observer collect failed: ${e.message}")
                }
            }
        }
    }

    val todayHourlyUsage: Flow<List<HourlyUsageEntity>> = allDatabaseUsage.flatMapLatest {
        val today = getDateFormat().format(Date())
        shieldRepository.getHourlyUsageForDate(today)
    }.flowOn(Dispatchers.Default)

    fun onRefresh() {
        triggerServiceRefresh()
        viewModelScope.launch {
            userPreferencesRepository.refreshAllAppStreaks(shieldRepository)
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            if (prefs.smartRepairOnRefresh) {
                resetCarryover()
            } else {
                syncDataNow()
            }
        }
    }

    fun syncDataNow() {
        viewModelScope.launch {
            syncDataNowInternal()
        }
    }

    private suspend fun syncDataNowInternal(isInitial: Boolean = false) {
        triggerServiceRefresh()
        refreshMutex.withLock {
            if (!isInitial) _uiState.update { it.copy(isLoading = true) }
            try {
                val todayStr = getDateFormat().format(Date())

                val syncManager = UsageSyncManager(this@HomeViewModel.context, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }

                ScreenUsageHelper.clearCache()
                updateGlobalFallbackInternal(forceFull = isInitial)
                performUsageStatsRefresh(showLoading = false)

                repairDataInternal(todayStr)
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Sync failed: ${e.message}")
            } finally {
                userPreferencesRepository.refreshAllAppStreaks(shieldRepository)
                performUsageStatsRefresh(showLoading = false)
            }
        }
    }

    fun resetCarryover() {
        triggerServiceRefresh()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val today = getDateFormat().format(Date())

                userPreferencesRepository.setLastSyncTimestamp(getMidnight(0))

                shieldRepository.deleteHourlyUsageForDate(today)

                val syncManager = UsageSyncManager(this@HomeViewModel.context, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Reset carryover failed: ${e.message}")
            } finally {
                userPreferencesRepository.refreshAllAppStreaks(shieldRepository)
                refreshUsageStats(showLoading = false)
            }
        }
    }

    fun deleteHourlyPackageUsageToday(packageName: String) {
        viewModelScope.launch {
            val today = getDateFormat().format(Date())
            shieldRepository.deleteHourlyUsageForPackage(today, packageName)
            refreshUsageStats(showLoading = false)
        }
    }

    fun deleteHourlyUsageAtHour(hour: Int, packageName: String) {
        viewModelScope.launch {
            val today = getDateFormat().format(Date())
            shieldRepository.deleteHourlyUsageAtHour(today, hour, packageName)
            refreshUsageStats(showLoading = false)
        }
    }

    private val midnightCache = arrayOfNulls<Long>(31)
    private var midnightCacheTime = 0L

    private fun getMidnight(offsetDaysFromToday: Int = 0): Long {
        val now = System.currentTimeMillis()
        if (now - midnightCacheTime > 60000) {
            midnightCache.fill(null)
            midnightCacheTime = now
        }
        midnightCache[offsetDaysFromToday]?.let { return it }
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -offsetDaysFromToday)
        val result = cal.timeInMillis
        midnightCache[offsetDaysFromToday] = result
        return result
    }

    private var lastFullFallbackRefresh = 0L
    private var isUpdatingFullHistory = false

    private suspend fun updateGlobalFallback(forceFull: Boolean = false) {
        refreshMutex.withLock {
            updateGlobalFallbackInternal(forceFull)
        }
    }

    private suspend fun updateGlobalFallbackInternal(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val isFullNeeded = forceFull || _globalFallbackMap.value.isEmpty() || (now - lastFullFallbackRefresh > 3600000)

        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (launcherApps, launcherPackage) = getLauncherInfo()
        val excludePackages = setOfNotNull(this@HomeViewModel.context.packageName, launcherPackage)

        if (isFullNeeded && !isUpdatingFullHistory) {
            lastFullFallbackRefresh = now
            if (forceFull) {
                updateFullHistoryFallbackInternal()
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    updateFullHistoryFallbackInternal()
                }
            }
        }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayStr = getDateFormat().format(Date(todayStart))

        val results = if (now - todayStart < 86400000) {
            val todayDetailed = ScreenUsageHelper.fetchDetailedUsageToday(usm)
            val todayLiveRecords = todayDetailed.appUsageMap.mapNotNull { (pkg, time) ->
                if (pkg in excludePackages || pkg !in launcherApps) null
                else UsageRecord.Live(pkg, time)
            }.toMutableList()

            val todayTotal = todayLiveRecords.sumOf { it.usageTimeMillis }
            if (todayTotal > 0) {
                todayLiveRecords.add(UsageRecord.Live("TOTAL", todayTotal))
            }
            listOf(todayStr to todayLiveRecords)
        } else {
            fetchFallbackForDays(0..0, usm, launcherApps, excludePackages, now)
        }

        _globalFallbackMap.update { current ->
            current + results.toMap()
        }
    }

    private suspend fun updateFullHistoryFallbackInternal() {
        if (isUpdatingFullHistory) return
        isUpdatingFullHistory = true
        try {
            val now = System.currentTimeMillis()
            val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(this@HomeViewModel.context.packageName, launcherPackage)
            val results = fetchFallbackForDays(1..7, usm, launcherApps, excludePackages, now)
            _globalFallbackMap.update { current -> current + results.toMap() }
        } finally {
            isUpdatingFullHistory = false
        }
    }

    private suspend fun fetchFallbackForDays(
        range: IntRange,
        usm: UsageStatsManager,
        launcherApps: Set<String>,
        excludePackages: Set<String>,
        now: Long
    ): List<Pair<String, List<UsageRecord.Live>>> = coroutineScope {
        range.map { i ->
            async {
                val dateFormat = getDateFormat()
                val start = getMidnight(i)
                val end = if (i == 0) now else getMidnight(i - 1)
                val dateStr = dateFormat.format(Date(start))
                val usageMap = mutableMapOf<String, Long>()
                val events = try { usm.queryEvents(start - 1800000L, end) } catch (e: Exception) { null }
                if (events == null) return@async dateStr to emptyList<UsageRecord.Live>()
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
                                if (segmentStart < segmentEnd) usageMap[p] = (usageMap[p] ?: 0L) + (segmentEnd - segmentStart)
                            }
                            activePkg = null
                        }
                        UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            if (isScreenOn) {
                                if (activePkg != null) {
                                    val segmentStart = maxOf(activeStartTime, start)
                                    val segmentEnd = minOf(time, end)
                                    if (segmentStart < segmentEnd) usageMap[activePkg] = (usageMap[activePkg] ?: 0L) + (segmentEnd - segmentStart)
                                }
                                activePkg = pkg
                                activeStartTime = time
                            }
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            if (activePkg == pkg) {
                                val segmentStart = maxOf(activeStartTime, start)
                                val segmentEnd = minOf(time, end)
                                if (segmentStart < segmentEnd) usageMap[pkg] = (usageMap[pkg] ?: 0L) + (segmentEnd - segmentStart)
                                activePkg = null
                            }
                        }
                    }
                }
                if (activePkg != null && isScreenOn) {
                    val segmentStart = maxOf(activeStartTime, start)
                    val segmentEnd = minOf(now, end)
                    if (segmentStart < segmentEnd) usageMap[activePkg] = (usageMap[activePkg] ?: 0L) + (segmentEnd - segmentStart)
                }
                val dayRecords = mutableListOf<UsageRecord.Live>()
                var dayTotalSum = 0L
                val maxAllowedForDay = if (i == 0) (now - start).coerceAtLeast(0L) else 86400000L
                usageMap.forEach { (pkg, time) ->
                    if (pkg in excludePackages || pkg !in launcherApps) return@forEach
                    if (time > 0) {
                        val cappedTime = time.coerceAtMost(maxAllowedForDay)
                        dayTotalSum += cappedTime
                        dayRecords.add(UsageRecord.Live(pkg, cappedTime))
                    }
                }
                if (dayTotalSum > 0) {
                    val finalTotal = dayTotalSum.coerceAtMost(maxAllowedForDay)
                    dayRecords.add(UsageRecord.Live("TOTAL", finalTotal))
                    dateStr to dayRecords.sortedByDescending { it.usageTimeMillis }
                } else dateStr to emptyList<UsageRecord.Live>()
            }
        }.awaitAll()
    }

    private suspend fun updatePackageFallback(packageName: String) = withContext(Dispatchers.IO) {
        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dateFormat = getDateFormat()
        val result = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()
        for (i in 0..30) {
            val start = getMidnight(i); val end = if (i == 0) now else getMidnight(i - 1)
            val dateStr = dateFormat.format(Date(start))
            val events = usm.queryEvents(start - 1800000L, end); val event = UsageEvents.Event()
            var activePkg: String? = null; var activeStartTime = 0L; var isScreenOn = true; var dayUsage = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event); val pkg = event.packageName; val time = event.timeStamp
                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> isScreenOn = true
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        isScreenOn = false; activePkg?.let { p -> if (p == packageName) { val sS = maxOf(activeStartTime, start); val sE = minOf(time, end); if (sS < sE) dayUsage += (sE - sS) } }
                        activePkg = null
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (isScreenOn) { if (activePkg == packageName) { val sS = maxOf(activeStartTime, start); val sE = minOf(time, end); if (sS < sE) dayUsage += (sE - sS) }; activePkg = pkg; activeStartTime = time }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (activePkg == pkg) { if (pkg == packageName) { val sS = maxOf(activeStartTime, start); val sE = minOf(time, end); if (sS < sE) dayUsage += (sE - sS) }; activePkg = null }
                    }
                }
            }
            if (activePkg == packageName && isScreenOn) { val sS = maxOf(activeStartTime, start); val sE = minOf(now, end); if (sS < sE) dayUsage += (sE - sS) }
            if (dayUsage > 0) result[dateStr] = dayUsage
        }
        detailFallbackMap = result
    }

    fun selectDate(dateMillis: Long?) {
        val cal = Calendar.getInstance()
        if (dateMillis != null) cal.timeInMillis = dateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val date = cal.timeInMillis
        if (_uiState.value.selectedDateMillis == date && refreshJob?.isActive == true) return
        _uiState.update { it.copy(
            selectedDateMillis = date,
            allAppsUsage = emptyList(),
            topApps = emptyList(),
            isLoading = true
        ) }
        refreshUsageStats(showLoading = true)
    }

    private fun refreshUsageStats(showLoading: Boolean = true) {
        val previousJob = refreshJob
        refreshJob = viewModelScope.launch(Dispatchers.Default) {
            previousJob?.cancel()
            previousJob?.join()
            refreshMutex.withLock {
                performUsageStatsRefresh(showLoading)
            }
        }
    }

    private suspend fun performUsageStatsRefresh(showLoading: Boolean = true) {
        if (showLoading) _uiState.update { it.copy(isLoading = true) }

        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = this@HomeViewModel.context.packageManager

        val (launcherApps, launcherPackage) = getLauncherInfo()
        val excludePackages = setOfNotNull(this@HomeViewModel.context.packageName, launcherPackage)

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
        val dayEnd = if (dayStart + 86400000L > now) now else calDate.timeInMillis

        val dateFormat = getDateFormat()

        val todayDetailed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = isSelectedToday)
        }
        val filteredTodayUsage = todayDetailed.appUsageMap.filter { (pkg, _) ->
            val isUserApp = pkg in launcherApps || pm.getLaunchIntentForPackage(pkg) != null
            pkg !in excludePackages && isUserApp
        }.mapValues { (_, usage) ->
            usage.coerceAtMost(timeSinceMidnight)
        }.toMutableMap()

        if (isSelectedToday && timeSinceMidnight > 300_000L && filteredTodayUsage.values.sum() == 0L) {
            android.util.Log.w("HomeVM", "UsageHelper empty, falling back to queryAndAggregateUsageStats")
            val systemFallback = withContext(Dispatchers.IO) {
                usm.queryAndAggregateUsageStats(todayStart, now)
            }
            if (systemFallback != null) {
                systemFallback.forEach { (pkg, stats) ->
                    val isUserApp = pkg in launcherApps || pm.getLaunchIntentForPackage(pkg) != null
                    if (pkg !in excludePackages && isUserApp) {
                        val time = stats.totalTimeVisible.coerceAtLeast(stats.totalTimeInForeground).coerceAtMost(timeSinceMidnight)
                        if (time > 0) {
                            filteredTodayUsage[pkg] = maxOf(filteredTodayUsage[pkg] ?: 0L, time)
                        }
                    }
                }
            }
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
            } else if (preferSystemUsageHistory) {
                dayStats.forEach { (pkg, stat) ->
                    val isUserApp = pkg in launcherApps || pm.getLaunchIntentForPackage(pkg) != null
                    if (pkg in excludePackages || !isUserApp) return@forEach

                    val time = stat.totalTimeVisible.coerceAtLeast(stat.totalTimeInForeground)
                    if (time > 0) {
                        appTotals[pkg] = time
                        lastUsedMap[pkg] = stat.lastTimeUsed
                    }
                }
            }
        }

        val totalToday = filteredTodayUsage.values.sum().coerceAtMost(timeSinceMidnight)

        if (isSelectedToday && totalToday == 0L && timeSinceMidnight > 300_000L) {
            android.util.Log.w("HomeVM", "totalToday is 0 after refresh, scheduling deferred repair")
            val todayStr = dateFormat.format(Date(todayStart))
            viewModelScope.launch {
                delay(100)
                refreshMutex.withLock {
                    repairDataInternal(todayStr, RepairMode.SYSTEM)
                }
            }
        }

        val missingPkgs = appTotals.keys.filter { !appInfoCache.containsKey(it) }
        if (missingPkgs.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    missingPkgs.map { pkg ->
                        async {
                            try {
                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                val label = pm.getApplicationLabel(appInfo).toString()
                                appInfoCache[pkg] = label
                            } catch (_: Exception) {}
                        }
                    }.awaitAll()
                }
            }
        }

        val appList = appTotals.mapNotNull { (pkg, time) ->
            val existing = _uiState.value.allAppsUsage.find { it.packageName == pkg }
            val sessions = appSessionCounts[pkg] ?: existing?.sessionCount ?: (if (time > 4000) 1 else 0)
            val lastUsed = lastUsedMap[pkg] ?: existing?.lastTimeUsed ?: 0L
            val cached = appInfoCache[pkg]
            if (cached != null) {
                AppUsageInfo(pkg, cached, time, sessionCount = sessions, lastTimeUsed = lastUsed)
            } else {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    appInfoCache[pkg] = label
                    AppUsageInfo(pkg, label, time, sessionCount = sessions, lastTimeUsed = lastUsed)
                } catch (_: Exception) {
                    AppUsageInfo(pkg, pkg, time, sessionCount = sessions, lastTimeUsed = lastUsed)
                }
            }
        }

        val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }
        val topApps = allAppsUsage.take(5)

        val selectedDateStr = dateFormat.format(Date(selectedDate))
        val selectedDayHistory = allHistory.filter { it.date == selectedDateStr }
        val appSum = allAppsUsage.sumOf { it.totalTimeVisible }

        val storedShieldUsage = selectedDayHistory.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis
        val storedGoalUsage = selectedDayHistory.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis
        val storedOtherUsage = selectedDayHistory.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis

        val liveShields = allShields.map { shield ->
            val usage = filteredTodayUsage[shield.packageName] ?: 0L
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val liveRemaining = (limitMillis - usage).coerceAtLeast(0L)
            val finalRemaining = if (shield.remainingTimeMillis > 0 && liveRemaining > shield.remainingTimeMillis) {
                shield.remainingTimeMillis
            } else {
                liveRemaining
            }
            shield.copy(remainingTimeMillis = finalRemaining)
        }

        val selectedDayTotal = if (isSelectedToday) {
            appSum.coerceAtMost(timeSinceMidnight)
        } else {
            val dbTotal = selectedDayHistory.find { it.packageName == "TOTAL" }?.usageTimeMillis
            val fallbackTotal = if (preferSystemUsageHistory) {
                _globalFallbackMap.value[selectedDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
            } else null

            dbTotal ?: fallbackTotal ?: appSum.coerceAtMost(86400000L)
        }

        val (finalShieldUsage, finalGoalUsage, finalOtherUsage) = if (isSelectedToday || (storedShieldUsage == null && storedGoalUsage == null)) {
            val shieldPkgs = liveShields.asSequence().filter { it.type == FocusType.SHIELD }.map { it.packageName }.toSet()
            val goalPkgs = liveShields.asSequence().filter { it.type == FocusType.GOAL }.map { it.packageName }.toSet()

            var s = 0L
            var g = 0L
            allAppsUsage.forEach { app ->
                if (app.packageName in shieldPkgs) s += app.totalTimeVisible
                else if (app.packageName in goalPkgs) g += app.totalTimeVisible
            }
            val o = (selectedDayTotal - (s + g)).coerceAtLeast(0L)
            Triple(s, g, o)
        } else {
            Triple(storedShieldUsage ?: 0L, storedGoalUsage ?: 0L, storedOtherUsage ?: 0L)
        }

        val dbHourly = try {
            withTimeout(15000) { shieldRepository.getHourlyUsageForDateSync(selectedDateStr) }
        } catch (_: Exception) {
            emptyList()
        }
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val newlyLocked = mutableListOf<HourlyUsageEntity>()
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
            }
        }

        val allHourlyData = dbHourly + newlyLocked

        val hourlyUsage = (0..23).map { hour ->
            val appsInHour = if (isSelectedToday && hourlyAppUsage.containsKey(hour)) {
                val pkgMap = hourlyAppUsage[hour] ?: emptyMap()
                pkgMap.mapNotNull { (pkg, duration) ->
                    if (duration > 0) {
                        val cached = appInfoCache[pkg]
                        AppUsageInfo(pkg, cached ?: pkg, duration, lastTimeUsed = lastUsedMap[pkg] ?: 0L)
                    } else null
                }
            } else {
                appTotals.mapNotNull { (pkg, trueTotal) ->
                    val dbEntry = allHourlyData.find { it.hour == hour && it.packageName == pkg }

                    val durationForThisHour = if (dbEntry != null) {
                        dbEntry.usageTimeMillis
                    } else {
                        val lockedHours = allHourlyData.asSequence().filter { it.packageName == pkg }.map { it.hour }.toSet()
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
                        val cached = appInfoCache[pkg]
                        AppUsageInfo(pkg, cached ?: pkg, durationForThisHour, lastTimeUsed = lastUsedMap[pkg] ?: 0L)
                    } else null
                }
            }.let { list ->
                if (_uiState.value.hourlySortType == HourlySortType.USAGE_TIME) list.sortedByDescending { it.totalTimeVisible }
                else list.sortedByDescending { it.lastTimeUsed }
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
            ?: (if (preferSystemUsageHistory) _globalFallbackMap.value[yesterdayDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis else null)
            ?: 0L

        val todayDateStr = dateFormat.format(Date(todayStart))
        val actualTodayDbTotal = allHistory.find { it.date == todayDateStr && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L

        val actualTodayTotal = totalToday.coerceAtMost(timeSinceMidnight)

        val history = (0 until 21).map { i ->
            val dStart = getMidnight(i)
            val dStr = dateFormat.format(Date(dStart))

            val dbEntry = globalHistory.find { it.date == dStr }
            val hasSystemData = _globalFallbackMap.value[dStr] != null && preferSystemUsageHistory
            val dayTotal = if (dStr == selectedDateStr) {
                selectedDayTotal
            } else if (i == 0) {
                actualTodayTotal
            } else {
                val dbTotal = dbEntry?.usageTimeMillis
                val fallbackTotal = if (preferSystemUsageHistory) {
                    _globalFallbackMap.value[dStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
                } else null

                dbTotal ?: fallbackTotal ?: 0L
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
        val historyByDate = allHistory.filter { it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL") }.groupBy { it.date }
        val snapshotStamps = ArrayList<AppUsageInfo>(21)

        for (i in 0 until 21) {
            val dStart = getMidnight(i)
            val dStr = dateFormat.format(Date(dStart))
            val dbDayApps = historyByDate[dStr] ?: emptyList()

            val topEntry = if (dbDayApps.isNotEmpty()) {
                val dbMax = dbDayApps.maxByOrNull { it.usageTimeMillis }!!
                if (i == 0) {
                    val liveTop = filteredTodayUsage.maxByOrNull { it.value }
                    if (liveTop != null && liveTop.value > dbMax.usageTimeMillis) liveTop.key to liveTop.value
                    else dbMax.packageName to dbMax.usageTimeMillis
                } else dbMax.packageName to dbMax.usageTimeMillis
            } else {
                if (i == 0) filteredTodayUsage.maxByOrNull { it.value }?.let { it.key to it.value }
                else if (preferSystemUsageHistory) {
                    _globalFallbackMap.value[dStr]?.filter { it.packageName != "TOTAL" }
                        ?.maxByOrNull { it.usageTimeMillis }
                        ?.let { it.packageName to it.usageTimeMillis }
                } else null
            }

            val topPkg = topEntry?.first
            var usageT = topEntry?.second ?: 0L
            if (i == 0 && usageT > timeSinceMidnight + 10000) usageT = timeSinceMidnight

            val hasDb = dbDayApps.isNotEmpty()
            val hasSys = _globalFallbackMap.value[dStr]?.isNotEmpty() == true && preferSystemUsageHistory

            if (topPkg != null && (i == 0 || hasDb || (preferSystemUsageHistory && hasSys))) {
                val cached = appInfoCache[topPkg]
                snapshotStamps.add(AppUsageInfo(topPkg, cached ?: topPkg, usageT, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0))
            } else {
                snapshotStamps.add(AppUsageInfo("", "", 0L, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0))
            }
        }
        val (liveStreak, finalBestStreak) = userPreferencesRepository.refreshGlobalStreak(shieldRepository)

        _uiState.update { state -> state.copy(
            totalScreenTime      = selectedDayTotal,
            yesterdayScreenTime  = totalYesterday,
            percentageChange     = percentageChange,
            dailyUsageHistory    = history.reversed(),
            hourlyUsage          = hourlyUsage,
            snapshotStamps       = snapshotStamps.reversed(),
            topApps              = if (topApps.isEmpty() && isSelectedToday) state.topApps else topApps,
            allAppsUsage         = if (allAppsUsage.isEmpty() && isSelectedToday) state.allAppsUsage else allAppsUsage,
            shieldUsage          = finalShieldUsage,
            goalUsage            = finalGoalUsage,
            otherUsage           = finalOtherUsage,
            activeShields = sortShields(liveShields.filter { it.type == FocusType.SHIELD }, state.shieldSortType),
            activeGoals   = sortShields(liveShields.filter { it.type == FocusType.GOAL }, state.goalSortType),
            globalCurrentStreak = liveStreak,
            globalBestStreak = finalBestStreak,
            targetMillis = currentTargetMinutes * 60 * 1000L,
            isLoading = false
        ) }

    }

    fun loadAppDetail(packageName: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && _appDetailUiState.value.packageName == packageName && appDetailJob?.isActive == true) return
        val isNew = _appDetailUiState.value.packageName != packageName
        appDetailJob?.cancel()
        if (isNew) _appDetailUiState.value = AppDetailUiState(packageName = packageName, isLoading = true)
        else _appDetailUiState.update { it.copy(isLoading = true) }
        appDetailJob = viewModelScope.launch {
            try {
                val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val pm = this@HomeViewModel.context.packageManager; val dateFormat = getDateFormat()
                var appName = packageName
                try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    appName = pm.getApplicationLabel(appInfo).toString()
                    appInfoCache[packageName] = appName
                } catch (_: Exception) {}

                _appDetailUiState.update { it.copy(appName = appName) }
                withContext(Dispatchers.IO) { if (detailFallbackMap.isEmpty() || forceRefresh || isNew) updatePackageFallback(packageName) }
                combine(shieldRepository.getLastNDaysUsageForPackage(packageName, 21), shieldRepository.getShieldByPackageNameFlow(packageName), userPreferencesRepository.userPreferencesFlow) { historyDB, shield, prefs ->
                    try {
                        val detailed = withContext(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(5000) { ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true) } }
                        val todayU = detailed?.appUsageMap?.get(packageName) ?: 0L; val sessions = detailed?.sessionCounts?.get(packageName) ?: 0
                        val hourlyU = MutableList(24) { detailed?.hourlyUsageMap?.get(it)?.get(packageName) ?: 0L }; val peakH = hourlyU.indices.maxByOrNull { hourlyU[it] } ?: -1
                        val yesterdayStr = dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
                        val yesterdayU = historyDB.find { it.date == yesterdayStr }?.usageTimeMillis ?: if (prefs.preferSystemUsageHistory) detailFallbackMap[yesterdayStr] ?: 0L else 0L
                        val history = (0 until 21).map { i -> val dStart = getMidnight(i); val dStr = dateFormat.format(Date(dStart)); val dbE = historyDB.find { it.date == dStr }; val dTotal = if (i == 0) todayU else dbE?.usageTimeMillis ?: if (prefs.preferSystemUsageHistory) detailFallbackMap[dStr] ?: 0L else 0L; DailyUsage(dStart, dTotal, dbE != null, detailFallbackMap[dStr] != null, i == 0) }

                        val lastCharge = prefs.lastChargeTimestamp
                        val manualReset = prefs.manualResetTimestamps[packageName] ?: 0L
                        val resetTime = maxOf(lastCharge, manualReset)
                        var sinceLastCharge = 0L

                        if (resetTime > 0) {
                            val usageSince = withContext(Dispatchers.IO) { ScreenUsageHelper.fetchAppUsageSince(usm, resetTime) }
                            sinceLastCharge = usageSince[packageName] ?: 0L
                        }

                        _appDetailUiState.update { it.copy(
                            packageName = packageName,
                            appName = appName,
                            type = shield?.type,
                            todayUsage = todayU,
                            yesterdayUsage = yesterdayU,
                            averageUsage = if (history.any { it.totalTime > 0 }) history.filter { it.totalTime > 0 }.map { it.totalTime }.average().toLong() else 0L,
                            totalSessions = sessions.coerceAtLeast(if (todayU > 0) 1 else 0),
                            peakHour = peakH,
                            percentageChange = if (yesterdayU > 0) (todayU - yesterdayU).toFloat() / yesterdayU * 100 else if (todayU > 0) 100f else 0f,
                            usageHistory = history.reversed(),
                            hourlyUsage = hourlyU,
                            currentStreak = shield?.currentStreak ?: 0,
                            bestStreak = shield?.bestStreak ?: 0,
                            shieldEntity = shield,
                            isPaused = shield?.isPaused ?: false,
                            pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                            sinceLastChargeUsage = sinceLastCharge,
                            lastResetTimestamp = resetTime,
                            batteryStatsResetEnabled = prefs.batteryStatsResetEnabled,
                            isLoading = false
                        ) }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeVM", "Error in app detail combine for $packageName: ${e.message}")
                        _appDetailUiState.update { it.copy(isLoading = false) }
                    }
                }.catch { e ->
                    android.util.Log.e("HomeVM", "App detail flow failed for $packageName: ${e.message}")
                    _appDetailUiState.update { it.copy(isLoading = false) }
                }.collect()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "App detail load failed for $packageName: ${e.message}")
                _appDetailUiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun pauseShield(durationHours: Int?) {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val end = if (durationHours != null) System.currentTimeMillis() + (durationHours * 3600000L) else 0L
        val updated = shield.copy(isPaused = true, pauseEndTimestamp = end)
        _appDetailUiState.update { it.copy(shieldEntity = updated, isPaused = true, pauseEndTimestamp = end) }
        viewModelScope.launch { shieldRepository.updateShield(updated) }
    }

    fun resumeShield() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        val updated = shield.copy(isPaused = false, pauseEndTimestamp = 0L)
        _appDetailUiState.update { it.copy(shieldEntity = updated, isPaused = false, pauseEndTimestamp = 0L) }
        viewModelScope.launch { shieldRepository.updateShield(updated) }
    }

    fun resetAppUsage(packageName: String) {
        viewModelScope.launch {
            userPreferencesRepository.resetAppStats(packageName)
        }
    }

    fun setBatteryStatsResetEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBatteryStatsResetEnabled(enabled)
        }
    }

    fun onShieldSortTypeChange(sortType: ShieldSortType) {
        _uiState.update { it.copy(shieldSortType = sortType, activeShields = sortShields(it.activeShields, sortType)) }
    }

    fun onGoalSortTypeChange(sortType: ShieldSortType) {
        _uiState.update { it.copy(goalSortType = sortType, activeGoals = sortShields(it.activeGoals, sortType)) }
    }

    fun onHourlySortTypeChange(sortType: HourlySortType) {
        _uiState.update { it.copy(hourlySortType = sortType) }
        refreshUsageStats(showLoading = false)
    }

    private fun updateShieldedLists() {
        refreshUsageStats(showLoading = false)
    }

    private fun updateUninstalledShieldPackages() {
        if (allShields.isEmpty()) return
        viewModelScope.launch {
            val pm = context.packageManager
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val uninstalled = withContext(Dispatchers.IO) {
                allShields
                    .map { it.packageName }
                    .filter { pkg ->
                        try {
                            pm.getApplicationInfo(pkg, 0)
                            false
                        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                            true
                        }
                    }
                    .filter { pkg -> dismissedUninstalledApps[pkg] != todayStr }
                    .toSet()
            }
            _uiState.update { it.copy(uninstalledShieldPackageNames = uninstalled) }
        }
    }

    private fun sortShields(shields: List<ShieldEntity>, sortType: ShieldSortType): List<ShieldEntity> {
        return when (sortType) {
            ShieldSortType.ALPHABETICAL -> shields.sortedBy { it.appName.lowercase() }
            ShieldSortType.REMAINING_TIME -> shields.sortedBy {
                if (it.timeLimitMinutes > 0) it.remainingTimeMillis.toDouble() / (it.timeLimitMinutes * 60 * 1000L) else 0.0
            }
        }
    }

    private var isActive = true

    fun setActive(active: Boolean) {
        isActive = active
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            var lastUpdateDay = cal.get(Calendar.DAY_OF_YEAR)
            var lastSanityCheck = 0L
            while (true) {
                try {
                    if (!isActive) {
                        delay(5000)
                        continue
                    }
                    cal.timeInMillis = System.currentTimeMillis()
                    val currentDay = cal.get(Calendar.DAY_OF_YEAR)
                    if (currentDay != lastUpdateDay) {
                        appInfoCache.clear(); _globalFallbackMap.value = emptyMap(); detailFallbackMap = emptyMap()
                        val today = getMidnight(0); val yesterday = getMidnight(1)
                        if (_uiState.value.selectedDateMillis == yesterday) _uiState.update { it.copy(selectedDateMillis = today) }
                        lastUpdateDay = currentDay
                    }
                    refreshMutex.withLock {
                        performUsageStatsRefresh(showLoading = false)
                        refreshCurrentAppDetailUsage()
                    }
                    val now2 = System.currentTimeMillis()
                    if (now2 - lastSanityCheck > 300_000L) {
                        lastSanityCheck = now2
                        val checkTodayStart = getMidnight(0)
                        val checkTimeSinceMidnight = now2 - checkTodayStart
                        if (_uiState.value.totalScreenTime == 0L && checkTimeSinceMidnight > 300_000L) {
                            android.util.Log.w("HomeVM", "Sanity: totalScreenTime=0, scheduling repair")
                            val todayStr = getDateFormat().format(Date(checkTodayStart))
                            viewModelScope.launch {
                                delay(100)
                                refreshMutex.withLock {
                                    repairDataInternal(todayStr, RepairMode.SYSTEM)
                                }
                            }
                        }
                    }
                    val remainingToTarget = (currentTargetMinutes * 60 * 1000L - _uiState.value.totalScreenTime).coerceAtLeast(0L)
                    val interval = when {
                        remainingToTarget < 30_000L -> 2000L
                        remainingToTarget < 300_000L -> 5000L
                        else -> 15000L
                    }
                    delay(interval)
                } catch (e: Exception) {
                    android.util.Log.e("HomeVM", "Real-time update failed: ${e.message}")
                    delay(5000)
                }
            }
        }
    }

    private suspend fun refreshCurrentAppDetailUsage() {
        val pkg = _appDetailUiState.value.packageName
        if (pkg.isEmpty()) return

        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val detailedUsage = withContext(Dispatchers.IO) {
            ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true)
        }
        val currentTodayUsage = detailedUsage.appUsageMap[pkg] ?: 0L

        val appHourlyUsage = MutableList(24) { hour ->
            detailedUsage.hourlyUsageMap[hour]?.get(pkg) ?: 0L
        }
        val peakHour = appHourlyUsage.indices.maxByOrNull { appHourlyUsage[it] } ?: -1

        val yesterdayUsage = _appDetailUiState.value.yesterdayUsage
        val percentageChange = when {
            yesterdayUsage > 0 -> ((currentTodayUsage - yesterdayUsage).toFloat() / yesterdayUsage) * 100
            currentTodayUsage > 0 -> 100f
            else -> 0f
        }

        _appDetailUiState.update { it.copy(
            todayUsage = currentTodayUsage,
            percentageChange = percentageChange,
            totalSessions = detailedUsage.sessionCounts[pkg]?.coerceAtLeast(if (currentTodayUsage > 0) 1 else 0) ?: it.totalSessions,
            hourlyUsage = appHourlyUsage,
            peakHour = peakHour
        ) }
    }

    fun clearAppDetail(packageName: String) {
        if (_appDetailUiState.value.packageName == packageName) { appDetailJob?.cancel(); _appDetailUiState.value = AppDetailUiState() }
    }

    fun openSettingsSheet() { _appDetailUiState.update { it.copy(isSettingsSheetOpen = true) } }
    fun closeSettingsSheet() { _appDetailUiState.update { it.copy(isSettingsSheetOpen = false) } }

    fun saveFocus(
        packageName: String, appName: String, timeLimitMinutes: Int, maxEmergencyUses: Int, isRemindersEnabled: Boolean,
        isStrictModeEnabled: Boolean, isAutoQuitEnabled: Boolean, maxUsesPerPeriod: Int, refreshPeriodMinutes: Int,
        goalReminderPeriodMinutes: Int, isDelayAppEnabled: Boolean, isGoalCallerEnabled: Boolean = false,
        isGoalCallerSoundEnabled: Boolean = true, goalCallerSoundUri: String? = null,
        limitPeriod: LimitPeriod = LimitPeriod.DAILY
    ) {
        val type = _appDetailUiState.value.type ?: FocusType.SHIELD
        viewModelScope.launch {
            try {
                val existing = shieldRepository.getShieldByPackageName(packageName)
                val periodChanged = existing != null && existing.limitPeriod != limitPeriod
                val newRemainingMillis = if (periodChanged) timeLimitMinutes * 60 * 1000L else (existing?.remainingTimeMillis ?: (timeLimitMinutes * 60 * 1000L))
                val shield = existing?.copy(timeLimitMinutes = timeLimitMinutes, limitPeriod = limitPeriod, remainingTimeMillis = newRemainingMillis, maxEmergencyUses = maxEmergencyUses, isRemindersEnabled = isRemindersEnabled, isStrictModeEnabled = isStrictModeEnabled, isAutoQuitEnabled = isAutoQuitEnabled, maxUsesPerPeriod = maxUsesPerPeriod, refreshPeriodMinutes = refreshPeriodMinutes, goalReminderPeriodMinutes = goalReminderPeriodMinutes, isDelayAppEnabled = isDelayAppEnabled, isGoalCallerEnabled = isGoalCallerEnabled, isGoalCallerSoundEnabled = isGoalCallerSoundEnabled, goalCallerSoundUri = goalCallerSoundUri)
                    ?: ShieldEntity(packageName = packageName, appName = appName, type = type, timeLimitMinutes = timeLimitMinutes, limitPeriod = limitPeriod, maxEmergencyUses = maxEmergencyUses, isRemindersEnabled = isRemindersEnabled, isStrictModeEnabled = isStrictModeEnabled, isAutoQuitEnabled = isAutoQuitEnabled, maxUsesPerPeriod = maxUsesPerPeriod, refreshPeriodMinutes = refreshPeriodMinutes, goalReminderPeriodMinutes = goalReminderPeriodMinutes, isDelayAppEnabled = isDelayAppEnabled, isGoalCallerEnabled = isGoalCallerEnabled, isGoalCallerSoundEnabled = isGoalCallerSoundEnabled, goalCallerSoundUri = goalCallerSoundUri)
                shieldRepository.insertShield(shield); triggerServiceRefresh()
            } catch (e: Exception) { android.util.Log.e("HomeViewModel", "Error saving focus: ${e.message}") } finally { closeSettingsSheet() }
        }
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch { shieldRepository.deleteShield(shield); _appDetailUiState.update { it.copy(type = null, shieldEntity = null) }; triggerServiceRefresh() }
    }

    fun deleteShield(shield: ShieldEntity) {
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
            triggerServiceRefresh()
        }
    }

    private fun triggerServiceRefresh() {
        val intent = Intent("com.etrisad.zenith.action.REFRESH_SERVICES").apply { setPackage(this@HomeViewModel.context.packageName) }
        this@HomeViewModel.context.sendBroadcast(intent)
    }

    fun formatDuration(millis: Long): String {
        val h = millis / 3600000L; val m = (millis / 60000L) % 60L; val s = (millis / 1000L) % 60L
        return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m"; else -> "${s}s" }
    }

    fun onVisibleWeekChanged(pageIndex: Int) {
        viewModelScope.launch {
            val history = _uiState.value.dailyUsageHistory; if (history.isEmpty()) return@launch
            val pages = history.chunked(7); if (pageIndex !in pages.indices) return@launch
            val weekDays = pages[pageIndex]; val avg = if (weekDays.isNotEmpty()) weekDays.map { it.totalTime }.average().toLong() else 0L
            val dateFormat = getDateFormat(); val appUsageMap = mutableMapOf<String, Long>()
            val preferSystem = userPreferencesRepository.userPreferencesFlow.first().preferSystemUsageHistory
            weekDays.forEach { day ->
                val dateStr = dateFormat.format(Date(day.date))
                allHistory.filter { it.date == dateStr }.forEach { if (it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL")) appUsageMap[it.packageName] = (appUsageMap[it.packageName] ?: 0L) + it.usageTimeMillis }
                if (preferSystem) _globalFallbackMap.value[dateStr]?.forEach { if (it.packageName != "TOTAL") {
                    appUsageMap[it.packageName] = it.usageTimeMillis
                } }
            }
            val topApps = appUsageMap.entries.sortedByDescending { it.value }.take(3).map { (pkg, time) ->
                val cached = appInfoCache[pkg]
                AppUsageInfo(pkg, cached ?: pkg, time)
            }
            _uiState.update { it.copy(weeklyAvgTime = avg, weeklyTopApps = topApps) }
        }
    }

    private fun getDateFormat(): SimpleDateFormat {
        return dateFormatTL.get()!!
    }

    companion object {
        private val dateFormatTL = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }
}