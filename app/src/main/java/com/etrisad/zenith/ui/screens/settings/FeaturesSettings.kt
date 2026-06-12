package com.etrisad.zenith.ui.screens.settings

import android.app.WallpaperManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.ForegroundNotificationStatusMode
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun FeaturesSettings(
    preferences: UserPreferences,
    onTotalUsagePillEnabledChange: (Boolean) -> Unit,
    onForegroundNotificationStatusModeChange: (ForegroundNotificationStatusMode) -> Unit,
    onSessionUsageOverlayEnabledChange: (Boolean) -> Unit,
    onSessionUsageOverlaySizeChange: (Int) -> Unit,
    onSessionUsageOverlayOpacityChange: (Int) -> Unit,
    onMindfulGatewayEnabledChange: (Boolean) -> Unit,
    onEarlyKickEnabledChange: (Boolean) -> Unit,
    onInterceptAudioFocusEnabledChange: (Boolean) -> Unit,
    onBatteryStatsResetEnabledChange: (Boolean) -> Unit
) {
    Column {
        PreferenceCategory(title = "Interface Overlays")

        SettingsToggle(
            title = "Total Usage Pill",
            description = "Show your total screen time in the mindful pause overlay",
            checked = preferences.totalUsagePillEnabled,
            onCheckedChange = onTotalUsagePillEnabledChange,
            icon = Icons.Outlined.Public,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Session Usage Overlay",
            description = "Show a floating HUD with remaining time when an app is allowed",
            checked = preferences.sessionUsageOverlayEnabled,
            onCheckedChange = onSessionUsageOverlayEnabledChange,
            icon = Icons.Outlined.Timer,
            shape = if (preferences.sessionUsageOverlayEnabled) RoundedCornerShape(8.dp) else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        AnimatedVisibility(
            visible = preferences.sessionUsageOverlayEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                HUDAppearanceSettings(
                    size = preferences.sessionUsageOverlaySize,
                    opacity = preferences.sessionUsageOverlayOpacity,
                    onSizeChange = onSessionUsageOverlaySizeChange,
                    onOpacityChange = onSessionUsageOverlayOpacityChange,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Status Notifications")

        ForegroundNotificationStatusSelector(
            selectedMode = preferences.foregroundNotificationStatusMode,
            onModeChange = onForegroundNotificationStatusModeChange,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Advanced Control")

        SettingsToggle(
            title = "Mindful Gateway",
            description = "Interrupt every non-whitelisted app with a mindful pause, even without a specific shield",
            checked = preferences.mindfulGatewayEnabled,
            onCheckedChange = onMindfulGatewayEnabledChange,
            icon = Icons.Outlined.AutoFixHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Early Kick",
            description = "Optionally eject from apps 5 minutes before your time limit expires",
            checked = preferences.earlyKickEnabled,
            onCheckedChange = onEarlyKickEnabledChange,
            icon = Icons.Outlined.ExitToApp,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Pause Media",
            description = "Automatically pause media when an overlay appears",
            checked = preferences.interceptAudioFocusEnabled,
            onCheckedChange = onInterceptAudioFocusEnabledChange,
            icon = Icons.Outlined.MusicNote,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Battery Usage Reset",
            description = "Track app usage since last charge and provide a reset option in app details",
            checked = preferences.batteryStatsResetEnabled,
            onCheckedChange = onBatteryStatsResetEnabledChange,
            icon = Icons.Outlined.BatteryChargingFull,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )
    }
}

@Composable
fun ForegroundNotificationStatusSelector(
    selectedMode: ForegroundNotificationStatusMode,
    onModeChange: (ForegroundNotificationStatusMode) -> Unit,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status Notification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Choose what Zenith shows while monitoring is active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val modeOptions = listOf(
                ForegroundNotificationStatusMode.DAILY_USAGE to "Usage",
                ForegroundNotificationStatusMode.ACTIVE_FOCUS to "Focus",
                ForegroundNotificationStatusMode.DEFAULT to "Default"
            )

            ZenithToggleButtonGroup(
                options = modeOptions.map { ZenithToggleOption(text = it.second) },
                selectedIndices = setOf(modeOptions.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)),
                onToggle = { index -> onModeChange(modeOptions[index].first) },
                size = ZenithButtonSize.Medium,
                isInsideContainer = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HUDAppearanceSettings(
    size: Int,
    opacity: Int,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val context = LocalContext.current
    val wallpaperDrawable = remember {
        try {
            WallpaperManager.getInstance(context).drawable
        } catch (_: Exception) {
            null
        }
    }

    var localSize by remember(size) { mutableFloatStateOf(size.toFloat()) }
    var localOpacity by remember(opacity) { mutableFloatStateOf(opacity.toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "HUD Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoSizeSelectSmall, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = localSize,
                    onValueChange = { localSize = it },
                    onValueChangeFinished = { onSizeChange(localSize.toInt()) },
                    valueRange = 50f..200f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.PhotoSizeSelectLarge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Size: ${localSize.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Opacity, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = localOpacity,
                    onValueChange = { localOpacity = it },
                    onValueChangeFinished = { onOpacityChange(localOpacity.toInt()) },
                    valueRange = 20f..100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Opacity: ${localOpacity.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Preview",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (wallpaperDrawable != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberDrawablePainter(wallpaperDrawable),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                }

                val animatedScale by animateFloatAsState(
                    targetValue = localSize / 100f,
                    label = "HUDScale"
                )
                val animatedOpacity by animateFloatAsState(
                    targetValue = localOpacity / 100f,
                    label = "HUDOpacity"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            alpha = animatedOpacity
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        progress = { 0.75f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        stroke = Stroke(width = 6.dp.value),
                        trackStroke = Stroke(width = 6.dp.value),
                        wavelength = 20.dp,
                        waveSpeed = 0.dp
                    )
                    Text(
                        text = "15m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
