package com.etrisad.zenith.ui.screens.home

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.HomeUiState
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.ShieldSortType
import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import kotlin.math.abs

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    onSeeFullList: () -> Unit,
    onAppClick: (String) -> Unit,
    onBedtimeClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.etrisad.zenith.data.preferences.UserPreferences()
    )

    val coroutineScope = rememberCoroutineScope()

    HomeScreenContent(
        uiState = uiState,
        preferences = preferences,
        innerPadding = innerPadding,
        onSetTarget = { minutes ->
            coroutineScope.launch {
                userPreferencesRepository.setScreenTimeTarget(minutes)
            }
        },
        formatDuration = viewModel::formatDuration,
        onShieldSortTypeChange = viewModel::onShieldSortTypeChange,
        onGoalSortTypeChange = viewModel::onGoalSortTypeChange,
        onSeeFullList = onSeeFullList,
        onAppClick = onAppClick,
        onBedtimeClick = onBedtimeClick,
        onStatsClick = onSeeFullList,
        onDaySelected = { viewModel.selectDate(it) },
        onRefresh = { viewModel.onRefresh() }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    preferences: com.etrisad.zenith.data.preferences.UserPreferences,
    innerPadding: PaddingValues,
    onSetTarget: (Int) -> Unit,
    formatDuration: (Long) -> String,
    onShieldSortTypeChange: (ShieldSortType) -> Unit,
    onGoalSortTypeChange: (ShieldSortType) -> Unit,
    onSeeFullList: () -> Unit,
    onAppClick: (String) -> Unit,
    onBedtimeClick: () -> Unit,
    onStatsClick: () -> Unit,
    onDaySelected: (Long?) -> Unit,
    onRefresh: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var isManualRefreshing by remember { mutableStateOf(false) }

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60000)
            value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) isManualRefreshing = false
    }

    val bedtimeStatus = rememberBedtimeStatus(preferences)

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            isManualRefreshing = true
            onRefresh()
        },
        state = pullToRefreshState,
        indicator = {
            val isRefreshing = uiState.isLoading
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
        val targetMillis = preferences.screenTimeTargetMinutes * 60 * 1000L
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
                UsageDashboard(
                    totalScreenTime = uiState.totalScreenTime,
                    globalCurrentStreak = uiState.globalCurrentStreak,
                    screenTimeTargetMinutes = preferences.screenTimeTargetMinutes,
                    onSetTarget = onSetTarget,
                    formatDuration = formatDuration,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                UsageTrendsRow(
                    yesterdayScreenTime = uiState.yesterdayScreenTime,
                    percentageChange = uiState.percentageChange,
                    formatDuration = formatDuration
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                val selectedUsage = remember(uiState.dailyUsageHistory, uiState.selectedDateMillis) {
                    uiState.dailyUsageHistory.find { it.date == uiState.selectedDateMillis }
                }
                val isFreshInstall = remember(uiState.dailyUsageHistory) {
                    uiState.dailyUsageHistory.none { !it.isLive && it.hasDatabaseRecord }
                }

                val showSystemWarning = selectedUsage != null && 
                                 !selectedUsage.hasDatabaseRecord && 
                                 selectedUsage.hasSystemData && 
                                 !selectedUsage.isLive

                val showFreshInstallWarning = selectedUsage != null && 
                                             selectedUsage.isLive && 
                                             isFreshInstall

                AnimatedVisibility(
                    visible = showSystemWarning || showFreshInstallWarning,
                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) + fadeOut()
                ) {
                    val containerColor = if (showFreshInstallWarning) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                    }
                    
                    val contentColor = if (showFreshInstallWarning) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    }

                    val icon = if (showFreshInstallWarning) Icons.Outlined.Analytics else Icons.Outlined.Info
                    
                    val message = if (showFreshInstallWarning) {
                        buildAnnotatedString {
                            append("Today's data may be ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("inaccurate")
                            }
                            append(" because Zenith is still collecting your usage patterns. We recommend using Zenith for at least ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("3 days")
                            }
                            append(" for more accurate tracking and insights.")
                        }
                    } else {
                        buildAnnotatedString {
                            append("The data for the selected day is taken directly from the usage system and may ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("not be entirely accurate")
                            }
                            append(". So take it with a ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("grain of salt")
                            }
                            append(".")
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                UsageHistoryCard(
                    history = uiState.dailyUsageHistory,
                    targetMillis = targetMillis,
                    showDatabaseIndicator = preferences.showDatabaseIndicator,
                    selectedDateMillis = uiState.selectedDateMillis,
                    formatDuration = formatDuration,
                    onDaySelected = { usage ->
                        onDaySelected(usage?.date)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                TopAppsSection(
                    topApps = uiState.topApps,
                    formatDuration = formatDuration,
                    expressiveColors = preferences.expressiveColors,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    onSeeFullList = onSeeFullList,
                    onAppClick = { packageName -> onAppClick(packageName) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                QuickActionsSection(
                    bedtimeStatus = bedtimeStatus,
                    onBedtimeClick = onBedtimeClick,
                    onStatsClick = onStatsClick
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                        EmptyShieldsMessage(message = "No active goals. Go to Focus to add one!")
                    }
                }
            } else {
                shieldList(shields = uiState.activeGoals, formatDuration = formatDuration, nowMillis = nowMillis, onAppClick = onAppClick)
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                ShieldSortHeader(
                    title = "Active Shields",
                    currentSortType = uiState.shieldSortType,
                    onSortTypeChange = onShieldSortTypeChange
                )
                Spacer(modifier = Modifier.height(16.dp))
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
                        EmptyShieldsMessage(message = "No active shields. Go to Focus to add one!")
                    }
                }
            } else {
                shieldList(shields = uiState.activeShields, formatDuration = formatDuration, nowMillis = nowMillis, onAppClick = onAppClick)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboard(
    totalScreenTime: Long,
    globalCurrentStreak: Int,
    screenTimeTargetMinutes: Int,
    onSetTarget: (Int) -> Unit,
    formatDuration: (Long) -> String,
    shape: Shape = RoundedCornerShape(32.dp)
) {
    var showTargetSheet by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (screenTimeTargetMinutes > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = if (globalCurrentStreak > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        AnimatedContent(
                            targetState = globalCurrentStreak,
                            transitionSpec = {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                    slideOutVertically { height -> -height } + fadeOut())
                            },
                            label = "StreakAnimation"
                        ) { targetStreak ->
                            Text(
                                text = "$targetStreak",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "Daily Screen Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { showTargetSheet = true },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Set Target",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val hours = totalScreenTime / (1000 * 60 * 60)
            val minutes = (totalScreenTime / (1000 * 60)) % 60
            val seconds = (totalScreenTime / 1000) % 60

            Row(
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy, 
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = hours > 0,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        DigitTicker(hours.toString(), MaterialTheme.typography.displayLarge, MaterialTheme.colorScheme.onSurface, prefix = "h")
                        TickerUnit("h")
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                AnimatedVisibility(
                    visible = minutes > 0 || hours > 0,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        DigitTicker(minutes.toString(), MaterialTheme.typography.displayLarge, MaterialTheme.colorScheme.onSurface, prefix = "m")
                        TickerUnit("m")
                    }
                }

                AnimatedVisibility(
                    visible = hours == 0L && minutes == 0L,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        DigitTicker(seconds.toString(), MaterialTheme.typography.displayLarge, MaterialTheme.colorScheme.onSurface, prefix = "s")
                        TickerUnit("s")
                    }
                }
            }

            val targetMillis = screenTimeTargetMinutes * 60 * 1000L
            val isTargetSet = screenTimeTargetMinutes > 0
            val isExceeded = isTargetSet && totalScreenTime > targetMillis

            if (isTargetSet) {
                AnimatedContent(
                    targetState = isExceeded to (targetMillis - totalScreenTime),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    label = "TargetStatusAnimation"
                ) { (exceeded, remaining) ->
                    Text(
                        text = if (exceeded)
                            "Limit exceeded! Time to rest and reset for tomorrow."
                        else
                            "Target: ${formatDuration(targetMillis)} (${formatDuration(remaining)} left)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val progress = if (isTargetSet) {
                if (isExceeded) 0f
                else ((targetMillis - totalScreenTime).toFloat() / targetMillis).coerceIn(0f, 1f)
            } else {
                0.7f
            }

            val dashboardProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 50f),
                label = "DashboardProgress"
            )

            LinearWavyProgressIndicator(
                progress = { dashboardProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = (if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f)
            )
        }
    }

    if (showTargetSheet) {
        ScreenTimeTargetBottomSheet(
            initialMinutes = screenTimeTargetMinutes,
            onDismiss = { showTargetSheet = false },
            onSave = {
                onSetTarget(it)
                showTargetSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeTargetBottomSheet(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hours by remember { mutableIntStateOf(initialMinutes / 60) }
    var minutes by remember { mutableIntStateOf(initialMinutes % 60) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Daily Screen Time Target",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set a goal to help you stay mindful of your device usage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NumberPicker(
                    value = hours,
                    onValueChange = { hours = it },
                    range = 0..23,
                    label = "Hours"
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                NumberPicker(
                    value = minutes,
                    onValueChange = { minutes = it },
                    range = 0..59,
                    label = "Minutes"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val presets = listOf(120, 240, 360, 480)
                presets.forEach { preset ->
                    FilterChip(
                        selected = (hours * 60 + minutes) == preset,
                        onClick = {
                            hours = preset / 60
                            minutes = preset % 60
                        },
                        label = { Text("${preset / 60}h") },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ZenithGroupedButton {
                if (initialMinutes > 0) {
                    ZenithButtonWeighted(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onSave(0)
                            }
                        },
                        text = "Remove",
                        type = ZenithButtonType.Tonal,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        weight = 1f,
                        isFirst = true,
                        isLast = false
                    )
                }

                ZenithButtonWeighted(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onSave(hours * 60 + minutes)
                        }
                    },
                    text = "Save Target",
                    weight = 1.5f,
                    isFirst = initialMinutes <= 0,
                    isLast = true
                )
            }
        }
    }
}

@Composable
fun UsageTrendsRow(
    yesterdayScreenTime: Long,
    percentageChange: Float,
    formatDuration: (Long) -> String
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
                    if (yesterdayScreenTime > 0) formatDuration(yesterdayScreenTime) else "-",
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
                    if (yesterdayScreenTime > 0) {
                        Icon(
                            imageVector = if (percentageChange >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (percentageChange >= 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val absPercentage = abs(percentageChange).toInt()
                        val percentageText = if (absPercentage > 100) "100" else absPercentage.toString()
                        val suffix = if (absPercentage > 100) "%+" else "%"
                        
                        AnimatedContent(
                            targetState = "$percentageText$suffix",
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                            },
                            label = "TrendAnimation"
                        ) { targetText ->
                            Text(
                                text = targetText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (percentageChange >= 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                            )
                        }
                    } else {
                        Text(
                            "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopAppsSection(
    topApps: List<AppUsageInfo>,
    formatDuration: (Long) -> String,
    expressiveColors: Boolean,
    shape: Shape = RoundedCornerShape(32.dp),
    onSeeFullList: () -> Unit,
    onAppClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { expanded = !expanded },
        shape = shape,
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
                Text(
                    text = "Top Used Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                AnimatedVisibility(
                    visible = !expanded,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                            scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                           scaleOut(targetScale = 0.8f, animationSpec = spring(stiffness = Spring.StiffnessLow))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy((-12).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topApps.take(3).reversed().forEach { app ->
                            val appIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, app.icon) {
                                val icon = app.icon
                                if (icon != null) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        value = icon.toBitmap().asImageBitmap()
                                    }
                                }
                            }
                            val icon = appIcon
                            if (icon != null) {
                                Image(
                                    painter = BitmapPainter(icon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(1.5.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                ) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    topApps.forEachIndexed { index, app ->
                        val itemShape = when {
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }

                        val context = LocalContext.current
                        val appIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, app.packageName) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                value = try {
                                    context.packageManager.getApplicationIcon(app.packageName)?.toBitmap()?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .clickable { onAppClick(app.packageName) },
                            shape = itemShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (expressiveColors) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = formatDuration(app.totalTimeVisible),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                leadingContent = {
                                    val icon = appIcon
                                    if (icon != null) {
                                        Image(
                                            painter = BitmapPainter(icon),
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
                                            Icon(
                                                imageVector = Icons.Outlined.Android,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
                            )
                        }
                        if (index < topApps.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val seeFullListShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(seeFullListShape)
                            .clickable { onSeeFullList() },
                        shape = seeFullListShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "See Full List",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

data class BedtimeStatus(
    val isActive: Boolean,
    val timeRemaining: String,
    val progress: Float
)

@Composable
fun rememberBedtimeStatus(prefs: UserPreferences): BedtimeStatus {
    var status by remember { mutableStateOf(BedtimeStatus(false, "", 1f)) }
    
    LaunchedEffect(prefs) {
        while (true) {
            val now = Calendar.getInstance()
            if (!prefs.bedtimeEnabled) {
                status = BedtimeStatus(false, "", 1f)
            } else {
                val currentDay = now.get(Calendar.DAY_OF_WEEK)
                val yesterdayCalendar = Calendar.getInstance().apply {
                    timeInMillis = now.timeInMillis
                    add(Calendar.DAY_OF_YEAR, -1)
                }
                val yesterdayDay = yesterdayCalendar.get(Calendar.DAY_OF_WEEK)
                
                val startParts = prefs.bedtimeStartTime.split(":")
                val endParts = prefs.bedtimeEndTime.split(":")
                val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
                val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

                val effectiveStartMinutes = startMinutes - 30
                
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                var isActive = false
                if (effectiveStartMinutes <= endMinutes) {
                    if (currentDay in prefs.bedtimeDays) {
                        isActive = currentMinutes in effectiveStartMinutes until endMinutes
                    }
                } else {
                    if (currentDay in prefs.bedtimeDays && currentMinutes >= effectiveStartMinutes) {
                        isActive = true
                    } else if (yesterdayDay in prefs.bedtimeDays && currentMinutes < endMinutes) {
                        isActive = true
                    }
                }

                if (isActive) {
                    val nowAdj = if (currentMinutes < effectiveStartMinutes && effectiveStartMinutes > endMinutes) currentMinutes + 1440 else currentMinutes
                    val startAdj = effectiveStartMinutes
                    val endAdj = if (endMinutes < effectiveStartMinutes) endMinutes + 1440 else endMinutes
                    
                    val totalDuration = endAdj - startAdj
                    val elapsed = nowAdj - startAdj
                    val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                    
                    val remainingMinutes = (endAdj - nowAdj).coerceAtLeast(0)
                    val h = remainingMinutes / 60
                    val m = remainingMinutes % 60
                    val timeStr = if (h > 0) "${h}h ${m}m" else "${m}m"
                    
                    status = BedtimeStatus(true, timeStr, progress)
                } else {
                    status = BedtimeStatus(false, "", 1f)
                }
            }
            delay(10000)
        }
    }
    return status
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickActionsSection(
    bedtimeStatus: BedtimeStatus,
    onBedtimeClick: () -> Unit,
    onStatsClick: () -> Unit
) {
    var showRemainingTime by remember { mutableStateOf(false) }
    
    LaunchedEffect(bedtimeStatus.isActive) {
        if (bedtimeStatus.isActive) {
            while (true) {
                delay(3000)
                showRemainingTime = !showRemainingTime
            }
        } else {
            showRemainingTime = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionCard(
            icon = Icons.Outlined.Alarm,
            label = "Alarm"
        )
        QuickActionCard(
            icon = Icons.Outlined.Timer,
            label = "Pomodoro"
        )
        QuickActionCard(
            icon = Icons.Outlined.Insights,
            label = "Stats",
            onClick = onStatsClick
        )
        QuickActionCard(
            icon = Icons.Outlined.Bedtime,
            label = if (bedtimeStatus.isActive && showRemainingTime) bedtimeStatus.timeRemaining else "Bedtime",
            onClick = onBedtimeClick,
            content = if (bedtimeStatus.isActive) {
                {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    CircularWavyProgressIndicator(
                        progress = { bedtimeStatus.progress },
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        stroke = Stroke(width = with(density) { 3.dp.toPx() }),
                        trackStroke = Stroke(width = with(density) { 3.dp.toPx() }),
                        wavelength = 8.dp
                    )
                }
            } else null
        )
    }
}

@Composable
fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "QuickActionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .height(60.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        if (onClick != null) {
                            onClick()
                        } else {
                            Toast.makeText(context, "Coming Soon", Toast.LENGTH_SHORT).show()
                        }
                    }
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (content != null) {
                    content()
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220, delayMillis = 90)) + 
                 slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { it / 2 })
                .togetherWith(fadeOut(animationSpec = tween(90)) + 
                 slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 2 })
            },
            label = "QuickActionLabelAnimation"
        ) { targetLabel ->
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun LazyListScope.shieldList(
    shields: List<ShieldEntity>,
    formatDuration: (Long) -> String,
    nowMillis: Long,
    onAppClick: (String) -> Unit
) {
    itemsIndexed(
        items = shields,
        key = { _, shield -> shield.packageName }
    ) { index, shield ->
        val shape = when {
            shields.size == 1 -> RoundedCornerShape(24.dp)
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            index == shields.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
        Column(modifier = Modifier.animateItem()) {
            ShieldItem(shield = shield, shape = shape, formatDuration = formatDuration, nowMillis = nowMillis, onAppClick = onAppClick)
            if (index < shields.size - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldItem(
    shield: ShieldEntity,
    shape: RoundedCornerShape,
    formatDuration: (Long) -> String,
    nowMillis: Long,
    onAppClick: (String) -> Unit
) {
    val totalLimitMillis = remember(shield.timeLimitMinutes) { shield.timeLimitMinutes * 60 * 1000L }
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    val context = LocalContext.current
    val appIcon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, shield.packageName) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            value = try {
                context.packageManager.getApplicationIcon(shield.packageName)?.toBitmap()?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onAppClick(shield.packageName) },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ListItem(
                headlineContent = {
                    Text(
                        text = shield.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                supportingContent = {
                    val timeLabel = if (shield.type == FocusType.GOAL) "To Go" else "Left"
                    
                    AnimatedContent(
                        targetState = Triple(usesExhausted, remainingMillis, remainingResetMillis),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                        },
                        label = "ShieldRemainingTimeAnimation"
                    ) { (isExhausted, remaining, resetMillis) ->
                        val mainText = if (isExhausted && resetMillis > 0) {
                            "Uses Exhausted • Reset in ${formatDuration(resetMillis)}"
                        } else {
                            "${formatDuration(remaining)} $timeLabel"
                        }
                        Text(
                            text = mainText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isExhausted && resetMillis > 0) {
                                MaterialTheme.colorScheme.error
                            } else if (shield.type == FocusType.GOAL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                },
                leadingContent = {
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = appIcon
                        if (icon != null) {
                            Image(
                                painter = BitmapPainter(icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                                colorFilter = colorFilter
                            )
                        } else {
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                                )
                            }
                        }

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
                },
                trailingContent = {
                    val percentage = if (shield.type == FocusType.GOAL) {
                        ((1f - progress) * 100).toInt()
                    } else {
                        (progress * 100).toInt()
                    }
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(8.dp)
                    .clip(CircleShape),
                color = indicatorColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun EmptyShieldsMessage(message: String) {
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
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DigitTicker(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = ""
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        text.forEachIndexed { index, char ->
            val key = "${prefix}_${text.length - index}"
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    if (targetState.isDigit() && initialState.isDigit()) {
                        if (targetState > initialState) {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                        } else {
                            (slideInVertically { -it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
                        }
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "DigitTicker_$key",
                contentAlignment = Alignment.BottomStart
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style.copy(
                        letterSpacing = (-2).sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Bottom,
                            trim = LineHeightStyle.Trim.Both
                        )
                    ),
                    fontWeight = FontWeight.Bold,
                    color = color,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun TickerUnit(unit: String) {
    Text(
        text = unit,
        style = MaterialTheme.typography.displayLarge.copy(
            letterSpacing = (-2).sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Bottom,
                trim = LineHeightStyle.Trim.Both
            )
        ),
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun NumberPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        IconButton(
            onClick = { if (value < range.last) onValueChange(value + 1) },
            enabled = value < range.last
        ) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Increase $label")
        }
        Text(
            text = value.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(
            onClick = { if (value > range.first) onValueChange(value - 1) },
            enabled = value > range.first
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Decrease $label")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenExpressivePreview() {
    ZenithTheme(expressiveColors = true) {
        HomeScreenContent(
            uiState = HomeUiState(
                totalScreenTime = 3600000 * 3 + 1800000,
                topApps = listOf(
                    AppUsageInfo("com.instagram", "Instagram", 3600000),
                    AppUsageInfo("com.twitter", "X", 1800000),
                    AppUsageInfo("com.youtube", "YouTube", 900000)
                ),
                activeShields = listOf(
                    ShieldEntity("com.instagram", "Instagram", FocusType.SHIELD, 60, remainingTimeMillis = 30 * 60 * 1000L),
                    ShieldEntity("com.twitter", "X", FocusType.SHIELD, 30, remainingTimeMillis = 5 * 60 * 1000L)
                ),
                activeGoals = listOf(
                    ShieldEntity("com.duolingo", "Duolingo", FocusType.GOAL, 30, remainingTimeMillis = 10 * 60 * 1000L)
                )
            ),
            preferences = com.etrisad.zenith.data.preferences.UserPreferences(expressiveColors = true),
            onSetTarget = {},
            formatDuration = { "3h 30m" },
            onShieldSortTypeChange = {},
            onGoalSortTypeChange = {},
            onSeeFullList = {},
            onAppClick = {},
            onBedtimeClick = {},
            onStatsClick = {},
            onDaySelected = {},
            onRefresh = {},
            innerPadding = PaddingValues()
        )
    }
}
