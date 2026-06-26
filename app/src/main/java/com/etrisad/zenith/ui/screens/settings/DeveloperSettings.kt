package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.focus.AppPickerBottomSheet
import com.etrisad.zenith.ui.components.overlay.InterceptBottomSheet
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.util.ScreenUsageHelper

@Composable
fun DeveloperSettings(
    preferences: UserPreferences,
    focusUiState: FocusUiState,
    onSearchQueryChange: (String) -> Unit,
    onShowDatabaseIndicatorChange: (Boolean) -> Unit,
    onSmartRepairOnRefreshChange: (Boolean) -> Unit,
    onNavigateToDatabaseDebug: () -> Unit,
    onNavigateToDataRepairment: () -> Unit,
    onTestGoalOverlay: () -> Unit,
    onTestUpdateSheet: () -> Unit,
    onNavigateToFontTest: () -> Unit,
    onNavigateToSystemUsageDebug: () -> Unit,
    onTriggerOnboardingPermissions: () -> Unit,
    onTriggerOnboardingStats: () -> Unit,
    onTriggerOnboardingUpdate: () -> Unit,
    onResetBedtimeStreak: () -> Unit,
    onResetStreakRecovery: () -> Unit,
    onUpdateAppStreak: (String, Int) -> Unit,
    onUpdateGlobalScreenTime: (Long) -> Unit,
    onUpdateAppScreenTime: (String, Long) -> Unit,
    onTestUsageGlimpse: () -> Unit
) {
    var showAppPickerForStreak by remember { mutableStateOf(false) }
    var showAppPickerForUsage by remember { mutableStateOf(false) }
    var showGlobalUsageDialog by remember { mutableStateOf(false) }

    var selectedAppInfo by remember { mutableStateOf<AppInfo?>(null) }
    var showStreakEditSheet by remember { mutableStateOf(false) }
    var showAppUsageEditSheet by remember { mutableStateOf(false) }
    var showBottomSheetTest by remember { mutableStateOf(false) }
    var bottomSheetExpandValue by remember { mutableFloatStateOf(0f) }

    if (preferences.developerModeEnabled) {
        Column {
            PreferenceCategory(title = "Database Editor")

            SettingsActionItem(
                title = "Edit App Streak",
                summary = "Manually override current streak for any shielded app",
                onClick = { showAppPickerForStreak = true },
                icon = Icons.AutoMirrored.Outlined.TrendingUp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Edit Global Screen Time",
                summary = "Override total screen time for today",
                onClick = { showGlobalUsageDialog = true },
                icon = Icons.Outlined.Public,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Edit App Screen Time",
                summary = "Override today's usage for a specific app",
                onClick = { showAppPickerForUsage = true },
                icon = Icons.Outlined.Smartphone,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            PreferenceCategory(title = "Database & Data")

            SettingsToggle(
                title = "Database Source Indicator",
                description = "Show indicator for database records in usage graphs",
                checked = preferences.showDatabaseIndicator,
                onCheckedChange = onShowDatabaseIndicatorChange,
                icon = Icons.Outlined.Storage,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsToggle(
                title = "Smart Repair on Refresh",
                description = "Enable resetCarryover() when pulling to refresh on dashboard",
                checked = preferences.smartRepairOnRefresh,
                onCheckedChange = onSmartRepairOnRefreshChange,
                icon = Icons.Outlined.AutoFixHigh,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Database Records",
                summary = "View and manage all recorded usage data",
                onClick = onNavigateToDatabaseDebug,
                icon = Icons.Outlined.SdStorage,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "View System Usage Fetch",
                summary = "View 1:1 usage history duplication from system only",
                onClick = onNavigateToSystemUsageDebug,
                icon = Icons.Outlined.Analytics,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Data Repairment",
                summary = "Fix missing or incorrect usage history",
                onClick = onNavigateToDataRepairment,
                icon = Icons.Outlined.Build,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            PreferenceCategory(title = "Onboarding Triggers")

            SettingsActionItem(
                title = "Trigger Permission Sheet",
                summary = "Open the initial permission onboarding sheet",
                onClick = onTriggerOnboardingPermissions,
                icon = Icons.Outlined.AdminPanelSettings,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Reset & Trigger Stats Onboarding",
                summary = "Reset flag and show statistic experience choice",
                onClick = onTriggerOnboardingStats,
                icon = Icons.Outlined.BarChart,
                shape = RoundedCornerShape(8.dp)
            )

            if (com.etrisad.zenith.BuildConfig.SHOW_UPDATES) {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Reset & Trigger Update Onboarding",
                    summary = "Reset flag and show update preference choice",
                    onClick = onTriggerOnboardingUpdate,
                    icon = Icons.Outlined.Update,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Reset Bedtime Streak",
                summary = "Reset current and best bedtime streaks to 0",
                onClick = onResetBedtimeStreak,
                icon = Icons.Outlined.Bedtime,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Run Streak Recovery",
                summary = "Manually verify and restore streaks from system history",
                onClick = onResetStreakRecovery,
                icon = Icons.Outlined.RestartAlt,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            PreferenceCategory(title = "UI & Functional Testing")

            SettingsActionItem(
                title = "Test Goal Overlay",
                summary = "Immediately trigger the full screen caller overlay",
                onClick = onTestGoalOverlay,
                icon = Icons.Outlined.BugReport,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Test Usage Glimpse",
                summary = "Show the 5-minute usage glimpse HUD immediately",
                onClick = onTestUsageGlimpse,
                icon = Icons.Outlined.Visibility,
                shape = RoundedCornerShape(8.dp)
            )

            if (com.etrisad.zenith.BuildConfig.SHOW_UPDATES) {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsActionItem(
                    title = "Test Update Sheet",
                    summary = "Immediately trigger the new update bottom sheet",
                    onClick = onTestUpdateSheet,
                    icon = Icons.Outlined.NewReleases,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Test Variable Font",
                summary = "Demo variable axes with Google Sans Flex",
                onClick = onNavigateToFontTest,
                icon = Icons.Outlined.FontDownload,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))
            SettingsActionItem(
                title = "Test Bottom Sheet Expand",
                summary = "Interactive test for InterceptBottomSheet expand behavior",
                onClick = { showBottomSheetTest = true },
                icon = Icons.Outlined.OpenInFull,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showBottomSheetTest) {
        var showContentB by remember { mutableStateOf(false) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenHeightDp = with(density) {
            androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        }
        val contentHeight = screenHeightDp * (0.3f + bottomSheetExpandValue * 0.6f)

        Dialog(
            onDismissRequest = { showBottomSheetTest = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                InterceptBottomSheet(
                    visible = true,
                    backgroundAlpha = 0.6f,
                    isLandscape = false,
                    showBedtimePill = false,
                    contentKey = showContentB
                ) { key ->
                    Column(
                        modifier = Modifier
                            .height(contentHeight)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (key == true) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Content B — Shield",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "This demonstrates content transition.\nTap the button below to toggle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )

                            Text(
                                text = "Content A — Pause Point",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Slide to adjust height.\nBottom stays fixed, top expands.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "Height: ${(30 + bottomSheetExpandValue * 60).toInt()}% of screen",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Slider(
                            value = bottomSheetExpandValue,
                            onValueChange = { bottomSheetExpandValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showContentB = !showContentB },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (key == true) "Show Content A" else "Show Content B",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showBottomSheetTest = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Close", fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAppPickerForStreak) {
        AppPickerBottomSheet(
            uiState = focusUiState.copy(selectedFocusType = FocusType.SHIELD),
            title = "Select App for Streak Edit",
            onDismiss = { showAppPickerForStreak = false },
            onAppSelected = {
                selectedAppInfo = it
                showAppPickerForStreak = false
                showStreakEditSheet = true
            },
            onSearchQueryChange = onSearchQueryChange
        )
    }

    if (showAppPickerForUsage) {
        AppPickerBottomSheet(
            uiState = focusUiState.copy(selectedFocusType = FocusType.SHIELD),
            title = "Select App for Usage Edit",
            onDismiss = { showAppPickerForUsage = false },
            onAppSelected = {
                selectedAppInfo = it
                showAppPickerForUsage = false
                showAppUsageEditSheet = true
            },
            onSearchQueryChange = onSearchQueryChange
        )
    }

    if (showStreakEditSheet && selectedAppInfo != null) {
        val shield = focusUiState.activeShields.find { it.packageName == selectedAppInfo!!.packageName }
            ?: focusUiState.activeGoals.find { it.packageName == selectedAppInfo!!.packageName }

        EditValueBottomSheet(
            title = "Edit Streak: ${selectedAppInfo!!.appName}",
            currentValueLabel = "Current Streak",
            currentValue = "${shield?.currentStreak ?: 0} days",
            inputValueLabel = "New Streak (days)",
            initialValue = shield?.currentStreak?.toString() ?: "0",
            onDismiss = { showStreakEditSheet = false },
            onConfirm = { newValue ->
                onUpdateAppStreak(selectedAppInfo!!.packageName, newValue.toIntOrNull() ?: 0)
                showStreakEditSheet = false
            }
        )
    }

    if (showGlobalUsageDialog) {
        val currentGlobalUsage = preferences.lastKnownDailyUsage

        EditValueBottomSheet(
            title = "Edit Global Screen Time",
            currentValueLabel = "Recorded Today",
            currentValue = formatMillis(currentGlobalUsage),
            inputValueLabel = "New Usage (minutes)",
            initialValue = (currentGlobalUsage / 60000).toString(),
            onDismiss = { showGlobalUsageDialog = false },
            onConfirm = { newValue ->
                val minutes = newValue.toLongOrNull() ?: 0L
                onUpdateGlobalScreenTime(minutes * 60 * 1000)
                showGlobalUsageDialog = false
            }
        )
    }

    if (showAppUsageEditSheet && selectedAppInfo != null) {
        val context = LocalContext.current
        val usageStatsManager = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val todayUsage = remember(selectedAppInfo) {
            ScreenUsageHelper.fetchDetailedUsageToday(usageStatsManager).appUsageMap[selectedAppInfo!!.packageName] ?: 0L
        }

        EditValueBottomSheet(
            title = "Edit Usage: ${selectedAppInfo!!.appName}",
            currentValueLabel = "System Recorded",
            currentValue = formatMillis(todayUsage),
            inputValueLabel = "New Usage (minutes)",
            initialValue = (todayUsage / 60000).toString(),
            onDismiss = { showAppUsageEditSheet = false },
            onConfirm = { newValue ->
                val minutes = newValue.toLongOrNull() ?: 0L
                onUpdateAppScreenTime(selectedAppInfo!!.packageName, minutes * 60 * 1000)
                showAppUsageEditSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditValueBottomSheet(
    title: String,
    currentValueLabel: String,
    currentValue: String,
    inputValueLabel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentValueLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentValue,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it.filter { char -> char.isDigit() } },
                label = { Text(inputValueLabel) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onConfirm(inputValue) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Update Data", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
