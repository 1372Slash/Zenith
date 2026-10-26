package com.etrisad.zenith.ui.screens.settings

import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.repository.ShieldRepository
import com.etrisad.zenith.service.SharedMonitoringState
import com.etrisad.zenith.util.ScreenUsageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
internal fun applyAppExclusions(
    privateApps: Set<String>,
    usageOnlyApps: Set<String>,
    preferencesRepository: UserPreferencesRepository,
    shieldRepository: ShieldRepository,
    scope: CoroutineScope
) {
    val normalizedUsageOnly = usageOnlyApps - privateApps
    val newlyPrivate = privateApps - SharedMonitoringState.privatePackages

    SharedMonitoringState.privatePackages = privateApps
    SharedMonitoringState.excludedFromTrackingPackages = normalizedUsageOnly
    SharedMonitoringState.updateRestrictedPackages()
    ScreenUsageHelper.clearCache()

    scope.launch {
        preferencesRepository.setAppExclusions(privateApps, normalizedUsageOnly)
        if (newlyPrivate.isNotEmpty()) {
            shieldRepository.purgePrivateAppData(newlyPrivate)
        }
    }
}

internal fun appExclusionSummary(
    privateApps: Set<String>,
    usageOnlyApps: Set<String>
): String {
    val privateCount = privateApps.size
    val usageOnlyCount = (usageOnlyApps - privateApps).size
    return when {
        privateCount == 0 && usageOnlyCount == 0 -> "All apps tracked normally"
        usageOnlyCount == 0 -> "$privateCount private ${if (privateCount == 1) "app" else "apps"} (fully ignored)"
        privateCount == 0 -> "$usageOnlyCount ${if (usageOnlyCount == 1) "app" else "apps"} hidden (still monitored)"
        else -> "$privateCount private · $usageOnlyCount hidden"
    }
}
