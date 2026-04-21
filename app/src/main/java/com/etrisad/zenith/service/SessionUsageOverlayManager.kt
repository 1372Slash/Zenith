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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class SessionUsageOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    private data class HUDInstance(
        val packageName: String,
        val overlayView: ComposeView,
        val lifecycleOwner: MyLifecycleOwner,
        val viewModelStore: ViewModelStore,
        val isVisibleState: MutableState<Boolean>
    )

    private val activeHUDs = mutableListOf<HUDInstance>()
    private val MAX_HUDS = 4

    fun showHUD(
        packageName: String,
        durationMinutes: Int,
        size: Int,
        opacity: Int,
        onSessionEnd: () -> Unit = {}
    ) {
        // Jika sudah ada HUD untuk package ini, jangan buat baru
        if (activeHUDs.any { it.packageName == packageName }) return

        // Batasi maksimal 4 instance, hapus yang paling lama jika penuh
        if (activeHUDs.size >= MAX_HUDS) {
            hideHUD(activeHUDs.first().packageName)
        }

        val isVisibleState = mutableStateOf(true)
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
            x = 100
            y = 200 + (activeHUDs.size * 50) // Beri sedikit offset agar tidak menumpuk persis
        }

        val composeView = ComposeView(context).apply {
            setContent {
                ZenithTheme {
                    SessionUsageHUD(
                        durationMinutes = durationMinutes,
                        size = size,
                        opacity = opacity,
                        isVisible = isVisibleState,
                        onDrag = { dx, dy ->
                            params.x += dx.roundToInt()
                            params.y += dy.roundToInt()
                            try {
                                windowManager.updateViewLayout(this, params)
                            } catch (_: Exception) { }
                        },
                        onFinish = {
                            hideHUD(packageName)
                            onSessionEnd()
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
            activeHUDs.add(HUDInstance(packageName, composeView, lOwner, vStore, isVisibleState))
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideHUD(packageName: String? = null) {
        val iterator = activeHUDs.iterator()
        while (iterator.hasNext()) {
            val hud = iterator.next()
            if (packageName == null || hud.packageName == packageName) {
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
                iterator.remove()
                if (packageName != null) break
            }
        }
    }

    fun updateForegroundApp(packageName: String) {
        activeHUDs.forEach { hud ->
            val isCurrentlyVisible = hud.isVisibleState.value
            val shouldBeVisible = packageName == hud.packageName || packageName.startsWith("${hud.packageName}.")

            if (isCurrentlyVisible != shouldBeVisible) {
                hud.isVisibleState.value = shouldBeVisible

                // Perbarui WindowManager flags dan ukuran untuk mencegah gangguan hitbox
                val view = hud.overlayView
                val params = view.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    if (shouldBeVisible) {
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        params.width = WindowManager.LayoutParams.WRAP_CONTENT
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT
                    } else {
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        // Kecilkan ukuran ke 1x1 agar benar-benar tidak menghalangi sentuhan
                        params.width = 1
                        params.height = 1
                    }
                    try {
                        windowManager.updateViewLayout(view, params)
                    } catch (_: Exception) {}
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
    durationMinutes: Int,
    size: Int,
    opacity: Int,
    isVisible: State<Boolean>,
    onDrag: (Float, Float) -> Unit,
    onFinish: () -> Unit
) {
    val totalSeconds = durationMinutes * 60
    var secondsLeft by remember { mutableIntStateOf(totalSeconds) }
    var animatingOut by remember { mutableStateOf(false) }
    var entranceAnimationStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entranceAnimationStarted = true
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        animatingOut = true
    }

    val progress = secondsLeft.toFloat() / totalSeconds.toFloat()
    val scaleFactor = size / 100f
    
    val showHUD = isVisible.value && !animatingOut && entranceAnimationStarted

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

    Box(
        modifier = Modifier
            .wrapContentSize()
            .then(
                if (showHUD && scale > 0.1f) {
                    Modifier.pointerInput(scaleFactor) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            // Apply scale factor to drag amount to keep HUD pinned to finger
                            onDrag(dragAmount.x * scale, dragAmount.y * scale)
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = (scale / scaleFactor).coerceIn(0f, 1f) * (opacity / 100f)
                    
                    // Penting: Pastikan transformasi terjadi dari titik tengah
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                },
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
