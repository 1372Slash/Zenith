package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.etrisad.zenith.ui.components.UsageHistoryList
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.ui.viewmodel.UsageRecord

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DatabaseDebugScreen(
    viewModel: HomeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val historyData by viewModel.fullUsageHistory.collectAsState(initial = emptyList())
    val todayHourlyRaw by viewModel.todayHourlyUsage.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()
    var showManagementSheet by remember { mutableStateOf(false) }

    val appInfoMap = remember(uiState.allAppsUsage) {
        uiState.allAppsUsage.associateBy { it.packageName }
    }

    val todayHourlyGrouped = remember(todayHourlyRaw) {
        todayHourlyRaw
            .filter { it.packageName != "TOTAL" }
            .groupBy { it.hour }
            .toSortedMap(compareByDescending { it })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (historyData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ZenithContainedLoadingIndicator(
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Analysing database history...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            UsageHistoryList(
                historyData = historyData,
                formatDuration = { viewModel.formatDuration(it) },
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                )
            )
        }

        ExtendedFloatingActionButton(
            onClick = { showManagementSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    end = 24.dp
                ),
            icon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
            text = { Text("Manage Carryover") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )

        if (showManagementSheet) {
            ModalBottomSheet(
                onDismissRequest = { showManagementSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        "Hourly Carryover",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "These records exist in the hourly breakdown for today. Delete entries that look incorrect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (todayHourlyGrouped.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hourly records found for today.")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            todayHourlyGrouped.forEach { (hour, entities) ->
                                item(key = "hour_$hour") {
                                    Text(
                                        text = String.format("%02d:00 - %02d:59", hour, hour),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                items(
                                    items = entities.sortedByDescending { it.usageTimeMillis },
                                    key = { "${it.hour}_${it.packageName}" }
                                ) { entity ->
                                    val appInfo = appInfoMap[entity.packageName]
                                    CarryOverItem(
                                        packageName = entity.packageName,
                                        displayName = appInfo?.appName ?: entity.packageName,
                                        duration = viewModel.formatDuration(entity.usageTimeMillis),
                                        onDelete = {
                                            viewModel.deleteHourlyUsageAtHour(entity.hour, entity.packageName)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.resetCarryover()
                            showManagementSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset All Hourly Data")
                    }
                }
            }
        }
    }
}

@Composable
fun CarryOverItem(
    packageName: String,
    displayName: String,
    duration: String,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = "app-icon://$packageName",
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    error = {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (displayName != packageName) {
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Black
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            }
        }
    }
}
