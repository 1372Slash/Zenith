package com.etrisad.zenith.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
@Composable
fun TopSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val slide = remember { Animatable(0f) }
        var panelHPx by remember { mutableIntStateOf(1) }
        val offsetAnim = remember { Animatable(0f) }
        LaunchedEffect(visible) {
            offsetAnim.snapTo(0f)
            if (visible) {
                slide.snapTo(0f)
                slide.animateTo(
                    1f,
                    // House bottom-sheet feel: plain spring, no bounce.
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
        fun requestDismiss() {
            scope.launch {
                slide.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                onDismissRequest()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = ::requestDismiss
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { panelHPx = it.height.coerceAtLeast(1) }
                    .graphicsLayer {
                        translationY = -(1f - slide.value) * panelHPx
                    }
                    .heightIn(max = 560.dp)
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 28.dp,
                            bottomEnd = 28.dp
                        )
                    )
                    .background(containerColor)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .offset {
                        IntOffset(0, offsetAnim.value.roundToInt())
                    }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                val maxPull = with(density) { 120.dp.toPx() }
                                offsetAnim.snapTo(
                                    (offsetAnim.value + delta).coerceAtMost(maxPull)
                                )
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            scope.launch {
                                val dismissDistance = with(density) { 80.dp.toPx() }
                                if (offsetAnim.value < -dismissDistance || velocity < -1500f) {
                                    offsetAnim.animateTo(
                                        -panelHPx.toFloat(),
                                        animationSpec = tween(300, easing = EaseInCubic)
                                    )
                                    onDismissRequest()
                                } else {
                                    offsetAnim.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            }
                        }
                    )
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    content()
                }
            }
        }
    }
}
