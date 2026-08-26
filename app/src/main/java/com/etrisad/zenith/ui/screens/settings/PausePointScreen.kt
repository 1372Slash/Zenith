package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import kotlinx.coroutines.launch

@Composable
fun PausePointScreen(
    preferences: UserPreferences,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository,
    onNavigateToQrSettings: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            )
    ) {
        PreferenceCategory(title = "Task Type")

        PausePointTaskType.entries.forEachIndexed { index, taskType ->
            if (taskType == PausePointTaskType.QR_SCAN) {
                SettingsToggleWithNavigation(
                    title = taskType.displayName,
                    description = taskType.description,
                    checked = taskType in preferences.pausePointTaskTypes,
                    onCheckedChange = { checked ->
                        val updated = if (checked) {
                            preferences.pausePointTaskTypes + taskType
                        } else {
                            preferences.pausePointTaskTypes - taskType
                        }
                        coroutineScope.launch { preferencesRepository.setPausePointTaskTypes(updated) }
                    },
                    icon = taskType.icon,
                    shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        PausePointTaskType.entries.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    },
                    summary = "${preferences.pausePointQrCodes.size} saved code${if (preferences.pausePointQrCodes.size == 1) "" else "s"}",
                    onClick = onNavigateToQrSettings
                )
            } else {
                SettingsToggle(
                    title = taskType.displayName,
                    description = taskType.description,
                    checked = taskType in preferences.pausePointTaskTypes,
                    onCheckedChange = { checked ->
                        val updated = if (checked) {
                            preferences.pausePointTaskTypes + taskType
                        } else {
                            preferences.pausePointTaskTypes - taskType
                        }
                        coroutineScope.launch { preferencesRepository.setPausePointTaskTypes(updated) }
                    },
                    icon = taskType.icon,
                    shape = when (index) {
                        0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        PausePointTaskType.entries.lastIndex -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }
                )
            }
            if (index != PausePointTaskType.entries.lastIndex) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SettingsToggleWithNavigation(
    title: String,
    description: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit
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
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(8.dp)
            )
        }

        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 0.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "Manage QR codes",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
