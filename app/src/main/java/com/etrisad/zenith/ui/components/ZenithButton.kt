package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ZenithButtonType {
    Filled, Tonal, Elevated, Outlined, Text, Hold
}

enum class ZenithButtonSize {
    Small, Medium, Large, ExtraLarge
}

data class ZenithToggleOption(
    val text: String? = null,
    val icon: ImageVector? = null,
    val weight: Float = 1f,
    val type: ZenithButtonType? = null,
    val selectedType: ZenithButtonType? = null,
    val isLoading: Boolean = false,
    val loadingProgress: Float? = null,
    val backgroundProgress: Float? = null,
    val backgroundProgressProvider: (() -> Float)? = null,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val enabled: Boolean = true
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZenithButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: ZenithButtonType = ZenithButtonType.Filled,
    size: ZenithButtonSize = ZenithButtonSize.ExtraLarge,
    text: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingProgress: Float? = null,
    backgroundProgress: Float? = null,
    backgroundProgressProvider: (() -> Float)? = null,
    fillMaxWidth: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
    pillCornerRadius: Dp? = null,
    pressedCornerRadius: Dp? = null,
    height: Dp? = null,
    shape: Shape? = null,
    selected: Boolean = false,
    onHoldComplete: (() -> Unit)? = null,
    holdDuration: Long = 1500L,
    enableAfterDelayMillis: Long = 0L,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isFirst: Boolean = true,
    isLast: Boolean = true,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    ZenithButtonImpl(
        onClick = onClick,
        modifier = modifier,
        type = type,
        size = size,
        text = text,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        loadingProgress = loadingProgress,
        backgroundProgress = backgroundProgress,
        backgroundProgressProvider = backgroundProgressProvider,
        fillMaxWidth = fillMaxWidth,
        containerColor = containerColor,
        contentColor = contentColor,
        pillCornerRadiusOverride = pillCornerRadius,
        pressedCornerRadiusOverride = pressedCornerRadius,
        heightOverride = height,
        selected = selected,
        onHoldComplete = onHoldComplete,
        holdDuration = holdDuration,
        enableAfterDelayMillis = enableAfterDelayMillis,
        interactionSource = interactionSource,
        customShape = shape,
        isFirst = isFirst,
        isLast = isLast,
        content = content
    )
}

