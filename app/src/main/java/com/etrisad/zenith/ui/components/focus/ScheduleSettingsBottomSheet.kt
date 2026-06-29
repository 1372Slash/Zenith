package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import kotlinx.coroutines.launch

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

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    var name by remember { mutableStateOf(editingSchedule?.name ?: "My Schedule") }
    var mode by remember { mutableStateOf(editingSchedule?.mode ?: ScheduleMode.BLOCK) }
    var maxEmergencyUses by remember { mutableStateOf(editingSchedule?.maxEmergencyUses?.toString() ?: "3") }
    var interceptNotifications by remember { mutableStateOf(editingSchedule?.interceptNotifications ?: false) }

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
            MaterialTheme(
                typography = MaterialTheme.typography.copy(
                    displayLarge = MaterialTheme.typography.headlineLarge
                )
            ) {
                TimePicker(state = startTimeState)
            }
        }
    }

    if (showEndTimePicker.value) {
        TimePickerDialog(
            onDismiss = { showEndTimePicker.value = false },
            onConfirm = { showEndTimePicker.value = false }
        ) {
            MaterialTheme(
                typography = MaterialTheme.typography.copy(
                    displayLarge = MaterialTheme.typography.headlineLarge
                )
            ) {
                TimePicker(state = endTimeState)
            }
        }
    }

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
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val packageNames = uiState.selectedAppsForSchedule.toList()
                    MultiAppIconGroup(
                        packageNames = packageNames,
                        totalCount = packageNames.size,
                        size = 72.dp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onEditApps() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Schedule Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                val middleShape = RoundedCornerShape(8.dp)

                Spacer(modifier = Modifier.height(24.dp))

                ZenithGroupedButton(size = ZenithButtonSize.Medium) {
                    val isBlock = mode == ScheduleMode.BLOCK
                    ZenithButtonWeighted(
                        onClick = { mode = ScheduleMode.BLOCK },
                        text = "Block",
                        type = if (isBlock) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                        selected = isBlock,
                        size = ZenithButtonSize.Medium,
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                        isLast = false,
                        isDisableWeight = true,
                        containerColor = if (isBlock) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        contentColor = if (isBlock) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    ZenithButtonWeighted(
                        onClick = { mode = ScheduleMode.ALLOW },
                        text = "Allow",
                        type = if (!isBlock) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                        selected = !isBlock,
                        size = ZenithButtonSize.Medium,
                        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp),
                        isFirst = false,
                        isDisableWeight = true,
                        containerColor = if (!isBlock) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        contentColor = if (!isBlock) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = { showStartTimePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 8.dp),
                        color = containerColor
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
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Surface(
                        onClick = { showEndTimePicker.value = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 28.dp),
                        color = containerColor
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
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PreferenceCategory(title = "Limits")

                CardGroup(shape = RoundedCornerShape(28.dp), containerColor = containerColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency Uses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (mode == ScheduleMode.ALLOW) "Uses for non-whitelisted apps" else "Uses for blocked apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            modifier = Modifier.width(72.dp).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                BasicTextField(
                                    value = maxEmergencyUses,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) maxEmergencyUses = it },
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

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

                Spacer(modifier = Modifier.height(120.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        scope.launch {
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
                            sheetState.hide()
                            onSave(name, startStr, endStr, mode, maxEmergencyUses.toIntOrNull() ?: 3, interceptNotifications)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = if (editingSchedule != null) "Update Schedule" else "Save Schedule"
                )
            }
        }
    }
}

