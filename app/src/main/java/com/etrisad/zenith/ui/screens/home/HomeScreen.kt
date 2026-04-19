package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.theme.ZenithTheme
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.HomeUiState
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.ShieldSortType

import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

import androidx.compose.ui.text.style.TextAlign
import com.etrisad.zenith.data.preferences.UserPreferencesRepository

import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    onSeeFullList: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val preferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.etrisad.zenith.data.preferences.UserPreferences(
            com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM,
            true,
            false,
            0,
            60,
            30
        )
    )

    val coroutineScope = rememberCoroutineScope()

    HomeScreenContent(
        uiState = uiState,
        preferences = preferences,
        onSetTarget = { minutes ->
            coroutineScope.launch {
                userPreferencesRepository.setScreenTimeTarget(minutes)
            }
        },
        formatDuration = viewModel::formatDuration,
        onShieldSortTypeChange = viewModel::onShieldSortTypeChange,
        onGoalSortTypeChange = viewModel::onGoalSortTypeChange,
        onSeeFullList = onSeeFullList
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    preferences: com.etrisad.zenith.data.preferences.UserPreferences,
    onSetTarget: (Int) -> Unit,
    formatDuration: (Long) -> String,
    onShieldSortTypeChange: (ShieldSortType) -> Unit,
    onGoalSortTypeChange: (ShieldSortType) -> Unit,
    onSeeFullList: () -> Unit
) {
    Scaffold { innerPadding ->
        val targetMillis = preferences.screenTimeTargetMinutes * 60 * 1000L
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
                WelcomeHeader()
            }
            item {
                UsageDashboard(
                    uiState = uiState,
                    preferences = preferences,
                    onSetTarget = onSetTarget,
                    formatDuration = formatDuration,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                UsageTrendsRow(
                    uiState = uiState,
                    formatDuration = formatDuration
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                UsageHistoryCard(
                    history = uiState.dailyUsageHistory,
                    targetMillis = targetMillis,
                    formatDuration = formatDuration,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                TopAppsSection(
                    topApps = uiState.topApps,
                    formatDuration = formatDuration,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    onSeeFullList = onSeeFullList
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
                    EmptyShieldsMessage(message = "No active goals. Go to Focus to add one!")
                }
            } else {
                shieldList(shields = uiState.activeGoals, formatDuration = formatDuration)
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
                    EmptyShieldsMessage(message = "No active shields. Go to Focus to add one!")
                }
            } else {
                shieldList(shields = uiState.activeShields, formatDuration = formatDuration)
            }
        }
    }
}

