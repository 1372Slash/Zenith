package com.etrisad.zenith.service

import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.data.local.database.OverlayLogBuffer
import com.etrisad.zenith.data.preferences.UserPreferences

class RedirectShieldController {

    private val policy = RedirectShieldPolicy()

    @Volatile
    private var lastConfigSummary: String? = null

    fun updatePreferences(prefs: UserPreferences) {
        policy.update(
            enabled = prefs.redirectShieldEnabled,
            shieldedApps = prefs.redirectShieldApps,
            whitelistedApps = prefs.redirectShieldWhitelistedApps,
            triggerApps = prefs.redirectShieldTriggerApps
        )

        val shieldedApps = prefs.redirectShieldApps
        val firstShieldedApp = if (shieldedApps.isNotEmpty()) shieldedApps.iterator().next() else null
        val isBrowserKnown = firstShieldedApp != null && WebsiteRepository.isKnownBrowser(firstShieldedApp)
        val browserSupported = isBrowserKnown || shieldedApps.any { WebsiteRepository.isKnownBrowser(it) }
        val browserStr = if (isBrowserKnown) "true" else "false"
        val supportedStr = if (browserSupported) "true" else "false"

        OverlayLogBuffer.d(
            "RedirectShield",
            "config enabled=" + prefs.redirectShieldEnabled.toString() +
                " shielded=" + shieldedApps.size.toString() +
                " whitelisted=" + prefs.redirectShieldWhitelistedApps.size.toString() +
                " triggers=" + prefs.redirectShieldTriggerApps.size.toString() +
                " isKnownBrowser=" + browserStr +
                " browserSupported=" + supportedStr
        )

        val summary = "enabled=" + prefs.redirectShieldEnabled + " " +
            "shielded=" + prefs.redirectShieldApps.size + " " +
            "whitelisted=" + prefs.redirectShieldWhitelistedApps.size + " " +
            "triggers=" + prefs.redirectShieldTriggerApps.size
        if (summary != lastConfigSummary) {
            lastConfigSummary = summary
            OverlayLogBuffer.d("RedirectShield", "config $summary")
        }
    }

    fun reset() {
        policy.reset()
    }

    fun onForegroundChanged(packageName: String): RedirectShieldResult =
        policy.onForegroundChanged(packageName)
}