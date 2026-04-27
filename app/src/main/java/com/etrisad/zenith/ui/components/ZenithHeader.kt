package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.navigation.Screen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZenithHeader(
    currentRoute: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    isNavRailVisible: Boolean = false,
    onBack: () -> Unit
) {
    val isHome = currentRoute == Screen.Home.route
    val isDeepScreen =
        currentRoute == Screen.UsageStats.route || currentRoute?.startsWith("app_detail") == true

    val railOffset = if (isNavRailVisible) 80.dp else 0.dp
    
    val centeringOffset by animateDpAsState(
        targetValue = if (isNavRailVisible && !isDeepScreen) -(railOffset / 2) else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "CenteringOffset"
    )

    // Fixed symmetrical width to ensure perfect centering in all modes
    val sideSlotWidth = 56.dp

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
            navigationIcon = {
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedVisibility(
                        visible = isDeepScreen,
                        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                                scaleIn(initialScale = 0.8f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                        exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + 
                               scaleOut(targetScale = 0.8f)
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
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            title = {
                // Title alignment: 0f for center, -1f for start
                val titleBias by animateFloatAsState(
                    targetValue = if (isDeepScreen) -1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "TitleAlignment"
                )

                // Increased padding for deep screens to provide better separation from the back button
                val titlePadding by animateDpAsState(
                    targetValue = if (isDeepScreen) 32.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "TitlePadding"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = titlePadding)
                        .offset { IntOffset(x = centeringOffset.roundToPx(), y = 0) },
                    contentAlignment = BiasAlignment(horizontalBias = titleBias, verticalBias = 0f)
                ) {
                    val title = when {
                        isHome -> "home_header"
                        currentRoute == Screen.Focus.route -> "Focus"
                        currentRoute == Screen.Settings.route -> "Settings"
                        currentRoute == Screen.UsageStats.route -> "Usage Stats"
                        currentRoute?.startsWith("app_detail") == true -> "App Detail"
                        else -> "Zenith"
                    }

                    AnimatedContent(
                        targetState = title,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.92f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.92f))
                        },
                        label = "HeaderTitleAnimation"
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
                                    text = if (isAppName) "Zenith" else "Welcome Back, User",
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
            },
            actions = {
                // Balance the side slots precisely to ensure perfect centering
                Spacer(modifier = Modifier.width(sideSlotWidth))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )
    }
}
