package com.etrisad.zenith.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GracePeriodViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    private val _editError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError

    fun clearEditError() { _editError.value = null }

    fun canEditGracePeriod(prefs: UserPreferences): Boolean {
        val remaining = userPreferencesRepository.getGracePeriodCooldownRemaining(prefs)
        return remaining <= 0L
    }

    fun getCooldownText(prefs: UserPreferences): String? {
        val remaining = userPreferencesRepository.getGracePeriodCooldownRemaining(prefs)
        if (remaining <= 0L) return null
        val days = remaining / (24 * 60 * 60 * 1000L)
        val hours = (remaining % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L)
        return if (days > 0) "${days}d ${hours}h remaining" else "${hours}h remaining"
    }

    fun setGracePeriodEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setGracePeriodEnabled(enabled)
        }
    }

    fun setGracePeriodStartTime(time: String) {
        viewModelScope.launch {
            val prefs = userPreferences.value
            val remaining = userPreferencesRepository.getGracePeriodCooldownRemaining(prefs)
            if (remaining > 0L) {
                _editError.value = "Grace period can only be edited once per week. ${getCooldownText(prefs)}"
                return@launch
            }
            if (!userPreferencesRepository.isGracePeriodDurationValid(time, prefs.gracePeriodEndTime)) {
                _editError.value = "Grace period cannot exceed ${UserPreferencesRepository.GRACE_PERIOD_MAX_DURATION_MINUTES / 60} hours"
                return@launch
            }
            val ok = userPreferencesRepository.setGracePeriodStartTime(time)
            if (!ok) _editError.value = "Failed to update grace period"
        }
    }

    fun setGracePeriodEndTime(time: String) {
        viewModelScope.launch {
            val prefs = userPreferences.value
            val remaining = userPreferencesRepository.getGracePeriodCooldownRemaining(prefs)
            if (remaining > 0L) {
                _editError.value = "Grace period can only be edited once per week. ${getCooldownText(prefs)}"
                return@launch
            }
            if (!userPreferencesRepository.isGracePeriodDurationValid(prefs.gracePeriodStartTime, time)) {
                _editError.value = "Grace period cannot exceed ${UserPreferencesRepository.GRACE_PERIOD_MAX_DURATION_MINUTES / 60} hours"
                return@launch
            }
            val ok = userPreferencesRepository.setGracePeriodEndTime(time)
            if (!ok) _editError.value = "Failed to update grace period"
        }
    }

    fun setGracePeriodDays(days: Set<Int>) {
        viewModelScope.launch {
            val prefs = userPreferences.value
            val remaining = userPreferencesRepository.getGracePeriodCooldownRemaining(prefs)
            if (remaining > 0L) {
                _editError.value = "Grace period can only be edited once per week. ${getCooldownText(prefs)}"
                return@launch
            }
            val ok = userPreferencesRepository.setGracePeriodDays(days)
            if (!ok) _editError.value = "Failed to update grace period"
        }
    }
}

class GracePeriodViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GracePeriodViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GracePeriodViewModel(userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
