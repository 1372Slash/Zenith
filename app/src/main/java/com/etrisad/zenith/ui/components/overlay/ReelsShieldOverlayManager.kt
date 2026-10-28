package com.etrisad.zenith.ui.components.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.etrisad.zenith.data.preferences.FontOption
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.theme.GSFlexSettings
import com.etrisad.zenith.ui.theme.ZenithTheme
import kotlin.math.roundToInt

/**
 * Floating pill HUD for the Reels Shield.
 *
 * Shows the shared daily pool on the left and the current session's swipe
 * count on the right. The window is attached only while a tracked short-video
 * surface is on screen and is removed right after it goes away, so an idle
 * device holds no overlay window and runs no recomposition.
 *
 * Theme values are copied out of the preference snapshot instead of collecting
 * the whole DataStore flow here. Counter writes therefore never recompose the
 * pill for unrelated preference fields.
 */
class ReelsShieldOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isAccessibilityOverlay = context is AccessibilityService
    private val verticalOffsetPx: Int by lazy {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (resourceId > 0) {
            try {
                context.resources.getDimensionPixelSize(resourceId)
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
        statusBar + (PILL_TOP_MARGIN_DP * context.resources.displayMetrics.density).roundToInt()
    }

    private val poolCount = mutableIntStateOf(0)
    private val sessionCount = mutableIntStateOf(0)
    private val remainingCount = mutableIntStateOf(-1)
    private val dailyLimit = mutableIntStateOf(0)
    private val appLabel = mutableStateOf("")
    private val limitReached = mutableStateOf(false)
    private val pillVisible = mutableStateOf(false)
    private val theme = mutableStateOf(PillTheme())

    private var composeView: ComposeView? = null
    private var lifecycleOwner: PillLifecycleOwner? = null
    private var viewModelStore: ViewModelStore? = null
    private var detachRunnable: Runnable? = null

    val isShowing: Boolean get() = composeView != null

    /** Only theme fields, filtered so counter writes never change the theme state. */
    fun updateTheme(prefs: UserPreferences) {
        val next = PillTheme(
            fontOption = prefs.fontOption,
            dynamicColor = prefs.dynamicColor,
            expressiveColors = prefs.expressiveColors,
            gsFlexSettings = prefs.gsFlexSettings
        )
        runOnMain {
            if (theme.value != next) theme.value = next
        }
    }

    /**
     * Refreshes the counters and makes the pill visible. Safe from any thread;
     * the caller is the accessibility event path, which is already main.
     */
    fun show(
        pool: Int,
        limit: Int,
        session: Int,
        remaining: Int,
        label: String,
        atLimit: Boolean
    ) {
        runOnMain {
            poolCount.intValue = pool
            sessionCount.intValue = session
            remainingCount.intValue = remaining
            dailyLimit.intValue = limit
            appLabel.value = label
            limitReached.value = atLimit
            cancelDetach()

            if (composeView == null) {
                // Compose the pill hidden first so the entry animation has a
                // real starting frame instead of appearing fully drawn.
                pillVisible.value = false
                attach()
                mainHandler.post {
                    if (composeView != null) pillVisible.value = true
                }
            } else {
                pillVisible.value = true
            }
        }
    }

    /** Fades the pill out and removes the window; used when leaving the feed. */
    fun hideNow() {
        runOnMain {
            if (composeView == null) return@runOnMain
            pillVisible.value = false
            scheduleDetach(EXIT_ANIMATION_MS + 40L)
        }
    }

    fun destroy() {
        runOnMain {
            cancelDetach()
            pillVisible.value = false
            detach()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun scheduleDetach(delayMs: Long) {
        cancelDetach()
        val runnable = Runnable {
            detachRunnable = null
            detach()
        }
        detachRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelDetach() {
        detachRunnable?.let { mainHandler.removeCallbacks(it) }
        detachRunnable = null
    }

    private fun attach() {
        val store = ViewModelStore()
        val owner = PillLifecycleOwner()
        owner.performRestore(null)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (isAccessibilityOverlay) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = verticalOffsetPx
        }

        val view = ComposeView(context).apply {
            setContent {
                val pillTheme = theme.value
                ZenithTheme(
                    fontOption = pillTheme.fontOption,
                    dynamicColor = pillTheme.dynamicColor,
                    expressiveColors = pillTheme.expressiveColors,
                    gsFlexSettings = pillTheme.gsFlexSettings
                ) {
                    ReelsShieldPill(
                        pool = poolCount.intValue,
                        limit = dailyLimit.intValue,
                        session = sessionCount.intValue,
                        remaining = remainingCount.intValue,
                        label = appLabel.value,
                        atLimit = limitReached.value,
                        visible = pillVisible.value
                    )
                }
            }
        }

        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeViewModelStoreOwner(object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        })
        view.setViewTreeSavedStateRegistryOwner(owner)

        try {
            windowManager.addView(view, params)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            composeView = view
            lifecycleOwner = owner
            viewModelStore = store
        } catch (_: Exception) {
            // Accessibility overlays do not need the draw-over-apps
            // permission, but some OEMs reject the type. Try the regular
            // overlay type once before giving up.
            if (!isAccessibilityOverlay) {
                cleanupFailedAttach(view, owner, store)
                return
            }
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            try {
                windowManager.addView(view, params)
                owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
                owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                composeView = view
                lifecycleOwner = owner
                viewModelStore = store
            } catch (_: Exception) {
                cleanupFailedAttach(view, owner, store)
            }
        }
    }

    private fun cleanupFailedAttach(
        view: ComposeView,
        owner: PillLifecycleOwner,
        store: ViewModelStore
    ) {
        composeView = null
        lifecycleOwner = null
        viewModelStore = null
        pillVisible.value = false
        try {
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            view.disposeComposition()
            store.clear()
        } catch (_: Exception) {
        }
    }

    private fun detach() {
        val view = composeView ?: return
        val owner = lifecycleOwner
        val store = viewModelStore
        composeView = null
        lifecycleOwner = null
        viewModelStore = null
        try {
            owner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            view.disposeComposition()
            windowManager.removeViewImmediate(view)
            store?.clear()
        } catch (_: Exception) {
        }
    }

    private class PillLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
        fun handleLifecycleEvent(event: Lifecycle.Event) = lifecycleRegistry.handleLifecycleEvent(event)
        fun performRestore(savedState: Bundle?) = savedStateRegistryController.performRestore(savedState)
    }

    companion object {
        private const val PILL_TOP_MARGIN_DP = 10
        private const val EXIT_ANIMATION_MS = 180L
    }
}

@Immutable
private data class PillTheme(
    val fontOption: FontOption = FontOption.SYSTEM,
    val dynamicColor: Boolean = true,
    val expressiveColors: Boolean = false,
    val gsFlexSettings: GSFlexSettings = GSFlexSettings()
)

/**
 * Grouped HUD with neutral surfaces: two cards, a 2 dp gap, large outer
 * corners and tight facing corners. The left card shows the shared daily pool.
 * The right card shows how many reels were swiped in the current session plus
 * the remaining reels before Zenith presses back, so the next swipe is never a
 * surprise. No progress strips are drawn.
 */
@Composable
private fun ReelsShieldPill(
    pool: Int,
    limit: Int,
    session: Int,
    remaining: Int,
    label: String,
    atLimit: Boolean,
    visible: Boolean
) {
    val alpha = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "ReelsPillAlpha"
    )
    val scale = animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ReelsPillScale"
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(pool, session) {
        if (pool > 0 || session > 0) {
            pulse.snapTo(1.04f)
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val scheme = MaterialTheme.colorScheme
    val outerRadius = 24.dp
    val innerRadius = 4.dp
    val poolShape = RoundedCornerShape(
        topStart = outerRadius,
        bottomStart = outerRadius,
        topEnd = innerRadius,
        bottomEnd = innerRadius
    )
    val sessionShape = RoundedCornerShape(
        topStart = innerRadius,
        bottomStart = innerRadius,
        topEnd = outerRadius,
        bottomEnd = outerRadius
    )

    // Neutral surfaces only: no primary or tertiary fills, even when expressive
    // colours are enabled. The limit state is the sole exception and uses the
    // error role for the lock and the "0 left" text.
    val container = scheme.surfaceContainerHigh
    val content = scheme.onSurface
    val caption = scheme.onSurfaceVariant
    val badge = scheme.onSurface.copy(alpha = 0.08f)
    val chipContainer = scheme.surfaceContainerHighest
    val remainingLabel = if (remaining < 0) "∞ left" else "$remaining left"
    val sessionCaption = if (atLimit) "Limit reached" else label.ifBlank { "This session" }
    val sessionAccent = if (atLimit) scheme.error else content

    Row(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            val animatedScale = scale.value * pulse.value
            scaleX = animatedScale
            scaleY = animatedScale
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillSegment(
            shape = poolShape,
            container = container,
            content = content,
            captionColor = caption,
            badgeColor = badge,
            icon = Icons.Outlined.SwapVert,
            iconTint = content,
            value = if (limit > 0) "$pool/$limit" else "$pool",
            caption = "Pool",
            chipText = null,
            chipColor = chipContainer,
            chipContentColor = content
        )
        PillSegment(
            shape = sessionShape,
            container = container,
            content = content,
            captionColor = if (atLimit) sessionAccent else caption,
            badgeColor = badge,
            icon = if (atLimit) Icons.Outlined.Lock else Icons.Outlined.PlayCircle,
            iconTint = sessionAccent,
            value = "$session",
            caption = sessionCaption,
            chipText = remainingLabel,
            chipColor = if (atLimit) scheme.error.copy(alpha = 0.12f) else chipContainer,
            chipContentColor = sessionAccent
        )
    }
}

@Composable
private fun PillSegment(
    shape: Shape,
    container: Color,
    content: Color,
    captionColor: Color,
    badgeColor: Color,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    caption: String,
    chipText: String?,
    chipColor: Color,
    chipContentColor: Color
) {
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(
                start = 11.dp,
                top = 8.dp,
                end = 12.dp,
                bottom = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(7.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = content,
                        maxLines = 1
                    )
                    if (chipText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = chipColor
                        ) {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = chipContentColor,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = captionColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp)
                )
            }
        }
    }
}
