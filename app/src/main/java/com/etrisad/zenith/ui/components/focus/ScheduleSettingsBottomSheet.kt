package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.viewmodel.FocusUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsBottomSheet(
    uiState: FocusUiState,
    editingSchedule: ScheduleEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ScheduleMode, Int, Boolean) -> Unit,
    onEditApps: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val context = LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val preferences by repository.userPreferencesFlow.collectAsState(initial = UserPreferences())

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val appIcons = remember(uiState.selectedAppsForSchedule) {
        val packages = uiState.selectedAppsForSchedule.toList()
        packages.take(4).mapNotNull { pkg ->
            try {
                context.packageManager.getApplicationIcon(pkg)
            } catch (_: Exception) {
                null
            }
        }
    }

    var name by remember { mutableStateOf(editingSchedule?.name ?: "My Schedule") }
    var mode by remember { mutableStateOf(editingSchedule?.mode ?: ScheduleMode.BLOCK) }
    var maxEmergencyUses by remember { mutableStateOf(editingSchedule?.maxEmergencyUses?.toString() ?: "3") }
    var interceptNotifications by remember { mutableStateOf(editingSchedule?.interceptNotifications ?: false) }
    var isEditingName by remember { mutableStateOf(false) }

    val initialStart = editingSchedule?.startTime?.split(":")?.map { it.toInt() } ?: listOf(9, 0)
    val initialEnd = editingSchedule?.endTime?.split(":")?.map { it.toInt() } ?: listOf(17, 0)

    val startTimeState = rememberTimePickerState(initialHour = initialStart[0], initialMinute = initialStart[1], is24Hour = true)
    val endTimeState = rememberTimePickerState(initialHour = initialEnd[0], initialMinute = initialEnd[1], is24Hour = true)

    val currentLocale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val showStartTimePicker = remember { mutableStateOf(false) }
    val showEndTimePicker = remember { mutableStateOf(false) }

    if (showStartTimePicker.value) {
        TimePickerDialog(
            onDismiss = { showStartTimePicker.value = false },
            onConfirm = { showStartTimePicker.value = false }
        ) {
            TimePicker(state = startTimeState)
        }
    }

    if (showEndTimePicker.value) {
        TimePickerDialog(
            onDismiss = { showEndTimePicker.value = false },
            onConfirm = { showEndTimePicker.value = false }
        ) {
            TimePicker(state = endTimeState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
                    val packageNames = uiState.selectedAppsForSchedule.toList()
                    MultiAppIconGroup(
                        appIcons = appIcons,
                        totalCount = packageNames.size,
                        size = 56.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onEditApps() }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isEditingName = !isEditingName }
                    ) {
                        if (isEditingName) {
                            BasicTextField(
                                value = name,
                                onValueChange = { name = it },
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Schedule Settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Surface(
                        onClick = { showStartTimePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            bottomStart = 24.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        ),
                        color = containerColor.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Start Time",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format(
                                    currentLocale,
                                    "%02d:%02d",
                                    startTimeState.hour,
                                    startTimeState.minute
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        onClick = { showEndTimePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(
                            topEnd = 24.dp,
                            bottomEnd = 24.dp,
                            topStart = 8.dp,
                            bottomStart = 8.dp
                        ),
                        color = containerColor.copy(alpha = 0.6f),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "End Time",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format(
                                    currentLocale,
                                    "%02d:%02d",
                                    endTimeState.hour,
                                    endTimeState.minute
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Schedule Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScheduleModeOptionButton(
                        label = "Block",
                        icon = Icons.Outlined.Block,
                        selected = mode == ScheduleMode.BLOCK,
                        onClick = { mode = ScheduleMode.BLOCK },
                        isFirst = true,
                        isLast = false,
                        containerColor = containerColor
                    )
                    ScheduleModeOptionButton(
                        label = "Allow",
                        icon = Icons.Outlined.CheckCircleOutline,
                        selected = mode == ScheduleMode.ALLOW,
                        onClick = { mode = ScheduleMode.ALLOW },
                        isFirst = false,
                        isLast = true,
                        containerColor = containerColor
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = maxEmergencyUses,
                    onValueChange = { if (it.all { char -> char.isDigit() }) maxEmergencyUses = it },
                    label = { Text("Max Emergency Uses") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                    supportingText = {
                        Text(
                            if (mode == ScheduleMode.ALLOW)
                                "Apps not in the list will be limited to this many uses"
                            else "Selected apps can be used this many times in emergency"
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                PreferenceCategory(title = "Settings")

                CardGroup(
                    shape = RoundedCornerShape(24.dp),
                    containerColor = containerColor
                ) {
                    SettingsToggle(
                        title = "Intercept Notifications",
                        description = "Hold notifications until schedule ends",
                        checked = if (mode == ScheduleMode.BLOCK) interceptNotifications else false,
                        onCheckedChange = { 
                            if (it && !com.etrisad.zenith.util.isNotificationListenerEnabled(context)) {
                                android.widget.Toast.makeText(context, "Please grant Notification Access in settings", android.widget.Toast.LENGTH_LONG).show()
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } else {
                                interceptNotifications = it 
                            }
                        },
                        icon = Icons.Outlined.NotificationsPaused,
                        enabled = mode == ScheduleMode.BLOCK,
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        val startStr = String.format(
                            currentLocale,
                            "%02d:%02d",
                            startTimeState.hour,
                            startTimeState.minute
                        )
                        val endStr = String.format(
                            currentLocale,
                            "%02d:%02d",
                            endTimeState.hour,
                            endTimeState.minute
                        )
                        onSave(name, startStr, endStr, mode, maxEmergencyUses.toIntOrNull() ?: 3, interceptNotifications)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = if (editingSchedule != null) "Update Schedule" else "Save Schedule",
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { content() }
    )
}

@Composable
fun RowScope.ScheduleModeOptionButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    containerColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val widthScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 1.5f
            selected -> 1.25f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "WidthScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BgColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ContentColor"
    )

    val innerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "InnerRadius"
    )

    val outerRadius = 24.dp

    val shape = when {
        selected -> CircleShape
        isFirst -> RoundedCornerShape(
            topStart = outerRadius,
            bottomStart = outerRadius,
            topEnd = innerRadius,
            bottomEnd = innerRadius
        )
        isLast -> RoundedCornerShape(
            topEnd = outerRadius,
            bottomEnd = outerRadius,
            topStart = innerRadius,
            bottomStart = innerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }

    Box(
        modifier = Modifier
            .weight(widthScale)
            .height(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
