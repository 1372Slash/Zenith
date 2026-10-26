package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.focus.CardGroup
import com.etrisad.zenith.ui.components.focus.PreferenceCategory

@Composable
fun PrivacySettings(
    preferences: UserPreferences,
    onAppExclusionsChange: (Set<String>, Set<String>) -> Unit,
    onAccessibilityLimitedModeChange: (Boolean) -> Unit,
    onNotificationInterceptionEnabledChange: (Boolean) -> Unit,
    onNotificationExcludedPackagesChange: (Set<String>) -> Unit,
    onStoreInterceptedNotificationContentChange: (Boolean) -> Unit
) {
    var showAppExclusionsSheet by remember { mutableStateOf(false) }
    var showNotifExcludedSheet by remember { mutableStateOf(false) }
    var showPrivacyInfoSheet by remember { mutableStateOf(false) }
    val exclusionSummary = appExclusionSummary(
        preferences.privatePackages,
        preferences.excludedFromTrackingPackages
    )

    Column {
        PreferenceCategory(title = "Privacy & Data")

        SettingsActionItem(
            title = "Private & Hidden Apps",
            summary = exclusionSummary,
            onClick = { showAppExclusionsSheet = true },
            icon = Icons.Outlined.Security,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        SettingsActionItem(
            title = "Privacy & Data Guarantees",
            summary = "How Zenith handles access, storage, and backups",
            onClick = { showPrivacyInfoSheet = true },
            icon = Icons.Outlined.Info,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Accessibility Mode")

        SettingsToggle(
            title = "Limited Accessibility Mode",
            description = "Uses Accessibility ONLY for detecting app switches/launches (for mindful messages and shields). Disables reading screen content, node trees, text changes, and web URL tracking.",
            checked = preferences.accessibilityLimitedMode,
            onCheckedChange = onAccessibilityLimitedModeChange,
            icon = Icons.Outlined.AccessibilityNew,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Notification Privacy")

        SettingsToggle(
            title = "Notification Interception",
            description = "Allow Zenith to pause distracting notifications during active schedules or focus sessions.",
            checked = preferences.notificationInterceptionEnabled,
            onCheckedChange = onNotificationInterceptionEnabledChange,
            icon = Icons.Outlined.NotificationsActive,
            shape = if (preferences.notificationInterceptionEnabled)
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            else RoundedCornerShape(24.dp)
        )

        if (preferences.notificationInterceptionEnabled) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsActionItem(
                title = "Excluded Notification Apps",
                summary = if (preferences.notificationExcludedPackages.isEmpty()) "No apps excluded"
                          else "${preferences.notificationExcludedPackages.size} apps never intercepted",
                onClick = { showNotifExcludedSheet = true },
                icon = Icons.Outlined.NotificationsOff,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            SettingsToggle(
                title = "Store Notification Contents",
                description = "Saves message title and body for restoring after focus sessions. When disabled, only app package names are logged.",
                checked = preferences.storeInterceptedNotificationContent,
                onCheckedChange = onStoreInterceptedNotificationContentChange,
                icon = Icons.AutoMirrored.Outlined.Notes,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Local Storage & Cloud Backup")

        CardGroup(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        ) {
            BackupInfoRow(
                title = "Android Cloud Backup",
                description = "If Android backup is enabled, it can only restore preferences and light state such as streaks and timers. Usage history and notification logs are never included."
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        CardGroup(
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        ) {
            BackupInfoRow(
                title = "Local Backups",
                description = "Auto Backup and Manual Backup in Data Management copy the full database, including usage history. Keep those files private."
            )
        }
    }

    if (showAppExclusionsSheet) {
        AppExclusionsBottomSheet(
            initialPrivateApps = preferences.privatePackages,
            initialUsageOnlyApps = preferences.excludedFromTrackingPackages,
            onDismiss = { showAppExclusionsSheet = false },
            onSave = { privateApps, usageOnlyApps ->
                onAppExclusionsChange(privateApps, usageOnlyApps)
                showAppExclusionsSheet = false
            }
        )
    }

    if (showNotifExcludedSheet) {
        NotificationExcludedAppsBottomSheet(
            initialExcluded = preferences.notificationExcludedPackages,
            onDismiss = { showNotifExcludedSheet = false },
            onSave = { pkgs ->
                onNotificationExcludedPackagesChange(pkgs)
                showNotifExcludedSheet = false
            }
        )
    }

    if (showPrivacyInfoSheet) {
        PrivacyInfoBottomSheet(
            onDismiss = { showPrivacyInfoSheet = false }
        )
    }
}

@Composable
private fun BackupInfoRow(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
