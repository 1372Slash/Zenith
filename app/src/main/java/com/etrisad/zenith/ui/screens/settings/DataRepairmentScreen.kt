package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.RepairMode
import com.etrisad.zenith.ui.viewmodel.HomeViewModel
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.viewmodel.UsageHistoryGroup
import com.etrisad.zenith.ui.viewmodel.UsageRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DataRepairmentScreen(
    viewModel: HomeViewModel,
    innerPadding: PaddingValues
) {
    val repairableData by viewModel.repairableData.collectAsState(initial = emptyList())
    val isRepairing by viewModel.isRepairing.collectAsState()
    val preferences by viewModel.userPreferences.collectAsState(initial = UserPreferences())
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Advanced Repair Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Show all dates including today and those with existing records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.allowRepairNonUnavailable,
                        onCheckedChange = { scope.launch { viewModel.setAllowRepairNonUnavailable(it) } },
                        thumbContent = {
                            val thumbSize by animateDpAsState(
                                targetValue = if (preferences.allowRepairNonUnavailable) 28.dp else 24.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "thumb_size"
                            )

                            val iconColor by animateColorAsState(
                                targetValue = if (preferences.allowRepairNonUnavailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "switch_icon_color"
                            )

                            Box(
                                modifier = Modifier.size(thumbSize),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = preferences.allowRepairNonUnavailable,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                    scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                    },
                                    label = "switch_icon_anim"
                                ) { isChecked ->
                                    Icon(
                                        imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isChecked) 18.dp else 16.dp),
                                        tint = iconColor
                                    )
                                }
                            }
                        }
                    )
                }
            }

            if (repairableData.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All data looks good!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(
                        items = repairableData,
                        key = { _, group -> group.date }
                    ) { index, group ->
                        val isFirst = index == 0
                        val isLast = index == repairableData.size - 1
                        
                        RepairableGroupCard(
                            group = group,
                            formatDuration = { viewModel.formatDuration(it) },
                            onRepair = { date, mode -> scope.launch { viewModel.repairData(date, mode) } },
                            isFirst = isFirst,
                            isLast = isLast,
                            enabled = !isRepairing
                        )
                        
                        if (!isLast) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isRepairing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ZenithContainedLoadingIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Repairing Data...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Re-indexing history records",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepairableGroupCard(
    group: UsageHistoryGroup,
    formatDuration: (Long) -> String,
    onRepair: (String, RepairMode) -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var repairMode by remember { mutableStateOf(if (group.hasSystemData) RepairMode.SYSTEM else RepairMode.DATABASE_RECALC) }
    
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    )

    val shape = when {
        isFirst && isLast -> RoundedCornerShape(24.dp)
        isFirst -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        isLast -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        else -> RoundedCornerShape(8.dp)
    }

    Card(
        modifier = Modifier
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clip(shape)
                    .clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        onClick = { expanded = !expanded }
                    )
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (group.isMissing) MaterialTheme.colorScheme.errorContainer 
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), 
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (group.isMissing) Icons.Outlined.History else Icons.Outlined.BuildCircle,
                        contentDescription = null,
                        tint = if (group.isMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.date,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            group.isMissing -> "No existing record"
                            !group.hasDatabaseRecord -> "System data only"
                            else -> "Incomplete database record"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (group.isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Current Database Value",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (group.hasDatabaseRecord) formatDuration(group.totalTimeMillis) else "No data stored",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (!group.hasDatabaseRecord) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ) {
                                    Text("MISSING", modifier = Modifier.padding(horizontal = 4.dp))
                                }
                            }
                        }
                    }

                    Text(
                        text = "Repair Source (New Value)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RepairComparisonCard(
                            title = "Database",
                            subtitle = "Recalculate apps",
                            duration = formatDuration(group.databaseAppSumMillis),
                            icon = Icons.Outlined.SdStorage,
                            modifier = Modifier.weight(1f).clickable(enabled = group.hasDatabaseRecord) { 
                                repairMode = RepairMode.DATABASE_RECALC 
                            },
                            selected = repairMode == RepairMode.DATABASE_RECALC,
                            enabled = group.hasDatabaseRecord
                        )
                        
                        RepairComparisonCard(
                            title = "System",
                            subtitle = "Sync with OS",
                            duration = formatDuration(group.systemTotalMillis),
                            icon = Icons.Outlined.Calculate,
                            modifier = Modifier.weight(1f).clickable(enabled = group.hasSystemData) { 
                                repairMode = RepairMode.SYSTEM 
                            },
                            selected = repairMode == RepairMode.SYSTEM,
                            enabled = group.hasSystemData
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (repairMode == RepairMode.SYSTEM) "System Stats Breakdown" else "Existing App Breakdown",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val summaryPkgs = setOf("TOTAL", "SHIELD_TOTAL", "GOAL_TOTAL", "OTHER_TOTAL")
                        val displayRecords = group.records
                            .filter { record ->
                                when (repairMode) {
                                    RepairMode.SYSTEM -> record is UsageRecord.Live && record.packageName !in summaryPkgs
                                    RepairMode.DATABASE_RECALC -> record is UsageRecord.Database && record.entity.packageName !in summaryPkgs
                                }
                            }
                        
                        if (displayRecords.isEmpty()) {
                            Text(
                                "No individual app data available for this mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        displayRecords.forEachIndexed { index, record ->
                            val pkg = if (record is UsageRecord.Live) record.packageName else (record as UsageRecord.Database).entity.packageName
                            val dur = if (record is UsageRecord.Live) record.usageTimeMillis else (record as UsageRecord.Database).entity.usageTimeMillis
                            
                            RepairRecordItem(
                                packageName = pkg,
                                duration = formatDuration(dur),
                                isFirst = index == 0,
                                isLast = index == displayRecords.size - 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    HoldToAcceptButton(
                        onAccept = { onRepair(group.date, repairMode) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        text = if (repairMode == RepairMode.SYSTEM) "Repair from System Stats" else "Re-index from App Records"
                    )
                }
            }
        }
    }
}

@Composable
fun RepairRecordItem(
    packageName: String,
    duration: String,
    isFirst: Boolean,
    isLast: Boolean
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(16.dp)
        isFirst -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        isLast -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RoundedCornerShape(4.dp)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (packageName == "TOTAL") MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (packageName == "TOTAL") Icons.Outlined.Analytics else Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (packageName == "TOTAL") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (packageName == "TOTAL") FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                text = duration,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RepairComparisonCard(
    title: String,
    subtitle: String,
    duration: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.4f)
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp, 
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
        ),
        shadowElevation = if (selected) 6.dp else 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
                
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = if (enabled) duration else "--:--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun HoldToAcceptButton(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Hold to repair records"
) {
    var isHolding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val progress = remember { Animatable(0f) }

    LaunchedEffect(isHolding, enabled) {
        if (isHolding && enabled) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / 1500f).coerceIn(0f, 1f)
                progress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            if (isHolding) {
                onAccept()
                isHolding = false
                progress.snapTo(0f)
            }
        } else {
            progress.animateTo(0f, spring())
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isHolding && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Box(
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.6f
            }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isHolding = true
                    
                    waitForUpOrCancellation()
                    isHolding = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = if (progress.value >= 1f) Icons.Outlined.Check else Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isHolding) "Hold steady..." else text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
        }
    }
}
