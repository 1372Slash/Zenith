package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.etrisad.zenith.data.preferences.UserPreferencesRepository

class DeepFocusViewModelFactory(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeepFocusViewModel::class.java)) {
            return DeepFocusViewModel(context, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
