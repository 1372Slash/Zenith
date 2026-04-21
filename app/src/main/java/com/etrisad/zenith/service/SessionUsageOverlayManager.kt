package com.etrisad.zenith.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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

    fun showHUD(durationMinutes: Int, size: Int) {
        if (overlayView != null) {
            hideHUD()
        }

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
    onDrag: (Float, Float) -> Unit,
    onFinish: () -> Unit
) {
    val totalSeconds = durationMinutes * 60
    var secondsLeft by remember { mutableIntStateOf(totalSeconds) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        visible = false
    }

    val progress = secondsLeft.toFloat() / totalSeconds.toFloat()
    val scaleFactor = size / 100f

    val scale by animateFloatAsState(
        targetValue = if (visible) scaleFactor else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "HUDScale",
        finishedListener = {
            if (!visible) {
                onFinish()
            }
        }
    )

    Surface(
        modifier = Modifier
            .size(80.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale.coerceIn(0f, 1f)
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
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
