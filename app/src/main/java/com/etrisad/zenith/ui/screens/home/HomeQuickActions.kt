package com.etrisad.zenith.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.RemoveRedEye
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.HomeQuickActionsDefaults
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
enum class QuickActionType(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String? = null
) {
    ALARM("alarm", "Alarm", Icons.Outlined.Alarm),
    POMODORO("pomodoro", "Pomodoro", Icons.Outlined.Timer),
    STATS("stats", "Stats", Icons.Outlined.Insights),
    BEDTIME("bedtime", "Bedtime", Icons.Outlined.Bedtime),
    REELS_SHIELD(
        "reels_shield", "Reels Shield", Icons.Outlined.SwapVert,
        Screen.ReelsShield.route
    ),
    FOCUS("focus", "Focus", Icons.Outlined.Shield, Screen.Focus.route),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Screen.Settings.route),
    EYE_CARE("eye_care", "Eye Care", Icons.Outlined.RemoveRedEye, Screen.EyeCare.route),
    GRACE_PERIOD("grace_period", "Grace Period", Icons.Outlined.FreeBreakfast, Screen.GracePeriod.route),
    LOCKDOWN("lockdown", "Lockdown", Icons.Outlined.Lock, Screen.Lockdown.route),
    PAUSE_POINT("pause_point", "Pause Point", Icons.Outlined.PauseCircle, Screen.PausePoint.route);

    companion object {
        val ALL: List<QuickActionType> = entries.toList()
        fun fromId(id: String): QuickActionType? = ALL.firstOrNull { it.id == id }
    }
}
@Stable
class HomeCustomizeState {
    var menuOpen by mutableStateOf(false)
    var targetOpen by mutableStateOf(false)
    var quickEditOpen by mutableStateOf(false)
}

internal sealed interface QuickActionDragSource {
    data class Row(val index: Int) : QuickActionDragSource
    data class New(val type: QuickActionType) : QuickActionDragSource
}
@Stable
internal class QuickActionDragState {
    var source by mutableStateOf<QuickActionDragSource?>(null)
    val fingerPx = mutableStateOf(Offset.Zero)
    val layerOriginPx = mutableStateOf(Offset.Zero)
    val grabOffsetPx = mutableStateOf(Offset.Zero)
    var rowRect by mutableStateOf<Rect?>(null)
    var dropRect by mutableStateOf<Rect?>(null)
    var slotLeftPx by mutableStateOf(FloatArray(0))
    var slotWidthPx by mutableStateOf(0f)
    var slotCount by mutableStateOf(0)
    var hoverIndex by mutableStateOf(-1)

    fun begin(source: QuickActionDragSource, nodeOrigin: Offset, localOffset: Offset) {
        this.source = source
        grabOffsetPx.value = localOffset
        fingerPx.value = nodeOrigin + localOffset
        recomputeHover()
    }

    fun moveBy(delta: Offset) {
        fingerPx.value = fingerPx.value + delta
        recomputeHover()
    }

    fun clear() {
        source = null
        hoverIndex = -1
    }

    private fun recomputeHover() {
        val finger = fingerPx.value
        val lefts = slotLeftPx
        if (source == null || dropRect == null || lefts.isEmpty() || dropRect?.contains(finger) != true) {
            hoverIndex = -1
            return
        }
        val half = slotWidthPx / 2f
        var insertion = 0
        for (left in lefts) {
            if (finger.x > left + half) insertion++ else break
        }
        hoverIndex = insertion
    }
}
internal fun computeSlotRects(rowRect: Rect, cellWidthPx: Float, slotCount: Int): FloatArray {
    if (slotCount <= 0) return FloatArray(0)
    val gap = if (slotCount > 1) (rowRect.width - cellWidthPx * slotCount) / (slotCount - 1) else 0f
    val stride = cellWidthPx + gap
    return FloatArray(slotCount) { index -> rowRect.left + index * stride }
}
internal fun applyQuickActionDrop(
    current: List<String>,
    source: QuickActionDragSource,
    insertionIndex: Int
): List<String> {
    if (insertionIndex < 0) return current
    val next = current.toMutableList()
    when (source) {
        is QuickActionDragSource.Row -> {
            if (source.index !in next.indices) return current
            val item = next.removeAt(source.index)
            var target = insertionIndex.coerceIn(0, next.size)
            if (insertionIndex > source.index) target = (target - 1).coerceIn(0, next.size)
            if (target == source.index) return current
            next.add(target, item)
        }
        is QuickActionDragSource.New -> {
            if (source.type.id in next || next.size >= HomeQuickActionsDefaults.MAX) return current
            next.add(insertionIndex.coerceIn(0, next.size), source.type.id)
        }
    }
    return next.distinct().take(HomeQuickActionsDefaults.MAX).ifEmpty { current }
}
private fun Modifier.quickActionDragSource(
    dragState: QuickActionDragState,
    source: QuickActionDragSource,
    originPx: androidx.compose.runtime.MutableState<Offset>,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
): Modifier = this
    .onGloballyPositioned { placed -> originPx.value = placed.boundsInWindow().topLeft }
    .pointerInput(source) {
        detectDragGesturesAfterLongPress(
            onDragStart = { local ->
                if (dragState.source == null) {
                    onDragStart()
                    dragState.begin(source, originPx.value, local)
                }
            },
            onDrag = { change, delta ->
                if (dragState.source == source) {
                    change.consume()
                    dragState.moveBy(delta)
                }
            },
            onDragEnd = { if (dragState.source == source) onDragEnd() },
            onDragCancel = { if (dragState.source == source) dragState.clear() }
        )
    }
