package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
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
import com.etrisad.zenith.util.DateTimeUtils
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
        com.etrisad.zenith.util.ScreenUsageHelper.clearCache()
        val dayStartHour = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartHour
        val dayStartMinute = com.etrisad.zenith.service.SharedMonitoringState.cachedDayStartMinute
        var lastOfDay = 0L
        var cachedOfDay = 0L
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastOfDay > 60000) {
                cachedOfDay = DateTimeUtils.getDayStartTime(now, dayStartHour, dayStartMinute)
                lastOfDay = now
            }
            val timeSinceMidnight = (now - cachedOfDay).coerceAtLeast(0L)

            val detailedUsage = withContext(Dispatchers.IO) {
                com.etrisad.zenith.util.ScreenUsageHelper.fetchDetailedUsageToday(usm, dayStartHour = dayStartHour, dayStartMinute = dayStartMinute)
            }
            
            val liveAppUsage = detailedUsage.appUsageMap[packageName] ?: 0L
            val limitMillis = shield.timeLimitMinutes * 60 * 1000L
            val isAchieved = com.etrisad.zenith.service.SharedMonitoringState.notifiedGoals.contains(packageName)
            val usage = if (isAchieved && liveAppUsage < limitMillis) limitMillis else liveAppUsage

            value = Triple(
                shield, 
                usage.coerceAtMost(timeSinceMidnight), 
                detailedUsage.totalGlobalUsage.coerceAtMost(timeSinceMidnight)
            )
            delay(30000)
        }
    }

    val currentShield = combinedStateState.value.first
    val currentTotalUsageToday = combinedStateState.value.second
    val currentTotalGlobalUsageToday = combinedStateState.value.third

    InterceptBottomSheet(
        visible = showContent,
        backgroundAlpha = backgroundAlpha,
        isLandscape = isLandscape,
        showBedtimePill = true,
        userPreferences = userPrefs
    ) { _ ->
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
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .then(
                if (userPrefs.overlayFullScreen) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth().wrapContentHeight()
            )
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            modifier = if (userPrefs.overlayFullScreen) Modifier.weight(1f) else Modifier.wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

                Text(
                    text = "You're working towards your usage goal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            GoalProgressSection(shield, totalUsageToday, totalGlobalUsageToday, userPrefs)
        }
        ZenithButton(
            onClick = onGoalDismiss,
            text = "Got it, let's continue",
            icon = Icons.Outlined.CheckCircle,
            fillMaxWidth = true
        )
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
        modifier = modifier.then(
            if (userPrefs.overlayFullScreen) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().wrapContentHeight()
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (userPrefs.overlayFullScreen) Arrangement.Center else Arrangement.Top
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 24.dp, vertical = 12.dp),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalProgressSection(shield: ShieldEntity, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences) {
    val targetLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val progress = if (targetLimitMillis > 0) totalUsageToday.toFloat() / targetLimitMillis else 0f
    val remainingMillis = (targetLimitMillis - totalUsageToday).coerceAtLeast(0L)

    if (userPrefs.overlayFullScreen) {
        GoalFullScreenProgress(
            totalUsageToday = totalUsageToday,
            targetLimitMillis = targetLimitMillis,
            progress = progress,
            shield = shield
        )
    } else {
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
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalFullScreenProgress(
    totalUsageToday: Long,
    targetLimitMillis: Long,
    progress: Float,
    shield: ShieldEntity
) {
    val animatedProgress = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "goalFullScreenProgress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.size(220.dp),
                color = MaterialTheme.colorScheme.primary,
                amplitude = { 1f },
                wavelength = 58.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "used today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMillis(totalUsageToday),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "Target: ${shield.timeLimitMinutes}m",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoalProgressMini(shield: ShieldEntity, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences) {
    val targetLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val progress = if (targetLimitMillis > 0) totalUsageToday.toFloat() / targetLimitMillis else 0f

    if (userPrefs.overlayFullScreen) {
        GoalFullScreenProgressMini(
            totalUsageToday = totalUsageToday,
            progress = progress,
            shield = shield
        )
    } else {
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalFullScreenProgressMini(
    totalUsageToday: Long,
    progress: Float,
    shield: ShieldEntity
) {
    val animatedProgress = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "goalFullScreenProgressMini"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.size(140.dp),
                color = MaterialTheme.colorScheme.primary,
                amplitude = { 1f },
                wavelength = 36.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "used today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMillis(totalUsageToday),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Text(
            text = "Target: ${shield.timeLimitMinutes}m",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
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
