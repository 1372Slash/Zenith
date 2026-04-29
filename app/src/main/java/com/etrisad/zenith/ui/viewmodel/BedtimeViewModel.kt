package com.etrisad.zenith.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BedtimeViewModel(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    fun setBedtimeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeEnabled(enabled)
        }
    }

    fun setBedtimeStartTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeStartTime(time)
        }
    }

    fun setBedtimeEndTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeEndTime(time)
        }
    }

    fun setBedtimeDays(days: Set<Int>) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeDays(days)
        }
    }

    fun setBedtimeDndEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeDndEnabled(enabled)
        }
    }

    fun setBedtimeWindDownEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeWindDownEnabled(enabled)
        }
    }

    fun setBedtimeWhitelistedPackages(packages: Set<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setBedtimeWhitelistedPackages(packages)
        }
    }
}
