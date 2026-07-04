package com.etrisad.zenith.ui.viewmodel

import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.UsageSyncManager
import com.etrisad.zenith.util.ScreenUsageHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

data class DailyUsage(
    val date: Long,
    val totalTime: Long,
    val hasDatabaseRecord: Boolean = false,
    val hasSystemData: Boolean = false,
    val isLive: Boolean = false
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
class UsageHistoryManager(
    private val context: Context,
    private val shieldRepository: ShieldRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    var dayStartHour: Int = 0
    var dayStartMinute: Int = 0

    private var launcherAppsCache: Set<String>? = null
    private var launcherPackageCache: String? = null
    private var lastLauncherCheck = 0L

    suspend fun getLauncherInfo(): Pair<Set<String>, String?> = withContext(Dispatchers.IO) {
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

    private var midnightCacheTime = 0L
    private val dayStartCache = mutableMapOf<Int, Long>()

    fun getMidnight(offsetDaysFromToday: Int = 0): Long {
        val now = System.currentTimeMillis()
        if (now - midnightCacheTime > 60000) {
            dayStartCache.clear()
            midnightCacheTime = now
        }
        dayStartCache[offsetDaysFromToday]?.let { return it }
        var dayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute)
        if (offsetDaysFromToday > 0) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_YEAR, -offsetDaysFromToday)
            }
            dayStart = com.etrisad.zenith.util.DateTimeUtils.getDayStartForDate(cal.timeInMillis, dayStartHour, dayStartMinute)
        }
        dayStartCache[offsetDaysFromToday] = dayStart
        return dayStart
    }

    companion object {
        private val dateFormatTL = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    fun getDateFormat(): SimpleDateFormat = dateFormatTL.get()!!

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
                groups.add(UsageHistoryGroup(
                    date = dateStr,
                    records = liveRecords,
                    totalTimeMillis = if (preferSystem) (fallbackMap[dateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: dbTotal) else dbTotal,
                    hasDatabaseRecord = dbRecords.isNotEmpty(),
                    hasSystemData = actualSystemRecords.isNotEmpty(),
                    isLive = true,
                    hasSnapshot = hasSnap,
                    hasHourlyUsage = hasHourly,
                    hasPiechartData = true,
                    systemTotalMillis = fallbackMap[dateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L,
                    databaseAppSumMillis = dbAppSum,
                    shieldTotalMillis = if (preferSystem) (fallbackMap[dateStr]?.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis ?: dbShieldTotal) else dbShieldTotal,
                    goalTotalMillis = if (preferSystem) (fallbackMap[dateStr]?.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis ?: dbGoalTotal) else dbGoalTotal,
                    otherTotalMillis = if (preferSystem) (fallbackMap[dateStr]?.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis ?: dbOtherTotal) else dbOtherTotal
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

    private val _systemOnlyUsageHistory = MutableStateFlow<List<DailyUsage>>(emptyList())
    val systemOnlyUsageHistory: StateFlow<List<DailyUsage>> = _systemOnlyUsageHistory.asStateFlow()

    fun fetchSystemOnlyUsageHistory() {
        scope.launch(Dispatchers.Default) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)

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
                                        val pkg2 = activePkg
                                        val segmentStart = maxOf(activeStartTime, start)
                                        val segmentEnd = minOf(time, end)
                                        if (segmentStart < segmentEnd) {
                                            usageMap[pkg2] = (usageMap[pkg2] ?: 0L) + (segmentEnd - segmentStart)
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

    private val refreshMutex = Mutex()

    suspend fun repairData(date: String, mode: RepairMode = RepairMode.SYSTEM) {
        refreshMutex.withLock {
            repairDataInternal(date, mode)
        }
    }

    suspend fun repairDataInternal(date: String, mode: RepairMode = RepairMode.SYSTEM) {
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
        }
    }

    fun clearGlobalFallback() {
        _globalFallbackMap.value = emptyMap()
    }

    private var lastFullFallbackRefresh = 0L
    private var isUpdatingFullHistory = false

    suspend fun updateGlobalFallback(forceFull: Boolean = false) {
        refreshMutex.withLock {
            updateGlobalFallbackInternal(forceFull)
        }
    }

    suspend fun updateGlobalFallbackInternal(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val isFullNeeded = forceFull || _globalFallbackMap.value.isEmpty() || (now - lastFullFallbackRefresh > 3600000)

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (launcherApps, launcherPackage) = getLauncherInfo()
        val excludePackages = setOfNotNull(context.packageName, launcherPackage)

        if (isFullNeeded && !isUpdatingFullHistory) {
            lastFullFallbackRefresh = now
            if (forceFull) {
                updateFullHistoryFallbackInternal()
            } else {
                scope.launch(Dispatchers.IO) {
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
            val todayDetailed = ScreenUsageHelper.fetchDetailedUsageToday(usm, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
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
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val (launcherApps, launcherPackage) = getLauncherInfo()
            val excludePackages = setOfNotNull(context.packageName, launcherPackage)
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

    suspend fun updatePackageFallback(packageName: String) = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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
        result
    }

    val todayHourlyUsage: Flow<List<HourlyUsageEntity>> = allDatabaseUsage.flatMapLatest {
        val today = com.etrisad.zenith.util.DateTimeUtils.getDayStartDateString(System.currentTimeMillis(), dayStartHour, dayStartMinute)
        shieldRepository.getHourlyUsageForDate(today)
    }.flowOn(Dispatchers.Default)
}
