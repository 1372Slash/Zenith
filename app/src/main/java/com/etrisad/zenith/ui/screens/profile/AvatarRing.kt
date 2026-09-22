package com.etrisad.zenith.ui.screens.profile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

/**
 * Avatar ring with optional pinnacle effects. NONE draws a plain ring,
 * SPIN slowly rotates the sweep gradient around the ring, SHINE sweeps a
 * highlight arc over a solid ring. A null border falls back to [fallback].
 */
fun Modifier.avatarRing(
    border: AvatarBorder?,
    width: Dp,
    fallback: Brush
): Modifier = composed {
    if (border == null) {
        return@composed then(Modifier.border(width, fallback, CircleShape))
    }
    when (border.effect) {
        BorderEffect.NONE -> then(Modifier.border(width, borderBrushFor(border), CircleShape))
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
                    val stroke = width.toPx()
                    val radius = (size.minDimension - stroke) / 2f
                    rotate(angle) {
                        drawCircle(
                            brush = borderBrushFor(border),
                            radius = radius,
                            center = center,
                            style = Stroke(stroke)
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
                    .border(width, borderBrushFor(border), CircleShape)
                    .drawWithContent {
                        drawContent()
                        val stroke = width.toPx()
                        val radius = (size.minDimension - stroke) / 2f
                        rotate(angle) {
                            drawArc(
                                color = Color.White.copy(alpha = 0.85f),
                                startAngle = -35f,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = center - Offset(radius, radius),
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                    }
            )
        }
    }
}
