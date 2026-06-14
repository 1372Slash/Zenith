package com.etrisad.zenith.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.etrisad.zenith.ZenithApplication
import com.etrisad.zenith.data.manager.GitHubUpdateManager
import com.etrisad.zenith.data.preferences.PerformanceLevel
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.etrisad.zenith.service.UsageSyncManager
import com.etrisad.zenith.ui.navigation.Screen
import com.etrisad.zenith.ui.viewmodel.FocusViewModel
import com.etrisad.zenith.ui.viewmodel.FocusViewModelFactory
import com.etrisad.zenith.util.BackupUtils
import com.etrisad.zenith.worker.BackupManager
import kotlinx.coroutines.launch

@Composable
fun SettingsCategoryScreen(
    category: String,
    preferencesRepository: UserPreferencesRepository,
    navController: NavController,
    innerPadding: PaddingValues,
    onOpenPermissions: () -> Unit,
    onTriggerOnboardingStats: () -> Unit,
    onTriggerOnboardingUpdate: () -> Unit
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as ZenithApplication

    val backupManager = remember { BackupManager(context) }
    val updateManager = remember { GitHubUpdateManager(context) }

    var showGoalTestSheet by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showRestoreSheet by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var backupMetadata by remember { mutableStateOf<BackupUtils.BackupMetadata?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val focusViewModel: FocusViewModel = viewModel(
        factory = FocusViewModelFactory(
            context = context,
            shieldRepository = app.shieldRepository,
            preferencesRepository = preferencesRepository
        )
    )

    val focusUiState by focusViewModel.uiState.collectAsState()

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uri?.let {
                coroutineScope.launch {
                    try {
                        UsageSyncManager(context, app.shieldRepository, app.userPreferencesRepository).syncUsageData()
                    } catch (_: Exception) {}

                    BackupUtils.backupDatabase(context, it).onSuccess {
                        preferencesRepository.setLastBackupTimestamp(System.currentTimeMillis())
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
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                coroutineScope.launch {
                    preferencesRepository.setBackupDirectoryUri(it.toString())
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
                        backupMetadata = metadata
                        pendingRestoreUri = it
                        showRestoreSheet = true
                    } else {
                        Toast.makeText(context, "Invalid backup file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    val performanceApplyAction = remember { mutableStateOf<() -> Unit>({}) }
    var perfSelectedLevel by remember(preferences.performanceLevel) { mutableStateOf(preferences.performanceLevel) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 100.dp
            )
        ) {
            item {
                when (category.lowercase()) {
                    "features" -> FeaturesSettings(
                        preferences = preferences,
                        onTotalUsagePillEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setTotalUsagePillEnabled(enabled) } },
                        onSessionUsageOverlayEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlayEnabled(enabled) } },
                        onSessionUsageOverlaySizeChange = { size -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlaySize(size) } },
                        onSessionUsageOverlayOpacityChange = { opacity -> coroutineScope.launch { preferencesRepository.setSessionUsageOverlayOpacity(opacity) } },
                        onMindfulGatewayEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setMindfulGatewayEnabled(enabled) } },
                        onEarlyKickEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setEarlyKickEnabled(enabled) } },
                        onInterceptAudioFocusEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setInterceptAudioFocusEnabled(enabled) } },
                        onBatteryStatsResetEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setBatteryStatsResetEnabled(enabled) } }
                    )
                    "appearance" -> AppearanceSettings(
                        preferences = preferences,
                        onThemeChange = { theme -> coroutineScope.launch { preferencesRepository.setThemeConfig(theme) } },
                        onFontChange = { font -> coroutineScope.launch { preferencesRepository.setFontOption(font) } },
                        onDynamicColorChange = { enabled -> coroutineScope.launch { preferencesRepository.setDynamicColor(enabled) } },
                        onExpressiveColorsChange = { enabled -> coroutineScope.launch { preferencesRepository.setExpressiveColors(enabled) } },
                        onFloatingTabBarEnabledChange = { enabled -> coroutineScope.launch { preferencesRepository.setFloatingTabBarEnabled(enabled) } },
                        onNavigateToGSFlexCustomizer = { navController.navigate(Screen.GSFlexCustomizer.route) }
                    )
                    "data management" -> DataManagementSettings(
                        preferences = preferences,
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
                        onPickBackupDirectory = { directoryPickerLauncher.launch(null) },
                        onSetBackupInterval = { hours ->
                            coroutineScope.launch {
                                preferencesRepository.setBackupIntervalHours(hours)
                                if (preferences.autoBackupEnabled && preferences.backupDirectoryUri.isNotEmpty()) {
                                    backupManager.scheduleBackup(hours, preferences.backupDirectoryUri)
                                }
                            }
                        },
                        onBackupNow = {
                            if (preferences.backupDirectoryUri.isNotEmpty()) {
                                backupManager.runBackupNow(preferences.backupDirectoryUri)
                                Toast.makeText(context, "Backup started...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBackup = { backupLauncher.launch("zenith_backup_${System.currentTimeMillis()}.db") },
                        onRefreshOnOpenUsageStatsChange = { enabled -> coroutineScope.launch { preferencesRepository.setRefreshOnOpenUsageStats(enabled) } },
                        onRestore = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                    )
                    "performance" -> {
                        PerformanceSettings(
                            preferences = preferences,
                            selectedLevel = perfSelectedLevel,
                            onSelectLevel = { level -> perfSelectedLevel = level },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        PerformanceTuningPanel(
                            preferences = preferences,
                            selectedLevel = perfSelectedLevel,
                            onSelectedLevelChange = { perfSelectedLevel = it },
                            onApplyCustomSettings = { config ->
                                coroutineScope.launch {
                                    preferencesRepository.applyPerformanceSettings(config)
                                }
                            },
                            onSetPerformanceLevel = { level ->
                                coroutineScope.launch {
                                    preferencesRepository.setPerformanceLevel(level)
                                }
                            },
                            onRegisterApplyAction = { action -> performanceApplyAction.value = action },
                            onResetPerfMonDelays = { coroutineScope.launch { preferencesRepository.resetPerfMonDelays() } },
                        )
                    }
                    "developer" -> DeveloperSettings(
                        preferences = preferences,
                        focusUiState = focusUiState,
                        onSearchQueryChange = { focusViewModel.onSearchQueryChange(it) },
                        onShowDatabaseIndicatorChange = { enabled -> coroutineScope.launch { preferencesRepository.setShowDatabaseIndicator(enabled) } },
                        onSmartRepairOnRefreshChange = { enabled -> coroutineScope.launch { preferencesRepository.setSmartRepairOnRefresh(enabled) } },
                        onNavigateToDatabaseDebug = { navController.navigate(Screen.DatabaseDebug.route) },
                        onNavigateToDataRepairment = { navController.navigate(Screen.DataRepairment.route) },
                        onTestGoalOverlay = { showGoalTestSheet = true },
                        onTestUpdateSheet = {
                            coroutineScope.launch {
                                val release = updateManager.fetchLatestRelease()
                                if (release != null) {
                                    latestRelease = release
                                    showUpdateSheet = true
                                } else {
                                    Toast.makeText(context, "Failed to fetch latest release", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onNavigateToFontTest = { navController.navigate(Screen.FontTest.route) },
                        onNavigateToSystemUsageDebug = { navController.navigate(Screen.SystemUsageDebug.route) },
                        onTriggerOnboardingPermissions = { onOpenPermissions() },
                        onTriggerOnboardingStats = {
                            coroutineScope.launch {
                                preferencesRepository.setOnboardingStatsCompleted(false)
                                onTriggerOnboardingStats()
                                Toast.makeText(context, "Stats onboarding triggered", Toast.LENGTH_SHORT).show()
                                navController.popBackStack(Screen.Settings.route, false)
                            }
                        },
                        onTriggerOnboardingUpdate = {
                            coroutineScope.launch {
                                preferencesRepository.setOnboardingUpdateCompleted(false)
                                onTriggerOnboardingUpdate()
                                Toast.makeText(context, "Update onboarding triggered", Toast.LENGTH_SHORT).show()
                                navController.popBackStack(Screen.Settings.route, false)
                            }
                        },
                        onResetBedtimeStreak = { coroutineScope.launch { preferencesRepository.resetBedtimeStreak() } },
                        onResetStreakRecovery = {
                            coroutineScope.launch {
                                try {
                                    preferencesRepository.runManualStreakRecovery(app.shieldRepository)
                                    Toast.makeText(context, "Streak recovery completed successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Recovery failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onUpdateAppStreak = { pkg, streak ->
                            coroutineScope.launch {
                                val shield = app.shieldRepository.getShieldByPackageName(pkg)
                                if (shield != null) {
                                    app.shieldRepository.updateShield(shield.copy(currentStreak = streak, bestStreak = maxOf(shield.bestStreak, streak)))
                                }
                            }
                        },
                        onUpdateGlobalScreenTime = { time ->
                            coroutineScope.launch {
                                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                preferencesRepository.setLastKnownDailyUsage(time, todayStr)
                            }
                        },
                        onUpdateAppScreenTime = { pkg, time ->
                            coroutineScope.launch {
                                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                app.shieldRepository.insertDailyUsage(com.etrisad.zenith.data.local.entity.DailyUsageEntity(packageName = pkg, date = todayStr, usageTimeMillis = time))
                            }
                        }
                    )
                }
            }
        }

        if (category.lowercase() == "performance") {
            ExtendedFloatingActionButton(
                onClick = { performanceApplyAction.value.invoke() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                        end = 24.dp
                    ),
                icon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                text = { Text("Apply Settings") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }

        if (showGoalTestSheet) {
            com.etrisad.zenith.ui.components.AppGoalTestBottomSheet(
                focusViewModel = focusViewModel,
                onDismiss = { showGoalTestSheet = false }
            )
        }

        if (showUpdateSheet && latestRelease != null) {
            val isDark = when (preferences.themeConfig) {
                com.etrisad.zenith.data.preferences.ThemeConfig.FOLLOW_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.etrisad.zenith.data.preferences.ThemeConfig.LIGHT -> false
                com.etrisad.zenith.data.preferences.ThemeConfig.DARK -> true
            }
            com.etrisad.zenith.ui.components.UpdateBottomSheet(
                release = latestRelease!!,
                useExpressiveColors = preferences.expressiveColors,
                isDark = isDark,
                onDismiss = { showUpdateSheet = false },
                onUpdate = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, latestRelease!!.htmlUrl.toUri())
                    context.startActivity(intent)
                    showUpdateSheet = false
                }
            )
        }

        if (showRestoreSheet && backupMetadata != null && pendingRestoreUri != null) {
            com.etrisad.zenith.ui.components.RestoreConfirmationBottomSheet(
                preferences = preferences,
                metadata = backupMetadata!!,
                onDismiss = {
                    showRestoreSheet = false
                    backupMetadata = null
                    pendingRestoreUri = null
                },
                onConfirm = {
                    coroutineScope.launch {
                        val currentBackupUri = preferences.backupDirectoryUri
                        val currentBackupInterval = preferences.backupIntervalHours
                        val currentAutoBackupEnabled = preferences.autoBackupEnabled

                        BackupUtils.restoreDatabase(context, pendingRestoreUri!!).onSuccess {
                            preferencesRepository.setBackupDirectoryUri(currentBackupUri)
                            preferencesRepository.setBackupIntervalHours(currentBackupInterval)
                            preferencesRepository.setAutoBackupEnabled(currentAutoBackupEnabled)

                            if (currentAutoBackupEnabled && currentBackupUri.isNotEmpty()) {
                                backupManager.scheduleBackup(currentBackupInterval, currentBackupUri)
                            }

                            Toast.makeText(context, "Restore successful! Restarting app...", Toast.LENGTH_LONG).show()
                            BackupUtils.restartApp(context)
                        }.onFailure { e ->
                            Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    showRestoreSheet = false
                }
            )
        }
    }
}