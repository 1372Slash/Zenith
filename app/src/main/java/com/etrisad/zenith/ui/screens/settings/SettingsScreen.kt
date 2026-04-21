package com.etrisad.zenith.ui.screens.settings

import android.app.WallpaperManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.util.BackupUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(preferencesRepository: UserPreferencesRepository) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(
        initial = UserPreferences(
            themeConfig = ThemeConfig.FOLLOW_SYSTEM,
            dynamicColor = true,
            accessibilityDisabled = false,
            screenTimeTargetMinutes = 0,
            emergencyRechargeDurationMinutes = 60,
            delayAppDurationSeconds = 30,
            sessionUsageOverlayEnabled = false,
            sessionUsageOverlaySize = 100,
            sessionUsageOverlayOpacity = 90,
            whitelistedPackages = emptySet()
        )
    )
    val coroutineScope = rememberCoroutineScope()
    var showWhitelistSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    BackupUtils.backupDatabase(context, it).onSuccess {
                        Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                    }.onFailure { e ->
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    BackupUtils.restoreDatabase(context, it).onSuccess {
                        Toast.makeText(context, "Restore successful! Restarting app...", Toast.LENGTH_LONG).show()
                        BackupUtils.restartApp(context)
                    }.onFailure { e ->
                        Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    SettingsScreenContent(
        preferences = preferences,
        onThemeChange = { theme ->
            coroutineScope.launch {
                preferencesRepository.setThemeConfig(theme)
            }
        },
        onDynamicColorChange = { enabled ->
            coroutineScope.launch {
                preferencesRepository.setDynamicColor(enabled)
            }
        },
        onAccessibilityDisabledChange = { disabled ->
            coroutineScope.launch {
                preferencesRepository.setAccessibilityDisabled(disabled)
            }
        },
        onSetTarget = { minutes ->
            coroutineScope.launch {
                preferencesRepository.setScreenTimeTarget(minutes)
            }
        },
        onSetEmergencyRecharge = { minutes ->
            coroutineScope.launch {
                preferencesRepository.setEmergencyRechargeDuration(minutes)
            }
        },
        onSetDelayAppDuration = { seconds ->
            coroutineScope.launch {
                preferencesRepository.setDelayAppDuration(seconds)
            }
        },
        onSessionUsageOverlayEnabledChange = { enabled ->
            coroutineScope.launch {
                preferencesRepository.setSessionUsageOverlayEnabled(enabled)
            }
        },
        onSessionUsageOverlaySizeChange = { size ->
            coroutineScope.launch {
                preferencesRepository.setSessionUsageOverlaySize(size)
            }
        },
        onSessionUsageOverlayOpacityChange = { opacity ->
            coroutineScope.launch {
                preferencesRepository.setSessionUsageOverlayOpacity(opacity)
            }
        },
        showWhitelistSheet = showWhitelistSheet,
        onShowWhitelistSheetChange = { showWhitelistSheet = it },
        onSetWhitelistedPackages = { packages ->
            coroutineScope.launch {
                preferencesRepository.setWhitelistedPackages(packages)
            }
        },
        onBackup = { backupLauncher.launch("zenith_backup_${System.currentTimeMillis()}.db") },
        onRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
    )
}

@Composable
fun SettingsScreenContent(
    preferences: UserPreferences,
    onThemeChange: (ThemeConfig) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onAccessibilityDisabledChange: (Boolean) -> Unit,
    onSetTarget: (Int) -> Unit,
    onSetEmergencyRecharge: (Int) -> Unit,
    onSetDelayAppDuration: (Int) -> Unit,
    onSessionUsageOverlayEnabledChange: (Boolean) -> Unit,
    onSessionUsageOverlaySizeChange: (Int) -> Unit,
    onSessionUsageOverlayOpacityChange: (Int) -> Unit,
    showWhitelistSheet: Boolean,
    onShowWhitelistSheetChange: (Boolean) -> Unit,
    onSetWhitelistedPackages: (Set<String>) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    var showTargetSheet by remember { mutableStateOf(false) }
    var showEmergencyRechargeSheet by remember { mutableStateOf(false) }
    var showDelayAppSheet by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 100.dp
            )
        ) {
            item {
                SettingsHeader()
            }

            item {
                PreferenceCategory(title = "General")
            }

            item {
                SettingsToggle(
                    title = "Disable Accessibility",
                    description = "Remove Accessibility requirements from permission checks",
                    checked = preferences.accessibilityDisabled,
                    onCheckedChange = onAccessibilityDisabledChange,
                    icon = Icons.Outlined.AccessibilityNew,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Emergency Recharge Time",
                    summary = preferences.emergencyRechargeDurationMinutes.let { mins ->
                        val label = when {
                            mins >= 1440 -> "${mins / 1440} day${if (mins / 1440 > 1) "s" else ""}"
                            mins >= 60 -> "${mins / 60} hour${if (mins / 60 > 1) "s" else ""}"
                            else -> "$mins minutes"
                        }
                        "1 charge every $label"
                    },
                    onClick = { showEmergencyRechargeSheet = true },
                    icon = Icons.Outlined.Shield,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Daily Screen Time Target",
                    summary = if (preferences.screenTimeTargetMinutes > 0) {
                        val h = preferences.screenTimeTargetMinutes / 60
                        val m = preferences.screenTimeTargetMinutes % 60
                        "Target set to ${if (h > 0) "${h}h " else ""}${m}m"
                    } else "No target set",
                    onClick = { showTargetSheet = true },
                    icon = Icons.Outlined.Edit,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "App Opening Delay",
                    summary = preferences.delayAppDurationSeconds.let { secs ->
                        val label = when {
                            secs >= 3600 -> "${secs / 3600}h"
                            secs >= 60 -> "${secs / 60}m"
                            else -> "${secs}s"
                        }
                        "Wait $label before reopening"
                    },
                    onClick = { showDelayAppSheet = true },
                    icon = Icons.Outlined.History,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsToggle(
                    title = "Session Usage Overlay",
                    description = "Show a floating HUD with remaining time when an app is allowed",
                    checked = preferences.sessionUsageOverlayEnabled,
                    onCheckedChange = onSessionUsageOverlayEnabledChange,
                    icon = Icons.Outlined.Timer,
                    shape = if (preferences.sessionUsageOverlayEnabled) 
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    else RoundedCornerShape(8.dp)
                )
            }

            if (preferences.sessionUsageOverlayEnabled) {
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    HUDAppearanceSettings(
                        size = preferences.sessionUsageOverlaySize,
                        opacity = preferences.sessionUsageOverlayOpacity,
                        onSizeChange = onSessionUsageOverlaySizeChange,
                        onOpacityChange = onSessionUsageOverlayOpacityChange
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Whitelist Apps",
                    summary = "${preferences.whitelistedPackages.size} apps bypassed",
                    onClick = { onShowWhitelistSheetChange(true) },
                    icon = Icons.Outlined.VerifiedUser,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceCategory(title = "Data Management")
            }

            item {
                SettingsActionItem(
                    title = "Backup Data",
                    summary = "Save your settings and schedules to a file",
                    onClick = onBackup,
                    icon = Icons.Outlined.Backup,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Restore Data",
                    summary = "Load data from a previous backup file",
                    onClick = onRestore,
                    icon = Icons.Outlined.Restore,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceCategory(title = "Appearance")
            }

            item {
                ThemeSelector(
                    selectedTheme = preferences.themeConfig,
                    onThemeChange = onThemeChange,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsToggle(
                    title = "Dynamic Color",
                    description = "Apply system wallpaper colors (Android 12+)",
                    checked = preferences.dynamicColor,
                    onCheckedChange = onDynamicColorChange,
                    icon = Icons.Outlined.Palette,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceCategory(title = "About")
            }

            item {
                AboutCard()
            }
        }
    }

    if (showTargetSheet) {
        com.etrisad.zenith.ui.screens.home.ScreenTimeTargetBottomSheet(
            initialMinutes = preferences.screenTimeTargetMinutes,
            onDismiss = { showTargetSheet = false },
            onSave = { minutes ->
                onSetTarget(minutes)
                showTargetSheet = false
            }
        )
    }

    if (showEmergencyRechargeSheet) {
        EmergencyRechargeBottomSheet(
            initialMinutes = preferences.emergencyRechargeDurationMinutes,
            onDismiss = { showEmergencyRechargeSheet = false },
            onSave = { minutes ->
                onSetEmergencyRecharge(minutes)
                showEmergencyRechargeSheet = false
            }
        )
    }

    if (showDelayAppSheet) {
        DelayAppBottomSheet(
            initialSeconds = preferences.delayAppDurationSeconds,
            onDismiss = { showDelayAppSheet = false },
            onSave = { seconds ->
                onSetDelayAppDuration(seconds)
                showDelayAppSheet = false
            }
        )
    }

    if (showWhitelistSheet) {
        WhitelistBottomSheet(
            initialWhitelisted = preferences.whitelistedPackages,
            onDismiss = { onShowWhitelistSheetChange(false) },
            onSave = { packages ->
                onSetWhitelistedPackages(packages)
                onShowWhitelistSheetChange(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelayAppBottomSheet(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var seconds by remember { mutableIntStateOf(initialSeconds) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "App Opening Delay",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set how many seconds to wait before a user can reopen an app after being kicked out.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (seconds >= 5) seconds -= 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }

                Text(
                    text = if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                IconButton(
                    onClick = { if (seconds < 3600) seconds += 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                listOf(15, 30, 60).forEach { preset ->
                    FilterChip(
                        selected = seconds == preset,
                        onClick = { seconds = preset },
                        label = { Text("${preset}s") },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSave(seconds) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Delay", modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyRechargeBottomSheet(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var minutes by remember { mutableIntStateOf(initialMinutes) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Emergency Recharge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Set how long it takes to recover one emergency use count.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (minutes >= 5) minutes -= 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }

                Text(
                    text = when {
                        minutes >= 1440 -> "${minutes / 1440}d" + if (minutes % 1440 > 0) " ${ (minutes % 1440) / 60 }h" else ""
                        minutes >= 60 -> "${minutes / 60}h" + if (minutes % 60 > 0) " ${minutes % 60}m" else ""
                        else -> "${minutes}m"
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                IconButton(
                    onClick = { if (minutes < 1440) minutes += 5 },
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                val presets = listOf(60, 180, 1440)
                presets.forEach { preset ->
                    val label = when {
                        preset >= 1440 -> "${preset / 1440}d"
                        preset >= 60 -> "${preset / 60}h"
                        else -> "${preset}m"
                    }
                    FilterChip(
                        selected = minutes == preset,
                        onClick = { minutes = preset },
                        label = { Text(label) },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onSave(minutes) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Save Duration", modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
fun SettingsHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, bottom = 24.dp)
    ) {
        Text(
            text = "Personalize your Zenith experience",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun ThemeSelector(
    selectedTheme: ThemeConfig,
    onThemeChange: (ThemeConfig) -> Unit,
    shape: Shape = RoundedCornerShape(24.dp)
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
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val themeOptions = listOf(
                ThemeConfig.FOLLOW_SYSTEM to "System",
                ThemeConfig.LIGHT to "Light",
                ThemeConfig.DARK to "Dark"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                themeOptions.forEachIndexed { index, (config, label) ->
                    ThemeOptionButton(
                        label = label,
                        selected = selectedTheme == config,
                        onClick = { onThemeChange(config) },
                        isFirst = index == 0,
                        isLast = index == themeOptions.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val widthScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 1.5f
            selected -> 1.25f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "WidthScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer 
                      else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BgColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ContentColor"
    )

    val innerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "InnerRadius"
    )
    
    val outerRadius = 24.dp
    
    val shape = when {
        selected -> CircleShape
        isFirst -> RoundedCornerShape(
            topStart = outerRadius, 
            bottomStart = outerRadius, 
            topEnd = innerRadius, 
            bottomEnd = innerRadius
        )
        isLast -> RoundedCornerShape(
            topEnd = outerRadius, 
            bottomEnd = outerRadius, 
            topStart = innerRadius, 
            bottomStart = innerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }

    Box(
        modifier = Modifier
            .weight(widthScale)
            .height(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    icon: ImageVector,
    shape: Shape
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    onOpacityChange: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaperDrawable = remember {
        try {
            WallpaperManager.getInstance(context).drawable
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "HUD Appearance",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Size Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoSizeSelectSmall, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = size.toFloat(),
                    onValueChange = { onSizeChange(it.toInt()) },
                    valueRange = 50f..200f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.PhotoSizeSelectLarge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Size: $size%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Opacity Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Opacity, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = opacity.toFloat(),
                    onValueChange = { onOpacityChange(it.toInt()) },
                    valueRange = 20f..100f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Outlined.Contrast, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Opacity: $opacity%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Preview
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

                val scale = size / 100f
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = opacity / 100f
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
                        amplitude = { 1.5f },
                        wavelength = 20.dp
                    )
                    Text(
                        text = "15m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
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
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zenith v1.1",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Reclaim your focus with high-energy digital wellbeing intervention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
