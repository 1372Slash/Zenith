package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import com.etrisad.zenith.ui.components.focus.appIconShape
import com.etrisad.zenith.ui.viewmodel.AppUsageInfo
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import com.etrisad.zenith.ui.viewmodel.StatsRange

/**
 * Shared long-term statistics card used by both the global "Long-term Summary"
 * (Usage Stats) and the per-app "Long-term for this app" (App Detail).
 *
 * Visuals follow the existing app language; the shared parts are the period
 * navigator ([WeekStepperIndicator]), smooth pill weekday bars and breathing
 * heatmap cells. Accent colors differ per usage (primary / tertiary).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LongTermSection(
    title: String,
    accentColor: Color,
    highlightColor: Color,
    selectedRange: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
    rangeLabel: String,
    canGoNewer: Boolean,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    totalMillis: Long,
    prevTotal: Long? = null,
    dailyHistory: List<DailyUsage>,
    heatmapEmptyText: String,
    formatDuration: (Long) -> String,
    periodDays: List<Long> = emptyList(),
    dayAppLoader: (suspend (Long) -> List<AppUsageInfo>)? = null,
    onAppClick: (String) -> Unit = {},
    getAppType: (String) -> String? = { null },
    dataNote: String? = null
) {
    val safeMaxDaily = remember(dailyHistory) { (dailyHistory.maxOfOrNull { it.totalTime } ?: 1L).coerceAtLeast(1L) }

    val dayMap = remember(dailyHistory) { dailyHistory.associateBy { it.date } }
    var selectedDay by remember(periodDays) { mutableStateOf<Long?>(null) }
    var selectedWeek by remember(periodDays) { mutableStateOf<List<Long>?>(null) }
    var weekApps by remember(periodDays) { mutableStateOf(emptyList<AppUsageInfo>()) }
    var loadingWeek by remember(periodDays) { mutableStateOf(false) }
    var dayListExpanded by remember(selectedWeek) { mutableStateOf(false) }
    // Snapshots of the open week/day: AnimatedVisibility content recomposes with the
    // latest state even while the out-animation plays, so without these the detail
    // would read nulls and the close would fade emptiness (abrupt close).
    var lastOpenWeek by remember(periodDays) { mutableStateOf<List<Long>?>(null) }
    var lastOpenDay by remember(periodDays) { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedWeek, selectedDay) {
        if (selectedWeek != null) {
            lastOpenWeek = selectedWeek
            lastOpenDay = selectedDay
        }
    }
    LaunchedEffect(selectedWeek) {
        val week = selectedWeek
        val loader = dayAppLoader
        if (loader == null) {
            weekApps = emptyList()
            loadingWeek = false
        } else if (week == null) {
            // Closing: freeze the last loaded apps instead of wiping them. Clearing
            // here would change the inner crossfade key on the exact frame the
            // out-animation starts, so the list would vanish before the collapse
            // finishes. No stale data leaks: opening a week always sets loadingWeek
            // optimistically and the loader branch below overwrites weekApps, and
            // period changes reset this state via remember(periodDays).
        } else {
            loadingWeek = true
            try {
                val perDay = week.map { day -> async { loader(day) } }.awaitAll()
                weekApps = perDay.flatten().groupBy { it.packageName }
                    .map { (_, items) ->
                        items.first().copy(totalTimeVisible = items.sumOf { it.totalTimeVisible })
                    }
                    .sortedByDescending { it.totalTimeVisible }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                weekApps = emptyList()
            } finally {
                loadingWeek = false
            }
        }
    }
    val weeks = remember(periodDays, selectedRange) {
        buildCalWeeks(periodDays, if (selectedRange == StatsRange.YEARLY) "MMM" else "MMM yyyy")
    }
    val weekByDay = remember(weeks) {
        weeks.flatMap { w -> w.days.filterNotNull().map { it to w.days.filterNotNull() } }.toMap()
    }
    val periodStatTotals = remember(dayMap, periodDays) { periodDays.map { dayMap[it]?.totalTime ?: 0L } }
    val weekStatTotals = remember(selectedWeek, dayMap) { selectedWeek?.map { dayMap[it]?.totalTime ?: 0L } ?: emptyList() }
    val eeFmt = remember { java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH) }
    val dayDateFmt = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH) }

    val selectedWeekValue = selectedWeek
    // Header total follows the week selection; otherwise the period total.
    val headerTotal = if (selectedWeekValue != null) {
        formatDuration(selectedWeekValue.sumOf { dayMap[it]?.totalTime ?: 0L })
    } else {
        formatDuration(totalMillis)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.TrackChanges, null, tint = accentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(headerTotal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accentColor)
        }
        Spacer(modifier = Modifier.height(12.dp))
        ZenithToggleButtonGroup(
            options = listOf(
                ZenithToggleOption(text = "Monthly"),
                ZenithToggleOption(text = "Yearly")
            ),
            selectedIndices = setOf(
                when (selectedRange) {
                    StatsRange.MONTHLY -> 0
                    else -> 1
                }
            ),
            onToggle = { idx ->
                onRangeSelected(
                    when (idx) {
                        0 -> StatsRange.MONTHLY
                        else -> StatsRange.YEARLY
                    }
                )
            },
            isInsideContainer = true,
            isScalingEnabled = false,
            showTextSelected = false
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            WeekStepperIndicator(
                rangeLabel = rangeLabel,
                showPrevious = true,
                showNext = canGoNewer,
                isLoading = false,
                onPrevious = onPreviousPeriod,
                onNext = onNextPeriod
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        run {
            val weekMode = selectedWeek != null
            val totals = if (weekMode) weekStatTotals else periodStatTotals
            val total = totals.sum()
            val avg = if (totals.isNotEmpty()) total / totals.size else 0L
            val bestIdx = totals.indices.filter { totals[it] > 0L }.minByOrNull { totals[it] }
            val bestVal = bestIdx?.let { totals[it] } ?: 0L
            val bestMillis = bestIdx?.let {
                if (weekMode) selectedWeek?.getOrNull(it) else periodDays.getOrNull(it)
            }
            val deltaPct = if (prevTotal != null && prevTotal > 0L) {
                (totalMillis - prevTotal).toFloat() / prevTotal
            } else null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatTile(
                    caption = "Daily avg",
                    value = formatDuration(avg),
                    index = 0,
                    total = 4,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    caption = "Best day",
                    value = if (bestVal > 0L && bestMillis != null) {
                        "${eeFmt.format(java.util.Date(bestMillis))} ${formatShortDuration(bestVal)}"
                    } else "-",
                    index = 1,
                    total = 4,
                    modifier = Modifier.weight(1f),
                    onClick = if (bestVal > 0L && bestMillis != null) {
                        {
                            if (selectedDay == bestMillis) {
                                selectedDay = null
                                selectedWeek = null
                            } else {
                                weekByDay[bestMillis]?.let {
                                    selectedDay = bestMillis
                                    selectedWeek = it
                                }
                            }
                        }
                    } else null
                )
                StatTile(
                    caption = "Active",
                    value = if (totals.isEmpty()) "-" else "${totals.count { it > 0L }}/${totals.size}",
                    index = 2,
                    total = 4,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    caption = "vs last",
                    value = deltaPct?.let {
                        val r = Math.round(it * 100)
                        if (r > 0) "+$r%" else if (r < 0) "-$r%" else "0%"
                    } ?: "-",
                    index = 3,
                    total = 4,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(
            targetState = dailyHistory,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessLow)) + expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)))
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessLow)) + shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)))
            },
            label = "longTermHeatmap"
        ) { history ->
            // No animateContentSize here on purpose: the expand/shrink
            // AnimatedVisibility below drives resizing directly. Nesting
            // another size animation (like other cards avoid) makes the
            // parent card lag seconds behind the collapsing content.
            Column(modifier = Modifier.fillMaxWidth()) {
                if (history.isEmpty() || periodDays.isEmpty()) {
                    Text(heatmapEmptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // Calendar grid: weekday gutter + month headers + week columns,
                    // Monday-first like the weekly period convention.
                    val calCell = when (selectedRange) {
                        StatsRange.YEARLY -> 16.dp
                        else -> 30.dp
                    }
                    val calGap = if (selectedRange == StatsRange.YEARLY) 3.dp else 4.dp
                    val calRadius = if (selectedRange == StatsRange.YEARLY) 4.dp else 5.dp
                    val calScroll = rememberScrollState()
                    // Hidden until positioned: otherwise yearly flashes January
                    // for a frame before jumping to December (effect races a
                    // heavy 365-cell first composition).
                    var calReady by remember(selectedRange, periodDays) { mutableStateOf(false) }
                    LaunchedEffect(selectedRange, periodDays) {
                        if (selectedRange == StatsRange.YEARLY) calScroll.scrollTo(Int.MAX_VALUE)
                        calReady = true
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(calGap)) {
                            Spacer(modifier = Modifier.height(20.dp))
                            CalWeekdayLetters.forEach { letter ->
                                Box(
                                    modifier = Modifier.size(calCell)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        letter,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(calScroll)
                                    .alpha(if (calReady) 1f else 0f)
                            ) {
                            Column(verticalArrangement = Arrangement.spacedBy(calGap)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(calGap)) {
                                    weeks.forEach { week ->
                                        Box(
                                            modifier = Modifier.width(calCell).height(20.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Text(
                                                week.monthLabel ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                for (row in 0..6) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(calGap)) {
                                        weeks.forEach { week ->
                                            val millis = week.days.getOrNull(row)
                                            if (millis == null) {
                                                Spacer(modifier = Modifier.size(calCell))
                                            } else {
                                                val total = dayMap[millis]?.totalTime ?: 0L
                                                val intensity = (total.toFloat() / safeMaxDaily).coerceIn(0f, 1f)
                                                val alpha = when {
                                                    intensity == 0f -> 0.08f
                                                    intensity < 0.25f -> 0.25f
                                                    intensity < 0.5f -> 0.5f
                                                    intensity < 0.75f -> 0.75f
                                                    else -> 1f
                                                }
                                                val isDaySelected = selectedDay == millis
                                                // Instant dim on purpose: selection feedback must be
                                                // immediate, and a grid-wide spring storm drops frames.
                                                // Only the tapped cell pops (2 cells max animate) - same
                                                // budget applies to the highlight color morph below.
                                                val dimFactor = if (selectedWeek != null && selectedWeek?.contains(millis) != true) 0.35f else 1f
                                                val highlightBase by animateColorAsState(
                                                    targetValue = if (isDaySelected) highlightColor else accentColor,
                                                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                                                    label = "HeatmapHighlight"
                                                )
                                                val selectedPop by animateFloatAsState(
                                                    targetValue = if (isDaySelected) 1.12f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "LongTermCalPop"
                                                )
                                                Box(
                                                    modifier = Modifier.size(calCell)
                                                        .graphicsLayer {
                                                            scaleX = selectedPop
                                                            scaleY = selectedPop
                                                        }
                                                        .clip(RoundedCornerShape(calRadius))
                                                        .background(highlightBase.copy(alpha = (alpha * dimFactor).coerceIn(0f, 1f)))
                                                        .border(
                                                            width = if (isDaySelected) 2.dp else 0.dp,
                                                            color = if (isDaySelected) Color.White else Color.Transparent,
                                                            shape = RoundedCornerShape(calRadius)
                                                        )
                                                        .clickable {
                                                            if (selectedDay == millis) {
                                                                selectedDay = null
                                                                selectedWeek = null
                                                            } else {
                                                                selectedDay = millis
                                                                // Same-week taps only move the ring: the week's
                                                                // list is already showing, so skip the reload
                                                                // (the loader effect only restarts on week
                                                                // change - reloading here would spin forever).
                                                                val newWeek = weekByDay[millis]
                                                                if (newWeek != selectedWeek) {
                                                                    selectedWeek = newWeek
                                                                    // Optimistic: show the spinner immediately so the
                                                                    // panel never flashes stale rows first.
                                                                    loadingWeek = true
                                                                }
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                            val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            val scope = rememberCoroutineScope()
                            val calStep = with(LocalDensity.current) { ((calCell + calGap) * 8).toPx().toInt() }
                            val backAlpha by animateFloatAsState(
                                targetValue = if (calScroll.canScrollBackward) 1f else 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "CalBackAlpha"
                            )
                            val fwdAlpha by animateFloatAsState(
                                targetValue = if (calScroll.canScrollForward) 1f else 0f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "CalFwdAlpha"
                            )
                            if (calScroll.canScrollBackward) {
                                Box(
                                    modifier = Modifier.align(Alignment.CenterStart)
                                        .width(24.dp)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(Color.Transparent, fadeColor)))
                                )
                            }
                            if (calScroll.canScrollForward) {
                                Box(
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .width(24.dp)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(fadeColor, Color.Transparent)))
                                )
                            }
                            if (backAlpha > 0.01f) {
                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch {
                                            calScroll.animateScrollTo((calScroll.value - calStep).coerceAtLeast(0))
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterStart)
                                        .padding(start = 4.dp)
                                        .size(32.dp)
                                        .graphicsLayer { alpha = backAlpha },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Scroll to earlier weeks",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (fwdAlpha > 0.01f) {
                                FilledTonalIconButton(
                                    onClick = {
                                        scope.launch {
                                            calScroll.animateScrollTo((calScroll.value + calStep).coerceAtMost(calScroll.maxValue))
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .padding(end = 4.dp)
                                        .size(32.dp)
                                        .graphicsLayer { alpha = fwdAlpha },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Scroll to later weeks",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    val patternNote = remember(dayMap, periodDays) { weekendPatternOf(dayMap, periodDays) }
                    if (patternNote != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            patternNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (dataNote != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            dataNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(
                        visible = selectedWeek != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        label = "HeatmapDayDetail"
                    ) {
                        // Fall back to the snapshots while closing: otherwise the detail
                        // below would read nulls and the out-animation would fade emptiness.
                        val week = (selectedWeek ?: lastOpenWeek)?.takeIf { it.isNotEmpty() }
                        val openDay = selectedDay ?: lastOpenDay
                        if (week != null) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                // Identity-only key: week window + (per-app only) day + list
                                // data state. Same-week day taps on the global version keep
                                // the key identical, so the unchanged list never replays.
                                // Fade-only on purpose: the outer visibility already drives
                                // resizing, and a second size/slide animation nested inside
                                // fights it (abrupt jumps).
                                val detailKey = Triple(
                                    week,
                                    if (dayAppLoader == null) openDay else null,
                                    DayListState(loadingWeek, weekApps)
                                )
                                AnimatedContent(
                                    targetState = detailKey,
                                    transitionSpec = {
                                        fadeIn() togetherWith fadeOut() using SizeTransform(
                                            clip = false,
                                            sizeAnimationSpec = { _, _ -> snap() }
                                        )
                                    },
                                    label = "DayAppsContent"
                                ) { (animWeek, animDay, animList) ->
                                    Column {
                                    if (dayAppLoader != null) {
                                        Text(
                                            "Apps - " + com.etrisad.zenith.util.DateTimeUtils.formatDateRange(animWeek.first(), animWeek.last()),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        val dayMillis = animDay
                                        if (dayMillis != null) {
                                            Text(
                                                dayDateFmt.format(java.util.Date(dayMillis)),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                Text(
                                                    formatDuration(dayMap[dayMillis]?.totalTime ?: 0L),
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = accentColor
                                                )
                                                Text(
                                                    "- ${formatDuration(animWeek.sumOf { dayMap[it]?.totalTime ?: 0L })} this week",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (dayAppLoader != null && animList.loading) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = accentColor
                                            )
                                        }
                                    } else if (dayAppLoader != null && animList.apps.isEmpty()) {
                                        Text(
                                            "No app data for this week",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                } else if (dayAppLoader != null) {
                                    val topApps = animList.apps.take(3)
                                    val restApps = animList.apps.drop(3)
                                    val restTotal = restApps.sumOf { it.totalTimeVisible }
                                    // Grouped positions mirror the Other-Apps pattern in Usage Stats:
                                    // the header card continues the first/mid/last sequence.
                                    val groupTotal = if (restApps.isEmpty()) topApps.size
                                    else 3 + 1 + (if (dayListExpanded) restApps.size else 0)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        topApps.forEachIndexed { index, app ->
                                            DayAppRow(
                                                app = app,
                                                index = index,
                                                total = groupTotal,
                                                timeLabel = formatDuration(app.totalTimeVisible),
                                                type = getAppType(app.packageName),
                                                onAppClick = onAppClick
                                            )
                                        }
                                        if (restApps.isNotEmpty()) {
                                            Card(
                                                onClick = { dayListExpanded = !dayListExpanded },
                                                shape = longTermAppRowShape(3, groupTotal),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            ) {
                                                ListItem(
                                                    headlineContent = {
                                                        Text(
                                                            text = "Other Apps",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    },
                                                    supportingContent = {
                                                        Text(
                                                            text = "${restApps.size} more apps",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    },
                                                    trailingContent = {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = formatDuration(restTotal),
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                            Icon(
                                                                imageVector = if (dayListExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(20.dp).padding(start = 4.dp),
                                                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                            )
                                                        }
                                                    },
                                                    leadingContent = {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(accentColor.copy(alpha = 0.1f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Android,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(24.dp),
                                                                tint = accentColor
                                                            )
                                                        }
                                                    },
                                                    colors = ListItemDefaults.colors(
                                                        containerColor = Color.Transparent
                                                    )
                                                )
                                            }
                                            AnimatedVisibility(
                                                visible = dayListExpanded,
                                                enter = fadeIn() + expandVertically(),
                                                exit = fadeOut() + shrinkVertically(),
                                                label = "DayRestApps"
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                restApps.forEachIndexed { index, app ->
                                                    DayAppRow(
                                                        app = app,
                                                        index = 4 + index,
                                                        total = groupTotal,
                                                        timeLabel = formatDuration(app.totalTimeVisible),
                                                        type = getAppType(app.packageName),
                                                        onAppClick = onAppClick
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
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

private val CalWeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

private data class CalWeek(val days: List<Long?>, val monthLabel: String?)

/** Data snapshot driving the detail crossfade: loading flag + week app list. */
private data class DayListState(
    val loading: Boolean,
    val apps: List<AppUsageInfo>
)

