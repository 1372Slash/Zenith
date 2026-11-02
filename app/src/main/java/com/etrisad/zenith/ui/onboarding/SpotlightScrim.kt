package com.etrisad.zenith.ui.onboarding

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

data class SpotlightGeometry(
    val rect: Rect,
    val cornerRadius: Float
) {
    val center: Offset get() = rect.center
    val isEmpty: Boolean get() = rect.width <= 0f || rect.height <= 0f
}

fun Rect.toSpotlightGeometry(
    density: Density,
    container: Size? = null,
    padding: Dp = GuideTokens.HolePadding,
    minDiameter: Dp = GuideTokens.MinHoleDiameter,
    maxHeightFraction: Float = GuideTokens.HoleMaxHeightFraction,
    maxWidthFraction: Float = GuideTokens.HoleMaxWidthFraction
): SpotlightGeometry {
    val pad = with(density) { padding.toPx() }
    val minSize = with(density) { minDiameter.toPx() }
    val margin = with(density) { GuideTokens.HoleViewportMargin.toPx() }

    var widthPx = (width + pad * 2f).coerceAtLeast(minSize)
    var heightPx = (height + pad * 2f).coerceAtLeast(minSize)

    if (container != null && container.width > 0f && container.height > 0f) {
        val maxWidth = (container.width * maxWidthFraction).coerceAtLeast(minSize)
        val maxHeight = (container.height * maxHeightFraction).coerceAtLeast(minSize)
        widthPx = widthPx.coerceAtMost(maxWidth)
        heightPx = heightPx.coerceAtMost(maxHeight)
    }

    val centerX = if (container != null && container.width > 0f) {
        val visibleLeft = left.coerceIn(0f, container.width)
        val visibleRight = right.coerceIn(0f, container.width)
        if (visibleRight > visibleLeft) (visibleLeft + visibleRight) / 2f else center.x
    } else {
        center.x
    }
    val centerY = if (container != null && container.height > 0f) {
        val visibleTop = top.coerceIn(0f, container.height)
        val visibleBottom = bottom.coerceIn(0f, container.height)
        if (visibleBottom > visibleTop) (visibleTop + visibleBottom) / 2f else center.y
    } else {
        center.y
    }
    var left = centerX - widthPx / 2f
    var top = centerY - heightPx / 2f

    if (container != null && container.height > 0f) {
        val visibleTop = this.top.coerceIn(0f, container.height)
        val visibleBottom = bottom.coerceIn(0f, container.height)
        if (visibleBottom - visibleTop > heightPx) {
            top = (visibleTop + pad).coerceAtMost(
                (container.height - heightPx - margin).coerceAtLeast(margin)
            )
        }
    }

    if (container != null && container.width > 0f && container.height > 0f) {
        left = left.coerceIn(
            minimumValue = margin,
            maximumValue = (container.width - widthPx - margin).coerceAtLeast(margin)
        )
        top = top.coerceIn(
            minimumValue = margin,
            maximumValue = (container.height - heightPx - margin).coerceAtLeast(margin)
        )
    }

    val shortSide = min(widthPx, heightPx)
    val shortSideDp = shortSide / density.density.coerceAtLeast(0.01f)
    val cornerRadius = if (shortSideDp <= GuideTokens.PillMaxShortSideDp) {
        shortSide / 2f
    } else {
        min(shortSide / 2f, with(density) { GuideTokens.RectCornerDp.dp.toPx() })
    }

    return SpotlightGeometry(
        rect = Rect(left, top, left + widthPx, top + heightPx),
        cornerRadius = cornerRadius
    )
}

internal fun areSystemAnimatorsEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.animation.ValueAnimator.areAnimatorsEnabled()
    } else {
        true
    }

@Composable
fun SpotlightScrim(
    hole: (() -> SpotlightGeometry)?,
    modifier: Modifier = Modifier,
    dimAlpha: Float = GuideTokens.SCRIM_ALPHA,
    showRing: Boolean = true
) {
    val dim by animateFloatAsState(
        targetValue = dimAlpha,
        animationSpec = tween(220),
        label = "guideScrimDim"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (showRing) 1f else 0f,
        animationSpec = tween(if (showRing) 260 else 140),
        label = "guideRingAlpha"
    )
    val pulseState: State<Float> = if (showRing && areSystemAnimatorsEnabled()) {
        rememberInfiniteTransition(label = "guideRingPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "guideRingPulseOffset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = dim)
    val ring = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f * ringAlpha)
    val halo = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f * ringAlpha)

    Canvas(modifier.fillMaxSize()) {
        val geometry = hole?.invoke()?.takeIf { !it.isEmpty }
        val cutout = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            if (geometry != null) {
                addRoundRect(
                    RoundRect(
                        rect = geometry.rect,
                        cornerRadius = CornerRadius(geometry.cornerRadius)
                    )
                )
            }
        }
        drawPath(path = cutout, color = scrim)

        if (ringAlpha > 0.01f && geometry != null) {
            val pulseOffset = pulseState.value * 2.dp.toPx()
            val haloInset = 4.dp.toPx() + pulseOffset
            val ringInset = 1.dp.toPx() + pulseOffset
            val haloRect = Rect(
                left = geometry.rect.left - haloInset,
                top = geometry.rect.top - haloInset,
                right = geometry.rect.right + haloInset,
                bottom = geometry.rect.bottom + haloInset
            )
            drawRoundRect(
                color = halo,
                topLeft = haloRect.topLeft,
                size = haloRect.size,
                cornerRadius = CornerRadius(geometry.cornerRadius + haloInset),
                style = Stroke(width = 8.dp.toPx())
            )
            val ringRect = Rect(
                left = geometry.rect.left - ringInset,
                top = geometry.rect.top - ringInset,
                right = geometry.rect.right + ringInset,
                bottom = geometry.rect.bottom + ringInset
            )
            drawRoundRect(
                color = ring,
                topLeft = ringRect.topLeft,
                size = ringRect.size,
                cornerRadius = CornerRadius(geometry.cornerRadius + ringInset),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
