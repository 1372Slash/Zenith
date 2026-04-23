package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class SessionUsageOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentForegroundPackage: String = ""
    private var foregroundUpdateJob: Job? = null

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
        initialY: Int
    ) {
        val secondsElapsedState = mutableIntStateOf(0)
        val secondsLeftState = mutableIntStateOf(totalSeconds)
        val isVisibleState = mutableStateOf(true)
        var hudInstance: HUDInstance? = null
        var timerJob: Job? = null
        var x: Int = initialX
        var y: Int = initialY
        var backgroundTimestamp: Long = 0L
    }

    private val activeSessions = mutableListOf<Session>()
    private val MaxHuds = 4

    fun showHUD(
        packageName: String,
        durationMinutes: Int,
        size: Int,
        opacity: Int,
        isGoal: Boolean = false,
        onSessionEnd: () -> Unit = {}
    ) {
        if (activeSessions.any { it.packageName == packageName }) return

        if (activeSessions.size >= MaxHuds) {
            hideHUD(activeSessions.first().packageName)
        }

        val totalSeconds = durationMinutes * 60
        val session = Session(
            packageName, totalSeconds, size, opacity, isGoal, onSessionEnd,
            104, 204 + (activeSessions.size * 50)
        )
        activeSessions.add(session)

        val isForeground = currentForegroundPackage.contains(packageName) ||
                packageName.contains(currentForegroundPackage)
        if (isForeground && session.hudInstance == null) {
            session.hudInstance = createHUDInstance(session)
        }

        session.timerJob = scope.launch {
            while (true) {
                if (!isGoal && session.secondsLeftState.intValue <= 0) break
                if (isGoal && session.secondsElapsedState.intValue >= session.totalSeconds) break

                // Maximum Efficiency: Update internal state every 10 seconds to minimize CPU wakes
                delay(10000)

                if (session.backgroundTimestamp != 0L &&
                    System.currentTimeMillis() - session.backgroundTimestamp > 180000L) {
                    hideHUD(session.packageName)
                    return@launch
                }

                if (isGoal) {
                    session.secondsElapsedState.intValue = (session.secondsElapsedState.intValue + 10).coerceAtMost(session.totalSeconds)
                } else {
                    session.secondsLeftState.intValue = (session.secondsLeftState.intValue - 10).coerceAtLeast(0)
                }
            }
            if (session.hudInstance == null || (!isGoal && session.secondsLeftState.intValue <= 0) || (isGoal && session.secondsElapsedState.intValue >= session.totalSeconds)) {
                hideHUD(session.packageName)
            }
        }
    }

    private fun createHUDInstance(session: Session): HUDInstance {
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
                ZenithTheme {
                    SessionUsageHUD(
                        secondsLeftProvider = { if (session.isGoal) session.secondsElapsedState.intValue else session.secondsLeftState.intValue },
                        totalSeconds = session.totalSeconds,
                        size = session.size,
                        opacity = session.opacity,
                        isVisibleProvider = { session.isVisibleState.value },
                        isGoal = session.isGoal,
                        onDrag = { dx, dy ->
                            params.x += dx.roundToInt()
                            params.y += dy.roundToInt()
                            session.x = params.x
                            session.y = params.y
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { }
                        },
                        onFinish = {
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
            windowManager.addView(composeView, params)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return HUDInstance(composeView, lOwner, vStore)
    }

    private fun destroyHUDInstance(hud: HUDInstance) {
        try {
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

    fun hideHUD(packageName: String? = null) {
        val iterator = activeSessions.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            if (packageName == null || session.packageName == packageName) {
                session.timerJob?.cancel()
                session.hudInstance?.let { destroyHUDInstance(it) }
                session.onSessionEnd()
                iterator.remove()
                if (packageName != null) break
            }
        }
    }

    fun updateHUDUsage(packageName: String, usageMillis: Long) {
        activeSessions.find { it.packageName == packageName }?.let { session ->
            if (session.isGoal) {
                val newSeconds = (usageMillis / 1000).toInt()
                // Atur nilai secara langsung agar sinkron dengan data sistem, 
                // termasuk saat reset waktu di jam 00.00
                session.secondsElapsedState.intValue = newSeconds
            }
        }
    }

    fun updateForegroundApp(packageName: String) {
        if (currentForegroundPackage == packageName) return
        currentForegroundPackage = packageName
        
        foregroundUpdateJob?.cancel()
        foregroundUpdateJob = scope.launch {
            activeSessions.forEach { session ->
                val isForeground = packageName.contains(session.packageName) || session.packageName.contains(packageName)
                if (isForeground) {
                    session.backgroundTimestamp = 0L
                    session.isVisibleState.value = true
                    if (session.hudInstance == null && ((!session.isGoal && session.secondsLeftState.intValue > 0) || (session.isGoal && session.secondsElapsedState.intValue < session.totalSeconds))) {
                        session.hudInstance = createHUDInstance(session)
                    }
                } else {
                    if (session.isVisibleState.value) {
                        session.isVisibleState.value = false
                        if (session.backgroundTimestamp == 0L) {
                            session.backgroundTimestamp = System.currentTimeMillis()
                        }
                        launch {
                            delay(500)
                            if (!session.isVisibleState.value && session.hudInstance != null) {
                                destroyHUDInstance(session.hudInstance!!)
                                session.hudInstance = null
                            }
                        }
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
    val onSurface: Color,
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
    onDrag: (Float, Float) -> Unit,
    onFinish: () -> Unit
) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        val entranceAnimationStarted = remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entranceAnimationStarted.value = true }

        val animatingOutState = remember {
            derivedStateOf {
                val secondsLeft = secondsLeftProvider()
                if (isGoal) secondsLeft >= totalSeconds else secondsLeft <= 0
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
            finishedListener = { if (animatingOutState.value) onFinish() }
        )

        // Cache theme colors once to avoid lookups in dynamic parts
        val colorScheme = MaterialTheme.colorScheme
        val hudColors = remember(colorScheme) {
            HUDColors(
                surface = colorScheme.surface,
                primary = colorScheme.primary,
                onSurface = colorScheme.onSurface,
                track = colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }

        val baseSize = 80.dp
        val animationBuffer = 24.dp

        Box(
            modifier = Modifier
                .size((baseSize + animationBuffer) * scaleFactor)
                .pointerInput(scaleFactor) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(baseSize + animationBuffer)
                    .graphicsLayer {
                        val scale = scaleState.value
                        scaleX = scale
                        scaleY = scale
                        alpha = if (scaleFactor > 0f) (scale / scaleFactor).coerceIn(0f, 1f) * (opacity / 100f) else 0f
                        transformOrigin = TransformOrigin.Center
                    },
                contentAlignment = Alignment.Center
            ) {
                // Static background (doesn't recompose on timer ticks)
                Box(
                    modifier = Modifier
                        .requiredSize(baseSize)
                        .background(hudColors.surface, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Optimized Progress: Recomposes only every 10s
                    HUDProgress(
                        secondsLeftProvider = secondsLeftProvider,
                        totalSeconds = totalSeconds,
                        color = hudColors.primary,
                        trackColor = hudColors.track
                    )

                    // Optimized Text: Recomposes only every 10s
                    HUDTimerText(secondsLeftProvider, hudColors.onSurface)
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
    trackColor: Color
) {
    // Snapped progress: only updates every 10 seconds
    val snappedProgress by remember(totalSeconds) {
        derivedStateOf {
            val seconds = secondsLeftProvider()
            val snappedSeconds = (seconds / 10) * 10
            snappedSeconds.toFloat() / totalSeconds.toFloat()
        }
    }

    val progressAnimatable = remember { Animatable(snappedProgress) }

    LaunchedEffect(snappedProgress) {
        // Only animate the progress value itself
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
                // Isolate rendering for performance
                clip = true
                shape = CircleShape
            },
        color = color,
        trackColor = trackColor,
        // Keep it wavy but static for maximum CPU efficiency and stability
        amplitude = { 1f },
        wavelength = 20.dp,
        waveSpeed = 0.dp
    )
}

@Composable
private fun HUDTimerText(secondsProvider: () -> Int, color: Color) {
    val text by remember {
        derivedStateOf {
            val rawSeconds = secondsProvider()
            val snappedSeconds = (rawSeconds / 10) * 10
            if (snappedSeconds >= 60) {
                "${snappedSeconds / 60}m"
            } else {
                "${snappedSeconds}s"
            }
        }
    }

    // Motion: Subtle pop animation when the text changes
    val textScale = remember { Animatable(1f) }
    LaunchedEffect(text) {
        textScale.animateTo(
            targetValue = 1.15f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        )
        textScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)
        )
    }

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