/** Monday-first week columns for the calendar heatmap, padded with nulls. */
private fun buildCalWeeks(days: List<Long>, monthPattern: String = "MMM yyyy"): List<CalWeek> {
    if (days.isEmpty()) return emptyList()
    val monthFmt = java.text.SimpleDateFormat(monthPattern, java.util.Locale.ENGLISH)
    val cal = java.util.Calendar.getInstance()
    fun mondayBasedDow(millis: Long): Int {
        cal.timeInMillis = millis
        return when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> 7
            else -> cal.get(java.util.Calendar.DAY_OF_WEEK) - 1
        }
    }
    val leadPads = mondayBasedDow(days.first()) - 1
    val padded: List<Long?> = List(leadPads) { null } + days
    val trailPads = (7 - padded.size % 7) % 7
    val full = padded + List(trailPads) { null }
    val chunked = full.chunked(7)
    var prevMonth: String? = null
    return chunked.mapIndexed { index, weekDays ->
        val month = weekDays.firstOrNull { it != null }?.let { monthFmt.format(java.util.Date(it)) }
        val label = if (index == 0 || (month != null && month != prevMonth)) month else null
        prevMonth = month ?: prevMonth
        CalWeek(weekDays, label)
    }
}


/**
 * Grouped first/mid/last shape with animated corners, so expanding or
 * collapsing the day list morphs each card instead of snapping it.
 * Same per-corner animateDpAsState precedent as ActiveItemCard/AlarmScreen.
 */
