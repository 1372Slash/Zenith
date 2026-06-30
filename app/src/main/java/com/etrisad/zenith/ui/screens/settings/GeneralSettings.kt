package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.components.focus.TimePickerDialog
import kotlinx.coroutines.launch

@Composable
fun GeneralSettings(
    preferences: UserPreferences,
    onSetTarget: (Int) -> Unit,
    onSetEmergencyRecharge: (Int) -> Unit,
    onSetDelayAppDuration: (Int) -> Unit,
    onSetDayStartTime: (Int, Int) -> Unit,
    onShowWhitelistSheetChange: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    permissionsMissing: Boolean = false
) {
    var showTargetSheet by remember { mutableStateOf(false) }
    var showEmergencyRechargeSheet by remember { mutableStateOf(false) }
    var showDelayAppSheet by remember { mutableStateOf(false) }
    var showDayStartSheet by remember { mutableStateOf(false) }

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
            title = "Day Start Time",
            summary = if (preferences.dayStartHour == 0 && preferences.dayStartMinute == 0) {
                "Day resets at midnight (default)"
            } else {
                val h = preferences.dayStartHour
                val m = preferences.dayStartMinute
                "Day resets at ${String.format("%02d:%02d", h, m)}"
            },
            onClick = { showDayStartSheet = true },
            icon = Icons.Outlined.Schedule,
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

    if (showDayStartSheet) {
        DayStartTimeBottomSheet(
            initialHour = preferences.dayStartHour,
            initialMinute = preferences.dayStartMinute,
            onDismiss = { showDayStartSheet = false },
            onSave = { hour: Int, minute: Int ->
                onSetDayStartTime(hour, minute)
                showDayStartSheet = false
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var seconds by remember { mutableIntStateOf(initialSeconds) }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "App Opening Delay",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set how many seconds to wait before reopening an app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                    color = containerColor
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Delay Duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
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
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            IconButton(
                                onClick = { if (seconds < 3600) seconds += 5 },
                                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Text("+", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ZenithGroupedButton(size = ZenithButtonSize.Small) {
                    val presets = listOf(15, 30, 60)
                    presets.forEachIndexed { index, preset ->
                        val isSelected = seconds == preset
                        val label = "${preset}s"
                        val pShape = when (index) {
                            0 -> RoundedCornerShape(bottomStart = 28.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                            presets.lastIndex -> RoundedCornerShape(bottomEnd = 28.dp, topEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        ZenithButtonWeighted(
                            onClick = { seconds = preset },
                            text = label,
                            type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            selected = isSelected,
                            shape = pShape,
                            isFirst = index == 0,
                            isLast = index == presets.lastIndex,
                            contentScaleEnabled = false
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyRechargeBottomSheet(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var minutes by remember { mutableIntStateOf(initialMinutes) }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Emergency Recharge",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set how long it takes to recover one emergency use count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                    color = containerColor
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Recharge Duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
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
                                    minutes >= 1440 -> "${minutes / 1440}d" + if (minutes % 1440 > 0) " ${(minutes % 1440) / 60}h" else ""
                                    minutes >= 60 -> "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else ""
                                    else -> "${minutes}m"
                                },
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            IconButton(
                                onClick = { if (minutes < 1440) minutes += 5 },
                                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            ) {
                                Text("+", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ZenithGroupedButton(size = ZenithButtonSize.Small) {
                    val presets = listOf(60, 180, 1440)
                    presets.forEachIndexed { index, preset ->
                        val isSelected = minutes == preset
                        val label = when {
                            preset >= 1440 -> "${preset / 1440}d"
                            preset >= 60 -> "${preset / 60}h"
                            else -> "${preset}m"
                        }
                        val pShape = when (index) {
                            0 -> RoundedCornerShape(bottomStart = 28.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                            presets.lastIndex -> RoundedCornerShape(bottomEnd = 28.dp, topEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        ZenithButtonWeighted(
                            onClick = { minutes = preset },
                            text = label,
                            type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            selected = isSelected,
                            shape = pShape,
                            isFirst = index == 0,
                            isLast = index == presets.lastIndex,
                            contentScaleEnabled = false
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStartTimeBottomSheet(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentLocale = androidx.compose.ui.text.intl.Locale.current.platformLocale

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val scrollState = rememberScrollState()

    var showTimePicker by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                selectedHour = timePickerState.hour
                selectedMinute = timePickerState.minute
                showTimePicker = false
            }
        ) {
            MaterialTheme(
                typography = MaterialTheme.typography.copy(
                    displayLarge = MaterialTheme.typography.headlineLarge
                )
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Day Start Time",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set when a new day begins",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                    color = containerColor
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Day Resets At",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format(currentLocale, "%02d:%02d", selectedHour, selectedMinute),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                ZenithGroupedButton(size = ZenithButtonSize.Small) {
                    val presets = listOf(0 to 0, 6 to 0, 9 to 0, 12 to 0)
                    presets.forEachIndexed { index, (ph, pm) ->
                        val isSelected = selectedHour == ph && selectedMinute == pm
                        val label = if (ph == 0 && pm == 0) "Midnight" else String.format("%02d:%02d", ph, pm)
                        val pShape = when (index) {
                            0 -> RoundedCornerShape(bottomStart = 28.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                            presets.lastIndex -> RoundedCornerShape(bottomEnd = 28.dp, topEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        ZenithButtonWeighted(
                            onClick = {
                                selectedHour = ph
                                selectedMinute = pm
                            },
                            text = label,
                            type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            selected = isSelected,
                            shape = pShape,
                            isFirst = index == 0,
                            isLast = index == presets.lastIndex,
                            contentScaleEnabled = false
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onSave(selectedHour, selectedMinute)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Save Day Start Time"
                )
            }
        }
    }
}
