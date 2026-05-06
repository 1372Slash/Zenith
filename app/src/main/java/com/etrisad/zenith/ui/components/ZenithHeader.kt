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
    onBack: () -> Unit,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isHome = currentRoute == Screen.Home.route
    val isDeepScreen =
        currentRoute == Screen.UsageStats.route || 
        currentRoute == Screen.Bedtime.route ||
        currentRoute?.startsWith("app_detail") == true

    val sideSlotWidth = 68.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
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

        // Title Overlay (Absolute Center)
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
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                .graphicsLayer {
                    // Use a more expressive easing for the scroll-to-hide transition
                    val fraction = scrollBehavior.state.collapsedFraction
                    alpha = (1f - fraction).coerceAtLeast(0f)
                    // Add a subtle scale effect to make it feel less "stiff"
                    val scale = 1f - (fraction * 0.08f)
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

        // Overlay Sisi Kiri (Navigation)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .widthIn(min = sideSlotWidth)
                .graphicsLayer {
                    val fraction = scrollBehavior.state.collapsedFraction
                    alpha = (1f - fraction).coerceAtLeast(0f)
                    val scale = 1f - (fraction * 0.12f)
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

        // Overlay Sisi Kanan (Actions)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .widthIn(min = sideSlotWidth)
                .graphicsLayer {
                    val fraction = scrollBehavior.state.collapsedFraction
                    alpha = (1f - fraction).coerceAtLeast(0f)
                    val scale = 1f - (fraction * 0.12f)
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
