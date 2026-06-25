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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.theme.ZenithTheme
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
    interactionSource: MutableInteractionSource? = null,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    isDisableWeight: Boolean = false,
    isDisableExpand: Boolean = false,
    contentScaleEnabled: Boolean = true,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    ZenithButtonCore(
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
        coreInteractionSource = actualInteractionSource,
        customShape = shape,
        isFirst = isFirst,
        isLast = isLast,
        isDisableWeight = isDisableWeight,
        isDisableExpand = isDisableExpand,
        contentScaleEnabled = contentScaleEnabled,
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
    interactionSource: MutableInteractionSource? = null,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    shape: Shape? = null,
    isDisableWeight: Boolean = false,
    isDisableExpand: Boolean = false,
    contentScaleEnabled: Boolean = true,
    content: @Composable (RowScope.() -> Unit)? = null
) {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    var isTapped by remember { mutableStateOf(false) }
    val visualPressed = isPressed || isTapped

    val animatedWeight by animateFloatAsState(
        targetValue = when {
            visualPressed -> weight * 1.25f
            selected -> weight * 1.1f
            else -> weight
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "wAnim"
    )

    val finalModifier = if (isDisableWeight) modifier.weight(weight) else modifier.weight(animatedWeight)

    ZenithButtonCore(
        onClick = {
            scope.launch {
                isTapped = true
                delay(100)
                isTapped = false
            }
            onClick()
        },
        modifier = finalModifier,
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
        pillCornerRadiusOverride = pillCornerRadius,
        pressedCornerRadiusOverride = pressedCornerRadius,
        heightOverride = height,
        selected = selected,
        onHoldComplete = onHoldComplete,
        holdDuration = holdDuration,
        enableAfterDelayMillis = enableAfterDelayMillis,
        coreInteractionSource = actualInteractionSource,
        customShape = shape,
        isFirst = isFirst,
        isLast = isLast,
        isDisableWeight = isDisableWeight,
        isDisableExpand = isDisableExpand,
        contentScaleEnabled = contentScaleEnabled,
        content = content
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZenithButtonCore(
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
    coreInteractionSource: MutableInteractionSource,
    isFirst: Boolean,
    isLast: Boolean,
    customShape: Shape?,
    isDisableWeight: Boolean = false,
    isDisableExpand: Boolean = false,
    contentScaleEnabled: Boolean,
    content: @Composable (RowScope.() -> Unit)?
) {
    val isPressed by coreInteractionSource.collectIsPressedAsState()
    var isTapped by remember { mutableStateOf(false) }
    val visualPressed = isPressed || isTapped
    
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
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / enableAfterDelayMillis).coerceIn(0f, 1f)
                delayAnim.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
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
    val resPadH = when(size){ZenithButtonSize.Small->8.dp; ZenithButtonSize.Medium->12.dp; ZenithButtonSize.Large->20.dp; else->24.dp}
    val resInnerR = when(size){ZenithButtonSize.Small->4.dp; ZenithButtonSize.Medium->6.dp; ZenithButtonSize.Large->8.dp; else->12.dp}

    val animInnerR by animateDpAsState(
        targetValue = if (visualPressed) resPressR else resInnerR,
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
        targetValue = if (visualPressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cScale"
    )

    val finalScale = if (contentScaleEnabled) cScaleState.value * bounceScale.value else 1f

    val animR by animateDpAsState(
        targetValue = if (!enabled || visualPressed) resPressR else resPillR,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "animR"
    )
    
    val resExPad = when(size) {
        ZenithButtonSize.Small -> 4.dp
        ZenithButtonSize.Medium -> 8.dp
        ZenithButtonSize.Large -> 12.dp
        ZenithButtonSize.ExtraLarge -> 16.dp
    }
    
    val exPad by animateDpAsState(
        targetValue = when {
            isDisableExpand || !contentScaleEnabled -> 0.dp
            visualPressed -> resExPad
            selected -> resExPad / 2
            else -> 0.dp
        },
        label = "exPad"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && enabled && !isLoading && !isDelaying) {
            try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (e: Exception) { e.printStackTrace() }
            if (isHoldAction) {
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / holdDuration).coerceIn(0f, 1f)
                    holdAnim.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }
                if (curPressed) {
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
                scope.launch {
                    isTapped = true
                    delay(100)
                    isTapped = false
                }
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
        interactionSource = coreInteractionSource
    ) {
        Box(
            modifier = Modifier.then(if (fillMaxWidth) Modifier.fillMaxSize() else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val p = animatedProgressState.value
            if (p > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(
                                brush = progressBrush,
                                size = this.size.copy(width = this.size.width * p)
                            )
                        }
                )
            }

            Box(
                modifier = Modifier
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
                            AnimatedVisibility(
                                visible = icon != null,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                icon?.let {
                                    Icon(
                                        imageVector = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(resIconS)
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = text != null,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (icon != null) {
                                        Spacer(Modifier.width(resH * 0.15f))
                                    }
                                    Text(
                                        text = text ?: "",
                                        style = resTextS,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
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
    size: ZenithButtonSize = ZenithButtonSize.Medium,
    isInsideContainer: Boolean = false,
    isShowingCheck: Boolean = true,
    isScalingEnabled: Boolean = true,
    showTextSelected: Boolean = false,
    isFillMaxWidth: Boolean = true
) {
    val resH = when(size){ZenithButtonSize.Small->32.dp; ZenithButtonSize.Medium->40.dp; ZenithButtonSize.Large->48.dp; else->56.dp}
    Row(
        modifier = modifier
            .height(resH)
            .then(if (isFillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndices.contains(index)
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scope = rememberCoroutineScope()
            
            var isTapped by remember { mutableStateOf(false) }
            
            val animatedWeight by animateFloatAsState(
                targetValue = when {
                    isPressed || isTapped -> if (showTextSelected) option.weight * 1.8f else option.weight * 1.4f
                    isSelected -> if (showTextSelected) option.weight * 1.6f else option.weight * 1.2f
                    else -> if (showTextSelected) option.weight * 0.85f else option.weight
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "wAnim_$index"
            )

            val baseR = resH / 2
            val smallR = resH * 0.2f
            val startR by animateDpAsState(targetValue = if (isPressed || isTapped) baseR * 0.6f else if (isSelected || index == 0) baseR else smallR)
            val endR by animateDpAsState(targetValue = if (isPressed || isTapped) baseR * 0.6f else if (isSelected || index == options.size - 1) baseR else smallR)
            val currentType = if (isSelected) (option.selectedType ?: ZenithButtonType.Filled) else (option.type ?: ZenithButtonType.Tonal)

            val unselectedBg = if (isInsideContainer) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
            
            val unselectedContent = if (isInsideContainer) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            ZenithButton(
                onClick = { 
                    scope.launch {
                        isTapped = true
                        delay(100)
                        isTapped = false
                    }
                    onToggle(index) 
                },
                modifier = Modifier.weight(animatedWeight),
                type = currentType,
                size = size,
                text = if (showTextSelected) {
                    if (isSelected || isPressed || isTapped) option.text else null
                } else option.text,
                icon = if (isSelected && isMultiSelect && isShowingCheck && option.icon == null) Icons.Default.Check else option.icon,
                isLoading = option.isLoading,
                loadingProgress = option.loadingProgress,
                backgroundProgress = option.backgroundProgress,
                containerColor = option.containerColor ?: if (isSelected) null else unselectedBg,
                contentColor = option.contentColor ?: if (isSelected) null else unselectedContent,
                enabled = option.enabled,
                selected = isSelected,
                interactionSource = interactionSource,
                contentScaleEnabled = isScalingEnabled,
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
    val spacing = when(size) {
        ZenithButtonSize.Small -> 2.dp
        ZenithButtonSize.Medium -> 4.dp
        ZenithButtonSize.Large -> 4.dp
        ZenithButtonSize.ExtraLarge -> 4.dp
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun ZenithButtonGallery() {
    val scrollState = rememberScrollState()
    ZenithTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Zenith Button Gallery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Button Types", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZenithButton(onClick = {}, type = ZenithButtonType.Filled, text = "Filled", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                    ZenithButton(onClick = {}, type = ZenithButtonType.Tonal, text = "Tonal", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZenithButton(onClick = {}, type = ZenithButtonType.Elevated, text = "Elevated", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                    ZenithButton(onClick = {}, type = ZenithButtonType.Outlined, text = "Outlined", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZenithButton(onClick = {}, type = ZenithButtonType.Text, text = "Text", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                    ZenithButton(onClick = {}, type = ZenithButtonType.Hold, text = "Hold", modifier = Modifier.weight(1f), size = ZenithButtonSize.Medium)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Button Sizes", style = MaterialTheme.typography.titleMedium)
                ZenithButton(onClick = {}, size = ZenithButtonSize.ExtraLarge, text = "Extra Large", fillMaxWidth = true)
                ZenithButton(onClick = {}, size = ZenithButtonSize.Large, text = "Large", fillMaxWidth = true)
                ZenithButton(onClick = {}, size = ZenithButtonSize.Medium, text = "Medium", fillMaxWidth = true)
                ZenithButton(onClick = {}, size = ZenithButtonSize.Small, text = "Small", fillMaxWidth = true)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("With Icons", style = MaterialTheme.typography.titleMedium)
                ZenithButton(onClick = {}, icon = Icons.Default.Add, text = "Add Item", fillMaxWidth = true)
                ZenithButton(onClick = {}, icon = Icons.Default.Settings, text = "Settings", type = ZenithButtonType.Outlined, fillMaxWidth = true)
                ZenithButton(onClick = {}, icon = Icons.Default.Delete, type = ZenithButtonType.Tonal, contentColor = MaterialTheme.colorScheme.error)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("States", style = MaterialTheme.typography.titleMedium)
                ZenithButton(onClick = {}, text = "Loading Indeterminate", isLoading = true, fillMaxWidth = true)
                ZenithButton(onClick = {}, text = "Loading Progress 60%", isLoading = true, loadingProgress = 0.6f, fillMaxWidth = true)
                ZenithButton(onClick = {}, text = "Background Progress 40%", backgroundProgress = 0.4f, fillMaxWidth = true)
                ZenithButton(onClick = {}, text = "Disabled", enabled = false, fillMaxWidth = true)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Grouped Buttons", style = MaterialTheme.typography.titleMedium)
                ZenithGroupedButton {
                    ZenithButtonWeighted(onClick = {}, text = "Left", isLast = false)
                    ZenithButtonWeighted(onClick = {}, text = "Center", isFirst = false, isLast = false, type = ZenithButtonType.Tonal)
                    ZenithButtonWeighted(onClick = {}, text = "Right", isFirst = false)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Toggle Groups", style = MaterialTheme.typography.titleMedium)
                var selectedSingle by remember { mutableStateOf(setOf(0)) }
                ZenithToggleButtonGroup(
                    options = listOf(ZenithToggleOption("Morning"), ZenithToggleOption("Noon"), ZenithToggleOption("Night")),
                    selectedIndices = selectedSingle,
                    onToggle = { selectedSingle = setOf(it) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom Overrides", style = MaterialTheme.typography.titleMedium)
                ZenithButton(
                    onClick = {},
                    text = "Custom Colors & Radius",
                    containerColor = Color(0xFF6200EE),
                    contentColor = Color.White,
                    pillCornerRadius = 8.dp,
                    fillMaxWidth = true
                )
                ZenithButton(
                    onClick = {},
                    text = "Delayed Enable (3s)",
                    enableAfterDelayMillis = 3000L,
                    fillMaxWidth = true
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
fun ZenithButtonPreview() {
    ZenithButtonGallery()
}
