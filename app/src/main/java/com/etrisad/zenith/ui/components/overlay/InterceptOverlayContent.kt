package com.etrisad.zenith.ui.components.overlay

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.pausepoint.PausePointEngine
import com.etrisad.zenith.ui.components.pausepoint.PausePointTask
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskContent
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONTENT_A = "pausePoint"
private const val CONTENT_B = "actualContent"

@Composable
fun InterceptOverlayContent(
    packageName: String,
    appName: String,
    shield: ShieldEntity?,
    totalUsageToday: Long,
    totalGlobalUsageToday: Long,
    delayDurationSeconds: Int = 0,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onGoalDismiss: () -> Unit = {},
    onKeyboardFocusChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val app = context.applicationContext as com.etrisad.zenith.ZenithApplication
    val shieldRepository = app.shieldRepository
    val scope = rememberCoroutineScope()

    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    var prefsLoaded by remember { mutableStateOf(false) }
    val userPrefs by produceState(initialValue = UserPreferences()) {
        userPrefsRepo.userPreferencesFlow.collect {
            value = it
            prefsLoaded = true
        }
    }

    if (shield?.type == FocusType.GOAL) {
        ShieldOverlay(
            packageName = packageName,
            appName = appName,
            shield = shield,
            totalUsageToday = totalUsageToday,
            totalGlobalUsageToday = totalGlobalUsageToday,
            delayDurationSeconds = delayDurationSeconds,
            onAllowUse = onAllowUse,
            onCloseApp = onCloseApp,
            onGoalDismiss = onGoalDismiss
        )
        return
    }

    val pausePointEnabled = userPrefs.pausePointEnabled
    val enabledTypes = userPrefs.pausePointTaskTypes
    var currentPauseTask by remember(packageName) { mutableStateOf<PausePointTask?>(null) }
    var pauseTaskCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(pausePointEnabled, packageName) {
        if (pausePointEnabled && currentPauseTask == null) {
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            currentPauseTask = PausePointEngine.generateTask(
                enabledTypes = enabledTypes,
                goalPackageNames = goals.map { it.packageName }.toSet(),
                goalAppNames = goals.associate { it.packageName to it.appName },
                qrCodes = userPrefs.pausePointQrCodes
            )
        }
    }

    val showPausePoint = pausePointEnabled && !pauseTaskCompleted

    var showSheet by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showSheet) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showSheet = true
    }

    val currentOnAllowUse by rememberUpdatedState(onAllowUse)
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)

    val closeOverlay: () -> Unit = {
        scope.launch {
            showSheet = false
            delay(400)
            currentOnCloseApp()
        }
    }

    val dragUses = if (shield?.type == FocusType.SHIELD) shield.currentPeriodUses else null
    val dragMaxUses = if (shield?.type == FocusType.SHIELD) shield.maxUsesPerPeriod else null
    val dragEmergency = if (shield?.type == FocusType.SHIELD) shield.emergencyUseCount else null

    val incentiveProgress by produceState(initialValue = 0f) {
        shieldRepository.getIncentiveGoalProgress().collect { value = it }
    }
    val incentiveTier = remember(incentiveProgress) { IncentiveTier.fromProgress(incentiveProgress) }
    val isIncentiveActive = userPrefs.incentiveLockEnabled && !userPrefs.incentiveLockGoalsMetToday && shield?.type == FocusType.SHIELD
    var bonusUsesLeft by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var bonusConsumedThisSession by remember { mutableStateOf(false) }

    LaunchedEffect(isIncentiveActive, incentiveTier, userPrefs.incentiveBonusUsesUsed) {
        if (isIncentiveActive && incentiveTier.bonusUses < Int.MAX_VALUE) {
            bonusUsesLeft = shieldRepository.getIncentiveBonusUsesLeft()
        } else {
            bonusUsesLeft = Int.MAX_VALUE
        }
    }

    val onConsumeBonusUse: () -> Unit = {
        scope.launch {
            if (shieldRepository.consumeIncentiveBonusUse()) {
                bonusUsesLeft--
                bonusConsumedThisSession = true
            }
        }
    }

    InterceptBottomSheet(
        visible = showSheet,
        backgroundAlpha = backgroundAlpha,
        isLandscape = isLandscape,
        showBedtimePill = true,
        userPreferences = userPrefs,
        dragHandleCurrentUses = dragUses,
        dragHandleMaxUses = dragMaxUses,
        dragHandleEmergencyCount = dragEmergency,
        dragHandleIsIncentiveLocked = isIncentiveActive && !incentiveTier.isUnlocked,
        dragHandleIncentiveTier = if (isIncentiveActive) incentiveTier else null,
        dragHandleBonusUsesLeft = bonusUsesLeft,
        contentKey = when {
            !prefsLoaded -> null
            showPausePoint -> CONTENT_A
            else -> CONTENT_B
        },
        onCloseApp = closeOverlay
    ) { key ->
        when (key) {
            null -> {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp))
            }
            CONTENT_A -> {
                val task = currentPauseTask
                if (task != null) {
                    PausePointContent(
                        task = task,
                        onTaskCompleted = { pauseTaskCompleted = true },
                        onOpenApp = { goalPkg ->
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(goalPkg)
                                if (intent != null) {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            } catch (_: Exception) {}
                            closeOverlay()
                        },
                        onCloseApp = closeOverlay,
                        onKeyboardFocusChange = onKeyboardFocusChange
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp))
                }
            }
            else -> {
                ShieldOverlaySheetContent(
                    packageName = packageName,
                    appName = appName,
                    shield = shield,
                    totalUsageToday = totalUsageToday,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    delayDurationSeconds = delayDurationSeconds,
                    userPrefs = userPrefs,
                    isLandscape = isLandscape,
                    sheetVisible = showSheet,
                    incentiveProgress = incentiveProgress,
                    incentiveTier = if (isIncentiveActive) incentiveTier else null,
                    isIncentiveActive = isIncentiveActive,
                    bonusUsesLeft = bonusUsesLeft,
                    bonusConsumedThisSession = bonusConsumedThisSession,
                    onConsumeBonusUse = onConsumeBonusUse,
                    onAllowUse = { minutes, emergency ->
                        showSheet = false
                        currentOnAllowUse(minutes, emergency)
                    },
                    onCloseApp = closeOverlay
                )
            }
        }
    }
}

