package com.etrisad.zenith.ui.components.overlay

import androidx.compose.runtime.Composable
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity

@Composable
fun InterceptOverlayContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {}
) {
    ShieldOverlay(
        packageName = packageName,
        appName = appName,
        shield = shield,
        totalUsageToday = totalUsageToday,
        totalGlobalUsageToday = totalGlobalUsageToday,
        delayDurationSeconds = delayDurationSeconds,
        onAllowUse = onAllowUse,
        onCloseApp = onCloseApp,
        onGoalDismiss = onGoalDismiss
    )
}

@Composable
fun ScheduleOverlayContent(
    packageName: String,
    appName: String,
    schedule: ScheduleEntity,
    totalGlobalUsageToday: Long,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    ScheduleOverlay(
        packageName = packageName,
        appName = appName,
        schedule = schedule,
        totalGlobalUsageToday = totalGlobalUsageToday,
        onAllowUse = onAllowUse,
        onCloseApp = onCloseApp
    )
}
