package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenithHeader(
    currentRoute: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    isNavRailVisible: Boolean = false,
    userName: String = "User",
    categoryName: String? = null,
    onBack: () -> Unit,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isHome = currentRoute == Screen.Home.route
    val isDeepScreen =
        currentRoute == Screen.UsageStats.route || 
        currentRoute == Screen.Bedtime.route ||
        currentRoute == Screen.GracePeriod.route ||
        currentRoute == Screen.EyeCare.route ||
        currentRoute == Screen.DatabaseDebug.route ||
        currentRoute == Screen.DataRepairment.route ||
        currentRoute == Screen.FontTest.route ||
        currentRoute == Screen.GSFlexCustomizer.route ||
        currentRoute == Screen.SystemUsageDebug.route ||
        currentRoute == Screen.OverlayAppearance.route ||
        currentRoute == Screen.Alarm.route ||
        currentRoute?.startsWith("settings_category") == true ||
        currentRoute?.startsWith("app_detail") == true

    val sideSlotWidth = 68.dp

    val smoothedAlpha by animateFloatAsState(
        targetValue = (1f - scrollBehavior.state.collapsedFraction).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SmoothedHeaderAlpha"
    )
    val smoothedOffset by animateFloatAsState(
        targetValue = scrollBehavior.state.heightOffset,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "SmoothedHeaderOffset"
    )
    val smoothedFraction = 1f - smoothedAlpha

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = smoothedAlpha }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
    ) {
        CenterAlignedTopAppBar(
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars,
            navigationIcon = {},
            title = {},
            actions = {},
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )

        val titleBias by animateFloatAsState(
            targetValue = if (isDeepScreen) -1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "TitleAlignment"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .offset { IntOffset(0, smoothedOffset.roundToInt()) }
                .graphicsLayer {
                    alpha = smoothedAlpha
                    val scale = 1f - (smoothedFraction * 0.08f)
                    scaleX = scale
                    scaleY = scale
                }
                .zIndex(1f),
            contentAlignment = BiasAlignment(horizontalBias = titleBias, verticalBias = 0f)
        ) {
            val titleXOffset by animateDpAsState(
                targetValue = if (isDeepScreen) 80.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "TitleOffset"
            )

            val title = when {
                isHome -> "home_header"
                currentRoute == Screen.Focus.route -> "Focus"
                currentRoute == Screen.Settings.route -> "Settings"
                currentRoute == Screen.UsageStats.route -> "Usage Stats"
                currentRoute == Screen.Bedtime.route -> "Bedtime"
                currentRoute == Screen.GracePeriod.route -> "Grace Period"
                currentRoute == Screen.EyeCare.route -> "Eye Care"
                currentRoute == Screen.DatabaseDebug.route -> "Database"
                currentRoute == Screen.DataRepairment.route -> "Data Repairment"
                currentRoute == Screen.FontTest.route -> "M3 Expressive Editor"
                currentRoute == Screen.GSFlexCustomizer.route -> "GS Flex Designer"
                currentRoute == Screen.SystemUsageDebug.route -> "System Usage Fetch"
                currentRoute == Screen.OverlayAppearance.route -> "Overlay Appearance"
                currentRoute == Screen.Alarm.route -> "Alarm"
                currentRoute?.startsWith("settings_category") == true -> categoryName ?: "Settings"
                currentRoute?.startsWith("app_detail") == true -> "App Detail"
                else -> "Zenith"
            }

            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
                },
                label = "HeaderTitleAnimation",
                modifier = Modifier.offset(x = titleXOffset)
            ) { state ->
                if (state == "home_header") {
                    var showAppName by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(2500)
                        showAppName = true
                    }

                    AnimatedContent(
                        targetState = showAppName,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 2 })
                                .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                        },
                        label = "HomeAppNameAnimation"
                    ) { isAppName ->
                        Text(
                            text = if (isAppName) "Zenith" else "Welcome Back, $userName",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = if (isAppName) FontWeight.ExtraBold else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = state,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = if (isDeepScreen) TextAlign.Start else TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, smoothedOffset.roundToInt()) }
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .widthIn(min = sideSlotWidth)
                .graphicsLayer {
                    alpha = smoothedAlpha
                    val scale = 1f - (smoothedFraction * 0.12f)
                    scaleX = scale
                    scaleY = scale
                }
                .zIndex(2f),
            contentAlignment = Alignment.CenterStart
        ) {
            navigationIcon?.invoke()

            AnimatedVisibility(
                visible = isDeepScreen,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                        scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                        slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                       scaleOut(targetScale = 0.92f) +
                       slideOutHorizontally(targetOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(onClick = onBack)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, smoothedOffset.roundToInt()) }
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .widthIn(min = sideSlotWidth)
                .graphicsLayer {
                    alpha = smoothedAlpha
                    val scale = 1f - (smoothedFraction * 0.12f)
                    scaleX = scale
                    scaleY = scale
                }
                .zIndex(3f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
