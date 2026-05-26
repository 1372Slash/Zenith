package com.etrisad.zenith.ui.components.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeAlertPill(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val bedtimeInfo = remember(userPreferences) {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        
        val startParts = userPreferences.bedtimeStartTime.split(":")
        val endParts = userPreferences.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        val totalDuration = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            (1440 - startMinutes) + endMinutes
        }

        val elapsed = if (startMinutes <= endMinutes) {
            (currentMinutes - startMinutes).coerceAtLeast(0)
        } else {
            if (currentMinutes >= startMinutes) {
                currentMinutes - startMinutes
            } else {
                (1440 - startMinutes) + currentMinutes
            }
        }

        val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        val remaining = (totalDuration - elapsed).coerceAtLeast(0)
        val h = remaining / 60
        val m = remaining % 60
        
        val formattedTime = if (h > 0) "${h}h ${m}m" else "${m}m"
        
        // Determine if it's currently bedtime
        val isBedtime = if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }

        Triple(progress, formattedTime, isBedtime)
    }

    if (!bedtimeInfo.third) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "It's Bedtime!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Finish up soon and continue tomorrow.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = bedtimeInfo.second,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            CircularWavyProgressIndicator(
                progress = { bedtimeInfo.first },
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stroke = Stroke(width = 6.dp.value),
                trackStroke = Stroke(width = 6.dp.value),
                wavelength = 12.dp
            )
        }
    }
}

