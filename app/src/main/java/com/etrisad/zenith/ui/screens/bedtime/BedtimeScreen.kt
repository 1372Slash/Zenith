package com.etrisad.zenith.ui.screens.bedtime

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModel
import com.etrisad.zenith.ui.viewmodel.HourlyUsageInfo
import com.etrisad.zenith.ui.viewmodel.HourlySortType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeScreen(
    viewModel: BedtimeViewModel,
    innerPadding: PaddingValues
) {
    val preferences by viewModel.userPreferences.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val bedtimeTargetMillis = remember(uiState.bedtimeDurationTotalMillis) { (uiState.bedtimeDurationTotalMillis * 0.1f).toLong() }

    var showAppPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var isOtherHourAppsExpanded by remember(selectedHour) { mutableStateOf(false) }
    var hourlySortType by remember { mutableStateOf(HourlySortType.USAGE_TIME) }
    
    val hourlyAppsData = remember(uiState.hourlyUsage, selectedHour, hourlySortType) {
        val hourData = uiState.hourlyUsage.find { it.hour == selectedHour }
        if (hourData != null && hourData.apps.isNotEmpty()) {
            val sortedApps = when(hourlySortType) {
                HourlySortType.USAGE_TIME -> hourData.apps.sortedByDescending { it.totalTimeVisible }
                HourlySortType.RECENTLY_USED -> hourData.apps.sortedByDescending { it.lastTimeUsed }
            }
            val (regular, low) = sortedApps.partition { it.totalTimeVisible >= 60000L }
            val totalLowTime = low.sumOf { it.totalTimeVisible }
            Triple(hourData, regular, Triple(low, totalLowTime, hourData.hour))
        } else null
    }

    val hourlyGroupTotal = remember(hourlyAppsData, isOtherHourAppsExpanded) {
        val appsCount = if (hourlyAppsData != null) {
            val (_, regular, lowData) = hourlyAppsData
            val (low, totalLowTime, _) = lowData
            val appsListSize = regular.size + (if (totalLowTime > 0) (if (isOtherHourAppsExpanded) 1 + low.size else 1) else 0)
            1 + appsListSize
        } else 0
        2 + appsCount
    }
    
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = LocalTime.now()
            val nextMinute = currentTime.plusMinutes(1).withSecond(0).withNano(0)
            val delayMillis = Duration.between(currentTime, nextMinute).toMillis()
            delay(delayMillis.coerceAtLeast(1000))
        }
    }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item(key = "bedtime_toggle_card") {
            AnimatedVisibility(
                visible = !preferences.bedtimeEnabled,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + 
                        expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) + 
                       shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
                modifier = Modifier.animateItem()
            ) {
                Column {
                    BedtimeToggleCard(
                        enabled = preferences.bedtimeEnabled,
                        onToggle = { 
                            if (preferences.bedtimeEnabled) {
                                showPauseSheet = true
                            } else {
                                viewModel.setBedtimeEnabled(true)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (preferences.bedtimeEnabled) {
            item(key = "bedtime_status_progress") {
                Column(modifier = Modifier.animateItem()) {
                    BedtimeStatusProgress(
                        startTimeStr = preferences.bedtimeStartTime,
                        endTimeStr = preferences.bedtimeEndTime,
                        currentTime = currentTime
                    )
                }
            }
            
            item(key = "time_selection_row") {
                Column(modifier = Modifier.animateItem()) {
                    TimeSelectionRow(
                        startTime = preferences.bedtimeStartTime,
                        endTime = preferences.bedtimeEndTime,
                        onStartTimeClick = { showStartTimePicker = true },
                        onEndTimeClick = { showEndTimePicker = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "days_selection_card") {
                Column(modifier = Modifier.animateItem()) {
                    DaysSelectionCard(
                        selectedDays = preferences.bedtimeDays,
                        onDaysChange = { viewModel.setBedtimeDays(it) },
                        containerColor = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "bedtime_streak_card") {
                Column(modifier = Modifier.animateItem()) {
                    BedtimeStreakCard(
                        currentStreak = preferences.bedtimeCurrentStreak,
                        bestStreak = preferences.bedtimeBestStreak,
                        containerColor = containerColor,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item(key = "usage_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item(key = "bedtime_hourly_chart") {
                Column(modifier = Modifier.animateItem()) {
                    BedtimeHourlyUsageChart(
                        hourlyUsage = uiState.hourlyUsage,
                        selectedHour = selectedHour,
                        dateRange = uiState.bedtimeDateRange,
                        onHourClick = { selectedHour = if (selectedHour == it) null else it },
                        formatDuration = viewModel::formatDuration,
                        index = 0,
                        total = hourlyGroupTotal,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    )
                }
            }

            if (hourlyAppsData != null) {
                val (_, regularHourApps, lowData) = hourlyAppsData
                val (lowUsageHourApps, totalLowUsageHourTime, currentTargetHour) = lowData

                item(key = "hourly_apps_label_$currentTargetHour") {
                    Column(modifier = Modifier.animateItem()) {
                        val locale = LocalConfiguration.current.locales[0]
                        Spacer(modifier = Modifier.height(4.dp))
                        GroupedCard(
                            index = 1,
                            total = hourlyGroupTotal,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Apps used at ${String.format(locale, "%02d:00", currentTargetHour)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                ZenithToggleButtonGroup(
                                    options = listOf(
                                        ZenithToggleOption(text = "Usage", icon = Icons.Outlined.Sort),
                                        ZenithToggleOption(text = "Recent", icon = Icons.Outlined.Schedule)
                                    ),
                                    selectedIndices = setOf(if (hourlySortType == HourlySortType.USAGE_TIME) 0 else 1),
                                    onToggle = { index ->
                                        hourlySortType = if (index == 0) HourlySortType.USAGE_TIME else HourlySortType.RECENTLY_USED
                                    },
                                    modifier = Modifier.widthIn(min = 160.dp, max = 220.dp),
                                    size = ZenithButtonSize.Small,
                                    isScalingEnabled = false,
                                    isShowingCheck = false,
                                    showTextSelected = true,
                                    isFillMaxWidth = false
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = regularHourApps,
                    key = { _, app -> "hourly-$currentTargetHour-${app.packageName}" }
                ) { index, app ->
                    val groupIndex = 2 + index
                    Column(modifier = Modifier.animateItem()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        UsageItem(
                            app = app,
                            type = if (app.packageName in preferences.bedtimeWhitelistedPackages) "ALLOWED" else "OTHER",
                            formatDuration = viewModel::formatDuration,
                            index = groupIndex,
                            total = hourlyGroupTotal,
                            onClick = { },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            accentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (totalLowUsageHourTime > 0) {
                    val otherIndexInGroup = 2 + regularHourApps.size
                    item(key = "hourly_other_header_$currentTargetHour") {
                        Column(modifier = Modifier.animateItem()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            GroupedCard(
                                index = otherIndexInGroup,
                                total = hourlyGroupTotal,
                                onClick = { isOtherHourAppsExpanded = !isOtherHourAppsExpanded },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = "Other Apps",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = "${lowUsageHourApps.size} apps with minimal usage",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = viewModel.formatDuration(totalLowUsageHourTime),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Icon(
                                                imageVector = if (isOtherHourAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp).padding(start = 4.dp),
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    },
                                    leadingContent = {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Android,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }

                    if (isOtherHourAppsExpanded) {
                        itemsIndexed(
                            items = lowUsageHourApps,
                            key = { _, app -> "hourly-low-$currentTargetHour-${app.packageName}" }
                        ) { index, app ->
                            val groupIndex = 2 + regularHourApps.size + 1 + index
                            Column(modifier = Modifier.animateItem()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                UsageItem(
                                    app = app,
                                    type = if (app.packageName in preferences.bedtimeWhitelistedPackages) "ALLOWED" else "OTHER",
                                    formatDuration = viewModel::formatDuration,
                                    index = groupIndex,
                                    total = hourlyGroupTotal,
                                    onClick = { },
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                    accentColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bedtime_history_card") {
                Column(modifier = Modifier.animateItem()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    UsageHistoryCard(
                        history = uiState.bedtimeHistory,
                        targetMillis = bedtimeTargetMillis,
                        focusType = FocusType.SHIELD,
                        formatDuration = { viewModel.formatDuration(it) },
                        title = "History",
                        containerColor = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            item(key = "bedtime_efficiency_card") {
                Column(modifier = Modifier.animateItem()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val limitMillis = (uiState.bedtimeDurationTotalMillis * 0.1f).toLong()
                    val remainingMillis = limitMillis - uiState.bedtimeUsageTotalMillis
                    val isExceeded = remainingMillis < 0
                    
                    BedtimeEfficiencyCard(
                        percentage = uiState.bedtimeUsagePercentage,
                        totalUsage = viewModel.formatDuration(uiState.bedtimeUsageTotalMillis),
                        totalDuration = viewModel.formatDuration(uiState.bedtimeDurationTotalMillis),
                        usageLeft = viewModel.formatDuration(kotlin.math.abs(remainingMillis)),
                        isExceeded = isExceeded,
                        index = hourlyGroupTotal - 1,
                        total = hourlyGroupTotal,
                        containerColor = containerColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item(key = "settings_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item(key = "allowed_apps_card") {
                Column(modifier = Modifier.animateItem()) {
                    AllowedAppsCard(
                        allowedCount = preferences.bedtimeWhitelistedPackages.size,
                        onClick = { showAppPicker = true },
                        containerColor = containerColor,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "feature_notification") {
                Column(modifier = Modifier.animateItem()) {
                    FeatureCard(
                        icon = Icons.Outlined.NotificationsActive,
                        title = "Wind Down Notification",
                        subtitle = "Notify 30 minutes before bedtime",
                        enabled = preferences.bedtimeNotificationEnabled,
                        onToggle = { viewModel.setBedtimeNotificationEnabled(it) },
                        containerColor = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "feature_dnd") {
                Column(modifier = Modifier.animateItem()) {
                    FeatureCard(
                        icon = Icons.Outlined.DoNotDisturbOn,
                        title = "Do Not Disturb",
                        subtitle = "Silence notifications during bedtime",
                        enabled = preferences.bedtimeDndEnabled,
                        onToggle = { viewModel.setBedtimeDndEnabled(it) },
                        containerColor = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item(key = "feature_wind_down") {
                Column(modifier = Modifier.animateItem()) {
                    FeatureCard(
                        icon = Icons.Outlined.Block,
                        title = "Wind Down Restrictions",
                        subtitle = "Restrict app usage 30-min before bedtime",
                        enabled = preferences.bedtimeWindDownEnabled,
                        onToggle = { viewModel.setBedtimeWindDownEnabled(it) },
                        containerColor = containerColor,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        AllowedBedAppsBottomSheet(
            initialWhitelisted = preferences.bedtimeWhitelistedPackages,
            generalWhitelisted = preferences.whitelistedPackages,
            onDismiss = { showAppPicker = false },
            onSave = {
                viewModel.setBedtimeWhitelistedPackages(it)
                showAppPicker = false
            }
        )
    }

    if (showPauseSheet) {
        ConfirmBottomSheet(
            onDismiss = { showPauseSheet = false },
            onConfirm = { _ ->
                viewModel.setBedtimeEnabled(false)
                showPauseSheet = false
            },
            leverCount = 10,
            puzzleTimeoutSeconds = 10,
            showTimeSelection = false
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = preferences.bedtimeStartTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = {
                viewModel.setBedtimeStartTime(it)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = preferences.bedtimeEndTime,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = {
                viewModel.setBedtimeEndTime(it)
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeStatusProgress(
    startTimeStr: String,
    endTimeStr: String,
    currentTime: LocalTime
) {
    val startTime = try { LocalTime.parse(startTimeStr) } catch(_: Exception) { LocalTime.MIDNIGHT }
    val endTime = try { LocalTime.parse(endTimeStr) } catch(_: Exception) { LocalTime.MIDNIGHT }
    
    val isBedtime = if (endTime.isAfter(startTime)) {
        currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
    } else {
        currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
    }

    val locale = LocalConfiguration.current.locales[0]
    val (label, remainingText, progressValue) = remember(currentTime, startTime, endTime, isBedtime, locale) {
        if (isBedtime) {
            val totalDuration = if (endTime.isAfter(startTime)) {
                Duration.between(startTime, endTime)
            } else {
                Duration.ofHours(24).minus(Duration.between(endTime, startTime))
            }
            
            val elapsed = if (currentTime.isAfter(startTime)) {
                Duration.between(startTime, currentTime)
            } else {
                Duration.between(startTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, currentTime))
            }
            
            val remaining = totalDuration.minus(elapsed)
            val hours = remaining.toHours()
            val minutes = remaining.toMinutes() % 60
            
            Triple(
                "Bedtime ends in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
            )
        } else {
            val timeToStart = if (currentTime.isBefore(startTime)) {
                Duration.between(currentTime, startTime)
            } else {
                Duration.between(currentTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, startTime))
            }
            
            val hours = timeToStart.toHours()
            val minutes = timeToStart.toMinutes() % 60
            
            Triple(
                "Starts in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                1f - (timeToStart.toMinutes().toFloat() / (24 * 60).toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "BedtimeStatusProgress"
    )

    val density = LocalDensity.current
    val strokeWidth = remember(density) { with(density) { 4.dp.toPx() } }

    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = if (isBedtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            stroke = Stroke(width = strokeWidth),
            trackStroke = Stroke(width = strokeWidth),
            amplitude = { waveAmplitude },
            wavelength = 48.dp
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isBedtime) Icons.Default.Bedtime else Icons.Default.WbSunny,
                contentDescription = null,
                tint = if (isBedtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { 
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = remainingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeHourlyUsageChart(
    hourlyUsage: List<HourlyUsageInfo>,
    selectedHour: Int?,
    dateRange: String,
    onHourClick: (Int) -> Unit,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int,
    containerColor: Color
) {
    val maxUsage = remember(hourlyUsage) { hourlyUsage.maxOfOrNull { it.usageTimeMillis }?.coerceAtLeast(1L) ?: 1L }
    val accentColor = MaterialTheme.colorScheme.tertiary
    val locale = LocalConfiguration.current.locales[0]

    GroupedCard(index = index, total = total, containerColor = containerColor) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bedtime Activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedContent(
                    targetState = selectedHour,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                         slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                         slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 2 })
                    },
                    label = "SelectedHourText"
                ) { hour ->
                    if (hour != null) {
                        val usage = hourlyUsage.find { it.hour == hour }?.usageTimeMillis ?: 0L
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(locale, "%02d:00 - %02d:00", hour, (hour + 1) % 24),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = formatDuration(usage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                        }
                    } else {
                        Text(
                            text = dateRange,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                hourlyUsage.forEach { hourInfo ->
                    val isSelected = selectedHour == hourInfo.hour
                    val isCurrentHour = hourInfo.isLive
                    
                    val barHeight = (hourInfo.usageTimeMillis.toFloat() / maxUsage).coerceIn(0.05f, 1f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = barHeight,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "BedtimeHourlyBarHeight_${hourInfo.hour}"
                    )
                    
                    val barColor = when {
                        isSelected -> accentColor
                        isCurrentHour -> accentColor.copy(alpha = 0.7f)
                        else -> accentColor.copy(alpha = 0.2f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(animatedHeight)
                            .clip(CircleShape)
                            .background(barColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onHourClick(hourInfo.hour) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (hourlyUsage.isNotEmpty()) {
                    Text(String.format(locale, "%02d:00", hourlyUsage.first().hour), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Time distribution", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(String.format(locale, "%02d:00", (hourlyUsage.last().hour + 1) % 24), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeEfficiencyCard(
    percentage: Float,
    totalUsage: String,
    totalDuration: String,
    usageLeft: String,
    isExceeded: Boolean,
    index: Int,
    total: Int,
    containerColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percentage,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "BedtimeEfficiencyProgress"
    )

    val isGoalMet = !isExceeded
    val statusColor = if (isGoalMet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    GroupedCard(index = index, total = total, containerColor = containerColor) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGoalMet) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Usage Efficiency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isGoalMet) "Great job! Very low usage." else "High usage during bedtime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.1f),
                    stroke = Stroke(width = with(LocalDensity.current) { 4.dp.toPx() }, cap = StrokeCap.Round)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Usage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalUsage,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Period",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = totalDuration,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isExceeded) "Exceeded" else "Left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = usageLeft,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
fun BedtimeToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer 
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "toggleCardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
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
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bedtime Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (enabled) "Active based on schedule" else "Currently disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (enabled) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "thumb_size"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "switch_icon_color"
                    )

                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = enabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            },
                            label = "switch_icon_anim"
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

@Composable
fun TimeSelectionRow(
    startTime: String,
    endTime: String,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            onClick = onStartTimeClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Schedule, 
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Start", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = startTime, 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        Card(
            onClick = onEndTimeClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Alarm, 
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "End", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = endTime, 
                    style = MaterialTheme.typography.displaySmall, 
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun DaysSelectionCard(
    selectedDays: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit,
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday, 
                        null, 
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Repeat Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            DaysSelector(selectedDays = selectedDays, onDaysChange = onDaysChange)
        }
    }
}

@Composable
fun BedtimeStreakCard(
    currentStreak: Int,
    bestStreak: Int,
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Bedtime Streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$bestStreak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Best Streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = MaterialShapes.Sunny.toShape(),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$currentStreak",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "night streak",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DaysSelector(selectedDays: Set<Int>, onDaysChange: (Set<Int>) -> Unit) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    ZenithToggleButtonGroup(
        options = days.map { ZenithToggleOption(text = it) },
        selectedIndices = selectedDays.map { it - 1 }.toSet(),
        onToggle = { index ->
            val dayNum = index + 1
            val newDays = if (dayNum in selectedDays) selectedDays - dayNum else selectedDays + dayNum
            onDaysChange(newDays)
        },
        isMultiSelect = true,
        size = ZenithButtonSize.Medium,
        isInsideContainer = true,
        isShowingCheck = false
    )
}

@Composable
fun GroupedCard(
    index: Int,
    total: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    val shape = when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = { content() }
    )
}

@Composable
fun UsageItem(
    app: AppUsageInfo,
    type: String?,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    accentColor: Color = MaterialTheme.colorScheme.secondary
) {
    GroupedCard(index = index, total = total, onClick = onClick, modifier = modifier, containerColor = containerColor) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val badgeColor = when (type) {
                        "GOAL" -> MaterialTheme.colorScheme.tertiary
                        "SHIELD" -> MaterialTheme.colorScheme.primary
                        "ALLOWED" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    val badgeText = when (type) {
                        "GOAL" -> "Goal"
                        "SHIELD" -> "Shield"
                        "ALLOWED" -> "Allowed"
                        else -> "Other"
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.1f),
                        contentColor = badgeColor,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    text = formatDuration(app.totalTimeVisible),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            },
            leadingContent = {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("app-icon://${app.packageName}")
                        .crossfade(500)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Android,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@Composable
fun AllowedAppsCard(
    allowedCount: Int, 
    onClick: () -> Unit, 
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp)
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Allowed Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "$allowedCount apps bypass bedtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    containerColor: Color,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (enabled) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "thumb_size"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "switch_icon_color"
                    )

                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = enabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            },
                            label = "switch_icon_anim"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val parts = initialTime.split(":")
    val timeState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val locale = LocalConfiguration.current.locales[0]
            TextButton(onClick = {
                val time = String.format(locale, "%02d:%02d", timeState.hour, timeState.minute)
                onTimeSelected(time)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            MaterialTheme(
                typography = MaterialTheme.typography.copy(
                    displayLarge = MaterialTheme.typography.headlineLarge
                )
            ) {
                TimePicker(state = timeState)
            }
        }
    )
}