@Composable
fun RowScope.ZenithButtonWeighted(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    type: ZenithButtonType = ZenithButtonType.Filled,
    size: ZenithButtonSize = ZenithButtonSize.ExtraLarge,
    text: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingProgress: Float? = null,
    backgroundProgress: Float? = null,
    backgroundProgressProvider: (() -> Float)? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    pillCornerRadius: Dp? = null,
    pressedCornerRadius: Dp? = null,
    height: Dp? = null,
    selected: Boolean = false,
    onHoldComplete: (() -> Unit)? = null,
    holdDuration: Long = 1500L,
    enableAfterDelayMillis: Long = 0L,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isFirst: Boolean = true,
    isLast: Boolean = true,
    shape: Shape? = null,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedWeight by animateFloatAsState(
        targetValue = when {
            isPressed -> weight * 1.4f
            selected -> weight * 1.2f
            else -> weight
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "wAnim"
    )

    ZenithButton(
        onClick = onClick,
        modifier = modifier.weight(animatedWeight),
        type = type,
        size = size,
        text = text,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        loadingProgress = loadingProgress,
        backgroundProgress = backgroundProgress,
        backgroundProgressProvider = backgroundProgressProvider,
        fillMaxWidth = false,
        containerColor = containerColor,
        contentColor = contentColor,
        pillCornerRadius = pillCornerRadius,
        pressedCornerRadius = pressedCornerRadius,
        height = height,
        selected = selected,
        onHoldComplete = onHoldComplete,
        holdDuration = holdDuration,
        enableAfterDelayMillis = enableAfterDelayMillis,
        interactionSource = interactionSource,
        shape = shape,
        isFirst = isFirst,
        isLast = isLast,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZenithButtonImpl(
    onClick: () -> Unit,
    modifier: Modifier,
    type: ZenithButtonType,
    size: ZenithButtonSize,
    text: String?,
    icon: ImageVector?,
    enabled: Boolean,
    isLoading: Boolean,
    loadingProgress: Float?,
    backgroundProgress: Float?,
    backgroundProgressProvider: (() -> Float)?,
    fillMaxWidth: Boolean,
    containerColor: Color?,
    contentColor: Color?,
    pillCornerRadiusOverride: Dp?,
    pressedCornerRadiusOverride: Dp?,
    heightOverride: Dp?,
    selected: Boolean,
    onHoldComplete: (() -> Unit)?,
    holdDuration: Long,
    enableAfterDelayMillis: Long,
    interactionSource: MutableInteractionSource,
    isFirst: Boolean,
    isLast: Boolean,
    customShape: Shape?,
    content: @Composable (RowScope.() -> Unit)?
) {
    val isPressed by interactionSource.collectIsPressedAsState()
    val curPressed by rememberUpdatedState(isPressed)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    val delayAnim = remember(enableAfterDelayMillis) { Animatable(0f) }
    val isDelaying = enableAfterDelayMillis > 0 && delayAnim.value < 1f
    
    val bounceScale = remember { Animatable(1f) }

    LaunchedEffect(enableAfterDelayMillis) {
        if (enableAfterDelayMillis > 0) {
            delayAnim.animateTo(1f, tween(enableAfterDelayMillis.toInt(), easing = LinearEasing))
            bounceScale.animateTo(
                targetValue = 1f,
                initialVelocity = 2f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            )
        } else {
            delayAnim.snapTo(1f)
        }
    }

    val resH = heightOverride ?: when(size){ZenithButtonSize.Small->32.dp; ZenithButtonSize.Medium->40.dp; ZenithButtonSize.Large->48.dp; else->64.dp}
    val resPillR = pillCornerRadiusOverride ?: (resH / 2)
    val resPressR = pressedCornerRadiusOverride ?: when(size){ZenithButtonSize.Small->8.dp; ZenithButtonSize.Medium->12.dp; ZenithButtonSize.Large->16.dp; else->24.dp}
    val resTextS = when(size){ZenithButtonSize.Small->MaterialTheme.typography.labelSmall; ZenithButtonSize.Medium->MaterialTheme.typography.labelMedium; ZenithButtonSize.Large->MaterialTheme.typography.labelLarge; else->MaterialTheme.typography.titleMedium}
    val resIconS = when(size){ZenithButtonSize.Small->16.dp; ZenithButtonSize.Medium->18.dp; ZenithButtonSize.Large->20.dp; else->24.dp}
    val resPadH = when(size){ZenithButtonSize.Small->12.dp; ZenithButtonSize.Medium->16.dp; ZenithButtonSize.Large->24.dp; else->32.dp}
    val resInnerR = when(size){ZenithButtonSize.Small->4.dp; ZenithButtonSize.Medium->6.dp; ZenithButtonSize.Large->8.dp; else->12.dp}

    val animInnerR by animateDpAsState(
        targetValue = if (isPressed || selected) resPressR else resInnerR,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "animInnerR"
    )

    val holdAnim = remember { Animatable(0f) }
    val isHoldAction = onHoldComplete != null || type == ZenithButtonType.Hold

    val targetProgress = if (isDelaying) delayAnim.value else maxOf(holdAnim.value, backgroundProgress ?: (backgroundProgressProvider?.invoke() ?: 0f))
    val animatedProgressState = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (targetProgress < 0.05f || isDelaying) 
            spring(stiffness = Spring.StiffnessLow) 
        else 
            spring(stiffness = Spring.StiffnessHigh),
        label = "smoothProgress"
    )
    
    val cScaleState = animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cScale"
    )

    val finalScale = cScaleState.value * bounceScale.value

    val animR by animateDpAsState(
        targetValue = when {
            !enabled -> resPressR
            (backgroundProgress != null || backgroundProgressProvider != null) && !isHoldAction -> {
                if (isPressed) resPillR else resPressR
            }
            else -> {
                if (isPressed || selected) resPressR else resPillR
            }
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "animR"
    )
    
    val exPad by animateDpAsState(
        targetValue = when {
            isPressed -> 16.dp
            selected -> 8.dp
            else -> 0.dp
        },
        label = "exPad"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && !isLoading && !isDelaying) {
            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (e: Exception) { e.printStackTrace() }
            if (isHoldAction) {
                if (holdAnim.animateTo(1f, tween(holdDuration.toInt(), easing = LinearEasing)).endReason == AnimationEndReason.Finished && curPressed) {
                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (e: Exception) { e.printStackTrace() }
                    onHoldComplete?.invoke()
                    holdAnim.snapTo(0f)
                }
            }
        } else if (!isPressed && isHoldAction) {
            holdAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    val fShape = customShape ?: RoundedCornerShape(
        topStart = if (isFirst) animR else animInnerR,
        bottomStart = if (isFirst) animR else animInnerR,
        topEnd = if (isLast) animR else animInnerR,
        bottomEnd = if (isLast) animR else animInnerR
    )
    val primary = MaterialTheme.colorScheme.primary
    
    val targetBg = containerColor ?: when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
        type == ZenithButtonType.Filled || type == ZenithButtonType.Hold -> if (isLoading || isDelaying) MaterialTheme.colorScheme.primaryContainer else primary
        type == ZenithButtonType.Tonal -> MaterialTheme.colorScheme.secondaryContainer
        type == ZenithButtonType.Elevated -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> Color.Transparent
    }
    val animatedBg by animateColorAsState(targetValue = targetBg, label = "bgAnim")

    val targetContent = contentColor ?: when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        type == ZenithButtonType.Filled || type == ZenithButtonType.Hold -> if (isLoading || isDelaying) primary else MaterialTheme.colorScheme.onPrimary
        type == ZenithButtonType.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> primary
    }
    val animatedContentColor by animateColorAsState(targetValue = targetContent, label = "contentAnim")

    val progressBrush = remember(animatedContentColor) {
        Brush.horizontalGradient(
            listOf(animatedContentColor.copy(alpha = 0.12f), animatedContentColor.copy(alpha = 0.24f))
        )
    }

    Surface(
        onClick = {
            if (!enabled) {
                scope.launch {
                    val intensity = with(density) { 6.dp.toPx() }
                    shakeOffset.animateTo(intensity, spring(stiffness = 10000f))
                    shakeOffset.animateTo(-intensity, spring(stiffness = 10000f))
                    shakeOffset.animateTo(intensity / 2, spring(stiffness = 10000f))
                    shakeOffset.animateTo(-intensity / 2, spring(stiffness = 10000f))
                    shakeOffset.animateTo(0f, spring(stiffness = 10000f))
                }
            } else {
                onClick()
            }
        },
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(resH)
            .graphicsLayer { translationX = shakeOffset.value },
        enabled = (enabled && !isLoading && !isDelaying) || !enabled,
        shape = fShape,
        color = animatedBg,
        contentColor = animatedContentColor,
        tonalElevation = if (type == ZenithButtonType.Elevated) 2.dp else 0.dp,
        shadowElevation = if (type == ZenithButtonType.Elevated) 2.dp else 0.dp,
        border = if (type == ZenithButtonType.Outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = animatedProgressState.value
                    if (p > 0f) {
                        drawRect(
                            brush = progressBrush, 
                            size = this.size.copy(width = this.size.width * p)
                        )
                    }
                }
                .padding(horizontal = resPadH + exPad),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isLoading || isDelaying,
                transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessLow)) },
                label = "cSwitch"
            ) { loadingOrDelay ->
                if (loadingOrDelay) {
                    if (loadingProgress == null && !isDelaying) {
                        LoadingIndicator(color = animatedContentColor, modifier = Modifier.size(resH * 0.6f))
                    } else {
                        val prog = if (isDelaying) delayAnim.value else (loadingProgress ?: 0f)
                        Box(contentAlignment = Alignment.Center) {
                            CircularWavyProgressIndicator(
                                progress = { prog.coerceIn(0f, 1f) },
                                color = animatedContentColor,
                                trackColor = animatedContentColor.copy(alpha = 0.12f),
                                stroke = Stroke(width = with(density) { (resH * 0.06f).toPx() }),
                                trackStroke = Stroke(width = with(density) { (resH * 0.06f).toPx() }),
                                modifier = Modifier.size(resH * 0.7f)
                            )
                            
                            if (isDelaying) {
                                val secondsLeft = kotlin.math.ceil((1f - delayAnim.value) * (enableAfterDelayMillis / 1000f)).toInt()
                                if (secondsLeft > 0) {
                                    Text(
                                        text = secondsLeft.toString(),
                                        style = resTextS,
                                        fontWeight = FontWeight.Black,
                                        color = animatedContentColor
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.graphicsLayer { 
                            scaleX = finalScale
                            scaleY = finalScale
                        }
                    ) {
                        if (content != null) { content() } else {
                            if (icon != null) {
                                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(resIconS))
                                if (text != null) Spacer(Modifier.width(resH * 0.15f))
                            }
                            if (text != null) Text(text = text, style = resTextS, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZenithToggleButtonGroup(
    options: List<ZenithToggleOption>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isMultiSelect: Boolean = false,
    size: ZenithButtonSize = ZenithButtonSize.Medium
) {
    val resH = when(size){ZenithButtonSize.Small->32.dp; ZenithButtonSize.Medium->40.dp; ZenithButtonSize.Large->48.dp; else->56.dp}
    Row(modifier = modifier.height(resH).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndices.contains(index)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            
            val animatedWeight by animateFloatAsState(
                targetValue = when {
                    isPressed -> option.weight * 1.4f
                    isSelected -> option.weight * 1.2f
                    else -> option.weight
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "wAnim_$index"
            )

            val baseR = resH / 2
            val smallR = resH * 0.2f
            val startR by animateDpAsState(targetValue = if (isPressed) baseR * 0.6f else if (isSelected || index == 0) baseR else smallR)
            val endR by animateDpAsState(targetValue = if (isPressed) baseR * 0.6f else if (isSelected || index == options.size - 1) baseR else smallR)
            val currentType = if (isSelected) (option.selectedType ?: ZenithButtonType.Filled) else (option.type ?: ZenithButtonType.Tonal)
            
            ZenithButton(
                onClick = { onToggle(index) },
                modifier = Modifier.weight(animatedWeight),
                type = currentType,
                size = size,
                text = option.text,
                icon = if (isSelected && isMultiSelect && option.icon == null) Icons.Default.Check else option.icon,
                isLoading = option.isLoading,
                loadingProgress = option.loadingProgress,
                backgroundProgress = option.backgroundProgress,
                containerColor = option.containerColor,
                contentColor = option.contentColor,
                enabled = option.enabled,
                selected = isSelected,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(topStart = startR, bottomStart = startR, topEnd = endR, bottomEnd = endR)
            )
        }
    }
}

@Composable
fun ZenithGroupedButton(
    modifier: Modifier = Modifier,
    size: ZenithButtonSize = ZenithButtonSize.Large,
    content: @Composable RowScope.() -> Unit
) {
    val h = when(size){ZenithButtonSize.Small->32.dp; ZenithButtonSize.Medium->40.dp; ZenithButtonSize.Large->48.dp; else->56.dp}
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(h / 2),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = { Row(modifier = Modifier.padding(4.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content) }
    )
}

@Preview(showBackground = true)
@Composable
fun ZenithButtonPreview() {
    var selected by remember { mutableStateOf(setOf(1)) }

    val testProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        testProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Zenith Unified Buttons", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ZenithButton(onClick = {}, text = "Filled XL", fillMaxWidth = true)
            
            Text("Animated Background (5s Loop)", style = MaterialTheme.typography.labelLarge)
            ZenithButton(
                onClick = {}, 
                text = "${(5 * (1 - testProgress.value)).toInt()}s Remaining",
                backgroundProgress = testProgress.value, 
                type = ZenithButtonType.Tonal,
                fillMaxWidth = true
            )

            ZenithToggleButtonGroup(options = listOf(ZenithToggleOption("Option 1"), ZenithToggleOption("Option 2"), ZenithToggleOption("Option 3")), selectedIndices = selected, onToggle = { selected = setOf(it) })
            ZenithButton(type = ZenithButtonType.Hold, onClick = {}, text = "Hold Action (Fluid)", fillMaxWidth = true)
            
            var isEnabled by remember { mutableStateOf(true) }
            ZenithButton(
                onClick = { isEnabled = false }, 
                enabled = isEnabled,
                text = if (isEnabled) "Click to Disable" else "Disabled (Shake)", 
                fillMaxWidth = true
            )
            if (!isEnabled) {
                TextButton(onClick = { isEnabled = true }) { Text("Reset State") }
            }
            
            Text("Testing Text & Progress", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZenithButton(
                    onClick = {}, 
                    text = "Text Hold", 
                    type = ZenithButtonType.Text, 
                    onHoldComplete = {},
                    modifier = Modifier.weight(1f)
                )
                ZenithButton(
                    onClick = {}, 
                    text = "Manual 70%", 
                    backgroundProgress = 0.7f, 
                    type = ZenithButtonType.Text,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
