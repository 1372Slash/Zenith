package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WavyCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 1.2.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        val sweepAngle = (progress.coerceIn(0.01f, 1f) * 360f)
        val path = Path()
        val points = 100
        for (i in 0..points) {
            val angleDeg = (i.toFloat() / points) * sweepAngle - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            
            val waveAmplitude = 0.8f.dp.toPx()
            val waveFreq = 7f
            val r = radius + sin(angleRad * waveFreq + phase) * waveAmplitude
            
            val px = centerX + r * cos(angleRad.toDouble()).toFloat()
            val py = centerY + r * sin(angleRad.toDouble()).toFloat()
            
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth, 
                cap = StrokeCap.Round
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SnapshotSection(
    stamps: List<AppUsageInfo>,
    selectedDateMillis: Long,
    getAppType: (String) -> FocusType?,
    onDaySelected: (Long) -> Unit,
    formatDuration: (Long) -> String,
    showDatabaseIndicator: Boolean = false,
    startIndex: Int = 0,
    totalCount: Int = 2
) {
    val pages = remember(stamps) { stamps.chunked(7) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount }, initialPage = (pageCount - 1).coerceAtLeast(0))

    fun getLocalGroupShape(index: Int): RoundedCornerShape {
        return when {
            totalCount == 1 -> RoundedCornerShape(24.dp)
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            index == totalCount - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        }
    }

    Column {
        SnapshotCard(
            stamps = stamps,
            selectedDateMillis = selectedDateMillis,
            getAppType = getAppType,
            onDaySelected = onDaySelected,
            formatDuration = formatDuration,
            showDatabaseIndicator = showDatabaseIndicator,
            pagerState = pagerState,
            shape = getLocalGroupShape(startIndex)
        )
        Spacer(modifier = Modifier.height(4.dp))
        SnapshotInsightCard(
            stamps = stamps,
            currentPage = pagerState.currentPage,
            getAppType = getAppType,
            shape = getLocalGroupShape(startIndex + 1)
        )
    }
}

