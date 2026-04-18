package com.etrisad.zenith.service

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InterceptOverlayContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    val isPeriodExpired = shield != null && 
        (System.currentTimeMillis() - shield.lastPeriodResetTimestamp > shield.refreshPeriodMinutes * 60 * 1000L)
    
    val currentUses = if (isPeriodExpired) 0 else (shield?.currentPeriodUses ?: 0)
    val maxUses = shield?.maxUsesPerPeriod ?: 5
    val isUsesExceeded = currentUses >= maxUses
    val isTimeLimitReached = shield != null && totalUsageToday >= (shield.timeLimitMinutes * 60 * 1000L)
    
    val refreshTimeLeftMillis = if (shield != null) {
        val nextRefresh = shield.lastPeriodResetTimestamp + (shield.refreshPeriodMinutes * 60 * 1000L)
        (nextRefresh - System.currentTimeMillis()).coerceAtLeast(0L)
    } else 0L

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Scrim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .clickable(enabled = false) { }
        )

        AnimatedVisibility(
            visible = showContent,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Uses & Emergency Indicators (Top of Bottom Sheet)
                    if (shield != null) {
                        // Left: Uses
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 28.dp, start = 20.dp),
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

                        // Right: Emergency
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 28.dp, end = 20.dp),
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
                                text = "Emergency: ${shield.emergencyUseCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drag handle simulation
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // App Icon
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Mindful Pause",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Zenith Shield is active for this app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Usage Progress
                        if (shield != null) {
                            Spacer(modifier = Modifier.height(24.dp))
                            val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
                            val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
                            val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatMillis(totalUsageToday),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${formatMillis(remainingMillis)} left today",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val animatedProgress by animateFloatAsState(
                                    targetValue = progress.coerceIn(0f, 1f),
                                    animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
                                    label = "progress"
                                )
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }

                        if ((isUsesExceeded || isTimeLimitReached) && !isEmergencyUnlocked) {
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            if (isUsesExceeded && !isTimeLimitReached) {
                                var countdownText by remember { mutableStateOf(formatCountdown(refreshTimeLeftMillis)) }
                                LaunchedEffect(refreshTimeLeftMillis) {
                                    var current = refreshTimeLeftMillis
                                    while (current > 0) {
                                        delay(1000)
                                        current -= 1000
                                        countdownText = formatCountdown(current)
                                    }
                                }

                                Text(
                                    text = "Uses limit reached. Refresh in $countdownText",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Daily limit reached. Come back tomorrow.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            if (shield != null && shield.emergencyUseCount > 0) {
                                Spacer(modifier = Modifier.height(24.dp))
                                EmergencyButton(onEmergencyUse = { isEmergencyUnlocked = true })
                            }
                        } else {
                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = if (isEmergencyUnlocked) "Emergency Use: Select Duration" else "How long do you want to use it?",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            DurationButtonsGrid { minutes -> 
                                scope.launch {
                                    showContent = false
                                    delay(400)
                                    onAllowUse(minutes, isEmergencyUnlocked)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        TextButton(
                            onClick = {
                                scope.launch {
                                    showContent = false
                                    delay(400)
                                    onCloseApp()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Close App", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmergencyButton(onEmergencyUse: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = if (isHolding) tween(5000, easing = LinearEasing) else tween(300),
        label = "holdProgress"
    )

    LaunchedEffect(isHolding) {
        if (isHolding) {
            holdProgress = 1f
            delay(5000)
            if (isHolding) {
                onEmergencyUse()
                isHolding = false
                holdProgress = 0f
            }
        } else {
            holdProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        try {
                            awaitRelease()
                        } finally {
                            isHolding = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Simple progress background fill
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isHolding) "Hold for ${5 - (animatedProgress * 5).toInt()}s..." else "Hold for 5s to use Emergency",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DurationButtonsGrid(onAllowUse: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DurationButton(minutes = 2, delaySeconds = 0, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
            DurationButton(minutes = 5, delaySeconds = 3, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DurationButton(minutes = 10, delaySeconds = 6, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
            DurationButton(minutes = 20, delaySeconds = 10, onAllowUse = onAllowUse, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun DurationButton(
    minutes: Int,
    delaySeconds: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    // Smooth progress animation
    val progress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = if (delaySeconds > 0) {
            tween(durationMillis = delaySeconds * 1000, easing = LinearEasing)
        } else {
            snap()
        },
        label = "buttonProgress"
    )

    val isEnabled = progress >= 1f

    // Spring bounce scale animation when becoming enabled
    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.95f,
        animationSpec = if (isEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            tween(300)
        },
        label = "buttonScale"
    )

    FilledTonalButton(
        onClick = { if (isEnabled) onAllowUse(minutes) },
        enabled = isEnabled,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isEnabled) 1f else 0.8f
            },
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = isEnabled,
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + 
                 scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "buttonContent"
        ) { enabled ->
            if (!enabled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    // Calculate countdown from progress for smooth transition
                    val secondsLeft = if (delaySeconds > 0) {
                        kotlin.math.ceil(delaySeconds * (1f - progress)).toInt().coerceAtLeast(1)
                    } else 0
                    
                    if (secondsLeft > 0) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
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