@Composable
private fun longTermAppRowShape(index: Int, total: Int): RoundedCornerShape {
    val isSingle = total == 1
    val isFirst = index == 0
    val isLast = index == total - 1
    val topStart by animateDpAsState(
        targetValue = if (isSingle || isFirst) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "longTermTopStart"
    )
    val topEnd by animateDpAsState(
        targetValue = if (isSingle || isFirst) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "longTermTopEnd"
    )
    val bottomStart by animateDpAsState(
        targetValue = if (isSingle || isLast) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "longTermBottomStart"
    )
    val bottomEnd by animateDpAsState(
        targetValue = if (isSingle || isLast) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "longTermBottomEnd"
    )
    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )
}


@Composable
private fun DayAppRow(
    app: AppUsageInfo,
    index: Int,
    total: Int,
    timeLabel: String,
    type: String?,
    onAppClick: (String) -> Unit
) {
    Card(
        onClick = { onAppClick(app.packageName) },
        shape = longTermAppRowShape(index, total),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        "WEBSITE" -> "Website"
                        else -> "Other"
                    }
                    val isWebsitePackage = app.packageName.startsWith("zenith-web:")
                    if (isWebsitePackage && (type == "SHIELD" || type == "GOAL")) {
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Website",
                                tint = badgeColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
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
                    text = timeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            leadingContent = {
                val isWebsite = app.packageName.startsWith("zenith-web:")
                val shape = appIconShape(isWebsite)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data("app-icon://${app.packageName}").crossfade(500).build(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).then(
                        if (isWebsite) Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape
                        ) else Modifier
                    ).clip(shape),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(if (isWebsite) Icons.Outlined.Language else Icons.Outlined.Android, null, modifier = Modifier.size(24.dp))
                        }
                    }
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}


