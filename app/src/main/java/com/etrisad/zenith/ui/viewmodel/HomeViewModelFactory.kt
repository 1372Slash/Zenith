package com.etrisad.zenith.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.etrisad.zenith.data.repository.ShieldRepository

class HomeViewModelFactory(
    private val context: Context,
    private val shieldRepository: ShieldRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(context, shieldRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
