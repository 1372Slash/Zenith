package com.etrisad.zenith.ui.screens.settings

import android.widget.Toast
import android.app.WallpaperManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.ForegroundNotificationStatusMode
import com.etrisad.zenith.data.preferences.MindfulGatewayDefaults
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.util.hasCalendarPermission
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.focus.CardGroup
import com.etrisad.zenith.ui.components.focus.ZenithDropdown
import com.etrisad.zenith.ui.navigation.Screen
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun FeaturesSettings(
    preferences: UserPreferences,
    onTotalUsagePillEnabledChange: (Boolean) -> Unit,
    onForegroundNotificationStatusModeChange: (ForegroundNotificationStatusMode) -> Unit,
    onSessionUsageOverlayEnabledChange: (Boolean) -> Unit,
    onSessionUsageOverlaySizeChange: (Int) -> Unit,
    onSessionUsageOverlayOpacityChange: (Int) -> Unit,
    onUsageGlimpseEnabledChange: (Boolean) -> Unit,
    onNavigateToEyeCare: () -> Unit,
    onMindfulGatewayEnabledChange: (Boolean) -> Unit,
    onEarlyKickEnabledChange: (Boolean) -> Unit,
    onInterceptAudioFocusEnabledChange: (Boolean) -> Unit,
    onBatteryStatsResetEnabledChange: (Boolean) -> Unit,
    onShowCurrentEventEnabledChange: (Boolean) -> Unit,
    onDailyRecapEnabledChange: (Boolean) -> Unit,
    onWeeklyInsightEnabledChange: (Boolean) -> Unit,
    onTrendMilestoneEnabledChange: (Boolean) -> Unit,
    onIncentiveLockEnabledChange: (Boolean) -> Unit,
    onIncentiveLockDisableRequest: () -> Unit,
    onIncentiveLockCancelDisableRequest: () -> Unit,
    onNavigateToGracePeriod: () -> Unit,
    onWebsiteAutoTrackingEnabledChange: (Boolean) -> Unit,
    onNavigateToLockdown: () -> Unit,
    onNavigateToTamperProtection: () -> Unit,
    onNavigateToPomodoro: () -> Unit,
    onNavigateToPausePoint: () -> Unit = {},
    onMindfulGatewayDefaultsSave: (
        maxUses: Int,
        refreshMinutes: Int,
        maxEmergencyUses: Int,
        remindersEnabled: Boolean,
        strictModeEnabled: Boolean,
        pausePointEnabled: Boolean,
        endOverlayEnabled: Boolean,
        autoQuitEnabled: Boolean,
    ) -> Unit,
    onNavigateToReelsShield: () -> Unit = {},
    goalCount: Int
) {
    val context = LocalContext.current
    val calendarPermissionGranted = remember { hasCalendarPermission(context) }
    var showConfirmSheet by remember { mutableStateOf(false) }
    var showGatewayDefaultsSheet by remember { mutableStateOf(false) }

    if (showGatewayDefaultsSheet) {
        MindfulGatewayDefaultsBottomSheet(
            initialMaxUses = preferences.mindfulGatewayMaxUses,
            initialRefreshMinutes = preferences.mindfulGatewayRefreshMinutes,
            initialMaxEmergencyUses = preferences.mindfulGatewayMaxEmergencyUses,
            initialRemindersEnabled = preferences.mindfulGatewayRemindersEnabled,
            initialStrictModeEnabled = preferences.mindfulGatewayStrictModeEnabled,
            initialPausePointEnabled = preferences.mindfulGatewayPausePointEnabled,
            initialEndOverlayEnabled = preferences.mindfulGatewayEndOverlayEnabled,
            initialAutoQuitEnabled = preferences.mindfulGatewayAutoQuitEnabled,
            onDismiss = { showGatewayDefaultsSheet = false },
            onSave = onMindfulGatewayDefaultsSave
        )
    }

    if (showConfirmSheet) {
        com.etrisad.zenith.ui.components.ConfirmBottomSheet(
            onDismiss = { showConfirmSheet = false },
            onConfirm = {
                onIncentiveLockDisableRequest()
                showConfirmSheet = false
            },
            leverCount = 10,
            showTimeSelection = false
        )
    }

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
            shape = RoundedCornerShape(8.dp)
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
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Usage Glimpse",
            description = "Briefly show your total screen time every 5 minutes in a corner HUD",
            checked = preferences.usageGlimpseEnabled,
            onCheckedChange = onUsageGlimpseEnabledChange,
            icon = Icons.Outlined.Visibility,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Show Current Event",
            description = if (calendarPermissionGranted) "Show your current calendar event with progress during app delay" else "Grant Calendar Access permission in Settings to enable",
            checked = preferences.showCurrentEvent,
            onCheckedChange = { enabled ->
                if (calendarPermissionGranted) {
                    onShowCurrentEventEnabledChange(enabled)
                } else {
                    Toast.makeText(context, "Please enable Calendar Access permission in Settings", Toast.LENGTH_SHORT).show()
                }
            },
            icon = Icons.Outlined.CalendarMonth,
            enabled = calendarPermissionGranted,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Notifications")

        ForegroundNotificationStatusSelector(
            selectedMode = preferences.foregroundNotificationStatusMode,
            onModeChange = onForegroundNotificationStatusModeChange,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Daily Focus Recap",
            description = "Receive a daily summary of your total focus time and screen usage each evening",
            checked = preferences.dailyRecapEnabled,
            onCheckedChange = onDailyRecapEnabledChange,
            icon = Icons.Outlined.Today,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Weekly Insight",
            description = "Get a weekly breakdown comparing your focus trends across the week",
            checked = preferences.weeklyInsightEnabled,
            onCheckedChange = onWeeklyInsightEnabledChange,
            icon = Icons.Outlined.Assessment,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Trend Milestones",
            description = "Celebrate achievements like focus streaks and usage milestones",
            checked = preferences.trendMilestoneEnabled,
            onCheckedChange = onTrendMilestoneEnabledChange,
            icon = Icons.Outlined.EmojiEvents,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Entry Control")

        SettingsToggle(
            title = "Mindful Gateway",
            description = "Interrupt every restricted app with a mindful pause, even without a specific shield",
            checked = preferences.mindfulGatewayEnabled,
            onCheckedChange = onMindfulGatewayEnabledChange,
            icon = Icons.Outlined.AutoFixHigh,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        AnimatedVisibility(
            visible = preferences.mindfulGatewayEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Mindful Gateway Defaults",
                    summary = "Default uses, emergency, and pause behavior for every gateway shield",
                    onClick = { showGatewayDefaultsSheet = true },
                    icon = Icons.Outlined.Tune,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        val isDisablingIncentiveLock = preferences.incentiveLockDisableRequestTimestamp > 0

        SettingsToggle(
            title = "Incentive Lock",
            description = "Progressively unlock shields as you complete app goals. Bonus uses are awarded at 25% and 50% progress.",
            checked = preferences.incentiveLockEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    if (goalCount > 0) {
                        onIncentiveLockEnabledChange(true)
                    } else {
                        Toast.makeText(context, "Please add at least one app goal first", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showConfirmSheet = true
                }
            },
            icon = Icons.Outlined.Lock,
            enabled = (preferences.incentiveLockEnabled || goalCount > 0) && !isDisablingIncentiveLock,
            shape = RoundedCornerShape(8.dp)
        )

        AnimatedVisibility(
            visible = isDisablingIncentiveLock,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val deactivationTime = preferences.incentiveLockDisableRequestTimestamp + 3600000L
            var timeLeftMillis by remember(preferences.incentiveLockDisableRequestTimestamp) { 
                mutableLongStateOf(deactivationTime - System.currentTimeMillis()) 
            }
            
            LaunchedEffect(preferences.incentiveLockDisableRequestTimestamp) {
                if (preferences.incentiveLockDisableRequestTimestamp > 0) {
                    while (timeLeftMillis > 0) {
                        timeLeftMillis = deactivationTime - System.currentTimeMillis()
                        if (timeLeftMillis <= 0) {
                            onIncentiveLockEnabledChange(false)
                            onIncentiveLockCancelDisableRequest()
                        }
                        delay(1000)
                    }
                }
            }

            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Disabling Incentive Lock...",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val minutesLeft = (timeLeftMillis / 60000).coerceAtLeast(0)
                            val secondsLeft = ((timeLeftMillis % 60000) / 1000).coerceAtLeast(0)
                            val timeStr = String.format("%02d:%02d", minutesLeft, secondsLeft)
                            val targetTimeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(deactivationTime))
                            Text(
                                "Feature will be disabled in $timeStr (around $targetTimeStr)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = onIncentiveLockCancelDisableRequest) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Grace Period",
            summary = "Set a daily window where all restrictions are lifted (max 2 h; optional weekly edit lock)",
            onClick = onNavigateToGracePeriod,
            icon = Icons.Outlined.FreeBreakfast,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Eye Care",
            summary = "Remind you to look away every ${preferences.eyeCareWorkMinutes} minutes to reduce eye strain",
            onClick = onNavigateToEyeCare,
            icon = Icons.Outlined.RemoveRedEye,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Pomodoro",
            summary = "Lock down distractions and take puzzle-guided breaks",
            onClick = onNavigateToPomodoro,
            icon = Icons.Outlined.Visibility,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Lockdown",
            summary = if (preferences.lockdownEnabled && preferences.isInLockdown()) "Editing is currently restricted" else "Restrict editing during specific hours",
            onClick = onNavigateToLockdown,
            icon = Icons.Outlined.Lock,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Short Video")

        SettingsActionItem(
            title = "Reels Shield",
            summary = if (preferences.reelsShieldEnabled) {
                "On · ${preferences.reelsShieldDailyLimit} reels per day shared across ${preferences.reelsShieldPackages.size} apps"
            } else {
                "Count short videos watched across apps, then press back when the daily limit is reached"
            },
            onClick = onNavigateToReelsShield,
            icon = Icons.Outlined.SwapVert,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Protection")

        SettingsActionItem(
            title = "Tamper Protection",
            summary = if (preferences.tamperProtectionEnabled) "On. PIN required to turn it off" else "Add an extra Android step before Zenith can be uninstalled",
            onClick = onNavigateToTamperProtection,
            icon = Icons.Outlined.AdminPanelSettings,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Pause Point")

        SettingsActionItem(
            title = "Pause Point",
            summary = if (preferences.pausePointEnabled) {
                val count = preferences.pausePointTaskTypes.size
                if (count == 0) "Enabled (no task types selected)"
                else "Random tasks enabled ($count types)"
            } else "Disabled",
            onClick = onNavigateToPausePoint,
            icon = Icons.Outlined.PauseCircle,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Triggers")

        SettingsToggle(
            title = "Pause Media",
            description = "Automatically pause media when an overlay appears",
            checked = preferences.interceptAudioFocusEnabled,
            onCheckedChange = onInterceptAudioFocusEnabledChange,
            icon = Icons.Outlined.MusicNote,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Early Kick",
            description = "Eject you from an app 5 minutes before its time limit expires, as an advance warning",
            checked = preferences.earlyKickEnabled,
            onCheckedChange = onEarlyKickEnabledChange,
            icon = Icons.Outlined.ExitToApp,
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

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Website Tracking")

        SettingsToggle(
            title = "Auto Track Websites",
            description = "Automatically record time spent on all websites (not just shielded ones) and show them in usage stats",
            checked = preferences.websiteAutoTrackingEnabled,
            onCheckedChange = onWebsiteAutoTrackingEnabledChange,
            icon = Icons.Outlined.Language,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulGatewayDefaultsBottomSheet(
    initialMaxUses: Int,
    initialRefreshMinutes: Int,
    initialMaxEmergencyUses: Int,
    initialRemindersEnabled: Boolean,
    initialStrictModeEnabled: Boolean,
    initialPausePointEnabled: Boolean,
    initialEndOverlayEnabled: Boolean,
    initialAutoQuitEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    var maxUsesText by remember { mutableStateOf(initialMaxUses.toString()) }
    var refreshMinutes by remember { mutableIntStateOf(initialRefreshMinutes) }
    var maxEmergencyText by remember { mutableStateOf(initialMaxEmergencyUses.toString()) }
    var remindersEnabled by remember { mutableStateOf(initialRemindersEnabled) }
    var strictModeEnabled by remember { mutableStateOf(initialStrictModeEnabled) }
    var pausePointEnabled by remember { mutableStateOf(initialPausePointEnabled) }
    var endOverlayEnabled by remember { mutableStateOf(initialEndOverlayEnabled) }
    var autoQuitEnabled by remember { mutableStateOf(initialAutoQuitEnabled) }

    val refreshOptions = listOf(
        "Every 30 Minutes" to 30,
        "Every 1 Hour" to 60,
        "Every 2 Hours" to 120,
        "Every 6 Hours" to 360,
        "Every 12 Hours" to 720,
        "Every 24 Hours" to 1440
    )

    val currentMaxUses = maxUsesText.toIntOrNull() ?: initialMaxUses
    val currentMaxEmergency = maxEmergencyText.toIntOrNull() ?: initialMaxEmergencyUses

    val hasChanges = currentMaxUses != initialMaxUses ||
        refreshMinutes != initialRefreshMinutes ||
        currentMaxEmergency != initialMaxEmergencyUses ||
        remindersEnabled != initialRemindersEnabled ||
        strictModeEnabled != initialStrictModeEnabled ||
        pausePointEnabled != initialPausePointEnabled ||
        endOverlayEnabled != initialEndOverlayEnabled ||
        autoQuitEnabled != initialAutoQuitEnabled

    val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Mindful Gateway Defaults",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Default behavior for every mindful pause",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Zenith ships with these defaults, so nothing changes until you edit them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        PreferenceCategory(title = "Limits")

                        CardGroup(shape = topShape, containerColor = containerColor) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Timer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Times of Uses",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Skip allowance per refresh period",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        BasicTextField(
                                            value = maxUsesText,
                                            onValueChange = { if (it.all { char -> char.isDigit() }) maxUsesText = it },
                                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Refresh Period",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Usage reset interval",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                ZenithDropdown(
                                    options = refreshOptions,
                                    selectedOption = refreshMinutes,
                                    onOptionSelected = { refreshMinutes = it },
                                    width = 160.dp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = bottomShape, containerColor = containerColor) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Emergency Uses",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Emergency access uses",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        BasicTextField(
                                            value = maxEmergencyText,
                                            onValueChange = { if (it.all { char -> char.isDigit() }) maxEmergencyText = it },
                                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        PreferenceCategory(title = "Settings")

                        CardGroup(shape = topShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Show Reminders",
                                description = "Get notified before limit is reached",
                                checked = remindersEnabled,
                                onCheckedChange = { remindersEnabled = it },
                                icon = Icons.Outlined.NotificationsActive,
                                shape = topShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Strict Mode",
                                description = "No extensions allowed after limit",
                                checked = strictModeEnabled,
                                onCheckedChange = { strictModeEnabled = it },
                                icon = Icons.Outlined.GppGood,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Pause Point",
                                description = "Guard each app open with a Pause Point task",
                                checked = pausePointEnabled,
                                onCheckedChange = { pausePointEnabled = it },
                                icon = Icons.Outlined.PauseCircle,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "End Overlay",
                                description = "Show shield screen when a session ends",
                                checked = endOverlayEnabled,
                                onCheckedChange = { value ->
                                    endOverlayEnabled = value
                                    if (!value) autoQuitEnabled = true
                                },
                                icon = Icons.Outlined.Layers,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = bottomShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Auto Quit",
                                description = "Exit app automatically when session ends",
                                checked = autoQuitEnabled,
                                onCheckedChange = { value ->
                                    autoQuitEnabled = value
                                    if (!value) endOverlayEnabled = true
                                },
                                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                                shape = bottomShape
                            )
                        }

                        Spacer(modifier = Modifier.height(120.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithGroupedButton {
                    ZenithButtonWeighted(
                        onClick = {
                            maxUsesText = MindfulGatewayDefaults.MAX_USES_PER_PERIOD.toString()
                            refreshMinutes = MindfulGatewayDefaults.REFRESH_PERIOD_MINUTES
                            maxEmergencyText = MindfulGatewayDefaults.MAX_EMERGENCY_USES.toString()
                            remindersEnabled = MindfulGatewayDefaults.REMINDERS_ENABLED
                            strictModeEnabled = MindfulGatewayDefaults.STRICT_MODE_ENABLED
                            pausePointEnabled = MindfulGatewayDefaults.PAUSE_POINT_ENABLED
                            endOverlayEnabled = MindfulGatewayDefaults.END_OVERLAY_ENABLED
                            autoQuitEnabled = MindfulGatewayDefaults.AUTO_QUIT_ENABLED
                            onSave(
                                MindfulGatewayDefaults.MAX_USES_PER_PERIOD,
                                MindfulGatewayDefaults.REFRESH_PERIOD_MINUTES,
                                MindfulGatewayDefaults.MAX_EMERGENCY_USES,
                                MindfulGatewayDefaults.REMINDERS_ENABLED,
                                MindfulGatewayDefaults.STRICT_MODE_ENABLED,
                                MindfulGatewayDefaults.PAUSE_POINT_ENABLED,
                                MindfulGatewayDefaults.END_OVERLAY_ENABLED,
                                MindfulGatewayDefaults.AUTO_QUIT_ENABLED
                            )
                        },
                        text = "Reset",
                        type = ZenithButtonType.Tonal,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        weight = 1f,
                        isFirst = true,
                        isLast = false
                    )
                    ZenithButtonWeighted(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onSave(
                                    currentMaxUses,
                                    refreshMinutes,
                                    currentMaxEmergency,
                                    remindersEnabled,
                                    strictModeEnabled,
                                    pausePointEnabled,
                                    endOverlayEnabled,
                                    autoQuitEnabled
                                )
                                onDismiss()
                            }
                        },
                        text = "Save Defaults",
                        weight = 1.5f,
                        isFirst = false,
                        isLast = true,
                        enabled = hasChanges
                    )
                }
            }
        }
    }
}