@Composable
internal fun QuickActionsEditSheet(
    actions: List<String>,
    bedtimeStatus: BedtimeStatus,
    pomodoroStatus: PomodoroStatus,
    dragState: QuickActionDragState,
    expressiveColors: Boolean,
    onDragEnd: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val densityScale = 1f / LocalDensity.current.density.pow(0.2f)
    val available = remember(actions) { QuickActionType.ALL.filter { it.id !in actions } }
    val full = actions.size >= HomeQuickActionsDefaults.MAX
    val availableMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.30f)
        .dp.coerceAtLeast(120.dp)
    val containerColor = if (expressiveColors) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                clip = false
            )
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(containerColor)
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (full) {
                        "Row is full · remove a tile before adding another"
                    } else {
                        "Hold a tile and drag it onto the row · up to ${HomeQuickActionsDefaults.MAX}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (full) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close editor",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = available.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = availableMaxHeight)
            ) {
                val gridGap = 10.dp
                val contentWidth = (maxWidth - 40.dp).coerceAtLeast(1.dp)
                val minCell = 82.dp * densityScale + 4.dp
                val gridColumns = ((contentWidth + gridGap) / (minCell + gridGap))
                    .toInt()
                    .coerceIn(1, 4)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    available.chunked(gridColumns).forEach { rowTypes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gridGap),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowTypes.forEach { type ->
                                val origin = remember { mutableStateOf(Offset.Zero) }
                                val isDraggingNew =
                                    (dragState.source as? QuickActionDragSource.New)?.type == type
                                val tileModifier = Modifier
                                    .padding(2.dp)
                                    .graphicsLayer {
                                        alpha = when {
                                            isDraggingNew -> 0.35f
                                            full -> 0.45f
                                            else -> 1f
                                        }
                                        val tileScale = if (isDraggingNew) 0.90f else 1f
                                        scaleX = tileScale
                                        scaleY = tileScale
                                    }
                                    .then(
                                        if (full) Modifier
                                        else Modifier.quickActionDragSource(
                                            dragState = dragState,
                                            source = QuickActionDragSource.New(type),
                                            originPx = origin,
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = onDragEnd
                                        )
                                    )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = tileModifier) {
                                        QuickActionCardContent(
                                            type = type,
                                            bedtimeStatus = bedtimeStatus,
                                            pomodoroStatus = pomodoroStatus,
                                            densityScale = densityScale
                                        )
                                    }
                                }
                            }
                            repeat(gridColumns - rowTypes.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        if (available.isEmpty()) {
            Text(
                text = "Every quick action is already on the row.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        ZenithButton(
            onClick = onSave,
            modifier = Modifier.padding(horizontal = 20.dp),
            type = ZenithButtonType.Filled,
            size = ZenithButtonSize.Large,
            fillMaxWidth = true,
            text = "Save"
        )
    }
}
@Composable
internal fun QuickActionDragLayer(
    dragState: QuickActionDragState,
    actions: List<String>,
    bedtimeStatus: BedtimeStatus,
    pomodoroStatus: PomodoroStatus
) {
    val dragging = dragState.source ?: return
    val type = when (dragging) {
        is QuickActionDragSource.New -> dragging.type
        is QuickActionDragSource.Row ->
            actions.getOrNull(dragging.index)?.let(QuickActionType::fromId)
    }
    val densityScale = 1f / LocalDensity.current.density.pow(0.2f)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { placed ->
                dragState.layerOriginPx.value = placed.boundsInWindow().topLeft
            }
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    val origin = dragState.layerOriginPx.value
                    val finger = dragState.fingerPx.value
                    val grab = dragState.grabOffsetPx.value
                    translationX = finger.x - origin.x - grab.x
                    translationY = finger.y - origin.y - grab.y
                    transformOrigin = TransformOrigin(
                        (grab.x / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        (grab.y / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    )
                    val appearProgress = appear.value
                    val ghostScale = 0.78f + 0.30f * appearProgress
                    scaleX = ghostScale
                    scaleY = ghostScale
                    alpha = appearProgress * 0.97f
                    shadowElevation = 16.dp.toPx() * appearProgress
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .width(82.dp * densityScale)
                    .height(60.dp * densityScale)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (type != null) {
                        Icon(
                            imageVector = type.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp * densityScale),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp * densityScale))
            Text(
                text = type?.label ?: "Move",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeCustomizeMenuSheet(
    expressiveColors: Boolean,
    onDismiss: () -> Unit,
    onEditTarget: () -> Unit,
    onEditQuickActions: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hideThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (expressiveColors) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = "Customize Home",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            HomeCustomizeMenuItem(
                icon = Icons.Outlined.Tune,
                title = "Daily Screen Time Target",
                subtitle = "Change the goal shown on your dashboard",
                onClick = { hideThen(onEditTarget) }
            )
            HomeCustomizeMenuItem(
                icon = Icons.Outlined.SwapVert,
                title = "Edit Quick Actions",
                subtitle = "Add, remove or reorder the shortcuts below",
                onClick = { hideThen(onEditQuickActions) }
            )
        }
    }
}

@Composable
private fun HomeCustomizeMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun QuickActionCardContent(
    type: QuickActionType,
    bedtimeStatus: BedtimeStatus?,
    pomodoroStatus: PomodoroStatus?,
    densityScale: Float,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val coroutineScope = rememberCoroutineScope()
    var launching by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = when {
            
            
            launching -> 0.92f
            isPressed -> 0.88f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "QuickActionTileScale"
    )
    var showRemainingTime by remember { mutableStateOf(false) }

    LaunchedEffect(bedtimeStatus?.isActive) {
        if (bedtimeStatus?.isActive == true) {
            while (true) {
                delay(60000)
                showRemainingTime = !showRemainingTime
            }
        } else {
            showRemainingTime = false
        }
    }

    val progress: Float?
    val ringColor: Color
    when (type) {
        QuickActionType.POMODORO -> {
            val status = pomodoroStatus
            progress = if (status?.isActive == true) status.progress else null
            ringColor = if (status?.isBreak == true) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary
        }
        QuickActionType.BEDTIME -> {
            val status = bedtimeStatus
            progress = if (status?.isActive == true) status.progress else null
            ringColor = MaterialTheme.colorScheme.primary
        }
        else -> {
            progress = null
            ringColor = MaterialTheme.colorScheme.primary
        }
    }

    val label = if (
        type == QuickActionType.BEDTIME &&
        bedtimeStatus?.isActive == true &&
        showRemainingTime
    ) {
        bedtimeStatus.timeRemaining
    } else {
        type.label
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .width(82.dp * densityScale)
                .height(60.dp * densityScale)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(CircleShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = ripple(color = MaterialTheme.colorScheme.primary)
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!launching) {
                                launching = true
                                coroutineScope.launch {
                                    
                                    
                                    delay(80)
                                    try {
                                        onClick()
                                    } finally {
                                        launching = false
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (progress != null) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(32.dp * densityScale),
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        stroke = Stroke(width = with(LocalDensity.current) { 3.dp.toPx() } * densityScale),
                        trackStroke = Stroke(width = with(LocalDensity.current) { 3.dp.toPx() } * densityScale),
                        wavelength = 8.dp * densityScale
                    )
                } else {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.label,
                        modifier = Modifier.size(24.dp * densityScale),
                        tint = ringColor
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp * densityScale))
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically { it / 2 })
                    .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 2 })
            },
            label = "QuickActionLabel"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPressed || launching) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(82.dp * densityScale)
            )
        }
    }
}
@Composable
internal fun QuickActionsEditRow(
    actions: List<String>,
    bedtimeStatus: BedtimeStatus,
    pomodoroStatus: PomodoroStatus,
    onRemove: (String) -> Unit,
    dragState: QuickActionDragState,
    onDragEnd: () -> Unit
) {
    val density = LocalDensity.current
    val densityScale = 1f / density.density.pow(0.2f)
    val slotW = 82.dp * densityScale
    val slotH = 60.dp * densityScale
    val ringGap = 4.dp
    val cellW = slotW + ringGap * 2
    val haptic = LocalHapticFeedback.current
    val canRemove = actions.size > HomeQuickActionsDefaults.MIN
    val coroutineScope = rememberCoroutineScope()
    var removingId by remember { mutableStateOf<String?>(null) }

    val source = dragState.source
    val hover = dragState.hoverIndex
    val insertionColor = MaterialTheme.colorScheme.primary
    val draggedIndex = (source as? QuickActionDragSource.Row)?.index ?: -1
    val insertionIndex = if (source != null) hover else -1

    fun publishGeometry(rect: Rect) {
        with(density) {
            dragState.rowRect = rect
            dragState.dropRect = rect.inflate(32.dp.toPx())
            dragState.slotWidthPx = cellW.toPx()
            dragState.slotLeftPx = computeSlotRects(rect, cellW.toPx(), actions.size)
            dragState.slotCount = actions.size
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp * densityScale)
            .onGloballyPositioned { placed ->
                val rect = placed.boundsInWindow()
                if (rect != dragState.rowRect || dragState.slotCount != actions.size) {
                    publishGeometry(rect)
                }
            }
            .drawBehind {
                if (insertionIndex < 0) return@drawBehind
                val rowRect = dragState.rowRect ?: return@drawBehind
                val lefts = dragState.slotLeftPx
                if (lefts.isEmpty()) return@drawBehind
                val gap = if (actions.size > 1) {
                    ((rowRect.width - cellW.toPx() * actions.size) / (actions.size - 1))
                        .coerceAtLeast(0f)
                } else {
                    0f
                }
                val xInRow = when {
                    insertionIndex <= 0 -> 0f
                    insertionIndex >= actions.size -> rowRect.width
                    else -> lefts[insertionIndex] - rowRect.left - gap / 2f
                }
                val x = xInRow.coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
                drawLine(
                    color = insertionColor,
                    start = Offset(x, ringGap.toPx()),
                    end = Offset(x, ringGap.toPx() + slotH.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
    ) {
        
        
        
        val rowGap = if (actions.size > 1) {
            ((maxWidth - cellW * actions.size) / (actions.size - 1)).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val slotStride = cellW + rowGap

        actions.forEachIndexed { index, actionId ->
            key(actionId) {
                val type = QuickActionType.fromId(actionId)
                val dragged = index == draggedIndex
                val entrance = remember { Animatable(0f) }
                val targetX = slotStride * index
                val animatedX = remember {
                    Animatable(with(density) { targetX.toPx() })
                }
                LaunchedEffect(actionId) {
                    entrance.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
                LaunchedEffect(targetX) {
                    animatedX.animateTo(
                        targetValue = with(density) { targetX.toPx() },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(cellW)
                        .height(slotH + 26.dp * densityScale)
                        .graphicsLayer {
                            translationX = animatedX.value
                            alpha = entrance.value
                            val entranceScale = 0.82f + 0.18f * entrance.value
                            scaleX = entranceScale
                            scaleY = entranceScale
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        },
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .offset(x = ringGap - 2.dp, y = ringGap - 2.dp)
                            .size(slotW + 4.dp, slotH + 4.dp)
                            .background(
                                color = if (dragged) {
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.25f)
                                } else {
                                    Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (dragged) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                },
                                shape = CircleShape
                            )
                    )

                    if (type != null) {
                        val origin = remember { mutableStateOf(Offset.Zero) }
                        Box(
                            modifier = Modifier
                                .offset(x = ringGap, y = ringGap)
                                .size(slotW, slotH)
                                .graphicsLayer { alpha = if (dragged) 0.22f else 1f }
                                .quickActionDragSource(
                                    dragState = dragState,
                                    source = QuickActionDragSource.Row(index),
                                    originPx = origin,
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = onDragEnd
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            QuickActionCardContent(
                                type = type,
                                bedtimeStatus = bedtimeStatus,
                                pomodoroStatus = pomodoroStatus,
                                densityScale = densityScale
                            )
                        }

                        if (!dragged) {
                            Box(
                                modifier = Modifier
                                    .offset(x = cellW - 16.dp, y = 0.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .then(
                                        if (canRemove) {
                                            Modifier.clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (removingId == null) {
                                                    
                                                    
                                                    
                                                    removingId = actionId
                                                    coroutineScope.launch {
                                                        entrance.animateTo(
                                                            targetValue = 0f,
                                                            animationSpec = tween(140)
                                                        )
                                                        onRemove(actionId)
                                                        removingId = null
                                                    }
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove quick action",
                                    tint = if (canRemove) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
