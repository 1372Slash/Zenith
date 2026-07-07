package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Tab
import androidx.compose.material3.toShape
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.*
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.components.ShieldSortHeader
import com.etrisad.zenith.ui.components.UninstalledAppCard
import com.etrisad.zenith.ui.viewmodel.ShieldSortType
import kotlinx.coroutines.delay

enum class AppTypeTab { APPS, WEBSITES }

fun ShieldEntity.isWebsiteType(): Boolean =
    isWebsite || WebsiteRepository.isWebsitePackageName(packageName)

fun ScheduleEntity.containsApps(): Boolean =
    packageNames.any { !it.startsWith(WebsiteRepository.WEBSITE_PREFIX) }

fun ScheduleEntity.containsWebsites(): Boolean =
    packageNames.any { it.startsWith(WebsiteRepository.WEBSITE_PREFIX) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTypeTabRow(
    selectedTab: AppTypeTab,
    onTabChange: (AppTypeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = if (selectedTab == AppTypeTab.APPS) 0 else 1,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(RoundedCornerShape(24.dp))
    ) {
        Tab(
            selected = selectedTab == AppTypeTab.APPS,
            onClick = { onTabChange(AppTypeTab.APPS) },
            text = { Text("Apps") }
        )
        Tab(
            selected = selectedTab == AppTypeTab.WEBSITES,
            onClick = { onTabChange(AppTypeTab.WEBSITES) },
            text = { Text("Websites") }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActiveShieldCard(
    shield: ShieldEntity,
    shape: RoundedCornerShape,
    isHomeScreen: Boolean,
    onClick: (String) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onLongClick: ((String) -> Unit)? = null,
    nowMillis: Long = System.currentTimeMillis(),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    isSingle: Boolean = false
) {
    val topStartRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else if (isSingle || isFirst) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "topStartRadius"
    )
    val topEndRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else if (isSingle || isFirst) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "topEndRadius"
    )
    val bottomStartRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else if (isSingle || isLast) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomStartRadius"
    )
    val bottomEndRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else if (isSingle || isLast) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bottomEndRadius"
    )
    val animatedShape = RoundedCornerShape(
        topStart = topStartRadius,
        topEnd = topEndRadius,
        bottomStart = bottomStartRadius,
        bottomEnd = bottomEndRadius
    )
    val haptic = LocalHapticFeedback.current

    val isEffectivelyPaused = remember(shield.isPaused, shield.pauseEndTimestamp, nowMillis) {
        shield.isPaused && (shield.pauseEndTimestamp == 0L || nowMillis < shield.pauseEndTimestamp)
    }

    val nextResetTimestamp = remember(shield.lastPeriodResetTimestamp, shield.refreshPeriodMinutes) {
        shield.lastPeriodResetTimestamp + (shield.refreshPeriodMinutes * 60 * 1000L)
    }
    val remainingResetMillis = (nextResetTimestamp - nowMillis).coerceAtLeast(0L)
    val usesExhausted = remember(shield.currentPeriodUses, shield.maxUsesPerPeriod) {
        shield.currentPeriodUses >= shield.maxUsesPerPeriod && shield.maxUsesPerPeriod > 0
    }

    val isLocked = isEffectivelyPaused || (usesExhausted && remainingResetMillis > 0)

    val saturation by animateFloatAsState(
        targetValue = if (isLocked) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "IconSaturation"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isLocked) 0.6f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "IconAlpha"
    )

    val colorFilter = remember(saturation) {
        val matrix = ColorMatrix().apply { setToSaturation(saturation) }
        ColorFilter.colorMatrix(matrix)
    }

    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = shield.remainingTimeMillis.coerceIn(0L, totalLimitMillis)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    val cardColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val secondaryColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val isWebsite = shield.isWebsiteType()

    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(animatedShape)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelection?.invoke()
                    else if (isHomeScreen) onClick(shield.packageName)
                    else if (!isHomeScreen) onClick(shield.packageName)
                },
                onLongClick = if (isHomeScreen) null else {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isSelectionMode) onToggleSelection?.invoke()
                        else onLongClick?.invoke(shield.packageName)
                    }
                }
            ),
        shape = animatedShape,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconShape = appIconShape(isWebsite)
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("app-icon://${shield.packageName}")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).then(
                            if (isWebsite) Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                iconShape
                            ) else Modifier
                        ).clip(iconShape),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        alpha = iconAlpha,
                        error = {
                            Box(
                                modifier = Modifier.fillMaxSize().clip(iconShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isWebsite) Icons.Outlined.Language else Icons.Outlined.Android,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                                )
                            }
                        }
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = shield.currentStreak > 0,
                        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                                scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
                        exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                                scaleOut(spring(stiffness = Spring.StiffnessLow)),
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary,
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onTertiary
                                )
                                Text(
                                    text = "${shield.currentStreak}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }

                    if (isLocked) {
                        val (badgeProgress, badgeIcon, badgeColor) = when {
                            isEffectivelyPaused -> {
                                val remainingPauseMillis = if (shield.pauseEndTimestamp == 0L) -1L
                                else (shield.pauseEndTimestamp - nowMillis).coerceAtLeast(0L)
                                val initialPauseDuration = remember(shield.pauseEndTimestamp) {
                                    val diff = shield.pauseEndTimestamp - System.currentTimeMillis()
                                    when {
                                        diff <= 3600000L -> 3600000L
                                        diff <= 21600000L -> 21600000L
                                        else -> 86400000L
                                    }
                                }
                                val progress = if (shield.pauseEndTimestamp == 0L) 1f
                                else (remainingPauseMillis.toFloat() / initialPauseDuration).coerceIn(0f, 1f)
                                Triple(progress, Icons.Outlined.Pause, MaterialTheme.colorScheme.secondary)
                            }
                            else -> {
                                val resetPeriodMillis = shield.refreshPeriodMinutes * 60 * 1000L
                                val progress = if (resetPeriodMillis > 0) {
                                    (remainingResetMillis.toFloat() / resetPeriodMillis).coerceIn(0f, 1f)
                                } else 1f
                                Triple(progress, Icons.Outlined.History, MaterialTheme.colorScheme.error)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                                .offset(x = 2.dp, y = (-2).dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { badgeProgress },
                                    modifier = Modifier.size(14.dp),
                                    color = badgeColor,
                                    strokeWidth = 1.5.dp,
                                    trackColor = badgeColor.copy(alpha = 0.2f),
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                Icon(
                                    imageVector = badgeIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = badgeColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shield.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    if (!isHomeScreen && shield.isWebsite && shield.url != null) {
                        Text(
                            text = shield.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor,
                            maxLines = 1
                        )
                    }

                    if (!isHomeScreen) {
                        val hours = shield.timeLimitMinutes / 60
                        val mins = shield.timeLimitMinutes % 60
                        val typeText = if (shield.type == FocusType.GOAL) "target" else "limit"
                        val periodText = when (shield.limitPeriod) {
                            LimitPeriod.DAILY -> "/day"
                            LimitPeriod.WEEKLY -> "/week"
                        }
                        val limitText = if (hours > 0) "${hours}h ${mins}m $typeText" else "${mins}m $typeText"
                        val statusText = if (shield.type == FocusType.GOAL) "Productive" else {
                            if (shield.isStrictModeEnabled) "Strict" else "Normal"
                        }
                        Text(
                            text = "$limitText$periodText • $statusText",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor
                        )
                    }

                    val timeLabel = if (shield.type == FocusType.GOAL) "To Go" else "Left"
                    AnimatedContent(
                        targetState = Triple(usesExhausted, remainingMillis, remainingResetMillis),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)).togetherWith(fadeOut(animationSpec = tween(200)))
                        },
                        label = "ShieldRemainingTimeAnimation"
                    ) { (isExhausted, remaining, resetMillis) ->
                        val displayText = if (isExhausted && resetMillis > 0) {
                            "Reset in ${formatRemainingTime(resetMillis)}"
                        } else {
                            "${formatRemainingTime(remaining)} $timeLabel"
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExhausted && resetMillis > 0) {
                                MaterialTheme.colorScheme.error
                            } else if (isSelected) {
                                contentColor
                            } else if (shield.type == FocusType.GOAL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                val percentage = if (shield.type == FocusType.GOAL) {
                    ((1f - progress) * 100).toInt()
                } else {
                    (progress * 100).toInt()
                }

                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = secondaryColor
                )

                if (!isHomeScreen) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSelectionMode) {
                            if (onEdit != null) {
                                IconButton(onClick = onEdit) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = if (isSelected) contentColor else MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelection?.invoke() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
                label = "Progress"
            )

            val indicatorColor = if (isSelected) {
                contentColor
            } else if (shield.type == FocusType.GOAL) {
                MaterialTheme.colorScheme.primary
            } else {
                if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            }

            LinearWavyProgressIndicator(
                progress = { if (shield.type == FocusType.GOAL) 1f - animatedProgress else animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = indicatorColor,
                trackColor = if (isSelected) contentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActiveScheduleCard(
    schedule: ScheduleEntity,
    shape: RoundedCornerShape,
    isHomeScreen: Boolean,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onLongClick: ((Long) -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60000)
            value = System.currentTimeMillis()
        }
    }

    val (isActiveNow, progress) = remember(schedule.startTime, schedule.endTime, schedule.isActive, nowMillis) {
        if (!schedule.isActive) {
            false to 0f
        } else {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

            val startMinutes = try {
                val parts = schedule.startTime.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 0 }

            val endMinutes = try {
                val parts = schedule.endTime.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (_: Exception) { 0 }

            val startSeconds = startMinutes * 60
            val endSeconds = endMinutes * 60

            if (startMinutes <= endMinutes) {
                val isActive = currentMinutes in startMinutes until endMinutes
                val total = (endSeconds - startSeconds).coerceAtLeast(1)
                val elapsed = ((currentMinutes * 60) - startSeconds).coerceIn(0, total)
                isActive to (elapsed.toFloat() / total)
            } else {
                val isActive = currentMinutes >= startMinutes || currentMinutes < endMinutes
                val total = (24 * 3600 - startSeconds + endSeconds).coerceAtLeast(1)
                val elapsed = if (currentMinutes * 60 >= startSeconds) {
                    (currentMinutes * 60) - startSeconds
                } else {
                    (24 * 3600 - startSeconds) + (currentMinutes * 60)
                }
                val clampedElapsed = elapsed.coerceIn(0, total)
                isActive to (clampedElapsed.toFloat() / total)
            }
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    val secondaryColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(
            onClick = { if (isSelectionMode) onToggleSelection?.invoke() else onEdit() },
            onLongClick = if (isHomeScreen) null else {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isSelectionMode) onToggleSelection?.invoke()
                    else onLongClick?.invoke(schedule.id)
                }
            }
        ),
        shape = shape,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column {
            ListItem(
                headlineContent = { Text(schedule.name, fontWeight = FontWeight.Bold, color = contentColor) },
                supportingContent = {
                    val modeText = schedule.mode.name.lowercase().replaceFirstChar { it.uppercase() }
                    val statusText = if (isActiveNow) "Active" else "Inactive"
                    Column {
                        Text(
                            text = "${schedule.startTime} - ${schedule.endTime} • $modeText • $statusText • ${schedule.packageNames.size} apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isActiveNow) {
                                if (isSelected) contentColor else MaterialTheme.colorScheme.primary
                            } else secondaryColor
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = secondaryColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${schedule.emergencyUseCount}/${schedule.maxEmergencyUses} emergency uses left",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryColor
                            )
                        }
                    }
                },
                leadingContent = {
                    Box(contentAlignment = Alignment.Center) {
                        MultiAppIconGroup(
                            packageNames = schedule.packageNames.take(4),
                            totalCount = schedule.packageNames.size,
                            size = 40.dp
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp),
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        modifier = Modifier.padding(4.dp),
                                        tint = MaterialTheme.colorScheme.primaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                trailingContent = {
                    if (!isHomeScreen && !isSelectionMode) {
                        Row {
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = if (isSelected) contentColor else MaterialTheme.colorScheme.primary)
                            }
                            if (onDelete != null) {
                                IconButton(onClick = onDelete) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = if (isSelected) contentColor else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            if (isActiveNow) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp).height(8.dp),
                    color = if (isSelected) contentColor else MaterialTheme.colorScheme.primary,
                    trackColor = (if (isSelected) contentColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableItemContainer(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (!enabled) return@rememberSwipeToDismissBoxState false
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> { onDelete(); false }
                SwipeToDismissBoxValue.EndToStart -> { onEdit(); false }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.clip(shape),
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }, label = "SwipeBackground"
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.Delete
                SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Edit
                else -> null
            }
            val tint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.error
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }
            val isSwiping = direction != SwipeToDismissBoxValue.Settled
            val scale by animateFloatAsState(if (isSwiping) 1.2f else 1f, label = "IconScale")
            Box(
                modifier = Modifier.fillMaxSize().clip(shape).background(color).padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.activeShieldSection(
    key: String,
    title: String,
    shields: List<ShieldEntity>,
    sortType: ShieldSortType,
    onSortTypeChange: (ShieldSortType) -> Unit,
    tabValue: AppTypeTab,
    isHomeScreen: Boolean,
    onClick: (String) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    uninstalledPackages: Set<String> = emptySet(),
    onDeleteShield: (ShieldEntity) -> Unit = {},
    onDismissUninstalled: (String) -> Unit = {},
    isSelectionMode: Boolean = false,
    selectedShields: Set<String> = emptySet(),
    onEditShield: (ShieldEntity) -> Unit = {},
    onLongClick: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    showHeader: Boolean = true
) {
    val filteredShields = shields.filter { shield ->
        if (tabValue == AppTypeTab.APPS) !shield.isWebsiteType() else shield.isWebsiteType()
    }

    if (showHeader) {
        item(key = "${key}_header") {
            ShieldSortHeader(title = title, currentSortType = sortType, onSortTypeChange = onSortTypeChange)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (filteredShields.isEmpty()) {
        item(key = "${key}_empty") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                EmptyFocusMessage(message = "No active $title. Go to Focus to add one!")
            }
        }
    } else {
        itemsIndexed(
            items = filteredShields,
            key = { _, shield -> "${key}_${shield.packageName}" }
        ) { index, shield ->
            val isSingle = filteredShields.size == 1
            val isFirst = index == 0
            val isLast = index == filteredShields.size - 1
            val shape = when {
                isSingle -> RoundedCornerShape(24.dp)
                isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                isLast -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(8.dp)
            }

            Column(
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                    fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                    placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                )
            ) {
                if (isHomeScreen) {
                    ActiveShieldCard(
                        shield = shield,
                        shape = shape,
                        isHomeScreen = true,
                        onClick = onClick,
                        isFirst = isFirst,
                        isLast = isLast,
                        isSingle = isSingle
                    )
                } else {
                    SwipeableItemContainer(
                        onEdit = { onEditShield(shield) },
                        onDelete = { onDeleteShield(shield) },
                        shape = shape,
                        enabled = !isSelectionMode
                    ) {
                        ActiveShieldCard(
                            shield = shield,
                            shape = shape,
                            isHomeScreen = false,
                            onClick = onClick,
                            onEdit = { onEditShield(shield) },
                            onLongClick = onLongClick,
                            nowMillis = nowMillis,
                            isSelectionMode = isSelectionMode,
                            isSelected = shield.packageName in selectedShields,
                            onToggleSelection = { onToggleSelection(shield.packageName) },
                            isFirst = isFirst,
                            isLast = isLast,
                            isSingle = isSingle
                        )
                    }
                }

                if (shield.packageName in uninstalledPackages) {
                    Spacer(modifier = Modifier.height(4.dp))
                    UninstalledAppCard(
                        appName = shield.appName,
                        onDelete = { onDeleteShield(shield) },
                        onDismissToday = { onDismissUninstalled(shield.packageName) }
                    )
                }

                if (index < filteredShields.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.activeScheduleSection(
    key: String,
    schedules: List<ScheduleEntity>,
    tabValue: AppTypeTab,
    isHomeScreen: Boolean,
    onEditSchedule: (ScheduleEntity) -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    isSelectionMode: Boolean = false,
    selectedSchedules: Set<Long> = emptySet(),
    onDeleteSchedule: (ScheduleEntity) -> Unit = {},
    onLongClick: (Long) -> Unit = {},
    onToggleSelection: (Long) -> Unit = {}
) {
    val filteredSchedules = schedules.filter { schedule ->
        if (tabValue == AppTypeTab.APPS) schedule.containsApps() else schedule.containsWebsites()
    }

    item(key = "${key}_header") {
        Text(
            text = "Active Schedules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }

    if (filteredSchedules.isEmpty()) {
        item(key = "${key}_empty") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                EmptyFocusMessage(message = "No active schedules yet")
            }
        }
    } else {
        itemsIndexed(
            items = filteredSchedules,
            key = { _, schedule -> "${key}_${schedule.id}" }
        ) { index, schedule ->
            val shape = when {
                filteredSchedules.size == 1 -> RoundedCornerShape(24.dp)
                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                index == filteredSchedules.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(8.dp)
            }

            Column(
                modifier = Modifier.animateItem(
                    fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                    fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                    placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                )
            ) {
                val onEdit = if (isHomeScreen) {
                    { onEditSchedule(schedule) }
                } else {
                    { if (isSelectionMode) onToggleSelection(schedule.id) else onEditSchedule(schedule) }
                }

                if (isHomeScreen) {
                    ActiveScheduleCard(
                        schedule = schedule,
                        shape = shape,
                        isHomeScreen = true,
                        onEdit = onEdit
                    )
                } else {
                    SwipeableItemContainer(
                        onEdit = onEdit,
                        onDelete = { onDeleteSchedule(schedule) },
                        shape = shape,
                        enabled = !isSelectionMode
                    ) {
                        ActiveScheduleCard(
                            schedule = schedule,
                            shape = shape,
                            isHomeScreen = false,
                            onEdit = onEdit,
                            onDelete = { onDeleteSchedule(schedule) },
                            onLongClick = onLongClick,
                            isSelectionMode = isSelectionMode,
                            isSelected = schedule.id in selectedSchedules,
                            onToggleSelection = { onToggleSelection(schedule.id) }
                        )
                    }
                }

                if (index < filteredSchedules.size - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

fun LazyListScope.activeTypeTabRow(
    tabValue: AppTypeTab,
    onTabChange: (AppTypeTab) -> Unit
) {
    item(key = "global_tab") {
        AppTypeTabRow(selectedTab = tabValue, onTabChange = onTabChange)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ShieldSectionContent(
    title: String,
    shields: List<ShieldEntity>,
    sortType: ShieldSortType,
    onSortTypeChange: (ShieldSortType) -> Unit,
    tabValue: AppTypeTab,
    isHomeScreen: Boolean,
    onClick: (String) -> Unit,
    uninstalledPackages: Set<String> = emptySet(),
    onDeleteShield: (ShieldEntity) -> Unit = {},
    onDismissUninstalled: (String) -> Unit = {},
    showHeader: Boolean = true
) {
    val filteredShields = shields.filter { shield ->
        if (tabValue == AppTypeTab.APPS) !shield.isWebsiteType() else shield.isWebsiteType()
    }

    Column {
        if (showHeader) {
            ShieldSortHeader(title = title, currentSortType = sortType, onSortTypeChange = onSortTypeChange)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (filteredShields.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                EmptyFocusMessage(message = "No active $title. Go to Focus to add one!")
            }
        } else {
            filteredShields.forEachIndexed { index, shield ->
                val shape = when {
                    filteredShields.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    index == filteredShields.size - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(8.dp)
                }

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                            scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                            scaleOut(targetScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                ) {
                    Column {
                        if (isHomeScreen) {
                            ActiveShieldCard(
                                shield = shield,
                                shape = shape,
                                isHomeScreen = true,
                                onClick = onClick
                            )
                        }

                        if (shield.packageName in uninstalledPackages) {
                            Spacer(modifier = Modifier.height(4.dp))
                            UninstalledAppCard(
                                appName = shield.appName,
                                onDelete = { onDeleteShield(shield) },
                                onDismissToday = { onDismissUninstalled(shield.packageName) }
                            )
                        }

                        if (index < filteredShields.size - 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun appIconShape(isWebsite: Boolean): Shape =
    if (isWebsite) MaterialShapes.Square.toShape() else CircleShape
