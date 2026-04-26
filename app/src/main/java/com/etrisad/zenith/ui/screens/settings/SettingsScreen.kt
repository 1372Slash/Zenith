package com.etrisad.zenith.ui.screens.settings

import android.app.WallpaperManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.util.BackupUtils
import com.etrisad.zenith.worker.BackupManager
import kotlinx.coroutines.delay
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
    var showRestoreConfirmSheet by remember { mutableStateOf(false) }
    var restoreMetadata by remember { mutableStateOf<BackupUtils.BackupMetadata?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    val backupManager = remember { BackupManager(context) }

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

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                // Grant persistent permission
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                coroutineScope.launch {
                    preferencesRepository.setBackupDirectoryUri(it.toString())
                    // Reschedule if enabled
                    if (preferences.autoBackupEnabled) {
                        backupManager.scheduleBackup(preferences.backupIntervalHours, it.toString())
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
                    val metadata = BackupUtils.getBackupMetadata(context, it)
                    if (metadata != null) {
                        restoreMetadata = metadata
                        pendingRestoreUri = it
                        showRestoreConfirmSheet = true
                    } else {
                        Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
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
        onRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
        onAutoBackupEnabledChange = { enabled ->
            coroutineScope.launch {
                preferencesRepository.setAutoBackupEnabled(enabled)
                if (enabled && preferences.backupDirectoryUri.isNotEmpty()) {
                    backupManager.scheduleBackup(preferences.backupIntervalHours, preferences.backupDirectoryUri)
                } else {
                    backupManager.cancelBackup()
                }
            }
        },
        onPickBackupDirectory = {
            directoryPickerLauncher.launch(null)
        },
        onSetBackupInterval = { hours ->
            coroutineScope.launch {
                preferencesRepository.setBackupIntervalHours(hours)
                if (preferences.autoBackupEnabled && preferences.backupDirectoryUri.isNotEmpty()) {
                    backupManager.scheduleBackup(hours, preferences.backupDirectoryUri)
                }
            }
        },
        onFloatingTabBarEnabledChange = { enabled ->
            coroutineScope.launch {
                preferencesRepository.setFloatingTabBarEnabled(enabled)
            }
        }
    )

    if (showRestoreConfirmSheet && restoreMetadata != null && pendingRestoreUri != null) {
        RestoreConfirmationBottomSheet(
            metadata = restoreMetadata!!,
            onDismiss = {
                showRestoreConfirmSheet = false
                pendingRestoreUri = null
            },
            onConfirm = {
                coroutineScope.launch {
                    showRestoreConfirmSheet = false
                    BackupUtils.restoreDatabase(context, pendingRestoreUri!!).onSuccess {
                        Toast.makeText(context, "Restore successful! Restarting app...", Toast.LENGTH_LONG).show()
                        BackupUtils.restartApp(context)
                    }.onFailure { e ->
                        Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    onRestore: () -> Unit,
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    onPickBackupDirectory: () -> Unit,
    onSetBackupInterval: (Int) -> Unit,
    onFloatingTabBarEnabledChange: (Boolean) -> Unit
) {
    var showTargetSheet by remember { mutableStateOf(false) }
    var showEmergencyRechargeSheet by remember { mutableStateOf(false) }
    var showDelayAppSheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { SettingsHeader(scrollBehavior) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 150.dp
            )
        ) {
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
                SettingsToggle(
                    title = "Auto Backup",
                    description = "Periodically backup your database to a folder",
                    checked = preferences.autoBackupEnabled,
                    onCheckedChange = onAutoBackupEnabledChange,
                    icon = Icons.Outlined.AutoMode,
                    shape = if (preferences.autoBackupEnabled)
                        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                    else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )
            }

            if (preferences.autoBackupEnabled) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    AutoBackupSettings(
                        directoryUri = preferences.backupDirectoryUri,
                        intervalHours = preferences.backupIntervalHours,
                        onPickDirectory = onPickBackupDirectory,
                        onSetInterval = onSetBackupInterval
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsActionItem(
                    title = "Manual Backup",
                    summary = "Save your settings and schedules to a file now",
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
                    shape = RoundedCornerShape(8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsToggle(
                    title = "Floating Tab Bar",
                    description = "Use the new Material 3 Expressive floating navigation",
                    checked = preferences.floatingTabBarEnabled,
                    onCheckedChange = onFloatingTabBarEnabledChange,
                    icon = Icons.Outlined.Flaky, // Closest icon for "floating" or experimental
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
fun RestoreConfirmationBottomSheet(
    metadata: BackupUtils.BackupMetadata,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Restore Data?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Restoring will permanently overwrite your current settings and data with the selected backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Backup Contents",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    BackupDetailItem(
                        icon = Icons.Outlined.Storage,
                        label = "Database",
                        status = if (metadata.hasDatabase) "Available" else "Not found",
                        isAvailable = metadata.hasDatabase
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    BackupDetailItem(
                        icon = Icons.Outlined.Settings,
                        label = "User Preferences",
                        status = if (metadata.hasPreferences) "Available" else "Not found",
                        isAvailable = metadata.hasPreferences
                    )

                    if (metadata.latestUsageDate != null && metadata.latestUsageMillis != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BackupDetailItem(
                            icon = Icons.Outlined.History,
                            label = "Latest Record",
                            status = "${metadata.latestUsageDate} - ${formatDuration(metadata.latestUsageMillis)}",
                            isAvailable = true
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Total Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFileSize(metadata.fileSize),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore")
                }
            }
        }
    }
}

@Composable
private fun BackupDetailItem(
    icon: ImageVector,
    label: String,
    status: String,
    isAvailable: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHeader(scrollBehavior: TopAppBarScrollBehavior) {
    CenterAlignedTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    )
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
internal fun RowScope.ThemeOptionButton(
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

@Composable
fun AutoBackupSettings(
    directoryUri: String,
    intervalHours: Int,
    onPickDirectory: () -> Unit,
    onSetInterval: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Auto Backup Configuration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Directory Picker Item
            Surface(
                onClick = onPickDirectory,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Backup Location", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (directoryUri.isEmpty()) "Not selected" else "Folder selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (directoryUri.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            // Interval Selector
            Text("Backup Interval", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            val intervals = listOf(3, 6, 12, 24)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                intervals.forEachIndexed { index, hours ->
                    ThemeOptionButton(
                        label = "${hours}h",
                        selected = intervalHours == hours,
                        onClick = { onSetInterval(hours) },
                        isFirst = index == 0,
                        isLast = index == intervals.size - 1
                    )
                }
            }
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
                    text = "Zenith v1.2",
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
