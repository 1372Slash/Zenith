package com.etrisad.zenith.service

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

object AppStateHolder {
    val foregroundApp = MutableStateFlow<String?>(null)
    val isScreenOn = MutableStateFlow(true)
    val isPowerSaveMode = MutableStateFlow(false)
    val isAccessibilityServiceRunning = MutableStateFlow(false)
    val currentWebsiteDomain = MutableStateFlow<String?>(null)
    @Volatile
    var lastBrowserPackage: String? = null

    val websiteSessionStarts = ConcurrentHashMap<String, Long>()

    fun recordWebsiteSessionStart(websitePkg: String) {
        websiteSessionStarts[websitePkg] = System.currentTimeMillis()
    }

    fun consumeWebsiteSessionStart(websitePkg: String): Long? {
        return websiteSessionStarts.remove(websitePkg)
    }
}
