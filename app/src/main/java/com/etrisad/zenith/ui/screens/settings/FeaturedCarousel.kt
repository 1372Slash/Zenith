package com.etrisad.zenith.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.etrisad.zenith.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class FeaturedItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isTertiary: Boolean = false,
    val onClick: () -> Unit = {}
)

@Composable
fun FeaturedCarousel(
    onNavigate: (String) -> Unit = {},
    updateAvailable: GitHubRelease? = null,
    onUpdateClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val suggestions = remember {
        listOf(
            FeaturedItem(
                title = "Total Usage Pill",
                description = "Monitor your day with a tiny floating pill",
                icon = Icons.Outlined.TrackChanges,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Features")) }
            ),
            FeaturedItem(
                title = "Audio Focus Intercept",
                description = "Auto-pause music when apps are blocked",
                icon = Icons.Outlined.VolumeUp,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Features")) }
            ),
            FeaturedItem(
                title = "Goal Caller Overlay",
                description = "Full-screen nudge when goals are met",
                icon = Icons.Outlined.NotificationsActive,
                onClick = { onNavigate(Screen.Focus.route) }
            ),
            FeaturedItem(
                title = "GS Flex Customizer",
                description = "Fine-tune font weight and variations",
                icon = Icons.Outlined.SettingsSuggest,
                onClick = { onNavigate(Screen.GSFlexCustomizer.route) }
            ),
            FeaturedItem(
                title = "Early Kick Feature",
                description = "Build mindfulness by exiting apps early",
                icon = Icons.Outlined.ExitToApp,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Features")) }
            ),
            FeaturedItem(
                title = "Session Overlay",
                description = "Track app usage with a floating pill",
                icon = Icons.Outlined.VideoLabel,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Features")) }
            ),
            FeaturedItem(
                title = "Floating Tab Bar",
                description = "Enable a modern navigation experience",
                icon = Icons.Outlined.MoreHoriz,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Appearance")) }
            ),
            FeaturedItem(
                title = "Smart Data Repair",
                description = "Fix usage sync issues with system logs",
                icon = Icons.Outlined.Build,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Data Management")) }
            ),
            FeaturedItem(
                title = "Customize Appearance",
                description = "Change themes, fonts, and colors",
                icon = Icons.Outlined.Palette,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Appearance")) }
            ),
            FeaturedItem(
                title = "Mindful Gateway",
                description = "Breathe before you scroll impulsively",
                icon = Icons.Outlined.AutoFixHigh,
                onClick = { onNavigate(Screen.SettingsCategory.createRoute("Features")) }
            ),
            FeaturedItem(
                title = "Bedtime Guard",
                description = "Configure your wind-down schedule",
                icon = Icons.Outlined.Bedtime,
                onClick = { onNavigate(Screen.Bedtime.route) }
            ),
            FeaturedItem(
                title = "Join Community",
                description = "View development and sneak peaks",
                icon = Icons.Outlined.Chat,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://whatsapp.com/channel/0029VbAKkhlAojYyegxvV83V"))
                    context.startActivity(intent)
                }
            )
        ).shuffled().take(3)
    }

    val items = remember(suggestions, updateAvailable) {
        val list = mutableListOf<FeaturedItem>()

        if (BuildConfig.SHOW_UPDATES && updateAvailable != null) {
            list.add(
                FeaturedItem(
                    title = "Update Available",
                    description = "Version ${updateAvailable.tagName} is now available",
                    icon = Icons.Outlined.Update,
                    isTertiary = true,
                    onClick = onUpdateClick
                )
            )
        }
        
        list.add(
            FeaturedItem(
                title = "Support Development",
                description = "Fuel our mission with a tip on Ko-fi",
                icon = Icons.Outlined.Favorite,
                isTertiary = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/1372slash"))
                    context.startActivity(intent)
                }
            )
        )
        list.add(
            FeaturedItem(
                title = "Star on GitHub",
                description = "Love Zenith? Give us a star on GitHub!",
                icon = Icons.Outlined.Star,
                isTertiary = true,
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/1372Slash/Zenith"))
                    context.startActivity(intent)
                }
            )
        )
        
        list + suggestions
    }

    val itemsCount = items.size
    val pagerState = rememberPagerState { itemsCount }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = 4.dp
    
    val carouselAnimationSpec = remember { 
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ) 
    }

    val autoScrollProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pagerState.settledPage, itemsCount, lifecycleOwner) {
        if (itemsCount > 1) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                autoScrollProgress.snapTo(0f)
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                    autoScrollProgress.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }
                
                if (!pagerState.isScrollInProgress) {
                    val nextStep = (pagerState.currentPage + 1) % itemsCount
                    pagerState.animateScrollToPage(
                        page = nextStep,
                        animationSpec = carouselAnimationSpec
                    )
                }
            }
        } else {
            autoScrollProgress.snapTo(0f)
        }
    }

    val interactionSources = remember(itemsCount) { List(itemsCount) { MutableInteractionSource() } }
    
    val expressiveSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    val visualProgress by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val spacingPx = with(density) { spacing.toPx() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                for (i in 0 until itemsCount) {
                    val dist = (visualProgress - i).absoluteValue
                    val currentWeight = when {
                        dist < 1.0f -> {
                            val maxW = if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f
                            lerp(maxW, 0.1f, dist)
                        }
                        dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                        else -> 0.0f
                    }

                    if (currentWeight > 0.005f) {
                        val currentCornerRadius = if (dist < 1.0f) lerp(1000f, 24f, dist) else 24f
                        val currentAlpha = when {
                            dist < 1.0f -> lerp(1f, 0.4f, dist)
                            dist < 2.0f -> lerp(0.4f, 0f, dist - 1.0f)
                            else -> 0f
                        }
                        
                        val baseColor = if (items[i].isTertiary) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer

                        FeaturedStepCard(
                            item = items[i],
                            dist = dist,
                            isToTheLeft = i < visualProgress,
                            interactionSource = interactionSources[i],
                            modifier = Modifier.weight(currentWeight),
                            containerColor = baseColor.copy(alpha = currentAlpha),
                            cornerRadius = currentCornerRadius.dp,
                            motionSpec = expressiveSpring
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .alpha(0f)
                    .pointerInput(itemsCount) {
                        detectTapGestures { offset ->
                            val tapX = offset.x
                            var currentX = 0f
                            val currentProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction

                            val renderedWeights = (0 until itemsCount).map { i ->
                                val dist = (currentProgress - i).absoluteValue
                                when {
                                    dist < 1.0f -> lerp(if (i == 0 || i == itemsCount - 1) 0.9f else 0.82f, 0.1f, dist)
                                    dist < 2.0f -> lerp(0.1f, 0.0f, dist - 1.0f)
                                    else -> 0.0f
                                }
                            }

                            val visibleIndices = renderedWeights.indices.filter { renderedWeights[it] > 0.005f }
                            val totalGaps = (visibleIndices.size - 1).coerceAtLeast(0)
                            val availableWidthForCards = totalWidthPx - (spacingPx * totalGaps)

                            for (i in visibleIndices) {
                                val weight = renderedWeights[i]
                                val cardWidth = weight * availableWidthForCards

                                if (tapX >= currentX && tapX <= currentX + cardWidth) {
                                    coroutineScope.launch {
                                        val press = PressInteraction.Press(offset)
                                        interactionSources[i].emit(press)
                                        delay(150)
                                        interactionSources[i].emit(PressInteraction.Release(press))

                                        if (pagerState.currentPage == i) {
                                            items[i].onClick()
                                        } else {
                                            pagerState.animateScrollToPage(
                                                page = i,
                                                animationSpec = carouselAnimationSpec
                                            )
                                        }
                                    }
                                    break
                                }
                                currentX += cardWidth + spacingPx
                            }
                        }
                    }
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(itemsCount) { index ->
                val isActive = pagerState.currentPage == index
                CarouselIndicator(
                    isActive = isActive,
                    progress = if (isActive && pagerState.settledPage == index && autoScrollProgress.value < 1f) autoScrollProgress.value else 0f,
                    motionSpec = expressiveSpring
                )
            }
        }
    }
}

@Composable
fun CarouselIndicator(
    isActive: Boolean,
    progress: Float,
    motionSpec: SpringSpec<Float>
) {
    val width = if (isActive) 32.dp else 8.dp
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = spring(
            dampingRatio = motionSpec.dampingRatio,
            stiffness = motionSpec.stiffness
        ),
        label = "IndicatorWidth"
    )
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val animatedColor by animateColorAsState(
        targetValue = color,
        label = "IndicatorColor"
    )

    Box(
        modifier = Modifier
            .width(animatedWidth)
            .height(6.dp)
            .clip(CircleShape)
            .background(animatedColor)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
fun RowScope.FeaturedStepCard(
    item: FeaturedItem,
    dist: Float,
    isToTheLeft: Boolean,
    interactionSource: MutableInteractionSource,
    containerColor: Color,
    cornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    motionSpec: SpringSpec<Float>
) {
    val isFocused = dist < 0.6f
    val contentColor = if (item.isTertiary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {}
            ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        AnimatedContent(
            targetState = isFocused,
            transitionSpec = {
                val springSpec = spring<IntOffset>(
                    stiffness = motionSpec.stiffness,
                    dampingRatio = motionSpec.dampingRatio
                )
                
                val slideIn = if (targetState) {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                } else {
                    slideInHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                }

                val slideOut = if (targetState) {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) it else -it }
                } else {
                    slideOutHorizontally(animationSpec = springSpec) { if (isToTheLeft) -it else it }
                }

                (fadeIn(animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)) + slideIn +
                 scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)))
                    .togetherWith(
                        fadeOut(animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio)) + slideOut +
                        scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = motionSpec.stiffness, dampingRatio = motionSpec.dampingRatio))
                    )
            },
            label = "CardContentTransition",
            modifier = Modifier.fillMaxSize()
        ) { focused ->
            if (focused) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = contentColor.copy(alpha = 0.1f),
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = contentColor
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isToTheLeft) 
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft 
                        else 
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
