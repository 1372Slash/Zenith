package com.etrisad.zenith.ui.screens.alarm

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.model.AlarmItem
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.receiver.AlarmBroadcastReceiver
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.focus.AlarmItemSettingsBottomSheet
import com.etrisad.zenith.ui.components.focus.SwipeableItemContainer
import com.etrisad.zenith.ui.screens.bedtime.TimePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AlarmSortType { TIME, NAME, CLOSEST }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun AlarmScreen(
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())

    var sortType by remember { mutableStateOf(AlarmSortType.TIME) }

    val allAlarms = remember(prefs.alarmsJson) {
        preferencesRepository.parseAlarms(prefs.alarmsJson)
    }

    val alarmList = remember(sortType, allAlarms) {
        when (sortType) {
            AlarmSortType.TIME -> allAlarms.sortedBy { it.hour * 60 + it.minute }
            AlarmSortType.NAME -> allAlarms.sortedBy { it.name.lowercase() }
            AlarmSortType.CLOSEST -> {
                val now = java.util.Calendar.getInstance()
                allAlarms.sortedWith(
                    compareByDescending<AlarmItem> { it.enabled && prefs.alarmMasterEnabled }
                        .thenBy { alarm ->
                            val alarmCal = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                                set(java.util.Calendar.MINUTE, alarm.minute)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            if (alarm.days.isEmpty()) {
                                if (alarmCal.before(now)) alarmCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            } else {
                                for (i in 0..7) {
                                    val dayOfWeek = alarmCal.get(java.util.Calendar.DAY_OF_WEEK)
                                    if (alarm.days.contains(dayOfWeek) && alarmCal.after(now)) break
                                    alarmCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                }
                            }
                            alarmCal.timeInMillis
                        }
                )
            }
        }
    }

    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var newAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("") }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAlarmIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    val today = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedAlarmIds = emptySet()
    }

    fun toggleSelection(id: Long) {
        selectedAlarmIds = if (id in selectedAlarmIds) {
            val newSet = selectedAlarmIds - id
            if (newSet.isEmpty()) { isSelectionMode = false }
            newSet
        } else {
            selectedAlarmIds + id
        }
    }

    fun selectAll() {
        selectedAlarmIds = if (selectedAlarmIds.size == alarmList.size) {
            exitSelectionMode()
            emptySet()
        } else {
            alarmList.map { it.id }.toSet()
        }
    }

    fun deleteSelected() {
        scope.launch {
            selectedAlarmIds.forEach { id ->
                preferencesRepository.deleteAlarm(id)
            }
            rescheduleAlarms(context, preferencesRepository, prefs.alarmMasterEnabled)
            exitSelectionMode()
        }
    }

    fun openNewAlarm() {
        val name = AlarmItem.nextName(allAlarms)
        pendingName = name
        newAlarm = AlarmItem(hour = 7, minute = 0, name = name)
    }

    val animatedTopPadding by animateDpAsState(
        targetValue = if (isSelectionMode) 72.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionTopPadding"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + animatedTopPadding,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            )
        ) {
            if (alarmList.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No alarms set",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap + to add an alarm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                item(key = "sort_header") {
                    AnimatedVisibility(
                        visible = !isSelectionMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sort by",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            ZenithToggleButtonGroup(
                                modifier = Modifier.width(180.dp),
                                size = ZenithButtonSize.Medium,
                                options = listOf(
                                    ZenithToggleOption(
                                        icon = if (sortType == AlarmSortType.TIME) Icons.Filled.Schedule else Icons.Outlined.Schedule,
                                    ),
                                    ZenithToggleOption(
                                        icon = if (sortType == AlarmSortType.NAME) Icons.Filled.SortByAlpha else Icons.Outlined.SortByAlpha,
                                    ),
                                    ZenithToggleOption(
                                        icon = if (sortType == AlarmSortType.CLOSEST) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsActive,
                                    )
                                ),
                                selectedIndices = setOf(
                                    when (sortType) {
                                        AlarmSortType.TIME -> 0
                                        AlarmSortType.NAME -> 1
                                        AlarmSortType.CLOSEST -> 2
                                    }
                                ),
                                onToggle = { index ->
                                    sortType = when (index) {
                                        0 -> AlarmSortType.TIME
                                        1 -> AlarmSortType.NAME
                                        else -> AlarmSortType.CLOSEST
                                    }
                                }
                            )
                        }
                    }
                }

                val total = alarmList.size
                itemsIndexed(alarmList, key = { _, alarm -> alarm.id }) { index, alarm ->
                    val isSelected = alarm.id in selectedAlarmIds
                    val isActuallyActiveToday = alarm.enabled && prefs.alarmMasterEnabled && 
                                               (alarm.days.isEmpty() || alarm.days.contains(today))
                    
    val isHighlighted = isSelected || isActuallyActiveToday

                    val topStartRadius by animateDpAsState(
                        targetValue = if (isHighlighted || total == 1 || index == 0) 24.dp else 8.dp,
                        label = "topStartRadius"
                    )
                    val topEndRadius by animateDpAsState(
                        targetValue = if (isHighlighted || total == 1 || index == 0) 24.dp else 8.dp,
                        label = "topEndRadius"
                    )
                    val bottomStartRadius by animateDpAsState(
                        targetValue = if (isHighlighted || total == 1 || index == total - 1) 24.dp else 8.dp,
                        label = "bottomStartRadius"
                    )
                    val bottomEndRadius by animateDpAsState(
                        targetValue = if (isHighlighted || total == 1 || index == total - 1) 24.dp else 8.dp,
                        label = "bottomEndRadius"
                    )

                    val animatedShape = RoundedCornerShape(
                        topStart = topStartRadius,
                        topEnd = topEndRadius,
                        bottomStart = bottomStartRadius,
                        bottomEnd = bottomEndRadius
                    )

                    Column(
                        modifier = Modifier.animateItem(
                            fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                            fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                            placementSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    ) {
                        SwipeableItemContainer(
                            onEdit = { editingAlarm = alarm },
                            onDelete = {
                                scope.launch {
                                    preferencesRepository.deleteAlarm(alarm.id)
                                    rescheduleAlarms(context, preferencesRepository, prefs.alarmMasterEnabled)
                                }
                            },
                            shape = animatedShape,
                            enabled = !isSelectionMode
                        ) {
                            AlarmListItem(
                                alarm = alarm,
                                masterEnabled = prefs.alarmMasterEnabled,
                                shape = animatedShape,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onToggle = { enabled ->
                                    if (isSelectionMode) {
                                        toggleSelection(alarm.id)
                                    } else {
                                        scope.launch {
                                            preferencesRepository.updateAlarm(alarm.copy(enabled = enabled))
                                            rescheduleAlarms(context, preferencesRepository, prefs.alarmMasterEnabled)
                                        }
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        toggleSelection(alarm.id)
                                    } else {
                                        editingAlarm = alarm
                                    }
                                },
                                onLongClick = if (!isSelectionMode) {
                                    {
                                        isSelectionMode = true
                                        selectedAlarmIds = setOf(alarm.id)
                                    }
                                } else null
                            )
                        }
                        if (index < alarmList.lastIndex) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionTopBar(
                    selectedCount = selectedAlarmIds.size,
                    totalCount = alarmList.size,
                    onClose = { exitSelectionMode() },
                    onSelectAll = { selectAll() },
                    onDelete = { showDeleteConfirmation = true },
                    innerPadding = innerPadding
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !isSelectionMode,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = innerPadding.calculateBottomPadding() + 24.dp),
                enter = scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.5f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut()
            ) {
                MediumFloatingActionButton(
                    onClick = { openNewAlarm() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(28.dp),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add Alarm",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        if (showTimePicker) {
            val currentAlarm = editingAlarm ?: newAlarm
            if (currentAlarm != null) {
                TimePickerDialog(
                    initialTime = currentAlarm.timeString,
                    onDismiss = { showTimePicker = false },
                    onTimeSelected = { time ->
                        val parts = time.split(":")
                        val updated = currentAlarm.copy(hour = parts[0].toInt(), minute = parts[1].toInt())
                        if (editingAlarm != null) {
                            editingAlarm = updated
                        } else {
                            newAlarm = updated
                        }
                        showTimePicker = false
                    }
                )
            }
        }

        val currentEditing = editingAlarm
        if (currentEditing != null) {
            AlarmItemSettingsBottomSheet(
                alarmName = currentEditing.name,
                soundUri = currentEditing.soundUri,
                soundEnabled = currentEditing.soundEnabled,
                autoRepeatEnabled = currentEditing.autoRepeatEnabled,
                selectedDays = currentEditing.days,
                vibrateEnabled = currentEditing.vibrateEnabled,
                snoozeDurationMinutes = currentEditing.snoozeDurationMinutes,
                snoozeMaxCount = currentEditing.snoozeMaxCount,
                gradualVolumeEnabled = currentEditing.gradualVolumeEnabled,
                mathChallengeEnabled = currentEditing.mathChallengeEnabled,
                wakeUpAppPackageNames = currentEditing.wakeUpAppPackageNames,
                wakeUpAppDurationSeconds = currentEditing.wakeUpAppDurationSeconds,
                onDismiss = { editingAlarm = null },
                onSave = { name, soundUri, soundEnabled, autoRepeatEnabled, selectedDays, vibrateEnabled, snoozeDurationMinutes, snoozeMaxCount, gradualVolumeEnabled, mathChallengeEnabled, wakeUpAppPackageNames, wakeUpAppDurationSeconds ->
                    scope.launch {
                        preferencesRepository.updateAlarm(
                            currentEditing.copy(
                                name = name,
                                soundUri = soundUri,
                                soundEnabled = soundEnabled,
                                autoRepeatEnabled = autoRepeatEnabled,
                                days = selectedDays,
                                vibrateEnabled = vibrateEnabled,
                                snoozeDurationMinutes = snoozeDurationMinutes,
                                snoozeMaxCount = snoozeMaxCount,
                                gradualVolumeEnabled = gradualVolumeEnabled,
                                mathChallengeEnabled = mathChallengeEnabled,
                                wakeUpAppPackageNames = wakeUpAppPackageNames,
                                wakeUpAppDurationSeconds = wakeUpAppDurationSeconds
                            )
                        )
                        rescheduleAlarms(context, preferencesRepository, prefs.alarmMasterEnabled)
                    }
                    editingAlarm = null
                },
                onTimeClick = { showTimePicker = true },
                alarmTimeText = currentEditing.timeString
            )
        }

        val currentNew = newAlarm
        if (currentNew != null) {
            AlarmItemSettingsBottomSheet(
                alarmName = currentNew.name,
                soundUri = currentNew.soundUri,
                soundEnabled = currentNew.soundEnabled,
                autoRepeatEnabled = currentNew.autoRepeatEnabled,
                selectedDays = currentNew.days,
                vibrateEnabled = currentNew.vibrateEnabled,
                snoozeDurationMinutes = currentNew.snoozeDurationMinutes,
                snoozeMaxCount = currentNew.snoozeMaxCount,
                gradualVolumeEnabled = currentNew.gradualVolumeEnabled,
                mathChallengeEnabled = currentNew.mathChallengeEnabled,
                wakeUpAppPackageNames = currentNew.wakeUpAppPackageNames,
                wakeUpAppDurationSeconds = currentNew.wakeUpAppDurationSeconds,
                onDismiss = { newAlarm = null },
                onSave = { name, soundUri, soundEnabled, autoRepeatEnabled, selectedDays, vibrateEnabled, snoozeDurationMinutes, snoozeMaxCount, gradualVolumeEnabled, mathChallengeEnabled, wakeUpAppPackageNames, wakeUpAppDurationSeconds ->
                    scope.launch {
                        preferencesRepository.addAlarm(
                            currentNew.copy(
                                name = name,
                                soundUri = soundUri,
                                soundEnabled = soundEnabled,
                                autoRepeatEnabled = autoRepeatEnabled,
                                days = selectedDays,
                                vibrateEnabled = vibrateEnabled,
                                snoozeDurationMinutes = snoozeDurationMinutes,
                                snoozeMaxCount = snoozeMaxCount,
                                gradualVolumeEnabled = gradualVolumeEnabled,
                                mathChallengeEnabled = mathChallengeEnabled,
                                wakeUpAppPackageNames = wakeUpAppPackageNames,
                                wakeUpAppDurationSeconds = wakeUpAppDurationSeconds
                            )
                        )
                        rescheduleAlarms(context, preferencesRepository, prefs.alarmMasterEnabled)
                    }
                    newAlarm = null
                },
                onTimeClick = { showTimePicker = true },
                alarmTimeText = currentNew.timeString
            )
        }

        if (showDeleteConfirmation) {
            ConfirmBottomSheet(
                onDismiss = { showDeleteConfirmation = false },
                onConfirm = {
                    deleteSelected()
                    showDeleteConfirmation = false
                },
                leverCount = 3,
                showTimeSelection = false
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    innerPadding: PaddingValues
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = 4.dp
            )
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            label = "SelectScale"
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .scale(scale),
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close selection",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onSelectAll,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = if (selectedCount == totalCount) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                contentDescription = if (selectedCount == totalCount) "Deselect all" else "Select all",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(
            onClick = onDelete,
            enabled = selectedCount > 0,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete selected",
                tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmListItem(
    alarm: AlarmItem,
    masterEnabled: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val enabled = alarm.enabled && masterEnabled
    val today = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) }
    val isActuallyActiveToday = enabled && (alarm.days.isEmpty() || alarm.days.contains(today))
    val isHighlighted = isSelected || isActuallyActiveToday
    val isEnabledButInactive = alarm.enabled && !isActuallyActiveToday
    val haptic = LocalHapticFeedback.current

    val timeUntilMillis = remember(alarm, masterEnabled) {
        if (!enabled) -1L else {
            val now = java.util.Calendar.getInstance()
            val alarmCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, alarm.hour)
                set(java.util.Calendar.MINUTE, alarm.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (alarm.days.isEmpty()) {
                if (alarmCal.before(now)) alarmCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            } else {
                var found = false
                for (i in 0..7) {
                    val dayOfWeek = alarmCal.get(java.util.Calendar.DAY_OF_WEEK)
                    if (alarm.days.contains(dayOfWeek) && alarmCal.after(now)) {
                        found = true
                        break
                    }
                    alarmCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                if (!found) return@remember -1L
            }
            alarmCal.timeInMillis - now.timeInMillis
        }
    }
    val showCountdown = timeUntilMillis in 0 until (12 * 3600 * 1000L)

    val containerColor by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "alarmItemColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onLongClick != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                } else null
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn() + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut() + scaleOut(
                    targetScale = 0.7f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle(it) },
                    modifier = Modifier.padding(end = 12.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isEnabledButInactive) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format("%02d:%02d", alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                                else if (isEnabledButInactive) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    if (showCountdown) {
                        Surface(
                            shape = CircleShape,
                            color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                        ) {
                            val hours = timeUntilMillis / (3600 * 1000)
                            val minutes = (timeUntilMillis % (3600 * 1000)) / (60 * 1000)
                            val countdownText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                            
                            Text(
                                text = "In $countdownText",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (alarm.days.isNotEmpty()) {
                    Text(
                        text = alarm.daysLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else if (isEnabledButInactive) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = expandHorizontally(
                    expandFrom = Alignment.End,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.End,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (alarm.vibrateEnabled) {
                        Icon(
                            imageVector = Icons.Outlined.Vibration,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (alarm.autoRepeatEnabled) {
                        Icon(
                            imageVector = Icons.Outlined.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = expandHorizontally(
                    expandFrom = Alignment.Start,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkHorizontally(
                    shrinkTowards = Alignment.Start,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut()
            ) {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = when {
                            isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
                            isEnabledButInactive -> MaterialTheme.colorScheme.onTertiary
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        checkedTrackColor = when {
                            isHighlighted -> MaterialTheme.colorScheme.primary
                            isEnabledButInactive -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        uncheckedThumbColor = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = if (isHighlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    thumbContent = {
                        val thumbSize by animateDpAsState(
                            targetValue = if (alarm.enabled) 28.dp else 24.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "thumbSize"
                        )
                        val iconColor by animateColorAsState(
                            targetValue = when {
                                isEnabledButInactive -> MaterialTheme.colorScheme.tertiary
                                alarm.enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "iconColor"
                        )
                        Box(
                            modifier = Modifier.size(thumbSize),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = alarm.enabled,
                                transitionSpec = {
                                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleIn(
                                                initialScale = 0.5f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioHighBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ))
                                        .togetherWith(
                                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                    scaleOut(
                                                        targetScale = 0.5f,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                    )
                                        )
                                },
                                label = "switchIcon"
                            ) { isChecked ->
                                Icon(
                                    imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                    tint = iconColor
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

private suspend fun rescheduleAlarms(
    context: android.content.Context,
    preferencesRepository: UserPreferencesRepository,
    masterEnabled: Boolean
) = withContext(Dispatchers.IO) {
    AlarmBroadcastReceiver.cancelAlarm(context)

    if (masterEnabled) {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val alarms = preferencesRepository.parseAlarms(prefs.alarmsJson)
        val enabledAlarms = alarms.filter { it.enabled }
        for (alarm in enabledAlarms) {
            AlarmBroadcastReceiver.scheduleAlarm(context, alarm.timeString)
        }
    }
}
