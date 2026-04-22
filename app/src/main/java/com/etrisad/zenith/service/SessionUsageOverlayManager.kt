package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    private val MAX_HUDS = 4

    fun showHUD(
        packageName: String,
        durationMinutes: Int,
        size: Int,
        opacity: Int,
        isGoal: Boolean = false,
        onSessionEnd: () -> Unit = {}
    ) {
        if (activeSessions.any { it.packageName == packageName }) return

        if (activeSessions.size >= MAX_HUDS) {
            hideHUD(activeSessions.first().packageName)
        }

        val totalSeconds = durationMinutes * 60
        // Disesuaikan agar posisi visual lingkaran tetap konsisten dengan adanya buffer animasi (104, 204)
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

                delay(1000)

                // Safety: Reset session if user is outside the app for more than 3 minutes
                if (session.backgroundTimestamp != 0L &&
                    System.currentTimeMillis() - session.backgroundTimestamp > 180000L) {
                    hideHUD(session.packageName)
                    return@launch
                }

                if (isGoal) {
                    session.secondsElapsedState.intValue++
                } else {
                    session.secondsLeftState.intValue--
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
                        secondsLeft = if (session.isGoal) session.secondsElapsedState.intValue else session.secondsLeftState.intValue,
                        totalSeconds = session.totalSeconds,
                        size = session.size,
                        opacity = session.opacity,
                        isVisible = session.isVisibleState.value,
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
            System.gc()
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
                // Only update if the system value is greater than our local timer
                // this prevents stale system stats from resetting the HUD progress backwards
                if (newSeconds > session.secondsElapsedState.intValue) {
                    session.secondsElapsedState.intValue = newSeconds
                }
            }
        }
    }

    fun updateForegroundApp(packageName: String) {
        currentForegroundPackage = packageName
        scope.launch {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SessionUsageHUD(
    secondsLeft: Int,
    totalSeconds: Int,
    size: Int,
    opacity: Int,
    isVisible: Boolean,
    isGoal: Boolean = false,
    onDrag: (Float, Float) -> Unit,
    onFinish: () -> Unit
) {
    var entranceAnimationStarted by remember { mutableStateOf(false) }
    val animatingOut = if (isGoal) secondsLeft >= totalSeconds else secondsLeft <= 0

    LaunchedEffect(Unit) {
        entranceAnimationStarted = true
    }

    val progress = if (isGoal) {
        secondsLeft.toFloat() / totalSeconds.toFloat()
    } else {
        secondsLeft.toFloat() / totalSeconds.toFloat()
    }
    val scaleFactor = size / 100f
    val showHUD = isVisible && !animatingOut && entranceAnimationStarted

    val scale by animateFloatAsState(
        targetValue = if (showHUD) scaleFactor else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "HUDScale",
        finishedListener = {
            if (animatingOut) {
                onFinish()
            }
        }
    )

    val baseSize = 80.dp
    val animationBuffer = 24.dp // Ruang ekstra agar animasi bounce tidak terpotong

    Box(
        modifier = Modifier
            // Ukuran luar (hitbox) sekarang mencakup baseSize + buffer, dikalikan scaleFactor
            .size((baseSize + animationBuffer) * scaleFactor)
            .pointerInput(scaleFactor) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Box internal untuk animasi visual
        Box(
            modifier = Modifier
                .requiredSize(baseSize + animationBuffer)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (scaleFactor > 0f) (scale / scaleFactor).coerceIn(0f, 1f) * (opacity / 100f) else 0f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.requiredSize(baseSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        amplitude = { 1.5f },
                        wavelength = 20.dp
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (secondsLeft >= 60) "${secondsLeft / 60}m" else "${secondsLeft}s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
