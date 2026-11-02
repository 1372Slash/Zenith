package com.etrisad.zenith.ui.screens.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

fun Modifier.avatarRing(
    border: AvatarBorder?,
    width: Dp,
    fallback: Brush,
    animate: Boolean = true,
    shape: Shape = CircleShape
): Modifier = composed {
    if (border == null) {
        return@composed then(Modifier.border(width, fallback, shape))
    }
    if (!animate || border.effect == BorderEffect.NONE) {
        return@composed then(Modifier.border(width, borderBrushFor(border), shape))
    }
    when (border.effect) {
        BorderEffect.NONE -> then(Modifier.border(width, borderBrushFor(border), shape))
        BorderEffect.SPIN -> {
            val transition = rememberInfiniteTransition(label = "ringSpin")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 6000, easing = LinearEasing)
                ),
                label = "ringAngle"
            )
            then(
                Modifier.drawWithContent {
                    drawContent()
                    val outline = shape.createOutline(size, layoutDirection, this)
                    rotate(angle) {
                        drawShapeOutline(
                            outline = outline,
                            brush = borderBrushFor(border),
                            style = Stroke(width.toPx())
                        )
                    }
                }
            )
        }
        BorderEffect.SHINE -> {
            val transition = rememberInfiniteTransition(label = "ringShine")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3500, easing = LinearEasing)
                ),
                label = "shineAngle"
            )
            then(
                Modifier
                    .border(width, borderBrushFor(border), shape)
                    .drawWithContent {
                        drawContent()
                        val outline = shape.createOutline(size, layoutDirection, this)
                        val stroke = width.toPx()
                        val shineBrush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.1f to Color.White.copy(alpha = 0.85f),
                            0.2f to Color.Transparent,
                            1.0f to Color.Transparent
                        )
                        rotate(angle) {
                            drawShapeOutline(
                                outline = outline,
                                brush = shineBrush,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
            )
        }
    }
}

private fun DrawScope.drawShapeOutline(
    outline: Outline,
    brush: Brush,
    style: Stroke
) {
    when (outline) {
        is Outline.Rectangle -> drawRect(brush, outline.rect.topLeft, outline.rect.size, style = style)
        is Outline.Rounded -> {
            val path = Path().apply { addRoundRect(outline.roundRect) }
            drawPath(path, brush, style = style)
        }
        is Outline.Generic -> drawPath(outline.path, brush, style = style)
    }
}
