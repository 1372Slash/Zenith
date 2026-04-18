package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.etrisad.zenith.data.repository.ShieldRepository

class FocusViewModelFactory(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusViewModel(context, shieldRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
