package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import kotlinx.coroutines.launch

@Composable
fun GeneralSettings(
    preferences: UserPreferences,
    onSetTarget: (Int) -> Unit,
    onSetEmergencyRecharge: (Int) -> Unit,
    onSetDelayAppDuration: (Int) -> Unit,
    onShowWhitelistSheetChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    permissionsMissing: Boolean = false
) {
    var showTargetSheet by remember { mutableStateOf(false) }
    var showEmergencyRechargeSheet by remember { mutableStateOf(false) }
    var showDelayAppSheet by remember { mutableStateOf(false) }

    Column {
        PreferenceCategory(title = "General")

        SettingsActionItem(
            title = "Permissions",
            summary = if (permissionsMissing) "Some required permissions are not granted" else "Manage required and optional system permissions",
            onClick = onOpenPermissions,
            icon = Icons.Outlined.Security,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
            trailing = if (permissionsMissing) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Emergency Recharge Time",
            summary = preferences.emergencyRechargeDurationMinutes.let { mins ->
                val label = when {
                    mins >= 1440 -> "${mins / 1440} day${if (mins / 1440 > 1) "s" else ""}"
                    mins >= 60 -> "${mins / 60} hour${if (mins / 60 > 1) "s" else ""}"
                    else -> "$mins minutes"
                }
                "1 charge every $label"
            },
            onClick = { showEmergencyRechargeSheet = true },
            icon = Icons.Outlined.Shield,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Daily Screen Time Target",
            summary = if (preferences.screenTimeTargetMinutes > 0) {
                val h = preferences.screenTimeTargetMinutes / 60
                val m = preferences.screenTimeTargetMinutes % 60
                "Target set to ${if (h > 0) "${h}h " else ""}${m}m"
            } else "No target set",
            onClick = { showTargetSheet = true },
            icon = Icons.Outlined.Edit,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "App Opening Delay",
            summary = preferences.delayAppDurationSeconds.let { secs ->
                val label = when {
                    secs >= 3600 -> "${secs / 3600}h"
                    secs >= 60 -> "${secs / 60}m"
                    else -> "${secs}s"
                }
                "Wait $label before reopening"
            },
            onClick = { showDelayAppSheet = true },
            icon = Icons.Outlined.History,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Whitelist Apps",
            summary = "${preferences.whitelistedPackages.size} apps bypassed",
            onClick = { onShowWhitelistSheetChange(true) },
            icon = Icons.Outlined.VerifiedUser,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )
    }

    if (showTargetSheet) {
        com.etrisad.zenith.ui.screens.home.ScreenTimeTargetBottomSheet(
            initialMinutes = preferences.screenTimeTargetMinutes,
            onDismiss = { showTargetSheet = false },
            onSave = { minutes: Int ->
                onSetTarget(minutes)
                showTargetSheet = false
            }
        )
    }

    if (showEmergencyRechargeSheet) {
        EmergencyRechargeBottomSheet(
            initialMinutes = preferences.emergencyRechargeDurationMinutes,
            onDismiss = { showEmergencyRechargeSheet = false },
            onSave = { minutes: Int ->
                onSetEmergencyRecharge(minutes)
                showEmergencyRechargeSheet = false
            }
        )
    }

    if (showDelayAppSheet) {
        DelayAppBottomSheet(
            initialSeconds = preferences.delayAppDurationSeconds,
            onDismiss = { showDelayAppSheet = false },
            onSave = { seconds: Int ->
                onSetDelayAppDuration(seconds)
                showDelayAppSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelayAppBottomSheet(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var seconds by remember { mutableIntStateOf(initialSeconds) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "App Opening Delay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set how many seconds to wait before a user can reopen an app after being kicked out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (seconds >= 5) seconds -= 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }

                Text(
                    text = if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                IconButton(
                    onClick = { if (seconds < 3600) seconds += 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                listOf(15, 30, 60).forEach { preset ->
                    FilterChip(
                        selected = seconds == preset,
                        onClick = { seconds = preset },
                        label = { Text("${preset}s") },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ZenithButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onSave(seconds)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                text = "Save Delay"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyRechargeBottomSheet(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var minutes by remember { mutableIntStateOf(initialMinutes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Emergency Recharge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set how long it takes to recover one emergency use count.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (minutes >= 5) minutes -= 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }

                Text(
                    text = when {
                        minutes >= 1440 -> "${minutes / 1440}d" + if (minutes % 1440 > 0) " ${ (minutes % 1440) / 60 }h" else ""
                        minutes >= 60 -> "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else ""
                        else -> "${minutes}m"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                IconButton(
                    onClick = { if (minutes < 1440) minutes += 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val presets = listOf(60, 180, 1440)
                presets.forEach { preset ->
                    val label = when {
                        preset >= 1440 -> "${preset / 1440}d"
                        preset >= 60 -> "${preset / 60}h"
                        else -> "${preset}m"
                    }
                    FilterChip(
                        selected = minutes == preset,
                        onClick = { minutes = preset },
                        label = { Text(label) },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ZenithButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onSave(minutes)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                text = "Save Duration"
            )
        }
    }
}
