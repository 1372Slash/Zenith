package com.etrisad.zenith.ui.screens.deepfocus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.focus.CardGroup
import com.etrisad.zenith.ui.components.focus.MultiAppPickerBottomSheet
import com.etrisad.zenith.ui.components.focus.SettingsToggle
import com.etrisad.zenith.ui.components.focus.ZenithDropdown
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.DeepFocusViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeepFocusScreen(
    viewModel: DeepFocusViewModel,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferences by viewModel.userPreferences.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showAppPicker by remember { mutableStateOf(false) }
    var showDisableSheet by remember { mutableStateOf(false) }
    var showBreakSheet by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "toggle") {
            AnimatedVisibility(
                visible = !uiState.isSessionActive,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                       shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
            ) {
                Column {
                    DeepFocusToggleCard(
                        enabled = false,
                        onToggle = { viewModel.startSession() }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        if (uiState.isSessionActive) {
            item(key = "status") {
                DeepFocusStatusProgress(
                    remainingSessionMillis = uiState.remainingSessionMillis,
                    remainingBreakMillis = uiState.remainingBreakMillis,
                    isBreakActive = uiState.isBreakActive
                )
            }

            item(key = "settings") {
                val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                val middleShape = RoundedCornerShape(8.dp)
                val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                Column {
                    CardGroup(shape = topShape, containerColor = containerColor) {
                        SettingsToggle(
                            title = "Block allowed apps behind puzzle",
                            description = "Even your selected apps require a puzzle to access",
                            checked = uiState.blockAllowedApps,
                            onCheckedChange = { viewModel.setBlockAllowedApps(it) },
                            icon = Icons.Outlined.Lock
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    CardGroup(shape = middleShape, containerColor = containerColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Break duration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val breakOptions = remember { listOf(5, 10, 15, 20, 30, 45, 60) }
                            val breakDropdownOptions = remember { breakOptions.map { "${it}m" to it } }
                            ZenithDropdown(
                                options = breakDropdownOptions,
                                selectedOption = uiState.breakDurationMinutes,
                                onOptionSelected = { viewModel.setBreakDurationMinutes(it) },
                                width = 100.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    CardGroup(shape = middleShape, containerColor = containerColor) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    "Max apps",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val maxOptions = remember { listOf(3, 5, 7, 10, 15) }
                            val maxDropdownOptions = remember { maxOptions.map { "$it apps" to it } }
                            ZenithDropdown(
                                options = maxDropdownOptions,
                                selectedOption = uiState.maxSelection,
                                onOptionSelected = { viewModel.setMaxSelection(it) },
                                width = 100.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    CardGroup(shape = bottomShape, containerColor = containerColor) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        if (uiState.allowedPackages.isEmpty()) "No apps selected. All apps will be blocked."
                                        else "${uiState.allowedPackages.size}/${uiState.maxSelection} apps selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            ZenithButton(
                                onClick = { showAppPicker = true },
                                text = if (uiState.allowedPackages.isEmpty()) "Select apps" else "Change apps",
                                icon = Icons.Outlined.Edit,
                                type = ZenithButtonType.Outlined,
                                size = ZenithButtonSize.Large,
                                fillMaxWidth = true
                            )
                        }
                    }
                }
            }

            if (uiState.isBreakActive) {
                item(key = "extend_break") {
                    ZenithButton(
                        onClick = { showBreakSheet = true },
                        text = "Extend break via puzzle",
                        icon = Icons.Outlined.FreeBreakfast,
                        size = ZenithButtonSize.Large,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        fillMaxWidth = true
                    )
                }
            }

            item(key = "end_session") {
                Spacer(modifier = Modifier.height(8.dp))
                ZenithButton(
                    onClick = { showDisableSheet = true },
                    text = "End Deep Focus Session",
                    icon = Icons.Outlined.Stop,
                    type = ZenithButtonType.Text,
                    size = ZenithButtonSize.Large,
                    contentColor = MaterialTheme.colorScheme.error,
                    fillMaxWidth = true
                )
            }
        }
    }

    if (showAppPicker) {
        val pm = LocalContext.current.packageManager
        val installedApps = remember {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
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

    if (showBreakSheet) {
        ConfirmBottomSheet(
            onDismiss = { showBreakSheet = false },
            onConfirm = {
                viewModel.startBreak()
                showBreakSheet = false
            },
            leverCount = 10,
            puzzleTimeoutSeconds = 10,
            showTimeSelection = false
        )
    }
}

@Composable
fun DeepFocusToggleCard(enabled: Boolean, onToggle: () -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "toggleCardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Deep Focus",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Block distractions and stay in the zone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ZenithButton(
                onClick = onToggle,
                text = "Start",
                size = ZenithButtonSize.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeepFocusStatusProgress(
    remainingSessionMillis: Long,
    remainingBreakMillis: Long,
    isBreakActive: Boolean
) {
    val remaining = if (isBreakActive) remainingBreakMillis else remainingSessionMillis
    val total = if (isBreakActive) remainingSessionMillis else (remainingSessionMillis + remainingBreakMillis).coerceAtLeast(1L)
    val progressValue = if (total > 0) (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "DeepFocusProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "deepFocus_wavy")
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
    val accentColor = if (isBreakActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

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
                imageVector = if (isBreakActive) Icons.Outlined.FreeBreakfast else Icons.Outlined.Visibility,
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
                text = "${minutes}m ${seconds}s",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = if (isBreakActive) "Break remaining" else "Session remaining",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}