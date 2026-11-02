package com.etrisad.zenith.ui.onboarding

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.R
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.screens.profile.ProfileTier
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SHIELD_CHAPTER_ID = "the_shield"

@Composable
fun OnboardingOverlayHost(
    controller: OnboardingController,
    suspended: Boolean,
    masterySummary: String,
    questCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!GUIDED_PATH_ENABLED || suspended) return

    when (val state = controller.state) {
        is GuideState.ChapterComplete -> ChapterCompleteSheet(
            controller = controller,
            chapterId = state.chapterId,
            isReplay = state.isReplay,
            questCompleted = questCompleted
        )
        GuideState.MasteryReady -> MasterySheet(controller, masterySummary)
        GuideState.Mastered -> MasteredSheet(controller)
        GuideState.Idle, GuideState.Dismissed -> Unit
        is GuideState.Active -> {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
                modifier = modifier
            ) {
                ActiveStepOverlay(controller)
            }
        }
    }
}
@Composable
private fun ActiveStepOverlay(controller: OnboardingController) {
    val chapter = controller.currentChapter ?: return
    val step = controller.currentStep ?: return
    val index = controller.currentStepIndex

    val view = androidx.compose.ui.platform.LocalView.current
    val stepTitle = stringResource(step.titleRes)
    val stepBody = stringResource(step.bodyRes)
    val stepAnnouncement = stringResource(
        R.string.onboarding_a11y_step,
        chapter.order,
        guideChapters.size,
        index + 1,
        chapter.steps.size,
        stepTitle,
        stepBody
    )
    LaunchedEffect(step.id) {
        view.announceForAccessibility(stepAnnouncement)
    }

    val registry = LocalOnboardingTargets.current
    val actions = LocalOnboardingActions.current
    val scrollHub = LocalOnboardingScroll.current
    val animatorsEnabled = remember { areSystemAnimatorsEnabled() }

    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var showExitConfirm by remember(step.id) { mutableStateOf(false) }

    val rectAnim = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val cornerAnim = remember { Animatable(0f) }
    var hasPlacedHole by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { actions.perform(GuideAction.CollapseFocusFab) }
    }

    BackHandler(enabled = true) {
        if (showExitConfirm) {
            showExitConfirm = false
        } else if (!controller.back()) {
            showExitConfirm = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val densityPx = LocalDensity.current
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val safeDrawing = WindowInsets.safeDrawing
        val insets = GuideInsets(
            left = safeDrawing.getLeft(densityPx, layoutDirection),
            top = safeDrawing.getTop(densityPx),
            right = safeDrawing.getRight(densityPx, layoutDirection),
            bottom = safeDrawing.getBottom(densityPx)
        )
        val containerSize = IntSize(constraints.maxWidth, constraints.maxHeight)
        val marginPx = with(densityPx) { GuideTokens.ScreenMargin.toPx() }
        val gapPx = with(densityPx) { GuideTokens.TooltipGap.toPx() }
        val viewport = androidx.compose.ui.geometry.Size(
            containerSize.width.toFloat(),
            containerSize.height.toFloat()
        )
        LaunchedEffect(step.id, containerSize) {
            if (containerSize.width <= 0 || containerSize.height <= 0) return@LaunchedEffect
            if (step.action != GuideAction.None) {
                actions.perform(step.action)
            }
            val anchorId = step.targetId ?: run {
                targetBounds = null
                return@LaunchedEffect
            }

            val ready = registry.bounds(anchorId)
            if (step.action == GuideAction.None && step.scrollKey == null && ready != null &&
                ready.isComfortablyVisibleIn(containerSize)
            ) {
                targetBounds = ready
                snapshotFlow { registry.bounds(anchorId) }
                    .filterNotNull()
                    .collect { targetBounds = it }
                return@LaunchedEffect
            }

            androidx.compose.runtime.withFrameNanos { }
            val scrollJob = launch {
                val known = registry.bounds(anchorId) != null
                scrollHub.scrollTo(step.scrollKey ?: anchorId, timeoutMs = if (known) 150 else 400)
            }
            var placed = registry.await(anchorId, timeoutMs = 1_500)
            if (step.scrollKey == null && placed != null && placed.isComfortablyVisibleIn(containerSize)) {
                scrollJob.cancel()
            } else {
                scrollJob.join()
                placed = registry.bounds(anchorId) ?: placed
            }

            val topInset = with(densityPx) { GuideTokens.AnchorTopInset.toPx() }
            val bottomInset = with(densityPx) { GuideTokens.AnchorBottomInset.toPx() }
            var attempts = 0
            while (attempts < 2) {
                val current = placed ?: break
                if (current.isComfortablyVisibleIn(containerSize)) break
                attempts++
                runCatching {
                    registry.requester(anchorId)?.bringIntoView(
                        Rect(0f, -topInset, current.width, current.height + bottomInset)
                    )
                }
                androidx.compose.runtime.withFrameNanos { }
                placed = registry.bounds(anchorId) ?: current
            }

            if (placed == null) OnboardingMetrics.targetMissing(anchorId)
            targetBounds = placed ?: return@LaunchedEffect
            snapshotFlow { registry.bounds(anchorId) }
                .filterNotNull()
                .collect { targetBounds = it }
        }

        val geometry = targetBounds?.toSpotlightGeometry(
            density = densityPx,
            container = viewport
        )

        LaunchedEffect(geometry) {
            val target = geometry
            if (target == null) return@LaunchedEffect
            if (!animatorsEnabled || !hasPlacedHole) {
                rectAnim.snapTo(target.rect)
                cornerAnim.snapTo(target.cornerRadius)
                hasPlacedHole = true
                return@LaunchedEffect
            }

            val travelPx = hypot(
                target.rect.center.x - rectAnim.value.center.x,
                target.rect.center.y - rectAnim.value.center.y
            )
            val mediumPx = with(densityPx) { 110.dp.toPx() }
            val longPx = with(densityPx) { 240.dp.toPx() }

            val longHop = travelPx > longPx
            val stiffness = when {
                longHop -> 420f
                travelPx > mediumPx -> 620f
                else -> 900f
            }
            val damping = if (travelPx > mediumPx) 0.95f else 0.9f
            val rectSpec = spring<Rect>(dampingRatio = damping, stiffness = stiffness)
            val cornerSpec = spring<Float>(dampingRatio = damping, stiffness = stiffness)
            val rectJob = launch { rectAnim.animateTo(target.rect, rectSpec) }
            cornerAnim.animateTo(target.cornerRadius, cornerSpec)
            rectJob.join()
        }

        val showHole = hasPlacedHole && (geometry != null || step.targetId != null)

        SpotlightScrim(
            hole = if (showHole) {
                { SpotlightGeometry(rectAnim.value, cornerAnim.value) }
            } else {
                null
            },
            dimAlpha = GuideTokens.SCRIM_ALPHA,
            showRing = geometry != null
        )

        if (geometry != null && step.interaction == StepInteraction.TAP_THROUGH) {
            HoleBlockers(hole = geometry.rect)
        } else {
            FullBlocker()
        }

        var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
        var tooltipShown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { tooltipShown = true }
        val tooltipAlpha by animateFloatAsState(
            targetValue = if (tooltipShown && tooltipSize != IntSize.Zero) {
                1f
            } else {
                0f
            },
            animationSpec = if (animatorsEnabled) tween(160) else snap(),
            label = "tooltipAlpha"
        )
        val tooltipScale by animateFloatAsState(
            targetValue = if (tooltipShown) 1f else 0.95f,
            animationSpec = if (animatorsEnabled) spring(dampingRatio = 0.85f, stiffness = 340f) else snap(),
            label = "tooltipScale"
        )
        val centerX = containerSize.width / 2f
        val centerY = containerSize.height / 2f
        val anchor = if (showHole) rectAnim.value else Rect(
            left = centerX,
            top = centerY,
            right = centerX,
            bottom = centerY
        )
        val placement = with(densityPx) {
            placeTooltip(
                anchor = anchor,
                tooltip = tooltipSize,
                container = containerSize,
                insets = insets,
                marginPx = marginPx,
                gapPx = gapPx
            )
        }
        val maxTooltipHeight = with(densityPx) {
            val holeTop = if (showHole) rectAnim.value.top else (containerSize.height / 2f)
            val holeBottom = if (showHole) rectAnim.value.bottom else (containerSize.height / 2f)
            val fitsBelow = containerSize.height - insets.bottom - holeBottom - gapPx - marginPx
            val fitsAbove = holeTop - insets.top - gapPx - marginPx
            val room = maxOf(fitsBelow, fitsAbove, 0f)
            min(room, containerSize.height * 0.6f).toDp().coerceAtLeast(180.dp)
        }
        val maxTooltipWidth = if (maxWidth > 600.dp) GuideTokens.TooltipMaxWidthExpanded else GuideTokens.TooltipMaxWidth

        val isLastStep = index == chapter.steps.lastIndex
        val primaryLabel = when {
            step.interaction == StepInteraction.SHOW_ME -> stringResource(R.string.onboarding_show_me)
            isLastStep -> stringResource(R.string.onboarding_finish)
            else -> stringResource(R.string.onboarding_next)
        }
        val stepLabel = stringResource(
            R.string.onboarding_step_label,
            chapter.order,
            index + 1,
            chapter.steps.size
        )

        Box(
            modifier = Modifier
                .offset { placement.offset }
                .graphicsLayer {
                    alpha = tooltipAlpha
                    scaleX = tooltipScale
                    scaleY = tooltipScale
                }
                .widthIn(min = GuideTokens.TooltipMinWidth, max = maxTooltipWidth)
                .fillMaxWidth(0.9f)
                .onSizeChanged { tooltipSize = it }
        ) {
            OnboardingTooltip(
                stepLabel = stepLabel,
                title = stepTitle,
                body = stepBody,
                progress = (index + 1).toFloat() / chapter.steps.size,
                primaryLabel = primaryLabel,
                maxHeight = maxTooltipHeight,
                onPrimary = {
                    if (step.interaction == StepInteraction.SHOW_ME && step.action != GuideAction.None) {
                        actions.perform(step.action)
                    }
                    controller.next()
                },
                onBack = if (index > 0) ({ controller.back() }) else null,
                onSkip = { controller.skipChapter() },
                onClose = { controller.close() },
                onActivateTarget = if (
                    step.interaction == StepInteraction.SHOW_ME &&
                    step.action != GuideAction.None
                ) {
                    { actions.perform(step.action) }
                } else {
                    null
                },
                activateTargetLabel = stringResource(R.string.onboarding_a11y_activate_target)
            )
        }

        val arrowAlpha by animateFloatAsState(
            targetValue = if (rectAnim.isRunning && animatorsEnabled) 0f else 1f,
            animationSpec = if (animatorsEnabled) {
                tween(if (rectAnim.isRunning) 90 else 220)
            } else {
                snap()
            },
            label = "guideArrowAlpha"
        )
        if (tooltipSize != IntSize.Zero && geometry != null) {
            val arrowPx = with(densityPx) { 8.dp.toPx() }
            val arrowY = if (placement.side == TooltipSide.Below) {
                placement.offset.y.toFloat() - arrowPx
            } else {
                (placement.offset.y + tooltipSize.height).toFloat()
            }
            TooltipArrow(
                side = placement.side,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (placement.offset.x + placement.arrowX - arrowPx).toInt(),
                            y = arrowY.toInt()
                        )
                    }
                    .graphicsLayer { alpha = arrowAlpha }
            )
        }

        AnimatedVisibility(
            visible = showExitConfirm,
            enter = fadeIn(tween(160)) +
                scaleIn(initialScale = 0.94f, animationSpec = tween(200)),
            exit = fadeOut(tween(120)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            ExitConfirmation(
                onStay = { showExitConfirm = false },
                onLeave = {
                    showExitConfirm = false
                    controller.close()
                }
            )
        }
    }
}

