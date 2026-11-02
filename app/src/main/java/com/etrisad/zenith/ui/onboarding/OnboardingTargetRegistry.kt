package com.etrisad.zenith.ui.onboarding

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

data class TargetBounds(
    val bounds: Rect,
    val requester: BringIntoViewRequester
)

class OnboardingTargetRegistry {
    private val targets = mutableStateMapOf<String, TargetBounds>()

    fun register(id: String, bounds: Rect, requester: BringIntoViewRequester) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        val previous = targets[id]
        if (previous != null && nearlyEqual(previous.bounds, bounds)) return
        targets[id] = TargetBounds(bounds, requester)
    }

    fun unregister(id: String, requester: BringIntoViewRequester? = null) {
        val current = targets[id] ?: return
        if (requester == null || current.requester === requester) {
            targets.remove(id)
        }
    }

    fun bounds(id: String?): Rect? = id?.let { targets[it]?.bounds }

    fun requester(id: String?): BringIntoViewRequester? = id?.let { targets[it]?.requester }

    suspend fun await(id: String, timeoutMs: Long = 1_500): Rect? =
        withTimeoutOrNull(timeoutMs) {
            snapshotFlow { targets[id]?.bounds }.filterNotNull().first()
        }

    private fun nearlyEqual(a: Rect, b: Rect): Boolean =
        abs(a.left - b.left) < 1f && abs(a.top - b.top) < 1f &&
            abs(a.right - b.right) < 1f && abs(a.bottom - b.bottom) < 1f
}

val LocalOnboardingTargets = compositionLocalOf { OnboardingTargetRegistry() }

@Composable
fun androidx.compose.ui.Modifier.onboardingAnchor(id: String): androidx.compose.ui.Modifier {
    val registry = LocalOnboardingTargets.current
    val requester = remember(id) { BringIntoViewRequester() }

    DisposableEffect(id, registry) {
        onDispose { registry.unregister(id, requester) }
    }

    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { coordinates ->
            registry.register(id, coordinates.unclippedWindowBounds(), requester)
        }
}

private fun LayoutCoordinates.unclippedWindowBounds(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    return Rect(
        left = topLeft.x,
        top = topLeft.y,
        right = topLeft.x + size.width,
        bottom = topLeft.y + size.height
    )
}

class OnboardingActionHub {
    private val handlers = mutableMapOf<GuideAction, () -> Unit>()

    fun register(action: GuideAction, handler: () -> Unit) {
        handlers[action] = handler
    }

    fun unregister(action: GuideAction) {
        handlers.remove(action)
    }

    fun perform(action: GuideAction) {
        handlers[action]?.invoke()
    }
}

val LocalOnboardingActions = compositionLocalOf { OnboardingActionHub() }

class OnboardingScrollHub {
    private val handlers = mutableStateMapOf<String, suspend () -> Unit>()

    fun register(key: String, handler: suspend () -> Unit) {
        handlers[key] = handler
    }

    fun unregister(key: String) {
        handlers.remove(key)
    }

    suspend fun scrollTo(key: String?, timeoutMs: Long = 1_500): Boolean {
        if (key.isNullOrBlank()) return false
        val handler = handlers[key] ?: withTimeoutOrNull(timeoutMs) {
            snapshotFlow { handlers[key] }.filterNotNull().first()
        } ?: return false
        handler.invoke()
        return true
    }
}

val LocalOnboardingScroll = compositionLocalOf { OnboardingScrollHub() }

internal object GuideTokens {
    const val SCRIM_ALPHA = 0.78f
    val HolePadding = 10.dp

    val MinHoleDiameter = 64.dp

    const val HoleMaxHeightFraction = 0.58f
    const val HoleMaxWidthFraction = 0.96f

    const val PillMaxShortSideDp = 120f

    const val RectCornerDp = 18f

    val HoleViewportMargin = 8.dp

    val AnchorTopInset = 96.dp
    val AnchorBottomInset = 24.dp
    val TooltipMinWidth = 280.dp
    val TooltipMaxWidth = 340.dp
    val TooltipMaxWidthExpanded = 360.dp
    val TooltipGap = 16.dp
    val ScreenMargin = 16.dp
}
