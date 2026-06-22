package com.etrisad.zenith.ui.components.overlay

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.ui.components.ZenithButtonSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindDownOverlayContent(
    packageName: String,
    appName: String,
    sessionUsed: Boolean,
    userPreferences: com.etrisad.zenith.data.preferences.UserPreferences,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.toBitmap(width = 120, height = 120).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    var showContent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val delayDurationSeconds = 30
    val delayProgressAnimatable = remember { Animatable(0f) }
    var isDelaying by remember { mutableStateOf(!sessionUsed) }
    val autoKickProgress = remember { Animatable(0f) }

    LaunchedEffect(sessionUsed) {
        if (sessionUsed) {
            delay(2000)
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                autoKickProgress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            showContent = false
            delay(400)
            onCloseApp()
        } else {
            autoKickProgress.snapTo(0f)
        }
    }

    val motivationalMessages = remember {
        listOf(
            "Time for a quick stretch!",
            "Have you had enough water today?",
            "Take 3 deep breaths...",
            "Ready to crush your goals?",
            "Productivity is a marathon, not a sprint.",
            "Check your to-do list for a quick win!",
            "A small step today is a big leap tomorrow.",
            "Stay focused, stay mindful.",
            "Remember your homework or tasks!",
            "Do one small productive thing now.",
            "Do 15 Pushups"
        )
    }
    val randomMessage = remember(isDelaying) {
        if (isDelaying) motivationalMessages.random() else ""
    }

    LaunchedEffect(isDelaying) {
        if (isDelaying && !sessionUsed) {
            val totalDuration = delayDurationSeconds * 1000L
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / totalDuration).coerceIn(0f, 1f)
                delayProgressAnimatable.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            isDelaying = false
        } else if (!isDelaying && !sessionUsed) {
            delay(2000)
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                autoKickProgress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            showContent = false
            delay(400)
            onCloseApp()
        }
    }

    val backgroundAlphaState = animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    InterceptBottomSheet(
        visible = showContent,
        backgroundAlpha = backgroundAlphaState.value,
        isLandscape = isLandscape,
        maxWidthLandscape = 720.dp,
        showBedtimePill = true,
        userPreferences = userPreferences
    ) { _ ->
        if (isLandscape) {
            LandscapeWindDownLayout(
                appName = appName,
                appIcon = appIcon,
                sessionUsed = sessionUsed,
                isDelaying = isDelaying,
                randomMessage = randomMessage,
                delayProgressAnimatable = delayProgressAnimatable,
                delayDurationSeconds = delayDurationSeconds,
                autoKickProgress = autoKickProgress.value,
                onAllowUse = { minutes ->
                    scope.launch {
                        showContent = false
                        delay(400)
                        onAllowUse(minutes)
                    }
                },
                onCloseApp = {
                    scope.launch {
                        showContent = false
                        delay(400)
                        onCloseApp()
                    }
                }
            )
        } else {
            PortraitWindDownLayout(
                appName = appName,
                appIcon = appIcon,
                sessionUsed = sessionUsed,
                isDelaying = isDelaying,
                randomMessage = randomMessage,
                delayProgressAnimatable = delayProgressAnimatable,
                delayDurationSeconds = delayDurationSeconds,
                autoKickProgress = autoKickProgress.value,
                onAllowUse = { minutes ->
                    scope.launch {
                        showContent = false
                        delay(400)
                        onAllowUse(minutes)
                    }
                },
                onCloseApp = {
                    scope.launch {
                        showContent = false
                        delay(400)
                        onCloseApp()
                    }
                }
            )
        }
    }
}

