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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import kotlin.math.ceil

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
    onChunkSelected: (Int) -> Unit = {},
    olderWeekLoader: (suspend (chunkOffset: Int) -> List<DailyUsage>)? = null,
    loaderKey: Any? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    title: String = "History"
) {
    var internalSelectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    val effectiveSelectedDate = selectedDateMillis ?: internalSelectedDate
    var olderDays by remember(loaderKey) { mutableStateOf(emptyList<DailyUsage>()) }

    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }

    val selectedUsage = remember(history, olderDays, effectiveSelectedDate) {
        history.find { it.date == effectiveSelectedDate }
            ?: olderDays.find { it.date == effectiveSelectedDate }
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
                    label = "SelectedUsageAnim",
                    modifier = Modifier.height(24.dp)
                ) { usage ->
                    if (usage != null) {
                        Text(
                            text = formatDuration(usage.totalTime),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.wrapContentHeight()
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxHeight())
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
                onChunkSelected = onChunkSelected,
                olderWeekLoader = olderWeekLoader,
                loaderKey = loaderKey,
                onOlderDaysChanged = { olderDays = it }
            )
        }
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeekStepperIndicator(
    rangeLabel: String,
    showPrevious: Boolean,
    showNext: Boolean,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prevAlpha by animateFloatAsState(
        targetValue = if (showPrevious) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "WeekPrevAlpha"
    )
    val nextAlpha by animateFloatAsState(
        targetValue = if (showNext) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "WeekNextAlpha"
    )
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp).offset(x = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading && !showPrevious) {
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    FilledTonalIconButton(
                        onClick = { if (showPrevious) onPrevious() },
                        enabled = showPrevious || prevAlpha > 0.02f,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer {
                                alpha = prevAlpha
                                val scale = 0.8f + 0.2f * prevAlpha
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous week",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                AnimatedContent(
                    targetState = rangeLabel,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                slideInVertically { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    slideOutVertically { -it / 2 })
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.Center,
                    label = "WeekRangeLabel"
                ) { label ->
                    Text(
                        text = label.ifEmpty { " " },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Box(
                modifier = Modifier.size(28.dp).offset(x = (-1).dp),
                contentAlignment = Alignment.Center
            ) {
                FilledTonalIconButton(
                    onClick = { if (showNext) onNext() },
                    enabled = showNext || nextAlpha > 0.02f,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            alpha = nextAlpha
                            val scale = 0.8f + 0.2f * nextAlpha
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next week",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
    onChunkSelected: (Int) -> Unit,
    olderWeekLoader: (suspend (chunkOffset: Int) -> List<DailyUsage>)? = null,
    loaderKey: Any? = null,
    onOlderDaysChanged: (List<DailyUsage>) -> Unit = {}
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
    val basePages = remember(history) { history.chunked(7) }
    val basePageCount = basePages.size.coerceAtLeast(1)
    var olderChunks by remember(loaderKey) { mutableStateOf(listOf<List<DailyUsage>>()) }
    var loadingOlder by remember(loaderKey) { mutableStateOf(false) }
    var olderExhausted by remember(loaderKey) { mutableStateOf(false) }
    var emptyStreak by remember(loaderKey) { mutableStateOf(0) }

    val totalPages = olderChunks.size + basePageCount

    val pagerState = key(history.isNotEmpty(), loaderKey) {
        rememberPagerState(
            initialPage = (basePageCount - 1).coerceAtLeast(0),
            pageCount = { olderChunks.size + basePageCount }
        )
    }

    fun chunkAt(page: Int): List<DailyUsage> =
        if (page < olderChunks.size) olderChunks[page]
        else basePages.getOrNull(page - olderChunks.size) ?: emptyList()
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val current = pagerState.currentPage
        onChunkSelected((totalPages - 1) - current)
        if (olderWeekLoader != null && !loadingOlder && !olderExhausted &&
            current <= 1 && totalPages > 1
        ) {
            loadingOlder = true
            try {
                val week = olderWeekLoader(totalPages).take(7)
                if (week.size < 7 || week.all { it.totalTime == 0L }) {
                    emptyStreak += 1
                } else {
                    emptyStreak = 0
                }
                if (emptyStreak >= 12) {
                    olderExhausted = true
                    val drop = (emptyStreak - 2).coerceAtLeast(0)
                    if (drop > 0 && drop < olderChunks.size + 1) {
                        olderChunks = olderChunks.drop(drop)
                        emptyStreak = 2
                        onOlderDaysChanged(olderChunks.flatten())
                        pagerState.scrollToPage((current - drop).coerceAtLeast(0))
                    }
                } else if (week.size == 7) {
                    olderChunks = listOf(week) + olderChunks
                    onOlderDaysChanged(olderChunks.flatten())
                    if (pagerState.currentPage == current) {
                        pagerState.scrollToPage(current + 1)
                    }
                } else {
                    olderExhausted = true
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                loadingOlder = false
            }
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, totalPages) {
        if (pagerState.isScrollInProgress || olderChunks.size <= 4) return@LaunchedEffect
        val current = pagerState.currentPage
        val curOffset = (totalPages - 1) - current
        var drop = 0
        while (drop < olderChunks.size && ((totalPages - 1) - drop) > curOffset + 3) drop++
        if (drop > 0) {
            olderChunks = olderChunks.drop(drop)
            onOlderDaysChanged(olderChunks.flatten())
            var streak = 0
            for (chunk in olderChunks) {
                if (chunk.all { it.totalTime == 0L }) streak++ else break
            }
            emptyStreak = streak
            olderExhausted = streak >= 12
            pagerState.scrollToPage((current - drop).coerceAtLeast(0))
        }
    }

    var hasInitializedPager by remember(history.isNotEmpty(), loaderKey) { mutableStateOf(false) }
    var lastFollowDate by remember(loaderKey) { mutableStateOf<Long?>(null) }
    val selectionTargetPage = remember(selectedDateMillis, history, olderChunks) {
        if (selectedDateMillis == null) -1
        else {
            val baseIdx = history.indexOfFirst { it.date == selectedDateMillis }
            if (baseIdx != -1) olderChunks.size + baseIdx / 7
            else {
                val olderIdx = olderChunks.flatten().indexOfFirst { it.date == selectedDateMillis }
                if (olderIdx != -1) olderIdx / 7 else -1
            }
        }
    }
    LaunchedEffect(selectionTargetPage, selectedDateMillis) {
        if (!hasInitializedPager) {
            if (history.isNotEmpty()) {
                val initial = if (selectionTargetPage != -1) selectionTargetPage
                else (history.size - 1).coerceAtLeast(0) / 7 + olderChunks.size
                pagerState.scrollToPage(initial)
                hasInitializedPager = true
            }
        } else if (selectedDateMillis != lastFollowDate &&
            selectionTargetPage != -1 && selectionTargetPage != pagerState.currentPage
        ) {
            pagerState.animateScrollToPage(
                page = selectionTargetPage,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        lastFollowDate = selectedDateMillis
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
                val pageData = chunkAt(pageIndex)
                val isOlderChunk = pageIndex < olderChunks.size
                var chunkEntered by remember(pageData.firstOrNull()?.date, pageData.size) {
                    mutableStateOf(!isOlderChunk)
                }
                LaunchedEffect(pageData.firstOrNull()?.date, pageData.size) {
                    if (isOlderChunk && !chunkEntered) {
                        kotlinx.coroutines.delay(60)
                        chunkEntered = true
                    }
                }
                val pageAnimate = animateTrigger && chunkEntered

                val pageMax = remember(pageData, targetMillis) {
                    val raw = (pageData.maxOfOrNull { it.totalTime } ?: 0L)
                        .coerceAtLeast(targetMillis)
                        .coerceAtLeast(60 * 1000L)
                    
                    if (raw >= 3600000L) {
                        val hours = raw.toFloat() / 3600000f
                        val rawStep = (hours * 1.05f) / 3f
                        val step = when {
                            rawStep <= 0.2f -> 0.2f
                            rawStep <= 0.5f -> 0.5f
                            rawStep <= 1.0f -> ceil(rawStep * 5f) / 5f
                            else -> ceil(rawStep)
                        }
                        (step * 3 * 3600000L).toLong()
                    } else {
                        val minutes = raw.toFloat() / 60000f
                        val rawStep = (minutes * 1.05f) / 3f
                        val step = when {
                            rawStep <= 5f -> 5f
                            rawStep <= 10f -> 10f
                            rawStep <= 15f -> 15f
                            else -> ceil(rawStep / 5f) * 5f
                        }
                        (step * 3 * 60000L).toLong()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(4) { i ->
                            val currentMillis = (pageMax * (3 - i) / 3)
                            val labelText = when {
                                currentMillis == 0L -> "0"
                                currentMillis >= 3600000L -> {
                                    val hours = currentMillis.toFloat() / 3600000f
                                    if (hours % 1f == 0f) "${hours.toInt()}h"
                                    else String.format(Locale.getDefault(), "%.1fh", hours)
                                }
                                else -> "${currentMillis / 60000L}m"
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(start = 2.dp)
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
                                .padding(bottom = 32.dp, start = 20.dp)
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
                            .padding(bottom = 32.dp, start = 20.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        pageData.forEach { usage ->
                            val isSelected = selectedDateMillis == usage.date
                            val targetHeight = if (pageAnimate) (usage.totalTime.toFloat() / pageMax).coerceIn(0.01f, 1f) else 0.01f
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
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        onDaySelected(if (isSelected) null else usage)
                                    }
                            ) {
                                if (isGoalAchieved && !isInside) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.62f)
                                            .aspectRatio(1f)
                                            .clip(sunnyShape)
                                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = currentAlpha)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.fillMaxSize(0.6f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .fillMaxHeight(animatedHeight)
                                        .clip(CircleShape)
                                        .background(barColor),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    if (isGoalAchieved && isInside) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .fillMaxWidth(0.68f)
                                                .aspectRatio(1f)
                                                .clip(sunnyShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.fillMaxSize(0.6f)
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
                            .padding(start = 20.dp)
                            .height(26.dp),
                        horizontalArrangement = Arrangement.SpaceAround
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
                                targetValue = if (pageAnimate) indicatorColor else indicatorColor.copy(alpha = 0f),
                                animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessVeryLow)
                            )
                            val animatedWidth by animateDpAsState(
                                targetValue = if (pageAnimate) 12.dp else 0.dp,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                            )

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
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
                                            .width(animatedWidth.coerceAtMost(8.dp))
                                            .height(if (showDatabaseIndicator) 2.dp else 0.dp)
                                            .clip(CircleShape)
                                            .background(if (showDatabaseIndicator) animatedIndicatorColor else Color.Transparent)
                                    )
                                }
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
                                pagerState.animateScrollToPage(pagerState.pageCount - 1)
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

        Spacer(modifier = Modifier.height(4.dp))
        val visibleChunk = chunkAt(pagerState.currentPage)
        val rangeLabel = remember(visibleChunk) {
            if (visibleChunk.isEmpty()) ""
            else com.etrisad.zenith.util.DateTimeUtils.formatDateRange(
                visibleChunk.first().date,
                visibleChunk.last().date
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            WeekStepperIndicator(
                rangeLabel = rangeLabel,
                showPrevious = pagerState.currentPage > 0,
                showNext = pagerState.currentPage < pagerState.pageCount - 1,
                isLoading = loadingOlder,
                onPrevious = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                },
                onNext = {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )
        }
    }
}
