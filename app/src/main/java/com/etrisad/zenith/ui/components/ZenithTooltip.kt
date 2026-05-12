package com.etrisad.zenith.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TooltipArrowPosition {
    TopStart, TopCenter, TopEnd,
    BottomStart, BottomCenter, BottomEnd,
    StartCenter, EndCenter
}

@Composable
fun ZenithTooltip(
    text: String,
    modifier: Modifier = Modifier,
    arrowPosition: TooltipArrowPosition = TooltipArrowPosition.BottomCenter,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val arrowWidth = 16.dp
    val arrowHeight = 8.dp

    Row(
        modifier = modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (arrowPosition == TooltipArrowPosition.StartCenter) {
            TooltipArrowSide(
                color = containerColor,
                width = arrowHeight,
                height = arrowWidth,
                direction = TooltipArrowDirection.Left
            )
        }

        Column(
            horizontalAlignment = when (arrowPosition) {
                TooltipArrowPosition.TopStart, TooltipArrowPosition.BottomStart -> Alignment.Start
                TooltipArrowPosition.TopCenter, TooltipArrowPosition.BottomCenter -> Alignment.CenterHorizontally
                TooltipArrowPosition.TopEnd, TooltipArrowPosition.BottomEnd -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
        ) {
            if (arrowPosition == TooltipArrowPosition.TopStart || 
                arrowPosition == TooltipArrowPosition.TopCenter || 
                arrowPosition == TooltipArrowPosition.TopEnd) {
                TooltipArrow(
                    color = containerColor,
                    width = arrowWidth,
                    height = arrowHeight,
                    isUpward = true,
                    modifier = Modifier.padding(
                        start = if (arrowPosition == TooltipArrowPosition.TopStart) 16.dp else 0.dp,
                        end = if (arrowPosition == TooltipArrowPosition.TopEnd) 16.dp else 0.dp
                    )
                )
            }

            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 240.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center
                )
            }

            if (arrowPosition == TooltipArrowPosition.BottomStart || 
                arrowPosition == TooltipArrowPosition.BottomCenter || 
                arrowPosition == TooltipArrowPosition.BottomEnd) {
                TooltipArrow(
                    color = containerColor,
                    width = arrowWidth,
                    height = arrowHeight,
                    isUpward = false,
                    modifier = Modifier.padding(
                        start = if (arrowPosition == TooltipArrowPosition.BottomStart) 16.dp else 0.dp,
                        end = if (arrowPosition == TooltipArrowPosition.BottomEnd) 16.dp else 0.dp
                    )
                )
            }
        }

        if (arrowPosition == TooltipArrowPosition.EndCenter) {
            TooltipArrowSide(
                color = containerColor,
                width = arrowHeight,
                height = arrowWidth,
                direction = TooltipArrowDirection.Right
            )
        }
    }
}

private enum class TooltipArrowDirection { Left, Right }

@Composable
private fun TooltipArrowSide(
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    direction: TooltipArrowDirection,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .offset(x = if (direction == TooltipArrowDirection.Left) 1.dp else (-1).dp)
    ) {
        val path = Path().apply {
            if (direction == TooltipArrowDirection.Left) {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

@Composable
private fun TooltipArrow(
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    isUpward: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(width, height)
            .offset(y = if (isUpward) 1.dp else (-1).dp)
    ) {
        val path = Path().apply {
            if (isUpward) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenithTooltipBox(
    tooltipText: String,
    state: TooltipState,
    modifier: Modifier = Modifier,
    arrowPosition: TooltipArrowPosition = TooltipArrowPosition.BottomCenter,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = when (arrowPosition) {
                TooltipArrowPosition.StartCenter -> TooltipAnchorPosition.Right
                TooltipArrowPosition.EndCenter -> TooltipAnchorPosition.Left
                TooltipArrowPosition.TopStart, TooltipArrowPosition.TopCenter, TooltipArrowPosition.TopEnd -> TooltipAnchorPosition.Below
                else -> TooltipAnchorPosition.Above
            },
            spacingBetweenTooltipAndAnchor = 4.dp
        ),
        tooltip = {
            ZenithTooltip(
                text = tooltipText,
                arrowPosition = arrowPosition
            )
        },
        state = state,
        modifier = modifier,
        content = content
    )
}
