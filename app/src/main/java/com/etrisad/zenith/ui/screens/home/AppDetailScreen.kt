package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDetailScreen(
    @Suppress("UNUSED_PARAMETER") packageName: String,
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.appDetailUiState.collectAsState()

    LaunchedEffect(packageName) {
        viewModel.loadAppDetail(packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val targetMillis = uiState.shieldEntity?.timeLimitMinutes?.let { it * 60 * 1000L } ?: 0L
        val isFocusActive = uiState.shieldEntity != null

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 32.dp
            )
        ) {
            item {
                AppHeader(
                    appName = uiState.appName,
                    packageName = uiState.packageName,
                    icon = uiState.icon,
                    focusType = uiState.type,
                    isActive = isFocusActive
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                UsageCard(
                    title = "Today's Usage",
                    time = viewModel.formatDuration(uiState.todayUsage),
                    targetMillis = targetMillis,
                    currentUsage = uiState.todayUsage,
                    focusType = uiState.type,
                    formatDuration = { viewModel.formatDuration(it) },
                    isActive = isFocusActive,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                UsageTrendsRow(
                    yesterdayTime = viewModel.formatDuration(uiState.yesterdayUsage),
                    percentageChange = uiState.percentageChange,
                    focusType = uiState.type
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                AnimatedVisibility(
                    visible = uiState.shieldEntity != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        StreakCard(
                            currentStreak = uiState.currentStreak,
                            bestStreak = uiState.bestStreak,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            item {
                if (uiState.usageHistory.isNotEmpty()) {
                    UsageHistoryCard(
                        history = uiState.usageHistory,
                        targetMillis = targetMillis,
                        focusType = uiState.type,
                        formatDuration = { viewModel.formatDuration(it) },
                        onDaySelected = { /* No-op, we stay on Today's Usage in the header */ },
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = if (uiState.shieldEntity == null) 24.dp else 8.dp,
                            bottomEnd = if (uiState.shieldEntity == null) 24.dp else 8.dp
                        )
                    )
                } else {
                    // Placeholder during loading to prevent layout jump and ensure animation triggers correctly later
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                AnimatedVisibility(
                    visible = uiState.shieldEntity != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val shield = uiState.shieldEntity
                    if (shield != null) {
                        var showPauseSheet by remember { mutableStateOf(false) }

                        if (shield.isPaused) {
                            ResumeCard(
                                pauseEndTimestamp = shield.pauseEndTimestamp,
                                onResume = { viewModel.resumeShield() },
                                formatDuration = { viewModel.formatDuration(it) },
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                        } else {
                            PauseShieldCard(
                                onPauseClick = { showPauseSheet = true },
                                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        if (showPauseSheet) {
                            PauseBottomSheet(
                                onDismiss = { showPauseSheet = false },
                                onConfirmPause = { duration ->
                                    viewModel.pauseShield(duration)
                                    showPauseSheet = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = uiState.shieldEntity != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DeleteShieldCard(
                        onDelete = {
                            viewModel.deleteShieldFromDetail()
                        },
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppHeader(
    appName: String,
    @Suppress("UNUSED_PARAMETER") packageName: String,
    icon: android.graphics.drawable.Drawable?,
    focusType: FocusType?,
    isActive: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Image(
                painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Android, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        AnimatedVisibility(
            visible = isActive && focusType != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            if (focusType != null) {
                val typeColor = if (focusType == FocusType.SHIELD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                Surface(
                    color = typeColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = focusType.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = typeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UsageCard(
    title: String,
    time: String,
    targetMillis: Long,
    currentUsage: Long,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    isActive: Boolean,
    shape: androidx.compose.ui.graphics.Shape
) {
    val isTargetSet = targetMillis > 0
    val isExceeded = isTargetSet && focusType == FocusType.SHIELD && currentUsage > targetMillis

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = time,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = isActive && isTargetSet && focusType != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (focusType != null) {
                    val isGoal = focusType == FocusType.GOAL
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isGoal) {
                                if (currentUsage >= targetMillis) "Goal achieved! Keep it up."
                                else "Goal: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} more to go)"
                            } else {
                                if (isExceeded) "Limit exceeded!"
                                else "Limit: ${formatDuration(targetMillis)} (${formatDuration(targetMillis - currentUsage)} left)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val progress = if (isGoal) {
                            (currentUsage.toFloat() / targetMillis).coerceIn(0f, 1f)
                        } else {
                            if (isExceeded) 0f
                            else ((targetMillis - currentUsage).toFloat() / targetMillis).coerceIn(0f, 1f)
                        }

                        val dashboardProgress by animateFloatAsState(
                            targetValue = progress,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = 50f),
                            label = "AppUsageProgress"
                        )

                        val progressColor = when {
                            isGoal -> MaterialTheme.colorScheme.tertiary
                            isExceeded -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }

                        LinearWavyProgressIndicator(
                            progress = { dashboardProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            color = progressColor,
                            trackColor = progressColor.copy(alpha = 0.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageTrendsRow(
    yesterdayTime: String,
    percentageChange: Float,
    focusType: FocusType?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Yesterday",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    yesterdayTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Trend",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isUp = percentageChange >= 0
                    val isPositiveTrend = if (focusType == FocusType.GOAL) isUp else !isUp
                    val trendColor = if (isPositiveTrend) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error

                    Icon(
                        imageVector = if (isUp) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = trendColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${kotlin.math.abs(percentageChange).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = trendColor
                    )
                }
            }
        }
    }
}

@Composable
fun UsageHistoryCard(
    history: List<com.etrisad.zenith.ui.viewmodel.DailyUsage>,
    targetMillis: Long,
    focusType: FocusType?,
    formatDuration: (Long) -> String,
    onDaySelected: (com.etrisad.zenith.ui.viewmodel.DailyUsage?) -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    var selectedUsage by remember { mutableStateOf<com.etrisad.zenith.ui.viewmodel.DailyUsage?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val todayDate = remember { dateFormat.format(System.currentTimeMillis()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History (21 Days)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedContent(
                    targetState = selectedUsage,
                    transitionSpec = {
                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                slideInVertically { it / 2 })
                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                    slideOutVertically { -it / 2 })
                    },
                    label = "SelectedUsageAnim"
                ) { usage ->
                    if (usage != null && dateFormat.format(usage.date) != todayDate) {
                        Text(
                            text = formatDuration(usage.totalTime),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            UsageGraph(
                history = history,
                targetMillis = targetMillis,
                focusType = focusType,
                onDaySelected = { 
                    selectedUsage = it
                    onDaySelected(it)
                }
            )
        }
    }
}

@Composable
fun StreakCard(
    currentStreak: Int,
    bestStreak: Int,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Side: Title and Best Streak
            Column {
                Text(
                    text = "Streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$bestStreak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Best Streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Right Side: Today's Streak - Sunny shape for the number, text below
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    color = MaterialTheme.colorScheme.tertiary, // Using tertiary color
                    shape = sunnyShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$currentStreak",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "days today",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PauseShieldCard(
    onPauseClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.PauseCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Pause Shield/Goal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Temporarily disable limits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onPauseClick) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Pause",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ResumeCard(
    pauseEndTimestamp: Long,
    onResume: () -> Unit,
    formatDuration: (Long) -> String,
    shape: androidx.compose.ui.graphics.Shape
) {
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.longValue = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMillis = remember(pauseEndTimestamp, currentTime.longValue) {
        if (pauseEndTimestamp == 0L) -1L 
        else (pauseEndTimestamp - currentTime.longValue).coerceAtLeast(0L)
    }

    val resumeTimeStr = remember(pauseEndTimestamp) {
        if (pauseEndTimestamp == 0L) "Manually"
        else SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(pauseEndTimestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Shield Paused",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (remainingMillis == -1L) "Paused indefinitely"
                            else "Resumes in ${formatDuration(remainingMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Button(
                    onClick = onResume,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Resume Now", style = MaterialTheme.typography.labelLarge)
                }
            }
            
            if (pauseEndTimestamp != 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Estimasi waktu aktif kembali: $resumeTimeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PauseBottomSheet(
    onDismiss: () -> Unit,
    onConfirmPause: (Int?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                label = "PausePhaseTransition"
            ) { phase ->
                when (phase) {
                    1 -> PhaseOnePuzzle(onComplete = { currentPhase = 2 })
                    2 -> PhaseTwoHold(onComplete = { currentPhase = 3 })
                    3 -> PhaseThreeLoading(onComplete = { currentPhase = 4 })
                    4 -> PhaseFourSelection(onConfirm = onConfirmPause)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Nevermind", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun PhaseOnePuzzle(onComplete: () -> Unit) {
    val targetSequence = remember { List(3) { kotlin.random.Random.nextBoolean() } }
    val currentStates = remember { mutableStateListOf(false, false, false) }
    // Initialize with random positions, but ensure at least one is wrong to start
    LaunchedEffect(Unit) {
        for (i in 0..2) {
            currentStates[i] = kotlin.random.Random.nextBoolean()
        }
        if ((0..2).all { currentStates[it] == targetSequence[it] }) {
            val idx = kotlin.random.Random.nextInt(3)
            currentStates[idx] = !currentStates[idx]
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Phase 1: Security Puzzle",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Match the sequence",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            targetSequence.forEach { isOn ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(width = 56.dp, height = 28.dp)
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

        repeat(3) { index ->
            Lever(
                isOn = currentStates[index],
                onToggle = { 
                    currentStates[index] = it
                    if ((0..2).all { i -> currentStates[i] == targetSequence[i] }) {
                        onComplete()
                    }
                }
            )
            if (index < 2) Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun Lever(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LeverThumb"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onToggle(!isOn) }
                }
        )
        
        // Knob
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .fillMaxHeight(0.8f)
                .graphicsLayer {
                    translationX = (size.width * 2.33f) * thumbOffset
                }
                .background(
                    if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhaseTwoHold(onComplete: () -> Unit) {
    var isHolding by remember { mutableStateOf(false) }
    val progress = animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = if (isHolding) tween(10000, easing = LinearEasing) else tween(500),
        label = "HoldProgress",
        finishedListener = { if (it == 1f) onComplete() }
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Phase 2: Verification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hold the circle for 10 seconds", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(48.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PhaseThreeLoading(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(10000)
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
    var showSuccess by remember { mutableStateOf(true) }

    if (showSuccess) {
        SuccessPopup(onDismiss = { showSuccess = false })
    } else {
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

            options.forEach { (label, value) ->
                Button(
                    onClick = { onConfirm(value) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(label, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun SuccessPopup(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onDismiss()
    }

    val sunnyShape = remember {
        GenericShape { size, _ ->
            val path = MaterialShapes.Sunny.toPath().asComposePath()
            val matrix = androidx.compose.ui.graphics.Matrix()
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

@Composable
fun DeleteShieldCard(
    onDelete: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Remove from Zenith",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Stop tracking limits for this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
