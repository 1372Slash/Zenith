package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.SharedMonitoringState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeepFocusUiState(
    val isSessionActive: Boolean = false,
    val isBreakActive: Boolean = false,
    val sessionEndTimestamp: Long = 0L,
    val breakEndTimestamp: Long = 0L,
    val allowedPackages: Set<String> = emptySet(),
    val blockAllowedApps: Boolean = true,
    val breakDurationMinutes: Int = 15,
    val sessionDurationMinutes: Int = 60,
    val maxSelection: Int = 7,
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val remainingBreakMillis: Long = 0L,
    val remainingSessionMillis: Long = 0L
)

class DeepFocusViewModel(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeepFocusUiState())
    val uiState: StateFlow<DeepFocusUiState> = _uiState.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
                val isActive = prefs.deepFocusEnabled && prefs.deepFocusSessionEndTimestamp > now
                val isBreak = isActive && prefs.deepFocusBreakEndTimestamp > now
                _uiState.update {
                    it.copy(
                        isSessionActive = isActive,
                        isBreakActive = isBreak,
                        sessionEndTimestamp = prefs.deepFocusSessionEndTimestamp,
                        breakEndTimestamp = prefs.deepFocusBreakEndTimestamp,
                        allowedPackages = prefs.deepFocusAllowedPackages,
                        blockAllowedApps = prefs.deepFocusBlockAllowedApps,
                        breakDurationMinutes = prefs.deepFocusBreakDurationMinutes,
                        sessionDurationMinutes = prefs.deepFocusSessionDurationMinutes,
                        maxSelection = prefs.deepFocusMaxAllowedApps,
                        remainingBreakMillis = (prefs.deepFocusBreakEndTimestamp - now).coerceAtLeast(0L),
                        remainingSessionMillis = (prefs.deepFocusSessionEndTimestamp - now).coerceAtLeast(0L)
                    )
                }
                SharedMonitoringState.isDeepFocusActive = isActive
                SharedMonitoringState.isDeepFocusBlockingActive = isActive
                SharedMonitoringState.isDeepFocusBreakActive = isBreak
                SharedMonitoringState.deepFocusAllowedPackages = prefs.deepFocusAllowedPackages
                SharedMonitoringState.deepFocusBlockAllowedApps = prefs.deepFocusBlockAllowedApps
                loadInstalledApps()
            }
        }
        startTimer()
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val now = System.currentTimeMillis()
                val prefs = userPreferencesRepository.userPreferencesFlow.first()
                val isActive = prefs.deepFocusEnabled && prefs.deepFocusSessionEndTimestamp > now
                val isBreak = isActive && prefs.deepFocusBreakEndTimestamp > now
                _uiState.update {
                    it.copy(
                        remainingBreakMillis = (prefs.deepFocusBreakEndTimestamp - now).coerceAtLeast(0L),
                        remainingSessionMillis = (prefs.deepFocusSessionEndTimestamp - now).coerceAtLeast(0L),
                        isSessionActive = isActive,
                        isBreakActive = isBreak
                    )
                }
                if (isActive != SharedMonitoringState.isDeepFocusActive || isBreak != SharedMonitoringState.isDeepFocusBreakActive) {
                    SharedMonitoringState.isDeepFocusActive = isActive
                    SharedMonitoringState.isDeepFocusBlockingActive = isActive
                    SharedMonitoringState.isDeepFocusBreakActive = isBreak
                }
            }
        }
    }

    fun startSession() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val sessionEnd = System.currentTimeMillis() + (prefs.deepFocusSessionDurationMinutes * 60 * 1000L)
            userPreferencesRepository.setDeepFocusEnabled(true)
            userPreferencesRepository.setDeepFocusSessionEndTimestamp(sessionEnd)
            SharedMonitoringState.isDeepFocusActive = true
            SharedMonitoringState.isDeepFocusBlockingActive = true
            SharedMonitoringState.isDeepFocusBreakActive = false
            SharedMonitoringState.deepFocusAllowedPackages = prefs.deepFocusAllowedPackages
            SharedMonitoringState.deepFocusBlockAllowedApps = prefs.deepFocusBlockAllowedApps
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            val breakEnd = System.currentTimeMillis() + (prefs.deepFocusBreakDurationMinutes * 60 * 1000L)
            userPreferencesRepository.setDeepFocusBreakEndTimestamp(breakEnd)
            SharedMonitoringState.isDeepFocusBreakActive = true
        }
    }

    fun endSession() {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusEnabled(false)
            userPreferencesRepository.setDeepFocusSessionEndTimestamp(0L)
            userPreferencesRepository.setDeepFocusBreakEndTimestamp(0L)
            SharedMonitoringState.isDeepFocusActive = false
            SharedMonitoringState.isDeepFocusBlockingActive = false
            SharedMonitoringState.isDeepFocusBreakActive = false
        }
    }

    fun setAllowedPackages(packages: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusAllowedPackages(packages)
        }
    }

    fun setBlockAllowedApps(block: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusBlockAllowedApps(block)
            SharedMonitoringState.deepFocusBlockAllowedApps = block
        }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusBreakDurationMinutes(minutes)
        }
    }

    fun setSessionDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusSessionDurationMinutes(minutes)
        }
    }

    fun setMaxSelection(max: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setDeepFocusMaxAllowedApps(max)
        }
    }

    fun toggleAppSelection(packageName: String) {
        val current = _uiState.value.allowedPackages
        val max = _uiState.value.maxSelection
        val newSelection = if (packageName in current) {
            current - packageName
        } else {
            if (current.size >= max) return
            current + packageName
        }
        setAllowedPackages(newSelection)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            try {
                val query = _uiState.value.searchQuery
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
                        emptyList()
                    }

                    installedApps
                        .filter { app ->
                            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            val hasLauncher = pm.getLaunchIntentForPackage(app.packageName) != null
                            hasLauncher && !isSystem
                        }
                        .filter { it.packageName != context.packageName }
                        .map {
                            AppInfo(
                                packageName = it.packageName,
                                appName = pm.getApplicationLabel(it).toString()
                            )
                        }
                        .sortedBy { it.appName.lowercase() }
                        .filter {
                            query.isBlank() || it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                        }
                }
                _uiState.update { it.copy(installedApps = apps, isLoadingApps = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingApps = false) }
            }
        }
    }
}
