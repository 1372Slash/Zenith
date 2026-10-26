package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun DataManagementSettings(
    preferences: UserPreferences,
    onAutoBackupEnabledChange: (Boolean) -> Unit,
    onPickBackupDirectory: () -> Unit,
    onSetBackupInterval: (Int) -> Unit,
    onBackupNow: () -> Unit,
    onBackup: () -> Unit,
    onRefreshOnOpenUsageStatsChange: (Boolean) -> Unit,
    onRestore: () -> Unit
) {
    Column {
        PreferenceCategory(title = "Backup & Restore")

        SettingsToggle(
            title = "Auto Backup",
            description = "Periodically copy the full database (history included) to a folder",
            checked = preferences.autoBackupEnabled,
            onCheckedChange = onAutoBackupEnabledChange,
            icon = Icons.Outlined.AutoMode,
            shape = if (preferences.autoBackupEnabled)
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        AnimatedVisibility(
            visible = preferences.autoBackupEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                AutoBackupSettings(
                    directoryUri = preferences.backupDirectoryUri,
                    intervalHours = preferences.backupIntervalHours,
                    lastBackupTimestamp = preferences.lastBackupTimestamp,
                    onPickDirectory = onPickBackupDirectory,
                    onSetInterval = onSetBackupInterval,
                    onBackupNow = onBackupNow,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Manual Backup",
            summary = "Save a full local copy (usage history + settings) to a file now",
            onClick = onBackup,
            icon = Icons.Outlined.Backup,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Restore Data",
            summary = "Load data from a previous backup file",
            onClick = onRestore,
            icon = Icons.Outlined.Restore,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Sync & Maintenance")

        SettingsToggle(
            title = "Sync on Entry",
            description = "Refresh usage stats every time you open the stats screen. Enable this if you experience data inconsistency.",
            checked = preferences.refreshOnOpenUsageStats,
            onCheckedChange = onRefreshOnOpenUsageStatsChange,
            icon = Icons.Outlined.Sync,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )
    }
}

@Composable
fun AutoBackupSettings(
    directoryUri: String,
    intervalHours: Int,
    lastBackupTimestamp: Long,
    onPickDirectory: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onBackupNow: () -> Unit,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val context = LocalContext.current
    var isLocationValid by remember { mutableStateOf(true) }

    LaunchedEffect(directoryUri) {
        if (directoryUri.isNotEmpty()) {
            isLocationValid = withContext(Dispatchers.IO) {
                try {
                    val uri = android.net.Uri.parse(directoryUri)
                    val file = DocumentFile.fromTreeUri(context, uri)
                    file?.exists() == true && file.canWrite()
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            isLocationValid = true
        }
    }

    val readablePath = remember(directoryUri) {
        if (directoryUri.isEmpty()) "Not selected"
        else {
            try {
                val uri = android.net.Uri.parse(directoryUri)
                val path = uri.path ?: ""
                if (path.contains(":")) {
                    path.substringAfterLast(":")
                } else {
                    path
                }
            } catch (e: Exception) {
                "Selected Folder"
            }
        }
    }

    val lastBackupText = remember(lastBackupTimestamp) {
        if (lastBackupTimestamp == 0L) "Never"
        else {
            val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
            formatter.format(Instant.ofEpochMilli(lastBackupTimestamp))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
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

            Surface(
                onClick = onPickDirectory,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Backup Location", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (directoryUri.isNotEmpty() && !isLocationValid) "Location inaccessible" else readablePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (directoryUri.isEmpty() || !isLocationValid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (!isLocationValid && directoryUri.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Last Backup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = lastBackupText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Backup Interval", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            val intervals = listOf(3, 6, 12, 24)
            ZenithToggleButtonGroup(
                options = intervals.map { ZenithToggleOption(text = "${it}h") },
                selectedIndices = setOf(intervals.indexOf(intervalHours)),
                onToggle = { index -> onSetInterval(intervals[index]) },
                size = ZenithButtonSize.Medium,
                isInsideContainer = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            ZenithButton(
                onClick = onBackupNow,
                text = "Backup Now",
                icon = Icons.Outlined.CloudUpload,
                type = ZenithButtonType.Tonal,
                size = ZenithButtonSize.Medium,
                modifier = Modifier.fillMaxWidth(),
                enabled = directoryUri.isNotEmpty() && isLocationValid
            )
        }
    }
}
