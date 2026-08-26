package com.etrisad.zenith.ui.components.pausepoint

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.ui.components.Lever
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.qr.QrScanner
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay

@Composable
fun PausePointTaskContent(
    task: PausePointTask,
    onTaskCompleted: () -> Unit,
    onOpenApp: ((String) -> Unit)? = null,
    onUserActivity: () -> Unit = {},
    onKeyboardFocusChange: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = task.instruction,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        when (task) {
            is PausePointTask.Waiting -> WaitingTask(task, onTaskCompleted)
            is PausePointTask.Breathing -> BreathingTask(task, onTaskCompleted)
            is PausePointTask.Walk -> WalkTask(task, onTaskCompleted, onUserActivity)
            is PausePointTask.QrScan -> QrScanTask(task, onTaskCompleted, onUserActivity)
            is PausePointTask.NumberSlide -> NumberSlideTask(task, onTaskCompleted)
            is PausePointTask.Switch -> SwitchTask(task, onTaskCompleted)
            is PausePointTask.Counting -> CountingTask(task, onTaskCompleted, onUserActivity)
            is PausePointTask.Typing -> TypingTask(task, onTaskCompleted, onUserActivity, onKeyboardFocusChange)
            is PausePointTask.Math -> MathTask(task, onTaskCompleted, onUserActivity, onKeyboardFocusChange)
            is PausePointTask.ChooseApp -> ChooseAppTask(task, onTaskCompleted, onOpenApp)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WaitingTask(
    task: PausePointTask.Waiting,
    onCompleted: () -> Unit
) {
    val totalMillis = task.durationSeconds * 1000L
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val p = (elapsed.toFloat() / totalMillis).coerceIn(0f, 1f)
            progress.snapTo(p)
            if (p >= 1f) {
                onCompleted()
                break
            }
            delay(16)
        }
    }

    val secondsLeft = remember {
        derivedStateOf { ((1f - progress.value) * task.durationSeconds).toInt() }
    }

    Box(contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(120.dp),
            color = MaterialTheme.colorScheme.primary,
            amplitude = { 1f },
            wavelength = 32.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "${secondsLeft.value}s",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BreathingTask(
    task: PausePointTask.Breathing,
    onCompleted: () -> Unit
) {
    var currentRound by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("Inhale") }
    var phaseProgress by remember { mutableFloatStateOf(0f) }
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        for (round in 0 until task.rounds) {
            currentRound = round
            phase = "Inhale"
            animatable.snapTo(0f)
            animatable.animateTo(1f, animationSpec = tween(4000, easing = LinearEasing))
            phase = "Hold"
            animatable.animateTo(1f, animationSpec = tween(2000, easing = LinearEasing))
            phase = "Exhale"
            animatable.animateTo(0f, animationSpec = tween(4000, easing = LinearEasing))
        }
        onCompleted()
    }

    val scale by remember { derivedStateOf { animatable.value } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = phase,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = when (phase) {
                "Inhale" -> MaterialTheme.colorScheme.primary
                "Hold" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            }
        )

        val containerColor = MaterialTheme.colorScheme.primaryContainer
        val primaryColor = MaterialTheme.colorScheme.primary

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            Canvas(modifier = Modifier.size(100.dp)) {
                drawCircle(
                    color = containerColor,
                    radius = size.minDimension / 2
                )
                drawCircle(
                    color = primaryColor,
                    radius = size.minDimension / 2 * scale,
                    alpha = 0.6f
                )
            }
        }

        Text(
            text = "Round ${currentRound + 1}/${task.rounds}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WalkTask(
    task: PausePointTask.Walk,
    onCompleted: () -> Unit,
    onUserActivity: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    var stepsDone by remember { mutableIntStateOf(0) }
    var sensorAvailable by remember { mutableStateOf(true) }
    val lastStepTime = remember { java.util.concurrent.atomic.AtomicLong(0L) }
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnUserActivity by rememberUpdatedState(onUserActivity)

    val listener = remember {
        object : SensorEventListener {
            private val gravity = FloatArray(3)
            private val lastStepGravity = FloatArray(3)
            private var gravityInitialized = false
            private var hasStepReference = false
            private var rotationBlockUntil = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                gravity[0] = gravity[0] * 0.85f + x * 0.15f
                gravity[1] = gravity[1] * 0.85f + y * 0.15f
                gravity[2] = gravity[2] * 0.85f + z * 0.15f

                val gx = gravity[0]
                val gy = gravity[1]
                val gz = gravity[2]
                val gMag = sqrt((gx * gx + gy * gy + gz * gz).toDouble())
                if (!gravityInitialized) {
                    if (gMag < 6.0) return
                    gravityInitialized = true
                }

                val now = System.currentTimeMillis()

                val lgx = lastStepGravity[0]
                val lgy = lastStepGravity[1]
                val lgz = lastStepGravity[2]
                val lgMag = sqrt((lgx * lgx + lgy * lgy + lgz * lgz).toDouble())
                if (hasStepReference && gMag > 1e-3 && lgMag > 1e-3) {
                    val dot = ((gx * lgx + gy * lgy + gz * lgz) / (gMag * lgMag)).coerceIn(-1.0, 1.0)
                    if (dot < 0.64) {
                        lastStepGravity[0] = (gx / gMag).toFloat()
                        lastStepGravity[1] = (gy / gMag).toFloat()
                        lastStepGravity[2] = (gz / gMag).toFloat()
                        rotationBlockUntil = now + 800
                        return
                    }
                }

                if (now < rotationBlockUntil) return

                val linX = x - gx
                val linY = y - gy
                val linZ = z - gz
                val linMag = sqrt((linX * linX + linY * linY + linZ * linZ).toDouble())
                if (linMag > 2.0 && now - lastStepTime.get() > 350) {
                    lastStepTime.set(now)
                    hasStepReference = true
                    lastStepGravity[0] = (gx / gMag).toFloat()
                    lastStepGravity[1] = (gy / gMag).toFloat()
                    lastStepGravity[2] = (gz / gMag).toFloat()
                    stepsDone++
                    currentOnUserActivity()
                    if (stepsDone >= task.steps) currentOnCompleted()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    DisposableEffect(accelerometer) {
        sensorAvailable = accelerometer != null
        if (accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.DirectionsWalk,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "$stepsDone / ${task.steps}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = if (sensorAvailable) "Walk naturally — your steps are counted by the accelerometer"
            else "Your device has no accelerometer. Tap to count instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!sensorAvailable) {
            ZenithButton(
                onClick = {
                    stepsDone++
                    currentOnUserActivity()
                    if (stepsDone >= task.steps) currentOnCompleted()
                },
                text = if (stepsDone < task.steps) "Step ($stepsDone)" else "Done!",
                type = if (stepsDone < task.steps) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                size = ZenithButtonSize.Large,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QrScanTask(
    task: PausePointTask.QrScan,
    onTaskCompleted: () -> Unit,
    onUserActivity: () -> Unit
) {
    val hasValidCodes = task.validCodes.isNotEmpty()
    var showError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cameraGranted = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasValidCodes) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                QrScanner(
                    active = true,
                    permissionGranted = cameraGranted,
                    permissionMessage = "Camera permission is required to scan a saved QR code",
                    onQrDetected = { scanned ->
                        onUserActivity()
                        if (scanned.trim() in task.validCodes) {
                            onTaskCompleted()
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "QR Code: ${task.code}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Show this code to your camera\nto simulate scanning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (showError) {
            Text(
                text = "Wrong code — scan one of your saved QR codes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        if (hasValidCodes) {
            Text(
                text = "Align a saved QR code within the frame",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NumberSlideTask(
    task: PausePointTask.NumberSlide,
    onCompleted: () -> Unit
) {
    val gridSize = task.size
    val totalTiles = gridSize * gridSize
    var tiles by remember {
        mutableStateOf(
            (1 until totalTiles).shuffled().toMutableList().let {
                it.add(0)
                it
            }
        )
    }
    var moves by remember { mutableIntStateOf(0) }
    val solved = remember { derivedStateOf { tiles == (1 until totalTiles).toList() + 0 } }

    LaunchedEffect(solved.value) {
        if (solved.value && moves > 0) onCompleted()
    }

    fun canMove(index: Int): Boolean {
        val emptyIndex = tiles.indexOf(0)
        val row = index / gridSize
        val col = index % gridSize
        val emptyRow = emptyIndex / gridSize
        val emptyCol = emptyIndex % gridSize
        return (row == emptyRow && kotlin.math.abs(col - emptyCol) == 1) ||
                (col == emptyCol && kotlin.math.abs(row - emptyRow) == 1)
    }

    fun move(index: Int) {
        if (!canMove(index)) return
        val emptyIndex = tiles.indexOf(0)
        tiles = tiles.toMutableList().apply {
            this[emptyIndex] = this[index]
            this[index] = 0
        }
        moves++
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Moves: $moves",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0 until gridSize) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until gridSize) {
                        val index = row * gridSize + col
                        val value = tiles[index]
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (value == 0) Color.Transparent
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .then(
                                    if (value != 0) Modifier.clickable { move(index) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (value != 0) {
                                Text(
                                    text = "$value",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        if (solved.value && moves > 0) {
            Text(
                text = "Puzzle solved in $moves moves!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SwitchTask(
    task: PausePointTask.Switch,
    onCompleted: () -> Unit
) {
    val leverCount = task.leverCount
    val targetSequence = remember(leverCount) { List(leverCount) { Random.nextBoolean() } }
    val currentStates = remember(leverCount) {
        mutableStateListOf<Boolean>().apply {
            repeat(leverCount) { add(Random.nextBoolean()) }
            if (indices.all { this[it] == targetSequence[it] }) this[0] = !this[0]
        }
    }
    val solved = remember {
        derivedStateOf { currentStates.indices.all { currentStates[it] == targetSequence[it] } }
    }

    LaunchedEffect(solved.value) {
        if (solved.value) onCompleted()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            targetSequence.forEach { isOn ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(width = 48.dp, height = 24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (isOn) "ON" else "OFF",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(leverCount) { index ->
                Lever(
                    isOn = currentStates[index],
                    onToggle = { currentStates[index] = it }
                )
            }
        }

        if (solved.value) {
            Text(
                text = "Sequence matched!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CountingTask(
    task: PausePointTask.Counting,
    onCompleted: () -> Unit,
    onUserActivity: () -> Unit
) {
    var count by remember { mutableIntStateOf(0) }
    var showComplete by remember { mutableStateOf(false) }
    var cooldownRemainingMs by remember { mutableLongStateOf(0L) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var rapidTaps by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldownRemainingMs) {
        while (cooldownRemainingMs > 0L) {
            delay(100)
            cooldownRemainingMs = (cooldownRemainingMs - 100L).coerceAtLeast(0L)
        }
        rapidTaps = 0
    }

    fun registerTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 180) {
            rapidTaps++
            if (rapidTaps >= 8) {
                cooldownRemainingMs = 3000L
                rapidTaps = 0
            }
        } else {
            rapidTaps = 0
        }
        lastTapTime = now
        count++
        onUserActivity()
        if (count >= task.targetNumber) {
            showComplete = true
            onCompleted()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (task.label.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = if (task.label.isNotEmpty()) "$count / ${task.targetNumber} ${task.label}"
            else "$count / ${task.targetNumber}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        if (!showComplete) {
            val cooling = cooldownRemainingMs > 0L
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (cooling) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                    .clickable(enabled = !cooling) { registerTap() },
                contentAlignment = Alignment.Center
            ) {
                if (cooling) {
                    Text(
                        text = "${(cooldownRemainingMs / 1000L) + 1}s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Tap",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (cooling) {
                Text(
                    text = "Too fast — wait out the penalty",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TypingTask(
    task: PausePointTask.Typing,
    onCompleted: () -> Unit,
    onUserActivity: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    val isCorrect = typedText == task.sentence
    val focusRequester = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused

    LaunchedEffect(Unit) {
        delay(600)
        focusRequester.requestFocus()
    }

    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            delay(150)
            focusRequester.requestFocus()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onKeyboardFocusChange(false) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Text(
                text = task.sentence,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = typedText,
            onValueChange = {
                typedText = it
                onUserActivity()
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    val focused = state.isFocused
                    if (focused != fieldFocused) {
                        fieldFocused = focused
                        onKeyboardFocusChange(focused)
                    }
                },
            placeholder = { Text("Type the sentence above") },
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (isCorrect) onCompleted()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = if (typedText.isNotEmpty() && !isCorrect)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isCorrect)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
        )

        if (typedText.isNotEmpty() && !isCorrect) {
            Text(
                text = "Keep typing...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isCorrect) {
            Text(
                text = "Correct!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            ZenithButton(
                onClick = onCompleted,
                text = "Continue",
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.Large,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MathTask(
    task: PausePointTask.Math,
    onCompleted: () -> Unit,
    onUserActivity: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit
) {
    val a = remember { Random.nextInt(1, task.maxOperand + 1) }
    val b = remember { Random.nextInt(1, task.maxOperand + 1) }
    var answer by remember { mutableStateOf("") }
    val isCorrect = answer.toIntOrNull() == a + b
    val focusRequester = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused

    LaunchedEffect(isCorrect) {
        if (isCorrect) onCompleted()
    }

    LaunchedEffect(Unit) {
        delay(600)
        focusRequester.requestFocus()
    }

    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            delay(150)
            focusRequester.requestFocus()
        }
    }

    DisposableEffect(Unit) {
        onDispose { onKeyboardFocusChange(false) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OperandCard(a)
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            OperandCard(b)
            Text(
                text = "=",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            AnswerCard(
                value = answer,
                onValueChange = {
                    answer = it.filter(Char::isDigit)
                    onUserActivity()
                },
                isCorrect = isCorrect,
                focusRequester = focusRequester,
                onFocusChanged = { focused ->
                    if (focused != fieldFocused) {
                        fieldFocused = focused
                        onKeyboardFocusChange(focused)
                    }
                },
                onDone = {
                    if (isCorrect) onCompleted()
                }
            )
        }

        if (answer.isNotEmpty() && !isCorrect) {
            Text(
                text = "Try again",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (isCorrect) {
            Text(
                text = "Correct!",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            ZenithButton(
                onClick = onCompleted,
                text = "Continue",
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.Large,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OperandCard(value: Int) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AnswerCard(
    value: String,
    onValueChange: (String) -> Unit,
    isCorrect: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        isCorrect -> MaterialTheme.colorScheme.primary
        value.isNotEmpty() -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.5.dp, borderColor, shape)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun ChooseAppTask(
    task: PausePointTask.ChooseApp,
    onCompleted: () -> Unit,
    onOpenApp: ((String) -> Unit)?
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }
    val currentOnOpenApp by rememberUpdatedState(onOpenApp)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - startTime) / 5000f).coerceIn(0f, 1f)
                holdProgress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            onCompleted()
            holdProgress.snapTo(0f)
        } else {
            holdProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (task.suggestedPackage.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data("app-icon://${task.suggestedPackage}")
                    .crossfade(500)
                    .build(),
                contentDescription = task.suggestedAppName,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }

        Text(
            text = "How about using",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = task.suggestedAppName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "instead?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ZenithButton(
                onClick = { currentOnOpenApp?.invoke(task.suggestedPackage) },
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = "Open ${task.suggestedAppName}",
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.ExtraLarge,
                fillMaxWidth = true,
                enabled = onOpenApp != null && task.suggestedPackage.isNotEmpty(),
                shape = RoundedCornerShape(
                    topStart = 32.dp, topEnd = 32.dp,
                    bottomStart = 12.dp, bottomEnd = 12.dp
                )
            )

            ZenithButton(
                onClick = {},
                text = if (isPressed) {
                    val seconds = ceil(5 * (1f - holdProgress.value)).toInt()
                    "Hold ${seconds}s to dismiss"
                } else {
                    "I'll do it later (hold 5s)"
                },
                type = ZenithButtonType.Tonal,
                size = ZenithButtonSize.ExtraLarge,
                fillMaxWidth = true,
                interactionSource = interactionSource,
                backgroundProgressProvider = { holdProgress.value },
                shape = RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = 32.dp, bottomEnd = 32.dp
                )
            )
        }
    }
}
