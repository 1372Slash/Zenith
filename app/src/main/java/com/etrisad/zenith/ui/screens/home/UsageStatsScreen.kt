package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.SnapshotSection
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.HourlySortType
import com.etrisad.zenith.ui.viewmodel.HourlyUsageInfo
import androidx.compose.material.icons.outlined.Sort
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageStatsScreen(
    viewModel: HomeViewModel,
    innerPadding: PaddingValues,
    showDatabaseIndicator: Boolean,
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isManualRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) isManualRefreshing = false
    }
    
    var selectedHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var highlightedCategory by remember { mutableStateOf<String?>(null) } 
    
    var isOtherAppsExpanded by rememberSaveable { mutableStateOf(false) }
    var isOtherHourAppsExpanded by rememberSaveable(selectedHour) { mutableStateOf(false) }

    val (regularApps, lowUsageApps, totalLowUsageTime) = remember(uiState.allAppsUsage) {
        val (reg, low) = uiState.allAppsUsage.partition { it.totalTimeVisible >= 60000L }
        Triple(reg, low, low.sumOf { it.totalTimeVisible })
    }

    val hourlyAppsData = remember(uiState.hourlyUsage, selectedHour) {
        val hourData = uiState.hourlyUsage.find { it.hour == selectedHour }
        if (hourData != null && hourData.apps.isNotEmpty()) {
            val (regular, low) = hourData.apps.partition { it.totalTimeVisible >= 60000L }
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

    val appTypes = remember(uiState.activeShields, uiState.activeGoals) {
        val types = mutableMapOf<String, String>()
        uiState.activeShields.forEach { types[it.packageName] = "SHIELD" }
        uiState.activeGoals.forEach { types[it.packageName] = "GOAL" }
        types
    }

    fun getGroupShape(index: Int, total: Int): RoundedCornerShape {
        return when {
            total == 1 -> RoundedCornerShape(24.dp)
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
    }

    val shieldUsage = uiState.shieldUsage
    val goalUsage = uiState.goalUsage
    val otherUsage = uiState.otherUsage

    val isBedtimeHour = remember(uiState.selectedDateMillis, uiState.bedtimeEnabled, uiState.bedtimeStartTime, uiState.bedtimeEndTime, uiState.bedtimeDays) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = uiState.selectedDateMillis
        val todayDay = cal.get(Calendar.DAY_OF_WEEK)
        
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayDay = cal.get(Calendar.DAY_OF_WEEK)
        
        val startH = uiState.bedtimeStartTime.split(":").firstOrNull()?.toIntOrNull() ?: 22
        val endH = uiState.bedtimeEndTime.split(":").firstOrNull()?.toIntOrNull() ?: 7
        
        val todayActive = uiState.bedtimeEnabled && todayDay in uiState.bedtimeDays
        val yesterdayActive = uiState.bedtimeEnabled && yesterdayDay in uiState.bedtimeDays
        
        { hour: Int ->
            if (startH <= endH) {
                todayActive && hour in startH until endH
            } else {
                (todayActive && hour >= startH) || (yesterdayActive && hour < endH)
            }
        }
    }

    val hourlyAccentColor by animateColorAsState(
        targetValue = if (selectedHour != null && isBedtimeHour(selectedHour!!)) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "HourlyAccentColor"
    )
    
    val hourlyAccentContainerColor by animateColorAsState(
        targetValue = if (selectedHour != null && isBedtimeHour(selectedHour!!)) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "HourlyAccentContainerColor"
    )

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = {
            isManualRefreshing = true
            viewModel.syncDataNow()
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
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
        ) {
        item(key = "zenith_dashboard") {
            Column(modifier = Modifier.animateItem()) {
                val selectedDayTotal = remember(uiState.dailyUsageHistory, uiState.selectedDateMillis) {
                    uiState.dailyUsageHistory.find { it.date == uiState.selectedDateMillis }?.totalTime ?: 0L
                }
                ZenithDashboard(
                    totalTime = selectedDayTotal,
                    targetTime = uiState.targetMillis,
                    yesterdayTime = uiState.yesterdayScreenTime,
                    shieldUsage = shieldUsage,
                    goalUsage = goalUsage,
                    otherUsage = otherUsage,
                    topApp = uiState.topApps.firstOrNull(),
                    activeGoalsCount = uiState.activeGoals.size,
                    activeShieldsCount = uiState.activeShields.size,
                    formatDuration = viewModel::formatDuration,
                    highlightedCategory = highlightedCategory,
                    onCategoryClick = { highlightedCategory = if (highlightedCategory == it) null else it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item(key = "hourly_stats_header") {
            Column(modifier = Modifier.animateItem()) {
                GroupedCard(
                    index = 0, 
                    total = hourlyGroupTotal,
                    containerColor = hourlyAccentContainerColor
                ) {
                    HourlyStatsContent(
                        hourlyUsage = uiState.hourlyUsage,
                        selectedHour = selectedHour,
                        onHourClick = { selectedHour = if (selectedHour == it) null else it },
                        formatDuration = viewModel::formatDuration,
                        bedtimeEnabled = uiState.bedtimeEnabled,
                        bedtimeStartTime = uiState.bedtimeStartTime,
                        bedtimeEndTime = uiState.bedtimeEndTime,
                        bedtimeDays = uiState.bedtimeDays,
                        dateMillis = uiState.selectedDateMillis,
                        accentColor = hourlyAccentColor,
                        showDatabaseIndicator = showDatabaseIndicator
                    )
                }
            }
        }

        if (hourlyAppsData != null) {
            val (hourData, regularHourApps, lowData) = hourlyAppsData
            val (lowUsageHourApps, totalLowUsageHourTime, currentTargetHour) = lowData

            item(key = "hourly_apps_label_$currentTargetHour") {
                Column(modifier = Modifier.animateItem()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    GroupedCard(
                        index = 1,
                        total = hourlyGroupTotal,
                        containerColor = hourlyAccentContainerColor
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
                                tint = hourlyAccentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Apps used at ${String.format("%02d:00", currentTargetHour)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = hourlyAccentColor,
                                modifier = Modifier.weight(1f)
                            )
                            ZenithToggleButtonGroup(
                                options = listOf(
                                    ZenithToggleOption(text = "Usage", icon = Icons.Outlined.Sort),
                                    ZenithToggleOption(text = "Recent", icon = Icons.Outlined.Schedule)
                                ),
                                selectedIndices = setOf(if (uiState.hourlySortType == HourlySortType.USAGE_TIME) 0 else 1),
                                onToggle = { index ->
                                    val newType = if (index == 0) HourlySortType.USAGE_TIME else HourlySortType.RECENTLY_USED
                                    viewModel.onHourlySortTypeChange(newType)
                                },
                                modifier = Modifier.width(200.dp),
                                size = ZenithButtonSize.Small,
                                isScalingEnabled = false,
                                isShowingCheck = false,
                                showTextSelected = true
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
                        type = appTypes[app.packageName] ?: "OTHER",
                        formatDuration = viewModel::formatDuration,
                        index = groupIndex,
                        total = hourlyGroupTotal,
                        onClick = { onAppClick(app.packageName) },
                        containerColor = hourlyAccentContainerColor,
                        accentColor = hourlyAccentColor
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
                            containerColor = hourlyAccentContainerColor
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
                                        color = hourlyAccentColor
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = viewModel.formatDuration(totalLowUsageHourTime),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = hourlyAccentColor
                                        )
                                        Icon(
                                            imageVector = if (isOtherHourAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp).padding(start = 4.dp),
                                            tint = hourlyAccentColor
                                        )
                                    }
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(hourlyAccentColor.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Android,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = hourlyAccentColor
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                )
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
                                type = appTypes[app.packageName] ?: "OTHER",
                                formatDuration = viewModel::formatDuration,
                                index = groupIndex,
                                total = hourlyGroupTotal,
                                onClick = { onAppClick(app.packageName) },
                                containerColor = hourlyAccentContainerColor,
                                accentColor = hourlyAccentColor
                            )
                        }
                    }
                }
            }
        }

        item(key = "usage_trends_group_item") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(4.dp))
                UsageHistoryCard(
                    history = uiState.dailyUsageHistory,
                    targetMillis = uiState.targetMillis,
                    selectedDateMillis = uiState.selectedDateMillis,
                    formatDuration = viewModel::formatDuration,
                    onDaySelected = { usage ->
                        viewModel.selectDate(usage?.date)
                    },
                    onPageSelected = viewModel::onVisibleWeekChanged,
                    title = "Usage Trends",
                    showDatabaseIndicator = showDatabaseIndicator,
                    shape = getGroupShape(hourlyGroupTotal - 1, hourlyGroupTotal),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        }

        item(key = "highlight_header") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Highlight",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }

        item(key = "snapshot_stamps") {
            Column(modifier = Modifier.animateItem()) {
                SnapshotSection(
                    stamps = uiState.snapshotStamps,
                    selectedDateMillis = uiState.selectedDateMillis,
                    getAppType = { pkg ->
                        uiState.activeGoals.find { it.packageName == pkg }?.type
                            ?: uiState.activeShields.find { it.packageName == pkg }?.type
                    },
                    onDaySelected = { viewModel.selectDate(it) },
                    formatDuration = viewModel::formatDuration,
                    showDatabaseIndicator = showDatabaseIndicator,
                    startIndex = 0,
                    totalCount = 5
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "weekly_double_card") {
            Column(modifier = Modifier.animateItem()) {
                WeeklyStatsDoubleCard(
                    avgTime = uiState.weeklyAvgTime,
                    topApps = uiState.weeklyTopApps,
                    formatDuration = viewModel::formatDuration,
                    index = 2,
                    total = 5
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "insight_efficiency") {
            val efficiency = remember(shieldUsage, goalUsage) {
                val total = (shieldUsage + goalUsage).toFloat()
                if (total > 0) (goalUsage.toFloat() / total) * 100 else 0f
            }
            Column(modifier = Modifier.animateItem()) {
                EfficiencyScoreItem(
                    efficiency = efficiency,
                    goalUsage = goalUsage,
                    shieldUsage = shieldUsage,
                    formatDuration = viewModel::formatDuration,
                    index = 3,
                    total = 5,
                    onClick = {
                        highlightedCategory = if (highlightedCategory == "GOAL") "SHIELD" else "GOAL"
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "insight_heatmap") {
            Column(modifier = Modifier.animateItem()) {
                GoalHeatmapItem(
                    history = uiState.dailyUsageHistory,
                    targetMillis = uiState.targetMillis,
                    index = 4,
                    total = 5,
                    selectedDateMillis = uiState.selectedDateMillis,
                    onDayClick = { viewModel.selectDate(it) }
                )
            }
        }

        item(key = "about_you_header") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "About You",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }

        item(key = "insight_time_profile") {
            val profile = remember(uiState.hourlyUsage) {
                val morningUsage = uiState.hourlyUsage.filter { it.hour in 5..11 }.sumOf { it.usageTimeMillis }
                val afternoonUsage = uiState.hourlyUsage.filter { it.hour in 12..17 }.sumOf { it.usageTimeMillis }
                val eveningUsage = uiState.hourlyUsage.filter { it.hour in 18..22 }.sumOf { it.usageTimeMillis }
                val nightUsage = uiState.hourlyUsage.filter { it.hour >= 23 || it.hour <= 4 }.sumOf { it.usageTimeMillis }

                val max = listOf(morningUsage, afternoonUsage, eveningUsage, nightUsage).maxOrNull() ?: 0L
                when {
                    max == 0L -> "No usage yet"
                    max == morningUsage -> "Early Bird"
                    max == afternoonUsage -> "Day Runner"
                    max == eveningUsage -> "Evening Active"
                    else -> "Night Owl"
                }
            }
            Column(modifier = Modifier.animateItem()) {
                TimeProfileItem(
                    profile = profile, 
                    index = 0, 
                    total = 2,
                    onClick = {
                        val peakHour = when(profile) {
                            "Early Bird" -> 8
                            "Day Runner" -> 14
                            "Evening Active" -> 20
                            "Night Owl" -> 0
                            else -> null
                        }
                        if (peakHour != null) selectedHour = peakHour
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        item(key = "insight_usage_pattern") {
            val totalSessions = remember(uiState.allAppsUsage) { uiState.allAppsUsage.sumOf { it.sessionCount } }
            val totalUsageTime = remember(uiState.allAppsUsage) { uiState.allAppsUsage.sumOf { it.totalTimeVisible } }
            val avgSessionMillis = remember(totalUsageTime, totalSessions) {
                if (totalSessions > 0) totalUsageTime / totalSessions else 0L
            }
            Column(modifier = Modifier.animateItem()) {
                UsagePatternItem(
                    totalSessions = totalSessions,
                    avgSessionMillis = avgSessionMillis,
                    formatDuration = viewModel::formatDuration,
                    index = 1,
                    total = 2,
                    onClick = {
                        isOtherAppsExpanded = !isOtherAppsExpanded
                    }
                )
            }
        }

        item(key = "app_usage_header") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "App Usage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }

        itemsIndexed(
            items = regularApps,
            key = { _, app -> "regular-${app.packageName}" }
        ) { index, app ->
            val total = regularApps.size + (if (totalLowUsageTime > 0) (if (isOtherAppsExpanded) 1 + lowUsageApps.size else 1) else 0)
            Column(modifier = Modifier.animateItem()) {
                UsageItem(
                    app = app,
                    type = appTypes[app.packageName] ?: "OTHER",
                    formatDuration = viewModel::formatDuration,
                    index = index,
                    total = total,
                    onClick = { onAppClick(app.packageName) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (totalLowUsageTime > 0) {
            val otherIndex = regularApps.size
            val total = regularApps.size + (if (isOtherAppsExpanded) 1 + lowUsageApps.size else 1)
            
            item(key = "other_apps_header") {
                Column(modifier = Modifier.animateItem()) {
                    GroupedCard(
                        index = otherIndex,
                        total = total,
                        onClick = { isOtherAppsExpanded = !isOtherAppsExpanded }
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
                                    text = "${lowUsageApps.size} apps with minimal usage",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = viewModel.formatDuration(totalLowUsageTime),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Icon(
                                        imageVector = if (isOtherAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp).padding(start = 4.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            )
                        )
                    }
                    if (isOtherAppsExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            if (isOtherAppsExpanded) {
                itemsIndexed(
                    items = lowUsageApps,
                    key = { _, app -> "low-${app.packageName}" }
                ) { index, app ->
                    Column(modifier = Modifier.animateItem()) {
                        UsageItem(
                            app = app,
                            type = appTypes[app.packageName] ?: "OTHER",
                            formatDuration = viewModel::formatDuration,
                            index = otherIndex + 1 + index,
                            total = total,
                            onClick = { onAppClick(app.packageName) }
                        )
                        if (index < lowUsageApps.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun ZenithDashboard(
    totalTime: Long,
    targetTime: Long,
    yesterdayTime: Long,
    shieldUsage: Long,
    goalUsage: Long,
    otherUsage: Long,
    topApp: AppUsageInfo?,
    activeGoalsCount: Int,
    activeShieldsCount: Int,
    formatDuration: (Long) -> String,
    highlightedCategory: String?,
    onCategoryClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
        ) {
            val rawTotal = (shieldUsage + goalUsage + otherUsage).toFloat().coerceAtLeast(1f)
            val minAngleThreshold = 20f

            val shieldIsVisible = (shieldUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold || highlightedCategory == "SHIELD"
            val goalIsVisible = (goalUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold || highlightedCategory == "GOAL"
            val otherIsVisible = (otherUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold || highlightedCategory == "OTHER"

            val visibleTotal = (
                (if (shieldIsVisible) shieldUsage else 0L) +
                (if (goalIsVisible) goalUsage else 0L) +
                (if (otherIsVisible) otherUsage else 0L)
            ).toFloat().coerceAtLeast(1f)

            val animShieldAngle by animateFloatAsState(
                targetValue = if (shieldIsVisible) (shieldUsage.toFloat() / visibleTotal) * 360f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "ShieldAngle"
            )
            val animGoalAngle by animateFloatAsState(
                targetValue = if (goalIsVisible) (goalUsage.toFloat() / visibleTotal) * 360f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "GoalAngle"
            )
            val animOtherAngle by animateFloatAsState(
                targetValue = if (otherIsVisible) (otherUsage.toFloat() / visibleTotal) * 360f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "OtherAngle"
            )

            val baseStroke = 26.dp
            val highlightStroke = 38.dp

            val animShieldStroke by animateDpAsState(
                targetValue = if (highlightedCategory == "SHIELD") highlightStroke else baseStroke,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "ShieldStroke"
            )
            val animGoalStroke by animateDpAsState(
                targetValue = if (highlightedCategory == "GOAL") highlightStroke else baseStroke,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "GoalStroke"
            )
            val animOtherStroke by animateDpAsState(
                targetValue = if (highlightedCategory == "OTHER") highlightStroke else baseStroke,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "OtherStroke"
            )

            val animShieldAlpha by animateFloatAsState(
                targetValue = if (highlightedCategory == null || highlightedCategory == "SHIELD") 1f else 0.3f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "ShieldAlpha"
            )
            val animGoalAlpha by animateFloatAsState(
                targetValue = if (highlightedCategory == null || highlightedCategory == "GOAL") 1f else 0.3f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "GoalAlpha"
            )
            val animOtherAlpha by animateFloatAsState(
                targetValue = if (highlightedCategory == null || highlightedCategory == "OTHER") 1f else 0.3f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "OtherAlpha"
            )

            val shieldColor = MaterialTheme.colorScheme.primary
            val goalColor = MaterialTheme.colorScheme.tertiary
            val otherColor = MaterialTheme.colorScheme.secondary

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            ) {
                val radius = (size.minDimension - highlightStroke.toPx()) / 2
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)

                var currentAngle = -90f
                val totalVisibleCount = (if (shieldIsVisible) 1 else 0) + (if (goalIsVisible) 1 else 0) + (if (otherIsVisible) 1 else 0)
                val gap = if (totalVisibleCount > 1) 22f else 0f

                fun drawSegment(angle: Float, alpha: Float, strokeWidth: Float, color: Color, category: String) {
                    if (angle > 0.1f) {
                        val sweep = (angle - gap).coerceAtLeast(0f)
                        if (sweep > 2f) {
                            drawArc(
                                color = color.copy(alpha = alpha),
                                startAngle = currentAngle + gap / 2,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        } else if (highlightedCategory == category) {
                            drawArc(
                                color = color,
                                startAngle = currentAngle + angle / 2 - 1f,
                                sweepAngle = 2f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                    currentAngle += angle
                }

                drawSegment(animShieldAngle, animShieldAlpha, animShieldStroke.toPx(), shieldColor, "SHIELD")
                drawSegment(animGoalAngle, animGoalAlpha, animGoalStroke.toPx(), goalColor, "GOAL")
                drawSegment(animOtherAngle, animOtherAlpha, animOtherStroke.toPx(), otherColor, "OTHER")
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = totalTime,
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                            .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                    },
                    label = "TotalTimeAnimation"
                ) { targetTimeValue ->
                    Text(
                        text = formatDuration(targetTimeValue),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val anyHighlighted = highlightedCategory != null
            DashboardSmallCard(
                icon = Icons.Outlined.Android,
                label = "Other Apps",
                value = formatDuration(otherUsage),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconSectionColor = MaterialTheme.colorScheme.secondary,
                iconColor = MaterialTheme.colorScheme.onSecondary,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCategoryClick("OTHER") },
                isHighlighted = highlightedCategory == "OTHER",
                anyHighlighted = anyHighlighted
            )
            DashboardSmallCard(
                icon = Icons.Outlined.TrackChanges,
                label = "Goal Apps",
                value = formatDuration(goalUsage),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconSectionColor = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
                textColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCategoryClick("GOAL") },
                isHighlighted = highlightedCategory == "GOAL",
                anyHighlighted = anyHighlighted
            )
            DashboardSmallCard(
                icon = Icons.Outlined.Shield,
                label = "Shield Apps",
                value = formatDuration(shieldUsage),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconSectionColor = MaterialTheme.colorScheme.primary,
                iconColor = MaterialTheme.colorScheme.onPrimary,
                textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCategoryClick("SHIELD") },
                isHighlighted = highlightedCategory == "SHIELD",
                anyHighlighted = anyHighlighted
            )
        }
    }
}

@Composable
fun DashboardSmallCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    containerColor: Color,
    iconSectionColor: Color,
    iconColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    anyHighlighted: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
) {
    val animAlpha by animateFloatAsState(
        targetValue = if (!anyHighlighted || isHighlighted) 1f else 0.4f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CardAlpha"
    )
    
    val animScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "CardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animAlpha
                scaleX = animScale
                scaleY = animScale
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(42.dp)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(iconSectionColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            }
            
            Box(modifier = Modifier.fillMaxSize()) {
                val animatedHighlightAlpha by animateFloatAsState(
                    targetValue = if (isHighlighted) 0f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "HighlightAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(iconSectionColor.copy(alpha = animatedHighlightAlpha))
                )

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 4.dp, end = 12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = {
                            (slideInVertically { height -> height / 2 } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
                        },
                        label = "ValueAnimation"
                    ) { targetValue ->
                        Text(
                            text = targetValue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
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
fun HourlyStatsContent(
    hourlyUsage: List<HourlyUsageInfo>,
    selectedHour: Int?,
    onHourClick: (Int) -> Unit,
    formatDuration: (Long) -> String,
    bedtimeEnabled: Boolean,
    bedtimeStartTime: String,
    bedtimeEndTime: String,
    bedtimeDays: Set<Int>,
    dateMillis: Long,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    showDatabaseIndicator: Boolean = false
) {
    val maxUsage = hourlyUsage.maxOfOrNull { it.usageTimeMillis }?.coerceAtLeast(1L) ?: 1L
    
    val bedtimeSettings = remember(dateMillis, bedtimeEnabled, bedtimeStartTime, bedtimeEndTime, bedtimeDays) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateMillis
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
    
    val isBedtimeHour = bedtimeSettings
    
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
                    val usage = hourlyUsage.find { it.hour == hour }?.usageTimeMillis ?: 0L
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
            val isToday = remember(dateMillis) {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dateMillis == cal.timeInMillis
            }
            hourlyUsage.forEach { hourInfo ->
                val isSelected = selectedHour == hourInfo.hour
                val isCurrentHour = isToday && hourInfo.hour == Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                
                val barHeight = (hourInfo.usageTimeMillis.toFloat() / maxUsage).coerceIn(0.05f, 1f)
                val animatedHeight by animateFloatAsState(
                    targetValue = barHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "HourlyBarHeight"
                )
                
                val isBedtime = isBedtimeHour(hourInfo.hour)
                val baseColor = if (isBedtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

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
                        ) { onHourClick(hourInfo.hour) }
                )
            }
        }
        
        if (showDatabaseIndicator) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                hourlyUsage.forEach { hourInfo ->
                    val indicatorColor = when {
                        hourInfo.usageTimeMillis == 0L && !hourInfo.isLive -> MaterialTheme.colorScheme.error
                        hourInfo.isLive -> MaterialTheme.colorScheme.tertiary
                        hourInfo.hasDatabaseRecord -> MaterialTheme.colorScheme.primary
                        hourInfo.hasSystemData -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.5.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EfficiencyScoreItem(
    efficiency: Float,
    goalUsage: Long,
    shieldUsage: Long,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = efficiency / 100f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "EfficiencyProgress"
    )
    GroupedCard(index = index, total = total, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stars,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Focus Efficiency",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (efficiency >= 50) "Productive Day!" else "Distraction Heavy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${efficiency.roundToInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                stroke = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }, cap = StrokeCap.Round)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Goal: ${formatDuration(goalUsage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Shield: ${formatDuration(shieldUsage)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TimeProfileItem(
    profile: String,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val profileDetails = when (profile) {
        "Early Bird" -> "Your productivity peaks in the morning. Use this time for tasks that require high concentration."
        "Day Runner" -> "You are most active during core working hours. Maintain your momentum throughout the day."
        "Evening Active" -> "Your energy increases as the day gets darker. Perfect for reflection or creative projects."
        "Night Owl" -> "You prefer to be active late at night. Be careful with blue light exposure before bed."
        else -> "Continue using the device to determine your chronotype profile."
    }

    GroupedCard(index = index, total = total, onClick = { 
        isExpanded = !isExpanded
        onClick()
    }) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = "Daily Chronotype",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                supportingContent = {
                    Text(
                        text = "Your peak activity period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                trailingContent = {
                    SuggestionChip(
                        onClick = { },
                        label = { Text(profile) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null,
                        shape = CircleShape
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Text(
                    text = profileDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun UsagePatternItem(
    totalSessions: Int,
    avgSessionMillis: Long,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    GroupedCard(index = index, total = total, onClick = onClick) {
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
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MonitorWeight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Usage Pattern",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total $totalSessions unlocks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(avgSessionMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "avg / session",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GoalHeatmapItem(
    history: List<DailyUsage>,
    targetMillis: Long,
    index: Int,
    total: Int,
    selectedDateMillis: Long,
    onDayClick: (Long) -> Unit
) {
    GroupedCard(index = index, total = total) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Goal Consistency",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val last14Days = history.takeLast(14)
                last14Days.forEach { day ->
                    val isMet = day.totalTime > 0 && day.totalTime <= targetMillis
                    val isSelected = day.date == selectedDateMillis
                    
                    val proximity = if (targetMillis > 0) (day.totalTime.toFloat() / targetMillis).coerceIn(0f, 1f) else 0f
                    val targetMetAlpha = (1f - (proximity * 0.6f)).coerceAtLeast(0.4f)
                    
                    val targetColor = when {
                        day.totalTime == 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        isMet -> MaterialTheme.colorScheme.tertiary.copy(alpha = targetMetAlpha)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    }
                    
                    val animatedColor by animateColorAsState(
                        targetValue = targetColor,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "HeatmapColor"
                    )
                    
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                        label = "HeatmapScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            .clip(RoundedCornerShape(4.dp))
                            .background(animatedColor)
                            .clickable { onDayClick(day.date) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Last 14 Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Target Met", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WeeklyStatsDoubleCard(
    avgTime: Long,
    topApps: List<AppUsageInfo>,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int
) {
    val shape = when {
        total == 1 -> RoundedCornerShape(24.dp)
        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Weekly Avg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                AnimatedContent(
                    targetState = avgTime,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(spring(stiffness = Spring.StiffnessLow)))
                            .togetherWith(slideOutVertically { -it / 2 } + fadeOut(spring(stiffness = Spring.StiffnessLow)))
                    },
                    label = "WeeklyAvgAnim"
                ) { targetAvg ->
                    Text(
                        text = formatDuration(targetAvg),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Top Activities",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                AnimatedContent(
                    targetState = topApps,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.8f) + fadeIn(spring(stiffness = Spring.StiffnessLow)))
                            .togetherWith(scaleOut(targetScale = 0.8f) + fadeOut(spring(stiffness = Spring.StiffnessLow)))
                    },
                    label = "TopAppsAnim"
                ) { targetApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy((-12).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (targetApps.isEmpty()) {
                            Text("-", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            targetApps.take(3).forEach { app ->
                                val appIcon = remember(app.icon) {
                                    app.icon?.toBitmap()?.asImageBitmap()
                                }
                                if (appIcon != null) {
                                    Image(
                                        painter = BitmapPainter(appIcon),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(2.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    val badgeText = when (type) {
                        "GOAL" -> "Goal"
                        "SHIELD" -> "Shield"
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
                val appIcon = remember(app.icon) {
                    app.icon?.toBitmap()?.asImageBitmap()
                }
                if (appIcon != null) {
                    Image(
                        painter = BitmapPainter(appIcon),
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
}
