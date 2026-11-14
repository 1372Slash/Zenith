package com.etrisad.zenith.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.util.StreakBonus
import kotlin.math.roundToInt

private val STAGE_SEGMENTS = intArrayOf(3, 5, 8, 14)

private val STAGE_CHECKPOINTS = floatArrayOf(3f, 8f, 16f, 30f)

private const val SEGMENT_ANIMATION_MILLIS = 420

private val SEGMENT_GAP = 3.dp
private val OUTER_SEGMENT_CORNER = 5.dp
private val INNER_SEGMENT_CORNER = 4.dp

private val LitSegmentShape: Shape = RoundedCornerShape(percent = 50)

private val MAX_SEGMENTS = STAGE_SEGMENTS.max()

@Composable
fun StreakGauge(
    streakDays: Int,
    modifier: Modifier = Modifier,
    capDays: Int = StreakBonus.CAP_DAYS,
    height: Dp = 12.dp
) {
    val cap = capDays.coerceAtLeast(1)
    val days = streakDays.coerceIn(0, cap)
    val stage = remember(days, cap) { stageFor(days, cap) }
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val colorSpan = (cap - 1).coerceAtLeast(1)
    val slotPadding = SEGMENT_GAP / 2

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .progressSemantics(days.toFloat(), 0f..cap.toFloat())
    ) {
        repeat(MAX_SEGMENTS) { index ->
            val slotWeight by animateFloatAsState(
                targetValue = if (index < stage.segments) 1f else 0f,
                animationSpec = tween(SEGMENT_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
                label = "streakSegmentWeight"
            )
            val lit = index < stage.filled
            val targetColor = if (lit) {
                lerp(
                    primary,
                    tertiary,
                    ((stage.startDay + index).toFloat() / colorSpan).coerceIn(0f, 1f)
                )
            } else {
                emptyColor
            }
            val color by animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(SEGMENT_ANIMATION_MILLIS),
                label = "streakSegmentColor"
            )

            if (slotWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .weight(slotWeight)
                        .fillMaxHeight()
                        .padding(horizontal = slotPadding)
                        .clip(if (lit) LitSegmentShape else groupedSegmentShape(index, stage.segments))
                        .background(color)
                )
            }
        }
    }
}

private data class GaugeStage(
    val segments: Int,
    val filled: Int,
    val startDay: Int
)

private fun groupedSegmentShape(index: Int, segments: Int): Shape {
    val start = if (index == 0) OUTER_SEGMENT_CORNER else INNER_SEGMENT_CORNER
    val end = if (index == segments - 1) OUTER_SEGMENT_CORNER else INNER_SEGMENT_CORNER
    return RoundedCornerShape(
        topStart = start,
        bottomStart = start,
        topEnd = end,
        bottomEnd = end
    )
}

private fun stageFor(days: Int, cap: Int): GaugeStage {
    var startDay = 0
    for (index in STAGE_SEGMENTS.indices) {
        val end = if (index == STAGE_SEGMENTS.lastIndex) {
            cap
        } else {
            (STAGE_CHECKPOINTS[index] / STAGE_CHECKPOINTS.last() * cap)
                .roundToInt()
                .coerceIn(startDay + 1, cap)
        }
        if (days <= end) {
            return GaugeStage(
                segments = STAGE_SEGMENTS[index],
                filled = (days - startDay).coerceIn(0, STAGE_SEGMENTS[index]),
                startDay = startDay
            )
        }
        startDay = end
    }
    return GaugeStage(STAGE_SEGMENTS.first(), 0, 0)
}
