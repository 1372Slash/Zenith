package com.etrisad.zenith.service

import kotlinx.coroutines.flow.MutableStateFlow

object AppStateHolder {
    val foregroundApp = MutableStateFlow<String?>(null)
    val isScreenOn = MutableStateFlow(true)
    val isPowerSaveMode = MutableStateFlow(false)
    val isAccessibilityServiceRunning = MutableStateFlow(false)
}
