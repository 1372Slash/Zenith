package com.etrisad.zenith.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
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
        itemsIndexed(
            items = historyData,
            key = { _, it -> it.date }
        ) { index, group ->
            UsageHistoryGroupCard(
                group = group,
                formatDuration = formatDuration,
                dateFormatter = dateFormatter,
                isFirst = index == 0,
                isLast = index == historyData.size - 1
            )
            
            if (index < historyData.size - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun UsageHistoryGroupCard(
    group: UsageHistoryGroup,
    formatDuration: (Long) -> String,
    dateFormatter: SimpleDateFormat,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    )

    val shape = when {
        isFirst && isLast -> RoundedCornerShape(24.dp)
        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        isLast -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = shape,
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
                    .clip(shape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = { if (!group.isMissing) expanded = !expanded }
                    )
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
                        .size(40.dp)
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
                        modifier = Modifier.size(20.dp),
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
                    
                    if (group.hasSnapshot || group.hasHourlyUsage) {
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
                        val isFirstRec = index == 0
                        val isLastRec = index == group.records.size - 1
                        
                        when (record) {
                            is UsageRecord.Database -> {
                                UsageRecordListItem(
                                    packageName = record.entity.packageName,
                                    formattedDuration = formatDuration(record.entity.usageTimeMillis),
                                    formattedTime = dateFormatter.format(Date(record.entity.lastUpdated)),
                                    isDatabase = true,
                                    isFirst = isFirstRec,
                                    isLast = isLastRec
                                )
                            }
                            is UsageRecord.Live -> {
                                UsageRecordListItem(
                                    packageName = record.packageName,
                                    formattedDuration = formatDuration(record.usageTimeMillis),
                                    formattedTime = "Now",
                                    isDatabase = false,
                                    isFirst = isFirstRec,
                                    isLast = isLastRec
                                )
                            }
                        }
                        
                        if (!isLastRec) {
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
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(16.dp)
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        isLast -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(4.dp)
    }

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
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (isDatabase) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (packageName == "TOTAL") Icons.Outlined.History else Icons.Outlined.SdStorage,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isDatabase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }

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