@Composable
fun WelcomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Welcome Back,",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Zenith User",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboard(
    uiState: HomeUiState,
    preferences: com.etrisad.zenith.data.preferences.UserPreferences,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Spacer(modifier = Modifier.width(32.dp)) // Equalizer for the icon
                Text(
                    text = "Daily Screen Time",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { showTargetSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Set Target",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = formatDuration(uiState.totalScreenTime),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val targetMillis = preferences.screenTimeTargetMinutes * 60 * 1000L
            val isTargetSet = preferences.screenTimeTargetMinutes > 0
            val isExceeded = isTargetSet && uiState.totalScreenTime > targetMillis

            if (isTargetSet) {
                Text(
                    text = if (isExceeded)
                        "Limit exceeded! Time to rest and reset for tomorrow."
                    else
                        "Target: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - uiState.totalScreenTime)} left)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val progress = if (isTargetSet) {
                if (isExceeded) 0f
                else ((targetMillis - uiState.totalScreenTime).toFloat() / targetMillis).coerceIn(0f, 1f)
            } else {
                0.7f // Default placeholder progress
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
            initialMinutes = preferences.screenTimeTargetMinutes,
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
    var hours by remember { mutableIntStateOf(initialMinutes / 60) }
    var minutes by remember { mutableIntStateOf(initialMinutes % 60) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
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

            Button(
                onClick = { onSave(hours * 60 + minutes) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Target")
            }

            if (initialMinutes > 0) {
                TextButton(
                    onClick = { onSave(0) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Remove Target", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun UsageTrendsRow(
    uiState: HomeUiState,
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
                    formatDuration(uiState.yesterdayScreenTime),
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
                    Icon(
                        imageVector = if (uiState.percentageChange >= 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (uiState.percentageChange >= 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${abs(uiState.percentageChange).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.percentageChange >= 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
fun UsageHistoryCard(
    history: List<com.etrisad.zenith.ui.viewmodel.DailyUsage>,
    targetMillis: Long,
    formatDuration: (Long) -> String,
    shape: Shape
) {
    var selectedUsage by remember { mutableStateOf<com.etrisad.zenith.ui.viewmodel.DailyUsage?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last 21 Days",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                AnimatedContent(
                    targetState = selectedUsage,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                slideInVertically { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    slideOutVertically { -it / 2 })
                    },
                    label = "SelectedUsageAnim"
                ) { usage ->
                    if (usage != null && dateFormat.format(usage.date) != todayDate) {
                        Text(
                            text = formatDuration(usage.totalTime),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            UsageGraph(
                history = history,
                targetMillis = targetMillis,
                onDaySelected = { selectedUsage = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageGraph(
    history: List<com.etrisad.zenith.ui.viewmodel.DailyUsage>,
    targetMillis: Long,
    onDaySelected: (com.etrisad.zenith.ui.viewmodel.DailyUsage?) -> Unit
) {
    val sunnyShape = remember {
        GenericShape { size, _ ->
            val path = MaterialShapes.Sunny.toPath().asComposePath()
            val matrix = Matrix()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            addPath(path)
        }
    }
    val pages = remember(history) { history.chunked(7) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount }, initialPage = (pageCount - 1).coerceAtLeast(0))

    // Determine max usage for the current visible page (Adaptive Scale)
    val currentPageData = if (pagerState.currentPage < pages.size) pages[pagerState.currentPage] else emptyList()
    val maxUsageRaw = (currentPageData.maxOfOrNull { it.totalTime } ?: 0L)
        .coerceAtLeast(targetMillis)
        .coerceAtLeast(1L)

    // Scale to nearest hour for cleaner indicators
    val maxUsageHours = (maxUsageRaw / (1000 * 60 * 60)).coerceAtLeast(1) + 1
    val maxUsage = maxUsageHours * 1000 * 60 * 60L

    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }

    var selectedDate by remember { mutableStateOf<Long?>(null) }

    // Trigger for opening animation
    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animateTrigger = true
    }

    // Reset selection when paging
    LaunchedEffect(pagerState.currentPage) {
        selectedDate = null
        onDaySelected(null)
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            // Background grid lines (Indicators)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) { i ->
                    val hourLabel = (maxUsageHours * (3 - i) / 3)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${hourLabel}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.width(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Goal line
            if (targetMillis > 0) {
                val goalRatio = (targetMillis.toFloat() / maxUsage).coerceIn(0f, 1f)
                val goalLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp, start = 32.dp)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val y = size.height * (1f - goalRatio)
                        drawLine(
                            color = goalLineColor,
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            // Pager for Bars
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 32.dp)
            ) { pageIndex ->
                val pageData = if (pageIndex < pages.size) pages[pageIndex] else emptyList()

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    pageData.forEach { usage ->
                        val isSelected = selectedDate == usage.date
                        val targetHeight = if (animateTrigger) (usage.totalTime.toFloat() / maxUsage).coerceIn(0.01f, 1f) else 0.01f
                        val animatedHeight by animateFloatAsState(
                            targetValue = targetHeight,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BarHeight"
                        )

                        val isGoalAchieved = targetMillis > 0 && usage.totalTime <= targetMillis
                        val isToday = dateFormat.format(usage.date) == todayDate
                        
                        val baseColor = if (isGoalAchieved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        val barColor = when {
                            isSelected -> baseColor
                            isToday -> baseColor.copy(alpha = 0.8f)
                            else -> baseColor.copy(alpha = 0.4f)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedDate = if (isSelected) null else usage.date
                                    onDaySelected(if (isSelected) null else usage)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .fillMaxHeight(animatedHeight)
                                    .clip(CircleShape)
                                    .background(barColor),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                if (isGoalAchieved && animatedHeight > 0.15f) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .size(28.dp)
                                            .clip(sunnyShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fixed Labels (Days) at the bottom, matching the pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp)
                    .height(20.dp),
                userScrollEnabled = false
            ) { pageIndex ->
                val pageData = if (pageIndex < pages.size) pages[pageIndex] else emptyList()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    pageData.forEach { usage ->
                                Text(
                                text = dayFormat.format(usage.date).first().toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pager Indicators (Dots)
        Row(
            Modifier
                .height(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(6.dp)
                )
            }
        }
    }
}

@Composable
fun TopAppsSection(
    topApps: List<AppUsageInfo>,
    formatDuration: (Long) -> String,
    shape: Shape = RoundedCornerShape(32.dp),
    onSeeFullList: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
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

                // Stacked app icons
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
                            if (app.icon != null) {
                                Image(
                                    painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
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
                            topApps.size == 1 -> RoundedCornerShape(24.dp)
                            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            else -> RoundedCornerShape(8.dp)
                        }

                        val context = LocalContext.current
                        val appIcon = remember(app.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(app.packageName)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = itemShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // See Full List Card at the bottom of the group
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSeeFullList() },
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
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
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.shieldList(
    shields: List<ShieldEntity>,
    formatDuration: (Long) -> String
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
            ShieldItem(shield = shield, shape = shape, formatDuration = formatDuration)
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
    formatDuration: (Long) -> String
) {
    // Simulated remaining time for UI
    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    val context = LocalContext.current
    val appIcon = remember(shield.packageName) {
        try {
            context.packageManager.getApplicationIcon(shield.packageName)
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "${formatDuration(remainingMillis)} $timeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (shield.type == FocusType.GOAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        }
                    )
                },
                leadingContent = {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
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
            color = MaterialTheme.colorScheme.primary,
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
fun HomeScreenPreview() {
    ZenithTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                totalScreenTime = 3600000 * 3 + 1800000, // 3h 30m
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
            preferences = com.etrisad.zenith.data.preferences.UserPreferences(
                com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM,
                true,
                false,
                180, // 3h target
                60,
                30
            ),
            onSetTarget = {},
            formatDuration = { "3h 30m" },
            onShieldSortTypeChange = {},
            onGoalSortTypeChange = {},
            onSeeFullList = {}
        )
    }
}