@Composable
fun ScheduleOverlayContent(
    packageName: String,
    appName: String,
    schedule: ScheduleEntity,
    totalGlobalUsageToday: Long,
    onAllowUse: (Int, Boolean) -> Unit,
    onCloseApp: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val app = context.applicationContext as com.etrisad.zenith.ZenithApplication
    val shieldRepository = app.shieldRepository
    val scope = rememberCoroutineScope()

    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    var prefsLoaded by remember { mutableStateOf(false) }
    val userPrefs by produceState(initialValue = UserPreferences()) {
        userPrefsRepo.userPreferencesFlow.collect {
            value = it
            prefsLoaded = true
        }
    }

    val pausePointEnabled = userPrefs.pausePointEnabled
    val enabledTypes = userPrefs.pausePointTaskTypes
    var currentPauseTask by remember(packageName) { mutableStateOf<PausePointTask?>(null) }
    var pauseTaskCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(pausePointEnabled, packageName) {
        if (pausePointEnabled && currentPauseTask == null) {
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            currentPauseTask = PausePointEngine.generateTask(
                enabledTypes = enabledTypes,
                goalPackageNames = goals.map { it.packageName }.toSet(),
                goalAppNames = goals.associate { it.packageName to it.appName },
                qrCodes = userPrefs.pausePointQrCodes
            )
        }
    }

    val showPausePoint = pausePointEnabled && !pauseTaskCompleted

    var showSheet by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showSheet) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showSheet = true
    }

    val currentOnAllowUse by rememberUpdatedState(onAllowUse)
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)

    val closeOverlay: () -> Unit = {
        scope.launch {
            showSheet = false
            delay(400)
            currentOnCloseApp()
        }
    }

    InterceptBottomSheet(
        visible = showSheet,
        backgroundAlpha = backgroundAlpha,
        isLandscape = isLandscape,
        showBedtimePill = true,
        userPreferences = userPrefs,
        dragHandleEmergencyCount = schedule.emergencyUseCount,
        contentKey = when {
            !prefsLoaded -> null
            showPausePoint -> CONTENT_A
            else -> CONTENT_B
        },
        onCloseApp = closeOverlay
    ) { key ->
        when (key) {
            null -> {
                Box(modifier = Modifier.fillMaxWidth().height(240.dp))
            }
            CONTENT_A -> {
                val task = currentPauseTask
                if (task != null) {
                    PausePointContent(
                        task = task,
                        onTaskCompleted = { pauseTaskCompleted = true },
                        onOpenApp = { goalPkg ->
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(goalPkg)
                                if (intent != null) {
                                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            } catch (_: Exception) {}
                            closeOverlay()
                        },
                        onCloseApp = closeOverlay,
                        onKeyboardFocusChange = onKeyboardFocusChange
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp))
                }
            }
            else -> {
                ScheduleOverlaySheetContent(
                    packageName = packageName,
                    appName = appName,
                    schedule = schedule,
                    totalGlobalUsageToday = totalGlobalUsageToday,
                    userPrefs = userPrefs,
                    isLandscape = isLandscape,
                    onAllowUse = { minutes, emergency ->
                        showSheet = false
                        currentOnAllowUse(minutes, emergency)
                    },
                    onCloseApp = closeOverlay
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PausePointContent(
    task: PausePointTask,
    onTaskCompleted: () -> Unit,
    onOpenApp: (String) -> Unit,
    onCloseApp: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        PausePointLandscapeContent(
            task = task,
            onTaskCompleted = onTaskCompleted,
            onOpenApp = onOpenApp,
            onCloseApp = onCloseApp,
            onKeyboardFocusChange = onKeyboardFocusChange
        )
        return
    }

    val autoKickProgress = remember { Animatable(0f) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val bumpActivity: () -> Unit = { interactionTick++ }
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)
    val enableAutoKick = task.type != PausePointTaskType.CHOOSE_APP &&
            task.type != PausePointTaskType.WAITING &&
            task.type != PausePointTaskType.BREATHING

    LaunchedEffect(interactionTick, enableAutoKick) {
        if (!enableAutoKick) {
            autoKickProgress.snapTo(0f)
            return@LaunchedEffect
        }
        autoKickProgress.snapTo(0f)
        delay(5000)
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
            autoKickProgress.snapTo(p)
            if (p >= 1f) break
            delay(16)
        }
        delay(300)
        currentOnCloseApp()
    }

    DisposableEffect(Unit) {
        onDispose { onKeyboardFocusChange(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) bumpActivity()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = task.type.icon,
                    contentDescription = task.type.displayName,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Pause Point",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = task.type.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = task.type.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PausePointTaskContent(
                task = task,
                onTaskCompleted = onTaskCompleted,
                onOpenApp = onOpenApp,
                onUserActivity = bumpActivity,
                onKeyboardFocusChange = onKeyboardFocusChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            CloseAppTextButton(
                onCloseApp = onCloseApp,
                autoKickProgress = { autoKickProgress.value },
                size = ZenithButtonSize.ExtraLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PausePointLandscapeContent(
    task: PausePointTask,
    onTaskCompleted: () -> Unit,
    onOpenApp: (String) -> Unit,
    onCloseApp: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit
) {
    val autoKickProgress = remember { Animatable(0f) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val bumpActivity: () -> Unit = { interactionTick++ }
    val currentOnCloseApp by rememberUpdatedState(onCloseApp)
    val enableAutoKick = task.type != PausePointTaskType.CHOOSE_APP &&
            task.type != PausePointTaskType.WAITING &&
            task.type != PausePointTaskType.BREATHING

    LaunchedEffect(interactionTick, enableAutoKick) {
        if (!enableAutoKick) {
            autoKickProgress.snapTo(0f)
            return@LaunchedEffect
        }
        autoKickProgress.snapTo(0f)
        delay(5000)
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
            autoKickProgress.snapTo(p)
            if (p >= 1f) break
            delay(16)
        }
        delay(300)
        currentOnCloseApp()
    }

    DisposableEffect(Unit) {
        onDispose { onKeyboardFocusChange(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) bumpActivity()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = task.type.icon,
                        contentDescription = task.type.displayName,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = task.type.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Pause Point",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PausePointTaskContent(
                    task = task,
                    onTaskCompleted = onTaskCompleted,
                    onOpenApp = onOpenApp,
                    onUserActivity = bumpActivity,
                    onKeyboardFocusChange = onKeyboardFocusChange
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloseAppTextButton(
                    onCloseApp = onCloseApp,
                    autoKickProgress = { autoKickProgress.value },
                    size = ZenithButtonSize.Large
                )
            }
        }
    }
}

