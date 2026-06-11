package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalOverlay(
    packageName: String,
    appName: String,
    shield: ShieldEntity,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    onGoalDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var appIconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    DisposableEffect(packageName) {
        val job = scope.launch(Dispatchers.IO) {
            val bmp = try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(120, 120).asImageBitmap()
            } catch (_: Exception) { null }
            withContext(Dispatchers.Main) { appIconBitmap = bmp }
        }
        onDispose {
            job.cancel()
            appIconBitmap = null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentOnGoalDismiss by rememberUpdatedState(onGoalDismiss)

    val combinedStateState = produceState(
        initialValue = Triple(shield, totalUsageToday, totalGlobalUsageToday),
        packageName,
        shield,
        totalUsageToday,
        totalGlobalUsageToday
    ) {
        val usm = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val cal = Calendar.getInstance()
        var lastOfDay = 0L
        var cachedOfDay = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastOfDay > 60000) {
                cal.timeInMillis = now
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cachedOfDay = cal.timeInMillis
                lastOfDay = now
            }
            val timeSinceMidnight = (now - cachedOfDay).coerceAtLeast(0L)

            val detailedUsage = withContext(Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm)
            }
            
            val liveAppUsage = detailedUsage.appUsageMap[packageName] ?: 0L

            value = Triple(
                shield, 
                liveAppUsage.coerceAtMost(timeSinceMidnight), 
                totalGlobalUsageToday.coerceAtMost(timeSinceMidnight)
            )
            delay(30000)
        }
    }

    val currentShield = combinedStateState.value.first
    val currentTotalUsageToday = combinedStateState.value.second
    val currentTotalGlobalUsageToday = combinedStateState.value.third

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
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
            Column(
                modifier = Modifier
                    .let { 
                        if (isLandscape) it.widthIn(max = 640.dp).wrapContentHeight() 
                        else it.fillMaxWidth().wrapContentHeight() 
                    }
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BedtimeAlertPill(
                    userPreferences = userPrefs,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                if (isLandscape) {
                    LandscapeGoalLayout(
                        modifier = Modifier.displayCutoutPadding(),
                        appName = appName,
                        appIcon = appIconBitmap,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        onGoalDismiss = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnGoalDismiss()
                            }
                        }
                    )
                } else {
                    PortraitGoalLayout(
                        appName = appName,
                        appIcon = appIconBitmap,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        onGoalDismiss = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnGoalDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitGoalLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    onGoalDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 0.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OverlayDragHandleWithIndicators()

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Text(
            text = "You're working towards your usage goal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GoalSection(shield, totalUsageToday, totalGlobalUsageToday, userPrefs, onGoalDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeGoalLayout(
    modifier: Modifier = Modifier,
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    onGoalDismiss: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Goal Pursuit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "You're working towards your usage goal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                GoalProgressMini(shield, totalUsageToday, totalGlobalUsageToday, userPrefs)
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GoalLandscapeContent(onGoalDismiss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalSection(shield: ShieldEntity, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences, onGoalDismiss: () -> Unit) {
    val targetLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val progress = if (targetLimitMillis > 0) totalUsageToday.toFloat() / targetLimitMillis else 0f
    val remainingMillis = (targetLimitMillis - totalUsageToday).coerceAtLeast(0L)

    val estimateTime = remember(remainingMillis) {
        val finishTime = System.currentTimeMillis() + remainingMillis
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(finishTime))
    }

    val achievedMessages = remember {
        listOf(
            "Target reached! Keep the momentum going.",
            "Goal achieved! You're doing great.",
            "Mission accomplished! Stay focused.",
            "Limit reached! Time to level up.",
            "You made it! Keep up the good work.",
            "Goal unlocked! Stay productive.",
            "Outstanding! You've met your target."
        )
    }
    val randomAchievedMessage = remember { achievedMessages.random() }

    Spacer(modifier = Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (progress >= 1f) "Goal Achieved" else "${(progress * 100).toInt()}% Done",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            TotalUsagePill(totalGlobalUsageToday, userPrefs)
            Text(
                text = "Target: ${shield.timeLimitMinutes}m",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        val animatedProgressState = animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
            label = "goalProgress"
        )
        LinearWavyProgressIndicator(
            progress = { animatedProgressState.value },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(10.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            wavelength = 40.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress >= 1f) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = randomAchievedMessage,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estimated finish",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = estimateTime,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }


    Spacer(modifier = Modifier.height(24.dp))

    ZenithButton(
        onClick = onGoalDismiss,
        text = "Got it, let's continue",
        icon = Icons.Outlined.CheckCircle,
        fillMaxWidth = true
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalProgressMini(shield: ShieldEntity, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences) {
    val targetLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val progress = if (targetLimitMillis > 0) totalUsageToday.toFloat() / targetLimitMillis else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (progress >= 1f) "Achieved" else "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            TotalUsagePill(totalGlobalUsageToday, userPrefs)
            Text(
                text = "${shield.timeLimitMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(10.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            wavelength = 40.dp
        )
    }
}

@Composable
fun GoalLandscapeContent(onGoalDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Keep it up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        ZenithButton(
            onClick = onGoalDismiss,
            text = "Continue",
            modifier = Modifier.fillMaxWidth(0.8f)
        )
    }
}
