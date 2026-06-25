package com.etrisad.zenith.ui.components.focus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.viewmodel.AppInfo
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingsBottomSheet(
    appInfo: AppInfo,
    usageToday: Long,
    existingShield: ShieldEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Boolean, Int, Boolean, Boolean, String?, LimitPeriod) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { UserPreferencesRepository(context) }
    val preferences by repository.userPreferencesFlow.collectAsState(initial = UserPreferences())

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = if (preferences.expressiveColors) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    val screenHeight = configuration.screenHeightDp.dp
    val timePickerState = rememberTimePickerState(
        initialHour = (existingShield?.timeLimitMinutes ?: 60) / 60,
        initialMinute = (existingShield?.timeLimitMinutes ?: 60) % 60,
        is24Hour = true
    )
    var remindersEnabled by remember { mutableStateOf(existingShield?.isRemindersEnabled ?: true) }
    var goalReminderPeriodMinutes by remember { mutableIntStateOf(existingShield?.goalReminderPeriodMinutes ?: 120) }
    var isGoalDropdownExpanded by remember { mutableStateOf(false) }

    var selectedPeriod by remember { mutableStateOf(existingShield?.limitPeriod ?: LimitPeriod.DAILY) }
    var isGoalCallerEnabled by remember { mutableStateOf(existingShield?.isGoalCallerEnabled ?: false) }
    var isGoalCallerSoundEnabled by remember { mutableStateOf(existingShield?.isGoalCallerSoundEnabled ?: true) }
    var goalCallerSoundUri by remember { mutableStateOf(existingShield?.goalCallerSoundUri) }

    val ringtonePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let {
                goalCallerSoundUri = it.toString()
            }
        }
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            goalCallerSoundUri = it.toString()
        }
    }

    val goalReminderOptions = listOf(
        "Every 1 Hour" to 60,
        "Every 2 Hours" to 120,
        "Every 4 Hours" to 240,
        "Every 8 Hours" to 480,
        "Once a Day" to 1440
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SubcomposeAsyncImage(
                        model = "app-icon://${appInfo.packageName}",
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop,
                        error = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.Android, contentDescription = null)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Goal Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Today: ${formatRemainingTime(usageToday)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Period",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LimitPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { selectedPeriod = period },
                            label = {
                                Text(
                                    when (period) {
                                        LimitPeriod.DAILY -> "Daily"
                                        LimitPeriod.WEEKLY -> "Weekly"
                                    }
                                )
                            },
                            shape = CircleShape
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when (selectedPeriod) {
                        LimitPeriod.DAILY -> "Daily Goal Target (HH:MM)"
                        LimitPeriod.WEEKLY -> "Weekly Goal Target (HH:MM)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimeInput(
                        state = timePickerState,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val presets = when (selectedPeriod) {
                        LimitPeriod.DAILY -> listOf(30, 60, 120, 240)
                        LimitPeriod.WEEKLY -> listOf(120, 300, 600, 1200)
                    }
                    presets.forEach { preset ->
                        FilterChip(
                            selected = (timePickerState.hour * 60 + timePickerState.minute) == preset,
                            onClick = {
                                timePickerState.hour = preset / 60
                                timePickerState.minute = preset % 60
                            },
                            label = { Text(if (preset >= 60) "${preset / 60}h" else "${preset}m") },
                            shape = CircleShape
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = isGoalDropdownExpanded,
                    onExpandedChange = { isGoalDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = goalReminderOptions.find { it.second == goalReminderPeriodMinutes }?.first
                            ?: "Custom",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Remind of Goal") },
                        supportingText = { Text("Zenith will nudge you to open this app") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isGoalDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        leadingIcon = { Icon(Icons.Outlined.Alarm, contentDescription = null) }
                    )
                    ExposedDropdownMenu(
                        expanded = isGoalDropdownExpanded,
                        onDismissRequest = { isGoalDropdownExpanded = false }
                    ) {
                        goalReminderOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.first) },
                                onClick = {
                                    goalReminderPeriodMinutes = option.second
                                    isGoalDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PreferenceCategory(title = "Settings")

                val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                val middleShape = RoundedCornerShape(8.dp)
                val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

                CardGroup(shape = topShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Goal Reminders",
                        description = "Receive notifications to reach your daily target",
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it },
                        icon = Icons.Outlined.NotificationsActive,
                        shape = topShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(
                    shape = if (isGoalCallerEnabled) middleShape else bottomShape,
                    containerColor = containerColor
                ) {
                    SettingsToggle(
                        title = "Goal Caller Overlay",
                        description = "Wake device with a dialer-like UI for this app",
                        checked = isGoalCallerEnabled,
                        onCheckedChange = { isGoalCallerEnabled = it },
                        icon = Icons.Outlined.PhoneInTalk,
                        shape = if (isGoalCallerEnabled) middleShape else bottomShape
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isGoalCallerEnabled,
                    enter = androidx.compose.animation.expandVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                    ) + androidx.compose.animation.fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        CardGroup(shape = bottomShape, containerColor = containerColor) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Caller Configuration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isGoalCallerSoundEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Enable Sound",
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = isGoalCallerSoundEnabled,
                                        onCheckedChange = { isGoalCallerSoundEnabled = it },
                                        thumbContent = {
                                            val thumbSize by animateDpAsState(
                                                targetValue = if (isGoalCallerSoundEnabled) 28.dp else 24.dp,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                label = "thumb_size"
                                            )

                                            val iconColor by animateColorAsState(
                                                targetValue = if (isGoalCallerSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                label = "switch_icon_color"
                                            )

                                            Box(
                                                modifier = Modifier.size(thumbSize),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AnimatedContent(
                                                    targetState = isGoalCallerSoundEnabled,
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
                                
                                AnimatedVisibility(
                                    visible = isGoalCallerSoundEnabled,
                                    enter = expandVertically(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                    ) + fadeIn(),
                                    exit = shrinkVertically(
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                    ) + fadeOut()
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Text(
                                            text = "Sound Source",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isDefault = goalCallerSoundUri == null
                                            val isSystem = goalCallerSoundUri != null && goalCallerSoundUri?.startsWith("content://media") == true
                                            val isFile = goalCallerSoundUri != null && goalCallerSoundUri?.startsWith("content://media") == false

                                            GroupedOptionButton(
                                                label = "Default",
                                                selected = isDefault,
                                                onClick = { goalCallerSoundUri = null },
                                                isFirst = true,
                                                isLast = false
                                            )
                                            GroupedOptionButton(
                                                label = "System",
                                                selected = isSystem,
                                                onClick = {
                                                    val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALL)
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select System Sound")
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, goalCallerSoundUri?.toUri())
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                                    }
                                                    ringtonePickerLauncher.launch(intent)
                                                },
                                                isFirst = false,
                                                isLast = false
                                            )
                                            GroupedOptionButton(
                                                label = "File",
                                                selected = isFile,
                                                onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                                                isFirst = false,
                                                isLast = true
                                            )
                                        }
                                        
                                        if (goalCallerSoundUri != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Custom sound selected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        onSave(
                            timePickerState.hour * 60 + timePickerState.minute,
                            remindersEnabled,
                            goalReminderPeriodMinutes,
                            isGoalCallerEnabled,
                            isGoalCallerSoundEnabled,
                            goalCallerSoundUri,
                            selectedPeriod
                        )
                        scope.launch {
                            sheetState.hide()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Set Goal"
                )
            }
        }
    }
}
