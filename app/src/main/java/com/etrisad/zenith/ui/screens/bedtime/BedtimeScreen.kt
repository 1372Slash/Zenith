package com.etrisad.zenith.ui.screens.bedtime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.ui.viewmodel.BedtimeViewModel
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import com.etrisad.zenith.ui.components.PauseBottomSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeScreen(
    viewModel: BedtimeViewModel,
    innerPadding: PaddingValues
) {
    val preferences by viewModel.userPreferences.collectAsState()
    var showAppPicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showPauseSheet by remember { mutableStateOf(false) }
    
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = LocalTime.now()
            val nextMinute = currentTime.plusMinutes(1).withSecond(0).withNano(0)
            val delayMillis = Duration.between(currentTime, nextMinute).toMillis()
            kotlinx.coroutines.delay(delayMillis.coerceAtLeast(1000))
        }
    }

    val containerColor = if (preferences.expressiveColors) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 16.dp))

        AnimatedVisibility(
            visible = !preferences.bedtimeEnabled,
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) + 
                    expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)),
            exit = fadeOut(spring(stiffness = Spring.StiffnessLow)) + 
                   shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
        ) {
            Column {
                BedtimeToggleCard(
                    enabled = preferences.bedtimeEnabled,
                    onToggle = { 
                        if (preferences.bedtimeEnabled) {
                            showPauseSheet = true
                        } else {
                            viewModel.setBedtimeEnabled(true)
                        }
                    },
                    expressive = preferences.expressiveColors
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = preferences.bedtimeEnabled,
            enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
                    expandVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
            exit = fadeOut(spring()) + shrinkVertically(spring())
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BedtimeStatusProgress(
                    startTimeStr = preferences.bedtimeStartTime,
                    endTimeStr = preferences.bedtimeEndTime,
                    currentTime = currentTime
                )
                
                TimeSelectionRow(
                    startTime = preferences.bedtimeStartTime,
                    endTime = preferences.bedtimeEndTime,
                    onStartTimeClick = { showStartTimePicker = true },
                    onEndTimeClick = { showEndTimePicker = true },
                    containerColor = containerColor
                )

                DaysSelectionCard(
                    selectedDays = preferences.bedtimeDays,
                    onDaysChange = { viewModel.setBedtimeDays(it) },
                    containerColor = containerColor
                )

                Spacer(modifier = Modifier.height(12.dp))

                AllowedAppsCard(
                    allowedCount = preferences.bedtimeWhitelistedPackages.size,
                    onClick = { showAppPicker = true },
                    containerColor = containerColor
                )

                FeaturesCard(
                    dndEnabled = preferences.bedtimeDndEnabled,
                    windDownEnabled = preferences.bedtimeWindDownEnabled,
                    notificationEnabled = preferences.bedtimeNotificationEnabled,
                    onDndToggle = { viewModel.setBedtimeDndEnabled(it) },
                    onWindDownToggle = { viewModel.setBedtimeWindDownEnabled(it) },
                    onNotificationToggle = { viewModel.setBedtimeNotificationEnabled(it) },
                    containerColor = containerColor
                )
            }
        }
        Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 24.dp))
    }

    if (showAppPicker) {
        AllowedBedAppsBottomSheet(
            initialWhitelisted = preferences.bedtimeWhitelistedPackages,
            generalWhitelisted = preferences.whitelistedPackages,
            onDismiss = { showAppPicker = false },
            onSave = {
                viewModel.setBedtimeWhitelistedPackages(it)
                showAppPicker = false
            }
        )
    }

    if (showPauseSheet) {
        PauseBottomSheet(
            onDismiss = { showPauseSheet = false },
            onConfirmPause = { _ ->
                viewModel.setBedtimeEnabled(false)
                showPauseSheet = false
            },
            leverCount = 10,
            puzzleTimeoutSeconds = 10,
            showTimeSelection = false
        )
    }

    if (showStartTimePicker) {
        TimePickerDialog(
            initialTime = preferences.bedtimeStartTime,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = {
                viewModel.setBedtimeStartTime(it)
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialTime = preferences.bedtimeEndTime,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = {
                viewModel.setBedtimeEndTime(it)
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeStatusProgress(
    startTimeStr: String,
    endTimeStr: String,
    currentTime: LocalTime
) {
    val startTime = try { LocalTime.parse(startTimeStr) } catch(e: Exception) { LocalTime.MIDNIGHT }
    val endTime = try { LocalTime.parse(endTimeStr) } catch(e: Exception) { LocalTime.MIDNIGHT }
    
    val isBedtime = if (endTime.isAfter(startTime)) {
        currentTime.isAfter(startTime) && currentTime.isBefore(endTime)
    } else {
        currentTime.isAfter(startTime) || currentTime.isBefore(endTime)
    }

    val (label, remainingText, progress) = remember(currentTime, startTime, endTime, isBedtime) {
        if (isBedtime) {
            val totalDuration = if (endTime.isAfter(startTime)) {
                Duration.between(startTime, endTime)
            } else {
                Duration.ofHours(24).minus(Duration.between(endTime, startTime))
            }
            
            val elapsed = if (currentTime.isAfter(startTime)) {
                Duration.between(startTime, currentTime)
            } else {
                Duration.between(startTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, currentTime))
            }
            
            val remaining = totalDuration.minus(elapsed)
            val hours = remaining.toHours()
            val minutes = remaining.toMinutes() % 60
            
            Triple(
                "Bedtime ends in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                (elapsed.toMinutes().toFloat() / totalDuration.toMinutes().toFloat()).coerceIn(0f, 1f)
            )
        } else {
            val timeToStart = if (currentTime.isBefore(startTime)) {
                Duration.between(currentTime, startTime)
            } else {
                Duration.between(currentTime, LocalTime.MAX).plus(Duration.between(LocalTime.MIDNIGHT, startTime))
            }
            
            val hours = timeToStart.toHours()
            val minutes = timeToStart.toMinutes() % 60
            
            Triple(
                "Starts in",
                if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m",
                1f - (timeToStart.toMinutes().toFloat() / (24 * 60).toFloat()).coerceIn(0f, 1f)
            )
        }
    }

    val density = LocalDensity.current
    val strokeWidth = remember(density) { with(density) { 4.dp.toPx() } }

    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
    val waveAmplitude by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude"
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(240.dp)
            .padding(16.dp)
    ) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = if (isBedtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            stroke = Stroke(width = strokeWidth),
            trackStroke = Stroke(width = strokeWidth),
            amplitude = { waveAmplitude },
            wavelength = 48.dp
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isBedtime) Icons.Default.Bedtime else Icons.Default.WbSunny,
                contentDescription = null,
                tint = if (isBedtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { 
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = remainingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BedtimeToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit, expressive: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer 
                            else if (expressive) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bedtime Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (enabled) "Active based on schedule" else "Currently disabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun TimeSelectionRow(
    startTime: String,
    endTime: String,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Card(
            onClick = onStartTimeClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Schedule, 
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Start", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = startTime, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            }
        }
        Card(
            onClick = onEndTimeClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Alarm, 
                            null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "End", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = endTime, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun DaysSelectionCard(
    selectedDays: Set<Int>,
    onDaysChange: (Set<Int>) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.CalendarToday, 
                        null, 
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Repeat Days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            DaysSelector(selectedDays = selectedDays, onDaysChange = onDaysChange)
        }
    }
}

@Composable
fun DaysSelector(selectedDays: Set<Int>, onDaysChange: (Set<Int>) -> Unit) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEachIndexed { index, day ->
            val dayNum = index + 1
            val isSelected = dayNum in selectedDays
            ExpressiveDayButton(
                label = day,
                selected = isSelected,
                onClick = {
                    val newDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                    onDaysChange(newDays)
                },
                isFirst = index == 0,
                isLast = index == days.size - 1
            )
        }
    }
}

@Composable
internal fun RowScope.ExpressiveDayButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isFirst: Boolean,
    isLast: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val widthScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 1.5f
            selected -> 1.25f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "WidthScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BgColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "ContentColor"
    )

    val innerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "InnerRadius"
    )

    val outerRadius = 24.dp

    val shape = when {
        selected -> CircleShape
        isFirst -> RoundedCornerShape(
            topStart = outerRadius,
            bottomStart = outerRadius,
            topEnd = innerRadius,
            bottomEnd = innerRadius
        )
        isLast -> RoundedCornerShape(
            topEnd = outerRadius,
            bottomEnd = outerRadius,
            topStart = innerRadius,
            bottomStart = innerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }

    Box(
        modifier = Modifier
            .weight(widthScale)
            .height(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun AllowedAppsCard(allowedCount: Int, onClick: () -> Unit, containerColor: androidx.compose.ui.graphics.Color) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Allowed Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = "$allowedCount apps bypass bedtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun FeaturesCard(
    dndEnabled: Boolean,
    windDownEnabled: Boolean,
    notificationEnabled: Boolean,
    onDndToggle: (Boolean) -> Unit,
    onWindDownToggle: (Boolean) -> Unit,
    onNotificationToggle: (Boolean) -> Unit,
    containerColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Settings, 
                        null, 
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            FeatureRow(
                icon = Icons.Outlined.NotificationsActive,
                title = "Wind Down Notification",
                subtitle = "Notify 30 minutes before bedtime",
                enabled = notificationEnabled,
                onToggle = onNotificationToggle
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            FeatureRow(
                icon = Icons.Outlined.DoNotDisturbOn,
                title = "Do Not Disturb",
                subtitle = "Silence notifications during bedtime",
                enabled = dndEnabled,
                onToggle = onDndToggle
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            FeatureRow(
                icon = Icons.Outlined.Block,
                title = "Wind Down Restrictions",
                subtitle = "Restrict app usage 30-min before bedtime",
                enabled = windDownEnabled,
                onToggle = onWindDownToggle
            )
        }
    }
}

@Composable
fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val parts = initialTime.split(":")
    val timeState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val time = java.util.Locale.getDefault().let { locale ->
                    String.format(locale, "%02d:%02d", timeState.hour, timeState.minute)
                }
                onTimeSelected(time)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            TimePicker(state = timeState)
        }
    )
}
