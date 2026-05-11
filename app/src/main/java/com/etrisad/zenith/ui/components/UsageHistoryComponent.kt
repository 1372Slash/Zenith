package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import java.text.SimpleDateFormat
import java.util.Locale

import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

@Composable
fun UsageHistoryCard(
    history: List<DailyUsage>,
    targetMillis: Long,
    focusType: FocusType? = null,
    showDatabaseIndicator: Boolean = false,
    selectedDateMillis: Long? = null,
    formatDuration: (Long) -> String,
    onDaySelected: (DailyUsage?) -> Unit = {},
    onPageSelected: (Int) -> Unit = {},
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    title: String = "Last 21 Days"
) {
    var internalSelectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val effectiveSelectedDate = selectedDateMillis ?: internalSelectedDate
    
    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }
    
    val selectedUsage = remember(history, effectiveSelectedDate) {
        history.find { it.date == effectiveSelectedDate }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                focusType = focusType,
                showDatabaseIndicator = showDatabaseIndicator,
                selectedDateMillis = effectiveSelectedDate,
                onDaySelected = { 
                    if (selectedDateMillis == null) {
                        internalSelectedDate = it?.date
                    }
                    onDaySelected(it)
                },
                onPageSelected = onPageSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageGraph(
    history: List<DailyUsage>,
    targetMillis: Long,
    focusType: FocusType? = null,
    showDatabaseIndicator: Boolean = false,
    selectedDateMillis: Long? = null,
    onDaySelected: (DailyUsage?) -> Unit,
    onPageSelected: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
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
    val initialPage = remember(history) { (pageCount - 1).coerceAtLeast(0) }
    val pagerState = rememberPagerState(pageCount = { pageCount }, initialPage = initialPage)

    LaunchedEffect(pagerState.currentPage) {
        onPageSelected(pagerState.currentPage)
    }

    var hasInitializedPager by remember { mutableStateOf(false) }
    LaunchedEffect(history, selectedDateMillis) {
        if (history.isNotEmpty()) {
            val targetIndex = if (selectedDateMillis != null) {
                history.indexOfFirst { it.date == selectedDateMillis }
            } else -1
            
            val targetPage = if (targetIndex != -1) {
                targetIndex / 7
            } else if (!hasInitializedPager) {
                (history.size - 1).coerceAtLeast(0) / 7
            } else {
                -1
            }

            if (targetPage != -1 && targetPage != pagerState.currentPage) {
                pagerState.animateScrollToPage(targetPage)
            }
            hasInitializedPager = true
        }
    }

    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }

    var animateTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(history) {
        if (history.isNotEmpty() && !animateTrigger) {
            kotlinx.coroutines.delay(200)
            animateTrigger = true
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageData = if (pageIndex < pages.size) pages[pageIndex] else emptyList()

                val pageMaxRaw = (pageData.maxOfOrNull { it.totalTime } ?: 0L)
                    .coerceAtLeast(targetMillis)
                    .coerceAtLeast(60 * 1000L)

                val pageMax = if (pageMaxRaw < 3600000L) {
                    ((pageMaxRaw / 60000L).coerceAtLeast(1) + 3) * 60000L
                } else {
                    ((pageMaxRaw / 3600000L).coerceAtLeast(1) + 1) * 3600000L
                }

                val maxDisplay = if (pageMax < 3600000L) (pageMax / 60000L) else (pageMax / 3600000L)
                val unit = if (pageMax < 3600000L) "m" else "h"

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(4) { i ->
                            val labelValue = (maxDisplay * (3 - i) / 3)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${labelValue}$unit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.width(28.dp)
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

                    val goalRatio = (targetMillis.toFloat() / pageMax).coerceIn(0f, 1f)
                    val animatedGoalRatio by animateFloatAsState(
                        targetValue = goalRatio,
                        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)
                    )

                    val goalLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                    if (targetMillis > 0) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 32.dp, start = 32.dp)
                        ) {
                            val y = size.height * (1f - animatedGoalRatio)
                            drawLine(
                                color = goalLineColor,
                                start = androidx.compose.ui.geometry.Offset(0f, y),
                                end = androidx.compose.ui.geometry.Offset(size.width, y),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp, start = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        pageData.forEach { usage ->
                            val isSelected = selectedDateMillis == usage.date
                            val targetHeight = if (animateTrigger) (usage.totalTime.toFloat() / pageMax).coerceIn(0.01f, 1f) else 0.01f
                            val animatedHeight by animateFloatAsState(
                                targetValue = targetHeight,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                            )

                            val isGoalAchieved = if (focusType == FocusType.GOAL) {
                                targetMillis > 0 && usage.totalTime >= targetMillis
                            } else {
                                targetMillis > 0 && usage.totalTime <= targetMillis
                            }

                            val isToday = dateFormat.format(usage.date) == todayDate
                            val baseColor = if (isGoalAchieved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            val currentAlpha = when {
                                isSelected -> 1f
                                isToday -> 0.8f
                                else -> 0.4f
                            }
                            val barColor = baseColor.copy(alpha = currentAlpha)

                            val isInside = animatedHeight > 0.22f
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onDaySelected(if (isSelected) null else usage)
                                    }
                            ) {
                                if (isGoalAchieved && !isInside) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(sunnyShape)
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = currentAlpha)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .fillMaxHeight(animatedHeight)
                                        .clip(CircleShape)
                                        .background(barColor),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    if (isGoalAchieved && isInside) {
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(start = 32.dp)
                            .height(26.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        pageData.forEach { usage ->
                            val isSelected = selectedDateMillis == usage.date
                            val indicatorColor = when {
                                usage.totalTime == 0L && !usage.isLive -> MaterialTheme.colorScheme.error
                                usage.isLive -> MaterialTheme.colorScheme.tertiary
                                usage.hasDatabaseRecord -> MaterialTheme.colorScheme.primary
                                usage.hasSystemData -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }

                            val animatedIndicatorColor by animateColorAsState(
                                targetValue = if (animateTrigger) indicatorColor else indicatorColor.copy(alpha = 0f),
                                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow)
                            )
                            val animatedWidth by animateDpAsState(
                                targetValue = if (animateTrigger) 12.dp else 0.dp,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayFormat.format(usage.date).first().toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .width(animatedWidth)
                                        .height(if (showDatabaseIndicator) 2.dp else 0.dp)
                                        .clip(CircleShape)
                                        .background(if (showDatabaseIndicator) animatedIndicatorColor else Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }

            val showTodayButton by remember {
                derivedStateOf { pagerState.currentPage < pagerState.pageCount - 1 }
            }

            val buttonAlpha by animateFloatAsState(
                targetValue = if (showTodayButton) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "TodayButtonAlpha"
            )
            val buttonScale by animateFloatAsState(
                targetValue = if (showTodayButton) 1f else 0.8f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                label = "TodayButtonScale"
            )

            if (buttonAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .graphicsLayer {
                            alpha = buttonAlpha
                            scaleX = buttonScale
                            scaleY = buttonScale
                        }
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pageCount - 1)
                            }
                        },
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "Today",
                                modifier = Modifier.size(16.dp)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
