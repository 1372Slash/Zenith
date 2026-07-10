package com.etrisad.zenith.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.toPath

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlarmOverlayContent(
    alarmTime: String = "07:00",
    alarmName: String = "Alarm",
    snoozeDurationMinutes: Int = 5,
    onDismiss: () -> Unit,
    onStopAlarm: () -> Unit,
    onSnooze: () -> Unit = {},
    snoozeCount: Int = 0,
    snoozeMaxCount: Int = 3,
    mathChallengeEnabled: Boolean = false,
    wakeUpAppPackageNames: List<String> = emptyList(),
    wakeUpAppNames: Map<String, String> = emptyMap(),
    wakeUpAppDurationSeconds: Int = 120,
    wakeUpAccumulatedSeconds: Int = 0,
    wakeUpComplete: Boolean = false,
    onWakeUpAppOpened: (String) -> Unit = {},
    onWakeUpDismiss: () -> Unit = {}
) {
    var showMathChallenge by remember { mutableStateOf(false) }
    var mathUserAnswer by remember { mutableStateOf("") }
    var mathCorrect by remember { mutableStateOf(false) }
    var mathA by remember { mutableStateOf(0) }
    var mathB by remember { mutableStateOf(0) }
    var mathError by remember { mutableStateOf(false) }
    var showWakeUpSheet by remember { mutableStateOf(false) }

    LaunchedEffect(showMathChallenge) {
        if (showMathChallenge) {
            mathA = (1..20).random()
            mathB = (1..20).random()
            mathUserAnswer = ""
            mathCorrect = false
            mathError = false
        }
    }

    fun handleDismiss(action: () -> Unit) {
        if (mathChallengeEnabled && !showMathChallenge) {
            showMathChallenge = true
        } else if (wakeUpAppPackageNames.isNotEmpty() && !wakeUpComplete) {
            showWakeUpSheet = true
        } else {
            action()
        }
    }

    fun handleMathSubmit() {
        val answer = mathUserAnswer.toIntOrNull()
        if (answer == mathA + mathB) {
            mathCorrect = true
            onDismiss()
        } else {
            mathError = true
        }
    }

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    val timeInfo = remember(currentTimeMillis, alarmTime) {
        val alarmCal = java.util.Calendar.getInstance().apply {
            val parts = alarmTime.split(":")
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val diffMs = currentTimeMillis - alarmCal.timeInMillis
        val diffMinutes = (diffMs / 60000).toInt()
        val diffHours = diffMinutes / 60
        val remainMinutes = kotlin.math.abs(diffMinutes) % 60
        val overdue = diffMinutes >= 0
        val diffText = if (kotlin.math.abs(diffMinutes) < 1) "Now" else {
            val sign = if (overdue) "Overdue by" else "In"
            val absMin = kotlin.math.abs(diffMinutes)
            if (absMin < 60) "$sign ${absMin}m"
            else "$sign ${diffHours}h ${remainMinutes}m"
        }
        val currentFormatted = String.format("%02d:%02d", nowCal.get(java.util.Calendar.HOUR_OF_DAY), nowCal.get(java.util.Calendar.MINUTE))
        Triple(currentFormatted, alarmTime, diffText)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "rotationAngle"
    )

    val hintTransition = rememberInfiniteTransition(label = "hint")
    val hintPhase by hintTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "hintPhase"
    )

    val backgroundAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgAlpha"
    )

    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val pulseColor = MaterialTheme.colorScheme.primary
    val pulseSecondaryColor = MaterialTheme.colorScheme.secondary

    val decorativeShape = remember {
        val availableShapes = listOf(
            MaterialShapes.Sunny,
            MaterialShapes.Slanted,
            MaterialShapes.Arch,
            MaterialShapes.Burst,
            MaterialShapes.Puffy,
            MaterialShapes.Bun,
            MaterialShapes.PixelCircle,
            MaterialShapes.Flower
        )
        val selected = availableShapes.random()
        GenericShape { size, _ ->
            val path = selected.toPath().asComposePath()
            val matrix = androidx.compose.ui.graphics.Matrix()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            addPath(path)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = backgroundAlpha),
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondary.copy(alpha = backgroundAlpha)
                        )
                    )
                )
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1.2f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (alarmName == "Alarm") "It's Time!" else alarmName,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Your scheduled alarm is active",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    AlarmInfoCard(
                        alarmTime = timeInfo.second,
                        currentTime = timeInfo.first,
                        diffText = timeInfo.third
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AlarmIconBox(
                        size = 240.dp,
                        innerSize = 190.dp,
                        iconSize = 130.dp,
                        rotationAngle = rotationAngle,
                        decorativeShape = decorativeShape,
                        pulseProgress = pulseProgress,
                        pulseColor = pulseColor,
                        pulseSecondaryColor = pulseSecondaryColor
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    SnoozeButton(
                        onSnooze = onSnooze,
                        snoozeDurationMinutes = snoozeDurationMinutes,
                        snoozeCount = snoozeCount,
                        snoozeMaxCount = snoozeMaxCount
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    AlarmActionGroup(
                        hintPhase = hintPhase,
                        onStopAlarm = onStopAlarm,
                        onDismiss = { handleDismiss(onDismiss) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (alarmName == "Alarm") "It's Time!" else alarmName,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Your scheduled alarm is active",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )

                AlarmInfoCard(
                    alarmTime = timeInfo.second,
                    currentTime = timeInfo.first,
                    diffText = timeInfo.third
                )

                Spacer(modifier = Modifier.weight(1f))

                AlarmIconBox(
                    size = 320.dp,
                    innerSize = 260.dp,
                    iconSize = 180.dp,
                    rotationAngle = rotationAngle,
                    decorativeShape = decorativeShape,
                    pulseProgress = pulseProgress,
                    pulseColor = pulseColor,
                    pulseSecondaryColor = pulseSecondaryColor
                )

                Spacer(modifier = Modifier.weight(1.3f))

                SnoozeButton(
                    onSnooze = onSnooze,
                    snoozeDurationMinutes = snoozeDurationMinutes,
                    snoozeCount = snoozeCount,
                    snoozeMaxCount = snoozeMaxCount
                )

                Spacer(modifier = Modifier.height(32.dp))

                AlarmActionGroup(
                    hintPhase = hintPhase,
                    onStopAlarm = onStopAlarm,
                    onDismiss = { handleDismiss(onDismiss) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        if (showMathChallenge) {
            MathChallengeOverlay(
                mathA = mathA,
                mathB = mathB,
                mathUserAnswer = mathUserAnswer,
                mathError = mathError,
                onAnswerChange = { mathUserAnswer = it },
                onSubmit = { handleMathSubmit() },
                onDismiss = { showMathChallenge = false }
            )
        }

        if (showWakeUpSheet) {
            WakeUpAppBottomSheet(
                wakeUpAppNames = wakeUpAppNames,
                accumulatedSeconds = wakeUpAccumulatedSeconds,
                requiredSeconds = wakeUpAppDurationSeconds,
                isComplete = wakeUpComplete,
                onOpenApp = { pkg ->
                    onWakeUpAppOpened(pkg)
                },
                onDone = {
                    showWakeUpSheet = false
                    onWakeUpDismiss()
                },
                onBackToAlarm = { showWakeUpSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WakeUpAppBottomSheet(
    wakeUpAppNames: Map<String, String>,
    accumulatedSeconds: Int,
    requiredSeconds: Int,
    isComplete: Boolean,
    onOpenApp: (String) -> Unit,
    onDone: () -> Unit,
    onBackToAlarm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onBackToAlarm() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (isComplete) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isComplete) "Wake-Up Verified!" else "Wake-Up Verification",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isComplete) "You proved you're awake!"
                       else "Open the apps below to prove you're awake",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isComplete) {
                LinearProgressIndicator(
                    progress = (accumulatedSeconds.toFloat() / requiredSeconds.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$accumulatedSeconds / $requiredSeconds seconds",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            wakeUpAppNames.forEach { (pkg, appName) ->
                Surface(
                    onClick = { if (!isComplete) onOpenApp(pkg) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = appName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!isComplete) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isComplete) {
                ZenithButton(
                    onClick = { scope.launch { sheetState.hide(); onDone() } },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Done — I'm Awake!",
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true
                )
            } else {
                ZenithButton(
                    onClick = { onBackToAlarm() },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Back to Alarm",
                    type = ZenithButtonType.Outlined,
                    size = ZenithButtonSize.ExtraLarge,
                    fillMaxWidth = true
                )
            }
        }
    }
}

@Composable
private fun AlarmIconBox(
    size: androidx.compose.ui.unit.Dp,
    innerSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    rotationAngle: Float,
    decorativeShape: androidx.compose.ui.graphics.Shape,
    pulseProgress: Float,
    pulseColor: Color,
    pulseSecondaryColor: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.drawBehind {
            val maxRadius = size.toPx() * 1.35f
            val alpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.25f

            drawCircle(
                color = pulseColor.copy(alpha = alpha),
                radius = maxRadius * pulseProgress,
                center = center
            )

            drawCircle(
                color = pulseSecondaryColor.copy(alpha = alpha * 0.5f),
                radius = maxRadius * 0.8f * pulseProgress,
                center = center
            )
        }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotationAngle }
                .clip(decorativeShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(innerSize)
                .graphicsLayer { rotationZ = -rotationAngle * 0.7f }
                .clip(decorativeShape)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
        )

        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                modifier = Modifier.size(iconSize * 0.6f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AlarmActionGroup(
    hintPhase: Float,
    onStopAlarm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val leftWeight by animateFloatAsState(
            targetValue = if (hintPhase < 1f) 1.25f + (0.15f * kotlin.math.sin(hintPhase * kotlin.math.PI.toFloat())) else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            label = "leftWeight"
        )

        Box(
            modifier = Modifier
                .weight(leftWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 42.dp, bottomStart = 42.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onStopAlarm),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AlarmOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Stop Alarm",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        val rightWeight by animateFloatAsState(
            targetValue = if (hintPhase >= 1f) 1.25f + (0.15f * kotlin.math.sin((hintPhase - 1f) * kotlin.math.PI.toFloat())) else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            label = "rightWeight"
        )

        Box(
            modifier = Modifier
                .weight(rightWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 42.dp, bottomEnd = 42.dp, topStart = 10.dp, bottomStart = 10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "I'm Awake!",
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun SnoozeButton(
    onSnooze: () -> Unit,
    snoozeDurationMinutes: Int,
    snoozeCount: Int,
    snoozeMaxCount: Int
) {
    val canSnooze = snoozeMaxCount == Int.MAX_VALUE || snoozeCount < snoozeMaxCount
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "snoozeScale"
    )
    val fillingColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        onClick = { if (canSnooze) onSnooze() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        interactionSource = interactionSource,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    drawRect(
                        color = fillingColor,
                        size = size.copy(width = size.width * if (canSnooze) snoozeCount.toFloat() / snoozeMaxCount.toFloat() else 0f)
                    )
                }
                .padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (canSnooze) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (canSnooze) "Snooze for $snoozeDurationMinutes minutes"
                           else "Snooze Used Up",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (canSnooze) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun AlarmInfoCard(
    alarmTime: String,
    currentTime: String,
    diffText: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Alarm Time Card (Left)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Alarm",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = alarmTime,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Current Time Card (Right)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp, topStart = 8.dp, bottomStart = 8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Now",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (diffText != "Now") {
            val isOverdue = diffText.startsWith("Overdue")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isOverdue) MaterialTheme.colorScheme.errorContainer 
                        else MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer 
                                else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer 
                                else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MathChallengeOverlay(
    mathA: Int,
    mathB: Int,
    mathUserAnswer: String,
    mathError: Boolean,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismissWithAnimation() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissWithAnimation() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Icon(
                imageVector = Icons.Outlined.Calculate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Math Challenge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Solve to dismiss the alarm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "$mathA + $mathB = ?",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = mathUserAnswer,
                onValueChange = { onAnswerChange(it.filter { c -> c.isDigit() }) },
                modifier = Modifier
                    .width(200.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                textStyle = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                singleLine = true,
                isError = mathError,
                supportingText = if (mathError) {
                    { Text("Wrong answer, try again") }
                } else null,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            ZenithButton(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                text = "Submit Answer",
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.ExtraLarge,
                fillMaxWidth = true,
                enabled = mathUserAnswer.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(12.dp))

            ZenithButton(
                onClick = { dismissWithAnimation() },
                modifier = Modifier.fillMaxWidth(),
                text = "Cancel",
                type = ZenithButtonType.Outlined,
                size = ZenithButtonSize.ExtraLarge,
                fillMaxWidth = true
            )
        }
    }
}