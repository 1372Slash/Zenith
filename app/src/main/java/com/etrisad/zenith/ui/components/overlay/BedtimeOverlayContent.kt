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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButtonSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeOverlayContent(
    packageName: String,
    appName: String,
    userPreferences: UserPreferences,
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
    
    var startExitTimer by remember { mutableStateOf(false) }
    val exitProgress by animateFloatAsState(
        targetValue = if (startExitTimer) 1f else 0f,
        animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
        label = "exitProgress"
    )

    LaunchedEffect(startExitTimer) {
        if (startExitTimer) {
            delay(5000)
            showContent = false
            delay(400)
            onCloseApp()
        }
    }

    val bedtimeUiState by produceState(
        initialValue = Triple(0f, "0m", ""),
        key1 = userPreferences
    ) {
        val cal = Calendar.getInstance()
        while (true) {
            cal.timeInMillis = System.currentTimeMillis()
            val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            
            val startParts = userPreferences.bedtimeStartTime.split(":")
            val endParts = userPreferences.bedtimeEndTime.split(":")
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
            
            value = Triple(progress, formattedTime, userPreferences.bedtimeEndTime)
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    InterceptBottomSheet(
        visible = showContent,
        backgroundAlpha = backgroundAlphaState.value,
        isLandscape = isLandscape,
        maxWidthLandscape = 720.dp,
        maxHeightFraction = if (isLandscape) 0.95f else 0.9f,
        showBedtimePill = false
    ) { _ ->
        if (isLandscape) {
            LandscapeBedtimeLayout(
                appName = appName,
                appIcon = appIcon,
                bedtimeUiState = bedtimeUiState,
                exitProgress = exitProgress,
                onCloseApp = {
                    scope.launch {
                        showContent = false
                        delay(400)
                        onCloseApp()
                    }
                }
            )
        } else {
            PortraitBedtimeLayout(
                appName = appName,
                appIcon = appIcon,
                bedtimeUiState = bedtimeUiState,
                exitProgress = exitProgress,
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
fun PortraitBedtimeLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    bedtimeUiState: Triple<Float, String, String>,
    exitProgress: Float,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BedtimeHeader(appName, appIcon)

            Spacer(modifier = Modifier.height(40.dp))

            BedtimeProgress(bedtimeUiState)

            Spacer(modifier = Modifier.height(40.dp))

            BedtimeDescription()
        }

        CloseAppTextButton(
            onCloseApp = onCloseApp,
            autoKickProgress = { exitProgress },
            size = ZenithButtonSize.ExtraLarge
        )
    }
}

@Composable
fun LandscapeBedtimeLayout(
    appName: String,
    appIcon: androidx.compose.ui.graphics.ImageBitmap?,
    bedtimeUiState: Triple<Float, String, String>,
    exitProgress: Float,
    onCloseApp: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .displayCutoutPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BedtimeHeader(appName, appIcon, isSmall = true)
                Spacer(modifier = Modifier.height(20.dp))
                BedtimeDescription()
            }

            Column(
                modifier = Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BedtimeProgress(bedtimeUiState, isSmall = true)
                Spacer(modifier = Modifier.height(24.dp))
                CloseAppTextButton(
                    onCloseApp = onCloseApp,
                    autoKickProgress = { exitProgress },
                    size = ZenithButtonSize.Large
                )
            }
        }
    }
}

@Composable
private fun BedtimeHeader(appName: String, appIcon: androidx.compose.ui.graphics.ImageBitmap?, isSmall: Boolean = false) {
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
        text = "Bedtime Mode",
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
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BedtimeProgress(bedtimeUiState: Triple<Float, String, String>, isSmall: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { bedtimeUiState.first },
            modifier = Modifier.size(if (isSmall) 160.dp else 220.dp),
            color = MaterialTheme.colorScheme.tertiary,
            amplitude = { 1f },
            wavelength = if (isSmall) 40.dp else 58.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = bedtimeUiState.second,
                style = if (isSmall) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
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
}

@Composable
private fun BedtimeDescription() {
    Text(
        text = "Rest is productive too. This app is restricted until your bedtime ends.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

