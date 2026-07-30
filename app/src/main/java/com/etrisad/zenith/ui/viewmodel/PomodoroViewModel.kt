package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.SharedMonitoringState
import com.etrisad.zenith.service.ZenithNotificationListener
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class PomodoroUiState(
    val isSessionActive: Boolean = false,
    val isBreakActive: Boolean = false,
    val isPaused: Boolean = false,
    val sessionEndTimestamp: Long = 0L,
    val breakEndTimestamp: Long = 0L,
    val allowedPackages: Set<String> = emptySet(),
    val blockAllowedApps: Boolean = true,
    val sessionDurationMinutes: Int = 25,
    val breakDurationMinutes: Int = 5,
    val longBreakDurationMinutes: Int = 15,
    val sessionCount: Int = 4,
    val sessionsBeforeLongBreak: Int = 4,
    val currentSessionNumber: Int = 1,
    val maxSelection: Int = 7,
    val pauseable: Boolean = true,
    val presetsJson: String = "{}",
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false,
    val remainingBreakMillis: Long = 0L,
    val remainingSessionMillis: Long = 0L
)

class PomodoroViewModel(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PomodoroUiState())
    val uiState: StateFlow<PomodoroUiState> = _uiState.asStateFlow()

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val listStringType = Types.newParameterizedType(List::class.java, String::class.java)
    private val presetsType = Types.newParameterizedType(Map::class.java, String::class.java, listStringType)
    private val presetsAdapter = moshi.adapter<Map<String, List<String>>>(presetsType)

    init {
        viewModelScope.launch {
            userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
                val isActive = prefs.pomodoroEnabled && prefs.pomodoroSessionEndTimestamp > now
                val isBreak = isActive && prefs.pomodoroBreakEndTimestamp > now
                val isPaused = isActive && SharedMonitoringState.isPomodoroPaused
                _uiState.update {
                    it.copy(
                        isSessionActive = isActive,
                        isBreakActive = isBreak,
                        isPaused = isPaused,
                        sessionEndTimestamp = prefs.pomodoroSessionEndTimestamp,
                        breakEndTimestamp = prefs.pomodoroBreakEndTimestamp,
                        allowedPackages = prefs.pomodoroAllowedPackages,
                        blockAllowedApps = prefs.pomodoroBlockAllowedApps,
                        sessionDurationMinutes = prefs.pomodoroSessionDurationMinutes,
                        breakDurationMinutes = prefs.pomodoroBreakDurationMinutes,
                        longBreakDurationMinutes = prefs.pomodoroLongBreakDurationMinutes,
                        sessionCount = prefs.pomodoroSessionCount,
                        sessionsBeforeLongBreak = prefs.pomodoroSessionsBeforeLongBreak,
                        currentSessionNumber = prefs.pomodoroCurrentSessionNumber,
                        maxSelection = prefs.pomodoroMaxAllowedApps,
                        pauseable = prefs.pomodoroPauseable,
                        presetsJson = prefs.pomodoroPresets,
                        remainingBreakMillis = if (isPaused) it.remainingBreakMillis else (prefs.pomodoroBreakEndTimestamp - now).coerceAtLeast(0L),
                        remainingSessionMillis = if (isPaused) it.remainingSessionMillis else (prefs.pomodoroSessionEndTimestamp - now).coerceAtLeast(0L)
                    )
                }
                SharedMonitoringState.isPomodoroActive = isActive
                SharedMonitoringState.isPomodoroBlockingActive = isActive && !isPaused
                SharedMonitoringState.isPomodoroBreakActive = isBreak
                SharedMonitoringState.pomodoroAllowedPackages = prefs.pomodoroAllowedPackages
                SharedMonitoringState.pomodoroBlockAllowedApps = prefs.pomodoroBlockAllowedApps
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
                val state = _uiState.value
                val isActive = state.sessionEndTimestamp > now
                val isBreak = isActive && state.breakEndTimestamp > now
                val isPaused = isActive && SharedMonitoringState.isPomodoroPaused
                val wasActive = state.isSessionActive
                _uiState.update {
                    it.copy(
                        remainingBreakMillis = if (isPaused) it.remainingBreakMillis else (state.breakEndTimestamp - now).coerceAtLeast(0L),
                        remainingSessionMillis = if (isPaused) it.remainingSessionMillis else (state.sessionEndTimestamp - now).coerceAtLeast(0L),
                        isSessionActive = isActive,
                        isBreakActive = isBreak,
                        isPaused = isPaused
                    )
                }
                if (wasActive && !isActive && !isBreak) {
                    onSessionCompleted()
                }
                val newBlockingActive = isActive && !isPaused
                if (isActive != SharedMonitoringState.isPomodoroActive || newBlockingActive != SharedMonitoringState.isPomodoroBlockingActive || isBreak != SharedMonitoringState.isPomodoroBreakActive) {
                    SharedMonitoringState.isPomodoroActive = isActive
                    SharedMonitoringState.isPomodoroBlockingActive = newBlockingActive
                    SharedMonitoringState.isPomodoroBreakActive = isBreak
                }
            }
        }
    }

    private fun onSessionCompleted() {
        viewModelScope.launch {
            val state = _uiState.value
            val nextSession = state.currentSessionNumber + 1
            if (nextSession > state.sessionCount) {
                endSession()
                return@launch
            }
            userPreferencesRepository.setPomodoroCurrentSessionNumber(nextSession)
            val isLongBreak = nextSession % state.sessionsBeforeLongBreak == 0
            val breakDuration = if (isLongBreak) state.longBreakDurationMinutes else state.breakDurationMinutes
            val breakEnd = System.currentTimeMillis() + (breakDuration * 60 * 1000L)
            userPreferencesRepository.setPomodoroBreakEndTimestamp(breakEnd)
            userPreferencesRepository.setPomodoroSessionEndTimestamp(breakEnd + (state.sessionDurationMinutes * 60 * 1000L))
            SharedMonitoringState.isPomodoroActive = true
            SharedMonitoringState.isPomodoroBlockingActive = true
            SharedMonitoringState.isPomodoroBreakActive = true
        }
    }

    fun pauseSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val state = _uiState.value
            if (!state.isSessionActive) return@launch
            SharedMonitoringState.isPomodoroPaused = true
            SharedMonitoringState.pomodoroPauseTimestamp = now
            SharedMonitoringState.isPomodoroBlockingActive = false
        }
    }

    fun resumeSession() {
        viewModelScope.launch {
            val state = _uiState.value
            if (!state.isSessionActive || !state.isPaused) return@launch
            val pauseDuration = System.currentTimeMillis() - SharedMonitoringState.pomodoroPauseTimestamp
            val newSessionEnd = state.sessionEndTimestamp + pauseDuration
            val newBreakEnd = state.breakEndTimestamp + pauseDuration
            userPreferencesRepository.setPomodoroSessionEndTimestamp(newSessionEnd)
            userPreferencesRepository.setPomodoroBreakEndTimestamp(newBreakEnd)
            SharedMonitoringState.isPomodoroPaused = false
            SharedMonitoringState.pomodoroPauseTimestamp = 0L
            SharedMonitoringState.isPomodoroBlockingActive = true
        }
    }

    fun setPauseable(pauseable: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroPauseable(pauseable)
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            val currentPresets = parsePresets(_uiState.value.presetsJson).toMutableMap()
            currentPresets[name] = _uiState.value.allowedPackages.toList()
            val json = presetsAdapter.toJson(currentPresets) ?: "{}"
            userPreferencesRepository.setPomodoroPresets(json)
        }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch {
            val currentPresets = parsePresets(_uiState.value.presetsJson).toMutableMap()
            currentPresets.remove(name)
            val json = presetsAdapter.toJson(currentPresets) ?: "{}"
            userPreferencesRepository.setPomodoroPresets(json)
        }
    }

    fun applyPreset(packages: List<String>) {
        viewModelScope.launch {
            val targetSize = _uiState.value.maxSelection
            val clamped = packages.take(targetSize).toSet()
            userPreferencesRepository.setPomodoroAllowedPackages(clamped)
        }
    }

    private fun parsePresets(json: String): Map<String, List<String>> {
        return try {
            presetsAdapter.fromJson(json) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getPresets(): Map<String, List<String>> = parsePresets(_uiState.value.presetsJson)

    fun startSession() {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.userPreferencesFlow.first()
            userPreferencesRepository.setPomodoroCurrentSessionNumber(1)
            val sessionEnd = System.currentTimeMillis() + (prefs.pomodoroSessionDurationMinutes * 60 * 1000L)
            userPreferencesRepository.setPomodoroEnabled(true)
            userPreferencesRepository.setPomodoroSessionEndTimestamp(sessionEnd)
            userPreferencesRepository.setPomodoroBreakEndTimestamp(0L)
            SharedMonitoringState.isPomodoroActive = true
            SharedMonitoringState.isPomodoroBlockingActive = true
            SharedMonitoringState.isPomodoroBreakActive = false
            SharedMonitoringState.isPomodoroPaused = false
            SharedMonitoringState.pomodoroPauseTimestamp = 0L
            SharedMonitoringState.pomodoroAllowedPackages = prefs.pomodoroAllowedPackages
            SharedMonitoringState.pomodoroBlockAllowedApps = prefs.pomodoroBlockAllowedApps
        }
    }

    fun endSession() {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroEnabled(false)
            userPreferencesRepository.setPomodoroSessionEndTimestamp(0L)
            userPreferencesRepository.setPomodoroBreakEndTimestamp(0L)
            userPreferencesRepository.setPomodoroCurrentSessionNumber(1)
            SharedMonitoringState.isPomodoroActive = false
            SharedMonitoringState.isPomodoroBlockingActive = false
            SharedMonitoringState.isPomodoroBreakActive = false
            SharedMonitoringState.isPomodoroPaused = false
            SharedMonitoringState.pomodoroPauseTimestamp = 0L
            ZenithNotificationListener.restorePomodoroNotifications(context)
        }
    }

    fun skipToNextSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            userPreferencesRepository.setPomodoroSessionEndTimestamp(now)
            userPreferencesRepository.setPomodoroBreakEndTimestamp(now)
        }
    }

    fun setAllowedPackages(packages: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroAllowedPackages(packages)
        }
    }

    fun setBlockAllowedApps(block: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroBlockAllowedApps(block)
            SharedMonitoringState.pomodoroBlockAllowedApps = block
        }
    }

    fun setSessionDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroSessionDurationMinutes(minutes)
        }
    }

    fun setBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroBreakDurationMinutes(minutes)
        }
    }

    fun setLongBreakDurationMinutes(minutes: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroLongBreakDurationMinutes(minutes)
        }
    }

    fun setSessionCount(count: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroSessionCount(count)
        }
    }

    fun setSessionsBeforeLongBreak(count: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroSessionsBeforeLongBreak(count)
        }
    }

    fun setMaxSelection(max: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setPomodoroMaxAllowedApps(max)
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
