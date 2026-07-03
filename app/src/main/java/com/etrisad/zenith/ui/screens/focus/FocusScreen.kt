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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
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
import com.etrisad.zenith.ui.components.UninstalledAppCard
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.data.website.WebsiteRepository
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
    scrollBehavior: TopAppBarScrollBehavior,
    onAppClick: (String) -> Unit,
    onDeleteShield: (ShieldEntity) -> Unit,
    onDismissUninstalled: (String) -> Unit
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
            scrollBehavior = scrollBehavior,
            onEditShield = onEditShield,
            onDeleteShield = { pendingDeleteShield = it },
            onDeleteShieldDirect = onDeleteShield,
            onDismissUninstalled = onDismissUninstalled,
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
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onTabChange = { viewModel.setPickerTab(it) },
                onWebsiteSearchChange = { viewModel.onWebsiteSearchQueryChange(it) },
                onWebsiteSelected = {
                    viewModel.confirmWebsite(it)
                    isAppPickerOpen.value = false
                }
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
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onTabChange = { viewModel.setPickerTab(it) },
                onWebsiteSearchChange = { viewModel.onWebsiteSearchQueryChange(it) },
                onWebsiteToggled = { viewModel.confirmWebsiteForSchedule(it) }
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
                    onSave = { limit, reminders, goalReminder, isCaller, isSound, soundUri, period ->
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
                            goalCallerSoundUri = soundUri,
                            limitPeriod = period
                        )
                    }
                )
            } else {
                ShieldSettingsBottomSheet(
                    appInfo = appInfo,
                    usageToday = uiState.selectedAppUsageToday,
                    existingShield = existingShield,
                    onDismiss = { viewModel.closeSettingsSheet() },
                    onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh, delayApp, period ->
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
                            isDelayAppEnabled = delayApp,
                            limitPeriod = period
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
    scrollBehavior: TopAppBarScrollBehavior,
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
    onToggleScheduleSelection: (Long) -> Unit = {},
    onDeleteShieldDirect: (ShieldEntity) -> Unit = {},
    onDismissUninstalled: (String) -> Unit = {}
) {
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60000)
            value = System.currentTimeMillis()
        }
    }

    var activeTab by remember { mutableStateOf(AppTypeTab.APPS) }

    val listState = rememberLazyListState()
    val canScroll by remember {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    LaunchedEffect(canScroll) {
        if (!canScroll) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 16.dp))
        AppTypeTabRow(
            selectedTab = activeTab,
            onTabChange = { activeTab = it }
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(animationSpec = tween(300)) + slideInHorizontally { direction * it / 4 })
                        .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -direction * it / 4 })
                },
                label = "TabContent"
            ) { tab ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 24.dp,
                        bottom = 150.dp
                    )
                ) {

                activeShieldSection(
                    key = "goals",
                title = "Active Goals",
                shields = uiState.activeGoals,
                sortType = uiState.goalSortType,
                onSortTypeChange = onGoalSortTypeChange,
                tabValue = tab,
                isHomeScreen = false,
                onClick = onAppClick,
                nowMillis = nowMillis,
                uninstalledPackages = uiState.uninstalledShields,
                onDeleteShield = onDeleteShieldDirect,
                onDismissUninstalled = onDismissUninstalled,
                isSelectionMode = isSelectionMode,
                selectedShields = selectedShields,
                onEditShield = onEditShield,
                onLongClick = onAppLongClick,
                onToggleSelection = onToggleShieldSelection
            )
    
            item { Spacer(modifier = Modifier.height(24.dp)) }
    
            item(key = "shields_header") {
                ShieldSortHeader(
                    title = "Active Shields",
                    currentSortType = uiState.shieldSortType,
                    onSortTypeChange = onShieldSortTypeChange
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
    
            if (uiState.incentiveLockEnabled && !uiState.incentiveLockGoalsMetToday) {
                item(key = "incentive_lock_encourage") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val tierLabel = when {
                                uiState.incentiveProgress < 0.25f -> "Locked"
                                uiState.incentiveProgress < 0.5f -> "Limited Access"
                                uiState.incentiveProgress < 0.75f -> "Moderate Access"
                                else -> "Almost Unlocked"
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = uiState.incentiveProgress,
                                animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
                                label = "IncentiveProgress"
                            )
                            val percentage = (uiState.incentiveProgress * 100).toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(24.dp))
                                    }
                                    if (uiState.incentiveTier.bonusUses < Int.MAX_VALUE) {
                                        val total = uiState.incentiveTier.bonusUses
                                        Surface(shape = RoundedCornerShape(8.dp), color = if (uiState.bonusUsesLeft > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp, shadowElevation = 2.dp, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 3.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) {
                                                Icon(imageVector = Icons.Outlined.Timer, contentDescription = "Bonus", modifier = Modifier.size(10.dp), tint = if (uiState.bonusUsesLeft > 0) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(text = "${uiState.bonusUsesLeft}/$total", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (uiState.bonusUsesLeft > 0) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Shields: $tierLabel", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = "Complete your app goals to earn shield access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "$percentage%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearWavyProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(10.dp), color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
    
            activeShieldSection(
                key = "shields",
                title = "Active Shields",
                shields = uiState.activeShields,
                sortType = uiState.shieldSortType,
                onSortTypeChange = onShieldSortTypeChange,
                tabValue = tab,
                isHomeScreen = false,
                onClick = onAppClick,
                nowMillis = nowMillis,
                uninstalledPackages = uiState.uninstalledShields,
                onDeleteShield = onDeleteShieldDirect,
                onDismissUninstalled = onDismissUninstalled,
                isSelectionMode = isSelectionMode,
                selectedShields = selectedShields,
                onEditShield = onEditShield,
                onLongClick = onAppLongClick,
                onToggleSelection = onToggleShieldSelection,
                showHeader = false
            )
    
            item { Spacer(modifier = Modifier.height(24.dp)) }
    
            activeScheduleSection(
                key = "schedules",
                schedules = uiState.activeSchedules,
                tabValue = tab,
                isHomeScreen = false,
                onEditSchedule = onEditSchedule,
                nowMillis = nowMillis,
                isSelectionMode = isSelectionMode,
                selectedSchedules = selectedSchedules,
                onDeleteSchedule = onDeleteSchedule,
                onLongClick = { onScheduleLongClick(it) },
                onToggleSelection = onToggleScheduleSelection
            )
        }
            }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                    )
                )
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
            scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
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
