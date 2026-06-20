package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.CalendarEventProvider
import com.etrisad.zenith.data.CurrentCalendarEvent
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()

    val combinedStateState = produceState(
        initialValue = Triple(shield, totalUsageToday, totalGlobalUsageToday),
        packageName,
        shield
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
            
            val s = shieldRepository.getShieldByPackageNameFlow(packageName).first()
            val liveAppUsage = detailedUsage.appUsageMap[packageName] ?: 0L

            value = Triple(
                s ?: shield, 
                liveAppUsage.coerceAtMost(timeSinceMidnight), 
                detailedUsage.totalGlobalUsage.coerceAtMost(timeSinceMidnight)
            )
            delay(30000)
        }
    }

    val currentShield = combinedStateState.value.first
    val currentTotalUsageToday = combinedStateState.value.second
    val currentTotalGlobalUsageToday = combinedStateState.value.third
    
    var showContent by remember { mutableStateOf(false) }
    var isEmergencyUnlocked by remember { mutableStateOf(false) }

    val userPrefsRepo = remember(context.applicationContext) { UserPreferencesRepository(context.applicationContext) }
    val userPrefs by produceState(initialValue = UserPreferences()) {
        userPrefsRepo.userPreferencesFlow.collect { value = it }
    }

    val incentiveProgress by produceState(initialValue = 0f) {
        shieldRepository.getIncentiveGoalProgress().collect { value = it }
    }
    val isIncentiveLocked = userPrefs.incentiveLockEnabled && !userPrefs.incentiveLockGoalsMetToday && (currentShield?.type == FocusType.SHIELD || currentShield == null)

    val isDelayEnabled = currentShield != null && currentShield.isDelayAppEnabled && currentShield.type == FocusType.SHIELD
    
    val initialProgress = remember(packageName, delayDurationSeconds) {
        if (isDelayEnabled && currentShield != null && currentShield.lastDelayStartTimestamp > 0 && delayDurationSeconds > 0) {
            val elapsed = System.currentTimeMillis() - currentShield.lastDelayStartTimestamp
            (elapsed.toFloat() / (delayDurationSeconds * 1000f)).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    val delayProgressAnimatable = remember(packageName) { Animatable(initialProgress) }
    var isDelaying by remember(packageName) { 
        mutableStateOf(isDelayEnabled && currentShield != null && currentShield.lastDelayStartTimestamp != 0L && initialProgress < 1f)
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

    var currentEvent by remember { mutableStateOf<CurrentCalendarEvent?>(null) }

    LaunchedEffect(isDelaying, userPrefs.showCurrentEvent) {
        if (isDelaying && userPrefs.showCurrentEvent) {
            withContext(Dispatchers.IO) {
                currentEvent = CalendarEventProvider.fetchCurrentEvent(context)
            }
        }
    }

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    LaunchedEffect(isDelaying) {
        if (isDelaying && delayDurationSeconds > 0) {
            val totalDuration = delayDurationSeconds * 1000L
            val startTime = System.currentTimeMillis() - (delayProgressAnimatable.value * totalDuration).toLong()
            
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / totalDuration).coerceIn(0f, 1f)
                delayProgressAnimatable.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            isDelaying = false
        } else {
            isDelaying = false
        }
    }

    val isPeriodExpired = remember(currentShield) {
        currentShield != null && (System.currentTimeMillis() - currentShield.lastPeriodResetTimestamp > currentShield.refreshPeriodMinutes * 60 * 1000L)
    }
    
    val currentUses = remember(currentShield, isPeriodExpired) {
        if (isPeriodExpired) 0 else (currentShield?.currentPeriodUses ?: 0)
    }
    val maxUses = currentShield?.maxUsesPerPeriod ?: 5
    val isUsesExceeded = remember(currentUses, maxUses, isIncentiveLocked) { isIncentiveLocked || currentUses >= maxUses }
    val isTimeLimitReached = remember(currentTotalUsageToday, currentShield) {
        currentShield != null && currentShield.timeLimitMinutes > 0 && currentTotalUsageToday >= (currentShield.timeLimitMinutes * 60 * 1000L)
    }

    val remainingMinutes = remember(currentTotalUsageToday, currentShield) {
        currentShield?.let {
            if (it.timeLimitMinutes <= 0) return@let null
            val limitMillis = it.timeLimitMinutes * 60 * 1000L
            ((limitMillis - currentTotalUsageToday) / (60 * 1000L)).toInt().coerceAtLeast(0)
        }
    }

    val isBlocked by remember(isUsesExceeded, isTimeLimitReached, remainingMinutes, isEmergencyUnlocked, currentShield?.type, isIncentiveLocked) {
        derivedStateOf {
            val effectivelyTimeReached = isTimeLimitReached || (remainingMinutes != null && remainingMinutes <= 0)
            val baseBlocked = (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked
            baseBlocked && (currentShield?.type == FocusType.SHIELD || (isIncentiveLocked && currentShield == null))
        }
    }

    val autoKickProgress = remember(packageName) { Animatable(0f) }
    var isEmergencyHolding by remember(packageName) { mutableStateOf(false) }

    data class ShieldOverlayState(
        val isBlocked: Boolean,
        val isEmergencyHolding: Boolean,
        val isDelaying: Boolean,
        val isEmergencyUnlocked: Boolean
    )

    LaunchedEffect(showContent) {
        if (!showContent) {
            autoKickProgress.stop()
            delayProgressAnimatable.stop()
        }
    }

    LaunchedEffect(packageName) {
        snapshotFlow { 
            ShieldOverlayState(isBlocked, isEmergencyHolding, isDelaying, isEmergencyUnlocked)
        }
            .collectLatest { state ->
                if (state.isBlocked) {
                    if (!state.isEmergencyHolding) {
                        autoKickProgress.snapTo(0f)
                        val startTime = System.currentTimeMillis()
                        while (true) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val p = (elapsed.toFloat() / 4000f).coerceIn(0f, 1f)
                            autoKickProgress.snapTo(p)
                            if (p >= 1f) break
                            delay(16)
                        }
                        if (showContent) {
                            showContent = false
                            delay(300)
                        }
                        onCloseApp()
                    } else {
                        autoKickProgress.stop()
                        autoKickProgress.snapTo(0f)
                    }
                } else if (!state.isDelaying && !state.isEmergencyUnlocked && (currentShield?.type == FocusType.SHIELD || currentShield == null)) {
                    autoKickProgress.snapTo(0f)
                    delay(8000)
                    
                    val startTime = System.currentTimeMillis()
                    while (true) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                        autoKickProgress.snapTo(p)
                        if (p >= 1f) break
                        delay(16)
                    }
                    
                    if (showContent) {
                        showContent = false
                        delay(300)
                    }
                    onCloseApp()
                } else {
                    autoKickProgress.stop()
                    autoKickProgress.snapTo(0f)
                }
            }
    }
    
    val refreshTimeLeftMillis = remember(currentShield) {
        if (currentShield != null) {
            val nextRefresh = currentShield.lastPeriodResetTimestamp + (currentShield.refreshPeriodMinutes * 60 * 1000L)
            (nextRefresh - System.currentTimeMillis()).coerceAtLeast(0L)
        } else 0L
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentOnAllowUse by rememberUpdatedState(onAllowUse)
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)
    val currentOnGoalDismiss by rememberUpdatedState(onGoalDismiss)

    if (currentShield?.type == FocusType.GOAL) {
        GoalOverlay(
            packageName = packageName,
            appName = appName,
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
        return
    }

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
                    LandscapeInterceptLayout(
                        modifier = Modifier.displayCutoutPadding(),
                        appName = appName,
                        packageName = packageName,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        remainingMinutes = remainingMinutes,
                        isEmergencyUnlocked = isEmergencyUnlocked,
                        isDelaying = isDelaying,
                        randomMessage = randomMessage,
                        currentEvent = currentEvent,
                        delayProgressAnimatable = delayProgressAnimatable,
                        delayDurationSeconds = delayDurationSeconds,
                        isUsesExceeded = isUsesExceeded,
                        isTimeLimitReached = isTimeLimitReached,
                        refreshTimeLeftMillis = refreshTimeLeftMillis,
                        currentUses = currentUses,
                        maxUses = maxUses,
                        isIncentiveLocked = isIncentiveLocked,
                        incentiveProgress = incentiveProgress,
                        autoKickProgress = { autoKickProgress.value },
                        onEmergencyHoldingChange = { isEmergencyHolding = it },
                        onEmergencyClick = { isEmergencyUnlocked = true },
                        onAllowUse = { minutes ->
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnAllowUse(minutes, isEmergencyUnlocked)
                            }
                        },
                        onCloseApp = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnCloseApp()
                            }
                        }
                    )
                } else {
                    PortraitInterceptLayout(
                        appName = appName,
                        packageName = packageName,
                        shield = currentShield,
                        totalUsageToday = currentTotalUsageToday,
                        totalGlobalUsageToday = currentTotalGlobalUsageToday,
                        userPrefs = userPrefs,
                        remainingMinutes = remainingMinutes,
                        isEmergencyUnlocked = isEmergencyUnlocked,
                        isDelaying = isDelaying,
                        randomMessage = randomMessage,
                        currentEvent = currentEvent,
                        delayProgressAnimatable = delayProgressAnimatable,
                        delayDurationSeconds = delayDurationSeconds,
                        isUsesExceeded = isUsesExceeded,
                        isTimeLimitReached = isTimeLimitReached,
                        refreshTimeLeftMillis = refreshTimeLeftMillis,
                        currentUses = currentUses,
                        maxUses = maxUses,
                        isIncentiveLocked = isIncentiveLocked,
                        incentiveProgress = incentiveProgress,
                        autoKickProgress = { autoKickProgress.value },
                        onEmergencyHoldingChange = { isEmergencyHolding = it },
                        onEmergencyClick = { isEmergencyUnlocked = true },
                        onAllowUse = { minutes ->
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnAllowUse(minutes, isEmergencyUnlocked)
                            }
                        },
                        onCloseApp = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                currentOnCloseApp()
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
fun PortraitInterceptLayout(
    appName: String,
    packageName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    currentEvent: CurrentCalendarEvent?,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OverlayDragHandleWithIndicators(
            currentUses = if (shield?.type == FocusType.SHIELD) currentUses else null,
            maxUses = if (shield?.type == FocusType.SHIELD) maxUses else null,
            emergencyCount = if (shield?.type == FocusType.SHIELD) shield.emergencyUseCount else null,
            isIncentiveLocked = isIncentiveLocked
        )

        Column(
            modifier = Modifier
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp, top = 24.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("app-icon://$packageName")
                        .crossfade(500)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            Icons.Outlined.Block,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
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
                text = if (shield != null) "Zenith Shield is active for this app." else "Mindful Gateway is guarding your focus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ShieldSection(
                shield = shield,
                remainingMinutes = remainingMinutes,
                totalUsageToday = totalUsageToday,
                totalGlobalUsageToday = totalGlobalUsageToday,
                userPrefs = userPrefs,
                isEmergencyUnlocked = isEmergencyUnlocked,
                isDelaying = isDelaying,
                randomMessage = randomMessage,
                currentEvent = currentEvent,
                delayProgressAnimatable = delayProgressAnimatable,
                delayDurationSeconds = delayDurationSeconds,
                isUsesExceeded = isUsesExceeded,
                isTimeLimitReached = isTimeLimitReached,
                refreshTimeLeftMillis = refreshTimeLeftMillis,
                isIncentiveLocked = isIncentiveLocked,
                incentiveProgress = incentiveProgress,
                autoKickProgress = autoKickProgress,
                onEmergencyClick = onEmergencyClick,
                onEmergencyHoldingChange = onEmergencyHoldingChange,
                onAllowUse = onAllowUse,
                onCloseApp = onCloseApp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapeInterceptLayout(
    modifier: Modifier = Modifier,
    appName: String,
    packageName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    remainingMinutes: Int?,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    currentEvent: CurrentCalendarEvent?,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    currentUses: Int,
    maxUses: Int,
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
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
                        ZenithButton(
                            onClick = { },
                            text = "$currentUses/$maxUses",
                            icon = Icons.Outlined.Timer,
                            type = ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            isDisableWeight = true,
                            isDisableExpand = true,
                            backgroundProgressProvider = { ((maxUses - currentUses).toFloat() / maxUses.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.widthIn(max = 110.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        ZenithButton(
                            onClick = { },
                            text = "${shield.emergencyUseCount}",
                            icon = Icons.Outlined.Bolt,
                            type = ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            isDisableWeight = true,
                            isDisableExpand = true,
                            backgroundProgressProvider = { (shield.emergencyUseCount.toFloat() / 3f).coerceIn(0f, 1f) },
                            modifier = Modifier.widthIn(max = 110.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.error
                        )
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("app-icon://$packageName")
                            .crossfade(500)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
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
                    text = "Mindful Pause",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (shield != null) "Zenith Shield is active for this app." else "Mindful Gateway is guarding your focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))
                ShieldProgressMini(shield, totalUsageToday, totalGlobalUsageToday, userPrefs)
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShieldLandscapeContent(
                    shield = shield,
                    remainingMinutes = remainingMinutes,
                    totalUsageToday = totalUsageToday,
                    isEmergencyUnlocked = isEmergencyUnlocked,
                    isDelaying = isDelaying,
                    randomMessage = randomMessage,
                    currentEvent = currentEvent,
                    delayProgressAnimatable = delayProgressAnimatable,
                    delayDurationSeconds = delayDurationSeconds,
                    isUsesExceeded = isUsesExceeded,
                    isTimeLimitReached = isTimeLimitReached,
                    refreshTimeLeftMillis = refreshTimeLeftMillis,
                    isIncentiveLocked = isIncentiveLocked,
                    incentiveProgress = incentiveProgress,
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
fun ShieldSection(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    userPrefs: UserPreferences,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    currentEvent: CurrentCalendarEvent?,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val currentRemainingMinutes = remember(shield, totalUsageToday, remainingMinutes) {
        shield?.let {
            if (it.timeLimitMinutes <= 0) return@let null
            val totalLimitMillis = it.timeLimitMinutes * 60 * 1000L
            val remainingMillis = (totalLimitMillis - totalUsageToday).coerceAtLeast(0L)
            (remainingMillis / 60000).toInt()
        } ?: remainingMinutes
    }

    val effectivelyTimeReached = isTimeLimitReached || (currentRemainingMinutes != null && currentRemainingMinutes <= 0)
    val isBlocked = (isUsesExceeded || effectivelyTimeReached) && !isEmergencyUnlocked

    Spacer(modifier = Modifier.height(16.dp))
    val totalLimitMillis = shield?.let { it.timeLimitMinutes * 60 * 1000L } ?: 0L
    val remainingMillis = if (totalLimitMillis > 0) (totalLimitMillis - totalUsageToday).coerceAtLeast(0L) else 0L
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
            if (totalLimitMillis > 0) {
                Text(
                    text = "${formatMillis(remainingMillis)} left today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        
        if (totalLimitMillis > 0) {
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
            isIncentiveLocked = isIncentiveLocked,
            incentiveProgress = incentiveProgress,
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
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds, currentEvent)
            } else {
                DurationSelectionSection(minutesToDisplay, isEmergencyUnlocked, onAllowUse)
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.ExtraLarge)
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShieldProgressMini(shield: ShieldEntity?, totalUsageToday: Long, totalGlobalUsageToday: Long, userPrefs: UserPreferences) {
    val totalLimitMillis = shield?.let { it.timeLimitMinutes * 60 * 1000L } ?: 0L
    val remainingMillis = if (totalLimitMillis > 0) (totalLimitMillis - totalUsageToday).coerceAtLeast(0L) else 0L
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
            if (totalLimitMillis > 0) {
                Text(
                    text = "${formatMillis(remainingMillis)} left",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        if (totalLimitMillis > 0) {
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
}


@Composable
fun ShieldLandscapeContent(
    shield: ShieldEntity?,
    remainingMinutes: Int?,
    totalUsageToday: Long = 0,
    isEmergencyUnlocked: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    currentEvent: CurrentCalendarEvent?,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f,
    autoKickProgress: () -> Float,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {},
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val displayRemainingMinutes = if (shield != null && shield.timeLimitMinutes > 0) {
        val totalLimitMillis = shield.timeLimitMinutes * 60 * 1000L
        ((totalLimitMillis - totalUsageToday).coerceAtLeast(0L) / 60000).toInt()
    } else remainingMinutes

    if ((isUsesExceeded || isTimeLimitReached) && !isEmergencyUnlocked) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            LimitReachedContent(
                isUsesExceeded = isUsesExceeded,
                isTimeLimitReached = isTimeLimitReached,
                refreshTimeLeftMillis = refreshTimeLeftMillis,
                isIncentiveLocked = isIncentiveLocked,
                incentiveProgress = incentiveProgress
            )
            if (shield != null && shield.emergencyUseCount > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
            }
            Spacer(modifier = Modifier.weight(1f))
            CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
        }
    } else {
        if (isDelaying) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                DelayInProgressSection(randomMessage, delayProgressAnimatable, delayDurationSeconds, currentEvent)
                Spacer(modifier = Modifier.weight(1f))
                CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
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
                CloseAppTextButton(onCloseApp, autoKickProgress, size = ZenithButtonSize.Large)
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
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f,
    onEmergencyClick: () -> Unit,
    onEmergencyHoldingChange: (Boolean) -> Unit = {}
) {
    Spacer(modifier = Modifier.height(24.dp))
    LimitReachedContent(isUsesExceeded, isTimeLimitReached, refreshTimeLeftMillis, isIncentiveLocked, incentiveProgress)
    if (shield != null && shield.emergencyUseCount > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        EmergencyButton(onEmergencyUse = onEmergencyClick, onHoldingChange = onEmergencyHoldingChange)
    }
}

@Composable
fun LimitReachedContent(
    isUsesExceeded: Boolean,
    isTimeLimitReached: Boolean,
    refreshTimeLeftMillis: Long,
    isIncentiveLocked: Boolean = false,
    incentiveProgress: Float = 0f
) {
    if (isIncentiveLocked) {
        val percentage = (incentiveProgress * 100).toInt()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Incentive Lock Active",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Complete all your app goals to unlock!\nCurrently at $percentage% completion.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stay focused! You're making great progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    } else if (isUsesExceeded && !isTimeLimitReached) {
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
    delayDurationSeconds: Int,
    currentEvent: CurrentCalendarEvent? = null
) {
    val eventProgress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentEvent) {
        if (currentEvent != null) {
            while (true) {
                val now = System.currentTimeMillis()
                val duration = currentEvent.eventEndMillis - currentEvent.eventStartMillis
                eventProgress.floatValue = if (duration > 0) {
                    ((now - currentEvent.eventStartMillis).toFloat() / duration).coerceIn(0f, 1f)
                } else 0f
                delay(100)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentEvent != null) {
            val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val pillLarge = 24.dp
            val pillSmall = 4.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(
                        topStart = pillLarge, bottomStart = pillLarge,
                        topEnd = pillSmall, bottomEnd = pillSmall
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Surface(
                    color = pillColor,
                    shape = RoundedCornerShape(pillSmall),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        RollingText(
                            text = currentEvent.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!currentEvent.description.isNullOrBlank()) {
                            RollingText(
                                text = currentEvent.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = pillColor,
                    shape = RoundedCornerShape(
                        topStart = pillSmall, bottomStart = pillSmall,
                        topEnd = pillLarge, bottomEnd = pillLarge
                    )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { eventProgress.floatValue.coerceIn(0f, 1f) },
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            stroke = Stroke(width = 6.dp.value),
                            trackStroke = Stroke(width = 6.dp.value),
                            wavelength = 8.dp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Text(
                text = randomMessage,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { delayProgressAnimatable.value },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.tertiary,
                amplitude = { 1f },
                wavelength = 30.dp,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
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

@Composable
private fun RollingText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight? = null,
    color: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val textWidth = remember(text, style, fontWeight) {
        val mergedStyle = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = mergedStyle,
            maxLines = 1,
            softWrap = false
        ).size.width
    }
    var containerWidth by remember { mutableIntStateOf(0) }
    val needsScroll = textWidth > containerWidth
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(needsScroll, textWidth, containerWidth) {
        if (needsScroll && textWidth > 0 && containerWidth > 0) {
            val maxScroll = -(textWidth - containerWidth + 24f).coerceAtMost(0f)
            while (true) {
                offsetX.animateTo(
                    targetValue = maxScroll,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                delay(1500)
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                delay(1500)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer { clip = true }
            .onSizeChanged { containerWidth = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.graphicsLayer { translationX = offsetX.value }
        )
    }
}
