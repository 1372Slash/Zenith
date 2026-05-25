package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences

@Composable
fun DeveloperSettings(
    preferences: UserPreferences,
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
    onResetCustomDelays: () -> Unit
) {
    if (preferences.developerModeEnabled) {
        Column {
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
                title = "Data Repairment",
                summary = "Fix missing or incorrect usage history",
                onClick = onNavigateToDataRepairment,
                icon = Icons.Outlined.Build,
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
