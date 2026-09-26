package com.etrisad.zenith.ui.components.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.InterceptOverlayManager
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.nfc.NfcScanActivity
import com.etrisad.zenith.ui.components.nfc.isNfcSupported
import com.etrisad.zenith.ui.components.pausepoint.PausePointEngine
import com.etrisad.zenith.ui.components.pausepoint.PausePointTask
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskContent
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.random.Random

private const val CONTENT_A = "pausePoint"
private const val CONTENT_B = "actualContent"
private const val CONTENT_C = "pausePointTestResult"
private fun android.content.Context.isCameraGranted(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

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
    onKeyboardFocusChange: (Boolean) -> Unit = {},
    forcedTaskType: PausePointTaskType? = null
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

    val pausePointEnabled = forcedTaskType != null || userPrefs.pausePointEnabled
    val enabledTypes = forcedTaskType?.let { setOf(it) } ?: userPrefs.pausePointTaskTypes
    var currentPauseTask by remember(packageName, forcedTaskType) { mutableStateOf<PausePointTask?>(null) }
    var pauseTaskCompleted by remember { mutableStateOf(false) }
    var goalPackagesForSwitch by remember { mutableStateOf(emptySet<String>()) }
    val nfcAvailableForSwitch = remember { isNfcSupported(context) }

    LaunchedEffect(pausePointEnabled, packageName, forcedTaskType, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        if (pausePointEnabled && currentPauseTask == null) {
            val retained = if (forcedTaskType == null &&
                InterceptOverlayManager.retainedPauseTaskForPackage == packageName
            ) InterceptOverlayManager.retainedPauseTask else null
            InterceptOverlayManager.clearRetainedPauseTask()
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            goalPackagesForSwitch = goals.map { it.packageName }.toSet()
            currentPauseTask = retained ?: when (forcedTaskType) {
                PausePointTaskType.QR_SCAN -> PausePointTask.QrScan(
                    code = "PAUSE-${Random.nextInt(100000, 999999)}",
                    validCodes = userPrefs.pausePointQrCodes,
                    acceptAny = true
                )
                PausePointTaskType.NFC_SCAN -> PausePointTask.NfcScan(
                    validTagIds = userPrefs.pausePointNfcTagIds,
                    acceptAny = true
                )
                PausePointTaskType.CHOOSE_APP ->
                    if (goals.isEmpty()) {
                        PausePointTask.ChooseApp(suggestedPackage = "", suggestedAppName = "a goal app")
                    } else {
                        val goal = goals.random()
                        PausePointTask.ChooseApp(
                            suggestedPackage = goal.packageName,
                            suggestedAppName = goal.appName
                        )
                    }
                else -> PausePointEngine.generateTask(
                    enabledTypes = enabledTypes,
                    goalPackageNames = goals.map { it.packageName }.toSet(),
                    goalAppNames = goals.associate { it.packageName to it.appName },
                    qrCodes = userPrefs.pausePointQrCodes,
                    nfcTagIds = userPrefs.pausePointNfcTagIds,
                    config = userPrefs.pausePointConfig,
                    cameraGranted = context.isCameraGranted(),
                    nfcAvailable = nfcAvailableForSwitch
                )
            }
        }
    }

    val switchAlternatives = remember(currentPauseTask, enabledTypes, userPrefs, goalPackagesForSwitch) {
        val task = currentPauseTask
        if (forcedTaskType != null || task == null) emptyList()
        else PausePointEngine.availableAlternatives(
            currentType = task.type,
            enabledTypes = enabledTypes,
            qrCodes = userPrefs.pausePointQrCodes,
            nfcTagIds = userPrefs.pausePointNfcTagIds,
            goalPackageNames = goalPackagesForSwitch,
            cameraGranted = context.isCameraGranted(),
            nfcAvailable = nfcAvailableForSwitch
        )
    }

    val onSwitchPauseTask: () -> Unit = {
        val current = currentPauseTask
        if (current != null) {
            scope.launch {
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            goalPackagesForSwitch = goals.map { it.packageName }.toSet()
            val alternatives = PausePointEngine.availableAlternatives(
                currentType = current.type,
                enabledTypes = enabledTypes,
                qrCodes = userPrefs.pausePointQrCodes,
                nfcTagIds = userPrefs.pausePointNfcTagIds,
                goalPackageNames = goals.map { it.packageName }.toSet(),
                cameraGranted = context.isCameraGranted(),
                nfcAvailable = isNfcSupported(context)
            )
            if (alternatives.isNotEmpty()) {
                currentPauseTask = PausePointEngine.generateTask(
                    enabledTypes = alternatives.toSet(),
                    goalPackageNames = goals.map { it.packageName }.toSet(),
                    goalAppNames = goals.associate { it.packageName to it.appName },
                    qrCodes = userPrefs.pausePointQrCodes,
                    nfcTagIds = userPrefs.pausePointNfcTagIds,
                    config = userPrefs.pausePointConfig,
                    cameraGranted = context.isCameraGranted(),
                    nfcAvailable = isNfcSupported(context)
                )
            }
            }
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

    val onNfcScanRequested: (PausePointTask.NfcScan) -> Unit = { nfcTask ->
        InterceptOverlayManager.retainedPauseTask = nfcTask
        InterceptOverlayManager.retainedPauseTaskForPackage = packageName
        showSheet = false
        scope.launch {
            delay(450)
            try {
                NfcScanActivity.start(
                    context = context,
                    expectedTagIds = nfcTask.validTagIds,
                    acceptAny = nfcTask.acceptAny
                )
                InterceptOverlayManager.hideActiveOverlayForNfcScan()
            } catch (_: Exception) {
                InterceptOverlayManager.clearRetainedPauseTask()
            }
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
        dismissOnOutsideTap = forcedTaskType != null,
        dragHandleCurrentUses = dragUses,
        dragHandleMaxUses = dragMaxUses,
        dragHandleEmergencyCount = dragEmergency,
        dragHandleIsIncentiveLocked = isIncentiveActive && !incentiveTier.isUnlocked,
        dragHandleIncentiveTier = if (isIncentiveActive) incentiveTier else null,
        dragHandleBonusUsesLeft = bonusUsesLeft,
        contentKey = when {
            !prefsLoaded -> null
            showPausePoint -> CONTENT_A
            forcedTaskType != null -> CONTENT_C
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
                        onKeyboardFocusChange = onKeyboardFocusChange,
                        showSwitchButton = switchAlternatives.isNotEmpty(),
                        onSwitchTask = onSwitchPauseTask,
                        onNfcScanRequested = onNfcScanRequested
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp))
                }
            }
            CONTENT_C -> {
                val taskType = currentPauseTask?.type
                if (taskType != null) {
                    PausePointTestResultContent(
                        taskType = taskType,
                        isEnabled = taskType in userPrefs.pausePointTaskTypes,
                        onEnable = {
                            scope.launch {
                                userPrefsRepo.setPausePointTaskTypes(userPrefs.pausePointTaskTypes + taskType)
                                userPrefsRepo.setPausePointEnabled(true)
                            }
                            closeOverlay()
                        }
                    )
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
    var goalPackagesForSwitch by remember { mutableStateOf(emptySet<String>()) }
    val nfcAvailableForSwitch = remember { isNfcSupported(context) }

    LaunchedEffect(pausePointEnabled, packageName, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        if (pausePointEnabled && currentPauseTask == null) {
            val retained = if (InterceptOverlayManager.retainedPauseTaskForPackage == packageName) {
                InterceptOverlayManager.retainedPauseTask
            } else null
            InterceptOverlayManager.clearRetainedPauseTask()
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            goalPackagesForSwitch = goals.map { it.packageName }.toSet()
            currentPauseTask = retained ?: PausePointEngine.generateTask(
                enabledTypes = enabledTypes,
                goalPackageNames = goals.map { it.packageName }.toSet(),
                goalAppNames = goals.associate { it.packageName to it.appName },
                qrCodes = userPrefs.pausePointQrCodes,
                nfcTagIds = userPrefs.pausePointNfcTagIds,
                config = userPrefs.pausePointConfig,
                cameraGranted = context.isCameraGranted(),
                nfcAvailable = nfcAvailableForSwitch
            )
        }
    }

    val switchAlternatives = remember(currentPauseTask, enabledTypes, userPrefs, goalPackagesForSwitch) {
        val task = currentPauseTask
        if (task == null) emptyList()
        else PausePointEngine.availableAlternatives(
            currentType = task.type,
            enabledTypes = enabledTypes,
            qrCodes = userPrefs.pausePointQrCodes,
            nfcTagIds = userPrefs.pausePointNfcTagIds,
            goalPackageNames = goalPackagesForSwitch,
            cameraGranted = context.isCameraGranted(),
            nfcAvailable = nfcAvailableForSwitch
        )
    }

    val onSwitchPauseTask: () -> Unit = {
        val current = currentPauseTask
        if (current != null) {
            scope.launch {
            val goals = withContext(Dispatchers.IO) {
                shieldRepository.allShields.first().filter { it.type == FocusType.GOAL }
            }
            goalPackagesForSwitch = goals.map { it.packageName }.toSet()
            val alternatives = PausePointEngine.availableAlternatives(
                currentType = current.type,
                enabledTypes = enabledTypes,
                qrCodes = userPrefs.pausePointQrCodes,
                nfcTagIds = userPrefs.pausePointNfcTagIds,
                goalPackageNames = goals.map { it.packageName }.toSet(),
                cameraGranted = context.isCameraGranted(),
                nfcAvailable = isNfcSupported(context)
            )
            if (alternatives.isNotEmpty()) {
                currentPauseTask = PausePointEngine.generateTask(
                    enabledTypes = alternatives.toSet(),
                    goalPackageNames = goals.map { it.packageName }.toSet(),
                    goalAppNames = goals.associate { it.packageName to it.appName },
                    qrCodes = userPrefs.pausePointQrCodes,
                    nfcTagIds = userPrefs.pausePointNfcTagIds,
                    config = userPrefs.pausePointConfig,
                    cameraGranted = context.isCameraGranted(),
                    nfcAvailable = isNfcSupported(context)
                )
            }
            }
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

    val onNfcScanRequested: (PausePointTask.NfcScan) -> Unit = { nfcTask ->
        InterceptOverlayManager.retainedPauseTask = nfcTask
        InterceptOverlayManager.retainedPauseTaskForPackage = packageName
        showSheet = false
        scope.launch {
            delay(450)
            try {
                NfcScanActivity.start(
                    context = context,
                    expectedTagIds = nfcTask.validTagIds,
                    acceptAny = nfcTask.acceptAny
                )
                InterceptOverlayManager.hideActiveOverlayForNfcScan()
            } catch (_: Exception) {
                InterceptOverlayManager.clearRetainedPauseTask()
            }
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
                        onKeyboardFocusChange = onKeyboardFocusChange,
                        showSwitchButton = switchAlternatives.isNotEmpty(),
                        onSwitchTask = onSwitchPauseTask,
                        onNfcScanRequested = onNfcScanRequested
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

private const val SWITCH_HOLD_MS = 5000L

private fun <S> pauseTaskSwitchTransform(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
    (fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
        slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { it / 3 }
        ).togetherWith(
            fadeOut(animationSpec = tween(250)) +
                slideOutHorizontally(animationSpec = tween(250)) { -it / 3 }
        ).using(SizeTransform(clip = false))
}

@Composable
private fun SwitchPauseTaskButton(
    onSwitchTask: () -> Unit,
    size: ZenithButtonSize,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }
    var switching by remember { mutableStateOf(false) }
    val currentOnSwitch by rememberUpdatedState(onSwitchTask)

    LaunchedEffect(isPressed) {
        if (isPressed && !switching) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - startTime) / SWITCH_HOLD_MS.toFloat()).coerceIn(0f, 1f)
                holdProgress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            switching = true
            currentOnSwitch()
            delay(1200)
            holdProgress.snapTo(0f)
            switching = false
        } else if (!isPressed && !switching) {
            holdProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    val holdSeconds = ceil(SWITCH_HOLD_MS / 1000f * (1f - holdProgress.value)).toInt().coerceAtLeast(1)
    ZenithButton(
        onClick = {},
        text = when {
            switching -> "Switching…"
            isPressed -> "Hold ${holdSeconds}s to switch"
            else -> "I cannot do this now"
        },
        icon = Icons.Outlined.SwapHoriz,
        type = ZenithButtonType.Tonal,
        size = size,
        fillMaxWidth = true,
        enabled = !switching,
        interactionSource = interactionSource,
        backgroundProgressProvider = { holdProgress.value },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PausePointContent(
    task: PausePointTask,
    onTaskCompleted: () -> Unit,
    onOpenApp: (String) -> Unit,
    onCloseApp: () -> Unit,
    onKeyboardFocusChange: (Boolean) -> Unit,
    showSwitchButton: Boolean = false,
    onSwitchTask: () -> Unit = {},
    onNfcScanRequested: ((PausePointTask.NfcScan) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        PausePointLandscapeContent(
            task = task,
            onTaskCompleted = onTaskCompleted,
            onOpenApp = onOpenApp,
            onCloseApp = onCloseApp,
            onKeyboardFocusChange = onKeyboardFocusChange,
            showSwitchButton = showSwitchButton,
            onSwitchTask = onSwitchTask,
            onNfcScanRequested = onNfcScanRequested
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
        verticalArrangement = Arrangement.spacedBy(if (showSwitchButton) 12.dp else 0.dp)
    ) {
        AnimatedContent(
            targetState = task,
            transitionSpec = pauseTaskSwitchTransform(),
            label = "pause_task_switch"
        ) { currentTask ->
            Column(
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
                            imageVector = currentTask.type.icon,
                            contentDescription = currentTask.type.displayName,
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
                            text = currentTask.type.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = currentTask.type.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                PausePointTaskContent(
                    task = currentTask,
                    onTaskCompleted = onTaskCompleted,
                    onOpenApp = onOpenApp,
                    onUserActivity = bumpActivity,
                    onKeyboardFocusChange = onKeyboardFocusChange,
                    onNfcScanRequested = onNfcScanRequested
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showSwitchButton) {
                key(task) {
                    SwitchPauseTaskButton(
                        onSwitchTask = {
                            bumpActivity()
                            onSwitchTask()
                        },
                        size = ZenithButtonSize.Large
                    )
                }
            }

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
    onKeyboardFocusChange: (Boolean) -> Unit,
    showSwitchButton: Boolean = false,
    onSwitchTask: () -> Unit = {},
    onNfcScanRequested: ((PausePointTask.NfcScan) -> Unit)? = null
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
            AnimatedContent(
                targetState = task.type,
                transitionSpec = pauseTaskSwitchTransform(),
                label = "pause_task_info_switch",
                modifier = Modifier.weight(1f)
            ) { currentType ->
                Column(
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
                            imageVector = currentType.icon,
                            contentDescription = currentType.displayName,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentType.displayName,
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
                        text = currentType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = task,
                    transitionSpec = pauseTaskSwitchTransform(),
                    label = "pause_task_switch_landscape"
                ) { currentTask ->
                    PausePointTaskContent(
                        task = currentTask,
                        onTaskCompleted = onTaskCompleted,
                        onOpenApp = onOpenApp,
                        onUserActivity = bumpActivity,
                        onKeyboardFocusChange = onKeyboardFocusChange,
                        onNfcScanRequested = onNfcScanRequested
                    )
                }

                if (showSwitchButton) {
                    Spacer(modifier = Modifier.height(12.dp))
                    key(task) {
                        SwitchPauseTaskButton(
                            onSwitchTask = {
                                bumpActivity()
                                onSwitchTask()
                            },
                            size = ZenithButtonSize.Medium
                        )
                    }
                }

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

@Composable
private fun PausePointTestResultContent(
    taskType: PausePointTaskType,
    isEnabled: Boolean,
    onEnable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
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
                    imageVector = taskType.icon,
                    contentDescription = taskType.displayName,
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
                    text = "${taskType.displayName} Passed!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "You successfully completed this pause point.",
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
            Text(
                text = "Enable this pause point?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "It will appear before you open blocked apps during focus, giving you a moment to pause before continuing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            ZenithButton(
                onClick = onEnable,
                modifier = Modifier.fillMaxWidth(),
                text = if (isEnabled) "${taskType.displayName} Enabled" else "Enable ${taskType.displayName}",
                icon = Icons.Filled.Check,
                size = ZenithButtonSize.ExtraLarge,
                enabled = !isEnabled
            )
        }
    }
}
