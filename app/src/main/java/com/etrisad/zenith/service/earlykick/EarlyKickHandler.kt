package com.etrisad.zenith.service.earlykick

import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.website.WebsiteRepository

data class EarlyKickDecision(
    val shouldKick: Boolean,
    val targetPackage: String? = null
)

class EarlyKickHandler(
    private val earlyKickManager: EarlyKickManager
) {
    fun evaluate(
        currentApp: String,
        currentTime: Long,
        cachedTotalUsage: Long,
        shield: ShieldEntity?,
        allowedApps: Map<String, Long>,
        lastAllowedRemainingTime: Map<String, Long>,
        prefs: UserPreferences?,
        isOverlayShowing: Boolean,
        isAppPaused: (ShieldEntity) -> Boolean,
        getWebsiteUsageToday: (String) -> Long,
        websiteDomainProvider: () -> String?,
        isKnownBrowser: (String) -> Boolean,
        allShieldsCache: Map<String, ShieldEntity>
    ): EarlyKickDecision {
        val appDecision = evaluateAppShield(
            currentApp = currentApp,
            currentTime = currentTime,
            cachedTotalUsage = cachedTotalUsage,
            shield = shield,
            allowedApps = allowedApps,
            lastAllowedRemainingTime = lastAllowedRemainingTime,
            prefs = prefs,
            isOverlayShowing = isOverlayShowing,
            isAppPaused = isAppPaused
        )
        if (appDecision.shouldKick) return appDecision

        return evaluateWebsiteShield(
            currentApp = currentApp,
            currentTime = currentTime,
            prefs = prefs,
            isOverlayShowing = isOverlayShowing,
            allowedApps = allowedApps,
            lastAllowedRemainingTime = lastAllowedRemainingTime,
            getWebsiteUsageToday = getWebsiteUsageToday,
            websiteDomainProvider = websiteDomainProvider,
            isKnownBrowser = isKnownBrowser,
            allShieldsCache = allShieldsCache,
            isAppPaused = isAppPaused
        )
    }

    private fun evaluateAppShield(
        currentApp: String,
        currentTime: Long,
        cachedTotalUsage: Long,
        shield: ShieldEntity?,
        allowedApps: Map<String, Long>,
        lastAllowedRemainingTime: Map<String, Long>,
        prefs: UserPreferences?,
        isOverlayShowing: Boolean,
        isAppPaused: (ShieldEntity) -> Boolean
    ): EarlyKickDecision {
        if (shield == null || shield.type == FocusType.GOAL || isAppPaused(shield) || isOverlayShowing) {
            return EarlyKickDecision(false)
        }

        val limitMillis = shield.timeLimitMinutes * 60 * 1000L
        val actualRemaining = (limitMillis - cachedTotalUsage).coerceAtLeast(0L)
        if (actualRemaining <= 0L) return EarlyKickDecision(false)

        val isSessionActive = allowedApps[currentApp]?.let { it > currentTime } ?: false
        val allowedAtRemaining = lastAllowedRemainingTime[currentApp] ?: Long.MAX_VALUE
        val sessionStartedAboveThreshold = allowedAtRemaining > 300000L

        if (earlyKickManager.shouldKick(currentApp, actualRemaining, prefs?.earlyKickEnabled ?: false) &&
            (!isSessionActive || sessionStartedAboveThreshold)
        ) {
            return EarlyKickDecision(shouldKick = true, targetPackage = currentApp)
        }
        return EarlyKickDecision(false)
    }

    private fun evaluateWebsiteShield(
        currentApp: String,
        currentTime: Long,
        prefs: UserPreferences?,
        isOverlayShowing: Boolean,
        allowedApps: Map<String, Long>,
        lastAllowedRemainingTime: Map<String, Long>,
        getWebsiteUsageToday: (String) -> Long,
        websiteDomainProvider: () -> String?,
        isKnownBrowser: (String) -> Boolean,
        allShieldsCache: Map<String, ShieldEntity>,
        isAppPaused: (ShieldEntity) -> Boolean
    ): EarlyKickDecision {
        if (isOverlayShowing) return EarlyKickDecision(false)

        val websiteDomain = websiteDomainProvider()
        if (websiteDomain == null || !isKnownBrowser(currentApp)) {
            return EarlyKickDecision(false)
        }

        val websitePkg = "zenith-web:$websiteDomain"
        val websiteShield = allShieldsCache[websitePkg]
        if (websiteShield == null || websiteShield.type == FocusType.GOAL || isAppPaused(websiteShield)) {
            return EarlyKickDecision(false)
        }

        val websiteLimitMillis = websiteShield.timeLimitMinutes * 60 * 1000L
        val websiteUsage = getWebsiteUsageToday(websiteDomain)
        val websiteRemaining = (websiteLimitMillis - websiteUsage).coerceAtLeast(0L)
        if (websiteRemaining <= 0L) return EarlyKickDecision(false)

        val websiteGrant = allowedApps[websitePkg]
        val isWebsiteSessionActive = websiteGrant?.let { it > currentTime } ?: false
        val websiteAllowedAt = lastAllowedRemainingTime[websitePkg] ?: Long.MAX_VALUE
        val websiteSessionStartedAboveThreshold = websiteAllowedAt > 300000L

        if (earlyKickManager.shouldKick(websitePkg, websiteRemaining, prefs?.earlyKickEnabled ?: false) &&
            (!isWebsiteSessionActive || websiteSessionStartedAboveThreshold)
        ) {
            return EarlyKickDecision(shouldKick = true, targetPackage = websitePkg)
        }
        return EarlyKickDecision(false)
    }
}
