package com.etrisad.zenith.ui.screens.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.ShieldSortType

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

    // Enhanced Spring Motion animations
    val fabProgress by animateFloatAsState(
        targetValue = if (isFabMenuExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 150f),
        label = "fabProgress"
    )

    val rotation = fabProgress * 45f
    val fabOffset = (fabProgress * 12).dp
    val iconSize = (44 - (fabProgress * 8)).dp // Interpolates between 44.dp and 36.dp

    Scaffold(
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = isFabMenuExpanded,
                // Menurunkan FAB lebih jauh agar lebih rapat ke NavigationBar
                modifier = Modifier.offset(x = 16.dp, y = 40.dp),
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
                // Item 1: Add Shield
                ExpressiveFabMenuItem(
                    index = 0,
                    onClick = {
                        isFabMenuExpanded = false
                        viewModel.selectAppForFocus(null, FocusType.SHIELD)
                        isAppPickerOpen = true
                    },
                    icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                    text = { Text("Add Shield") }
                )

                // Item 2: Add Goal
                ExpressiveFabMenuItem(
                    index = 1,
                    onClick = {
                        isFabMenuExpanded = false
                        viewModel.selectAppForFocus(null, FocusType.GOAL)
                        isAppPickerOpen = true
                    },
                    icon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                    text = { Text("Add Goal") }
                )
            }
        }
    ) { innerPadding ->
        FocusScreenContent(
            uiState = uiState,
            innerPadding = innerPadding,
            onEditShield = { viewModel.editShield(it) },
            onDeleteShield = { viewModel.deleteShield(it) },
            onShieldSortTypeChange = { viewModel.onShieldSortTypeChange(it) },
            onGoalSortTypeChange = { viewModel.onGoalSortTypeChange(it) }
        )

        if (isAppPickerOpen) {
            AppPickerBottomSheet(
                uiState = uiState,
                onDismiss = { isAppPickerOpen = false },
                onAppSelected = {
                    viewModel.selectAppForFocus(it, uiState.selectedFocusType)
                    isAppPickerOpen = false
                },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) }
            )
        }

        if (uiState.isSettingsSheetOpen && uiState.selectedAppForFocus != null) {
            FocusSettingsBottomSheet(
                appInfo = uiState.selectedAppForFocus!!,
                focusType = uiState.selectedFocusType,
                existingShield = (uiState.activeShields + uiState.activeGoals).find { it.packageName == uiState.selectedAppForFocus!!.packageName },
                onDismiss = { viewModel.closeSettingsSheet() },
                onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh, goalReminder, delayApp ->
                    viewModel.saveFocus(limit, emergency, reminders, strict, autoQuit, maxUses, refresh, goalReminder, delayApp)
                }
            )
        }
    }
}

