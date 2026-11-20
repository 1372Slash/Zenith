package com.etrisad.zenith.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun XpHistorySection(
    days: List<XpDay>,
    totalSavedMillis: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Saved ${formatCompactDuration(totalSavedMillis)} in total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (days.isEmpty()) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No XP recorded yet - check back tomorrow",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { days.size })
            val pillListState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            var expandedDate by rememberSaveable { mutableStateOf<String?>(null) }

            LaunchedEffect(pagerState.currentPage) {
                pillListState.animateScrollToItem(
                    pagerState.currentPage.coerceIn(0, days.lastIndex)
                )
            }

            LaunchedEffect(pagerState.settledPage) {
                expandedDate = null
            }

            LazyRow(
                state = pillListState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(days, key = { _, day -> "xp-day-${day.date}" }) { index, day ->
                    XpDayPill(
                        date = day.date,
                        hasXp = day.xp > 0,
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
            ) { page ->
                val day = days[page]
                val pageDistance = (
                    (pagerState.currentPage - pagerState.currentPageOffsetFraction) - page
                    ).absoluteValue.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier.graphicsLayer {
                        val scale = 1f - pageDistance * 0.05f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - pageDistance * 0.45f
                    }
                ) {
                    XpDayPage(
                        day = day,
                        previousDay = remember(days, day.date) { xpPreviousDay(days, day) },
                        expanded = expandedDate == day.date,
                        onToggleExpanded = {
                            expandedDate = if (expandedDate == day.date) null else day.date
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "For more info, tap the info button in the header above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun XpDayPill(
    date: String,
    hasXp: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "xpDayPillContainer"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val (weekday, dayOfMonth) = remember(date) { xpDayPillParts(date) }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.width(46.dp).height(58.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = dayOfMonth,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = contentColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = if (hasXp) 1f else 0.25f))
            )
        }
    }
}

@Composable
private fun XpDayPage(
    day: XpDay,
    previousDay: XpDay?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val percentChange = remember(day.xp, previousDay?.xp) {
        xpPercentChange(day.xp, previousDay?.xp)
    }
    val sources = remember(day.sources) { day.sources.sortedByDescending { it.xp } }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "xpBreakdownChevron"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp
                    )
                ),
            shape = RoundedCornerShape(
                topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape
                    ) {
                        Text(
                            text = xpDayHeadline(day.date),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }

                    if (day.savedMillis > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Saved ${formatCompactDuration(day.savedMillis)}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily total",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (percentChange != null) {
                        XpChangePill(percentChange = percentChange)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "+${formatXp(day.xp.toLong())} XP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Card(
                onClick = onToggleExpanded,
                shape = if (expanded) {
                    RoundedCornerShape(8.dp)
                } else {
                    RoundedCornerShape(
                        topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp
                    )
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "XP Breakdown",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + expandVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (sources.isEmpty()) {
                        XpGroupedRow(
                            shape = RoundedCornerShape(
                                topStart = 8.dp, topEnd = 8.dp,
                                bottomStart = 24.dp, bottomEnd = 24.dp
                            )
                        ) {
                            Text(
                                text = "No detailed breakdown was saved for this day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        sources.forEachIndexed { index, source ->
                            XpGroupedRow(shape = xpGroupedRowShape(index, sources.size)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = source.label,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${formatXp(source.xp.toLong())}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1
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
private fun XpGroupedRow(
    shape: Shape,
    content: @Composable () -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun XpChangePill(percentChange: Int) {
    val isUp = percentChange >= 0
    val contentColor = if (isUp) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.error
    Surface(
        color = contentColor.copy(alpha = 0.15f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (isUp) Icons.AutoMirrored.Outlined.TrendingUp
                else Icons.AutoMirrored.Outlined.TrendingDown,
                contentDescription = if (isUp) "More than yesterday" else "Less than yesterday",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "${if (isUp) "+" else ""}$percentChange%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

private fun xpGroupedRowShape(index: Int, total: Int): Shape = when {
    index == total - 1 -> RoundedCornerShape(
        topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp
    )
    else -> RoundedCornerShape(8.dp)
}

private fun xpPreviousDay(days: List<XpDay>, day: XpDay): XpDay? {
    val millis = parseXpDayMillis(day.date) ?: return null
    val previousMillis = millis - 86_400_000L
    return days.firstOrNull { it.date != day.date && parseXpDayMillis(it.date) == previousMillis }
}

private fun xpPercentChange(current: Int, previous: Int?): Int? {
    if (previous == null || previous <= 0) return null
    return ((current - previous) * 100.0 / previous).roundToInt()
}

private fun xpDayHeadline(raw: String): String {
    val millis = parseXpDayMillis(raw) ?: return raw
    val today = startOfDayMillis(System.currentTimeMillis())
    val day = startOfDayMillis(millis)
    return when {
        day == today -> "Today"
        day == today - 86_400_000L -> "Yesterday"
        else -> prettyProfileDate(raw)
    }
}

private fun xpDayPillParts(raw: String): Pair<String, String> {
    val millis = parseXpDayMillis(raw) ?: return raw.take(1) to ""
    val weekdayFormat = SimpleDateFormat("EEEEE", Locale.getDefault())
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    val date = Date(millis)
    return weekdayFormat.format(date).uppercase(Locale.getDefault()) to dayFormat.format(date)
}

private fun parseXpDayMillis(raw: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(raw)?.time
} catch (_: Exception) {
    null
}

private fun startOfDayMillis(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
