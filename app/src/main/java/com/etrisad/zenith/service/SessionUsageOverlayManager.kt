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
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var viewModelStore: ViewModelStore? = null

    private var targetPackageName: String? = null
    private var isVisibleState = mutableStateOf(false)

    fun showHUD(packageName: String, durationMinutes: Int, size: Int, opacity: Int) {
        if (overlayView != null) {
            hideHUD()
        }
        
        targetPackageName = packageName
        isVisibleState.value = true

        val vStore = ViewModelStore()
        viewModelStore = vStore

        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

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
            y = 200
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
                            hideHUD()
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
            overlayView = composeView
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideHUD() {
        overlayView?.let {
            try {
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                it.disposeComposition()
                windowManager.removeViewImmediate(it)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                overlayView = null
                lifecycleOwner = null
                viewModelStore = null
                targetPackageName = null
                isVisibleState.value = false
            }
        }
    }

    fun updateForegroundApp(packageName: String) {
        val target = targetPackageName ?: return
        // Cek jika app yang sedang dibuka mengandung target package (misal: "com.instagram.android")
        // atau jika target package mengandung app yang sedang dibuka.
        isVisibleState.value = packageName.contains(target) || target.contains(packageName)
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
        modifier = Modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = (scale / scaleFactor).coerceIn(0f, 1f) * (opacity / 100f)
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
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