@Composable
private fun FullBlocker() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .consumeAllPointerEvents()
    )
}

private fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun HoleBlockers(hole: Rect) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val top = hole.top.coerceIn(0f, height.toFloat())
        val bottom = hole.bottom.coerceIn(0f, height.toFloat())
        val left = hole.left.coerceIn(0f, width.toFloat())
        val right = hole.right.coerceIn(0f, width.toFloat())

        BlockerBand(width.toFloat(), top, 0f, 0f, density)
        BlockerBand(width.toFloat(), height - bottom, 0f, bottom, density)
        BlockerBand(left, bottom - top, 0f, top, density)
        BlockerBand(width - right, bottom - top, right, top, density)
    }
}

@Composable
private fun BlockerBand(widthPx: Float, heightPx: Float, leftPx: Float, topPx: Float, density: androidx.compose.ui.unit.Density) {
    if (widthPx <= 0f || heightPx <= 0f) return
    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .width(with(density) { widthPx.toDp() })
            .height(with(density) { heightPx.toDp() })
            .clearAndSetSemantics { }
            .consumeAllPointerEvents()
    )
}

@Composable
private fun ExitConfirmation(onStay: () -> Unit, onLeave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .width(320.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_exit_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_exit_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                ZenithGroupedButton(size = ZenithButtonSize.Large) {
                    ZenithButtonWeighted(
                        onClick = onStay,
                        text = stringResource(R.string.onboarding_exit_stay),
                        type = ZenithButtonType.Tonal,
                        size = ZenithButtonSize.Large,
                        isFirst = true,
                        isLast = false
                    )
                    ZenithButtonWeighted(
                        onClick = onLeave,
                        text = stringResource(R.string.onboarding_exit_leave),
                        type = ZenithButtonType.Filled,
                        size = ZenithButtonSize.Large,
                        isFirst = false,
                        isLast = true
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterCompleteSheet(
    controller: OnboardingController,
    chapterId: String,
    isReplay: Boolean,
    questCompleted: Boolean
) {
    val chapter = guideChapterById(chapterId) ?: return
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(chapterId) {
        if (!isReplay) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val nextChapter = guideChapterById(chapter.nextChapterId)
    val also = stringResource(chapter.closingAlsoRes).lines().filter { it.isNotBlank() }
    val badgeName = stringResource(chapter.badgeNameRes)

    CelebrationSheet(
        onDismiss = { controller.close() },
        actions = { dismissThen ->
            ZenithGroupedButton(size = ZenithButtonSize.Large) {
                ZenithButtonWeighted(
                    onClick = { dismissThen { controller.close() } },
                    text = stringResource(R.string.onboarding_close),
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Large,
                    isFirst = true,
                    isLast = false
                )
                ZenithButtonWeighted(
                    onClick = { dismissThen { controller.continueAfterChapter() } },
                    text = if (nextChapter != null) {
                        stringResource(R.string.onboarding_next_chapter)
                    } else {
                        stringResource(R.string.onboarding_continue)
                    },
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Large,
                    isFirst = false,
                    isLast = true
                )
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = chapter.tier.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.onboarding_chapter_complete_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = badgeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isReplay) {
                stringResource(R.string.onboarding_replay_complete)
            } else {
                stringResource(R.string.onboarding_badge_earned, badgeName)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(chapter.closingMessageRes),
            style = MaterialTheme.typography.bodyMedium
        )
        if (chapterId == SHIELD_CHAPTER_ID) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_quest_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (questCompleted) R.string.onboarding_quest_done
                    else R.string.onboarding_quest_pending
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (also.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_also_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            also.forEach { line ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("·", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MasterySheet(controller: OnboardingController, summary: String) {
    val context = LocalContext.current

    CelebrationSheet(
        onDismiss = { controller.close() },
        actions = { dismissThen ->
            ZenithGroupedButton(size = ZenithButtonSize.Large) {
                ZenithButtonWeighted(
                    onClick = { dismissThen { controller.claimMastery() } },
                    text = stringResource(R.string.onboarding_mastery_claim),
                    type = ZenithButtonType.Filled,
                    size = ZenithButtonSize.Large,
                    isFirst = true,
                    isLast = false
                )
                ZenithButtonWeighted(
                    onClick = { dismissThen { shareMastery(context, summary) } },
                    text = stringResource(R.string.onboarding_share_path),
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Large,
                    isFirst = false,
                    isLast = true
                )
            }
        }
    ) {
        Text(
            text = stringResource(R.string.onboarding_ladder_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProfileTier.entries.forEach { tier ->
                Icon(
                    imageVector = tier.icon,
                    contentDescription = tier.title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_mastery_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_mastery_body),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MasteredSheet(controller: OnboardingController) {
    CelebrationSheet(
        onDismiss = { controller.dismissCelebration() },
        actions = { dismissThen ->
            ZenithButton(
                onClick = { dismissThen { controller.dismissCelebration() } },
                type = ZenithButtonType.Filled,
                size = ZenithButtonSize.Large,
                fillMaxWidth = true,
                text = stringResource(R.string.onboarding_close)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = ProfileTier.M.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_mastered_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_mastered_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CelebrationSheet(
    onDismiss: () -> Unit,
    actions: @Composable (dismissThen: (() -> Unit) -> Unit) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    content = content
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions { action ->
                        scope.launch {
                            runCatching { sheetState.hide() }
                            action()
                        }
                    }
                }
            }
        }
    }
}

private fun Rect.isComfortablyVisibleIn(container: IntSize): Boolean {
    if (width <= 0f || height <= 0f) return false
    if (container.width <= 0 || container.height <= 0) return false
    val visibleWidth =
        (min(right, container.width.toFloat()) - max(left, 0f)).coerceAtLeast(0f)
    val visibleHeight =
        (min(bottom, container.height.toFloat()) - max(top, 0f)).coerceAtLeast(0f)
    if (visibleWidth <= 0f || visibleHeight <= 0f) return false
    val requiredHeight = min(height * 0.6f, container.height * 0.5f)
    return visibleWidth >= width * 0.6f && visibleHeight >= requiredHeight
}

private fun shareMastery(context: android.content.Context, summary: String) {
    sendShare(context, "Zenith Guided Path — Zenith Master\n$summary")
}

private fun sendShare(context: android.content.Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.onboarding_share_path)))
    }
}
