package com.etrisad.zenith.service

import kotlinx.coroutines.flow.MutableStateFlow

object AppStateHolder {
    val foregroundApp = MutableStateFlow<String?>(null)
    val isScreenOn = MutableStateFlow(true)
    val isPowerSaveMode = MutableStateFlow(false)
    val isAccessibilityServiceRunning = MutableStateFlow(false)

    // True while at least one activity of the app is started (visible).
    // Used to decide whether a camera-typed foreground service may be started
    // (Android 14+ requires while-in-use access for FGS type camera).
    @Volatile
    var isAppVisible = false
}