@Composable
fun SnapshotInsightCard(
    stamps: List<AppUsageInfo>,
    currentPage: Int,
    getAppType: (String) -> FocusType?,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val pageData = remember(stamps, currentPage) {
        stamps.chunked(7).getOrNull(currentPage) ?: emptyList()
    }

    val dominantApp = remember(pageData) {
        pageData.filter { it.packageName.isNotEmpty() }
            .groupBy { it.packageName }
            .maxByOrNull { it.value.size }
            ?.value?.firstOrNull()
    }

    val dominantType = remember(dominantApp) {
        dominantApp?.let { getAppType(it.packageName) }
    }

    val (icon, message, accentColor) = when (dominantType) {
        FocusType.SHIELD -> Triple(
            Icons.Outlined.Warning,
            "Usage of ${dominantApp?.appName} is very intense. It might be time to tighten the limits.",
            MaterialTheme.colorScheme.primary
        )
        FocusType.GOAL -> Triple(
            Icons.Outlined.TrendingUp,
            "Fantastic! You're consistently staying focused on ${dominantApp?.appName}. Keep going!",
            MaterialTheme.colorScheme.tertiary
        )
        else -> if (dominantApp != null) {
            Triple(
                Icons.Outlined.AutoAwesome,
                "It looks like ${dominantApp.appName} is grabbing your attention. Let's try to limit it for better focus.",
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Triple(
                Icons.Outlined.Camera,
                "Not enough data yet for a snapshot insight. Keep using Zenith!",
                MaterialTheme.colorScheme.outline
            )
        }
    }

    val animatedAccentColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "InsightAccentColor"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = accentColor.copy(alpha = 0.05f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "InsightContainerColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = animatedContainerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(animatedAccentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedAccentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            AnimatedContent(
                targetState = message,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                     slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 2 })
                        .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                     slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 2 })
                },
                label = "InsightMessage"
            ) { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SnapshotCard(
    stamps: List<AppUsageInfo>,
    selectedDateMillis: Long,
    getAppType: (String) -> FocusType?,
    onDaySelected: (Long) -> Unit,
    formatDuration: (Long) -> String,
    showDatabaseIndicator: Boolean = false,
    pagerState: PagerState,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val pages = remember(stamps) { stamps.chunked(7) }
    val pageCount = pages.size.coerceAtLeast(1)
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    
    val selectedIndex = remember(stamps, selectedDateMillis) {
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedDateMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val target = cal.timeInMillis

        stamps.indices.find { i ->
            val dCal = Calendar.getInstance()
            val daysAgo = (stamps.size - 1) - i
            dCal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            dCal.set(Calendar.HOUR_OF_DAY, 0)
            dCal.set(Calendar.MINUTE, 0)
            dCal.set(Calendar.SECOND, 0)
            dCal.set(Calendar.MILLISECOND, 0)
            dCal.timeInMillis == target
        }
    }

    val selectedPageIndex = selectedIndex?.let { it / 7 }
    val selectedAppIndex = selectedIndex?.let { it % 7 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
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
                        imageVector = Icons.Outlined.Camera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Snapshot",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                val selectedApp = selectedIndex?.let { stamps.getOrNull(it) }
                val isSelectedToday = selectedIndex == stamps.size - 1
                val isPendingTodaySelected = isSelectedToday && currentHour < 21
                
                val displayApp = if (isPendingTodaySelected) null else selectedApp

                AnimatedContent(
                    targetState = displayApp,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                         slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                         slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) { -it / 2 })
                    },
                    label = "SnapshotDuration"
                ) { app ->
                    if (app != null && app.totalTimeVisible > 0) {
                        val appType = remember(app.packageName) { getAppType(app.packageName) }
                        val textColor = when (appType) {
                            FocusType.GOAL -> MaterialTheme.colorScheme.tertiary
                            FocusType.SHIELD -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = formatDuration(app.totalTimeVisible),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    } else if (isPendingTodaySelected) {
                        Text(
                            text = "Pending (9 PM)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { pageIndex ->
                val pageData = pages.getOrNull(pageIndex) ?: emptyList()
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    pageData.forEachIndexed { appIndex, app ->
                        val isSelected = selectedAppIndex == appIndex && selectedPageIndex == pageIndex
                        
                        val indexInStamps = pageIndex * 7 + appIndex
                        val streak = remember(stamps, indexInStamps) {
                            val currentApp = stamps.getOrNull(indexInStamps)
                            if (currentApp == null || currentApp.packageName.isEmpty()) 0
                            else {
                                var count = 0
                                var checkIndex = indexInStamps - 1
                                while (checkIndex >= 0 && stamps.getOrNull(checkIndex)?.packageName == currentApp.packageName) {
                                    count++
                                    checkIndex--
                                }
                                count
                            }
                        }

                        val hasPrevConnection = remember(stamps, indexInStamps) {
                            app.packageName.isNotEmpty() && indexInStamps > 0 && 
                            stamps.getOrNull(indexInStamps - 1)?.packageName == app.packageName
                        }
                        
                        val hasNextConnection = remember(stamps, indexInStamps) {
                            app.packageName.isNotEmpty() && indexInStamps < stamps.size - 1 && 
                            stamps.getOrNull(indexInStamps + 1)?.packageName == app.packageName
                        }

                        val isToday = indexInStamps == stamps.size - 1
                        val isPendingToday = isToday && currentHour < 21

                        val targetIndicatorSize = when {
                            isPendingToday -> 34.dp
                            app.icon != null -> 40.dp
                            else -> 24.dp
                        }

                        val indicatorSize by animateDpAsState(
                            targetValue = targetIndicatorSize,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                            label = "SnapshotIndicatorSize"
                        )

                        val dayShape = when (streak) {
                            0 -> MaterialShapes.Square.toShape()
                            1 -> MaterialShapes.Circle.toShape()
                            2 -> MaterialShapes.Pill.toShape()
                            3 -> MaterialShapes.Pentagon.toShape()
                            4 -> MaterialShapes.Gem.toShape()
                            5 -> MaterialShapes.Cookie7Sided.toShape()
                            else -> MaterialShapes.Sunny.toShape()
                        }

                        val appType = remember(app.packageName) { getAppType(app.packageName) }
                        val streakColor = when (appType) {
                            FocusType.GOAL -> MaterialTheme.colorScheme.tertiary
                            FocusType.SHIELD -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val accentAlpha = (streak * 0.15f).coerceAtMost(0.6f)
                        val baseColor = if (streak > 0) {
                            streakColor.copy(alpha = 0.1f + accentAlpha)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    val cal = Calendar.getInstance()
                                    val daysAgo = (stamps.size - 1) - indexInStamps
                                    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                                    onDaySelected(cal.timeInMillis)
                                }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(indicatorSize)
                                            .clip(dayShape)
                                            .background(
                                                if (isSelected) streakColor.copy(alpha = 0.3f + accentAlpha)
                                                else baseColor
                                            )
                                    )
                                }
                            }

                            if (hasPrevConnection || hasNextConnection) {
                                val lineColor = streakColor
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    val centerY = size.height / 2
                                    if (hasPrevConnection) {
                                        drawLine(
                                            color = lineColor,
                                            start = androidx.compose.ui.geometry.Offset(0f, centerY),
                                            end = androidx.compose.ui.geometry.Offset(size.width / 2, centerY),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                    if (hasNextConnection) {
                                        drawLine(
                                            color = lineColor,
                                            start = androidx.compose.ui.geometry.Offset(size.width / 2, centerY),
                                            end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isPendingToday) {
                                        WavyCircularProgress(
                                            progress = currentHour / 21f,
                                            color = streakColor,
                                            modifier = Modifier.size(14.dp)
                                            
                                        )
                                    } else if (app.icon != null) {
                                        Image(
                                            painter = BitmapPainter(app.icon.toBitmap().asImageBitmap()),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val dayLabel = remember(pageIndex, appIndex, stamps.size) {
                                    val cal = Calendar.getInstance()
                                    val indexInStamps = pageIndex * 7 + appIndex
                                    val daysAgo = (stamps.size - 1) - indexInStamps
                                    cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
                                    SimpleDateFormat("E", Locale.getDefault()).format(cal.time).first().toString()
                                }
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (showDatabaseIndicator) {
                                    val indicatorColor = when {
                                        app.totalTimeVisible == 0L && !app.isLive -> MaterialTheme.colorScheme.error
                                        app.isLive -> MaterialTheme.colorScheme.tertiary
                                        app.hasDatabaseRecord -> MaterialTheme.colorScheme.primary
                                        app.hasSystemData -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .width(12.dp)
                                            .height(2.dp)
                                            .clip(CircleShape)
                                            .background(indicatorColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
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
}