@Composable
fun PortraitWindDownLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    sessionUsed: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    autoKickProgress: Float,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        WindDownHeader(appName, appIcon, sessionUsed)

        Spacer(modifier = Modifier.height(32.dp))

        WindDownActionSection(
            sessionUsed = sessionUsed,
            isDelaying = isDelaying,
            randomMessage = randomMessage,
            delayProgressAnimatable = delayProgressAnimatable,
            delayDurationSeconds = delayDurationSeconds,
            onAllowUse = onAllowUse
        )

        Spacer(modifier = Modifier.height(24.dp))

        CloseAppTextButton(
            onCloseApp = onCloseApp,
            autoKickProgress = { autoKickProgress },
            size = ZenithButtonSize.ExtraLarge
        )
    }
}

@Composable
fun LandscapeWindDownLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    sessionUsed: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    autoKickProgress: Float,
    onAllowUse: (Int) -> Unit,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .displayCutoutPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WindDownHeader(appName, appIcon, sessionUsed, isSmall = true)
            }

            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WindDownActionSection(
                    sessionUsed = sessionUsed,
                    isDelaying = isDelaying,
                    randomMessage = randomMessage,
                    delayProgressAnimatable = delayProgressAnimatable,
                    delayDurationSeconds = delayDurationSeconds,
                    onAllowUse = onAllowUse,
                    isSmall = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                CloseAppTextButton(
                    onCloseApp = onCloseApp,
                    autoKickProgress = { autoKickProgress },
                    size = ZenithButtonSize.Large
                )
            }
        }
    }
}

@Composable
private fun WindDownHeader(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    sessionUsed: Boolean,
    isSmall: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(if (isSmall) 64.dp else 80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier.size(if (isSmall) 48.dp else 60.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(if (isSmall) 36.dp else 48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Wind Down",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = appName,
        style = if (isSmall) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = if (sessionUsed) 
            "You've used your Wind Down session for this app. It's time to rest." 
            else "Bedtime is approaching. Take a final look before we lock things down.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun WindDownActionSection(
    sessionUsed: Boolean,
    isDelaying: Boolean,
    randomMessage: String,
    delayProgressAnimatable: Animatable<Float, AnimationVector1D>,
    delayDurationSeconds: Int,
    onAllowUse: (Int) -> Unit,
    isSmall: Boolean = false
) {
    if (!sessionUsed) {
        AnimatedContent(
            targetState = isDelaying,
            transitionSpec = {
                (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                 scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                    .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)))
            },
            label = "delayContent"
        ) { delaying ->
            if (delaying) {
                WindDownDelaySection(randomMessage, delayProgressAnimatable, delayDurationSeconds)
            } else {
                WindDownDurationSection(onAllowUse = onAllowUse)
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(if (isSmall) 36.dp else 48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No more sessions available tonight.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindDownDelaySection(
    message: String,
    progressAnimatable: Animatable<Float, AnimationVector1D>,
    durationSeconds: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = { progressAnimatable.value },
                modifier = Modifier.size(100.dp),
                color = MaterialTheme.colorScheme.tertiary,
                amplitude = { 1f },
                wavelength = 30.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            val secondsLeft by remember(durationSeconds) {
                derivedStateOf { ceil((1f - progressAnimatable.value) * durationSeconds).toInt() }
            }
            Text(
                text = "${secondsLeft}s",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
fun WindDownDurationSection(onAllowUse: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select your final session duration:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        WindDownDurationGrid(onAllowUse = onAllowUse)
    }
}

@Composable
fun WindDownDurationGrid(onAllowUse: (Int) -> Unit) {
    val outerRadius = 24.dp
    val innerRadius = 4.dp

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WindDownDurationButton(
                minutes = 2,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = outerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
            WindDownDurationButton(
                minutes = 5,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = outerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WindDownDurationButton(
                minutes = 8,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = outerRadius, bottomEnd = innerRadius)
            )
            WindDownDurationButton(
                minutes = 10,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = outerRadius)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WindDownDurationButton(
    minutes: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    val buttonScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        buttonScale.animateTo(
            targetValue = 1f,
            initialVelocity = 1.5f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    FilledTonalButton(
        onClick = { onAllowUse(minutes) },
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
            }
            .clip(shape),
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Text(
            text = "$minutes mins",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
