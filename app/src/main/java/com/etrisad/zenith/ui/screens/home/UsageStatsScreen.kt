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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.components.SnapshotSection
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.HourlyUsageInfo
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun UsageStatsScreen(
    viewModel: HomeViewModel,
    innerPadding: PaddingValues,
    showDatabaseIndicator: Boolean,
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var isOtherAppsExpanded by rememberSaveable { mutableStateOf(false) }
    var isOtherHourAppsExpanded by rememberSaveable(selectedHour) { mutableStateOf(false) }

    val (regularApps, lowUsageApps) = remember(uiState.allAppsUsage) {
        uiState.allAppsUsage.partition { it.totalTimeVisible >= 60000L }
    }
    
    val totalLowUsageTime = lowUsageApps.sumOf { it.totalTimeVisible }

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

    fun getGroupShape(index: Int, total: Int): RoundedCornerShape {
        return when {
            total == 1 -> RoundedCornerShape(28.dp)
            index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
            index == total - 1 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
            else -> RoundedCornerShape(12.dp)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item(key = "hourly_stats_header") {
            Column(modifier = Modifier.animateItem()) {
                GroupedCard(
                    index = 0, 
                    total = hourlyGroupTotal,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
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
                        dateMillis = uiState.selectedDateMillis
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
                        total = hourlyGroupTotal
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Apps used at ${String.format("%02d:00", currentTargetHour)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
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
                        formatDuration = viewModel::formatDuration,
                        index = groupIndex,
                        total = hourlyGroupTotal,
                        onClick = { onAppClick(app.packageName) }
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
                            onClick = { isOtherHourAppsExpanded = !isOtherHourAppsExpanded }
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = viewModel.formatDuration(totalLowUsageHourTime),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Icon(
                                            imageVector = if (isOtherHourAppsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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
                                formatDuration = viewModel::formatDuration,
                                index = groupIndex,
                                total = hourlyGroupTotal,
                                onClick = { onAppClick(app.packageName) }
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
                    title = "Usage Trends",
                    showDatabaseIndicator = showDatabaseIndicator,
                    shape = getGroupShape(hourlyGroupTotal - 1, hourlyGroupTotal),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        }

        item(key = "snapshot_stamps") {
            Column(modifier = Modifier.animateItem()) {
                Spacer(modifier = Modifier.height(12.dp))
                SnapshotSection(
                    stamps = uiState.snapshotStamps,
                    selectedDateMillis = uiState.selectedDateMillis,
                    getAppType = { pkg ->
                        uiState.activeGoals.find { it.packageName == pkg }?.type
                            ?: uiState.activeShields.find { it.packageName == pkg }?.type
                    },
                    onDaySelected = { viewModel.selectDate(it) },
                    formatDuration = viewModel::formatDuration,
                    showDatabaseIndicator = showDatabaseIndicator
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
        total == 1 -> RoundedCornerShape(28.dp)
        index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(12.dp)
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
    dateMillis: Long
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
                // Spans midnight: 
                // Late night today (e.g. 22-23) if today is active
                // Early morning today (e.g. 0-6) if yesterday was active
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
                    tint = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.primary
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
            hourlyUsage.forEach { hourInfo ->
                val isSelected = selectedHour == hourInfo.hour
                val isCurrentHour = hourInfo.hour == Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun UsageItem(
    app: AppUsageInfo,
    formatDuration: (Long) -> String,
    index: Int,
    total: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GroupedCard(index = index, total = total, onClick = onClick, modifier = modifier) {
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
