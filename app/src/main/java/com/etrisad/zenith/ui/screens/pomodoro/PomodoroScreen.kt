package com.etrisad.zenith.ui.screens.pomodoro

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.focus.CardGroup
import com.etrisad.zenith.ui.components.focus.MultiAppIconGroup
import com.etrisad.zenith.ui.components.focus.PreferenceCategory
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.focus.MultiAppPickerBottomSheet
import com.etrisad.zenith.ui.components.focus.SettingsToggle
import com.etrisad.zenith.ui.components.focus.ZenithDropdown
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.PomodoroViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroScreen(
    viewModel: PomodoroViewModel,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferences by viewModel.userPreferences.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showAppPicker by remember { mutableStateOf(false) }
    var showDisableSheet by remember { mutableStateOf(false) }
    var showPresetSaveSheet by remember { mutableStateOf(false) }
    var showPresetApplySheet by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 220.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") {
                val isLongBreak = uiState.isBreakActive && uiState.currentSessionNumber % uiState.sessionsBeforeLongBreak == 0
                PomodoroStatusProgress(
                    remainingSessionMillis = if (uiState.isSessionActive) uiState.remainingSessionMillis else uiState.sessionDurationMinutes * 60000L,
                    remainingBreakMillis = if (uiState.isSessionActive) uiState.remainingBreakMillis else uiState.breakDurationMinutes * 60000L,
                    isBreakActive = uiState.isBreakActive,
                    isPaused = uiState.isPaused,
                    currentSession = uiState.currentSessionNumber,
                    totalSessions = uiState.sessionCount,
                    isPreview = !uiState.isSessionActive,
                    isLongBreak = isLongBreak
                )
            }

            if (!uiState.isSessionActive) {
                item(key = "settings") {
                    TimeSettingsCard(
                        sessionDuration = uiState.sessionDurationMinutes,
                        breakDuration = uiState.breakDurationMinutes,
                        longBreakDuration = uiState.longBreakDurationMinutes,
                        sessionCount = uiState.sessionCount,
                        sessionsBeforeLongBreak = uiState.sessionsBeforeLongBreak,
                        onSessionDurationChange = { viewModel.setSessionDurationMinutes(it) },
                        onBreakDurationChange = { viewModel.setBreakDurationMinutes(it) },
                        onLongBreakDurationChange = { viewModel.setLongBreakDurationMinutes(it) },
                        onSessionCountChange = { viewModel.setSessionCount(it) },
                        onSessionsBeforeLongBreakChange = { viewModel.setSessionsBeforeLongBreak(it) },
                        containerColor = containerColor
                    )
                }

                item(key = "app_selection") {
                    AppSelectionCard(
                        allowedPackages = uiState.allowedPackages,
                        maxSelection = uiState.maxSelection,
                        blockAllowedApps = uiState.blockAllowedApps,
                        pauseable = uiState.pauseable,
                        presetsJson = uiState.presetsJson,
                        onAppPickerClick = { showAppPicker = true },
                        onBlockAllowedAppsChange = { viewModel.setBlockAllowedApps(it) },
                        onPauseableChange = { viewModel.setPauseable(it) },
                        onMaxSelectionChange = { viewModel.setMaxSelection(it) },
                        onSavePreset = { presetNameInput = ""; showPresetSaveSheet = true },
                        onApplyPreset = { showPresetApplySheet = true },
                        onDeletePreset = { viewModel.deletePreset(it) },
                        onApplyPresetPackages = { viewModel.applyPreset(it) },
                        containerColor = containerColor,
                        viewModel = viewModel
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
                        listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.surface),
                        startY = 0f,
                        endY = 50f
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(bottom = innerPadding.calculateBottomPadding() + 16.dp)
                .navigationBarsPadding()
        ) {
            if (!uiState.isSessionActive) {
                ZenithButton(
                    onClick = { viewModel.startSession() },
                    text = "Start Focus",
                    icon = Icons.Outlined.PlayArrow,
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZenithButton(
                        onClick = {
                            if (uiState.isPaused) viewModel.resumeSession()
                            else viewModel.pauseSession()
                        },
                        text = if (uiState.isPaused) "Resume Session" else "Pause Session",
                        icon = if (uiState.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                        type = if (uiState.isPaused) ZenithButtonType.Filled else ZenithButtonType.Outlined,
                        containerColor = if (uiState.isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = if (uiState.isPaused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                        size = ZenithButtonSize.Large,
                        fillMaxWidth = true
                    )

                    if (uiState.isBreakActive) {
                        ZenithButton(
                            onClick = { viewModel.skipToNextSession() },
                            text = if (uiState.currentSessionNumber >= uiState.sessionCount) "Skip to End" else "Skip Break",
                            icon = Icons.Outlined.SkipNext,
                            type = ZenithButtonType.Text,
                            size = ZenithButtonSize.Large,
                            fillMaxWidth = true
                        )
                    }

                    ZenithButton(
                        onClick = { showDisableSheet = true },
                        text = if (uiState.currentSessionNumber >= uiState.sessionCount) "End Pomodoro Session" else "End Session",
                        icon = Icons.Outlined.Stop,
                        type = ZenithButtonType.Text,
                        size = ZenithButtonSize.Large,
                        contentColor = MaterialTheme.colorScheme.error,
                        fillMaxWidth = true
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        val pm = LocalContext.current.packageManager
        val installedApps = remember {
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = pm.queryIntentActivities(intent, 0)
                activities.map { resolveInfo ->
                    AppInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(pm).toString()
                    )
                }
                    .distinctBy { it.packageName }
                    .sortedBy { it.appName }
            } catch (_: Exception) { emptyList() }
        }

        var searchQuery by remember { mutableStateOf("") }
        var tempSelection by remember { mutableStateOf(uiState.allowedPackages) }

        val pickerUiState = remember(installedApps, searchQuery, tempSelection) {
            FocusUiState(
                installedApps = installedApps,
                topApps = emptyList(),
                searchQuery = searchQuery,
                selectedAppsForSchedule = tempSelection,
                pickerTab = PickerTab.APPS
            )
        }

        MultiAppPickerBottomSheet(
            uiState = pickerUiState,
            onDismiss = { showAppPicker = false },
            onAppToggled = { pkg ->
                tempSelection = if (pkg in tempSelection) {
                    tempSelection - pkg
                } else if (tempSelection.size < uiState.maxSelection) {
                    tempSelection + pkg
                } else {
                    tempSelection
                }
            },
            onConfirm = {
                viewModel.setAllowedPackages(tempSelection)
                showAppPicker = false
            },
            onSearchQueryChange = { searchQuery = it },
            showTabs = false,
            title = "Select Allowed Apps",
            maxSelection = uiState.maxSelection
        )
    }

    if (showDisableSheet) {
        ConfirmBottomSheet(
            onDismiss = { showDisableSheet = false },
            onConfirm = {
                viewModel.endSession()
                showDisableSheet = false
            },
            leverCount = 10,
            puzzleTimeoutSeconds = 10,
            showTimeSelection = false
        )
    }

    if (showPresetSaveSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetSaveSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Save App Selection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Name this app selection preset to easily apply it later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    placeholder = { Text("e.g., Work, Study, Focus") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                ZenithButton(
                    onClick = {
                        if (presetNameInput.isNotBlank()) {
                            viewModel.savePreset(presetNameInput.trim())
                            showPresetSaveSheet = false
                        }
                    },
                    text = "Save Preset",
                    icon = Icons.Outlined.Save,
                    fillMaxWidth = true,
                    enabled = presetNameInput.isNotBlank()
                )
                Spacer(modifier = Modifier.height(8.dp))
                ZenithButton(
                    onClick = { showPresetSaveSheet = false },
                    text = "Cancel",
                    type = ZenithButtonType.Text,
                    fillMaxWidth = true
                )
            }
        }
    }

    if (showPresetApplySheet) {
        val presetsMap = remember(uiState.presetsJson) { viewModel.getPresets() }
        val presetList = remember(presetsMap) { presetsMap.toList() }
        ModalBottomSheet(
            onDismissRequest = { showPresetApplySheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Apply Preset",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (presetList.isEmpty()) {
                    Text("No presets saved yet.", modifier = Modifier.padding(vertical = 32.dp))
                } else {
                    presetList.forEachIndexed { index, pair ->
                        val name = pair.first
                        val packages = pair.second
                        
                        val shape = when {
                            presetList.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            index == presetList.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(8.dp)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable {
                                    viewModel.applyPreset(packages)
                                    showPresetApplySheet = false
                                },
                            shape = shape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${packages.size} apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < presetList.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TimeSettingsCard(
    sessionDuration: Int,
    breakDuration: Int,
    longBreakDuration: Int,
    sessionCount: Int,
    sessionsBeforeLongBreak: Int,
    onSessionDurationChange: (Int) -> Unit,
    onBreakDurationChange: (Int) -> Unit,
    onLongBreakDurationChange: (Int) -> Unit,
    onSessionCountChange: (Int) -> Unit,
    onSessionsBeforeLongBreakChange: (Int) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    var focusText by remember(sessionDuration) { mutableStateOf(sessionDuration.toString()) }
    var breakText by remember(breakDuration) { mutableStateOf(breakDuration.toString()) }
    var longBreakText by remember(longBreakDuration) { mutableStateOf(longBreakDuration.toString()) }

    val timeAdjustmentColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TimeAdjustmentCard(
                modifier = Modifier.weight(1f),
                title = "Focus",
                value = focusText,
                onValueChange = { focusText = it; it.toIntOrNull()?.let(onSessionDurationChange) },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                containerColor = timeAdjustmentColor
            )
            TimeAdjustmentCard(
                modifier = Modifier.weight(1f),
                title = "Break",
                value = breakText,
                onValueChange = { breakText = it; it.toIntOrNull()?.let(onBreakDurationChange) },
                shape = middleShape,
                containerColor = timeAdjustmentColor
            )
            TimeAdjustmentCard(
                modifier = Modifier.weight(1f),
                title = "Long",
                value = longBreakText,
                onValueChange = { longBreakText = it; it.toIntOrNull()?.let(onLongBreakDurationChange) },
                shape = RoundedCornerShape(topEnd = 24.dp, topStart = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                containerColor = timeAdjustmentColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        CardGroup(shape = middleShape, containerColor = containerColor) {
            ToggleOptionRow(
                icon = Icons.Outlined.Repeat,
                title = "Sessions",
                subtitle = "Total sessions to complete",
                options = listOf(2, 3, 4, 6, 8, 10),
                selected = sessionCount,
                onSelect = onSessionCountChange
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        CardGroup(shape = bottomShape, containerColor = containerColor) {
            ToggleOptionRow(
                icon = Icons.Outlined.HourglassEmpty,
                title = "Long Break Every",
                subtitle = "Sessions before a long break",
                options = listOf(2, 3, 4, 6, 8),
                selected = sessionsBeforeLongBreak,
                onSelect = onSessionsBeforeLongBreakChange
            )
        }
    }
}

@Composable
private fun TimeAdjustmentCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            BasicTextField(
                value = value,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) onValueChange(it) },
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ToggleOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val toggleOptions = remember(options) {
            options.map { ZenithToggleOption(text = "$it") }
        }
        val selectedIndex = remember(options, selected) { options.indexOf(selected).coerceAtLeast(0) }
        ZenithToggleButtonGroup(
            options = toggleOptions,
            selectedIndices = setOf(selectedIndex),
            onToggle = { index -> onSelect(options[index]) },
            size = ZenithButtonSize.Small,
            isInsideContainer = true
        )
    }
}

@Composable
fun AppSelectionCard(
    allowedPackages: Set<String>,
    maxSelection: Int,
    blockAllowedApps: Boolean,
    pauseable: Boolean,
    presetsJson: String,
    onAppPickerClick: () -> Unit,
    onBlockAllowedAppsChange: (Boolean) -> Unit,
    onPauseableChange: (Boolean) -> Unit,
    onMaxSelectionChange: (Int) -> Unit,
    onSavePreset: () -> Unit,
    onApplyPreset: () -> Unit,
    onDeletePreset: (String) -> Unit,
    onApplyPresetPackages: (List<String>) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    viewModel: PomodoroViewModel
) {
    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    Column {
        PreferenceCategory(title = "App Protection")
        CardGroup(shape = topShape, containerColor = containerColor) {
            SettingsToggle(
                title = "Block apps behind puzzle",
                description = "Selected apps require a puzzle to access",
                checked = blockAllowedApps,
                onCheckedChange = onBlockAllowedAppsChange,
                icon = Icons.Outlined.Lock,
                shape = topShape
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        CardGroup(shape = bottomShape, containerColor = containerColor) {
            SettingsToggle(
                title = "Pauseable Session",
                description = "Allow pausing to access apps freely",
                checked = pauseable,
                onCheckedChange = onPauseableChange,
                icon = Icons.Outlined.PauseCircle,
                shape = bottomShape
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        PreferenceCategory(title = "App Selection")
        CardGroup(shape = topShape, containerColor = containerColor) {
            val context = LocalContext.current
            val selectedCount = allowedPackages.size
            
            if (selectedCount == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Allowed Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "All apps will be blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val maxOptions = remember { listOf(3, 5, 7, 10, 15) }
                    val maxDropdownOptions = remember { maxOptions.map { "$it apps" to it } }
                    ZenithDropdown(
                        options = maxDropdownOptions,
                        selectedOption = maxSelection,
                        onOptionSelected = onMaxSelectionChange,
                        width = 80.dp
                    )
                }
            } else {
                val topAppNames = remember(allowedPackages) {
                    allowedPackages.take(2).map { pkg ->
                        try {
                            context.packageManager.getApplicationLabel(
                                context.packageManager.getApplicationInfo(pkg, 0)
                            ).toString()
                        } catch (_: Exception) { pkg }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiAppIconGroup(
                        packageNames = allowedPackages.toList().take(4),
                        totalCount = selectedCount,
                        size = 48.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$selectedCount Apps Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                topAppNames.forEachIndexed { index, name ->
                                    if (index > 0) append(", ")
                                    append(name)
                                }
                                if (selectedCount > 2) append(" +${selectedCount - 2} more")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val maxOptions = remember { listOf(3, 5, 7, 10, 15) }
                    val maxDropdownOptions = remember { maxOptions.map { "$it apps" to it } }
                    ZenithDropdown(
                        options = maxDropdownOptions,
                        selectedOption = maxSelection,
                        onOptionSelected = onMaxSelectionChange,
                        width = 80.dp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        CardGroup(shape = bottomShape, containerColor = containerColor) {
            Box(modifier = Modifier.padding(16.dp)) {
                ZenithButton(
                    onClick = onAppPickerClick,
                    text = if (allowedPackages.isEmpty()) "Select apps" else "Change selection",
                    icon = Icons.Outlined.Edit,
                    type = ZenithButtonType.Outlined,
                    size = ZenithButtonSize.Large,
                    fillMaxWidth = true
                )
            }
        }

        val presets = remember(presetsJson) { viewModel.getPresets() }
        if (presets.isNotEmpty() || allowedPackages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PreferenceCategory(title = "Presets")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZenithButton(
                    onClick = onSavePreset,
                    text = "Save current",
                    icon = Icons.Outlined.Save,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Medium,
                    modifier = Modifier.weight(1f)
                )
                if (presets.isNotEmpty()) {
                    ZenithButton(
                        onClick = onApplyPreset,
                        text = "Load preset",
                        icon = Icons.Outlined.Download,
                        type = ZenithButtonType.Tonal,
                        size = ZenithButtonSize.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (presets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val presetList = presets.toList()
                presetList.forEachIndexed { index, pair ->
                    val name = pair.first
                    val packages = pair.second
                    
                    val shape = when {
                        presetList.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        index == presetList.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .clickable { onApplyPresetPackages(packages) },
                        shape = shape,
                        colors = CardDefaults.cardColors(containerColor = containerColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${packages.size} apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { onDeletePreset(name) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (index < presetList.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroStatusProgress(
    remainingSessionMillis: Long,
    remainingBreakMillis: Long,
    isBreakActive: Boolean,
    isPaused: Boolean,
    currentSession: Int,
    totalSessions: Int,
    isPreview: Boolean = false,
    isLongBreak: Boolean = false
) {
    val remaining = if (isBreakActive) remainingBreakMillis else remainingSessionMillis
    val total = if (isBreakActive) {
        if (isPreview) remainingBreakMillis else remainingBreakMillis.coerceAtLeast(1L) 
    } else {
        if (isPreview) remainingSessionMillis else remainingSessionMillis.coerceAtLeast(1L)
    }
    
    val progressValue = if (isPreview) 1f else if (total > 0) (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "PomodoroProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "Pomodoro_wavy")
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } }
    val accentColor = if (isBreakActive) MaterialTheme.colorScheme.tertiary
    else if (isPaused) MaterialTheme.colorScheme.secondary
    else MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            stroke = Stroke(width = strokeWidthPx),
            trackStroke = Stroke(width = strokeWidthPx),
            amplitude = { waveAmplitude },
            wavelength = 48.dp
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when {
                    isBreakActive -> if (isLongBreak) Icons.Outlined.Coffee else Icons.Outlined.FreeBreakfast
                    isPaused -> Icons.Outlined.Pause
                    else -> Icons.Outlined.Timer
                },
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            val minutes = (remaining / 60000).toInt()
            val seconds = ((remaining % 60000) / 1000).toInt()
            Text(
                text = if (isPreview) "${minutes}m" else "${minutes}m ${seconds}s",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = when {
                    isPreview -> "Ready to Focus"
                    isBreakActive -> if (isLongBreak) "Long Break $currentSession/$totalSessions" else "Break $currentSession/$totalSessions"
                    isPaused -> "Paused - Session $currentSession/$totalSessions"
                    else -> "Session $currentSession/$totalSessions"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
