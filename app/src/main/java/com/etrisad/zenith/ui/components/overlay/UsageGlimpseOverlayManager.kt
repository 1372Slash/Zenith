package com.etrisad.zenith.ui.components.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.etrisad.zenith.ui.theme.GSFlexSettings
import com.etrisad.zenith.ui.theme.ZenithTheme
import java.util.Locale

class UsageGlimpseOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var viewModelStore: ViewModelStore? = null
    private var hidingRequested: MutableState<Boolean>? = null

    @Volatile
    var isShowing = false

    fun show(
        usageTodayMillis: Long,
        isDark: Boolean,
        fontOption: FontOption = FontOption.SYSTEM,
        dynamicColor: Boolean = true,
        expressiveColors: Boolean = false,
        gsFlexSettings: GSFlexSettings = GSFlexSettings(),
        progressFraction: Float = 1f
    ) {
        if (isShowing) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                show(usageTodayMillis, isDark, fontOption, dynamicColor, expressiveColors, gsFlexSettings, progressFraction)
            }
            return
        }

        val vStore = ViewModelStore()
        viewModelStore = vStore

        val lOwner = MyLifecycleOwner()
        lOwner.performRestore(null)
        lOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner = lOwner

        val formattedTime = formatUsageTime(usageTodayMillis)
        val hideRequest = mutableStateOf(false)
        hidingRequested = hideRequest

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lOwner)
            setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vStore
            })
            setViewTreeSavedStateRegistryOwner(lOwner)

            setContent {
                ZenithTheme(
                    darkTheme = isDark,
                    fontOption = fontOption,
                    dynamicColor = dynamicColor,
                    expressiveColors = expressiveColors,
                    gsFlexSettings = gsFlexSettings
                ) {
                    UsageGlimpseHUD(
                        formattedTime = formattedTime,
                        progressFraction = progressFraction,
                        hidingRequested = hideRequest,
                        onHidden = { hide() }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 204
        }

        try {
            windowManager.addView(composeView, params)
            overlayView = composeView
            isShowing = true
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }

        val hideReq = hidingRequested ?: run {
            destroyView()
            return
        }
        if (hideReq.value) {
            destroyView()
            return
        }
        hideReq.value = true
    }

    private fun destroyView() {
        hidingRequested = null
        val view = overlayView
        val lOwner = lifecycleOwner
        val vStore = viewModelStore

        overlayView = null
        lifecycleOwner = null
        viewModelStore = null
        isShowing = false

        if (view == null) return

        try {
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            view.disposeComposition()
            windowManager.removeViewImmediate(view)
            vStore?.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatUsageTime(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "${minutes}m"
        }
    }

    private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UsageGlimpseHUD(
    formattedTime: String,
    progressFraction: Float = 1f,
    hidingRequested: MutableState<Boolean>? = null,
    onHidden: () -> Unit = {}
) {
    val showState = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showState.value = true }

    LaunchedEffect(hidingRequested?.value) {
        if (hidingRequested?.value == true) showState.value = false
    }

    val scaleState = animateFloatAsState(
        targetValue = if (showState.value) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "GlimpseScale",
        finishedListener = {
            if (!showState.value) onHidden()
        }
    )

    val colorScheme = MaterialTheme.colorScheme
    val hudColors = remember(colorScheme) {
        GlimpseHUDColors(
            surface = colorScheme.surface,
            tertiary = colorScheme.tertiary,
            onSurface = colorScheme.onSurface,
            track = colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }

    val baseSize = 80.dp
    val animationBuffer = 24.dp

    Box(
        modifier = Modifier.size((baseSize + animationBuffer)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .requiredSize(baseSize + animationBuffer)
                .graphicsLayer {
                    val scale = scaleState.value
                    scaleX = scale
                    scaleY = scale
                    alpha = scale.coerceIn(0f, 1f)
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
                CircularWavyProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    color = hudColors.tertiary,
                    trackColor = hudColors.track,
                    amplitude = { 1f },
                    wavelength = 20.dp,
                    waveSpeed = 0.dp,
                    stroke = Stroke(width = 4.dp.value),
                    trackStroke = Stroke(width = 4.dp.value)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = hudColors.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleSmall,
                        color = hudColors.onSurface,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Immutable
private data class GlimpseHUDColors(
    val surface: Color,
    val tertiary: Color,
    val onSurface: Color,
    val track: Color
)