@Composable
private fun StatTile(
    caption: String,
    value: String,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = when {
        total == 1 -> RoundedCornerShape(16.dp)
        index == 0 -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        index == total - 1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(8.dp)
    }

    @Composable
    fun Content() {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(spring(stiffness = Spring.StiffnessLow)))
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(spring(stiffness = Spring.StiffnessLow)))
                },
                label = "StatTileValue"
            ) { animatedValue ->
                Text(
                    animatedValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Content()
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Content()
        }
    }
}

private fun formatShortDuration(millis: Long): String = when {
    millis <= 0L -> "0"
    millis >= 3600000L -> {
        val hours = millis.toFloat() / 3600000f
        if (hours % 1f == 0f) "${hours.toInt()}h" else String.format(java.util.Locale.ENGLISH, "%.1fh", hours)
    }
    else -> "${millis / 60000L}m"
}


private fun weekendPatternOf(dayMap: Map<Long, com.etrisad.zenith.ui.viewmodel.DailyUsage>, periodDays: List<Long>): String? {
    if (periodDays.size < 14) return null
    val cal = java.util.Calendar.getInstance()
    var weekendSum = 0L
    var weekendN = 0
    var weekdaySum = 0L
    var weekdayN = 0
    for (d in periodDays) {
        cal.timeInMillis = d
        val weekend = cal.get(java.util.Calendar.DAY_OF_WEEK).let { it == java.util.Calendar.SATURDAY || it == java.util.Calendar.SUNDAY }
        val v = dayMap[d]?.totalTime ?: 0L
        if (weekend) { weekendSum += v; weekendN++ } else { weekdaySum += v; weekdayN++ }
    }
    if (weekendN == 0 || weekdayN == 0) return null
    val we = weekendSum.toDouble() / weekendN
    val wd = weekdaySum.toDouble() / weekdayN
    if (we == 0.0 && wd == 0.0) return null
    if (wd == 0.0) return "Only used on weekends"
    if (we == 0.0) return "Only used on weekdays"
    fun ratio(r: Double): String = String.format(java.util.Locale.ENGLISH, "%.1f", r)
    return when {
        we > wd * 1.3 -> "Weekends ${ratio(we / wd)}x higher"
        wd > we * 1.3 -> "Weekdays ${ratio(wd / we)}x higher"
        else -> null
    }
}

