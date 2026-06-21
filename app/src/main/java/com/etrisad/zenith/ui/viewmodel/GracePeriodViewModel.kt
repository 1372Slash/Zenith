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

    fun setGracePeriodEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setGracePeriodEnabled(enabled)
        }
    }

    fun setGracePeriodStartTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGracePeriodStartTime(time)
        }
    }

    fun setGracePeriodEndTime(time: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGracePeriodEndTime(time)
        }
    }

    fun setGracePeriodDays(days: Set<Int>) {
        viewModelScope.launch {
            userPreferencesRepository.setGracePeriodDays(days)
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
