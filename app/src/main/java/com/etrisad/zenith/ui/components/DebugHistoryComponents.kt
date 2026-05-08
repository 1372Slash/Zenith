package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.viewmodel.UsageHistoryGroup
import com.etrisad.zenith.ui.viewmodel.UsageRecord
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UsageHistoryList(
    historyData: List<UsageHistoryGroup>,
    formatDuration: (Long) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(
            items = historyData,
            key = { it.date }
        ) { group ->
            UsageHistoryGroupCard(
                group = group,
                formatDuration = formatDuration,
                dateFormatter = dateFormatter
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun UsageHistoryGroupCard(
    group: UsageHistoryGroup,
    formatDuration: (Long) -> String,
    dateFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                group.isMissing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                group.isLive -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                group.hasDatabaseRecord -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { if (!group.isMissing) expanded = !expanded }
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor = when {
                    group.isMissing -> MaterialTheme.colorScheme.error
                    group.isLive -> MaterialTheme.colorScheme.tertiary
                    group.hasDatabaseRecord -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
                
                val statusContainerColor = when {
                    group.isMissing -> MaterialTheme.colorScheme.errorContainer
                    group.isLive -> MaterialTheme.colorScheme.tertiaryContainer
                    group.hasDatabaseRecord -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }

                val onStatusContainerColor = when {
                    group.isMissing -> MaterialTheme.colorScheme.onErrorContainer
                    group.isLive -> MaterialTheme.colorScheme.onTertiaryContainer
                    group.hasDatabaseRecord -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(statusContainerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            group.isMissing -> Icons.Outlined.History
                            group.isLive -> Icons.Outlined.Bolt
                            group.hasDatabaseRecord -> Icons.Outlined.CloudDone
                            else -> Icons.Outlined.Assessment
                        },
                        contentDescription = null,
                        tint = onStatusContainerColor
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.date,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (group.isLive) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = CircleShape,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = when {
                            group.isMissing -> "No data found anywhere"
                            group.isLive -> "Live monitoring data"
                            group.hasDatabaseRecord -> "Saved in Database"
                            else -> "Found in System Stats only"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = group.hasSnapshot,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Camera,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "SNAPSHOT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = group.hasHourlyUsage,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp, 
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        "HOURLY",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f
                                    )
                                }
                            }
                        }
                    }
                }

                if (!group.isMissing) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    group.records.forEachIndexed { index, record ->
                        val isFirst = index == 0
                        val isLast = index == group.records.size - 1
                        
                        when (record) {
                            is UsageRecord.Database -> {
                                UsageRecordListItem(
                                    packageName = record.entity.packageName,
                                    formattedDuration = formatDuration(record.entity.usageTimeMillis),
                                    formattedTime = dateFormatter.format(Date(record.entity.lastUpdated)),
                                    isDatabase = true,
                                    isFirst = isFirst,
                                    isLast = isLast
                                )
                            }
                            is UsageRecord.Live -> {
                                UsageRecordListItem(
                                    packageName = record.packageName,
                                    formattedDuration = formatDuration(record.usageTimeMillis),
                                    formattedTime = "Now",
                                    isDatabase = false,
                                    isFirst = isFirst,
                                    isLast = isLast
                                )
                            }
                        }
                        
                        if (!isLast) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UsageRecordListItem(
    packageName: String,
    formattedDuration: String,
    formattedTime: String,
    isDatabase: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val topRadius = if (isFirst) 16.dp else 4.dp
    val bottomRadius = if (isLast) 16.dp else 4.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
        shape = shape,
        color = if (isDatabase) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (packageName == "TOTAL") Icons.Outlined.History else Icons.Outlined.SdStorage,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isDatabase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isDatabase) "Saved in Database: $formattedTime" else "Fetched from App Usage",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.9f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDatabase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
