package com.etrisad.zenith.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.etrisad.zenith.data.manager.GitHubUpdateManager
import com.etrisad.zenith.data.preferences.PerformanceConfig
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.data.preferences.detectPreset
import com.etrisad.zenith.data.remote.model.GitHubRelease
import com.etrisad.zenith.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferencesRepository: UserPreferencesRepository,
    innerPadding: PaddingValues,
    navController: NavController,
    onOpenPermissions: () -> Unit,
    permissionsMissing: Boolean = false
) {
    val preferences by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val updateManager = remember { GitHubUpdateManager(context) }
    var checkingForUpdate by remember { mutableStateOf(false) }
    var showUpdateSheet by remember { mutableStateOf(false) }
    var showChangelogSheet by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var allReleases by remember { mutableStateOf<List<GitHubRelease>>(emptyList()) }
    var showWhitelistSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (preferences.checkUpdateOnStart) {
            coroutineScope.launch {
                when (val result = updateManager.checkForUpdates()) {
                    is GitHubUpdateManager.UpdateResult.NewUpdate -> {
                        latestRelease = result.release
                    }
                    else -> {}
                }
            }
        }
    }

    val onCheckForUpdate: () -> Unit = {
        if (!checkingForUpdate) {
            coroutineScope.launch {
                checkingForUpdate = true
                when (val result = updateManager.checkForUpdates()) {
                    is GitHubUpdateManager.UpdateResult.NewUpdate -> {
                        latestRelease = result.release
                        showUpdateSheet = true
                    }
                    is GitHubUpdateManager.UpdateResult.NoUpdate -> {
                        Toast.makeText(context, "Zenith is up to date!", Toast.LENGTH_SHORT).show()
                    }
                    is GitHubUpdateManager.UpdateResult.Error -> {
                        Toast.makeText(context, "Update check failed: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                checkingForUpdate = false
            }
        }
    }

    val onViewChangelog: () -> Unit = {
        coroutineScope.launch {
            val releases = updateManager.fetchAllReleases()
            if (releases != null) {
                allReleases = releases
                showChangelogSheet = true
            } else {
                Toast.makeText(context, "Failed to fetch changelog", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                FeaturedCarousel(
                    updateAvailable = latestRelease,
                    onUpdateClick = { showUpdateSheet = true },
                    onNavigate = { route ->
                        if (route == Screen.Focus.route || route == Screen.Home.route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            navController.navigate(route)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                GeneralSettings(
                    preferences = preferences,
                    onSetTarget = { mins -> coroutineScope.launch { preferencesRepository.setScreenTimeTarget(mins) } },
                    onSetEmergencyRecharge = { mins -> coroutineScope.launch { preferencesRepository.setEmergencyRechargeDuration(mins) } },
                    onSetDelayAppDuration = { secs -> coroutineScope.launch { preferencesRepository.setDelayAppDuration(secs) } },
                    onSetDayStartTime = { hour, minute -> coroutineScope.launch { preferencesRepository.setDayStartTime(hour, minute) } },
                    onShowWhitelistSheetChange = { showWhitelistSheet = it },
                    onOpenPermissions = onOpenPermissions,
                    permissionsMissing = permissionsMissing
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceCategory(title = "Categories")
                
                SettingsActionItem(
                    title = "Features",
                    summary = "Mindful Gateway, Session Overlay, Early Kick, and more",
                    onClick = { navController.navigate(Screen.SettingsCategory.createRoute("Features")) },
                    icon = Icons.Outlined.AutoFixHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Appearance",
                    summary = "Theme, Fonts, Dynamic Colors, and Layout",
                    onClick = { navController.navigate(Screen.SettingsCategory.createRoute("Appearance")) },
                    icon = Icons.Outlined.Palette,
                    shape = RoundedCornerShape(8.dp)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                val perfPreset = preferences.buildPerformanceConfig().detectPreset()
                SettingsActionItem(
                    title = "Performance",
                    summary = "Responsiveness & battery balance",
                    onClick = { navController.navigate(Screen.SettingsCategory.createRoute("Performance")) },
                    icon = Icons.Outlined.Timer,
                    shape = RoundedCornerShape(8.dp),
                    badge = {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = CircleShape
                        ) {
                            Text(
                                text = perfPreset.labelRes,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Data Management",
                    summary = "Backup, Restore, and Usage Sync",
                    onClick = { navController.navigate(Screen.SettingsCategory.createRoute("Data Management")) },
                    icon = Icons.Outlined.Storage,
                    shape = if (preferences.developerModeEnabled) RoundedCornerShape(8.dp) else RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                )

                if (preferences.developerModeEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsActionItem(
                        title = "Developer",
                        summary = "Advanced debugging and experimental tools",
                        onClick = { navController.navigate(Screen.SettingsCategory.createRoute("Developer")) },
                        icon = Icons.Outlined.BugReport,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                AboutSettings(
                    developerModeEnabled = preferences.developerModeEnabled,
                    onDeveloperModeChange = { enabled -> coroutineScope.launch { preferencesRepository.setDeveloperModeEnabled(enabled) } },
                    checkUpdateOnStart = preferences.checkUpdateOnStart,
                    onCheckUpdateOnStartChange = { enabled -> coroutineScope.launch { preferencesRepository.setCheckUpdateOnStart(enabled) } },
                    isCheckingForUpdate = checkingForUpdate,
                    onCheckForUpdate = onCheckForUpdate,
                    onViewChangelog = onViewChangelog
                )
            }
        }
    }

    if (showUpdateSheet && latestRelease != null) {
        val isDark = when (preferences.themeConfig) {
            ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
        com.etrisad.zenith.ui.components.UpdateBottomSheet(
            release = latestRelease!!,
            useExpressiveColors = preferences.expressiveColors,
            isDark = isDark,
            onDismiss = { showUpdateSheet = false },
            onUpdate = {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(latestRelease!!.htmlUrl))
                context.startActivity(intent)
                showUpdateSheet = false
            }
        )
    }

    if (showChangelogSheet && allReleases.isNotEmpty()) {
        val isDark = when (preferences.themeConfig) {
            ThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
        com.etrisad.zenith.ui.components.ChangelogBottomSheet(
            releases = allReleases,
            useExpressiveColors = preferences.expressiveColors,
            isDark = isDark,
            onDismiss = { showChangelogSheet = false }
        )
    }

    if (showWhitelistSheet) {
        WhitelistBottomSheet(
            initialWhitelisted = preferences.whitelistedPackages,
            onDismiss = { showWhitelistSheet = false },
            onSave = { packages ->
                coroutineScope.launch {
                    preferencesRepository.setWhitelistedPackages(packages)
                }
                showWhitelistSheet = false
            }
        )
    }
}