@Composable
fun FocusScreenContent(
    uiState: FocusUiState,
    innerPadding: PaddingValues,
    onEditShield: (ShieldEntity) -> Unit,
    onDeleteShield: (ShieldEntity) -> Unit,
    onShieldSortTypeChange: (ShieldSortType) -> Unit,
    onGoalSortTypeChange: (ShieldSortType) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = 100.dp
        )
    ) {
        item {
            FocusHeader()
        }

        // Active Goals Section
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
                EmptyFocusMessage(message = "No active goals yet")
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

                Column(modifier = Modifier.animateItem()) {
                    ShieldConfigItem(
                        shield = shield,
                        shape = shape,
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) }
                    )
                    if (index < uiState.activeGoals.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // Active Shields Section
        item {
            ShieldSortHeader(
                title = "Active Shields",
                currentSortType = uiState.shieldSortType,
                onSortTypeChange = onShieldSortTypeChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.activeShields.isEmpty()) {
            item {
                EmptyFocusMessage(message = "No active shields yet")
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

                Column(modifier = Modifier.animateItem()) {
                    ShieldConfigItem(
                        shield = shield,
                        shape = shape,
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) }
                    )
                    if (index < uiState.activeShields.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFocusMessage(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f)) + fadeIn()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun FocusHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Manage your focus barriers",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Zenith Shields",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldConfigItem(
    shield: ShieldEntity,
    shape: RoundedCornerShape,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(shield.packageName) {
        try {
            context.packageManager.getApplicationIcon(shield.packageName)
        } catch (_: Exception) {
            null
        }
    }

    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading Icon
                if (appIcon != null) {
                    Image(
                        painter = BitmapPainter(appIcon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Android, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shield.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val timeLabel = if (shield.type == FocusType.GOAL) "To Go" else "Left"
                    Text(
                        text = "${formatRemainingTime(remainingMillis)} $timeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (shield.type == FocusType.GOAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Percentage Text
                val percentage = if (shield.type == FocusType.GOAL) {
                    ((1f - progress) * 100).toInt() // Show progress towards goal
                } else {
                    (progress * 100).toInt() // Show remaining limit
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (shield.currentStreak > 0) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = "Streak",
                            modifier = Modifier.size(18.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Text(
                            text = "${shield.currentStreak}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800),
                            modifier = Modifier.padding(start = 2.dp, end = 12.dp)
                        )
                    }
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Trailing Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
                label = "Progress"
            )

            val indicatorColor = if (shield.type == FocusType.GOAL) {
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
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

fun formatRemainingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPickerBottomSheet(
    uiState: FocusUiState,
    onDismiss: () -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (uiState.selectedFocusType == FocusType.GOAL) "Select Productive App" else "Select App to Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onSearch = { /* Handle search if needed */ },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search apps...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SearchBarDefaults.inputFieldShape,
                colors = SearchBarDefaults.colors(),
                tonalElevation = SearchBarDefaults.TonalElevation,
                shadowElevation = 0.dp,
                windowInsets = SearchBarDefaults.windowInsets,
                content = {}
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoadingApps) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(uiState.installedApps) { index, app ->
                        val shape = when {
                            uiState.installedApps.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            index == uiState.installedApps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(8.dp)
                        }
                        AppListItem(app = app, shape = shape, onClick = { onAppSelected(app) })
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, shape: RoundedCornerShape, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                if (app.icon != null) {
                    Image(
                        painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Android, contentDescription = null)
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSettingsBottomSheet(
    appInfo: AppInfo,
    focusType: FocusType,
    existingShield: ShieldEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean, Boolean, Boolean, Int, Int, Int, Boolean) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = (existingShield?.timeLimitMinutes ?: (if (focusType == FocusType.GOAL) 60 else 30)) / 60,
        initialMinute = (existingShield?.timeLimitMinutes ?: (if (focusType == FocusType.GOAL) 60 else 30)) % 60,
        is24Hour = true
    )
    var maxEmergencyUses by remember { mutableStateOf(existingShield?.maxEmergencyUses?.toString() ?: "3") }
    var remindersEnabled by remember { mutableStateOf(existingShield?.isRemindersEnabled ?: true) }
    var strictModeEnabled by remember { mutableStateOf(existingShield?.isStrictModeEnabled ?: false) }
    var autoQuitEnabled by remember { mutableStateOf(existingShield?.isAutoQuitEnabled ?: false) }
    var isDelayAppEnabled by remember { mutableStateOf(existingShield?.isDelayAppEnabled ?: false) }
    var maxUses by remember { mutableStateOf(existingShield?.maxUsesPerPeriod?.toString() ?: "5") }
    var refreshPeriodMinutes by remember { mutableIntStateOf(existingShield?.refreshPeriodMinutes ?: 60) }
    var goalReminderPeriodMinutes by remember { mutableIntStateOf(existingShield?.goalReminderPeriodMinutes ?: 120) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isGoalDropdownExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val refreshOptions = listOf(
        "Per 30 Menit" to 30,
        "Per 1 Jam" to 60,
        "Per 2 Jam" to 120,
        "Per 6 Jam" to 360,
        "Per 12 Jam" to 720,
        "Per 24 Jam" to 1440
    )

    val goalReminderOptions = listOf(
        "Setiap 1 Jam" to 60,
        "Setiap 2 Jam" to 120,
        "Setiap 4 Jam" to 240,
        "Setiap 8 Jam" to 480,
        "Sekali Sehari" to 1440
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (appInfo.icon != null) {
                    Image(
                        painter = BitmapPainter(appInfo.icon.toBitmap().asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = appInfo.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (focusType == FocusType.GOAL) "Goal Settings" else "Shield Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (focusType == FocusType.GOAL) "Daily Goal Target (HH:MM)" else "Daily Time Limit (HH:MM)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
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
                val presets = if (focusType == FocusType.GOAL) listOf(30, 60, 120, 240) else listOf(15, 30, 60, 120)
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

            if (focusType == FocusType.SHIELD) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            value = refreshOptions.find { it.second == refreshPeriodMinutes }?.first ?: "Custom",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Refresh Period") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
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

                SettingsToggle(
                    title = "Show Reminders",
                    description = "Get notified before limit is reached",
                    checked = remindersEnabled,
                    onCheckedChange = { remindersEnabled = it },
                    icon = Icons.Outlined.NotificationsActive
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggle(
                    title = "Strict Mode",
                    description = "No extensions allowed after limit",
                    checked = strictModeEnabled,
                    onCheckedChange = { strictModeEnabled = it },
                    icon = Icons.Outlined.GppGood
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggle(
                    title = "Auto Quit",
                    description = "Exit app automatically when session ends",
                    checked = autoQuitEnabled,
                    onCheckedChange = { autoQuitEnabled = it },
                    icon = Icons.AutoMirrored.Outlined.ExitToApp
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggle(
                    title = "Delay App",
                    description = "Wait before reopening after being kicked out",
                    checked = isDelayAppEnabled,
                    onCheckedChange = { isDelayAppEnabled = it },
                    icon = Icons.Outlined.History
                )
            } else {
                // GOAL specific settings
                ExposedDropdownMenuBox(
                    expanded = isGoalDropdownExpanded,
                    onExpandedChange = { isGoalDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = goalReminderOptions.find { it.second == goalReminderPeriodMinutes }?.first ?: "Custom",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Remind of Goal") },
                        supportingText = { Text("Zenith will nudge you to open this app") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGoalDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null) }
                    )
                    ExposedDropdownMenu(
                        expanded = isGoalDropdownExpanded,
                        onDismissRequest = { isGoalDropdownExpanded = false }
                    ) {
                        goalReminderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.first) },
                                onClick = {
                                    goalReminderPeriodMinutes = option.second
                                    isGoalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SettingsToggle(
                    title = "Goal Reminders",
                    description = "Receive notifications to reach your daily target",
                    checked = remindersEnabled,
                    onCheckedChange = { remindersEnabled = it },
                    icon = Icons.Outlined.NotificationsActive
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    onSave(
                        timePickerState.hour * 60 + timePickerState.minute,
                        maxEmergencyUses.toIntOrNull() ?: 3,
                        remindersEnabled,
                        strictModeEnabled,
                        autoQuitEnabled,
                        maxUses.toIntOrNull() ?: 5,
                        refreshPeriodMinutes,
                        goalReminderPeriodMinutes,
                        isDelayAppEnabled
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = if (focusType == FocusType.GOAL) "Set Goal" else "Save Shield",
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionButtonMenuScope.ExpressiveFabMenuItem(
    index: Int,
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
            onShieldSortTypeChange = {},
            onGoalSortTypeChange = {}
        )
    }
}
