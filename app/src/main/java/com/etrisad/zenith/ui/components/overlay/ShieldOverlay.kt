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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldOverlay(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.etrisad.zenith.ZenithApplication
    val shieldRepository = app.shieldRepository

    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val allShields by shieldRepository.allShields.collectAsState(initial = emptyList())
    val currentShield = remember(allShields, packageName) {
        allShields.find { it.packageName == packageName } ?: shield
    }

    val databaseUsage by shieldRepository.getAllUsage().collectAsState(initial = emptyList())
    val todayDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    val dbAppUsage = remember(databaseUsage, packageName, todayDate) {
        databaseUsage.find { it.date == todayDate && it.packageName == packageName }?.usageTimeMillis ?: 0L
    }
    val dbGlobalUsage = remember(databaseUsage, todayDate) {
        databaseUsage.find { it.date == todayDate && it.packageName == "TOTAL" }?.usageTimeMillis ?: 0L
    }

    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }

    val currentTotalUsageToday = remember(totalUsageToday, dbAppUsage) { maxOf(totalUsageToday, dbAppUsage) }
    val currentTotalGlobalUsageToday = remember(totalGlobalUsageToday, dbGlobalUsage) { maxOf(totalGlobalUsageToday, dbGlobalUsage) }

    val isDelayEnabled = currentShield?.isDelayAppEnabled == true && currentShield.type == FocusType.SHIELD
    
    val initialProgress = remember(packageName, delayDurationSeconds) {
        if (isDelayEnabled && (currentShield?.lastDelayStartTimestamp ?: 0L) > 0 && delayDurationSeconds > 0) {
            val elapsed = System.currentTimeMillis() - currentShield.lastDelayStartTimestamp
            (elapsed.toFloat() / (delayDurationSeconds * 1000f)).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val delayProgressAnimatable = remember(packageName) { Animatable(initialProgress) }
    var isDelaying by remember(packageName) { 
        mutableStateOf(isDelayEnabled && (shield?.lastDelayStartTimestamp ?: 0L) != 0L && initialProgress < 1f) 
    }

    val motivationalMessages = remember {
        listOf(
            "Time for a quick stretch!",
            "Have you had enough water today?",
            "Take 3 deep breaths...",
            "Ready to crush your goals?",
            "Productivity is a marathon, not a sprint.",
            "Check your to-do list for a quick win!",
            "A small step today is a big leap tomorrow.",
            "Stay focused, stay mindful.",
            "Remember your homework or tasks!",
            "Do one small productive thing now.",
            "Do 15 Pushup"
        )
    }
    val randomMessage = remember(isDelaying) {
        if (isDelaying) motivationalMessages.random() else ""
    }
    
    val scope = rememberCoroutineScope()
    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    val userPrefs by userPrefsRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())

    val backgroundAlphaState = animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    LaunchedEffect(isDelaying) {
        if (isDelaying && delayDurationSeconds > 0) {
            val remainingProgress = 1f - delayProgressAnimatable.value
            val remainingDuration = (remainingProgress * delayDurationSeconds * 1000).toInt()
            
            if (remainingDuration > 0) {
                delayProgressAnimatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = remainingDuration,
                        easing = LinearEasing
                    )
                )
            }
            isDelaying = false
        } else {
            isDelaying = false
        }
    }

    val isPeriodExpired = remember(packageName, currentShield?.lastPeriodResetTimestamp) {
        currentShield != null && (System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L)
    }
    
    val currentUses = remember(packageName, currentShield?.currentPeriodUses, isPeriodExpired) {
        if (isPeriodExpired) 0 else (currentShield?.currentPeriodUses ?: 0)
    }
    val maxUses = currentShield?.maxUsesPerPeriod ?: 5
    val isUsesExceeded = remember(currentUses, maxUses) { currentUses >= maxUses }
    val isTimeLimitReached = remember(packageName, currentTotalUsageToday, currentShield?.timeLimitMinutes) {
        currentShield != null && currentShield.timeLimitMinutes > 0 && currentTotalUsageToday >= (currentShield.timeLimitMinutes * 60 * 1000L)
    }

    val remainingMinutes = remember(packageName, currentTotalUsageToday, currentShield?.timeLimitMinutes) {
        currentShield?.let {
            if (it.timeLimitMinutes <= 0) return@let null
            val limitMillis = it.timeLimitMinutes * 60 * 1000L
            ((limitMillis - currentTotalUsageToday) / (60 * 1000L)).toInt().coerceAtLeast(0)
        }
    }

    val isBlocked = remember(isUsesExceeded, isTimeLimitReached, remainingMinutes, isEmergencyUnlocked, currentShield?.type) {
        val effectivelyTimeReached = isTimeLimitReached || (remainingMinutes != null && remainingMinutes <= 0)
        (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked && currentShield?.type == FocusType.SHIELD
    }

    val autoKickProgress = remember(packageName) { Animatable(0f) }
    var isEmergencyHolding by remember(packageName) { mutableStateOf(false) }

    LaunchedEffect(packageName, isBlocked, isDelaying, isEmergencyHolding, isEmergencyUnlocked) {
        if (isBlocked) {
            if (!isEmergencyHolding) {
                autoKickProgress.snapTo(0f)
                autoKickProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
                )
                if (showContent) {
                    showContent = false
                    delay(300)
                }
                onCloseApp()
            } else {
                autoKickProgress.stop()
                autoKickProgress.snapTo(0f)
            }
        } else if (!isDelaying && !isEmergencyUnlocked && (currentShield?.type == FocusType.SHIELD || currentShield == null)) {
            autoKickProgress.snapTo(0f)
            delay(8000)
            
            if (isDelaying || isEmergencyUnlocked) return@LaunchedEffect

            autoKickProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
            )
            
            if (showContent) {
                showContent = false
                delay(300)
            }
            onCloseApp()
        } else {
            autoKickProgress.snapTo(0f)
        }
    }
    
    val refreshTimeLeftMillis = if (currentShield != null) {
        val nextRefresh = currentShield.lastPeriodResetTimestamp + (currentShield.refreshPeriodMinutes * 60 * 1000L)
        (nextRefresh - System.currentTimeMillis()).coerceAtLeast(0L)
    } else 0L

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlphaState.value }
                .background(Color.Black)
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
            Card(
                modifier = Modifier
                    .let { 
                        if (isLandscape) it.widthIn(max = 640.dp).wrapContentHeight() 
                        else it.fillMaxWidth().wrapContentHeight() 
                    }
                    .align(Alignment.BottomCenter)
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                if (isLandscape) {
                    LandscapeInterceptLayout(
                        appName = appName,
                        appIcon = appIcon,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        remainingMinutes = remainingMinutes,
                        isEmergencyUnlocked = isEmergencyUnlocked,
                        isDelaying = isDelaying,
                        randomMessage = randomMessage,
                        delayProgressAnimatable = delayProgressAnimatable,
                        delayDurationSeconds = delayDurationSeconds,
                        isUsesExceeded = isUsesExceeded,
                        isTimeLimitReached = isTimeLimitReached,
                        refreshTimeLeftMillis = refreshTimeLeftMillis,
                        currentUses = currentUses,
                        maxUses = maxUses,
                        autoKickProgress = { autoKickProgress.value },
                        onEmergencyHoldingChange = { isEmergencyHolding = it },
                        onEmergencyClick = { isEmergencyUnlocked = true },
                        onAllowUse = { minutes ->
                            scope.launch {
                                showContent = false
                                delay(400)
                                onAllowUse(minutes, isEmergencyUnlocked)
                            }
                        },
                        onCloseApp = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onCloseApp()
                            }
                        },
                        onGoalDismiss = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onGoalDismiss()
                            }
                        }
                    )
                } else {
                    PortraitInterceptLayout(
                        appName = appName,
                        appIcon = appIcon,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        remainingMinutes = remainingMinutes,
                        isEmergencyUnlocked = isEmergencyUnlocked,
                        isDelaying = isDelaying,
                        randomMessage = randomMessage,
                        delayProgressAnimatable = delayProgressAnimatable,
                        delayDurationSeconds = delayDurationSeconds,
                        isUsesExceeded = isUsesExceeded,
                        isTimeLimitReached = isTimeLimitReached,
                        refreshTimeLeftMillis = refreshTimeLeftMillis,
                        currentUses = currentUses,
                        maxUses = maxUses,
                        autoKickProgress = { autoKickProgress.value },
                        onEmergencyHoldingChange = { isEmergencyHolding = it },
                        onEmergencyClick = { isEmergencyUnlocked = true },
                        onAllowUse = { minutes ->
                            scope.launch {
                                showContent = false
                                delay(400)
                                onAllowUse(minutes, isEmergencyUnlocked)
                            }
                        },
                        onCloseApp = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onCloseApp()
                            }
                        },
                        onGoalDismiss = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onGoalDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PortraitInterceptLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (shield != null && shield.type == FocusType.SHIELD) {
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
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

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
                        if (shield?.type == FocusType.GOAL) Icons.Outlined.Flag else Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (shield?.type == FocusType.GOAL) "Goal Pursuit" else "Mindful Pause",
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
                text = if (shield?.type == FocusType.GOAL)
                    "You're working towards your usage goal."
                    else "Zenith Shield is active for this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (shield != null && shield.type == FocusType.GOAL) {
                GoalSection(shield, totalUsageToday, totalGlobalUsageToday, userPrefs, onGoalDismiss)
            } else {
                ShieldSection(
                    shield = shield,
                    remainingMinutes = remainingMinutes,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    userPrefs = userPrefs,
                    isEmergencyUnlocked = isEmergencyUnlocked,
                    isDelaying = isDelaying,
                    randomMessage = randomMessage,
                    delayProgressAnimatable = delayProgressAnimatable,
                    delayDurationSeconds = delayDurationSeconds,
                    isUsesExceeded = isUsesExceeded,
                    isTimeLimitReached = isTimeLimitReached,
                    refreshTimeLeftMillis = refreshTimeLeftMillis,
                    autoKickProgress = autoKickProgress,
                    onEmergencyClick = onEmergencyClick,
                    onEmergencyHoldingChange = onEmergencyHoldingChange,
                    onAllowUse = onAllowUse,
                    onCloseApp = onCloseApp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeInterceptLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (shield != null && shield.type == FocusType.SHIELD) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "$currentUses/$maxUses uses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Emergency: ${shield.emergencyUseCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

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
                    text = if (shield?.type == FocusType.GOAL) "Goal Pursuit" else "Mindful Pause",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (shield?.type == FocusType.GOAL)
                        "You're working towards your usage goal."
                    else "Zenith Shield is active for this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (shield != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (shield.type == FocusType.GOAL) {
                        GoalProgressMini(shield, totalUsageToday, totalGlobalUsageToday, userPrefs)
                    } else {
                        ShieldProgressMini(shield, totalUsageToday, totalGlobalUsageToday, userPrefs)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (shield != null && shield.type == FocusType.GOAL) {
                    GoalLandscapeContent(onGoalDismiss)
                } else {
                    ShieldLandscapeContent(
                        shield = shield,
                        remainingMinutes = remainingMinutes,
                        totalUsageToday = totalUsageToday,
                        isEmergencyUnlocked = isEmergencyUnlocked,
                        isDelaying = isDelaying,
                        randomMessage = randomMessage,
                        delayProgressAnimatable = delayProgressAnimatable,
                        delayDurationSeconds = delayDurationSeconds,
                        isUsesExceeded = isUsesExceeded,
                        isTimeLimitReached = isTimeLimitReached,
                        refreshTimeLeftMillis = refreshTimeLeftMillis,
                        autoKickProgress = autoKickProgress,
                        onEmergencyClick = onEmergencyClick,
                        onEmergencyHoldingChange = onEmergencyHoldingChange,
                        onAllowUse = onAllowUse,
                        onCloseApp = onCloseApp
                    )
                }
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

    val motivationText = remember(progress) {
        when {
            progress < 0.3f -> "Great start! Keep going."
            progress < 0.6f -> "You're halfway there! Stay focused."
            progress < 0.9f -> "Almost finished! You can do it."
            else -> "Just a little more to go!"
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${(progress * 100).toInt()}% Done",
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Estimated finish: $estimateTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = motivationText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onGoalDismiss,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Got it, let's continue", fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldSection(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val currentRemainingMinutes = remember(shield, totalUsageToday, remainingMinutes) {
        shield?.let {
            val totalLimitMillis = it.timeLimitMinutes * 60 * 1000L
            val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
            (remainingMillis / 60000).toInt()
        } ?: remainingMinutes
    }

    val effectivelyTimeReached = isTimeLimitReached || (currentRemainingMinutes != null && currentRemainingMinutes <= 0)
    val isBlocked = (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked

    if (shield != null) {
        Spacer(modifier = Modifier.height(16.dp))
        val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
        val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
        val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatMillis(totalUsageToday),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                TotalUsagePill(totalGlobalUsageToday, userPrefs)
                Text(
                    text = "${formatMillis(remainingMillis)} left today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
            val animatedProgressState = animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                label = "progress"
            )
            LinearWavyProgressIndicator(
                progress = { animatedProgressState.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(10.dp),
                color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                wavelength = 40.dp
            )
        }
    }

    if (isBlocked) {
        LimitReachedSection(
            isUsesExceeded = isUsesExceeded,
            isTimeLimitReached = effectivelyTimeReached,
            refreshTimeLeftMillis = refreshTimeLeftMillis,
            shield = shield,
            onEmergencyClick = onEmergencyClick,
            onEmergencyHoldingChange = onEmergencyHoldingChange
        )
    } else {
        val minutesToDisplay = if (isEmergencyUnlocked) null else currentRemainingMinutes
        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = isDelaying,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                 scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
            },
            label = "delayContent"
        ) { delaying ->
            if (delaying) {
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds)
            } else {
                DurationSelectionSection(minutesToDisplay, isEmergencyUnlocked, onAllowUse)
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    CloseAppTextButton(onCloseApp, autoKickProgress)
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
                text = "${(progress * 100).toInt()}%",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldProgressMini(shield: ShieldEntity, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences) {
    val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
    val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
    val progress = if (totalLimitMillis > 0) remainingMillis.toFloat() / totalLimitMillis else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatMillis(totalUsageToday),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            TotalUsagePill(totalGlobalUsageToday, userPrefs)
            Text(
                text = "${formatMillis(remainingMillis)} left",
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
            color = if (progress < 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
        Button(
            onClick = onGoalDismiss,
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Continue", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ShieldLandscapeContent(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long = 0,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val displayRemainingMinutes = if (shield != null) {
        val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
        ((totalLimitMillis - totalUsageToday).coerceAtLeast(0L) / 60000).toInt()
    } else remainingMinutes

    if ((isUsesExceeded || isTimeLimitReached) && !isEmergencyUnlocked) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            LimitReachedContent(isUsesExceeded, isTimeLimitReached, refreshTimeLeftMillis)
            if (shield != null && shield.emergencyUseCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
            }
            Spacer(modifier = Modifier.weight(1f))
            CloseAppTextButton(onCloseApp, autoKickProgress)
        }
    } else {
        if (isDelaying) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds)
                Spacer(modifier = Modifier.weight(1f))
                CloseAppTextButton(onCloseApp, autoKickProgress)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (isEmergencyUnlocked) "Emergency Use: Select Duration" else "How long do you want to use it?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                DurationButtonsGrid(if (isEmergencyUnlocked) null else displayRemainingMinutes, onAllowUse)
                Spacer(modifier = Modifier.weight(1f))
                CloseAppTextButton(onCloseApp, autoKickProgress)
            }
        }
    }
}

@Composable
fun LimitReachedSection(
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    shield: ShieldEntity?,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {}
) {
    Spacer(modifier = Modifier.height(24.dp))
    LimitReachedContent(isUsesExceeded, isTimeLimitReached, refreshTimeLeftMillis)
    if (shield != null && shield.emergencyUseCount > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
    }
}

@Composable
fun LimitReachedContent(
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long
) {
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
            text = "Uses limit reached.\nRefresh in $countdownText",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = "Daily limit reached.\nCome back tomorrow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DelayInProgressSection(
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = randomMessage,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { delayProgressAnimatable.value },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.tertiary,
                amplitude = { 1f },
                wavelength = 30.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            val secondsLeft by remember(delayDurationSeconds) {
                derivedStateOf { kotlin.math.ceil((1f - delayProgressAnimatable.value) * delayDurationSeconds).toInt() }
            }
            Text(
                text = "${secondsLeft}s",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun DurationSelectionSection(remainingMinutes: Int?, isEmergencyUnlocked: Boolean, onAllowUse: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isEmergencyUnlocked) "Emergency Use: Select Duration" else "How long do you want to use it?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        DurationButtonsGrid(if (isEmergencyUnlocked) null else remainingMinutes, onAllowUse)
    }
}
