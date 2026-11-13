package com.etrisad.zenith.service

import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.local.database.OverlayLogBuffer

object ShieldLatencyTracker {
    private const val TAG = "ShieldLatency"

    @Volatile
    private var detectedPackage: String? = null

    @Volatile
    private var detectedAt: Long = 0L

    @Volatile
    private var requestedAt: Long = 0L

    private fun mode(): String = try {
        SharedMonitoringState.performanceLevel.name
    } catch (_: Throwable) {
        "UNKNOWN"
    }

    private fun pollMs(): Long = try {
        SharedMonitoringState.performanceConfig.monDefault
    } catch (_: Throwable) {
        0L
    }

    private fun a11yState(): String = try {
        when {
            !ZenithService.isServiceRunning -> "off"
            SharedMonitoringState.accessibilityLimitedMode -> "limited"
            else -> "on"
        }
    } catch (_: Throwable) {
        "unknown"
    }

    fun onDetected(app: String, source: String) {
        if (app == detectedPackage && detectedAt > 0L) return
        detectedPackage = app
        detectedAt = System.currentTimeMillis()
        requestedAt = 0L
        val isBrowserKnown = WebsiteRepository.isKnownBrowser(app)
        val browserSupported = isBrowserKnown || app.lowercase().contains("browser") || app.lowercase().contains("chrome")
        OverlayLogBuffer.d(
            TAG,
            "detect app=$app src=$source isKnownBrowser=$isBrowserKnown browserSupported=$browserSupported mode=${mode()} a11y=${a11yState()} poll=${pollMs()}ms"
        )
    }

    fun onShowRequested(app: String, trigger: String) {
        val now = System.currentTimeMillis()
        if (app != detectedPackage) {
            detectedPackage = app
            detectedAt = now
        }
        requestedAt = now
        val isBrowserKnown = WebsiteRepository.isKnownBrowser(app)
        val browserSupported = isBrowserKnown || app.lowercase().contains("browser") || app.lowercase().contains("chrome")
        OverlayLogBuffer.d(
            TAG,
            "request app=$app trigger=$trigger detect_to_request=${now - detectedAt}ms isKnownBrowser=$isBrowserKnown browserSupported=$browserSupported mode=${mode()} a11y=${a11yState()}"
        )
    }

    fun onShown(app: String, type: String?) {
        val now = System.currentTimeMillis()
        if (app != detectedPackage) {
            val isBrowserKnown = WebsiteRepository.isKnownBrowser(app)
            val browserSupported = isBrowserKnown || app.lowercase().contains("browser") || app.lowercase().contains("chrome")
            OverlayLogBuffer.d(
                TAG,
                "shown app=$app type=${type ?: "-"} detect_to_show=-1ms isKnownBrowser=$isBrowserKnown browserSupported=$browserSupported mode=${mode()} a11y=${a11yState()}"
            )
            return
        }
        val detectMs = now - detectedAt
        val requestMs = if (requestedAt > 0L) now - requestedAt else -1L
        val isBrowserKnown = WebsiteRepository.isKnownBrowser(app)
        val browserSupported = isBrowserKnown || app.lowercase().contains("browser") || app.lowercase().contains("chrome")
        OverlayLogBuffer.d(
            TAG,
            "shown app=$app type=${type ?: "-"} detect_to_show=${detectMs}ms request_to_show=${requestMs}ms isKnownBrowser=$isBrowserKnown browserSupported=$browserSupported mode=${mode()} a11y=${a11yState()}"
        )
        detectedPackage = null
        detectedAt = 0L
        requestedAt = 0L
    }

    fun onCheckSkipped(app: String, reason: String) {
        val isBrowserKnown = WebsiteRepository.isKnownBrowser(app)
        val browserSupported = isBrowserKnown || app.lowercase().contains("browser") || app.lowercase().contains("chrome")
        OverlayLogBuffer.d(TAG, "skip app=$app reason=$reason isKnownBrowser=$isBrowserKnown browserSupported=$browserSupported mode=${mode()} a11y=${a11yState()}")
    }
}
