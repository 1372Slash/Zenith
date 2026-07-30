package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PomodoroAfterType {
    SHIELD,
    GOAL,
    BREAK_STARTED,
    BLOCKED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PomodoroPuzzleContent(
    userPreferences: UserPreferences?,
    afterContentType: PomodoroAfterType = PomodoroAfterType.BREAK_STARTED,
    skipPuzzle: Boolean = false,
    packageName: String = "",
    appName: String = "",
    shield: ShieldEntity? = null,
    totalUsageToday: Long = 0,
    totalGlobalUsageToday: Long = 0,
    onAllowUse: (Int, Boolean) -> Unit = { _, _ -> },
    onGoalDismiss: () -> Unit = {},
    onComplete: () -> Unit,
    onCloseApp: () -> Unit
) {
    var showContent by remember { mutableStateOf(false) }
    var puzzlePhase by remember { mutableIntStateOf(1) }
    var showingAfterContent by remember { mutableStateOf(false) }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) { showContent = true }
    LaunchedEffect(skipPuzzle) { if (skipPuzzle) showingAfterContent = true }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentOnAllowUse by rememberUpdatedState(onAllowUse)
    val currentOnGoalDismiss by rememberUpdatedState(onGoalDismiss)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)

    val dragUses = if (shield?.type == FocusType.SHIELD) shield.currentPeriodUses else null
    val dragMaxUses = if (shield?.type == FocusType.SHIELD) shield.maxUsesPerPeriod else null
    val dragEmergency = if (shield?.type == FocusType.SHIELD) shield.emergencyUseCount else null

    InterceptBottomSheet(
        visible = showContent,
        backgroundAlpha = backgroundAlpha,
        isLandscape = isLandscape,
        userPreferences = userPreferences,
        showBedtimePill = false,
        contentKey = if (showingAfterContent) "after" else "puzzle",
        dragHandleCurrentUses = dragUses,
        dragHandleMaxUses = dragMaxUses,
        dragHandleEmergencyCount = dragEmergency,
        onCloseApp = {
            showContent = false
            currentOnCloseApp()
        }
    ) { key ->
        if (key == "puzzle") {
            PuzzleContent(
                phase = puzzlePhase,
                onPhaseChange = { puzzlePhase = it },
                onComplete = { showingAfterContent = true },
                onCloseApp = {
                    showContent = false
                    currentOnCloseApp()
                }
            )
        } else {
            when (afterContentType) {
                PomodoroAfterType.SHIELD -> {
                    ShieldAfterContent(
                        packageName = packageName,
                        appName = appName,
                        shield = shield,
                        totalUsageToday = totalUsageToday,
                        totalGlobalUsageToday = totalGlobalUsageToday,
                        userPreferences = userPreferences,
                        onAllowUse = { minutes ->
                            showContent = false
                            currentOnAllowUse(minutes, false)
                        },
                        onCloseApp = {
                            showContent = false
                            currentOnCloseApp()
                        }
                    )
                }
                PomodoroAfterType.GOAL -> {
                    GoalAfterContent(
                        packageName = packageName,
                        appName = appName,
                        shield = shield,
                        totalUsageToday = totalUsageToday,
                        totalGlobalUsageToday = totalGlobalUsageToday,
                        userPreferences = userPreferences,
                        onGoalDismiss = {
                            showContent = false
                            currentOnGoalDismiss()
                        },
                        onCloseApp = {
                            showContent = false
                            currentOnCloseApp()
                        }
                    )
                }
                PomodoroAfterType.BREAK_STARTED -> {
                    BreakStartedContent {
                        showContent = false
                        currentOnComplete()
                    }
                }
                PomodoroAfterType.BLOCKED -> {
                    BlockedAfterContent(
                        appName = appName,
                        onCloseApp = {
                            showContent = false
                            currentOnCloseApp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PuzzleContent(
    phase: Int,
    onPhaseChange: (Int) -> Unit,
    onComplete: () -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = phase,
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
            label = "PuzzlePhaseTransition"
        ) { p ->
            when (p) {
                1 -> PgPhaseOne(
                    leverCount = 3,
                    onComplete = { onPhaseChange(2) },
                    onFailure = onCloseApp
                )
                2 -> PgPhaseTwoHold(
                    durationMillis = 10000L,
                    onComplete = { onPhaseChange(3) }
                )
                3 -> PgPhaseThreeLoading(
                    durationMillis = 5000L,
                    onComplete = { onPhaseChange(4) }
                )
                4 -> PgPhaseSuccess(
                    displayMillis = 2000L,
                    onDismiss = onComplete
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (phase <= 2) {
            ZenithButton(
                onClick = onCloseApp,
                modifier = Modifier.fillMaxWidth(),
                text = "Nevermind",
                type = ZenithButtonType.Text,
                contentColor = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ShieldAfterContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPreferences: UserPreferences?,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val isWebsite = WebsiteRepository.isWebsitePackageName(packageName)
    val remainingMinutes = shield?.let {
        if (it.timeLimitMinutes <= 0) null
        else {
            val limitMillis = it.timeLimitMinutes * 60 * 1000L
            ((limitMillis - totalUsageToday) / (60 * 1000L)).toInt().coerceAtLeast(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("app-icon://$packageName")
                    .crossfade(500)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

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

        if (userPreferences != null) {
            ShieldProgressSection(
                shield = shield,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                userPrefs = userPreferences
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "How long do you want to use it?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        DurationButtonsGrid(remainingMinutes = remainingMinutes, onAllowUse = onAllowUse)

        Spacer(modifier = Modifier.height(24.dp))

        CloseAppTextButton(onCloseApp, size = ZenithButtonSize.ExtraLarge, isWebsite = isWebsite)
    }
}

@Composable
private fun GoalAfterContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPreferences: UserPreferences?,
    onGoalDismiss: () -> Unit,
    onCloseApp: () -> Unit
) {
    val isWebsite = WebsiteRepository.isWebsitePackageName(packageName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("app-icon://$packageName")
                    .crossfade(500)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                error = {
                    Icon(
                        Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Goal Pursuit",
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

        if (userPreferences != null && shield != null) {
            GoalProgressSection(
                shield = shield,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                userPrefs = userPreferences
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        ZenithButton(
            onClick = onGoalDismiss,
            text = "Got it, let's continue",
            icon = Icons.Outlined.Check,
            fillMaxWidth = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        CloseAppTextButton(onCloseApp, size = ZenithButtonSize.Large, isWebsite = isWebsite)
    }
}

@Composable
private fun BreakStartedContent(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onComplete()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Break Started",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your allowed apps are now accessible.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun BlockedAfterContent(appName: String, onCloseApp: () -> Unit) {
    val autoKickProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(2000)
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
            autoKickProgress.snapTo(p)
            if (p >= 1f) break
            delay(16)
        }
        onCloseApp()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Stay Focused",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "$appName is blocked during your Deep Focus session.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        CloseAppTextButton(
            onCloseApp = onCloseApp,
            autoKickProgress = { autoKickProgress.value },
            size = ZenithButtonSize.ExtraLarge
        )
    }
}

@Composable
private fun PgPhaseOne(leverCount: Int, onComplete: () -> Unit, onFailure: () -> Unit) {
    val targetSequence = remember(leverCount) {
        List(leverCount) { kotlin.random.Random.nextBoolean() }
    }
    val currentStates = remember(leverCount) {
        mutableStateListOf<Boolean>().apply {
            repeat(leverCount) { add(kotlin.random.Random.nextBoolean()) }
            if (size > 0 && indices.all { this[it] == targetSequence[it] }) {
                this[0] = !this[0]
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 1: Security Puzzle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Match the sequence", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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

        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(leverCount) { index ->
                PgLever(
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
private fun PgLever(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val thumbPosition = remember { Animatable(if (isOn) 1f else 0f).apply { updateBounds(0f, 1f) } }

    val knobColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LeverColor"
    )

    LaunchedEffect(isOn) {
        thumbPosition.animateTo(
            targetValue = if (isOn) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    var totalWidth by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp)
            .onSizeChanged { totalWidth = it.width.toFloat() }
            .pointerInput(isOn) { detectTapGestures { onToggle(!isOn) } }
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
                .fillMaxWidth(0.3f).fillMaxHeight(0.8f)
                .graphicsLayer {
                    val travelDistance = size.width * 2.333f
                    translationX = thumbPosition.value * travelDistance
                }
                .clip(CircleShape).background(knobColor)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PgPhaseTwoHold(durationMillis: Long, onComplete: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isHolding) {
        if (isHolding) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                holdProgress = (elapsed.toFloat() / durationMillis).coerceIn(0f, 1f)
                if (holdProgress >= 1f) break
                delay(16)
            }
            onComplete()
        } else holdProgress = 0f
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 2: Verification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hold the circle for ${durationMillis / 1000} seconds", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(48.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp).clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        isHolding = true
                        try { awaitRelease() } finally { isHolding = false }
                    })
                }
        ) {
            CircularWavyProgressIndicator(
                progress = { holdProgress },
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
                    Icon(Icons.Outlined.TouchApp, null, tint = if (isHolding) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PgPhaseThreeLoading(durationMillis: Long, onComplete: () -> Unit) {
    LaunchedEffect(Unit) { delay(durationMillis); onComplete() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 3: Processing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Finalizing permission...", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(48.dp))
        CircularWavyProgressIndicator(modifier = Modifier.size(120.dp), color = MaterialTheme.colorScheme.tertiary, wavelength = 32.dp)
    }
}

@Composable
private fun PgPhaseSuccess(displayMillis: Long, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { delay(displayMillis); onDismiss() }
    val sunnyShape = remember {
        GenericShape { size, _ ->
            val path = MaterialShapes.Sunny.toPath().asComposePath()
            val matrix = Matrix(); matrix.scale(size.width, size.height); path.transform(matrix); addPath(path)
        }
    }
    Surface(color = MaterialTheme.colorScheme.primary, shape = sunnyShape, modifier = Modifier.size(120.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(64.dp))
        }
    }
}
