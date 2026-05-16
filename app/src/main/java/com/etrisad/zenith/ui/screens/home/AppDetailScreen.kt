package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.components.focus.GoalSettingsBottomSheet
import com.etrisad.zenith.ui.components.focus.ShieldSettingsBottomSheet
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: HomeViewModel,
    userPreferencesRepository: com.etrisad.zenith.data.preferences.UserPreferencesRepository,
    innerPadding: PaddingValues
) {
    val uiState by viewModel.appDetailUiState.collectAsState()
    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.etrisad.zenith.data.preferences.UserPreferences()
    )
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(packageName) {
        viewModel.loadAppDetail(packageName)
    }


    val shield = uiState.shieldEntity
    val isFocusActive = shield != null
    val isEffectivelyPaused = shield?.let {
        it.isPaused && (it.pauseEndTimestamp == 0L || nowMillis < it.pauseEndTimestamp)
    } ?: false
    val targetMillis = shield?.timeLimitMinutes?.let { it * 60 * 1000L } ?: 0L

    var selectedHour by remember { mutableStateOf<Int?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                viewModel.loadAppDetail(packageName, forceRefresh = true)
                delay(500)
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        indicator = {
            val scale by animateFloatAsState(
                targetValue = if (isRefreshing) 1f else pullToRefreshState.distanceFraction.coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "LoadingIndicatorScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = innerPadding.calculateTopPadding() + 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                ZenithContainedLoadingIndicator(
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = scale.coerceIn(0f, 1f)
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            )
        ) {
            item {
                AppHeader(
                    appName = uiState.appName,
                    packageName = uiState.packageName,
                    icon = uiState.icon,
                    focusType = uiState.type,
                    isActive = isFocusActive,
                    isPaused = isEffectivelyPaused,
                    pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                    nowMillis = nowMillis,
                    shield = shield
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                UsageCard(
                    title = "Today's Usage",
                    time = viewModel.formatDuration(uiState.todayUsage),
                    targetMillis = targetMillis,
                    currentUsage = uiState.todayUsage,
                    focusType = uiState.type,
                    formatDuration = { viewModel.formatDuration(it) },
                    isActive = isFocusActive,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                UsageTrendsRow(
                    yesterdayTime = viewModel.formatDuration(uiState.yesterdayUsage),
                    percentageChange = uiState.percentageChange,
                    focusType = uiState.type
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                AnimatedVisibility(
                    visible = isFocusActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        StreakCard(
                            currentStreak = uiState.currentStreak,
                            bestStreak = uiState.bestStreak,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            item {
                UsageHistoryCard(
                    history = uiState.usageHistory,
                    targetMillis = targetMillis,
                    focusType = uiState.type,
                    showDatabaseIndicator = preferences.showDatabaseIndicator,
                    formatDuration = { viewModel.formatDuration(it) },
                    onDaySelected = { },
                    title = "History (21 Days)",
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                if (uiState.hourlyUsage.any { it > 0 }) {
                    HourlyUsageChart(
                        hourlyUsage = uiState.hourlyUsage,
                        selectedHour = selectedHour,
                        onHourClick = { selectedHour = if (selectedHour == it) null else it },
                        focusType = uiState.type,
                        formatDuration = { viewModel.formatDuration(it) },
                        bedtimeEnabled = preferences.bedtimeEnabled,
                        bedtimeStartTime = preferences.bedtimeStartTime,
                        bedtimeEndTime = preferences.bedtimeEndTime,
                        bedtimeDays = preferences.bedtimeDays
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item {
                SecondaryStatsRow(
                    averageUsage = viewModel.formatDuration(uiState.averageUsage),
                    totalSessions = uiState.totalSessions,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                PeakHourCard(
                    peakHour = uiState.peakHour,
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = if (!isFocusActive) 24.dp else 8.dp,
                        bottomEnd = if (!isFocusActive) 24.dp else 8.dp
                    )
                )
                if (isFocusActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item {
                AnimatedVisibility(
                    visible = isFocusActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (shield != null) {
                        var showPauseSheet by remember { mutableStateOf(false) }
                        var showDeleteSheet by remember { mutableStateOf(false) }

                        Column {
                            AnimatedContent(
                                targetState = isEffectivelyPaused,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                                        .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.95f))
                                },
                                label = "PauseResumeTransition"
                            ) { isPaused ->
                                if (isPaused) {
                                    ResumeCard(
                                        pauseEndTimestamp = shield.pauseEndTimestamp,
                                        onResume = { viewModel.resumeShield() },
                                        formatDuration = { viewModel.formatDuration(it) },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                        nowMillis = nowMillis
                                    )
                                } else {
                                    PauseShieldCard(
                                        onPauseClick = { showPauseSheet = true },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))

                            DeleteShieldCard(
                                onDelete = {
                                    showDeleteSheet = true
                                },
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            )
                        }

                        if (showDeleteSheet) {
                            ConfirmBottomSheet(
                                onDismiss = { showDeleteSheet = false },
                                onConfirm = {
                                    viewModel.deleteShieldFromDetail()
                                    showDeleteSheet = false
                                },
                                leverCount = 3,
                                showTimeSelection = false
                            )
                        }

                        if (showPauseSheet) {
                            ConfirmBottomSheet(
                                onDismiss = { showPauseSheet = false },
                                onConfirm = { duration ->
                                    viewModel.pauseShield(duration)
                                    showPauseSheet = false
                                },
                                leverCount = 5,
                                showTimeSelection = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isSettingsSheetOpen) {
        val appInfo = AppInfo(uiState.packageName, uiState.appName, uiState.icon)
        val existingShield = uiState.shieldEntity
        val focusType = uiState.type ?: FocusType.SHIELD

        if (focusType == FocusType.GOAL) {
            GoalSettingsBottomSheet(
                appInfo = appInfo,
                usageToday = uiState.todayUsage,
                existingShield = existingShield,
                onDismiss = { viewModel.closeSettingsSheet() },
                onSave = { limit, reminders, goalReminder, isCaller, isSound, soundUri ->
                    viewModel.saveFocus(
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
                usageToday = uiState.todayUsage,
                existingShield = existingShield,
                onDismiss = { viewModel.closeSettingsSheet() },
                onSave = { limit, emergency, reminders, strict, autoQuit, maxUses, refresh, delayApp ->
                    viewModel.saveFocus(
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
}

@Composable
fun AppHeader(
    appName: String,
    @Suppress("UNUSED_PARAMETER") packageName: String,
    icon: android.graphics.drawable.Drawable?,
    focusType: FocusType?,
    isActive: Boolean,
    isPaused: Boolean = false,
    pauseEndTimestamp: Long = 0L,
    nowMillis: Long = System.currentTimeMillis(),
    shield: ShieldEntity? = null
) {
    val nextResetTimestamp = shield?.let { it.lastPeriodResetTimestamp + (it.refreshPeriodMinutes * 60 * 1000L) } ?: 0L
    val remainingResetMillis = (nextResetTimestamp - nowMillis).coerceAtLeast(0L)
    val usesExhausted = shield?.let {
        it.currentPeriodUses >= it.maxUsesPerPeriod && it.maxUsesPerPeriod > 0
    } ?: false

    val isLocked = isPaused || (usesExhausted && remainingResetMillis > 0)

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Android,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                    )
                }
            }

            shield?.let { s ->
                androidx.compose.animation.AnimatedVisibility(
                    visible = s.currentStreak > 0,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                            scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                            scaleOut(spring(stiffness = Spring.StiffnessLow)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalFireDepartment,
                                contentDescription = "Streak",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                            Text(
                                text = "${s.currentStreak}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            if (isLocked) {
                val (badgeProgress, badgeIcon, badgeColor) = when {
                    isPaused -> {
                        val remainingPauseMillis = if (pauseEndTimestamp == 0L) -1L
                        else (pauseEndTimestamp - nowMillis).coerceAtLeast(0L)

                        val initialPauseDuration = remember(pauseEndTimestamp) {
                            val diff = pauseEndTimestamp - System.currentTimeMillis()
                            when {
                                diff <= 3600000L -> 3600000L
                                diff <= 21600000L -> 21600000L
                                else -> 86400000L
                            }
                        }

                        val progress = if (pauseEndTimestamp == 0L) 1f
                        else (remainingPauseMillis.toFloat() / initialPauseDuration).coerceIn(0f, 1f)
                        Triple(progress, Icons.Outlined.Pause, MaterialTheme.colorScheme.secondary)
                    }
                    else -> {
                        val resetPeriodMillis = (shield?.refreshPeriodMinutes ?: 0) * 60 * 1000L
                        val progress = if (resetPeriodMillis > 0) {
                            (remainingResetMillis.toFloat() / resetPeriodMillis).coerceIn(0f, 1f)
                        } else 1f
                        Triple(progress, Icons.Outlined.History, MaterialTheme.colorScheme.error)
                    }
                }

                Box(
                    modifier = Modifier
                        .size(90.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .offset(x = 4.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { badgeProgress },
                                modifier = Modifier.size(26.dp),
                                color = badgeColor,
                                strokeWidth = 2.dp,
                                trackColor = badgeColor.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = badgeColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (usesExhausted && remainingResetMillis > 0) {
            Text(
                text = "Uses Exhausted • Reset in ${remainingResetMillis / 60000}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        AnimatedVisibility(
            visible = isActive && focusType != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (focusType != null) {
                val typeColor = if (focusType == FocusType.SHIELD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = focusType.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageCard(
    title: String,
    time: String,
    targetMillis: Long,
    currentUsage: Long,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    isActive: Boolean,
    shape: androidx.compose.ui.graphics.Shape
) {
    val isTargetSet = targetMillis > 0
    val isExceeded = isTargetSet && focusType == FocusType.SHIELD && currentUsage > targetMillis

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = time,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = isActive && isTargetSet && focusType != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (focusType != null) {
                    val isGoal = focusType == FocusType.GOAL

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isGoal) {
                                if (currentUsage >= targetMillis) "Goal achieved! Keep it up."
                                else "Goal: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} more to go)"
                            } else {
                                if (isExceeded) "Limit exceeded!"
                                else "Limit: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} left)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val progress = if (isGoal) {
                            (currentUsage.toFloat() / targetMillis).coerceIn(0f, 1f)
                        } else {
                            if (isExceeded) 0f
                            else ((targetMillis - currentUsage).toFloat() / targetMillis).coerceIn(0f, 1f)
                        }

                        val dashboardProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 50f),
                            label = "AppUsageProgress"
                        )

                        val progressColor = when {
                            isGoal -> MaterialTheme.colorScheme.tertiary
                            isExceeded -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }

                        LinearWavyProgressIndicator(
                            progress = { dashboardProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageTrendsRow(
    yesterdayTime: String,
    percentageChange: Float,
    focusType: FocusType?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Yesterday",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    yesterdayTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Trend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isUp = percentageChange >= 0
                    val isPositiveTrend = if (focusType == FocusType.GOAL) isUp else !isUp
                    val trendColor = if (isPositiveTrend) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

                    Icon(
                        imageVector = if (isUp) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = trendColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${kotlin.math.abs(percentageChange).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

@Composable
fun StreakCard(
    currentStreak: Int,
    bestStreak: Int,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                    text = "Streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                val sunnyShape = remember {
                    GenericShape { size, _ ->
                        val path = MaterialShapes.Sunny.toPath().asComposePath()
                        val matrix = Matrix()
                        matrix.scale(size.width, size.height)
                        path.transform(matrix)
                        addPath(path)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = sunnyShape,
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
                    text = "days today",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PauseShieldCard(
    onPauseClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PauseCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Pause Shield/Goal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Temporarily disable limits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onPauseClick) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Pause",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ResumeCard(
    pauseEndTimestamp: Long,
    onResume: () -> Unit,
    formatDuration: (Long) -> String,
    shape: androidx.compose.ui.graphics.Shape,
    nowMillis: Long = System.currentTimeMillis()
) {
    val remainingMillis = remember(pauseEndTimestamp, nowMillis) {
        if (pauseEndTimestamp == 0L) -1L
        else (pauseEndTimestamp - nowMillis).coerceAtLeast(0L)
    }

    val resumeTimeStr = remember(pauseEndTimestamp) {
        if (pauseEndTimestamp == 0L) "Manually"
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(pauseEndTimestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Shield Paused",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (remainingMillis == -1L) "Paused indefinitely"
                            else "Resumes in ${formatDuration(remainingMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Button(
                    onClick = onResume,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Resume Now", style = MaterialTheme.typography.labelLarge)
                }
            }
            
            if (pauseEndTimestamp != 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Estimated time: $resumeTimeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun SecondaryStatsRow(
    averageUsage: String,
    totalSessions: Int,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Daily Average",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    averageUsage,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Today's Opens",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Launch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "$totalSessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PeakHourCard(
    peakHour: Int,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    if (peakHour != -1) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Peak Usage Hour",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val amPm = if (peakHour < 12) "AM" else "PM"
                    val displayHour = when {
                        peakHour == 0 -> 12
                        peakHour > 12 -> peakHour - 12
                        else -> peakHour
                    }
                    Text(
                        "Most active around $displayHour $amPm",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyUsageChart(
    hourlyUsage: List<Long>,
    selectedHour: Int?,
    onHourClick: (Int) -> Unit,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    bedtimeEnabled: Boolean,
    bedtimeStartTime: String,
    bedtimeEndTime: String,
    bedtimeDays: Set<Int>
) {
    val maxUsage = remember(hourlyUsage) { hourlyUsage.maxOrNull()?.coerceAtLeast(1L) ?: 1L }
    val accentColor = if (focusType == FocusType.GOAL) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    val isBedtimeHour = remember(bedtimeEnabled, bedtimeStartTime, bedtimeEndTime, bedtimeDays) {
        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_WEEK)
        
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayDay = cal.get(Calendar.DAY_OF_WEEK)
        
        val startH = bedtimeStartTime.split(":").firstOrNull()?.toIntOrNull() ?: 22
        val endH = bedtimeEndTime.split(":").firstOrNull()?.toIntOrNull() ?: 7
        
        val todayActive = bedtimeEnabled && todayDay in bedtimeDays
        val yesterdayActive = bedtimeEnabled && yesterdayDay in bedtimeDays
        
        { hour: Int ->
            if (startH <= endH) {
                todayActive && hour in startH until endH
            } else {
                (todayActive && hour >= startH) || (yesterdayActive && hour < endH)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        text = "Hourly Usage",
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
                        val usage = hourlyUsage.getOrNull(hour) ?: 0L
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("%02d:00 - %02d:00", hour, (hour + 1) % 24),
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
                            text = "Tap a bar for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                
                hourlyUsage.forEachIndexed { hour, usage ->
                    val isSelected = selectedHour == hour
                    val isCurrentHour = hour == currentHour
                    
                    val barHeight = (usage.toFloat() / maxUsage).coerceIn(0.05f, 1f)
                    val animatedHeight by animateFloatAsState(
                        targetValue = barHeight,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "HourlyBarHeight_$hour"
                    )
                    
                    val isBedtime = isBedtimeHour(hour)
                    val baseColor = if (isBedtime) MaterialTheme.colorScheme.tertiary else accentColor

                    val barColor = when {
                        isSelected -> baseColor
                        isCurrentHour -> baseColor.copy(alpha = 0.7f)
                        else -> baseColor.copy(alpha = 0.3f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.5.dp)
                            .fillMaxHeight(animatedHeight)
                            .clip(CircleShape)
                            .background(barColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onHourClick(hour) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DeleteShieldCard(
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Remove from Zenith",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Stop tracking limits for this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
