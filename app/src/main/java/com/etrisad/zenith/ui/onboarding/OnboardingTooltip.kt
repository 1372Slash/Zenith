package com.etrisad.zenith.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.R
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlin.math.roundToInt

enum class TooltipSide { Below, Above }

private data class StepCopy(val title: String, val body: String)

data class GuideInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

data class TooltipPlacement(
    val offset: IntOffset,
    val side: TooltipSide,
    val arrowX: Float
)

fun Density.placeTooltip(
    anchor: Rect,
    tooltip: IntSize,
    container: IntSize,
    insets: GuideInsets,
    marginPx: Float,
    gapPx: Float
): TooltipPlacement {
    val spaceBelow = container.height - insets.bottom - anchor.bottom - gapPx
    val spaceAbove = anchor.top - insets.top - gapPx
    val below = when {
        spaceBelow >= tooltip.height -> true
        spaceAbove >= tooltip.height -> false
        else -> spaceBelow >= spaceAbove
    }

    val rawY = if (below) anchor.bottom + gapPx else anchor.top - gapPx - tooltip.height
    val y = rawY.coerceIn(
        insets.top + marginPx,
        (container.height - insets.bottom - marginPx - tooltip.height).coerceAtLeast(insets.top + marginPx)
    )

    val rawX = anchor.center.x - tooltip.width / 2f
    val x = rawX.coerceIn(
        insets.left + marginPx,
        (container.width - insets.right - marginPx - tooltip.width)
            .coerceAtLeast(insets.left + marginPx)
    )

    val arrowInset = 24.dp.roundToPx().toFloat()
    val arrowX = (anchor.center.x - x).coerceIn(
        arrowInset,
        (tooltip.width - arrowInset).coerceAtLeast(arrowInset)
    )
    return TooltipPlacement(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        side = if (below) TooltipSide.Below else TooltipSide.Above,
        arrowX = arrowX
    )
}

@Composable
fun OnboardingTooltip(
    stepLabel: String,
    title: String,
    body: String,
    progress: Float,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onBack: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onClose: () -> Unit,
    onActivateTarget: (() -> Unit)? = null,
    activateTargetLabel: String? = null,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 360.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "guideProgress"
    )

    val paneLabel = stringResource(R.string.onboarding_open_path)
    val closeLabel = stringResource(R.string.onboarding_close)
    val backLabel = stringResource(R.string.onboarding_back)
    val skipLabel = stringResource(R.string.onboarding_skip_chapter)

    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                paneTitle = paneLabel
                val activate = onActivateTarget
                val activateLabel = activateTargetLabel
                if (activate != null && activateLabel != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(activateLabel) {
                            activate()
                            true
                        }
                    )
                }
            }
            .heightIn(max = maxHeight),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = stepLabel,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                    },
                    label = "guideStepLabel",
                    modifier = Modifier.weight(1f)
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = closeLabel,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = StepCopy(title = title, body = body),
                transitionSpec = {
                    val enter = fadeIn(tween(220)) +
                        slideInVertically(tween(260)) { height -> height / 12 }
                    val exit = fadeOut(tween(140)) +
                        slideOutVertically(tween(160)) { height -> -height / 16 }
                    enter togetherWith exit
                },
                label = "guideStepCopy",
                modifier = Modifier.weight(1f, fill = false)
            ) { copy ->
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = onBack != null,
                    enter = fadeIn(tween(160)) + expandHorizontally(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160))
                ) {
                    val back = onBack
                    if (back != null) {
                        TextButton(
                            onClick = back,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(backLabel) }
                    }
                }
                if (onSkip != null) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(skipLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                ZenithButton(
                    onClick = onPrimary,
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Large,
                    isDisableExpand = true,
                    content = {
                        AnimatedContent(
                            targetState = primaryLabel,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                            label = "guidePrimaryLabel"
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TooltipArrow(
    side: TooltipSide,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    Canvas(modifier.size(width = 16.dp, height = 8.dp)) {
        val path = Path().apply {
            if (side == TooltipSide.Below) {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
            }
            close()
        }
        drawPath(path, color = color, style = Fill)
    }
}
