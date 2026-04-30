package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PauseBottomSheet(
    onDismiss: () -> Unit,
    onConfirmPause: (Int?) -> Unit,
    leverCount: Int = 3,
    puzzleTimeoutSeconds: Int? = null,
    holdDurationMillis: Long = 10000L,
    processingDurationMillis: Long = 5000L,
    successDisplayMillis: Long = 2000L
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var currentPhase by remember { mutableIntStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentPhase,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))).togetherWith(
                            slideOutHorizontally { -it } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                        )
                    } else {
                        (slideInHorizontally { -it } + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow))).togetherWith(
                            slideOutHorizontally { it } + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow))
                        )
                    }.using(SizeTransform(clip = false))
                },
                label = "PausePhaseTransition"
            ) { phase ->
                when (phase) {
                    1 -> PhaseOnePuzzle(
                        leverCount = leverCount,
                        timeoutSeconds = puzzleTimeoutSeconds,
                        onComplete = { currentPhase = 2 },
                        onFailure = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                    )
                    2 -> PhaseTwoHold(
                        durationMillis = holdDurationMillis,
                        onComplete = { currentPhase = 3 }
                    )
                    3 -> PhaseThreeLoading(
                        durationMillis = processingDurationMillis,
                        onComplete = { currentPhase = 4 }
                    )
                    4 -> SuccessPopup(
                        displayMillis = successDisplayMillis,
                        onDismiss = { currentPhase = 5 }
                    )
                    5 -> PhaseFourSelection(
                        onConfirm = { duration ->
                            scope.launch {
                                sheetState.hide()
                                onConfirmPause(duration)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Text("Nevermind", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun PhaseOnePuzzle(
    leverCount: Int,
    timeoutSeconds: Int?,
    onComplete: () -> Unit,
    onFailure: () -> Unit
) {
    val targetSequence = remember(leverCount) { List(leverCount) { kotlin.random.Random.nextBoolean() } }
    val currentStates = remember(leverCount) {
        mutableStateListOf<Boolean>().apply {
            repeat(leverCount) { add(kotlin.random.Random.nextBoolean()) }
            // Ensure initial state is not already solved
            if (size > 0 && indices.all { this[it] == targetSequence[it] }) {
                this[0] = !this[0]
            }
        }
    }

    var timeLeft by remember(timeoutSeconds) { mutableIntStateOf(timeoutSeconds ?: 0) }
    
    if (timeoutSeconds != null) {
        LaunchedEffect(timeoutSeconds) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            onFailure()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Phase 1: Security Puzzle",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (timeoutSeconds != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Time remaining: ${timeLeft}s",
                style = MaterialTheme.typography.labelLarge,
                color = if (timeLeft < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Match the sequence",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
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

        Spacer(modifier = Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(leverCount) { index ->
                Lever(
                    isOn = currentStates[index],
                    onToggle = { 
                        currentStates[index] = it
                        if (currentStates.indices.all { i -> currentStates[i] == targetSequence[i] }) {
                            onComplete()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun Lever(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val thumbPosition = remember {
        Animatable(if (isOn) 1f else 0f).apply {
            updateBounds(0f, 1f)
        }
    }

    val knobColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LeverColor"
    )

    LaunchedEffect(isOn) {
        thumbPosition.animateTo(
            targetValue = if (isOn) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    var totalWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp)
            .onSizeChanged { totalWidth = it.width.toFloat() }
            .pointerInput(isOn) {
                detectTapGestures { onToggle(!isOn) }
            }
            .pointerInput(isOn) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        val travel = totalWidth * 0.7f
                        if (travel > 0) {
                            val newValue = (thumbPosition.value + dragAmount / travel).coerceIn(0f, 1f)
                            scope.launch { thumbPosition.snapTo(newValue) }
                        }
                    },
                    onDragEnd = {
                        val targetState = thumbPosition.value > 0.5f
                        onToggle(targetState)
                        
                        if (targetState == isOn) {
                            scope.launch {
                                thumbPosition.animateTo(
                                    if (isOn) 1f else 0f,
                                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .fillMaxHeight(0.8f)
                .graphicsLayer {
                    val travelDistance = size.width * 2.333f
                    translationX = thumbPosition.value * travelDistance
                }
                .clip(CircleShape)
                .background(knobColor)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhaseTwoHold(durationMillis: Long, onComplete: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    val progress = animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = if (isHolding) tween(durationMillis.toInt(), easing = LinearEasing) else spring(stiffness = Spring.StiffnessLow),
        label = "HoldProgress",
        finishedListener = { if (it == 1f) onComplete() }
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 2: Verification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hold the circle for ${durationMillis / 1000} seconds", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
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
                }
        ) {
            CircularWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                wavelength = 32.dp
            )
            Surface(
                shape = CircleShape,
                color = if (isHolding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = if (isHolding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhaseThreeLoading(durationMillis: Long, onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(durationMillis)
        onComplete()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 3: Processing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Finalizing permission...", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(48.dp))

        CircularWavyProgressIndicator(
            modifier = Modifier.size(120.dp),
            color = MaterialTheme.colorScheme.tertiary,
            wavelength = 32.dp
        )
    }
}

@Composable
fun PhaseFourSelection(onConfirm: (Int?) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Access Granted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("How long should we pause?", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))

        val options = listOf(
            "1 Hour" to 1,
            "6 Hours" to 6,
            "24 Hours" to 24,
            "Until I resume" to null
        )

        options.forEachIndexed { index, (label, value) ->
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                options.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                else -> RoundedCornerShape(4.dp)
            }

            Surface(
                onClick = { onConfirm(value) },
                shape = shape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().clip(shape)
            ) {
                Box(
                    modifier = Modifier.padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (index < options.size - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun SuccessPopup(displayMillis: Long, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(displayMillis)
        onDismiss()
    }

    val sunnyShape = remember {
        GenericShape { size, _ ->
            val path = MaterialShapes.Sunny.toPath().asComposePath()
            val matrix = Matrix()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            addPath(path)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = sunnyShape,
        modifier = Modifier.size(120.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
