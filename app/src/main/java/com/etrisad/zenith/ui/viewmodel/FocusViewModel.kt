package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val shieldedApps: List<ShieldEntity> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val selectedAppForShield: AppInfo? = null,
    val isSettingsSheetOpen: Boolean = false,
    val sortType: ShieldSortType = ShieldSortType.ALPHABETICAL
)

class FocusViewModel(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())

    init {
        viewModelScope.launch {
            shieldRepository.allShields.collect { shields ->
                _uiState.value = _uiState.value.copy(
                    shieldedApps = sortShields(shields, _uiState.value.sortType)
                )
            }
        }
        loadInstalledApps()
    }

    fun onSortTypeChange(sortType: ShieldSortType) {
        _uiState.value = _uiState.value.copy(
            sortType = sortType,
            shieldedApps = sortShields(_uiState.value.shieldedApps, sortType)
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

    fun selectAppForShield(app: AppInfo) {
        _uiState.value = _uiState.value.copy(
            selectedAppForShield = app,
            isSettingsSheetOpen = true
        )
    }

    fun closeSettingsSheet() {
        _uiState.value = _uiState.value.copy(
            isSettingsSheetOpen = false,
            selectedAppForShield = null
        )
    }

    fun saveShield(
        timeLimitMinutes: Int,
        emergencyUseCount: Int,
        isRemindersEnabled: Boolean,
        isStrictModeEnabled: Boolean,
        isAutoQuitEnabled: Boolean,
        maxUsesPerPeriod: Int = 5,
        refreshPeriodMinutes: Int = 60
    ) {
        val selectedApp = _uiState.value.selectedAppForShield ?: return
        viewModelScope.launch {
            val existing = _uiState.value.shieldedApps.find { it.packageName == selectedApp.packageName }
            val shield = ShieldEntity(
                packageName = selectedApp.packageName,
                appName = selectedApp.appName,
                timeLimitMinutes = timeLimitMinutes,
                emergencyUseCount = emergencyUseCount,
                isRemindersEnabled = isRemindersEnabled,
                isStrictModeEnabled = isStrictModeEnabled,
                isAutoQuitEnabled = isAutoQuitEnabled,
                remainingTimeMillis = existing?.remainingTimeMillis ?: (timeLimitMinutes * 60 * 1000L),
                lastUsedTimestamp = System.currentTimeMillis(),
                maxUsesPerPeriod = maxUsesPerPeriod,
                refreshPeriodMinutes = refreshPeriodMinutes,
                currentPeriodUses = existing?.currentPeriodUses ?: 0,
                lastPeriodResetTimestamp = existing?.lastPeriodResetTimestamp ?: System.currentTimeMillis()
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
                selectedAppForShield = appInfo,
                isSettingsSheetOpen = true
            )
        }
    }
}
