package com.etrisad.zenith.service

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

        val summary = "enabled=${prefs.redirectShieldEnabled} " +
            "shielded=${prefs.redirectShieldApps.size} " +
            "whitelisted=${prefs.redirectShieldWhitelistedApps.size} " +
            "triggers=${prefs.redirectShieldTriggerApps.size}"
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
