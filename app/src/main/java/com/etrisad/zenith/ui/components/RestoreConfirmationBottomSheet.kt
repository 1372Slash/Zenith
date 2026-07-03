package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import com.etrisad.zenith.ui.viewmodel.HourlyUsageInfo
import com.etrisad.zenith.util.BackupUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreConfirmationBottomSheet(
    preferences: UserPreferences,
    metadata: BackupUtils.BackupMetadata,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Restore Data?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Restoring will permanently overwrite your current settings and data with the selected backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Backup Contents",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        BackupDetailItem(
                            icon = Icons.Outlined.Storage,
                            label = "Database",
                            status = if (metadata.hasDatabase) "Available" else "Not found",
                            isAvailable = metadata.hasDatabase
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        BackupDetailItem(
                            icon = Icons.Outlined.Settings,
                            label = "User Preferences",
                            status = if (metadata.hasPreferences) "Available" else "Not found",
                            isAvailable = metadata.hasPreferences
                        )

                        if (metadata.latestUsageDate != null && metadata.latestUsageMillis != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BackupDetailItem(
                                icon = Icons.Outlined.History,
                                label = "Latest Record",
                                status = "${metadata.latestUsageDate} - ${formatDuration(metadata.latestUsageMillis)}",
                                isAvailable = true
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackupBadge(label = "History", isAvailable = metadata.historySnapshot.isNotEmpty())
                            BackupBadge(label = "Snapshot", isAvailable = metadata.topAppsHistory.isNotEmpty())
                            BackupBadge(label = "Hourly", isAvailable = metadata.hasHourly)
                            BackupBadge(label = "Piechart", isAvailable = metadata.hasPiechart)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        ZenithToggleButtonGroup(
                            options = listOf(
                                ZenithToggleOption(text = "History"),
                                ZenithToggleOption(text = "Snapshot"),
                                ZenithToggleOption(text = "Hourly"),
                                ZenithToggleOption(text = "Piechart")
                            ),
                            selectedIndices = setOf(selectedTab),
                            onToggle = { selectedTab = it },
                            size = ZenithButtonSize.Small,
                            isInsideContainer = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedContent(
                            targetState = selectedTab,
                            label = "detailContent",
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)) togetherWith
                                        fadeOut(animationSpec = tween(90))
                            }
                        ) { tab ->
                            when (tab) {
                                0 -> HistoryDetail(preferences, metadata)
                                1 -> SnapshotDetail(preferences, metadata)
                                2 -> HourlyDetail(preferences, metadata)
                                3 -> PiechartDetail(preferences, metadata)
                            }
                        }
                        
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Size",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(metadata.fileSize),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ZenithGroupedButton(size = ZenithButtonSize.Large) {
                    ZenithButtonWeighted(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        text = "Cancel",
                        type = ZenithButtonType.Outlined,
                        isLast = false,
                        size = ZenithButtonSize.ExtraLarge
                    )
                    
                    ZenithButtonWeighted(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onConfirm()
                            }
                        },
                        text = "Restore",
                        type = ZenithButtonType.Filled,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        isFirst = false,
                        size = ZenithButtonSize.ExtraLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupBadge(
    label: String,
    isAvailable: Boolean
) {
    Surface(
        color = if (isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isAvailable) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HistoryDetail(
    preferences: UserPreferences,
    metadata: BackupUtils.BackupMetadata
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val history = remember(metadata.historySnapshot, metadata.latestUsageDate) {
        val calendar = Calendar.getInstance()
        metadata.latestUsageDate?.let {
            try {
                dateFormat.parse(it)?.let { date -> calendar.time = date }
            } catch (_: Exception) {}
        }
        
        val dates = (0 until 21).map {
            val d = dateFormat.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            d
        }.reversed()

        dates.map { dateStr ->
            val snapshot = metadata.historySnapshot.find { it.first == dateStr }
            val dateLong = try { dateFormat.parse(dateStr)?.time ?: 0L } catch (e: Exception) { 0L }
            DailyUsage(
                date = dateLong,
                totalTime = snapshot?.second ?: 0L,
                hasDatabaseRecord = snapshot != null
            )
        }
    }

    UsageHistoryCard(
        history = history,
        targetMillis = preferences.screenTimeTargetMinutes * 60 * 1000L,
        formatDuration = { formatDuration(it) },
        showDatabaseIndicator = preferences.showDatabaseIndicator,
        selectedDateMillis = history.lastOrNull()?.date,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(0.dp),
        title = "Last 21 Days (Global)"
    )
}

@Composable
private fun SnapshotDetail(
    preferences: UserPreferences,
    metadata: BackupUtils.BackupMetadata
) {
    val context = LocalContext.current
    val pm = remember { context.packageManager }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val latestDateMillis = remember(metadata.latestUsageDate) {
        metadata.latestUsageDate?.let {
            try {
                dateFormat.parse(it)?.time
            } catch (_: Exception) { null }
        } ?: System.currentTimeMillis()
    }

    val snapshotStamps = remember(metadata.topAppsHistory, latestDateMillis) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = latestDateMillis

        val dates = (0 until 7).map {
            val d = dateFormat.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            d
        }.reversed()

        dates.map { dateStr ->
            val topApp = metadata.topAppsHistory.find { it.first == dateStr }
            if (topApp != null) {
                val pkg = topApp.second
                var appName = pkg
                if (WebsiteRepository.isWebsitePackageName(pkg)) {
                    appName = WebsiteRepository.extractDomainFromPackageName(pkg)
                } else {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        appName = pm.getApplicationLabel(appInfo).toString()
                    } catch (_: Exception) {}
                }

                AppUsageInfo(
                    packageName = pkg,
                    appName = appName,
                    totalTimeVisible = topApp.third,
                    hasDatabaseRecord = true,
                    hasSystemData = false,
                    isLive = false
                )
            } else {
                AppUsageInfo("", "", 0L, hasDatabaseRecord = false)
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 1 })
    
    SnapshotCard(
        stamps = snapshotStamps,
        selectedDateMillis = latestDateMillis,
        getAppType = { _ -> null },
        onDaySelected = { _ -> },
        formatDuration = { formatDuration(it) },
        showDatabaseIndicator = preferences.showDatabaseIndicator,
        pagerState = pagerState,
        isBackupPreview = true,
        referenceDateMillis = latestDateMillis,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(0.dp)
    )
}

@Composable
private fun HourlyDetail(preferences: UserPreferences, metadata: BackupUtils.BackupMetadata) {
    if (metadata.latestHourlyData.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No hourly data available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
        val latestDateMillis = remember(metadata.latestUsageDate) {
            metadata.latestUsageDate?.let {
                try {
                    dateFormat.parse(it)?.time
                } catch (_: Exception) { null }
            } ?: System.currentTimeMillis()
        }

        Column {
            Text(
                text = "Usage on ${metadata.latestUsageDate ?: "Latest Day"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val hourlyUsage = (0..23).map { hour ->
                HourlyUsageInfo(
                    hour = hour,
                    usageTimeMillis = metadata.latestHourlyData[hour] ?: 0L,
                    hasDatabaseRecord = metadata.latestHourlyData.containsKey(hour)
                )
            }
            
            HourlyStatsContent(
                hourlyUsage = hourlyUsage,
                selectedHour = null,
                onHourClick = {},
                formatDuration = { formatDuration(it) },
                bedtimeEnabled = false,
                bedtimeStartTime = "22:00",
                bedtimeEndTime = "07:00",
                bedtimeDays = emptySet(),
                dateMillis = latestDateMillis,
                showDatabaseIndicator = preferences.showDatabaseIndicator
            )
        }
    }
}

@Composable
private fun PiechartDetail(preferences: UserPreferences, metadata: BackupUtils.BackupMetadata) {
    if (metadata.latestPiechartData.isEmpty() && metadata.latestShieldUsage == 0L) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No app breakdown available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val context = LocalContext.current
        val pm = remember { context.packageManager }
        
        Column {
            Text(
                text = "Breakdown on ${metadata.latestUsageDate ?: "Latest Day"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            ZenithDashboard(
                totalTime = metadata.latestUsageMillis ?: 0L,
                targetTime = 0L,
                yesterdayTime = 0L,
                shieldUsage = metadata.latestShieldUsage,
                goalUsage = metadata.latestGoalUsage,
                otherUsage = metadata.latestOtherUsage,
                topApp = null,
                activeGoalsCount = 0,
                activeShieldsCount = 0,
                formatDuration = { formatDuration(it) },
                highlightedCategory = null,
                onCategoryClick = {}
            )
            
            if (metadata.latestPiechartData.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Top Apps in Backup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                metadata.latestPiechartData.forEach { (pkg, duration) ->
                    var appName = pkg
                    if (WebsiteRepository.isWebsitePackageName(pkg)) {
                        appName = WebsiteRepository.extractDomainFromPackageName(pkg)
                    } else {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            appName = pm.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {}
                    }
                    
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            coil.compose.SubcomposeAsyncImage(
                                model = "app-icon://$pkg",
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                error = {
                                    val isWebsite = pkg.startsWith("zenith-web:")
                                    Icon(if (isWebsite) Icons.Outlined.Language else Icons.Outlined.Android, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
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
    accentColor: Color = MaterialTheme.colorScheme.primary,
    showDatabaseIndicator: Boolean = false
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
                val barHeight = (hourInfo.usageTimeMillis.toFloat() / maxUsage).coerceIn(0.01f, 1f)
                val animatedHeight by animateFloatAsState(
                    targetValue = barHeight,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "HourlyBarHeight"
                )
                
                val barColor = accentColor.copy(alpha = if (hourInfo.usageTimeMillis > 0) 0.8f else 0.2f)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.5.dp)
                        .fillMaxHeight(animatedHeight)
                        .clip(CircleShape)
                        .background(barColor)
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
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight()
        ) {
            val rawTotal = (shieldUsage + goalUsage + otherUsage).toFloat().coerceAtLeast(1f)
            val minAngleThreshold = 20f

            val shieldIsVisible = (shieldUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold
            val goalIsVisible = (goalUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold
            val otherIsVisible = (otherUsage.toFloat() / rawTotal) * 360f >= minAngleThreshold

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

            val strokeWidth = 22.dp

            val shieldColor = MaterialTheme.colorScheme.primary
            val goalColor = MaterialTheme.colorScheme.tertiary
            val otherColor = MaterialTheme.colorScheme.secondary

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
            ) {
                val radius = (size.minDimension - strokeWidth.toPx()) / 2
                val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)

                var currentAngle = -90f
                val totalVisibleCount = (if (shieldIsVisible) 1 else 0) + (if (goalIsVisible) 1 else 0) + (if (otherIsVisible) 1 else 0)
                val gap = if (totalVisibleCount > 1) 22f else 0f

                fun drawSegment(angle: Float, color: Color) {
                    if (angle > 0.1f) {
                        val sweep = (angle - gap).coerceAtLeast(0f)
                        if (sweep > 2f) {
                            drawArc(
                                color = color,
                                startAngle = currentAngle + gap / 2,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                    currentAngle += angle
                }

                drawSegment(animShieldAngle, shieldColor)
                drawSegment(animGoalAngle, goalColor)
                drawSegment(animOtherAngle, otherColor)
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatDuration(totalTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            CategorySmallPreview(icon = Icons.Outlined.Shield, label = "Shield", value = formatDuration(shieldUsage), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            CategorySmallPreview(icon = Icons.Outlined.TrackChanges, label = "Goal", value = formatDuration(goalUsage), color = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.height(8.dp))
            CategorySmallPreview(icon = Icons.Outlined.Android, label = "Other", value = formatDuration(otherUsage), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun CategorySmallPreview(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BackupDetailItem(
    icon: ImageVector,
    label: String,
    status: String,
    isAvailable: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
