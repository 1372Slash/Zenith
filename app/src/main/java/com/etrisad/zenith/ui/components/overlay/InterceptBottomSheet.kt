package com.etrisad.zenith.ui.components.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences

@Composable
fun InterceptBottomSheet(
    visible: Boolean,
    backgroundAlpha: Float,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    maxWidthLandscape: Dp = 640.dp,
    showBedtimePill: Boolean = false,
    userPreferences: UserPreferences? = null,
    maxHeightFraction: Float? = null,
    dragHandleCurrentUses: Int? = null,
    dragHandleMaxUses: Int? = null,
    dragHandleEmergencyCount: Int? = null,
    dragHandleIsIncentiveLocked: Boolean = false,
    contentKey: Any? = Unit,
    content: @Composable ColumnScope.(key: Any?) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlpha }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } }
        )

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
            ) + fadeOut(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .then(
                        when {
                            maxHeightFraction != null -> Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(maxHeightFraction)
                            isLandscape -> Modifier
                                .widthIn(max = maxWidthLandscape)
                                .wrapContentHeight()
                            else -> Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        }
                    )
                    .align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showBedtimePill && userPreferences != null) {
                    BedtimeAlertPill(
                        userPreferences = userPreferences,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Column {
                        OverlayDragHandleWithIndicators(
                            currentUses = if (isLandscape) null else dragHandleCurrentUses,
                            maxUses = if (isLandscape) null else dragHandleMaxUses,
                            emergencyCount = if (isLandscape) null else dragHandleEmergencyCount,
                            isIncentiveLocked = if (isLandscape) false else dragHandleIsIncentiveLocked
                        )
                        AnimatedContent(
                            targetState = contentKey,
                            transitionSpec = {
                                (slideInHorizontally { it } + fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )).togetherWith(
                                    slideOutHorizontally { -it } + fadeOut(
                                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                                    )
                                ).using(SizeTransform(clip = false))
                            },
                            label = "interceptContent"
                        ) { key ->
                            Column(Modifier.fillMaxWidth()) {
                                content(key)
                            }
                        }
                    }
                }
            }
        }
    }
}
