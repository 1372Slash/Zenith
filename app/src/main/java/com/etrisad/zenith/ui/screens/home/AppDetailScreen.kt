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
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import com.etrisad.zenith.data.local.entity.FocusType
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.etrisad.zenith.ui.components.ConfirmBottomSheet
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AppDetailScreen(
    packageName: String,
    viewModel: HomeViewModel,
    innerPadding: PaddingValues
) {
    val uiState by viewModel.appDetailUiState.collectAsState()
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    LaunchedEffect(packageName) {
        viewModel.loadAppDetail(packageName)
    }

    DisposableEffect(packageName) {
        onDispose {
            viewModel.clearAppDetail(packageName)
        }
    }

    val shield = uiState.shieldEntity
    val isFocusActive = shield != null
    val isEffectivelyPaused = shield?.let {
        it.isPaused && (it.pauseEndTimestamp == 0L || nowMillis < it.pauseEndTimestamp)
    } ?: false
    val targetMillis = shield?.timeLimitMinutes?.let { it * 60 * 1000L } ?: 0L

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 32.dp
        )
    ) {
            item {
                AppHeader(
                    appName = uiState.appName,
                    packageName = uiState.packageName,
                    icon = uiState.icon,
                    focusType = uiState.type,
                    isActive = isFocusActive,
                    isPaused = isEffectivelyPaused,
                    pauseEndTimestamp = shield?.pauseEndTimestamp ?: 0L,
                    nowMillis = nowMillis
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
                    visible = isFocusActive,
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
                        onDaySelected = { },
                        shape = RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = if (!isFocusActive) 24.dp else 8.dp,
                            bottomEnd = if (!isFocusActive) 24.dp else 8.dp
                        )
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp))
                }
                if (isFocusActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            item {
                AnimatedVisibility(
                    visible = isFocusActive,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (shield != null) {
                        var showPauseSheet by remember { mutableStateOf(false) }

                        Column {
                            AnimatedContent(
                                targetState = isEffectivelyPaused,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                                        .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 0.95f))
                                },
                                label = "PauseResumeTransition"
                            ) { isPaused ->
                                if (isPaused) {
                                    ResumeCard(
                                        pauseEndTimestamp = shield.pauseEndTimestamp,
                                        onResume = { viewModel.resumeShield() },
                                        formatDuration = { viewModel.formatDuration(it) },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                        nowMillis = nowMillis
                                    )
                                } else {
                                    PauseShieldCard(
                                        onPauseClick = { showPauseSheet = true },
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(2.dp))

                            DeleteShieldCard(
                                onDelete = {
                                    viewModel.deleteShieldFromDetail()
                                },
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            )
                        }

                        if (showPauseSheet) {
                            ConfirmBottomSheet(
                                onDismiss = { showPauseSheet = false },
                                onConfirm = { duration ->
                                    viewModel.pauseShield(duration)
                                    showPauseSheet = false
                                },
                                leverCount = 5,
                                showTimeSelection = true
                            )
                        }
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
    isActive: Boolean,
    isPaused: Boolean = false,
    pauseEndTimestamp: Long = 0L,
    nowMillis: Long = System.currentTimeMillis()
) {
    val saturation by animateFloatAsState(
        targetValue = if (isPaused) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "IconSaturation"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isPaused) 0.6f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "IconAlpha"
    )

    val colorFilter = remember(saturation) {
        val matrix = ColorMatrix().apply { setToSaturation(saturation) }
        ColorFilter.colorMatrix(matrix)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(
                    painter = BitmapPainter(icon.toBitmap().asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    colorFilter = colorFilter
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Android,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = iconAlpha)
                    )
                }
            }

            if (isPaused) {
                val remainingMillis = remember(pauseEndTimestamp, nowMillis) {
                    if (pauseEndTimestamp == 0L) -1L
                    else (pauseEndTimestamp - nowMillis).coerceAtLeast(0L)
                }

                val initialPauseDuration = remember(pauseEndTimestamp) {
                    val diff = pauseEndTimestamp - System.currentTimeMillis()
                    when {
                        diff <= 3600000L -> 3600000L
                        diff <= 21600000L -> 21600000L
                        else -> 86400000L
                    }
                }

                val progress = remember(remainingMillis) {
                    if (pauseEndTimestamp == 0L) 1f
                    else (remainingMillis.toFloat() / initialPauseDuration).coerceIn(0f, 1f)
                }

                Box(
                    modifier = Modifier
                        .size(90.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .offset(x = 4.dp, y = (-4).dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { if (pauseEndTimestamp == 0L) 1f else progress },
                                modifier = Modifier.size(26.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                strokeWidth = 2.dp,
                                trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                strokeCap = StrokeCap.Round
                            )
                            Icon(
                                imageVector = Icons.Outlined.Pause,
                                contentDescription = "Paused",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
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
                    color = MaterialTheme.colorScheme.tertiary,
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
    shape: androidx.compose.ui.graphics.Shape,
    nowMillis: Long = System.currentTimeMillis()
) {
    val remainingMillis = remember(pauseEndTimestamp, nowMillis) {
        if (pauseEndTimestamp == 0L) -1L
        else (pauseEndTimestamp - nowMillis).coerceAtLeast(0L)
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
                        "Estimated time: $resumeTimeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
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
