package com.etrisad.zenith.ui.screens.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ScheduleMode
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.ShieldSortType
import com.etrisad.zenith.ui.components.focus.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    innerPadding: PaddingValues,
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAppPickerOpen = remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    var pendingDeleteShield by remember { mutableStateOf<ShieldEntity?>(null) }
    var pendingDeleteSchedule by remember { mutableStateOf<ScheduleEntity?>(null) }

    val fabProgress by animateFloatAsState(
        targetValue = if (isFabMenuExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 150f),
        label = "fabProgress"
    )

    val rotation = fabProgress * 45f
    val fabOffset = (fabProgress * 12).dp
    val iconSize = (44 - (fabProgress * 8)).dp

    val onEditShield = remember(viewModel) { { it: ShieldEntity -> viewModel.editShield(it) } }
    val onEditSchedule = remember(viewModel) { { it: ScheduleEntity -> viewModel.editSchedule(it) } }
    val onShieldSortTypeChange = remember(viewModel) { { it: ShieldSortType -> viewModel.onShieldSortTypeChange(it) } }
    val onGoalSortTypeChange = remember(viewModel) { { it: ShieldSortType -> viewModel.onGoalSortTypeChange(it) } }
    val onToggleShieldSelection = remember(viewModel) { { it: String -> viewModel.toggleShieldSelection(it) } }
    val onToggleScheduleSelection = remember(viewModel) { { it: Long -> viewModel.toggleScheduleSelection(it) } }

    Box(modifier = Modifier.fillMaxSize()) {
        FocusScreenContent(
            uiState = uiState,
            innerPadding = innerPadding,
            onEditShield = onEditShield,
            onDeleteShield = { pendingDeleteShield = it },
            onEditSchedule = onEditSchedule,
            onDeleteSchedule = { pendingDeleteSchedule = it },
            onShieldSortTypeChange = onShieldSortTypeChange,
            onGoalSortTypeChange = onGoalSortTypeChange,
            onAppClick = { pkg ->
                if (uiState.isSelectionMode) {
                    viewModel.toggleShieldSelection(pkg)
                } else {
                    onAppClick(pkg)
                }
            },
            onAppLongClick = { pkg ->
                if (!uiState.isSelectionMode) {
                    viewModel.toggleSelectionMode()
                }
                viewModel.toggleShieldSelection(pkg)
            },
            onScheduleLongClick = { id ->
                if (!uiState.isSelectionMode) {
                    viewModel.toggleSelectionMode()
                }
                viewModel.toggleScheduleSelection(id)
            },
            isSelectionMode = uiState.isSelectionMode,
            selectedShields = uiState.selectedShields,
            selectedSchedules = uiState.selectedSchedules,
            onToggleShieldSelection = onToggleShieldSelection,
            onToggleScheduleSelection = onToggleScheduleSelection
        )

        FloatingActionButtonMenu(
            expanded = isFabMenuExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 110.dp),
            button = {
                ToggleFloatingActionButton(
                    checked = isFabMenuExpanded,
                    onCheckedChange = { isFabMenuExpanded = it },
                    modifier = Modifier
                        .size(80.dp)
                        .offset(x = fabOffset, y = -fabOffset),
                    containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary
                    ),
                    containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(
                        28.dp,
                        50.dp
                    ),
                    containerSize = ToggleFloatingActionButtonDefaults.containerSize(
                        80.dp,
                        56.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = if (isFabMenuExpanded) "Close Menu" else "Add Shield",
                            modifier = Modifier
                                .size(iconSize)
                                .rotate(rotation),
                            tint = if (isFabMenuExpanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        ) {
            ExpressiveFabMenuItem(
                onClick = {
                    isFabMenuExpanded = false
                    viewModel.selectAppForFocus(null, FocusType.SHIELD)
                    isAppPickerOpen.value = true
                },
                icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                text = { Text("Add Shield") }
            )

            ExpressiveFabMenuItem(
                onClick = {
                    isFabMenuExpanded = false
                    viewModel.selectAppForFocus(null, FocusType.GOAL)
                    isAppPickerOpen.value = true
                },
                icon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                text = { Text("Add Goal") }
            )

            ExpressiveFabMenuItem(
                onClick = {
                    isFabMenuExpanded = false
                    viewModel.openSchedulePicker()
                },
                icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                text = { Text("Add Schedule") }
            )
        }

        if (isAppPickerOpen.value) {
            AppPickerBottomSheet(
                uiState = uiState,
                onDismiss = { isAppPickerOpen.value = false },
                onAppSelected = {
                    viewModel.selectAppForFocus(it, uiState.selectedFocusType)
                    isAppPickerOpen.value = false
                },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) }
            )
        }

        if (uiState.isScheduleSettingsOpen) {
            ScheduleSettingsBottomSheet(
                uiState = uiState,
                editingSchedule = uiState.editingSchedule,
                onDismiss = { viewModel.closeScheduleSettings() },
                onSave = { name, start, end, mode, maxEmergency, intercept ->
                    viewModel.saveSchedule(name, start, end, mode, maxEmergency, intercept)
                },
                onEditApps = {
                    viewModel.openSchedulePicker(resetSelection = false)
                }
            )
        }

        if (uiState.isSchedulePickerOpen) {
            MultiAppPickerBottomSheet(
                uiState = uiState,
                onDismiss = { viewModel.closeSchedulePicker() },
                onAppToggled = { viewModel.toggleAppSelectionForSchedule(it) },
                onConfirm = { viewModel.proceedToScheduleSettings() },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) }
            )
        }

        if (uiState.isSettingsSheetOpen && uiState.selectedAppForFocus != null) {
            val appInfo = uiState.selectedAppForFocus!!
            val existingShield = (uiState.activeShields + uiState.activeGoals).find { it.packageName == appInfo.packageName }

            if (uiState.selectedFocusType == FocusType.GOAL) {
                GoalSettingsBottomSheet(
                    appInfo = appInfo,
                    usageToday = uiState.selectedAppUsageToday,
                    existingShield = existingShield,
                    onDismiss = { viewModel.closeSettingsSheet() },
                    onSave = { limit, reminders, goalReminder, isCaller, isSound, soundUri ->
                        viewModel.saveFocus(
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            timeLimitMinutes = limit,
                            maxEmergencyUses = 3,
                            isRemindersEnabled = reminders,
                            isStrictModeEnabled = false,
                            isAutoQuitEnabled = false,
                            maxUsesPerPeriod = 5,
                            refreshPeriodMinutes = 60,
                            goalReminderPeriodMinutes = goalReminder,
                            isDelayAppEnabled = false,
                            isGoalCallerEnabled = isCaller,
                            isGoalCallerSoundEnabled = isSound,
                            goalCallerSoundUri = soundUri
                        )
                    }
                )
            } else {
                ShieldSettingsBottomSheet(
                    appInfo = appInfo,
                    usageToday = uiState.selectedAppUsageToday,
                    existingShield = existingShield,
                    onDismiss = { viewModel.closeSettingsSheet() },
                    onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh, delayApp ->
                        viewModel.saveFocus(
                            packageName = appInfo.packageName,
                            appName = appInfo.appName,
                            timeLimitMinutes = limit,
                            maxEmergencyUses = emergency,
                            isRemindersEnabled = reminders,
                            isStrictModeEnabled = strict,
                            isAutoQuitEnabled = autoQuit,
                            maxUsesPerPeriod = maxUses,
                            refreshPeriodMinutes = refresh,
                            goalReminderPeriodMinutes = 120,
                            isDelayAppEnabled = delayApp
                        )
                    }
                )
            }
        }

        pendingDeleteShield?.let { shield ->
            ConfirmBottomSheet(
                onDismiss = { pendingDeleteShield = null },
                onConfirm = {
                    viewModel.deleteShield(shield)
                    pendingDeleteShield = null
                },
                leverCount = 3,
                showTimeSelection = false
            )
        }

        pendingDeleteSchedule?.let { schedule ->
            ConfirmBottomSheet(
                onDismiss = { pendingDeleteSchedule = null },
                onConfirm = {
                    viewModel.deleteSchedule(schedule)
                    pendingDeleteSchedule = null
                },
                leverCount = 3,
                showTimeSelection = false
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusScreenContent(
    uiState: FocusUiState,
    innerPadding: PaddingValues,
    onEditShield: (ShieldEntity) -> Unit,
    onDeleteShield: (ShieldEntity) -> Unit,
    onEditSchedule: (ScheduleEntity) -> Unit,
    onDeleteSchedule: (ScheduleEntity) -> Unit,
    onShieldSortTypeChange: (ShieldSortType) -> Unit,
    onGoalSortTypeChange: (ShieldSortType) -> Unit,
    onAppClick: (String) -> Unit,
    onAppLongClick: (String) -> Unit = {},
    onScheduleLongClick: (Long) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedShields: Set<String> = emptySet(),
    selectedSchedules: Set<Long> = emptySet(),
    onToggleShieldSelection: (String) -> Unit = {},
    onToggleScheduleSelection: (Long) -> Unit = {}
) {
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60000)
            value = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = 150.dp
        )
    ) {
        item {
            ShieldSortHeader(
                title = "Active Goals",
                currentSortType = uiState.goalSortType,
                onSortTypeChange = onGoalSortTypeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.activeGoals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    EmptyFocusMessage(message = "No active goals yet")
                }
            }
        } else {
            itemsIndexed(
                items = uiState.activeGoals,
                key = { _, shield -> shield.packageName }
            ) { index, shield ->
                val shape = when {
                    uiState.activeGoals.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.activeGoals.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                ) {
                    SwipeableItemContainer(
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) },
                        shape = shape,
                        enabled = !isSelectionMode
                    ) {
                        ShieldConfigItem(
                            shield = shield,
                            shape = shape,
                            onEdit = { onEditShield(shield) },
                            onClick = { onAppClick(shield.packageName) },
                            onLongClick = { onAppLongClick(shield.packageName) },
                            nowMillis = nowMillis,
                            isSelectionMode = isSelectionMode,
                            isSelected = shield.packageName in selectedShields,
                            onToggleSelection = { onToggleShieldSelection(shield.packageName) }
                        )
                    }
                    if (index < uiState.activeGoals.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            ShieldSortHeader(
                title = "Active Shields",
                currentSortType = uiState.shieldSortType,
                onSortTypeChange = onShieldSortTypeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.incentiveLockEnabled && !uiState.incentiveLockGoalsMetToday) {
            item(key = "incentive_lock_encourage") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Shields are currently locked",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Reach 100% on all your app goals to unlock your shielded apps and use them freely!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.activeShields.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    EmptyFocusMessage(message = "No active shields yet")
                }
            }
        } else {
            itemsIndexed(
                items = uiState.activeShields,
                key = { _, shield -> shield.packageName }
            ) { index, shield ->
                val shape = when {
                    uiState.activeShields.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.activeShields.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                ) {
                    SwipeableItemContainer(
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) },
                        shape = shape,
                        enabled = !isSelectionMode
                    ) {
                        ShieldConfigItem(
                            shield = shield,
                            shape = shape,
                            onEdit = { onEditShield(shield) },
                            onClick = { onAppClick(shield.packageName) },
                            onLongClick = { onAppLongClick(shield.packageName) },
                            nowMillis = nowMillis,
                            isSelectionMode = isSelectionMode,
                            isSelected = shield.packageName in selectedShields,
                            onToggleSelection = { onToggleShieldSelection(shield.packageName) }
                        )
                    }
                    if (index < uiState.activeShields.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            Text(
                text = "Active Schedules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        if (uiState.activeSchedules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    EmptyFocusMessage(message = "No active schedules yet")
                }
            }
        } else {
            itemsIndexed(
                items = uiState.activeSchedules,
                key = { _, schedule -> schedule.id }
            ) { index, schedule ->
                val shape = when {
                    uiState.activeSchedules.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == uiState.activeSchedules.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                Column(
                    modifier = Modifier.animateItem(
                        fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                        fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                ) {
                    SwipeableItemContainer(
                        onEdit = { onEditSchedule(schedule) },
                        onDelete = { onDeleteSchedule(schedule) },
                        shape = shape,
                        enabled = !isSelectionMode
                    ) {
                        ScheduleItem(
                            schedule = schedule,
                            shape = shape,
                            onEdit = { if (isSelectionMode) onToggleScheduleSelection(schedule.id) else onEditSchedule(schedule) },
                            onDelete = { onDeleteSchedule(schedule) },
                            onLongClick = { onScheduleLongClick(schedule.id) },
                            isSelectionMode = isSelectionMode,
                            isSelected = schedule.id in selectedSchedules,
                            onToggleSelection = { onToggleScheduleSelection(schedule.id) }
                        )
                    }
                    if (index < uiState.activeSchedules.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableItemContainer(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (!enabled) return@rememberSwipeToDismissBoxState false
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onEdit()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(shape),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection

            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }, label = "SwipeBackground"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.Delete
                SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Edit
                else -> null
            }

            val tint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }

            val isSwiping = direction != SwipeToDismissBoxValue.Settled
            val scale by animateFloatAsState(if (isSwiping) 1.2f else 1f, label = "IconScale")

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleItem(
    schedule: ScheduleEntity,
    shape: RoundedCornerShape,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60000)
            value = System.currentTimeMillis()
        }
    }

    val (isActiveNow, progress) = remember(schedule.startTime, schedule.endTime, schedule.isActive, nowMillis) {
        if (!schedule.isActive) {
            false to 0f
        } else {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
            val currentSeconds = currentMinutes * 60 + calendar.get(java.util.Calendar.SECOND)

            val startMinutes = try {
                val parts = schedule.startTime.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 0 }

            val endMinutes = try {
                val parts = schedule.endTime.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 0 }

            val startSeconds = startMinutes * 60
            val endSeconds = endMinutes * 60

            if (startMinutes <= endMinutes) {
                val isActive = currentMinutes in startMinutes until endMinutes
                val total = (endSeconds - startSeconds).coerceAtLeast(1)
                val elapsed = (currentSeconds - startSeconds).coerceIn(0, total)
                isActive to (elapsed.toFloat() / total)
            } else {
                val isActive = currentMinutes >= startMinutes || currentMinutes < endMinutes
                val total = (24 * 3600 - startSeconds + endSeconds).coerceAtLeast(1)
                val elapsed = if (currentSeconds >= startSeconds) {
                    currentSeconds - startSeconds
                } else {
                    (24 * 3600 - startSeconds) + currentSeconds
                }
                val clampedElapsed = elapsed.coerceIn(0, total)
                isActive to (clampedElapsed.toFloat() / total)
            }
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val secondaryColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onEdit() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isSelectionMode) onToggleSelection() else onLongClick()
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column {
            ListItem(
                headlineContent = { Text(schedule.name, fontWeight = FontWeight.Bold, color = contentColor) },
                supportingContent = {
                    val modeText = schedule.mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    val statusText = if (isActiveNow) "Active" else "Inactive"
                    Column {
                        Text(
                            text = "${schedule.startTime} - ${schedule.endTime} • $modeText • $statusText • ${schedule.packageNames.size} apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActiveNow) {
                                if (isSelected) contentColor else MaterialTheme.colorScheme.primary
                            } else secondaryColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = secondaryColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${schedule.emergencyUseCount}/${schedule.maxEmergencyUses} emergency uses left",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryColor
                            )
                        }
                    }
                },
                leadingContent = {
                    Box(contentAlignment = Alignment.Center) {
                        MultiAppIconGroup(
                            packageNames = schedule.packageNames.take(4),
                            totalCount = schedule.packageNames.size,
                            size = 40.dp
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp),
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp),
                                        tint = MaterialTheme.colorScheme.primaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                trailingContent = {
                    if (!isSelectionMode) {
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit",
                                    tint = if (isSelected) contentColor else MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = if (isSelected) contentColor else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (isActiveNow) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                        .height(8.dp),
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.primary,
                    trackColor = (if (isSelected) contentColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f)
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ShieldConfigItem(
    shield: ShieldEntity,
    shape: RoundedCornerShape,
    onEdit: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    nowMillis: Long,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current

    val isEffectivelyPaused = remember(shield.isPaused, shield.pauseEndTimestamp, nowMillis) {
        shield.isPaused && (shield.pauseEndTimestamp == 0L || nowMillis < shield.pauseEndTimestamp)
    }

    val nextResetTimestamp = remember(shield.lastPeriodResetTimestamp, shield.refreshPeriodMinutes) {
        shield.lastPeriodResetTimestamp + (shield.refreshPeriodMinutes * 60 * 1000L)
    }
    val remainingResetMillis = (nextResetTimestamp - nowMillis).coerceAtLeast(0L)
    val usesExhausted = remember(shield.currentPeriodUses, shield.maxUsesPerPeriod) {
        shield.currentPeriodUses >= shield.maxUsesPerPeriod && shield.maxUsesPerPeriod > 0
    }

    val isLocked = isEffectivelyPaused || (usesExhausted && remainingResetMillis > 0)

    val saturation by animateFloatAsState(
        targetValue = if (isLocked) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "IconSaturation"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isLocked) 0.6f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "IconAlpha"
    )

    val colorFilter = remember(saturation) {
        val matrix = ColorMatrix().apply { setToSaturation(saturation) }
        ColorFilter.colorMatrix(matrix)
    }

    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    val cardColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val secondaryColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isSelectionMode) onToggleSelection() else onLongClick()
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("app-icon://${shield.packageName}")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        alpha = iconAlpha,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                                )
                            }
                        }
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = shield.currentStreak > 0,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                                scaleOut(spring(stiffness = Spring.StiffnessLow)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary
                                )
                                Text(
                                    text = "${shield.currentStreak}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }

                    if (isLocked) {
                        val (badgeProgress, badgeIcon, badgeColor) = when {
                            isEffectivelyPaused -> {
                                val remainingPauseMillis = if (shield.pauseEndTimestamp == 0L) -1L
                                else (shield.pauseEndTimestamp - nowMillis).coerceAtLeast(0L)
                                val initialPauseDuration = remember(shield.pauseEndTimestamp) {
                                    val diff = shield.pauseEndTimestamp - System.currentTimeMillis()
                                    when {
                                        diff <= 3600000L -> 3600000L
                                        diff <= 21600000L -> 21600000L
                                        else -> 86400000L
                                    }
                                }
                                val progress = if (shield.pauseEndTimestamp == 0L) 1f
                                else (remainingPauseMillis.toFloat() / initialPauseDuration).coerceIn(0f, 1f)
                                Triple(progress, Icons.Outlined.Pause, MaterialTheme.colorScheme.secondary)
                            }
                            else -> {
                                val resetPeriodMillis = shield.refreshPeriodMinutes * 60 * 1000L
                                val progress = if (resetPeriodMillis > 0) {
                                    (remainingResetMillis.toFloat() / resetPeriodMillis).coerceIn(0f, 1f)
                                } else 1f
                                Triple(progress, Icons.Outlined.History, MaterialTheme.colorScheme.error)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .offset(x = 2.dp, y = (-2).dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { badgeProgress },
                                    modifier = Modifier.size(14.dp),
                                    color = badgeColor,
                                    strokeWidth = 1.5.dp,
                                    trackColor = badgeColor.copy(alpha = 0.2f),
                                    strokeCap = StrokeCap.Round
                                )
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = badgeColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shield.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    val hours = shield.timeLimitMinutes / 60
                    val mins = shield.timeLimitMinutes % 60
                    val typeText = if (shield.type == FocusType.GOAL) "target" else "limit"
                    val limitText = if (hours > 0) "${hours}h ${mins}m $typeText" else "${mins}m $typeText"

                    val statusText = if (shield.type == FocusType.GOAL) {
                        "Productive"
                    } else {
                        if (shield.isStrictModeEnabled) "Strict" else "Normal"
                    }

                    Text(
                        text = "$limitText • $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor
                    )

                    val timeLabel = if (shield.type == FocusType.GOAL) "To Go" else "Left"
                    val timeText = if (usesExhausted && remainingResetMillis > 0) {
                        "Reset in ${formatRemainingTime(remainingResetMillis)}"
                    } else {
                        "${formatRemainingTime(remainingMillis)} $timeLabel"
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (usesExhausted && remainingResetMillis > 0) {
                            MaterialTheme.colorScheme.error
                        } else if (isSelected) {
                            contentColor
                        } else if (shield.type == FocusType.GOAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val percentage = if (shield.type == FocusType.GOAL) {
                    ((1f - progress) * 100).toInt()
                } else {
                    (progress * 100).toInt()
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = secondaryColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isSelectionMode) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = if (isSelected) contentColor else MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelection() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
                label = "Progress"
            )

            val indicatorColor = if (isSelected) {
                contentColor
            } else if (shield.type == FocusType.GOAL) {
                MaterialTheme.colorScheme.primary
            } else {
                if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            }

            LinearWavyProgressIndicator(
                progress = { if (shield.type == FocusType.GOAL) 1f - animatedProgress else animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = indicatorColor,
                trackColor = if (isSelected) contentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}







@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionButtonMenuScope.ExpressiveFabMenuItem(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit
) {
    FloatingActionButtonMenuItem(
        onClick = onClick,
        icon = icon,
        text = text
    )
}

@Preview(showBackground = true)
@Composable
fun FocusScreenPreview() {
    ZenithTheme {
        FocusScreenContent(
            uiState = FocusUiState(
                activeShields = listOf(
                    ShieldEntity("com.instagram", "Instagram", FocusType.SHIELD, 60),
                    ShieldEntity("com.twitter", "X", FocusType.SHIELD, 30, isStrictModeEnabled = true)
                )
            ),
            innerPadding = PaddingValues(0.dp),
            onEditShield = {},
            onDeleteShield = {},
            onEditSchedule = {},
            onDeleteSchedule = {},
            onShieldSortTypeChange = {},
            onGoalSortTypeChange = {},
            onAppClick = {}
        )
    }
}