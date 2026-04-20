package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.repository.ShieldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?
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
    val editingSchedule: ScheduleEntity? = null
)

class FocusViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private var allShields: List<ShieldEntity> = emptyList()

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                allShields = shields
                updateShieldedLists()
                updateInstalledAppsFilter()
            }
        }
        viewModelScope.launch {
            shieldRepository.allSchedules.collect { schedules ->
                _uiState.value = _uiState.value.copy(activeSchedules = schedules)
            }
        }
        loadInstalledApps()
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
        val shields = allShields.filter { it.type == FocusType.SHIELD }
        val goals = allShields.filter { it.type == FocusType.GOAL }

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

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                installedApps
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .filter { it.packageName != context.packageName }
                    .map {
                        AppInfo(
                            packageName = it.packageName,
                            appName = pm.getApplicationLabel(it).toString(),
                            icon = pm.getApplicationIcon(it)
                        )
                    }
                    .sortedBy { it.appName.lowercase() }
            }
            _allInstalledApps.value = apps
            updateInstalledAppsFilter()
        }
    }

    private fun updateInstalledAppsFilter() {
        val query = _uiState.value.searchQuery
        val shieldedPackages = allShields.map { it.packageName }.toSet()

        val filtered = if (query.isBlank()) {
            _allInstalledApps.value.filter { it.packageName !in shieldedPackages }
        } else {
            _allInstalledApps.value.filter {
                (it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)) &&
                        it.packageName !in shieldedPackages
            }
        }

        // Get top used apps from system usage stats
        val topApps = getTopUsedApps(limit = 6).filter { it.packageName !in shieldedPackages }

        _uiState.value = _uiState.value.copy(
            installedApps = filtered,
            topApps = topApps,
            isLoadingApps = false
        )
    }

    private fun getTopUsedApps(limit: Int): List<AppInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L // Last 24 hours

        val stats = usm.queryAndAggregateUsageStats(start, now)
        return stats.values
            .sortedByDescending { it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground) }
            .mapNotNull { stat ->
                _allInstalledApps.value.find { it.packageName == stat.packageName }
            }
            .take(limit)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        updateInstalledAppsFilter()
    }

    fun selectAppForFocus(app: AppInfo?, type: FocusType) {
        val usage = if (app != null) {
            getUsageTodayForPackage(app.packageName)
        } else 0L

        _uiState.value = _uiState.value.copy(
            selectedAppForFocus = app,
            selectedFocusType = type,
            isSettingsSheetOpen = app != null,
            selectedAppUsageToday = usage,
            isSchedulePickerOpen = false,
            isScheduleSettingsOpen = false
        )
    }

    fun openSchedulePicker() {
        _uiState.value = _uiState.value.copy(
            isSchedulePickerOpen = true,
            selectedAppsForSchedule = emptySet(),
            isSettingsSheetOpen = false,
            isScheduleSettingsOpen = false
        )
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
            isScheduleSettingsOpen = true,
            editingSchedule = null
        )
    }

    fun closeSchedulePicker() {
        _uiState.value = _uiState.value.copy(isSchedulePickerOpen = false)
    }

    fun closeScheduleSettings() {
        _uiState.value = _uiState.value.copy(isScheduleSettingsOpen = false, editingSchedule = null)
    }

    fun saveSchedule(
        name: String,
        startTime: String,
        endTime: String,
        mode: ScheduleMode
    ) {
        val packageNames = _uiState.value.selectedAppsForSchedule.toList()
        if (packageNames.isEmpty()) return

        viewModelScope.launch {
            val schedule = ScheduleEntity(
                name = name,
                packageNames = packageNames,
                startTime = startTime,
                endTime = endTime,
                mode = mode
            )
            shieldRepository.insertSchedule(schedule)
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
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val start = calendar.timeInMillis

        val stats = usm.queryAndAggregateUsageStats(start, now)
        return stats[packageName]?.let {
            it.totalTimeVisible.coerceAtLeast(it.totalTimeInForeground)
        } ?: 0L
    }

    fun closeSettingsSheet() {
        _uiState.value = _uiState.value.copy(
            isSettingsSheetOpen = false,
            selectedAppForFocus = null
        )
    }

    fun saveFocus(
        timeLimitMinutes: Int,
        maxEmergencyUses: Int = 3,
        isRemindersEnabled: Boolean = true,
        isStrictModeEnabled: Boolean = false,
        isAutoQuitEnabled: Boolean = false,
        maxUsesPerPeriod: Int = 5,
        refreshPeriodMinutes: Int = 60,
        goalReminderPeriodMinutes: Int = 0,
        isDelayAppEnabled: Boolean = false
    ) {
        val selectedApp = _uiState.value.selectedAppForFocus ?: return
        val type = _uiState.value.selectedFocusType
        viewModelScope.launch {
            val existing = allShields.find { it.packageName == selectedApp.packageName }
            val shield = ShieldEntity(
                packageName = selectedApp.packageName,
                appName = selectedApp.appName,
                type = type,
                timeLimitMinutes = timeLimitMinutes,
                emergencyUseCount = existing?.emergencyUseCount ?: (if (type == FocusType.SHIELD) maxEmergencyUses else 0),
                maxEmergencyUses = if (type == FocusType.SHIELD) maxEmergencyUses else 0,
                isRemindersEnabled = isRemindersEnabled,
                isStrictModeEnabled = if (type == FocusType.SHIELD) isStrictModeEnabled else false,
                isAutoQuitEnabled = if (type == FocusType.SHIELD) isAutoQuitEnabled else false,
                remainingTimeMillis = existing?.remainingTimeMillis ?: (timeLimitMinutes * 60 * 1000L),
                lastUsedTimestamp = System.currentTimeMillis(),
                maxUsesPerPeriod = if (type == FocusType.SHIELD) maxUsesPerPeriod else 0,
                refreshPeriodMinutes = if (type == FocusType.SHIELD) refreshPeriodMinutes else 0,
                currentPeriodUses = existing?.currentPeriodUses ?: 0,
                lastPeriodResetTimestamp = existing?.lastPeriodResetTimestamp ?: System.currentTimeMillis(),
                lastEmergencyRechargeTimestamp = existing?.lastEmergencyRechargeTimestamp ?: System.currentTimeMillis(),
                goalReminderPeriodMinutes = goalReminderPeriodMinutes,
                isDelayAppEnabled = if (type == FocusType.SHIELD) isDelayAppEnabled else false
            )
            shieldRepository.insertShield(shield)
            closeSettingsSheet()
        }
    }

    fun deleteShield(shield: ShieldEntity) {
        viewModelScope.launch {
            shieldRepository.deleteShield(shield)
        }
    }

    fun editShield(shield: ShieldEntity) {
        viewModelScope.launch {
            val appInfo = try {
                val pm = context.packageManager
                val app = pm.getApplicationInfo(shield.packageName, 0)
                AppInfo(shield.packageName, shield.appName, pm.getApplicationIcon(app))
            } catch (e: Exception) {
                AppInfo(shield.packageName, shield.appName, null)
            }
            val usage = getUsageTodayForPackage(shield.packageName)
            _uiState.value = _uiState.value.copy(
                selectedAppForFocus = appInfo,
                selectedFocusType = shield.type,
                isSettingsSheetOpen = true,
                selectedAppUsageToday = usage
            )
        }
    }
}
