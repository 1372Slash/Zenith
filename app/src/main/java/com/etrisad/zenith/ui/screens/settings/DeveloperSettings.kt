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
import com.etrisad.zenith.data.local.entity.FocusType
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.focus.AppPickerBottomSheet
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
    onCustomDelayEnabledChange: (Boolean) -> Unit,
    onSetDelayPowerSave: (Long) -> Unit,
    onSetDelayOverlayShowing: (Long) -> Unit,
    onSetDelayGoalNear: (Long) -> Unit,
    onSetDelayGoalMid: (Long) -> Unit,
    onSetDelayGoalFar: (Long) -> Unit,
    onSetDelayShieldVeryFar: (Long) -> Unit,
    onSetDelayShieldFar: (Long) -> Unit,
    onSetDelayShieldMid: (Long) -> Unit,
    onSetDelayShieldNear: (Long) -> Unit,
    onSetDelayDefault: (Long) -> Unit,
    onResetCustomDelays: () -> Unit,
    onNavigateToSystemUsageDebug: () -> Unit,
    onTriggerOnboardingPermissions: () -> Unit,
    onTriggerOnboardingStats: () -> Unit,
    onTriggerOnboardingUpdate: () -> Unit,
    onResetBedtimeStreak: () -> Unit,
    onResetStreakRecovery: () -> Unit,
    onUpdateAppStreak: (String, Int) -> Unit,
    onUpdateGlobalScreenTime: (Long) -> Unit,
    onUpdateAppScreenTime: (String, Long) -> Unit
) {
    var showAppPickerForStreak by remember { mutableStateOf(false) }
    var showAppPickerForUsage by remember { mutableStateOf(false) }
    var showGlobalUsageDialog by remember { mutableStateOf(false) }

    var selectedAppInfo by remember { mutableStateOf<AppInfo?>(null) }
    var showStreakEditSheet by remember { mutableStateOf(false) }
    var showAppUsageEditSheet by remember { mutableStateOf(false) }

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
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            PreferenceCategory(title = "Monitoring Intervals")

            SettingsToggle(
                title = "Custom Delay Time",
                description = "Manually adjust monitoring intervals (Advanced)",
                checked = preferences.customDelayEnabled,
                onCheckedChange = onCustomDelayEnabledChange,
                icon = Icons.Outlined.Timer,
                shape = if (preferences.customDelayEnabled)
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )

            AnimatedVisibility(
                visible = preferences.customDelayEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomDelaySettings(
                        preferences = preferences,
                        onSetDelayPowerSave = onSetDelayPowerSave,
                        onSetDelayOverlayShowing = onSetDelayOverlayShowing,
                        onSetDelayGoalNear = onSetDelayGoalNear,
                        onSetDelayGoalMid = onSetDelayGoalMid,
                        onSetDelayGoalFar = onSetDelayGoalFar,
                        onSetDelayShieldVeryFar = onSetDelayShieldVeryFar,
                        onSetDelayShieldFar = onSetDelayShieldFar,
                        onSetDelayShieldMid = onSetDelayShieldMid,
                        onSetDelayShieldNear = onSetDelayShieldNear,
                        onSetDelayDefault = onSetDelayDefault,
                        onReset = onResetCustomDelays,
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
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
@Composable
fun CustomDelaySettings(
    preferences: UserPreferences,
    onSetDelayPowerSave: (Long) -> Unit,
    onSetDelayOverlayShowing: (Long) -> Unit,
    onSetDelayGoalNear: (Long) -> Unit,
    onSetDelayGoalMid: (Long) -> Unit,
    onSetDelayGoalFar: (Long) -> Unit,
    onSetDelayShieldVeryFar: (Long) -> Unit,
    onSetDelayShieldFar: (Long) -> Unit,
    onSetDelayShieldMid: (Long) -> Unit,
    onSetDelayShieldNear: (Long) -> Unit,
    onSetDelayDefault: (Long) -> Unit,
    onReset: () -> Unit,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delay Intervals (ms)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DelaySliderItem(label = "Power Save Mode", value = preferences.delayPowerSave, onValueChange = onSetDelayPowerSave, onReset = { onSetDelayPowerSave(5000L) }, range = 500f..10000f)
            DelaySliderItem(label = "Overlay Showing", value = preferences.delayOverlayShowing, onValueChange = onSetDelayOverlayShowing, onReset = { onSetDelayOverlayShowing(8000L) }, range = 500f..15000f)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Goal Shield", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            DelaySliderItem(label = "Near (<1m)", value = preferences.delayGoalNear, onValueChange = onSetDelayGoalNear, onReset = { onSetDelayGoalNear(600L) })
            DelaySliderItem(label = "Mid (<5m)", value = preferences.delayGoalMid, onValueChange = onSetDelayGoalMid, onReset = { onSetDelayGoalMid(1200L) })
            DelaySliderItem(label = "Far (>5m)", value = preferences.delayGoalFar, onValueChange = onSetDelayGoalFar, onReset = { onSetDelayGoalFar(1800L) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Regular Shield", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            DelaySliderItem(label = "Near (<1m)", value = preferences.delayShieldNear, onValueChange = onSetDelayShieldNear, onReset = { onSetDelayShieldNear(600L) })
            DelaySliderItem(label = "Mid (>1m)", value = preferences.delayShieldMid, onValueChange = onSetDelayShieldMid, onReset = { onSetDelayShieldMid(1500L) })
            DelaySliderItem(label = "Far (>10m)", value = preferences.delayShieldFar, onValueChange = onSetDelayShieldFar, onReset = { onSetDelayShieldFar(3000L) })
            DelaySliderItem(label = "Very Far (>1h)", value = preferences.delayShieldVeryFar, onValueChange = onSetDelayShieldVeryFar, onReset = { onSetDelayShieldVeryFar(5000L) })

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DelaySliderItem(label = "Default Interval", value = preferences.delayDefault, onValueChange = onSetDelayDefault, onReset = { onSetDelayDefault(1200L) })
        }
    }
}

@Composable
private fun DelaySliderItem(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    onReset: () -> Unit,
    range: ClosedFloatingPointRange<Float> = 100f..5000f
) {
    var localValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = "Reset",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Text(text = "${localValue.toInt()}ms", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = localValue,
            onValueChange = {
                localValue = (Math.round(it / 10.0) * 10).toFloat()
            },
            onValueChangeFinished = { onValueChange(localValue.toLong()) },
            valueRange = range,
            modifier = Modifier.height(24.dp)
        )
    }
}
