package com.etrisad.zenith.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.ReelsShieldDefaults
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithTooltipBox
import com.etrisad.zenith.ui.components.focus.rememberAccessibilityServiceEnabled
import com.etrisad.zenith.util.DateTimeUtils
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Home for the Reels Shield. The layout follows BedtimeScreen one-to-one: the
 * wavy ring sits directly on the page without a card, the summary card has its
 * own container underneath, and turning the main switch on or off animates the
 * toggle card and every list item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReelsShieldScreen(
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences()
    )
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val accessibilityEnabled = rememberAccessibilityServiceEnabled()
    var showAppsSheet by remember { mutableStateOf(false) }
    var limitTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val todayKey = DateTimeUtils.getDayStartDateString(
        System.currentTimeMillis(),
        preferences.dayStartHour,
        preferences.dayStartMinute
    )
    val todayCount = if (preferences.reelsShieldTodayDate == todayKey) {
        preferences.reelsShieldTodayCount
    } else {
        0
    }
    val todayByApp = remember(
        preferences.reelsShieldTodayByApp,
        preferences.reelsShieldTodayDate,
        todayKey
    ) {
        if (preferences.reelsShieldTodayDate == todayKey) {
            preferences.reelsShieldTodayByApp.filterValues { it > 0 }
        } else {
            emptyMap()
        }
    }

    val historyEntries = remember(
        preferences.reelsShieldHistory,
        preferences.reelsShieldTodayCount,
        preferences.reelsShieldTodayDate,
        preferences.dayStartHour,
        preferences.dayStartMinute
    ) { buildReelsHistory(preferences) }
    var selectedHistoryDate by remember { mutableStateOf<Long?>(null) }
    val selectedHistoryDetail = remember(
        selectedHistoryDate,
        historyEntries,
        preferences.reelsShieldTodayByApp,
        preferences.reelsShieldHistoryByApp,
        preferences.reelsShieldTodayDate
    ) {
        selectedHistoryDate?.let {
            buildReelsHistoryDayDetail(preferences, historyEntries, it)
        }
    }
    val historyLabels = rememberAppLabels(
        selectedHistoryDetail?.apps?.map { it.packageName }?.toSet().orEmpty()
    )

    if (showAppsSheet) {
        WhitelistBottomSheet(
            initialWhitelisted = preferences.reelsShieldPackages,
            onDismiss = { showAppsSheet = false },
            onSave = { selected ->
                if (selected.isNotEmpty() && selected != preferences.reelsShieldPackages) {
                    coroutineScope.launch { preferencesRepository.setReelsShieldPackages(selected) }
                }
                showAppsSheet = false
            },
            title = "Short Video Apps",
            subtitle = "Instagram Reels and Facebook Reels are the primary targets. TikTok is available with partial support. Only apps Zenith can detect short videos in are listed.",
            allowedPackages = ReelsShieldDefaults.SUPPORTED_PACKAGES,
            priorityPackages = ReelsShieldDefaults.PRIORITY_PACKAGES
        )
    }

    limitTarget?.let { (packageName, label) ->
        ReelsShieldAppLimitSheet(
            packageName = packageName,
            label = label,
            usedToday = todayByApp[packageName] ?: 0,
            savedLimit = preferences.reelsShieldAppLimits[packageName]
                ?: preferences.reelsShieldDailyLimit,
            poolLimit = preferences.reelsShieldDailyLimit,
            onDismiss = { limitTarget = null },
            onLimitChange = { limit ->
                coroutineScope.launch {
                    preferencesRepository.setReelsShieldAppLimit(packageName, limit)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item(key = "reels_toggle_card") {
            AnimatedVisibility(
                visible = !preferences.reelsShieldEnabled,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                        shrinkVertically(
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                modifier = Modifier.animateItem()
            ) {
                Column {
                    ReelsShieldToggleCard(
                        enabled = preferences.reelsShieldEnabled,
                        onToggle = { enabled ->
                            if (enabled) {
                                coroutineScope.launch {
                                    preferencesRepository.setReelsShieldEnabled(true)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (preferences.reelsShieldEnabled) {
            item(key = "reels_hero") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsShieldHero(
                        todayCount = todayCount,
                        limit = preferences.reelsShieldDailyLimit,
                        todayByAppCount = todayByApp.size,
                        totalCount = preferences.reelsShieldTotalCount
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(key = "reels_warnings") {
                Column(modifier = Modifier.animateItem()) {
                    AnimatedVisibility(
                        visible = !accessibilityEnabled || preferences.accessibilityLimitedMode,
                        enter = expandVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) + fadeOut()
                    ) {
                        Column {
                            if (!accessibilityEnabled) {
                                SettingsActionItem(
                                    title = "Accessibility Service Off",
                                    summary = "Turn on Zenith's accessibility service so short-video swipes can be detected",
                                    onClick = {
                                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    },
                                    icon = Icons.Outlined.AccessibilityNew,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (preferences.accessibilityLimitedMode) {
                                Card(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = "Zenith's limited accessibility mode hides scroll events, so swipes cannot be counted. Turn it off in Privacy for this feature to work.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            item(key = "reels_limits_header") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsSectionHeader(title = "Limits")
                }
            }

            item(key = "reels_pool") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsShieldPoolCard(
                        limit = preferences.reelsShieldDailyLimit,
                        onLimitChange = { limit ->
                            coroutineScope.launch { preferencesRepository.setReelsShieldDailyLimit(limit) }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(key = "reels_app_limits") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsShieldAppsCard(
                        trackedPackages = preferences.reelsShieldPackages,
                        appLimits = preferences.reelsShieldAppLimits,
                        poolLimit = preferences.reelsShieldDailyLimit,
                        todayByApp = todayByApp,
                        onAppClick = { packageName, label ->
                            limitTarget = packageName to label
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item(key = "reels_apps") {
                Column(modifier = Modifier.animateItem()) {
                    SettingsActionItem(
                        title = "Tracked Apps",
                        summary = "${preferences.reelsShieldPackages.size} supported apps · Instagram and Facebook first",
                        onClick = { showAppsSheet = true },
                        icon = Icons.Outlined.Apps,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item(key = "reels_insights_header") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsSectionHeader(title = "Insights")
                }
            }

            // Chart + header + rows are one group, exactly like Usage Stats'
            // hourly group: the chart is index 0, so pressing a bar tightens
            // its bottom corners and the detail tucks right underneath it.
            val detail = selectedHistoryDetail
            val historyGroupTotal = if (detail == null) {
                1
            } else {
                1 + 1 + if (detail.apps.isEmpty()) 1 else detail.apps.size
            }

            item(key = "reels_history") {
                Column(modifier = Modifier.animateItem()) {
                    ReelsShieldHistoryCard(
                        history = historyEntries,
                        limit = preferences.reelsShieldDailyLimit,
                        selectedDateMillis = selectedHistoryDate,
                        onDaySelected = { selectedHistoryDate = it },
                        shape = reelsGroupShape(0, historyGroupTotal),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                }
            }

            item(key = "reels_history_detail") {
                Column(modifier = Modifier.animateItem()) {
                    AnimatedContent(
                        targetState = detail,
                        transitionSpec = {
                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    expandVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    ))
                                .togetherWith(
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                            shrinkVertically(
                                                animationSpec = spring(stiffness = Spring.StiffnessLow)
                                            )
                                )
                        },
                        label = "ReelsHistoryDetailAnim"
                    ) { dayDetail ->
                        if (dayDetail != null) {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                ReelsShieldHistoryDetailHeader(
                                    detail = dayDetail,
                                    index = 1,
                                    total = historyGroupTotal
                                )

                                if (dayDetail.apps.isEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ReelsShieldHistoryEmptyDetail(
                                        detail = dayDetail,
                                        index = 2,
                                        total = historyGroupTotal
                                    )
                                } else {
                                    dayDetail.apps.forEachIndexed { rowIndex, app ->
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ReelsShieldHistoryAppRow(
                                            packageName = app.packageName,
                                            label = historyLabels[app.packageName]
                                                ?: ReelsShieldDefaults.fallbackLabel(app.packageName),
                                            count = app.count,
                                            index = 2 + rowIndex,
                                            total = historyGroupTotal
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

/**
 * Bedtime's "Usage" section header: left-aligned, titleMedium bold, with the
 * same 8 dp horizontal and vertical padding.
 */
@Composable
private fun ReelsSectionHeader(title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

/**
 * Single card shown while the shield is off. Turning it on reveals the rest of
 * the screen and the switch in the app header.
 */
@Composable
private fun ReelsShieldToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "reelsToggleCardColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reels Shield",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (enabled) {
                        "Counting short-video swipes"
                    } else {
                        "Currently disabled · turn on to count Reels and Shorts"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (enabled) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "reelsThumbSize"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "reelsSwitchIconColor"
                    )
                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = enabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                        scaleIn(
                                            initialScale = 0.5f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioHighBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )).togetherWith(
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleOut(
                                                targetScale = 0.5f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                )
                            },
                            label = "reelsSwitchIcon"
                        ) { isChecked ->
                            Icon(
                                imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                tint = iconColor
                            )
                        }
                    }
                }
            )
        }
    }
}

/**
 * Wavy ring copied from BedtimeStatusProgress: no card wrapper, 240 dp box with
 * 16 dp inset, pulsing icon and the remaining swipes in the middle. The summary
 * card below is a separate container.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReelsShieldHero(
    todayCount: Int,
    limit: Int,
    todayByAppCount: Int,
    totalCount: Long
) {
    val safeLimit = limit.coerceAtLeast(1)
    val remaining = (safeLimit - todayCount).coerceAtLeast(0)
    val poolEmpty = todayCount >= safeLimit
    // Decreasing mode: the ring is full when the pool is untouched and empties
    // as swipes are used, matching the "swipes left" number in the middle.
    val progress = (remaining.toFloat() / safeLimit).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ReelsHeroProgress"
    )

    val density = LocalDensity.current
    val strokeWidth = remember(density) { with(density) { 4.dp.toPx() } }
    val infiniteTransition = rememberInfiniteTransition(label = "reelsWavy")
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reelsWaveAmplitude"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reelsIconScale"
    )

    val ringColor = if (poolEmpty) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp)
        ) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = ringColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                stroke = Stroke(width = strokeWidth),
                trackStroke = Stroke(width = strokeWidth),
                amplitude = { waveAmplitude },
                wavelength = 48.dp
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$remaining",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (poolEmpty) "pool empty" else "swipes left",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        ReelsShieldStatsCard(
            todayCount = todayCount,
            limit = safeLimit,
            todayByAppCount = todayByAppCount,
            totalCount = totalCount
        )
    }
}

/** Separate primary-tinted container under the ring. */
@Composable
private fun ReelsShieldStatsCard(
    todayCount: Int,
    limit: Int,
    todayByAppCount: Int,
    totalCount: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReelsHeroStat(
                label = "Used today",
                value = "$todayCount / $limit",
                modifier = Modifier.weight(1f)
            )
            ReelsHeroDivider()
            ReelsHeroStat(
                label = "Apps today",
                value = "$todayByAppCount",
                modifier = Modifier.weight(1f)
            )
            ReelsHeroDivider()
            ReelsHeroStat(
                label = "Lifetime",
                value = formatCompactCount(totalCount),
                tooltipText = NumberFormat.getIntegerInstance().format(totalCount),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelsHeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tooltipText: String? = null
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (tooltipText != null) {
                        Modifier.clickable {
                            scope.launch {
                                if (tooltipState.isVisible) tooltipState.dismiss() else tooltipState.show()
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    if (tooltipText != null) {
        ZenithTooltipBox(
            tooltipText = tooltipText,
            state = tooltipState,
            modifier = modifier
        ) {
            content()
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** 1000 -> "1K", 1550 -> "1.55K", 1_000_000 -> "1M". */
private fun formatCompactCount(value: Long): String {
    if (value < 1_000) return value.toString()
    val units = arrayOf("K", "M", "B", "T")
    var scaled = value.toDouble()
    var unitIndex = -1
    while (scaled >= 1_000 && unitIndex < units.lastIndex) {
        scaled /= 1_000
        unitIndex++
    }
    val format = DecimalFormat("0.##", DecimalFormatSymbols(Locale.US))
    var text = format.format(scaled)
    if ((text.toDoubleOrNull() ?: 0.0) >= 1_000 && unitIndex < units.lastIndex) {
        unitIndex++
        text = "1"
    }
    return "$text${units[unitIndex]}"
}

@Composable
private fun ReelsHeroDivider() {
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}
