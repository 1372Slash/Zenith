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
import com.etrisad.zenith.data.local.database.DbLogBuffer
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.model.IncentiveTier
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
    val websiteUsage: List<AppUsageInfo> = emptyList(),
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
    val selectedDateMillis: Long = 0L,
    val isLoading: Boolean = true,
    val uninstalledShieldPackageNames: Set<String> = emptySet(),
    val incentiveProgress: Float = 0f,
    val incentiveTier: IncentiveTier = IncentiveTier.UNLOCKED,
    val bonusUsesLeft: Int = 0
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

    private var dayStartHour: Int = 0
    private var dayStartMinute: Int = 0

    private val usageHistoryManager = UsageHistoryManager(context, shieldRepository, userPreferencesRepository, viewModelScope)
    private val shieldOperationsManager = ShieldOperationsManager(context, shieldRepository)

    val fullUsageHistory: Flow<List<UsageHistoryGroup>> get() = usageHistoryManager.fullUsageHistory
    val repairableData: Flow<List<UsageHistoryGroup>> get() = usageHistoryManager.repairableData
    val isRepairing: StateFlow<Boolean> get() = usageHistoryManager.isRepairing
    val systemOnlyUsageHistory: StateFlow<List<DailyUsage>> get() = usageHistoryManager.systemOnlyUsageHistory
    val todayHourlyUsage: Flow<List<HourlyUsageEntity>> get() = usageHistoryManager.todayHourlyUsage

    fun fetchSystemOnlyUsageHistory() = usageHistoryManager.fetchSystemOnlyUsageHistory()
    suspend fun setAllowRepairNonUnavailable(enabled: Boolean) = usageHistoryManager.setAllowRepairNonUnavailable(enabled)
    suspend fun repairData(date: String, mode: RepairMode = RepairMode.SYSTEM) = usageHistoryManager.repairData(date, mode)

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
                val thresholdDate = usageHistoryManager.getDateFormat().format(cal.time)
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

    private fun setupDataObservers() {
        var lastPreferSystem: Boolean? = null
        var lastOnboardingCompleted: Boolean? = null

        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                val liveShields = withContext(Dispatchers.IO) {
                    try {
                        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                        val accurateUsageMap = ScreenUsageHelper.fetchAppUsageTodayTillNow(usm, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
                        shields.map { shield ->
                            val usage = if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(shield.packageName)) {
                                val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(shield.packageName)
                                val todayDate = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                shieldRepository.getWebsiteUsage(todayDate, domain)?.usageTimeMillis ?: 0L
                            } else {
                                accurateUsageMap[shield.packageName] ?: 0L
                            }
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
                val currentIncentiveProgress = shieldRepository.getIncentiveGoalProgress().first()
                val currentIncentiveTier = IncentiveTier.fromProgress(currentIncentiveProgress)
                val currentBonusUsesLeft = if (currentIncentiveTier.bonusUses < Int.MAX_VALUE) {
                    shieldRepository.getIncentiveBonusUsesLeft()
                } else 0
                _uiState.update { it.copy(
                    incentiveProgress = currentIncentiveProgress,
                    incentiveTier = currentIncentiveTier,
                    bonusUsesLeft = currentBonusUsesLeft
                ) }
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
            val recentUsageFlow = shieldRepository.getRecentUsage(21).onEach {
                android.util.Log.d("ZenithDB", "DATA_OBSERVER: recentUsageFlow emitted ${it.size} items")
                DbLogBuffer.d("ZenithDB", "DATA_OBSERVER: recentUsageFlow emitted ${it.size} items")
            }
            val globalUsageFlow = shieldRepository.getLastNDaysGlobalUsage(60).onEach {
                android.util.Log.d("ZenithDB", "DATA_OBSERVER: globalUsageFlow emitted ${it.size} items")
                DbLogBuffer.d("ZenithDB", "DATA_OBSERVER: globalUsageFlow emitted ${it.size} items")
            }
            val prefsFlow = userPreferencesRepository.userPreferencesFlow.onEach {
                android.util.Log.d("ZenithDB", "DATA_OBSERVER: userPrefsFlow emitted")
                DbLogBuffer.d("ZenithDB", "DATA_OBSERVER: userPrefsFlow emitted")
            }
            combine(
                recentUsageFlow,
                globalUsageFlow,
                prefsFlow
            ) { usage, global, prefs ->
                android.util.Log.d("ZenithDB", "DATA_OBSERVER: recentUsage=${usage.size} records, globalUsage=${global.size} records")
                DbLogBuffer.d("ZenithDB", "DATA_OBSERVER: recentUsage=${usage.size} records, globalUsage=${global.size} records")
                if (usage.isEmpty() && global.isEmpty()) {
                    android.util.Log.w("ZenithDB", "DATA_OBSERVER: BOTH recentUsage AND globalUsage are EMPTY!")
                    DbLogBuffer.w("ZenithDB", "DATA_OBSERVER: BOTH recentUsage AND globalUsage are EMPTY!")
                }
                val forceUpdate = (lastPreferSystem != null && lastPreferSystem != prefs.preferSystemUsageHistory) ||
                        (lastOnboardingCompleted != null && lastOnboardingCompleted != prefs.onboardingStatsCompleted)

                lastPreferSystem = prefs.preferSystemUsageHistory
                lastOnboardingCompleted = prefs.onboardingStatsCompleted

                allHistory = usage
                globalHistory = global

                currentTargetMinutes = prefs.screenTimeTargetMinutes
                prefGlobalBestStreak = prefs.globalBestStreak
                preferSystemUsageHistory = prefs.preferSystemUsageHistory
                dayStartHour = prefs.dayStartHour.also { usageHistoryManager.dayStartHour = it }
                dayStartMinute = prefs.dayStartMinute.also { usageHistoryManager.dayStartMinute = it }

                dismissedUninstalledApps = prefs.dismissedUninstalledApps
                _uiState.update { it.copy(
                    bedtimeEnabled = prefs.bedtimeEnabled,
                    bedtimeStartTime = prefs.bedtimeStartTime,
                    bedtimeEndTime = prefs.bedtimeEndTime,
                    bedtimeDays = prefs.bedtimeDays
                ) }

                forceUpdate
            }.debounce(2000).collect { forceUpdate ->
                try {
                    refreshMutex.withLock {
                        if (forceUpdate) _uiState.update { it.copy(isLoading = true) }
                        usageHistoryManager.updateGlobalFallbackInternal(forceFull = forceUpdate)
                        performUsageStatsRefresh(showLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeVM", "Data observer collect failed: ${e.message}")
                }
            }
        }

        observeWebsiteUsage()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeWebsiteUsage() {
        viewModelScope.launch {
            _uiState.map { it.selectedDateMillis }
                .distinctUntilChanged()
                .flatMapLatest { millis ->
                    val dateStr = usageHistoryManager.getDateFormat().format(Date(millis))
                    shieldRepository.getWebsiteUsageForDate(dateStr)
                }
                .flowOn(Dispatchers.Default)
                .collect { entities ->
                    val usage = entities.map { entity ->
                        val domain = entity.domain
                        val displayName = com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                        val pkgName = com.etrisad.zenith.data.website.WebsiteRepository.createPackageName(domain)
                        AppUsageInfo(
                            packageName = pkgName,
                            appName = displayName,
                            totalTimeVisible = entity.usageTimeMillis,
                            hasDatabaseRecord = true,
                            isLive = false
                        )
                    }.sortedByDescending { it.totalTimeVisible }
                    _uiState.update { it.copy(websiteUsage = usage) }
                }
        }
    }

    fun onRefresh() {
        shieldOperationsManager.triggerServiceRefresh()
        viewModelScope.launch {
            userPreferencesRepository.refreshAppStreaks(shieldRepository)
            userPreferencesRepository.refreshWebStreaks(shieldRepository)
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
        shieldOperationsManager.triggerServiceRefresh()
        refreshMutex.withLock {
            if (!isInitial) _uiState.update { it.copy(isLoading = true) }
            try {
                val todayStr = usageHistoryManager.getDateFormat().format(Date())

                val syncManager = UsageSyncManager(this@HomeViewModel.context, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }

                ScreenUsageHelper.clearCache()
                usageHistoryManager.updateGlobalFallbackInternal(forceFull = isInitial)
                performUsageStatsRefresh(showLoading = false)

                usageHistoryManager.repairDataInternal(todayStr)
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Sync failed: ${e.message}")
            } finally {
                userPreferencesRepository.refreshAppStreaks(shieldRepository)
                userPreferencesRepository.refreshWebStreaks(shieldRepository)
                performUsageStatsRefresh(showLoading = false)
            }
        }
    }

    fun resetCarryover() {
        shieldOperationsManager.triggerServiceRefresh()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val today = usageHistoryManager.getDateFormat().format(Date())

                userPreferencesRepository.setLastSyncTimestamp(usageHistoryManager.getMidnight(0))

                shieldRepository.deleteHourlyUsageForDate(today)

                val syncManager = UsageSyncManager(this@HomeViewModel.context, shieldRepository, userPreferencesRepository)
                kotlinx.coroutines.withTimeoutOrNull(25000) {
                    syncManager.syncUsageData()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeVM", "Reset carryover failed: ${e.message}")
            } finally {
                userPreferencesRepository.refreshAppStreaks(shieldRepository)
                userPreferencesRepository.refreshWebStreaks(shieldRepository)
                refreshUsageStats(showLoading = false)
            }
        }
    }

    fun deleteHourlyPackageUsageToday(packageName: String) {
        viewModelScope.launch {
            val today = usageHistoryManager.getDateFormat().format(Date())
            shieldRepository.deleteHourlyUsageForPackage(today, packageName)
            refreshUsageStats(showLoading = false)
        }
    }

    fun deleteHourlyUsageAtHour(hour: Int, packageName: String) {
        viewModelScope.launch {
            val today = usageHistoryManager.getDateFormat().format(Date())
            shieldRepository.deleteHourlyUsageAtHour(today, hour, packageName)
            refreshUsageStats(showLoading = false)
        }
    }

    fun selectDate(dateMillis: Long?) {
        val now = System.currentTimeMillis()
        val date = if (dateMillis != null) {
            com.etrisad.zenith.util.DateTimeUtils.getDayStartForDate(dateMillis, dayStartHour, dayStartMinute)
        } else {
            com.etrisad.zenith.util.DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute)
        }
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
        val refreshId = System.currentTimeMillis() % 100000
        android.util.Log.d("ZenithDB", "REFRESH_START[$refreshId]: allHistory=${allHistory.size} allShields=${allShields.size} globalHistory=${globalHistory.size}")
        DbLogBuffer.d("ZenithDB", "REFRESH_START[$refreshId]: allHistory=${allHistory.size} allShields=${allShields.size} globalHistory=${globalHistory.size}")

        try {
        if (allHistory.isEmpty()) {
            android.util.Log.w("ZenithDB", "REFRESH[$refreshId]: allHistory EMPTY, falling back to direct Room query")
            DbLogBuffer.w("ZenithDB", "REFRESH[$refreshId]: allHistory EMPTY, falling back to direct Room query")
            try {
                allHistory = shieldRepository.getRecentUsage(30).first()
                android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: FALLBACK allHistory loaded ${allHistory.size} records")
                DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: FALLBACK allHistory loaded ${allHistory.size} records")
            } catch (e: Exception) {
                android.util.Log.e("ZenithDB", "REFRESH[$refreshId]: FALLBACK allHistory failed: ${e::class.simpleName}: ${e.message}")
                DbLogBuffer.e("ZenithDB", "REFRESH[$refreshId]: FALLBACK allHistory failed: ${e::class.simpleName}: ${e.message}")
            }
        }
        if (globalHistory.isEmpty()) {
            android.util.Log.w("ZenithDB", "REFRESH[$refreshId]: globalHistory EMPTY, falling back to direct Room query")
            DbLogBuffer.w("ZenithDB", "REFRESH[$refreshId]: globalHistory EMPTY, falling back to direct Room query")
            try {
                globalHistory = shieldRepository.getLastNDaysGlobalUsage(60).first()
                android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: FALLBACK globalHistory loaded ${globalHistory.size} records")
                DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: FALLBACK globalHistory loaded ${globalHistory.size} records")
            } catch (e: Exception) {
                android.util.Log.e("ZenithDB", "REFRESH[$refreshId]: FALLBACK globalHistory failed: ${e::class.simpleName}: ${e.message}")
                DbLogBuffer.e("ZenithDB", "REFRESH[$refreshId]: FALLBACK globalHistory failed: ${e::class.simpleName}: ${e.message}")
            }
        }

        val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = this@HomeViewModel.context.packageManager

        val (launcherApps, launcherPackage) = usageHistoryManager.getLauncherInfo()
        val excludePackages = setOfNotNull(this@HomeViewModel.context.packageName, launcherPackage)

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: launcherApps=${launcherApps.size} launcherPkg=$launcherPackage exclude=$excludePackages")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: launcherApps=${launcherApps.size} launcherPkg=$launcherPackage exclude=$excludePackages")

        val now = System.currentTimeMillis()
        val selectedDate = _uiState.value.selectedDateMillis
        val todayStart = usageHistoryManager.getMidnight(0)
        val timeSinceMidnight = now - todayStart

        val effectiveSelectedDate = if (selectedDate == 0L) todayStart else selectedDate
        if (selectedDate == 0L) {
            _uiState.update { it.copy(selectedDateMillis = todayStart) }
        }
        val isSelectedToday = effectiveSelectedDate == todayStart

        val calDate = java.util.Calendar.getInstance()
        calDate.timeInMillis = effectiveSelectedDate
        calDate.set(java.util.Calendar.HOUR_OF_DAY, dayStartHour)
        calDate.set(java.util.Calendar.MINUTE, dayStartMinute)
        calDate.set(java.util.Calendar.SECOND, 0)
        calDate.set(java.util.Calendar.MILLISECOND, 0)
        val dayStart = calDate.timeInMillis

        calDate.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = if (dayStart + 86400000L > now) now else calDate.timeInMillis

        val dateFormat = usageHistoryManager.getDateFormat()

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: fetching todayDetailed isSelectedToday=$isSelectedToday")
        val todayDetailed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = isSelectedToday, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
        }
        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: todayDetailed apps=${todayDetailed.appUsageMap.size} hourlyKeys=${todayDetailed.hourlyUsageMap.size}")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: todayDetailed apps=${todayDetailed.appUsageMap.size} hourlyKeys=${todayDetailed.hourlyUsageMap.size}")
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
                    usageHistoryManager.repairDataInternal(todayStr, RepairMode.SYSTEM)
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
                    val label = if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                        val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
                        com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                    } else pkg
                    AppUsageInfo(pkg, label, time, sessionCount = sessions, lastTimeUsed = lastUsed)
                }
            }
        }

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: appList=${appList.size} appTotals=${appTotals.size} filteredToday=${filteredTodayUsage.size} selectedDayHistory=${allHistory.filter { it.date == dateFormat.format(Date(if (selectedDate == 0L) todayStart else selectedDate)) }.size}")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: appList=${appList.size} appTotals=${appTotals.size} filteredToday=${filteredTodayUsage.size} selectedDayHistory=${allHistory.filter { it.date == dateFormat.format(Date(if (selectedDate == 0L) todayStart else selectedDate)) }.size}")

        val allAppsUsage = appList.sortedByDescending { it.totalTimeVisible }
        val topApps = allAppsUsage.take(5)

        val selectedDateStr = dateFormat.format(Date(selectedDate))

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_BEFORE websiteDateStr=$selectedDateStr currentThread=${Thread.currentThread().name}")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_BEFORE websiteDateStr=$selectedDateStr currentThread=${Thread.currentThread().name}")

        val websiteUsage = try {
            withContext(Dispatchers.IO) {
                android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_IO_START thread=${Thread.currentThread().name}")
                DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_IO_START thread=${Thread.currentThread().name}")
                val websiteEntities = shieldRepository.getWebsiteUsageListForDate(selectedDateStr)
                val result = websiteEntities.map { entity ->
                    val domain = entity.domain
                    val displayName = com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                    val pkgName = com.etrisad.zenith.data.website.WebsiteRepository.createPackageName(domain)
                    AppUsageInfo(
                        packageName = pkgName,
                        appName = displayName,
                        totalTimeVisible = entity.usageTimeMillis,
                        hasDatabaseRecord = true,
                        isLive = false
                    )
                }.sortedByDescending { it.totalTimeVisible }
                android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_IO_DONE size=${result.size}")
                DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_IO_DONE size=${result.size}")
                result
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.e("ZenithDB", "REFRESH[$refreshId]: WEBSITE_CANCELLED coroutine cancelled at withContext(Dispatchers.IO)")
            DbLogBuffer.e("ZenithDB", "REFRESH[$refreshId]: WEBSITE_CANCELLED coroutine cancelled at withContext(Dispatchers.IO)")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ZenithDB", "REFRESH[$refreshId]: WEBSITE_FAILED ${e::class.simpleName}: ${e.message}")
            DbLogBuffer.e("ZenithDB", "REFRESH[$refreshId]: WEBSITE_FAILED ${e::class.simpleName}: ${e.message}")
            emptyList()
        }

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_AFTER websiteSize=${websiteUsage.size} currentThread=${Thread.currentThread().name}")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: WEBSITE_AFTER websiteSize=${websiteUsage.size} currentThread=${Thread.currentThread().name}")
        val selectedDayHistory = allHistory.filter { it.date == selectedDateStr }
        val appSum = allAppsUsage.sumOf { it.totalTimeVisible }

        val storedShieldUsage = selectedDayHistory.find { it.packageName == "SHIELD_TOTAL" }?.usageTimeMillis
        val storedGoalUsage = selectedDayHistory.find { it.packageName == "GOAL_TOTAL" }?.usageTimeMillis
        val storedOtherUsage = selectedDayHistory.find { it.packageName == "OTHER_TOTAL" }?.usageTimeMillis

        val liveShields = allShields.map { shield ->
            val usage = if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(shield.packageName)) {
                val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(shield.packageName)
                val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                shieldRepository.getWebsiteUsage(todayDate, domain)?.usageTimeMillis ?: 0L
            } else {
                filteredTodayUsage[shield.packageName] ?: 0L
            }
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
                usageHistoryManager.globalFallbackMap.value[selectedDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
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
                        val label = cached ?: if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                            val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
                            com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                        } else pkg
                        AppUsageInfo(pkg, label, duration, lastTimeUsed = lastUsedMap[pkg] ?: 0L)
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
                        val label = cached ?: if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                            val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
                            com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                        } else pkg
                        AppUsageInfo(pkg, label, durationForThisHour, lastTimeUsed = lastUsedMap[pkg] ?: 0L)
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
            ?: (if (preferSystemUsageHistory) usageHistoryManager.globalFallbackMap.value[yesterdayDateStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis else null)
            ?: 0L

        val todayDateStr = dateFormat.format(Date(todayStart))
        val actualTodayDbTotal = allHistory.find { it.date == todayDateStr && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L

        val actualTodayTotal = totalToday.coerceAtMost(timeSinceMidnight)

        val history = (0 until 21).map { i ->
            val dStart = usageHistoryManager.getMidnight(i)
            val dStr = dateFormat.format(Date(dStart))

            val dbEntry = globalHistory.find { it.date == dStr }
            val hasSystemData = usageHistoryManager.globalFallbackMap.value[dStr] != null && preferSystemUsageHistory
            val dayTotal = if (dStr == selectedDateStr) {
                selectedDayTotal
            } else if (i == 0) {
                actualTodayTotal
            } else {
                val dbTotal = dbEntry?.usageTimeMillis
                val fallbackTotal = if (preferSystemUsageHistory) {
                    usageHistoryManager.globalFallbackMap.value[dStr]?.find { it.packageName == "TOTAL" }?.usageTimeMillis
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
            val dStart = usageHistoryManager.getMidnight(i)
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
                    usageHistoryManager.globalFallbackMap.value[dStr]?.filter { it.packageName != "TOTAL" }
                        ?.maxByOrNull { it.usageTimeMillis }
                        ?.let { it.packageName to it.usageTimeMillis }
                } else null
            }

            val topPkg = topEntry?.first
            var usageT = topEntry?.second ?: 0L
            if (i == 0 && usageT > timeSinceMidnight + 10000) usageT = timeSinceMidnight

            val hasDb = dbDayApps.isNotEmpty()
            val hasSys = usageHistoryManager.globalFallbackMap.value[dStr]?.isNotEmpty() == true && preferSystemUsageHistory

            if (topPkg != null && (i == 0 || hasDb || (preferSystemUsageHistory && hasSys))) {
                val cached = appInfoCache[topPkg]
                snapshotStamps.add(AppUsageInfo(topPkg, cached ?: topPkg, usageT, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0))
            } else {
                snapshotStamps.add(AppUsageInfo("", "", 0L, hasDatabaseRecord = hasDb, hasSystemData = hasSys, isLive = i == 0))
            }
        }
        val (liveStreak, finalBestStreak) = userPreferencesRepository.refreshGlobalStreak(shieldRepository)

        val prevState = _uiState.value
        val todayChanged = kotlin.math.abs(prevState.totalScreenTime - selectedDayTotal) >= 2000L
        val prevToday = prevState.dailyUsageHistory.firstOrNull()
        val curToday = history.reversed().firstOrNull()
        val todayBarChanged = prevToday?.totalTime != curToday?.totalTime

        android.util.Log.d("ZenithDB", "REFRESH[$refreshId]: UI_UPDATE prevTotal=${prevState.totalScreenTime} selectedDayTotal=$selectedDayTotal todayChanged=$todayChanged todayBarChanged=$todayBarChanged appListSize=${allAppsUsage.size} hourlySize=${hourlyUsage.size} shields=${liveShields.size} websiteSize=${websiteUsage.size}")
        DbLogBuffer.d("ZenithDB", "REFRESH[$refreshId]: UI_UPDATE prevTotal=${prevState.totalScreenTime} selectedDayTotal=$selectedDayTotal todayChanged=$todayChanged todayBarChanged=$todayBarChanged appListSize=${allAppsUsage.size} hourlySize=${hourlyUsage.size} shields=${liveShields.size} websiteSize=${websiteUsage.size}")

        if (!todayChanged && !todayBarChanged) {
            _uiState.update { state -> state.copy(
                hourlyUsage      = hourlyUsage,
                activeShields    = sortShields(liveShields.filter { it.type == FocusType.SHIELD }, state.shieldSortType),
                activeGoals      = sortShields(liveShields.filter { it.type == FocusType.GOAL }, state.goalSortType),
                globalCurrentStreak = liveStreak,
                globalBestStreak = finalBestStreak,
                isLoading        = false
            ) }
        } else {
            _uiState.update { state -> state.copy(
                totalScreenTime      = selectedDayTotal,
                yesterdayScreenTime  = totalYesterday,
                percentageChange     = percentageChange,
                dailyUsageHistory    = history.reversed(),
                hourlyUsage          = hourlyUsage,
                snapshotStamps       = snapshotStamps.reversed(),
                topApps              = if (topApps.isEmpty() && isSelectedToday) state.topApps else topApps,
                allAppsUsage         = if (allAppsUsage.isEmpty() && isSelectedToday) state.allAppsUsage else allAppsUsage,
                websiteUsage         = websiteUsage,
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

        android.util.Log.d("ZenithDB", "REFRESH_DONE[$refreshId]: totalScreenTime=${_uiState.value.totalScreenTime} appListSize=${_uiState.value.allAppsUsage.size} hourlySize=${_uiState.value.hourlyUsage.size} shields=${_uiState.value.activeShields.size} websiteSize=${_uiState.value.websiteUsage.size}")
        DbLogBuffer.d("ZenithDB", "REFRESH_DONE[$refreshId]: totalScreenTime=${_uiState.value.totalScreenTime} appListSize=${_uiState.value.allAppsUsage.size} hourlySize=${_uiState.value.hourlyUsage.size} shields=${_uiState.value.activeShields.size} websiteSize=${_uiState.value.websiteUsage.size}")

        } catch (e: kotlinx.coroutines.CancellationException) {
            android.util.Log.e("ZenithDB", "REFRESH_CANCELLED[$refreshId]: coroutine cancelled, propagating")
            DbLogBuffer.e("ZenithDB", "REFRESH_CANCELLED[$refreshId]: coroutine cancelled, propagating")
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ZenithDB", "REFRESH_FAILED[$refreshId]: ${e::class.simpleName}: ${e.message}")
            DbLogBuffer.e("ZenithDB", "REFRESH_FAILED[$refreshId]: ${e::class.simpleName}: ${e.message}")
        }
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
                val pm = this@HomeViewModel.context.packageManager; val dateFormat = usageHistoryManager.getDateFormat()
                var appName = packageName
                if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(packageName)) {
                    val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(packageName)
                    appName = com.etrisad.zenith.data.website.WebsiteRepository.getDisplayName(domain, "https://$domain")
                } else {
                    try {
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        appName = pm.getApplicationLabel(appInfo).toString()
                        appInfoCache[packageName] = appName
                    } catch (_: Exception) {}
                }

                _appDetailUiState.update { it.copy(appName = appName) }
                if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(packageName)) {
                    val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(packageName)
                    combine(shieldRepository.getWebsiteUsageForDomain(domain), shieldRepository.getShieldByPackageNameFlow(packageName), userPreferencesRepository.userPreferencesFlow) { websiteHistory, shield, prefs ->
                        try {
                            val todayStr = dateFormat.format(Date())
                            val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                            val yesterdayStr = dateFormat.format(yesterdayCal.time)
                            val todayWeb = websiteHistory.find { it.date == todayStr }
                            val yesterdayWeb = websiteHistory.find { it.date == yesterdayStr }
                            val todayU = todayWeb?.usageTimeMillis ?: 0L
                            val yesterdayU = yesterdayWeb?.usageTimeMillis ?: 0L

                            val hourlyEntities = withContext(Dispatchers.IO) { shieldRepository.getHourlyUsageForDateSync(todayStr) }
                            val hourlyU = MutableList(24) { 0L }
                            hourlyEntities.filter { it.packageName == packageName }.forEach { hourlyU[it.hour] = it.usageTimeMillis }
                            val peakH = hourlyU.indices.maxByOrNull { hourlyU[it] } ?: -1

                            val history = (0 until 21).map { i ->
                                val dStart = usageHistoryManager.getMidnight(i)
                                val dStr = dateFormat.format(Date(dStart))
                                val we = websiteHistory.find { it.date == dStr }
                                DailyUsage(dStart, we?.usageTimeMillis ?: 0L, we != null, false, i == 0)
                            }

                            _appDetailUiState.update { it.copy(
                                packageName = packageName,
                                appName = appName,
                                type = shield?.type,
                                todayUsage = todayU,
                                yesterdayUsage = yesterdayU,
                                averageUsage = if (history.any { it.totalTime > 0 }) history.filter { it.totalTime > 0 }.map { it.totalTime }.average().toLong() else 0L,
                                totalSessions = 0,
                                peakHour = peakH,
                                percentageChange = if (yesterdayU > 0) (todayU - yesterdayU).toFloat() / yesterdayU * 100 else if (todayU > 0) 100f else 0f,
                                usageHistory = history.reversed(),
                                hourlyUsage = hourlyU,
                                currentStreak = shield?.currentStreak ?: 0,
                                bestStreak = shield?.bestStreak ?: 0,
                                shieldEntity = shield,
                                isPaused = shield?.isPaused ?: false,
                                pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                                sinceLastChargeUsage = 0L,
                                lastResetTimestamp = 0L,
                                batteryStatsResetEnabled = false,
                                isLoading = false
                            ) }
                        } catch (e: Exception) {
                            android.util.Log.e("HomeVM", "Error in website detail combine for $packageName: ${e.message}")
                            _appDetailUiState.update { it.copy(isLoading = false) }
                        }
                    }.catch { e ->
                        android.util.Log.e("HomeVM", "Website detail flow failed for $packageName: ${e.message}")
                        _appDetailUiState.update { it.copy(isLoading = false) }
                    }.collect()
                } else {
                    detailFallbackMap = withContext(Dispatchers.IO) { if (detailFallbackMap.isEmpty() || forceRefresh || isNew) usageHistoryManager.updatePackageFallback(packageName) else detailFallbackMap }
                    combine(shieldRepository.getLastNDaysUsageForPackage(packageName, 21), shieldRepository.getShieldByPackageNameFlow(packageName), userPreferencesRepository.userPreferencesFlow) { historyDB, shield, prefs ->
                        try {
                            val detailed = withContext(Dispatchers.IO) { kotlinx.coroutines.withTimeoutOrNull(5000) { ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute) } }
                            val todayU = detailed?.appUsageMap?.get(packageName) ?: 0L; val sessions = detailed?.sessionCounts?.get(packageName) ?: 0
                            val hourlyU = MutableList(24) { detailed?.hourlyUsageMap?.get(it)?.get(packageName) ?: 0L }; val peakH = hourlyU.indices.maxByOrNull { hourlyU[it] } ?: -1
                            val yesterdayStr = dateFormat.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
                            val yesterdayU = historyDB.find { it.date == yesterdayStr }?.usageTimeMillis ?: if (prefs.preferSystemUsageHistory) detailFallbackMap[yesterdayStr] ?: 0L else 0L
                            val history = (0 until 21).map { i -> val dStart = usageHistoryManager.getMidnight(i); val dStr = dateFormat.format(Date(dStart)); val dbE = historyDB.find { it.date == dStr }; val dTotal = if (i == 0) todayU else dbE?.usageTimeMillis ?: if (prefs.preferSystemUsageHistory) detailFallbackMap[dStr] ?: 0L else 0L; DailyUsage(dStart, dTotal, dbE != null, detailFallbackMap[dStr] != null, i == 0) }

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
                }
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
        viewModelScope.launch {
            val updated = shieldOperationsManager.pauseShield(shield, durationHours)
            _appDetailUiState.update { it.copy(shieldEntity = updated, isPaused = true, pauseEndTimestamp = updated.pauseEndTimestamp) }
        }
    }

    fun resumeShield() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            val updated = shieldOperationsManager.resumeShield(shield)
            _appDetailUiState.update { it.copy(shieldEntity = updated, isPaused = false, pauseEndTimestamp = 0L) }
        }
    }

    fun resetAppUsage(packageName: String) {
        shieldOperationsManager.resetAppUsage(packageName, userPreferencesRepository, viewModelScope)
    }

    fun setBatteryStatsResetEnabled(enabled: Boolean) {
        shieldOperationsManager.setBatteryStatsResetEnabled(enabled, userPreferencesRepository, viewModelScope)
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
                        if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
                            false
                        } else {
                            try {
                                pm.getApplicationInfo(pkg, 0)
                                false
                            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                                true
                            }
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
                        appInfoCache.clear(); usageHistoryManager.clearGlobalFallback(); detailFallbackMap = emptyMap()
                        val today = usageHistoryManager.getMidnight(0); val yesterday = usageHistoryManager.getMidnight(1)
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
                        val checkTodayStart = usageHistoryManager.getMidnight(0)
                        val checkTimeSinceMidnight = now2 - checkTodayStart
                        if (_uiState.value.totalScreenTime == 0L && checkTimeSinceMidnight > 300_000L) {
                            android.util.Log.w("HomeVM", "Sanity: totalScreenTime=0, scheduling repair")
                            val todayStr = usageHistoryManager.getDateFormat().format(Date(checkTodayStart))
                            viewModelScope.launch {
                                delay(100)
                                refreshMutex.withLock {
                                    usageHistoryManager.repairDataInternal(todayStr, RepairMode.SYSTEM)
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

        if (com.etrisad.zenith.data.website.WebsiteRepository.isWebsitePackageName(pkg)) {
            val domain = com.etrisad.zenith.data.website.WebsiteRepository.extractDomainFromPackageName(pkg)
            val dateFormat = usageHistoryManager.getDateFormat()
            val todayStr = dateFormat.format(Date())
            val websiteUsage = withContext(Dispatchers.IO) {
                shieldRepository.getWebsiteUsage(todayStr, domain)
            }
            val currentTodayUsage = websiteUsage?.usageTimeMillis ?: 0L

            val hourlyEntities = withContext(Dispatchers.IO) {
                shieldRepository.getHourlyUsageForDateSync(todayStr)
            }
            val appHourlyUsage = MutableList(24) { 0L }
            hourlyEntities.filter { it.packageName == pkg }.forEach { appHourlyUsage[it.hour] = it.usageTimeMillis }
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
                totalSessions = 0,
                hourlyUsage = appHourlyUsage,
                peakHour = peakHour
            ) }
        } else {
            val usm = this@HomeViewModel.context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val detailedUsage = withContext(Dispatchers.IO) {
                ScreenUsageHelper.fetchDetailedUsageToday(usm, includeHourly = true, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
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
        shieldOperationsManager.saveFocus(
            packageName, appName, timeLimitMinutes, maxEmergencyUses, isRemindersEnabled,
            isStrictModeEnabled, isAutoQuitEnabled, maxUsesPerPeriod, refreshPeriodMinutes,
            goalReminderPeriodMinutes, isDelayAppEnabled, isGoalCallerEnabled, isGoalCallerSoundEnabled,
            goalCallerSoundUri, limitPeriod, type, viewModelScope, ::closeSettingsSheet
        )
    }

    fun deleteShieldFromDetail() {
        val shield = _appDetailUiState.value.shieldEntity ?: return
        viewModelScope.launch {
            shieldOperationsManager.deleteShieldFromDetail(shield)
            _appDetailUiState.update { it.copy(type = null, shieldEntity = null) }
        }
    }

    fun deleteShield(shield: ShieldEntity) {
        viewModelScope.launch {
            shieldOperationsManager.deleteShield(shield)
        }
    }

    fun formatDuration(millis: Long): String = shieldOperationsManager.formatDuration(millis)

    fun onVisibleWeekChanged(pageIndex: Int) {
        viewModelScope.launch {
            val history = _uiState.value.dailyUsageHistory; if (history.isEmpty()) return@launch
            val pages = history.chunked(7); if (pageIndex !in pages.indices) return@launch
            val weekDays = pages[pageIndex]; val avg = if (weekDays.isNotEmpty()) weekDays.map { it.totalTime }.average().toLong() else 0L
            val dateFormat = usageHistoryManager.getDateFormat(); val appUsageMap = mutableMapOf<String, Long>()
            val preferSystem = userPreferencesRepository.userPreferencesFlow.first().preferSystemUsageHistory
            weekDays.forEach { day ->
                val dateStr = dateFormat.format(Date(day.date))
                allHistory.filter { it.date == dateStr }.forEach { if (it.packageName !in setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL")) appUsageMap[it.packageName] = (appUsageMap[it.packageName] ?: 0L) + it.usageTimeMillis }
                if (preferSystem) usageHistoryManager.globalFallbackMap.value[dateStr]?.forEach { if (it.packageName != "TOTAL") {
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

}