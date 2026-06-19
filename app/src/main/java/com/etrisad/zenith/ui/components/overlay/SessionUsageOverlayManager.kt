package com.etrisad.zenith.ui.components.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.etrisad.zenith.data.preferences.FontOption
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.TooltipArrowPosition
import com.etrisad.zenith.ui.components.ZenithTooltipBox
import com.etrisad.zenith.ui.theme.GSFlexSettings
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class SessionUsageOverlayManager(
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sharedPrefs = MutableStateFlow<UserPreferences?>(null)
    val sharedPrefs: StateFlow<UserPreferences?> = _sharedPrefs

    init {
        scope.launch {
            preferencesRepository.userPreferencesFlow.collectLatest { prefs ->
                _sharedPrefs.value = prefs
            }
        }
    }

    private val SYSTEM_UI_PACKAGES = setOf(
        "com.android.systemui",
        "android",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller"
    )

    private data class HUDInstance(
        val overlayView: ComposeView,
        val lifecycleOwner: MyLifecycleOwner,
        val viewModelStore: ViewModelStore
    )

    private class Session(
        val packageName: String,
        val totalSeconds: Int,
        val size: Int,
        val opacity: Int,
        val isGoal: Boolean,
        val onSessionEnd: () -> Unit,
        initialX: Int,
        initialY: Int,
        initialSeconds: Int = 0
    ) {
        val secondsElapsedState = mutableIntStateOf(initialSeconds)
        val secondsLeftState = mutableIntStateOf(if (isGoal) (totalSeconds - initialSeconds).coerceAtLeast(0) else totalSeconds)
        val isVisibleState = mutableStateOf(true)
        val isTemporarilyHiddenState = mutableStateOf(false)
        @Volatile
        var hudInstance: HUDInstance? = null
        var timerJob: Job? = null
        @Volatile
        var x: Int = initialX
        @Volatile
        var y: Int = initialY
        @Volatile
        var backgroundTimestamp: Long = 0L
        @Volatile
        var lastReportedUsageSeconds: Int = initialSeconds
    }

    companion object {
        private val activeSessions = Collections.synchronizedList(mutableListOf<Session>())
        private val globalHUDViews = ConcurrentHashMap<String, ComposeView>()
        private const val MaxHuds = 4

        @Volatile
        private var currentForegroundPackage: String = ""
        private var foregroundUpdateJob: Job? = null
        private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    fun showHUD(
        packageName: String,
        durationMinutes: Int,
        size: Int,
        opacity: Int,
        isGoal: Boolean = false,
        initialSeconds: Int = 0,
        onSessionEnd: () -> Unit = {}
    ) {
        synchronized(activeSessions) {
            val existing = activeSessions.find { it.packageName == packageName }
            if (existing != null) {
                if (existing.hudInstance == null) {
                    existing.isVisibleState.value = true
                    existing.hudInstance = createHUDInstance(existing)
                }
                return
            }

            if (activeSessions.size >= MaxHuds) {
                hideHUD(activeSessions.firstOrNull()?.packageName)
            }

            val totalSeconds = durationMinutes * 60
            val session = Session(
                packageName, totalSeconds, size, opacity, isGoal, onSessionEnd,
                104, 204 + (activeSessions.size * 50),
                initialSeconds = initialSeconds
            )
            activeSessions.add(session)

            val isForeground = currentForegroundPackage.contains(packageName) ||
                    packageName.contains(currentForegroundPackage)
            if ((isForeground || isGoal) && session.hudInstance == null) {
                session.hudInstance = createHUDInstance(session)
            }

            session.timerJob = managerScope.launch {
                var lastUpdateMillis = System.currentTimeMillis()
                while (true) {
                    if (!isGoal && session.secondsLeftState.intValue <= 0) break
                    if (isGoal && session.secondsElapsedState.intValue >= session.totalSeconds) break

                    val updateInterval = if (isGoal) {
                        val remainingSeconds = (session.totalSeconds - session.lastReportedUsageSeconds).coerceAtLeast(0)
                        when {
                            remainingSeconds < 60 -> 10000L
                            remainingSeconds < 300 -> 15000L
                            remainingSeconds < 900 -> 30000L
                            else -> 50000L
                        }
                    } else {
                        val remainingMillis = session.secondsLeftState.intValue * 1000L
                        when {
                            remainingMillis > 3600000 -> 80000L
                            remainingMillis > 600000 -> 50000L
                            remainingMillis > 300000 -> 30000L
                            remainingMillis > 60000 -> 15000L
                            else -> 10000L
                        }
                    }

                    delay(updateInterval)

                    if (session.backgroundTimestamp != 0L &&
                        System.currentTimeMillis() - session.backgroundTimestamp > 300000L) {
                        hideHUD(session.packageName)
                        return@launch
                    }

                    val now = System.currentTimeMillis()
                    val elapsedMillis = now - lastUpdateMillis
                    if (elapsedMillis >= 1000) {
                        val secondsToProcess = (elapsedMillis / 1000).toInt()
                        if (!isGoal) {
                            session.secondsLeftState.intValue = (session.secondsLeftState.intValue - secondsToProcess).coerceAtLeast(0)
                        } else {
                            val newElapsed = session.secondsElapsedState.intValue + secondsToProcess
                            session.secondsElapsedState.intValue = newElapsed.coerceAtMost(session.totalSeconds)
                        }
                        lastUpdateMillis = now - (elapsedMillis % 1000)
                    }
                }
                
                if (session.hudInstance == null) {
                    hideHUD(session.packageName)
                } else if (!isGoal) {
                    hideHUD(session.packageName)
                }
            }
        }
    }

    fun destroyAllHUDs() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { destroyAllHUDs() }
            return
        }
        android.util.Log.d("Zenith_SCREEN", "SessionUsageOverlayManager: destroying all HUDs")
        synchronized(activeSessions) {
            activeSessions.forEach { session ->
                session.hudInstance?.let { destroyHUDInstance(it, session.packageName) }
                session.hudInstance = null
                session.isVisibleState.value = false
                session.timerJob?.cancel()
                session.timerJob = null
            }
            activeSessions.clear()
        }
    }

    fun destroy() {
        destroyAllHUDs()
        scope.cancel()
        managerScope.cancel()
        foregroundUpdateJob?.cancel()
    }

    fun hideAllHUDViews() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideAllHUDViews() }
            return
        }
        synchronized(activeSessions) {
            activeSessions.forEach { session ->
                session.hudInstance?.let { destroyHUDInstance(it, session.packageName) }
                session.hudInstance = null
                session.isVisibleState.value = false
            }
        }
    }

    fun restoreAllHUDViews() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAllHUDViews() }
            return
        }
        synchronized(activeSessions) {
            activeSessions.forEach { session ->
                session.isVisibleState.value = true
                session.backgroundTimestamp = 0L
                if (session.hudInstance == null && !session.isTemporarilyHiddenState.value &&
                    ((!session.isGoal && session.secondsLeftState.intValue > 0) ||
                     (session.isGoal && session.secondsElapsedState.intValue < session.totalSeconds))) {
                    session.hudInstance = createHUDInstance(session)
                }
            }
        }
    }

    private fun createHUDInstance(session: Session): HUDInstance {
        globalHUDViews[session.packageName]?.let { oldView ->
            try {
                windowManager.removeViewImmediate(oldView)
            } catch (_: Exception) {}
            globalHUDViews.remove(session.packageName)
        }

        val vStore = ViewModelStore()
        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = session.x
            y = session.y
        }

        val composeView = ComposeView(context).apply {
            setContent {
                val userPrefs by sharedPrefs.collectAsState(initial = null)
                
                ZenithTheme(
                    fontOption = userPrefs?.fontOption ?: FontOption.SYSTEM,
                    dynamicColor = userPrefs?.dynamicColor ?: true,
                    expressiveColors = userPrefs?.expressiveColors ?: false,
                    gsFlexSettings = userPrefs?.gsFlexSettings ?: GSFlexSettings()
                ) {
                    SessionUsageHUD(
                        secondsLeftProvider = { if (session.isGoal) session.secondsElapsedState.intValue else session.secondsLeftState.intValue },
                        totalSeconds = session.totalSeconds,
                        size = session.size,
                        opacity = session.opacity,
                        isVisibleProvider = { session.isVisibleState.value && !session.isTemporarilyHiddenState.value },
                        isGoal = session.isGoal,
                        userPrefs = userPrefs,
                        onDrag = { dx, dy ->
                            params.x += dx.roundToInt()
                            params.y += dy.roundToInt()
                            session.x = params.x
                            session.y = params.y
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { }
                        },
                        onHideTemporarily = {
                            session.isTemporarilyHiddenState.value = true
                            managerScope.launch {
                                delay(30000)
                                session.isTemporarilyHiddenState.value = false
                                updateForegroundApp(currentForegroundPackage, force = true)
                                preferencesRepository.setHudHideFeatureLearned(true)
                            }
                        },
                        onFinish = {
                            val isTimeUp = if (session.isGoal) 
                                session.secondsElapsedState.intValue >= session.totalSeconds 
                                else session.secondsLeftState.intValue <= 0
                                
                            if (isTimeUp) {
                                session.isVisibleState.value = false
                            }
                            hideHUD(session.packageName)
                        }
                    )
                }
            }
        }

        composeView.setViewTreeLifecycleOwner(lOwner)
        composeView.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = vStore
        })
        composeView.setViewTreeSavedStateRegistryOwner(lOwner)

        try {
            globalHUDViews[session.packageName] = composeView
            windowManager.addView(composeView, params)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            globalHUDViews.remove(session.packageName)
            e.printStackTrace()
        }

        return HUDInstance(composeView, lOwner, vStore)
    }

    private fun destroyHUDInstance(hud: HUDInstance, packageName: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { destroyHUDInstance(hud, packageName) }
            return
        }
        try {
            globalHUDViews.remove(packageName)
            hud.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            hud.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            hud.lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            hud.overlayView.disposeComposition()
            windowManager.removeViewImmediate(hud.overlayView)
            hud.viewModelStore.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeAllHUDViews() {
        synchronized(activeSessions) {
            activeSessions.forEach { session ->
                session.hudInstance?.let {
                    destroyHUDInstance(it, session.packageName)
                    session.hudInstance = null
                }
            }
        }
    }

    fun hideHUD(packageName: String? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideHUD(packageName) }
            return
        }
        synchronized(activeSessions) {
            val iterator = activeSessions.iterator()
            while (iterator.hasNext()) {
                val session = iterator.next()
                if (packageName == null || session.packageName == packageName) {
                    session.timerJob?.cancel()
                    session.hudInstance?.let { destroyHUDInstance(it, session.packageName) }
                    session.onSessionEnd()
                    iterator.remove()
                    if (packageName != null) break
                }
            }
        }
    }

    fun updateHUDUsage(packageName: String, usageMillis: Long) {
        synchronized(activeSessions) {
            activeSessions.find { it.packageName == packageName }?.let { session ->
                if (session.isGoal) {
                    val seconds = (usageMillis / 1000).toInt()
                    session.lastReportedUsageSeconds = seconds
                    session.secondsElapsedState.intValue = seconds
                }
            }
        }
    }

    fun ensureSessionHUDActive(packageName: String) {
        if (packageName.isEmpty() || SYSTEM_UI_PACKAGES.contains(packageName)) return
        synchronized(activeSessions) {
            val session = activeSessions.find { it.packageName == packageName }
            session?.let {
                it.isVisibleState.value = true
                it.backgroundTimestamp = 0L
                if (it.hudInstance == null && !it.isTemporarilyHiddenState.value &&
                    ((!it.isGoal && it.secondsLeftState.intValue > 0) ||
                     (it.isGoal && it.secondsElapsedState.intValue < it.totalSeconds))) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        it.hudInstance = createHUDInstance(it)
                    } else {
                        mainHandler.post {
                            synchronized(it) {
                                if (it.hudInstance == null) {
                                    it.hudInstance = createHUDInstance(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateForegroundApp(packageName: String, force: Boolean = false) {
        if (packageName.isEmpty() || SYSTEM_UI_PACKAGES.contains(packageName)) return
        if (!force && currentForegroundPackage == packageName) return
        currentForegroundPackage = packageName
        
        foregroundUpdateJob?.cancel()
        foregroundUpdateJob = managerScope.launch {
            val sessionsToProcess = synchronized(activeSessions) { activeSessions.toList() }
            sessionsToProcess.forEach { session ->
                val isForeground = packageName.isNotEmpty() &&
                                 (packageName == session.packageName || packageName.startsWith("${session.packageName}."))
                
                if (isForeground) {
                    session.backgroundTimestamp = 0L
                    session.isVisibleState.value = true
                    
                    synchronized(session) {
                        if (session.hudInstance == null && !session.isTemporarilyHiddenState.value && 
                            ((!session.isGoal && session.secondsLeftState.intValue > 0) || 
                             (session.isGoal && session.secondsElapsedState.intValue < session.totalSeconds))) {
                            try {
                                session.hudInstance = createHUDInstance(session)
                            } catch (e: Exception) {
                                Log.e("SessionHUD", "Failed to create HUD: ${e.message}")
                            }
                        }
                    }
                } else {
                    if (session.isVisibleState.value) {
                        session.isVisibleState.value = false
                        if (session.backgroundTimestamp == 0L) {
                            session.backgroundTimestamp = System.currentTimeMillis()
                        }
                    }
                    session.hudInstance?.let {
                        destroyHUDInstance(it, session.packageName)
                        session.hudInstance = null
                    }
                }
            }
        }
    }

    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry
        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: Bundle?) = savedStateRegistryController.performRestore(savedState)
    }
}

@Immutable
private data class HUDColors(
    val surface: Color,
    val primary: Color,
    val tertiary: Color,
    val onSurface: Color,
    val onTertiary: Color,
    val track: Color
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionUsageHUD(
    secondsLeftProvider: () -> Int,
    totalSeconds: Int,
    size: Int,
    opacity: Int,
    isVisibleProvider: () -> Boolean,
    isGoal: Boolean = false,
    userPrefs: UserPreferences?,
    onDrag: (Float, Float) -> Unit,
    onHideTemporarily: () -> Unit,
    onFinish: () -> Unit
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        val entranceAnimationStarted = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entranceAnimationStarted.value = true }
        val tooltipState = rememberTooltipState(isPersistent = true)

        LaunchedEffect(userPrefs?.hudHideFeatureLearned) {
            if (userPrefs?.hudHideFeatureLearned == false && userPrefs != null) {
                delay(3000)
                tooltipState.show()
                delay(8000)
                tooltipState.dismiss()
            }
        }

        val isCompleted = remember {
            derivedStateOf {
                val seconds = secondsLeftProvider()
                if (isGoal) seconds >= totalSeconds else seconds <= 0
            }
        }

        var celebrationFinished by remember { mutableStateOf(false) }

        val animatingOutState = remember {
            derivedStateOf {
                if (isGoal) isCompleted.value && celebrationFinished else isCompleted.value
            }
        }

        val celebrationScale = remember { Animatable(1f) }
        LaunchedEffect(isCompleted.value) {
            if (isCompleted.value && isGoal) {
                celebrationScale.animateTo(
                    targetValue = 1.12f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                celebrationScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                delay(1500)
                celebrationFinished = true
            }
        }

        val scaleFactor = remember(size) { size / 100f }
        val showHUDState = remember {
            derivedStateOf {
                isVisibleProvider() && !animatingOutState.value && entranceAnimationStarted.value
            }
        }

        val scaleState = animateFloatAsState(
            targetValue = if (showHUDState.value) scaleFactor else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "HUDScale",
            finishedListener = { 
                if (!showHUDState.value) onFinish()
            }
        )

        val colorScheme = MaterialTheme.colorScheme
        val hudColors = remember(colorScheme) {
            HUDColors(
                surface = colorScheme.surface,
                primary = colorScheme.primary,
                tertiary = colorScheme.tertiary,
                onSurface = colorScheme.onSurface,
                onTertiary = colorScheme.onTertiary,
                track = colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }

        val baseSize = 80.dp
        val animationBuffer = 24.dp

        ZenithTooltipBox(
            tooltipText = "Double click to\nhide for 30s",
            state = tooltipState,
            arrowPosition = TooltipArrowPosition.StartCenter
        ) {
            Box(
                modifier = Modifier
                    .size((baseSize + animationBuffer) * scaleFactor)
                    .pointerInput(scaleFactor) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                onHideTemporarily()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(baseSize + animationBuffer)
                        .graphicsLayer {
                            val scale = scaleState.value * celebrationScale.value
                            scaleX = scale
                            scaleY = scale
                            alpha = if (scaleFactor > 0f) (scale / scaleFactor).coerceIn(0f, 1f) * (opacity / 100f) else 0f
                            transformOrigin = TransformOrigin.Center
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(baseSize)
                            .background(hudColors.surface, CircleShape)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        HUDProgress(
                            secondsLeftProvider = secondsLeftProvider,
                            totalSeconds = totalSeconds,
                            color = hudColors.primary,
                            tertiaryColor = hudColors.tertiary,
                            trackColor = hudColors.track,
                            isCompleted = isCompleted.value && isGoal
                        )

                        HUDTimerText(
                            secondsProvider = secondsLeftProvider,
                            isCompleted = isCompleted.value && isGoal,
                            color = if (isCompleted.value && isGoal) hudColors.tertiary else hudColors.onSurface,
                            iconColor = if (isCompleted.value && isGoal) hudColors.tertiary else hudColors.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HUDProgress(
    secondsLeftProvider: () -> Int,
    totalSeconds: Int,
    color: Color,
    tertiaryColor: Color,
    trackColor: Color,
    isCompleted: Boolean
) {
    val finalColor by animateColorAsState(
        targetValue = if (isCompleted) tertiaryColor else color,
        animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow),
        label = "ProgressColor"
    )
    val amplitude by animateFloatAsState(if (isCompleted) 4f else 1f, label = "WaveAmplitude")
    val waveSpeed by animateDpAsState(if (isCompleted) 15.dp else 0.dp, label = "WaveSpeed")

    val snappedProgress by remember(totalSeconds) {
        derivedStateOf {
            val seconds = secondsLeftProvider()
            seconds.toFloat() / totalSeconds.toFloat()
        }
    }

    val progressAnimatable = remember { Animatable(snappedProgress) }

    LaunchedEffect(snappedProgress) {
        progressAnimatable.animateTo(
            targetValue = snappedProgress,
            animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing)
        )
    }

    CircularWavyProgressIndicator(
        progress = { progressAnimatable.value },
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
            .graphicsLayer {
                clip = true
                shape = CircleShape
            },
        color = finalColor,
        trackColor = trackColor,
        amplitude = { amplitude },
        wavelength = 20.dp,
        waveSpeed = waveSpeed
    )
}

@Composable
private fun HUDTimerText(
    secondsProvider: () -> Int,
    isCompleted: Boolean,
    color: Color,
    iconColor: Color
) {
    val text by remember {
        derivedStateOf {
            val snappedSeconds = secondsProvider()
            if (snappedSeconds >= 60) {
                "${snappedSeconds / 60}m"
            } else {
                "${snappedSeconds}s"
            }
        }
    }

    val textScale = remember { Animatable(1f) }
    LaunchedEffect(text, isCompleted) {
        textScale.animateTo(
            targetValue = 1.2f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        )
        textScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
        )
    }

    if (isCompleted) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = textScale.value
                    scaleY = textScale.value
                }
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color,
            lineHeight = 16.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = textScale.value
                scaleY = textScale.value
            }
        )
    }
}
