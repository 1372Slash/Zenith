package com.etrisad.zenith.data.website

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

object WebsiteStateHolder {
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
