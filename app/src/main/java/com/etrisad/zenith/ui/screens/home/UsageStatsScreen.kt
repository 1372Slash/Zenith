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
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
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
    onAppClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedHour by rememberSaveable { mutableStateOf<Int?>(null) }

    val (regularApps, lowUsageApps) = remember(uiState.allAppsUsage) {
        uiState.allAppsUsage.partition { it.totalTimeVisible >= 60000L }
    }
    
    val totalLowUsageTime = lowUsageApps.sumOf { it.totalTimeVisible }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item {
            Column {
                GroupedCard(index = 0, total = 2) {
                    HourlyStatsContent(
                        hourlyUsage = uiState.hourlyUsage,
                        selectedHour = selectedHour,
                        onHourClick = { selectedHour = if (selectedHour == it) null else it },
                        formatDuration = viewModel::formatDuration
                    )
                }
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
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = selectedHour != null,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                val hourData = uiState.hourlyUsage.find { it.hour == selectedHour }
                if (hourData != null && hourData.apps.isNotEmpty()) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Apps at ${hourData.hour}:00",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        hourData.apps.forEachIndexed { index, app ->
                            val shape = when {
                                hourData.apps.size == 1 -> RoundedCornerShape(24.dp)
                                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                                index == hourData.apps.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                else -> RoundedCornerShape(8.dp)
                            }
                            UsageItem(
                                app = app,
                                formatDuration = viewModel::formatDuration,
                                shape = shape,
                                onClick = { onAppClick(app.packageName) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SevenDayStampsCard(stamps = uiState.sevenDayStamps)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "App Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        itemsIndexed(regularApps) { index, app ->
            val shape = when {
                regularApps.size == 1 && totalLowUsageTime == 0L -> RoundedCornerShape(24.dp)
                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                index == regularApps.size - 1 && totalLowUsageTime == 0L -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(8.dp)
            }

            UsageItem(
                app = app,
                formatDuration = viewModel::formatDuration,
                shape = shape,
                onClick = { onAppClick(app.packageName) }
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (totalLowUsageTime > 0) {
            item {
                val shape = if (regularApps.isEmpty()) {
                    RoundedCornerShape(24.dp)
                } else {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
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
                            Text(
                                text = viewModel.formatDuration(totalLowUsageTime),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
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
    }
}

@Composable
fun GroupedCard(
    index: Int,
    total: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = when {
        total == 1 -> RoundedCornerShape(28.dp)
        index == 0 -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(12.dp)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = { content() }
    )
}

@Composable
fun HourlyStatsContent(
    hourlyUsage: List<HourlyUsageInfo>,
    selectedHour: Int?,
    onHourClick: (Int) -> Unit,
    formatDuration: (Long) -> String
) {
    val maxUsage = hourlyUsage.maxOfOrNull { it.usageTimeMillis }?.coerceAtLeast(1L) ?: 1L
    
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
                    (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                },
                label = "SelectedHourText"
            ) { hour ->
                if (hour != null) {
                    val usage = hourlyUsage.find { it.hour == hour }?.usageTimeMillis ?: 0L
                    Text(
                        text = formatDuration(usage),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
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
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "HourlyBarHeight"
                )
                
                val barColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isCurrentHour -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
fun SevenDayStampsCard(
    stamps: List<AppUsageInfo>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Stars,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "7-Day Stamps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stamps.forEachIndexed { index, app ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (app.icon != null) {
                                Image(
                                    painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val dayLabel = remember(index) {
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DAY_OF_YEAR, -(7 - index))
                            SimpleDateFormat("E", Locale.getDefault()).format(cal.time).first().toString()
                        }
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
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
fun UsageItem(
    app: AppUsageInfo,
    formatDuration: (Long) -> String,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
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
