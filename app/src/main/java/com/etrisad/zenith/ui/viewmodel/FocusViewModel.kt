package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.local.entity.FocusType
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
    val shieldSortType: ShieldSortType = ShieldSortType.ALPHABETICAL,
    val goalSortType: ShieldSortType = ShieldSortType.ALPHABETICAL
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
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        val filtered = if (query.isBlank()) {
            _allInstalledApps.value
        } else {
            _allInstalledApps.value.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(installedApps = filtered)
    }

    fun selectAppForFocus(app: AppInfo?, type: FocusType) {
        _uiState.value = _uiState.value.copy(
            selectedAppForFocus = app,
            selectedFocusType = type,
            isSettingsSheetOpen = app != null // Only open settings if app is selected
        )
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
        goalReminderPeriodMinutes: Int = 0
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
                goalReminderPeriodMinutes = goalReminderPeriodMinutes
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
            _uiState.value = _uiState.value.copy(
                selectedAppForFocus = appInfo,
                selectedFocusType = shield.type,
                isSettingsSheetOpen = true
            )
        }
    }
}
