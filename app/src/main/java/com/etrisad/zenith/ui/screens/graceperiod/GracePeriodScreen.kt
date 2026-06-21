package com.etrisad.zenith.ui.screens.graceperiod

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.screens.bedtime.DaysSelectionCard
import com.etrisad.zenith.ui.screens.bedtime.TimePickerDialog
import com.etrisad.zenith.ui.screens.bedtime.TimeSelectionRow
import com.etrisad.zenith.ui.viewmodel.GracePeriodViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GracePeriodScreen(
    viewModel: GracePeriodViewModel,
    innerPadding: PaddingValues
) {
    val preferences by viewModel.userPreferences.collectAsState()
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            val nextMinute = currentTime.plusMinutes(1).withSecond(0).withNano(0)
            val delayMillis = Duration.between(currentTime, nextMinute).toMillis()
            delay(delayMillis.coerceAtLeast(1000))
        }
    }

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        label = "containerColor"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = !preferences.gracePeriodEnabled,
                enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                        expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) +
                       shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
            ) {
                Column {
                    GracePeriodToggleCard(
                        enabled = preferences.gracePeriodEnabled,
                        onToggle = {
                            if (preferences.gracePeriodEnabled) {
                                showPauseSheet = true
                            } else {
                                viewModel.setGracePeriodEnabled(true)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (preferences.gracePeriodEnabled) {
                GracePeriodStatusProgress(
                    startTimeStr = preferences.gracePeriodStartTime,
                    endTimeStr = preferences.gracePeriodEndTime,
                    currentTime = currentTime
                )

                Spacer(modifier = Modifier.height(16.dp))

                TimeSelectionRow(
                    startTime = preferences.gracePeriodStartTime,
                    endTime = preferences.gracePeriodEndTime,
                    onStartTimeClick = { showStartTimePicker = true },
                    onEndTimeClick = { showEndTimePicker = true }
                )

                Spacer(modifier = Modifier.height(4.dp))

                DaysSelectionCard(
                    selectedDays = preferences.gracePeriodDays,
                    onDaysChange = { viewModel.setGracePeriodDays(it) },
                    containerColor = containerColor,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Grace Period overrides all restrictions. Shields, schedules, and bedtime will not block any apps while active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showPauseSheet) {
        ConfirmBottomSheet(
            onDismiss = { showPauseSheet = false },
            onConfirm = {
                viewModel.setGracePeriodEnabled(false)
                showPauseSheet = false
            },
            leverCount = 10,
            puzzleTimeoutSeconds = 10,
            showTimeSelection = false
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = preferences.gracePeriodStartTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = {
                viewModel.setGracePeriodStartTime(it)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = preferences.gracePeriodEndTime,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = {
                viewModel.setGracePeriodEndTime(it)
                showEndTimePicker = false
            }
        )
    }
}

@Composable
fun GracePeriodToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "toggleCardColor"
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
                    imageVector = Icons.Outlined.FreeBreakfast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Grace Period",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (enabled) "All apps are temporarily unblocked" else "Currently disabled",
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
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "thumb_size"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "switch_icon_color"
                    )
                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = enabled,
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                 scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                            },
                            label = "switch_icon_anim"
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GracePeriodStatusProgress(
    startTimeStr: String,
    endTimeStr: String,
    currentTime: LocalTime
) {
    val startTime = try { LocalTime.parse(startTimeStr) } catch (_: Exception) { LocalTime.of(12, 0) }
    val endTime = try { LocalTime.parse(endTimeStr) } catch (_: Exception) { LocalTime.of(13, 0) }

    val isGracePeriod = if (endTime.isAfter(startTime)) {
        currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
    } else {
        currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
    }

    val (label, remainingText, progressValue) = remember(currentTime, startTime, endTime, isGracePeriod) {
        if (isGracePeriod) {
            val totalDuration = if (endTime.isAfter(startTime)) {
                Duration.between(startTime, endTime)
            } else {
                Duration.ofHours(24).minus(Duration.between(endTime, startTime))
            }
            val elapsed = if (currentTime.isAfter(startTime)) {
                Duration.between(startTime, currentTime)
            } else {
                Duration.between(startTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, currentTime))
            }
            val remaining = totalDuration.minus(elapsed)
            val hours = remaining.toHours()
            val minutes = remaining.toMinutes() % 60
            Triple(
                "Grace period ends in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
            )
        } else {
            val timeToStart = if (currentTime.isBefore(startTime)) {
                Duration.between(currentTime, startTime)
            } else {
                Duration.between(currentTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, startTime))
            }
            val hours = timeToStart.toHours()
            val minutes = timeToStart.toMinutes() % 60
            Triple(
                "Starts in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                1f - (timeToStart.toMinutes().toFloat() / (24 * 60).toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressValue,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "GracePeriodProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "grace_wavy")
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.toPx() } }
    val accentColor = if (isGracePeriod) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        CircularWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            stroke = Stroke(width = strokeWidthPx),
            trackStroke = Stroke(width = strokeWidthPx),
            amplitude = { waveAmplitude },
            wavelength = 48.dp
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isGracePeriod) Icons.Filled.FreeBreakfast else Icons.Outlined.Schedule,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = remainingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
