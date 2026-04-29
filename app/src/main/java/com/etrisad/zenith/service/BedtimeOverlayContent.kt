package com.etrisad.zenith.service

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeOverlayContent(
    packageName: String,
    appName: String,
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
    
    val userPrefsRepo = remember { UserPreferencesRepository(context) }
    val userPrefs by userPrefsRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())

    var startExitTimer by remember { mutableStateOf(false) }
    val exitProgress by animateFloatAsState(
        targetValue = if (startExitTimer) 1f else 0f,
        animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
        label = "exitProgress"
    )

    val showButtonProgress = exitProgress >= 0.4f

    val bedtimeUiState by produceState(
        initialValue = Triple(0f, "0m", ""),
        userPrefs
    ) {
        while (true) {
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            
            val startParts = userPrefs.bedtimeStartTime.split(":")
            val endParts = userPrefs.bedtimeEndTime.split(":")
            val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
            val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

            val totalDuration = if (endMinutes > startMinutes) {
                endMinutes - startMinutes
            } else {
                (1440 - startMinutes) + endMinutes
            }

            val elapsed = if (startMinutes <= endMinutes) {
                (currentMinutes - startMinutes).coerceAtLeast(0)
            } else {
                if (currentMinutes >= startMinutes) {
                    currentMinutes - startMinutes
                } else {
                    (1440 - startMinutes) + currentMinutes
                }
            }

            val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
            val remaining = (totalDuration - elapsed).coerceAtLeast(0)
            val h = remaining / 60
            val m = remaining % 60
            
            val formattedTime = if (h > 0) "${h}h ${m}m" else "${m}m"
            
            value = Triple(progress, formattedTime, userPrefs.bedtimeEndTime)
            delay(30000)
        }
    }

    val backgroundAlphaState = animateFloatAsState(
        targetValue = if (showContent) 0.6f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "backgroundAlpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
        startExitTimer = true
    }

    LaunchedEffect(exitProgress) {
        if (exitProgress >= 1f) {
            showContent = false
            delay(400)
            onCloseApp()
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = backgroundAlphaState.value }
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        )

        AnimatedVisibility(
            visible = showContent,
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
            Card(
                modifier = Modifier
                    .let { 
                        if (isLandscape) it.widthIn(max = 640.dp).fillMaxHeight(0.9f) 
                        else it.fillMaxWidth().fillMaxHeight(0.9f) 
                    }
                    .align(Alignment.BottomCenter)
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(60.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Bedtime,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Bedtime Mode",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = appName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(40.dp))

                            Box(contentAlignment = Alignment.Center) {
                                CircularWavyProgressIndicator(
                                    progress = { bedtimeUiState.first },
                                    modifier = Modifier.size(220.dp),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    amplitude = { 1f },
                                    wavelength = 58.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = bedtimeUiState.second,
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Until ${bedtimeUiState.third}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))

                            Text(
                                text = "Rest is productive too. This app is restricted until your bedtime ends.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                showContent = false
                                delay(400)
                                onCloseApp()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(MaterialTheme.shapes.large),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Close App",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            AnimatedVisibility(
                                visible = showButtonProgress,
                                enter = expandHorizontally(
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                                ) + fadeIn(),
                                exit = shrinkHorizontally() + fadeOut()
                            ) {
                                CircularWavyProgressIndicator(
                                    progress = { exitProgress },
                                    modifier = Modifier
                                        .padding(start = 12.dp)
                                        .size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                    trackStroke = with(density) { Stroke(width = 2.dp.toPx()) },
                                    stroke = with(density) { Stroke(width = 2.dp.toPx()) },
                                    wavelength = 8.dp,
                                    amplitude = { 1f }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
