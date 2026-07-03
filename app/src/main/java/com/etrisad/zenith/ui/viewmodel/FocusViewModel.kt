package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.website.TldSuggestions
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PickerTab { APPS, WEBSITES }

data class AppInfo(
    val packageName: String,
    val appName: String
)

data class FocusUiState(
    val activeShields: List<ShieldEntity> = emptyList(),
    val activeGoals: List<ShieldEntity> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val selectedAppForFocus: AppInfo? = null,
    val selectedFocusType: FocusType = FocusType.SHIELD,
    val isSettingsSheetOpen: Boolean = false,
    val topApps: List<AppInfo> = emptyList(),
    val shieldSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val goalSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val selectedAppUsageToday: Long = 0L,
    val activeSchedules: List<ScheduleEntity> = emptyList(),
    val isSchedulePickerOpen: Boolean = false,
    val selectedAppsForSchedule: Set<String> = emptySet(),
    val isScheduleSettingsOpen: Boolean = false,
    val editingSchedule: ScheduleEntity? = null,
    val isSelectionMode: Boolean = false,
    val selectedShields: Set<String> = emptySet(),
    val selectedSchedules: Set<Long> = emptySet(),
    val incentiveLockEnabled: Boolean = false,
    val incentiveLockGoalsMetToday: Boolean = false,
    val incentiveProgress: Float = 0f,
    val incentiveTier: IncentiveTier = IncentiveTier.UNLOCKED,
    val bonusUsesLeft: Int = 0,
    val uninstalledShields: Set<String> = emptySet(),
    val pickerTab: PickerTab = PickerTab.APPS,
    val websiteSearchQuery: String = "",
    val websiteSuggestions: List<String> = emptyList(),
    val websiteSuggestionsLoading: Boolean = false,
    val selectedWebsiteUrl: String? = null
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class FocusViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private var allShields: List<ShieldEntity> = emptyList()
    private var loadAppsJob: kotlinx.coroutines.Job? = null
    private var websiteValidationJob: kotlinx.coroutines.Job? = null
    private var dismissedUninstalledApps: Map<String, String> = emptyMap()

    init {
        viewModelScope.launch {
            shieldRepository.allShields
                .debounce(300)
                .flowOn(Dispatchers.Default)
                .collect { shields ->
                    try {
                        allShields = shields
                        updateShieldedLists(shields)
                        updateInstalledAppsFilter()
                    } catch (e: Exception) {
                        android.util.Log.e("FocusViewModel", "Error in allShields collector: ${e.message}")
                    }
                }
        }
        viewModelScope.launch {
            shieldRepository.allSchedules
                .flowOn(Dispatchers.Default)
                .collect { schedules ->
                    _uiState.update { it.copy(activeSchedules = schedules) }
                }
        }
        viewModelScope.launch {
            var lastWhitelist: Set<String>? = null
            preferencesRepository.userPreferencesFlow
                .flowOn(Dispatchers.Default)
                .collect { prefs ->
                    dismissedUninstalledApps = prefs.dismissedUninstalledApps
                    _uiState.update { it.copy(
                        incentiveLockEnabled = prefs.incentiveLockEnabled,
                        incentiveLockGoalsMetToday = prefs.incentiveLockGoalsMetToday
                    ) }
                    if (lastWhitelist == null || lastWhitelist != prefs.whitelistedPackages) {
                        lastWhitelist = prefs.whitelistedPackages
                        loadInstalledApps()
                    }
                }
        }
        viewModelScope.launch {
            shieldRepository.getIncentiveGoalProgress()
                .flowOn(Dispatchers.Default)
                .collect { progress ->
                    val tier = IncentiveTier.fromProgress(progress)
                    val bonusLeft = if (tier.bonusUses < Int.MAX_VALUE) {
                        shieldRepository.getIncentiveBonusUsesLeft()
                    } else 0
                    _uiState.update { it.copy(
                        incentiveProgress = progress,
                        incentiveTier = tier,
                        bonusUsesLeft = bonusLeft
                    ) }
                }
        }
        startRealTimeUpdates()
    }

    fun onShieldSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(shieldSortType = sortType)
        updateShieldedLists(allShields)
    }

    fun onGoalSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(goalSortType = sortType)
        updateShieldedLists(allShields)
    }

    private var updateShieldedJob: kotlinx.coroutines.Job? = null

    private fun updateShieldedLists(latestShields: List<ShieldEntity>) {
        updateShieldedJob?.cancel()
        updateShieldedJob = viewModelScope.launch {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager

                val accurateUsageMap = withContext(Dispatchers.IO) {
                    try {
                        com.etrisad.zenith.util.ScreenUsageHelper.fetchAppUsageTodayTillNow(usm, dayStartHour = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartHour, dayStartMinute = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartMinute)
                    } catch (e: Exception) {
                        android.util.Log.e("FocusViewModel", "Error fetching usage stats: ${e.message}")
                        emptyMap<String, Long>()
                    }
                }

                val liveShields = latestShields.map { shield ->
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

                val shields = liveShields.filter { it.type == FocusType.SHIELD }
                val goals = liveShields.filter { it.type == FocusType.GOAL }

                _uiState.update { currentState ->
                    currentState.copy(
                        activeShields = sortShields(shields, currentState.shieldSortType),
                        activeGoals = sortShields(goals, currentState.goalSortType)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("FocusViewModel", "Critical error in updateShieldedLists: ${e.message}")
                val shields = latestShields.filter { it.type == FocusType.SHIELD }
                val goals = latestShields.filter { it.type == FocusType.GOAL }
                _uiState.update { currentState ->
                    currentState.copy(
                        activeShields = sortShields(shields, currentState.shieldSortType),
                        activeGoals = sortShields(goals, currentState.goalSortType)
                    )
                }
            }
        }
    }

    private var isActive = true

    fun setActive(active: Boolean) {
        isActive = active
    }

    private fun startRealTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                if (!isActive) {
                    delay(5000)
                    continue
                }
                updateShieldedLists(allShields)
                delay(120000)
            }
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

    private fun loadInstalledApps() {
        loadAppsJob?.cancel()
        loadAppsJob = viewModelScope.launch {
            if (_allInstalledApps.value.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingApps = true)
            }

            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val installedApps = try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getInstalledApplications(0)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FocusViewModel", "Failed to get installed applications", e)
                        emptyList()
                    }

                    val whitelist = try {
                        preferencesRepository.userPreferencesFlow.first().whitelistedPackages
                    } catch (e: Exception) {
                        emptySet()
                    }

                    installedApps
                        .filter { app ->
                            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            !isSystem || app.packageName !in whitelist
                        }
                        .filter { it.packageName != context.packageName }
                        .map {
                            AppInfo(
                                packageName = it.packageName,
                                appName = pm.getApplicationLabel(it).toString()
                            )
                        }
                        .sortedBy { it.appName.lowercase() }
                }
                _allInstalledApps.value = apps
                updateInstalledAppsFilter()
            } catch (e: Exception) {
                android.util.Log.e("FocusViewModel", "Error loading apps: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingApps = false)
            }
        }
    }

    private var filterJob: kotlinx.coroutines.Job? = null
    private fun updateInstalledAppsFilter() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val query = _uiState.value.searchQuery

            val filtered = withContext(Dispatchers.Default) {
                if (query.isBlank()) {
                    _allInstalledApps.value
                } else {
                    _allInstalledApps.value.filter {
                        it.appName.contains(query, ignoreCase = true) ||
                                it.packageName.contains(query, ignoreCase = true)
                    }
                }
            }

            val topApps = withContext(Dispatchers.IO) {
                getTopUsedApps(limit = 6)
            }

            _uiState.update { currentState ->
                currentState.copy(
                    installedApps = filtered,
                    topApps = topApps,
                    isLoadingApps = false
                )
            }
            updateUninstalledShields()
        }
    }

    private fun updateUninstalledShields() {
        if (_allInstalledApps.value.isEmpty()) return
        val installedPkgs = _allInstalledApps.value.map { it.packageName }.toSet()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val uninstalled = allShields
            .map { it.packageName }
            .filter { pkg -> pkg !in installedPkgs }
            .filter { pkg -> !WebsiteRepository.isWebsitePackageName(pkg) }
            .filter { pkg -> dismissedUninstalledApps[pkg] != todayStr }
            .toSet()
        _uiState.update { it.copy(uninstalledShields = uninstalled) }
    }

    private suspend fun getTopUsedApps(limit: Int): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val accurateUsageMap = com.etrisad.zenith.util.ScreenUsageHelper.fetchAppUsageTodayTillNow(usm, dayStartHour = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartHour, dayStartMinute = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartMinute)
            accurateUsageMap.entries
                .sortedByDescending { it.value }
                .mapNotNull { (pkg, _) ->
                    _allInstalledApps.value.find { it.packageName == pkg }
                }
                .take(limit)
        } catch (e: Exception) {
            android.util.Log.e("FocusViewModel", "Error fetching top apps: ${e.message}")
            emptyList()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        updateInstalledAppsFilter()
    }

    fun setPickerTab(tab: PickerTab) {
        websiteValidationJob?.cancel()
        _uiState.value = _uiState.value.copy(pickerTab = tab, websiteSearchQuery = "", websiteSuggestions = emptyList(), websiteSuggestionsLoading = false)
    }

    fun onWebsiteSearchQueryChange(query: String) {
        websiteValidationJob?.cancel()
        val suggestions = if (query.isBlank()) emptyList() else TldSuggestions.suggest(query)
        _uiState.value = _uiState.value.copy(
            websiteSearchQuery = query,
            websiteSuggestions = suggestions,
            websiteSuggestionsLoading = query.isNotBlank()
        )
        if (query.isBlank()) return
        websiteValidationJob = viewModelScope.launch {
            delay(3000)
            if (com.etrisad.zenith.BuildConfig.SHOW_UPDATES) {
                withContext(Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                    val deferreds = suggestions.map { domain ->
                        async {
                            val ok = try {
                                val request = okhttp3.Request.Builder().url("https://$domain").build()
                                client.newCall(request).execute().use { it.isSuccessful }
                            } catch (_: Exception) {
                                false
                            }
                            Pair(domain, ok)
                        }
                    }
                    val reachable = deferreds.awaitAll().filter { it.second }.map { it.first }
                    _uiState.value = _uiState.value.copy(
                        websiteSuggestions = if (reachable.isNotEmpty()) reachable else suggestions,
                        websiteSuggestionsLoading = false
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(websiteSuggestionsLoading = false)
            }
        }
    }

    fun confirmWebsite(domain: String) {
        websiteValidationJob?.cancel()
        val fullUrl = WebsiteRepository.normalizeUrl(domain)
        val domainOnly = WebsiteRepository.extractDomain(domain)
        val displayName = WebsiteRepository.getDisplayName(domainOnly, fullUrl)
        val packageName = WebsiteRepository.createPackageName(domainOnly)
        val app = AppInfo(packageName = packageName, appName = displayName)
        _uiState.value = _uiState.value.copy(
            websiteSearchQuery = "",
            websiteSuggestions = emptyList(),
            websiteSuggestionsLoading = false,
            selectedWebsiteUrl = fullUrl
        )
        selectAppForFocus(app, _uiState.value.selectedFocusType)
    }

    fun selectAppForFocus(app: AppInfo?, type: FocusType) {
        viewModelScope.launch {
            val usage = if (app != null) {
                withContext(Dispatchers.IO) {
                    getUsageTodayForPackage(app.packageName)
                }
            } else 0L

            _uiState.update {
                it.copy(
                    selectedAppForFocus = app,
                    selectedFocusType = type,
                    isSettingsSheetOpen = app != null,
                    selectedAppUsageToday = usage,
                    isSchedulePickerOpen = false,
                    isScheduleSettingsOpen = false
                )
            }
        }
    }

    fun openSchedulePicker(resetSelection: Boolean = true) {
        _uiState.value = _uiState.value.copy(
            isSchedulePickerOpen = true,
            selectedAppsForSchedule = if (resetSelection) emptySet() else _uiState.value.selectedAppsForSchedule,
            isSettingsSheetOpen = false,
            editingSchedule = if (resetSelection) null else _uiState.value.editingSchedule
        )
    }

    fun confirmWebsiteForSchedule(domain: String) {
        val domainOnly = WebsiteRepository.extractDomain(domain)
        val packageName = WebsiteRepository.createPackageName(domainOnly)
        toggleAppSelectionForSchedule(packageName)
    }

    fun toggleAppSelectionForSchedule(packageName: String) {
        val current = _uiState.value.selectedAppsForSchedule
        val newSelection = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
        _uiState.value = _uiState.value.copy(selectedAppsForSchedule = newSelection)
    }

    fun proceedToScheduleSettings() {
        if (_uiState.value.selectedAppsForSchedule.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            isSchedulePickerOpen = false,
            isScheduleSettingsOpen = true
        )
    }

    fun closeSchedulePicker() {
        _uiState.value = _uiState.value.copy(isSchedulePickerOpen = false, pickerTab = PickerTab.APPS, websiteSearchQuery = "", websiteSuggestions = emptyList(), websiteSuggestionsLoading = false)
    }

    fun closeScheduleSettings() {
        _uiState.value = _uiState.value.copy(isScheduleSettingsOpen = false, editingSchedule = null)
    }

    fun editSchedule(schedule: ScheduleEntity) {
        _uiState.value = _uiState.value.copy(
            isScheduleSettingsOpen = true,
            editingSchedule = schedule,
            selectedAppsForSchedule = schedule.packageNames.toSet(),
            isSchedulePickerOpen = false,
            isSettingsSheetOpen = false
        )
    }

    fun saveSchedule(
        name: String,
        startTime: String,
        endTime: String,
        mode: ScheduleMode,
        maxEmergencyUses: Int = 3,
        interceptNotifications: Boolean = false
    ) {
        val packageNames = _uiState.value.selectedAppsForSchedule.toList()
        if (packageNames.isEmpty()) return

        viewModelScope.launch {
            val editing = _uiState.value.editingSchedule
            val schedule = if (editing != null) {
                editing.copy(
                    name = name,
                    packageNames = packageNames,
                    startTime = startTime,
                    endTime = endTime,
                    mode = mode,
                    interceptNotifications = interceptNotifications,
                    emergencyUseCount = editing.emergencyUseCount,
                    maxEmergencyUses = maxEmergencyUses
                )
            } else {
                ScheduleEntity(
                    name = name,
                    packageNames = packageNames,
                    startTime = startTime,
                    endTime = endTime,
                    mode = mode,
                    interceptNotifications = interceptNotifications,
                    emergencyUseCount = maxEmergencyUses,
                    maxEmergencyUses = maxEmergencyUses
                )
            }

            if (editing != null) {
                shieldRepository.updateSchedule(schedule)
            } else {
                shieldRepository.insertSchedule(schedule)
            }
            closeScheduleSettings()
        }
    }

    fun deleteSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            shieldRepository.deleteSchedule(schedule)
        }
    }

    private fun getUsageTodayForPackage(packageName: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val accurateUsageMap = com.etrisad.zenith.util.ScreenUsageHelper.fetchAppUsageTodayTillNow(usm, dayStartHour = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartHour, dayStartMinute = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartMinute)
        return accurateUsageMap[packageName] ?: 0L
    }

    fun closeSettingsSheet() {
        _uiState.value = _uiState.value.copy(
            isSettingsSheetOpen = false,
            selectedAppForFocus = null,
            selectedWebsiteUrl = null
        )
    }

    fun saveFocus(
        packageName: String,
        appName: String,
        timeLimitMinutes: Int,
        maxEmergencyUses: Int = 3,
        isRemindersEnabled: Boolean = true,
        isStrictModeEnabled: Boolean = false,
        isAutoQuitEnabled: Boolean = false,
        maxUsesPerPeriod: Int = 5,
        refreshPeriodMinutes: Int = 60,
        goalReminderPeriodMinutes: Int = 0,
        isDelayAppEnabled: Boolean = false,
        isGoalCallerEnabled: Boolean = false,
        isGoalCallerSoundEnabled: Boolean = true,
        goalCallerSoundUri: String? = null,
        limitPeriod: LimitPeriod = LimitPeriod.DAILY,
        url: String? = null
    ) {
        val type = _uiState.value.selectedFocusType
        val isWebsite = WebsiteRepository.isWebsitePackageName(packageName)
        val websiteUrl = if (isWebsite) (url ?: _uiState.value.selectedWebsiteUrl) else null
        viewModelScope.launch {
            try {
                val existing = allShields.find { it.packageName == packageName }

                val shouldResetStreak = existing?.let {
                    if (it.type == type) {
                        when (type) {
                            FocusType.SHIELD -> timeLimitMinutes > it.timeLimitMinutes
                            FocusType.GOAL -> timeLimitMinutes < it.timeLimitMinutes
                        }
                    } else true
                } ?: false

                val periodChanged = existing != null && existing.limitPeriod != limitPeriod
                val shield = ShieldEntity(
                    packageName = packageName,
                    appName = appName,
                    type = type,
                    timeLimitMinutes = timeLimitMinutes,
                    limitPeriod = limitPeriod,
                    emergencyUseCount = existing?.emergencyUseCount ?: (if (type == FocusType.SHIELD) maxEmergencyUses else 0),
                    maxEmergencyUses = if (type == FocusType.SHIELD) maxEmergencyUses else 0,
                    isRemindersEnabled = isRemindersEnabled,
                    isStrictModeEnabled = if (type == FocusType.SHIELD) isStrictModeEnabled else false,
                    isAutoQuitEnabled = if (type == FocusType.SHIELD) isAutoQuitEnabled else false,
                    remainingTimeMillis = if (periodChanged) timeLimitMinutes * 60 * 1000L else (existing?.remainingTimeMillis ?: (timeLimitMinutes * 60 * 1000L)),
                    lastUsedTimestamp = System.currentTimeMillis(),
                    maxUsesPerPeriod = if (type == FocusType.SHIELD) maxUsesPerPeriod else 0,
                    refreshPeriodMinutes = if (type == FocusType.SHIELD) refreshPeriodMinutes else 0,
                    currentPeriodUses = existing?.currentPeriodUses ?: 0,
                    lastPeriodResetTimestamp = existing?.lastPeriodResetTimestamp ?: System.currentTimeMillis(),
                    lastEmergencyRechargeTimestamp = existing?.lastEmergencyRechargeTimestamp ?: System.currentTimeMillis(),
                    goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                    lastGoalReminderTimestamp = existing?.lastGoalReminderTimestamp ?: 0L,
                    isDelayAppEnabled = if (type == FocusType.SHIELD) isDelayAppEnabled else false,
                    isGoalCallerEnabled = isGoalCallerEnabled,
                    isGoalCallerSoundEnabled = isGoalCallerSoundEnabled,
                    goalCallerSoundUri = goalCallerSoundUri,
                    currentStreak = if (shouldResetStreak) 0 else (existing?.currentStreak ?: 0),
                    bestStreak = existing?.bestStreak ?: 0,
                    lastStreakUpdateTimestamp = existing?.lastStreakUpdateTimestamp ?: 0L,
                    lastSessionEndTimestamp = existing?.lastSessionEndTimestamp ?: 0L,
                    isPaused = existing?.isPaused ?: false,
                    pauseEndTimestamp = existing?.pauseEndTimestamp ?: 0L,
                    lastDelayStartTimestamp = existing?.lastDelayStartTimestamp ?: 0L,
                    timeAdded = existing?.timeAdded?.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    isWebsite = isWebsite,
                    url = websiteUrl
                )
                shieldRepository.insertShield(shield); com.etrisad.zenith.service.SharedMonitoringState.notifiedGoals.remove(packageName)
            } catch (e: Exception) {
                android.util.Log.e("FocusViewModel", "Error saving focus: ${e.message}")
            } finally {
                closeSettingsSheet()
            }
        }
    }

    fun deleteShield(shield: ShieldEntity) {
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
        }
    }

    fun editShield(shield: ShieldEntity) {
        viewModelScope.launch {
            val appInfo = AppInfo(shield.packageName, shield.appName)
            val usage = withContext(Dispatchers.IO) {
                getUsageTodayForPackage(shield.packageName)
            }
            _uiState.update {
                it.copy(
                    selectedAppForFocus = appInfo,
                    selectedFocusType = shield.type,
                    isSettingsSheetOpen = true,
                    selectedAppUsageToday = usage
                )
            }
        }
    }

    fun toggleSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = !_uiState.value.isSelectionMode,
            selectedShields = emptySet(),
            selectedSchedules = emptySet()
        )
    }

    fun toggleShieldSelection(packageName: String) {
        val current = _uiState.value.selectedShields
        val newSelection = if (packageName in current) {
            current - packageName
        } else {
            current + packageName
        }
        val isSelectionStillActive = newSelection.isNotEmpty() || _uiState.value.selectedSchedules.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedShields = newSelection,
            isSelectionMode = if (_uiState.value.isSelectionMode) isSelectionStillActive else _uiState.value.isSelectionMode
        )
    }

    fun toggleScheduleSelection(id: Long) {
        val current = _uiState.value.selectedSchedules
        val newSelection = if (id in current) {
            current - id
        } else {
            current + id
        }
        val isSelectionStillActive = _uiState.value.selectedShields.isNotEmpty() || newSelection.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedSchedules = newSelection,
            isSelectionMode = if (_uiState.value.isSelectionMode) isSelectionStillActive else _uiState.value.isSelectionMode
        )
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val shieldsToDelete = _uiState.value.selectedShields
            val schedulesToDelete = _uiState.value.selectedSchedules
            shieldsToDelete.forEach { pkg ->
                allShields.find { it.packageName == pkg }?.let {
                    shieldRepository.deleteShield(it)
                }
            }
            schedulesToDelete.forEach { id ->
                _uiState.value.activeSchedules.find { it.id == id }?.let {
                    shieldRepository.deleteSchedule(it)
                }
            }
            toggleSelectionMode()
        }
    }

    fun pauseSelected(durationMinutes: Int) {
        viewModelScope.launch {
            val shieldsToPause = _uiState.value.selectedShields
            val pauseEndTimestamp = if (durationMinutes == -1) 0L else System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
            shieldsToPause.forEach { pkg ->
                allShields.find { it.packageName == pkg }?.let { shield ->
                    shieldRepository.updateShield(shield.copy(
                        isPaused = true,
                        pauseEndTimestamp = pauseEndTimestamp
                    ))
                }
            }
            toggleSelectionMode()
        }
    }
}