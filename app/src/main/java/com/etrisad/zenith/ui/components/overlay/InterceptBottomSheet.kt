package com.etrisad.zenith.ui.components.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.ThemeConfig
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
    dragHandleIncentiveTier: IncentiveTier? = null,
    dragHandleBonusUsesLeft: Int = 0,
    sheetContentAlpha: Float = 1f,
    contentKey: Any? = Unit,
    content: @Composable ColumnScope.(key: Any?) -> Unit
) {
    val isDark = when (userPreferences?.themeConfig) {
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
        else -> isSystemInDarkTheme()
    }
    
    val context = LocalContext.current
    val currentScheme = if (userPreferences?.dynamicColor == true && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (isDark) darkColorScheme() else lightColorScheme()
    }

    val paletteScheme = remember(userPreferences, isDark, currentScheme) {
        if (userPreferences == null) return@remember currentScheme

        val paletteId = userPreferences.overlayPaletteId
        val customHue = userPreferences.overlayCustomHue

        val selectedPalette = PREDEFINED_PALETTES.find { it.id == paletteId }

        when {
            paletteId == "dynamic" -> {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(currentScheme.primary.toArgb(), hsv)
                val hue = hsv[0]
                val tintSurface = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isDark) 0.28f else 0.08f, if (isDark) 0.12f else 0.97f)))
                currentScheme.copy(
                    surface = tintSurface,
                    surfaceContainer = tintSurface,
                    surfaceContainerLow = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isDark) 0.18f else 0.04f, if (isDark) 0.16f else 0.96f))),
                    surfaceContainerHigh = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isDark) 0.38f else 0.12f, if (isDark) 0.24f else 0.9f)))
                )
            }
            paletteId == "custom" -> {
                val customSeed = Color(android.graphics.Color.HSVToColor(floatArrayOf(customHue, 0.6f, 0.55f)))
                generateDynamicColorScheme(customSeed, isDark, currentScheme, isForPreview = false)
            }
            selectedPalette != null -> {
                generateDynamicColorScheme(selectedPalette.seed, isDark, currentScheme, isForPreview = false)
            }
            else -> currentScheme
        }
    }

    MaterialTheme(colorScheme = paletteScheme) {
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
                                userPreferences?.overlayFullScreen == true -> Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(if (isLandscape) 0.95f else 0.9f)
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
                            .then(
                                if (maxHeightFraction != null || userPreferences?.overlayFullScreen == true)
                                    Modifier.weight(1f)
                                else Modifier.wrapContentHeight()
                            )
                            .imePadding()
                            .graphicsLayer { alpha = sheetContentAlpha },
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (userPreferences?.overlayPaletteId == "monochrome") {
                                if (isDark) Color(0xFF1B1B1B) else Color(0xFFF5F5F5)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = if (maxHeightFraction != null || userPreferences?.overlayFullScreen == true)
                                Modifier.fillMaxSize()
                            else Modifier.fillMaxWidth()
                        ) {
                            OverlayDragHandleWithIndicators(
                                currentUses = if (isLandscape) null else dragHandleCurrentUses,
                                maxUses = if (isLandscape) null else dragHandleMaxUses,
                                emergencyCount = if (isLandscape) null else dragHandleEmergencyCount,
                                isIncentiveLocked = if (isLandscape) false else dragHandleIsIncentiveLocked,
                                incentiveTier = if (isLandscape) null else dragHandleIncentiveTier,
                                bonusUsesLeft = if (isLandscape) 0 else dragHandleBonusUsesLeft
                            )
                            Box(
                                modifier = Modifier.then(
                                    if (maxHeightFraction != null || userPreferences?.overlayFullScreen == true)
                                        Modifier.weight(1f)
                                    else Modifier.wrapContentHeight()
                                )
                            ) {
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
                                    Column(
                                        modifier = if (maxHeightFraction != null || userPreferences?.overlayFullScreen == true)
                                            Modifier.fillMaxSize()
                                        else Modifier.fillMaxWidth()
                                    ) {
                                        content(key)
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
