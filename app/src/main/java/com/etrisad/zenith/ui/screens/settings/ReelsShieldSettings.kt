package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.MAX_REELS_SHIELD_DAILY_LIMIT
import com.etrisad.zenith.data.preferences.ReelsShieldDefaults
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.AppIconImage
import com.etrisad.zenith.ui.components.UsageHistoryCard
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.viewmodel.DailyUsage
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val MIN_REELS_LIMIT = 5
private val MAX_REELS_LIMIT = MAX_REELS_SHIELD_DAILY_LIMIT
private const val HISTORY_DAYS_SHOWN = 14

private val reelsAppLabelCache = ConcurrentHashMap<String, String>()
@Composable
fun ReelsShieldPoolCard(
    limit: Int,
    onLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeLimit = limit.coerceIn(MIN_REELS_LIMIT, MAX_REELS_LIMIT)
    var text by remember(safeLimit) { mutableStateOf(safeLimit.toString()) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        val next = text.toIntOrNull()?.coerceIn(MIN_REELS_LIMIT, MAX_REELS_LIMIT)
            ?: safeLimit
        text = next.toString()
        if (next != safeLimit) onLimitChange(next)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Reels per day",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                            text = newValue
                        }
                    },
                    textStyle = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            commit()
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) commit()
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        ZenithGroupedButton(size = ZenithButtonSize.Small) {
            val presets = listOf(10, 20, 30, 50)
            presets.forEachIndexed { index, preset ->
                val isSelected = text.toIntOrNull() == preset
                val pShape = when (index) {
                    0 -> RoundedCornerShape(
                        bottomStart = 28.dp,
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    )
                    presets.lastIndex -> RoundedCornerShape(
                        bottomEnd = 28.dp,
                        topEnd = 8.dp,
                        topStart = 8.dp,
                        bottomStart = 8.dp
                    )
                    else -> RoundedCornerShape(8.dp)
                }
                ZenithButtonWeighted(
                    onClick = {
                        text = preset.toString()
                        if (preset != safeLimit) onLimitChange(preset)
                    },
                    text = preset.toString(),
                    type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    selected = isSelected,
                    shape = pShape,
                    isFirst = index == 0,
                    isLast = index == presets.lastIndex,
                    contentScaleEnabled = false
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Each app can be capped below this pool. The stricter limit is the one enforced.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
@Composable
fun ReelsShieldAppsCard(
    trackedPackages: Set<String>,
    appLimits: Map<String, Int>,
    poolLimit: Int,
    todayByApp: Map<String, Int>,
    onAppClick: (packageName: String, label: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val installedPackages = rememberInstalledPackages(trackedPackages)
    val labels = rememberAppLabels(installedPackages)
    val pool = poolLimit.coerceIn(1, MAX_REELS_LIMIT)
    val packages = remember(installedPackages, labels, todayByApp, appLimits, pool) {
        installedPackages.sortedWith(
            compareByDescending<String> { todayByApp[it] ?: 0 }
                .thenBy { labels[it]?.lowercase() ?: it }
        )
    }
    val customCaps = packages.count { pkg ->
        (appLimits[pkg] ?: pool).coerceIn(1, pool) < pool
    }
    val totalCards = packages.size + 1

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ReelsGroupedCard(index = 0, total = totalCards) {
            Column {
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
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Per-app limits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Today by app · tap an app to set its cap",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (customCaps == 0) "Pool only" else "$customCaps custom",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }

                if (packages.isEmpty()) {
                    Text(
                        text = "No installed tracked apps yet. Pick apps under Tracked Apps first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
            }
        }

        packages.forEachIndexed { index, pkg ->
            val label = labels[pkg] ?: ReelsShieldDefaults.fallbackLabel(pkg)
            val used = todayByApp[pkg] ?: 0
            val cap = (appLimits[pkg] ?: pool).coerceIn(1, pool)
            val capped = cap < pool

            ReelsGroupedCard(
                index = index + 1,
                total = totalCards,
                onClick = { onAppClick(pkg, label) }
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (used == 1) "1 reel today" else "$used reels today",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (used >= cap) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        AppIconImage(
                            packageName = pkg,
                            modifier = Modifier.size(40.dp),
                            contentDescription = label
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (capped) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ) {
                                    Text(
                                        text = if (capped) "$cap / day" else "Pool only",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (capped) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                                if (ReelsShieldDefaults.isUntested(pkg)) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "Untested",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (ReelsShieldDefaults.isPartial(pkg)) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "Partial",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

/**
 * BedtimeScreen's GroupedCard shape, kept local so the Reels Shield list has
 * the same 24 dp outer / 8 dp inner corners without importing another screen.
 * The history chart card uses it too, so chart + detail read as one group
 * exactly like Usage Stats' hourly chart group.
 */
internal fun reelsGroupShape(index: Int, total: Int): Shape = when {
    total <= 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 8.dp,
        bottomEnd = 8.dp
    )
    index == total - 1 -> RoundedCornerShape(
        topStart = 8.dp,
        topEnd = 8.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    else -> RoundedCornerShape(8.dp)
}

@Composable
private fun ReelsGroupedCard(
    index: Int,
    total: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    val shape = reelsGroupShape(index, total)

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

/** Compact editor styled after the Shield settings bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsShieldAppLimitSheet(
    packageName: String,
    label: String,
    usedToday: Int,
    savedLimit: Int,
    poolLimit: Int,
    onDismiss: () -> Unit,
    onLimitChange: (Int) -> Unit
) {
    val pool = poolLimit.coerceIn(1, MAX_REELS_LIMIT)
    val safeSaved = savedLimit.coerceIn(1, pool)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var text by remember(packageName, safeSaved, pool) { mutableStateOf(safeSaved.toString()) }
    val parsed = text.toIntOrNull()?.coerceIn(1, pool)

    fun commitLocal() {
        val next = parsed ?: safeSaved
        text = next.toString()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIconImage(
                    packageName = packageName,
                    modifier = Modifier.size(72.dp),
                    contentDescription = label
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Reels Shield limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (usedToday == 1) "1 reel today" else "$usedToday reels today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd = 8.dp
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reels per day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                                text = newValue
                            }
                        },
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                commitLocal()
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) commitLocal()
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            ZenithGroupedButton(size = ZenithButtonSize.Small) {
                val presets = listOf(5, 10, 15, pool).distinct().sorted()
                presets.forEachIndexed { index, preset ->
                    val isSelected = parsed == preset
                    val pShape = when (index) {
                        0 -> RoundedCornerShape(
                            bottomStart = 28.dp,
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomEnd = 8.dp
                        )
                        presets.lastIndex -> RoundedCornerShape(
                            bottomEnd = 28.dp,
                            topEnd = 8.dp,
                            topStart = 8.dp,
                            bottomStart = 8.dp
                        )
                        else -> RoundedCornerShape(8.dp)
                    }
                    ZenithButtonWeighted(
                        onClick = {
                            text = preset.coerceIn(1, pool).toString()
                        },
                        text = preset.toString(),
                        type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                        size = ZenithButtonSize.Small,
                        selected = isSelected,
                        shape = pShape,
                        isFirst = index == 0,
                        isLast = index == presets.lastIndex,
                        contentScaleEnabled = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Shared pool: $pool per day. Set $pool to use only the shared pool; values are clamped to 1–$pool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            ZenithButton(
                onClick = {
                    val next = parsed ?: safeSaved
                    onLimitChange(next)
                    scope.launch { sheetState.hide() }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                text = "Save Limit",
                enabled = parsed != null
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Daily swipe counts for the last [HISTORY_DAYS_SHOWN] days, oldest first.
 * Counts are mapped to one minute per swipe so the shared usage graph, which
 * works in millis, can render them without a second chart implementation.
 */
internal fun buildReelsHistory(preferences: UserPreferences): List<DailyUsage> {
    val dateKeyFormat = DateTimeUtils.getDateFormat()
    val todayStart = DateTimeUtils.getDayStartTime(
        System.currentTimeMillis(),
        preferences.dayStartHour,
        preferences.dayStartMinute
    )
    val todayKey = dateKeyFormat.format(Date(todayStart))
    val history = preferences.reelsShieldHistory

    return (HISTORY_DAYS_SHOWN - 1 downTo 0).map { offset ->
        val dayStart = DateTimeUtils.getDayStartTime(
            todayStart - offset * 86_400_000L,
            preferences.dayStartHour,
            preferences.dayStartMinute
        )
        val key = dateKeyFormat.format(Date(dayStart))
        val count = if (key == todayKey) {
            if (preferences.reelsShieldTodayDate == todayKey) preferences.reelsShieldTodayCount else 0
        } else {
            history[key] ?: 0
        }
        DailyUsage(
            date = dayStart,
            totalTime = count.toLong() * 60_000L,
            hasDatabaseRecord = count > 0,
            isLive = offset == 0
        )
    }
}

data class ReelsHistoryAppCount(
    val packageName: String,
    val count: Int
)

data class ReelsHistoryDayDetail(
    val date: Long,
    val dateKey: String,
    val isToday: Boolean,
    val dayCount: Int,
    val apps: List<ReelsHistoryAppCount>
)

/** Per-app split for a tapped history bar, including legacy days without data. */
internal fun buildReelsHistoryDayDetail(
    preferences: UserPreferences,
    entries: List<DailyUsage>,
    date: Long
): ReelsHistoryDayDetail {
    val dateFormat = DateTimeUtils.getDateFormat()
    val dateKey = dateFormat.format(Date(date))
    val todayKey = entries.lastOrNull()?.date?.let { dateFormat.format(Date(it)) }
    val isToday = dateKey == todayKey
    val dayCount = ((entries.find { it.date == date }?.totalTime ?: 0L) / 60_000L).toInt()
    val byApp = if (isToday && preferences.reelsShieldTodayDate == dateKey) {
        preferences.reelsShieldTodayByApp
    } else {
        preferences.reelsShieldHistoryByApp[dateKey].orEmpty()
    }
    val apps = byApp.entries
        .filter { it.value > 0 }
        .sortedByDescending { it.value }
        .map { ReelsHistoryAppCount(it.key, it.value) }

    return ReelsHistoryDayDetail(
        date = date,
        dateKey = dateKey,
        isToday = isToday,
        dayCount = dayCount,
        apps = apps
    )
}

@Composable
fun ReelsShieldHistoryCard(
    history: List<DailyUsage>,
    limit: Int,
    selectedDateMillis: Long?,
    onDaySelected: (Long?) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    UsageHistoryCard(
        history = history,
        targetMillis = limit.coerceAtLeast(1).toLong() * 60_000L,
        // A non-null sentinel keeps the chart's internal selection disabled so
        // the tapped bar and the detail items stay in sync.
        selectedDateMillis = selectedDateMillis ?: Long.MIN_VALUE,
        formatDuration = { millis ->
            val count = millis / 60_000L
            if (count == 1L) "1 reel" else "$count reels"
        },
        onDaySelected = { usage -> onDaySelected(usage?.date) },
        axisLabelFormatter = { millis -> (millis / 60_000L).toString() },
        showGoalMarkers = false,
        shape = shape,
        containerColor = containerColor,
        title = "Reels History",
        modifier = modifier
    )
}

/**
 * Same header/row construction as UsageStats' "Apps used at hour" detail:
 * primary-tinted grouped cards, History icon, and the app rows underneath.
 * [index] starts at 1 because the history chart card is index 0 of the same
 * group, exactly like the hourly chart above the detail in Usage Stats.
 */
@Composable
fun ReelsShieldHistoryDetailHeader(
    detail: ReelsHistoryDayDetail,
    index: Int = 1,
    total: Int = 1,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val dateLabel = remember(detail.date, detail.isToday) {
        if (detail.isToday) "Today"
        else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(detail.date))
    }

    ReelsGroupedCard(
        index = index,
        total = total,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
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
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Apps scrolled · $dateLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (detail.dayCount == 1) "1 reel" else "${detail.dayCount} reels",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReelsShieldHistoryEmptyDetail(
    detail: ReelsHistoryDayDetail,
    index: Int = 2,
    total: Int = 1,
    modifier: Modifier = Modifier
) {
    ReelsGroupedCard(
        index = index,
        total = total,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        Text(
            text = if (detail.dayCount > 0) {
                "Per-app detail wasn't recorded for this day."
            } else {
                "No reels on this day."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun ReelsShieldHistoryAppRow(
    packageName: String,
    label: String,
    count: Int,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val accentColor = MaterialTheme.colorScheme.primary

    ReelsGroupedCard(
        index = index,
        total = total,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (count == 1) "1 reel" else "$count reels",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    if (ReelsShieldDefaults.isUntested(packageName)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Untested",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else if (ReelsShieldDefaults.isPartial(packageName)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            color = accentColor.copy(alpha = 0.1f),
                            contentColor = accentColor,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Partial",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            leadingContent = {
                AppIconImage(
                    packageName = packageName,
                    modifier = Modifier.size(40.dp),
                    contentDescription = label
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

/**
 * Resolves app labels off the main thread once per package and caches them;
 * the settings screen never calls PackageManager from composition.
 */
@Composable
internal fun rememberAppLabels(packages: Set<String>): Map<String, String> {
    val context = LocalContext.current
    var labels by remember(packages) {
        mutableStateOf(
            packages.associateWith { pkg ->
                reelsAppLabelCache[pkg] ?: ReelsShieldDefaults.fallbackLabel(pkg)
            }
        )
    }

    LaunchedEffect(packages) {
        if (packages.isEmpty()) return@LaunchedEffect
        val missing = packages.filter { !reelsAppLabelCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                missing.associateWith { pkg ->
                    try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) {
                        ReelsShieldDefaults.fallbackLabel(pkg)
                    }
                }
            }
            reelsAppLabelCache.putAll(loaded)
        }
        labels = packages.associateWith { pkg ->
            reelsAppLabelCache[pkg] ?: ReelsShieldDefaults.fallbackLabel(pkg)
        }
    }

    return labels
}

@Composable
private fun rememberInstalledPackages(packages: Set<String>): Set<String> {
    val context = LocalContext.current
    var installed by remember(packages) { mutableStateOf(packages) }

    LaunchedEffect(packages) {
        if (packages.isEmpty()) {
            installed = emptySet()
            return@LaunchedEffect
        }
        installed = withContext(Dispatchers.IO) {
            packages.filterTo(LinkedHashSet()) { pkg ->
                try {
                    context.packageManager.getApplicationInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    return installed
}
