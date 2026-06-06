package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.viewmodel.AppInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSettingsBottomSheet(
    appInfo: AppInfo,
    usageToday: Long,
    existingShield: ShieldEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean, Boolean, Boolean, Int, Int, Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val preferences by repository.userPreferencesFlow.collectAsState(initial = UserPreferences())

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val screenHeight = configuration.screenHeightDp.dp
    val timePickerState = rememberTimePickerState(
        initialHour = (existingShield?.timeLimitMinutes ?: 30) / 60,
        initialMinute = (existingShield?.timeLimitMinutes ?: 30) % 60,
        is24Hour = true
    )
    var maxEmergencyUses by remember { mutableStateOf(existingShield?.maxEmergencyUses?.toString() ?: "3") }
    var remindersEnabled by remember { mutableStateOf(existingShield?.isRemindersEnabled ?: true) }
    var strictModeEnabled by remember { mutableStateOf(existingShield?.isStrictModeEnabled ?: false) }
    var autoQuitEnabled by remember { mutableStateOf(existingShield?.isAutoQuitEnabled ?: false) }
    var isDelayAppEnabled by remember { mutableStateOf(existingShield?.isDelayAppEnabled ?: false) }
    var maxUses by remember { mutableStateOf(existingShield?.maxUsesPerPeriod?.toString() ?: "5") }
    var refreshPeriodMinutes by remember { mutableIntStateOf(existingShield?.refreshPeriodMinutes ?: 60) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val isPreventEdit = remember(existingShield, usageToday) {
        if (existingShield != null) {
            val limitMillis = existingShield.timeLimitMinutes * 60 * 1000L
            limitMillis > 0 && usageToday >= (limitMillis * 0.5)
        } else false
    }

    val refreshOptions = listOf(
        "Every 30 Minutes" to 30,
        "Every 1 Hour" to 60,
        "Every 2 Hours" to 120,
        "Every 6 Hours" to 360,
        "Every 12 Hours" to 720,
        "Every 24 Hours" to 1440
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubcomposeAsyncImage(
                        model = "app-icon://${appInfo.packageName}",
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Android, contentDescription = null)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Shield Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Today: ${formatRemainingTime(usageToday)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Daily Time Limit (HH:MM)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimeInput(
                        state = timePickerState,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val presets = listOf(15, 30, 60, 120)
                    presets.forEach { preset ->
                        FilterChip(
                            selected = (timePickerState.hour * 60 + timePickerState.minute) == preset,
                            onClick = {
                                timePickerState.hour = preset / 60
                                timePickerState.minute = preset % 60
                            },
                            label = { Text(if (preset >= 60) "${preset / 60}h" else "${preset}m") },
                            shape = CircleShape
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = maxUses,
                        onValueChange = { if (it.all { char -> char.isDigit() }) maxUses = it },
                        label = { Text("Times of Uses") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) }
                    )

                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = refreshOptions.find { it.second == refreshPeriodMinutes }?.first
                                ?: "Custom",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Refresh Period") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            modifier = Modifier.menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                true
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            refreshOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.first) },
                                    onClick = {
                                        refreshPeriodMinutes = option.second
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = maxEmergencyUses,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxEmergencyUses = it },
                    label = { Text("Max Emergency Uses") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                PreferenceCategory(title = "Settings")

                val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                val middleShape = RoundedCornerShape(8.dp)
                val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

                CardGroup(shape = topShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Show Reminders",
                        description = "Get notified before limit is reached",
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it },
                        icon = Icons.Outlined.NotificationsActive,
                        shape = topShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = middleShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Strict Mode",
                        description = "No extensions allowed after limit",
                        checked = strictModeEnabled,
                        onCheckedChange = { strictModeEnabled = it },
                        icon = Icons.Outlined.GppGood,
                        shape = middleShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = middleShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Auto Quit",
                        description = "Exit app automatically when session ends",
                        checked = autoQuitEnabled,
                        onCheckedChange = { autoQuitEnabled = it },
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        shape = middleShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = bottomShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Delay App",
                        description = "Wait before reopening after being kicked out",
                        checked = isDelayAppEnabled,
                        onCheckedChange = { isDelayAppEnabled = it },
                        icon = Icons.Outlined.History,
                        shape = bottomShape
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isPreventEdit) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Shield settings are locked because your remaining limit is less than 50%. You can only save changes that make the shield more restrictive (e.g., lower limit) to prevent bypassing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            val currentLimit = timePickerState.hour * 60 + timePickerState.minute
            val currentMaxUses = maxUses.toIntOrNull() ?: 5
            val currentMaxEmergency = maxEmergencyUses.toIntOrNull() ?: 3

            val canSave = remember(
                existingShield, isPreventEdit, currentLimit, currentMaxUses, currentMaxEmergency,
                remindersEnabled, strictModeEnabled, autoQuitEnabled, isDelayAppEnabled,
                refreshPeriodMinutes
            ) {
                if (existingShield == null) {
                    currentLimit > 0
                } else {
                    val limitDecreased = currentLimit < existingShield.timeLimitMinutes
                    val limitIncreased = currentLimit > existingShield.timeLimitMinutes

                    val usesDecreased = currentMaxUses < existingShield.maxUsesPerPeriod
                    val usesIncreased = currentMaxUses > existingShield.maxUsesPerPeriod

                    val emergencyDecreased = currentMaxEmergency < existingShield.maxEmergencyUses
                    val emergencyIncreased = currentMaxEmergency > existingShield.maxEmergencyUses

                    val remindersEnabledNew = !existingShield.isRemindersEnabled && remindersEnabled
                    val strictEnabled = !existingShield.isStrictModeEnabled && strictModeEnabled
                    val strictDisabled = existingShield.isStrictModeEnabled && !strictModeEnabled

                    val autoQuitEnabledNew = !existingShield.isAutoQuitEnabled && autoQuitEnabled
                    val autoQuitDisabled = existingShield.isAutoQuitEnabled && !autoQuitEnabled

                    val delayEnabledNew = !existingShield.isDelayAppEnabled && isDelayAppEnabled
                    val delayDisabled = existingShield.isDelayAppEnabled && !isDelayAppEnabled

                    val remindersChanged = remindersEnabled != existingShield.isRemindersEnabled
                    val refreshChanged = refreshPeriodMinutes != existingShield.refreshPeriodMinutes

                    val hasPositiveChange = limitDecreased || usesDecreased || emergencyDecreased ||
                            strictEnabled || autoQuitEnabledNew || delayEnabledNew || remindersEnabledNew

                    val hasNegativeChange = limitIncreased || usesIncreased || emergencyIncreased ||
                            strictDisabled || autoQuitDisabled || delayDisabled || (!remindersEnabledNew && remindersChanged)

                    val hasAnyChange = hasPositiveChange || hasNegativeChange || remindersChanged || refreshChanged

                    if (isPreventEdit) {
                        hasPositiveChange && !hasNegativeChange
                    } else {
                        hasAnyChange
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        onSave(
                            currentLimit,
                            currentMaxEmergency,
                            remindersEnabled,
                            strictModeEnabled,
                            autoQuitEnabled,
                            currentMaxUses,
                            refreshPeriodMinutes,
                            isDelayAppEnabled
                        )
                        scope.launch {
                            sheetState.hide()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Save Shield",
                    enabled = canSave
                )
            }
        }
    }
}
