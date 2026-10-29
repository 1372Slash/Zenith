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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
            subtitle = "Instagram and YouTube are fully detected. Facebook Lite and TikTok are partial support; other apps are untested by default. Only apps Zenith can detect short videos in are listed.",
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
                                    summary = "Turn on Zenith's accessibility service so short-video reels can be detected",
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
                                        text = "Zenith's limited accessibility mode hides scroll events, so reels cannot be counted. Turn it off in Privacy for this feature to work.",
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
                        summary = "${preferences.reelsShieldPackages.size} supported apps · Reels, Shorts, Spotlight & TikTok",
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
                        shape = reelsGroupShape(0, historyGroupTotal)
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
                        "Counting short-video reels"
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
                    text = if (poolEmpty) "pool empty" else "reels left",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ReelsShieldStatsGrid(
            todayCount = todayCount,
            limit = safeLimit,
            todayByAppCount = todayByAppCount,
            totalCount = totalCount
        )
    }
}
@Composable
private fun ReelsShieldStatsGrid(
    todayCount: Int,
    limit: Int,
    todayByAppCount: Int,
    totalCount: Long
) {
    val remaining = (limit - todayCount).coerceAtLeast(0)
    val poolEmpty = todayCount >= limit

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReelsHeroStatCard(
                label = "Used today",
                value = "$todayCount / $limit",
                subtitle = "Reels watched",
                icon = Icons.Outlined.SwapVert,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f)
            )
            ReelsHeroStatCard(
                label = "Apps today",
                value = "$todayByAppCount",
                subtitle = if (todayByAppCount == 1) "Active app" else "Active apps",
                icon = Icons.Outlined.Apps,
                shape = RoundedCornerShape(topEnd = 24.dp, topStart = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ReelsHeroStatCard(
                label = "Reels left",
                value = "$remaining",
                subtitle = if (poolEmpty) "Pool exhausted" else "Remaining pool",
                icon = if (poolEmpty) Icons.Outlined.HourglassEmpty else Icons.Outlined.Shield,
                shape = RoundedCornerShape(bottomStart = 24.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f),
                iconTint = if (poolEmpty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            ReelsHeroStatCard(
                label = "Lifetime",
                value = formatCompactCount(totalCount),
                subtitle = "Total reels",
                icon = Icons.Outlined.History,
                shape = RoundedCornerShape(bottomEnd = 24.dp, topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp),
                modifier = Modifier.weight(1f),
                tooltipText = NumberFormat.getIntegerInstance().format(totalCount)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelsHeroStatCard(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    tooltipText: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)

    val cardContent: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        ) {
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
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(iconTint.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = iconTint
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconTint,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }

    if (tooltipText != null) {
        ZenithTooltipBox(
            tooltipText = tooltipText,
            state = tooltipState,
            modifier = modifier
        ) {
            cardContent()
        }
    } else {
        Box(modifier = modifier) {
            cardContent()
        }
    }
}
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
