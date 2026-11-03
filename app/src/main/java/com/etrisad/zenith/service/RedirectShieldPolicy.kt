package com.etrisad.zenith.service

import com.etrisad.zenith.data.preferences.RedirectShieldDefaults

data class RedirectShieldResult(
    val redirect: Boolean,
    val returnToPackage: String? = null,
    val reason: String
) {
    companion object {
        fun allow(reason: String) = RedirectShieldResult(redirect = false, reason = reason)
    }
}

internal class RedirectShieldPolicy {

    @Volatile
    private var enabled = false

    @Volatile
    private var shieldedApps = emptySet<String>()

    @Volatile
    private var whitelistedApps = emptySet<String>()

    @Volatile
    private var triggerApps = emptySet<String>()

    @Volatile
    private var armed = false

    @Volatile
    private var graceUntil = 0L

    @Volatile
    private var lastRedirectAt = 0L

    @Volatile
    private var lastForegroundPackage: String? = null

    @Volatile
    private var lastSafePackage: String? = null

    private val isConfigured: Boolean
        get() = enabled && shieldedApps.isNotEmpty() && triggerApps.isNotEmpty()

    fun update(
        enabled: Boolean,
        shieldedApps: Set<String>,
        whitelistedApps: Set<String>,
        triggerApps: Set<String>
    ) {
        this.enabled = enabled
        this.shieldedApps = shieldedApps
        this.whitelistedApps = whitelistedApps
        this.triggerApps = triggerApps
        if (!isConfigured) {
            reset()
        }
    }

    fun reset() {
        armed = false
        graceUntil = 0L
        lastForegroundPackage = null
        lastSafePackage = null
    }

    fun onForegroundChanged(packageName: String): RedirectShieldResult {
        val previous = lastForegroundPackage
        lastForegroundPackage = packageName

        if (!isConfigured) {
            armed = false
            graceUntil = 0L
            lastSafePackage = null
            return RedirectShieldResult.allow("not_configured")
        }

        val now = System.currentTimeMillis()
        val withinGrace = armed || now <= graceUntil

        if (packageName in shieldedApps) {
            if (!withinGrace) {
                graceUntil = 0L
                return RedirectShieldResult.allow("not_armed")
            }

            if (previous != null && previous in whitelistedApps) {
                return RedirectShieldResult.allow("from_whitelisted")
            }

            if (now - lastRedirectAt < RedirectShieldDefaults.REDIRECT_COOLDOWN_MS) {
                return RedirectShieldResult.allow("cooldown")
            }

            lastRedirectAt = now
            armed = false
            graceUntil = 0L
            return RedirectShieldResult(
                redirect = true,
                returnToPackage = lastSafePackage,
                reason = "shielded"
            )
        }

        lastSafePackage = packageName

        if (packageName in triggerApps) {
            armed = true
            graceUntil = 0L
            return RedirectShieldResult.allow("trigger")
        }

        if (packageName in whitelistedApps) {
            armed = false
            graceUntil = 0L
            return RedirectShieldResult.allow("whitelisted")
        }

        armed = false
        graceUntil = if (withinGrace) now + RedirectShieldDefaults.ARM_GRACE_PERIOD_MS else 0L
        return RedirectShieldResult.allow(if (withinGrace) "left_trigger" else "idle")
    }
}