@Composable
fun OverlayDragHandleWithIndicators(
    modifier: Modifier = Modifier,
    currentUses: Int? = null,
    maxUses: Int? = null,
    emergencyCount: Int? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (currentUses != null && maxUses != null) {
                Row(
                    modifier = Modifier.padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$currentUses/$maxUses uses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (emergencyCount != null) {
                Row(
                    modifier = Modifier.padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Bolt,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Emergency: $emergencyCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TotalUsagePill(totalGlobalUsageToday: Long, userPrefs: UserPreferences, modifier: Modifier = Modifier) {
    if (!userPrefs.totalUsagePillEnabled) return

    val screenTimeTargetMinutes = userPrefs.screenTimeTargetMinutes
    val totalUsageMinutes = totalGlobalUsageToday / 60000
    val isTargetExceeded = totalUsageMinutes >= screenTimeTargetMinutes && screenTimeTargetMinutes > 0

    val totalPillColor = if (isTargetExceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val totalPillContentColor = if (isTargetExceeded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary

    val baseColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.tertiary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val cornerRadiusLarge = 24.dp
    val cornerRadiusSmall = 4.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            color = totalPillColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusLarge,
                bottomStart = cornerRadiusLarge,
                topEnd = cornerRadiusSmall,
                bottomEnd = cornerRadiusSmall
            )
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelSmall,
                color = totalPillContentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        val remainingMinutes = (screenTimeTargetMinutes - totalUsageMinutes).coerceAtLeast(0)
        val fillRatio = if (screenTimeTargetMinutes > 0) {
            remainingMinutes.toFloat() / screenTimeTargetMinutes
        } else 0f

        Surface(
            color = baseColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusSmall,
                bottomStart = cornerRadiusSmall,
                topEnd = cornerRadiusLarge,
                bottomEnd = cornerRadiusLarge
            )
        ) {
            val separatorColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier.drawBehind {
                    val progressWidth = size.width * fillRatio.coerceIn(0f, 1f)
                    drawRect(
                        color = fillColor,
                        size = Size(width = progressWidth, height = size.height)
                    )

                    if (fillRatio > 0f && fillRatio < 1f) {
                        drawRect(
                            color = separatorColor,
                            topLeft = Offset(progressWidth - 1.dp.toPx(), 0f),
                            size = Size(width = 2.dp.toPx(), height = size.height)
                        )
                    }
                }
            ) {
                Text(
                    text = formatMillis(totalGlobalUsageToday),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EmergencyButton(onEmergencyUse: () -> Unit, onHoldingChange: (Boolean) -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        onHoldingChange(isPressed)
        if (isPressed) {
            if (holdProgress.animateTo(1f, tween(5000, easing = LinearEasing)).endReason == AnimationEndReason.Finished) {
                onEmergencyUse()
                holdProgress.snapTo(0f)
            }
        } else {
            holdProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    ZenithButton(
        onClick = {},
        type = ZenithButtonType.Filled,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        backgroundProgressProvider = { holdProgress.value },
        interactionSource = interactionSource,
        fillMaxWidth = true,
        size = ZenithButtonSize.ExtraLarge,
        content = {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            val secondsRemaining = kotlin.math.ceil(5 * (1f - holdProgress.value)).toInt()
            Text(
                text = if (isPressed) "Hold for ${secondsRemaining}s..." else "Hold for 5s to use Emergency",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@Composable
fun CloseAppTextButton(onCloseApp: () -> Unit, autoKickProgress: () -> Float = { 0f }) {
    ZenithButton(
        onClick = onCloseApp,
        text = "Close App",
        type = ZenithButtonType.Text,
        contentColor = MaterialTheme.colorScheme.error,
        backgroundProgressProvider = autoKickProgress,
        fillMaxWidth = true,
        size = ZenithButtonSize.ExtraLarge
    )
}

@Composable
fun DurationButtonsGrid(remainingMinutes: Int?, onAllowUse: (Int) -> Unit) {
    val b20 = if (remainingMinutes != null && remainingMinutes < 20) remainingMinutes else 20
    val b10 = if (remainingMinutes != null && remainingMinutes < 10) (remainingMinutes * 0.5).toInt().coerceAtLeast(1) else 10
    val b5 = if (remainingMinutes != null && remainingMinutes < 5) {
        (remainingMinutes * 0.75).toInt().coerceAtLeast(1)
    } else 5
    val b2 = if (remainingMinutes != null && remainingMinutes < 2) remainingMinutes else 2

    val outerRadius = 24.dp
    val innerRadius = 4.dp

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DurationButton(
                minutes = b2,
                delaySeconds = 0,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = outerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
            DurationButton(
                minutes = b5,
                delaySeconds = 3,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = outerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DurationButton(
                minutes = b10,
                delaySeconds = 6,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = outerRadius, bottomEnd = innerRadius)
            )
            DurationButton(
                minutes = b20,
                delaySeconds = 10,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = outerRadius)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationButton(
    minutes: Int,
    delaySeconds: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    val progressState = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = if (delaySeconds > 0) {
            tween(durationMillis = delaySeconds * 1000, easing = LinearEasing)
        } else {
            snap()
        },
        label = "buttonProgress"
    )

    val isEnabled = progressState.value >= 1f
    val buttonScale = remember { Animatable(1f) }

    LaunchedEffect(isEnabled) {
        if (isEnabled) {
            buttonScale.animateTo(
                targetValue = 1f,
                initialVelocity = 1.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    FilledTonalButton(
        onClick = { if (isEnabled) onAllowUse(minutes) },
        enabled = isEnabled,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
                alpha = if (isEnabled) 1f else 0.8f
            },
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = isEnabled,
            transitionSpec = {
                fadeIn(animationSpec = tween(400))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "buttonContent"
        ) { enabled ->
            if (!enabled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progressState.value },
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        stroke = Stroke(width = 6.dp.value),
                        trackStroke = Stroke(width = 6.dp.value),
                        wavelength = 12.dp
                    )
                    val secondsLeft by remember(delaySeconds) {
                        derivedStateOf {
                            if (delaySeconds > 0) {
                                kotlin.math.ceil(delaySeconds * (1f - progressState.value)).toInt().coerceAtLeast(1)
                            } else 0
                        }
                    }

                    if (secondsLeft > 0) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            } else {
                Text(
                    text = "$minutes mins",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
