package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmItemSettingsBottomSheet(
    alarmName: String,
    soundUri: String?,
    soundEnabled: Boolean,
    autoRepeatEnabled: Boolean,
    selectedDays: Set<Int>,
    vibrateEnabled: Boolean,
    snoozeDurationMinutes: Int = 5,
    snoozeMaxCount: Int = 3,
    gradualVolumeEnabled: Boolean = false,
    mathChallengeEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, soundUri: String?, soundEnabled: Boolean, autoRepeatEnabled: Boolean, selectedDays: Set<Int>, vibrateEnabled: Boolean, snoozeDurationMinutes: Int, snoozeMaxCount: Int, gradualVolumeEnabled: Boolean, mathChallengeEnabled: Boolean) -> Unit,
    onTimeClick: (() -> Unit)? = null,
    alarmTimeText: String? = null
) {
    AlarmSettingsSheetContent(
        initialAlarmName = alarmName,
        initialSoundUri = soundUri,
        initialSoundEnabled = soundEnabled,
        initialAutoRepeatEnabled = autoRepeatEnabled,
        initialSelectedDays = selectedDays,
        initialVibrateEnabled = vibrateEnabled,
        initialSnoozeDurationMinutes = snoozeDurationMinutes,
        initialSnoozeMaxCount = snoozeMaxCount,
        initialGradualVolumeEnabled = gradualVolumeEnabled,
        initialMathChallengeEnabled = mathChallengeEnabled,
        onDismiss = onDismiss,
        onSave = onSave,
        onTimeClick = onTimeClick,
        alarmTimeText = alarmTimeText
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSettingsSheetContent(
    initialAlarmName: String = "Alarm",
    initialSoundUri: String?,
    initialSoundEnabled: Boolean,
    initialAutoRepeatEnabled: Boolean,
    initialSelectedDays: Set<Int> = emptySet(),
    initialVibrateEnabled: Boolean = true,
    initialSnoozeDurationMinutes: Int = 5,
    initialSnoozeMaxCount: Int = 3,
    initialGradualVolumeEnabled: Boolean = false,
    initialMathChallengeEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, soundUri: String?, soundEnabled: Boolean, autoRepeatEnabled: Boolean, selectedDays: Set<Int>, vibrateEnabled: Boolean, snoozeDurationMinutes: Int, snoozeMaxCount: Int, gradualVolumeEnabled: Boolean, mathChallengeEnabled: Boolean) -> Unit,
    onTimeClick: (() -> Unit)?,
    alarmTimeText: String?
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "containerColor"
    )

    var alarmName by remember { mutableStateOf(initialAlarmName) }
    var alarmSoundUri by remember { mutableStateOf(initialSoundUri) }
    var alarmSoundEnabled by remember { mutableStateOf(initialSoundEnabled) }
    var alarmAutoRepeatEnabled by remember { mutableStateOf(initialAutoRepeatEnabled) }
    var selectedDays by remember { mutableStateOf(initialSelectedDays) }
    var vibrateEnabled by remember { mutableStateOf(initialVibrateEnabled) }
    var snoozeDurationMinutes by remember { mutableStateOf(initialSnoozeDurationMinutes) }
    var snoozeMaxCount by remember { mutableStateOf(initialSnoozeMaxCount) }
    var gradualVolumeEnabled by remember { mutableStateOf(initialGradualVolumeEnabled) }
    var mathChallengeEnabled by remember { mutableStateOf(initialMathChallengeEnabled) }
    var isPlayingPreview by remember { mutableStateOf(false) }
    val previewPlayer = remember {
        android.media.MediaPlayer()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (previewPlayer.isPlaying) previewPlayer.stop()
            previewPlayer.release()
        }
    }

    val alarmDetail by remember(selectedDays, alarmTimeText) {
        derivedStateOf {
            val isTomorrow = alarmTimeText?.let { text ->
                val parts = text.split(":")
                if (parts.size == 2) {
                    val h = parts[0].toIntOrNull()
                    val m = parts[1].toIntOrNull()
                    if (h != null && m != null) {
                        val alarmTime = java.time.LocalTime.of(h, m)
                        alarmTime <= java.time.LocalTime.now()
                    } else null
                } else null
            }

            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val recurrence = when {
                selectedDays.isEmpty() -> null
                selectedDays.size == 7 -> "Every day"
                selectedDays == setOf(1, 7) -> "Weekends"
                selectedDays == setOf(2, 3, 4, 5, 6) -> "Weekdays"
                else -> selectedDays.sorted().joinToString(", ") { dayNames[it - 1] }
            }

            if (recurrence != null) recurrence
            else {
                val suffix = when (isTomorrow) {
                    true -> "Tomorrow"
                    false -> "Today"
                    null -> ""
                }
                if (suffix.isEmpty()) "Once" else "Once $suffix"
            }
        }
    }

    val snoozeDurationOptions = listOf(1, 5, 10, 15, 30)
    val snoozeCountOptions = listOf(1, 2, 3, 5, 10, Int.MAX_VALUE)

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
                alarmSoundUri = it.toString()
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
            alarmSoundUri = it.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.9f)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        BasicTextField(
                            value = alarmName,
                            onValueChange = { alarmName = it },
                            textStyle = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alarmDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
                        val middleShape = RoundedCornerShape(8.dp)
                        val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            onClick = { onTimeClick?.invoke() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                            color = containerColor
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Alarm Time",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = alarmTimeText ?: "--:--",
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                        ZenithGroupedButton(size = ZenithButtonSize.Small) {
                            dayNames.forEachIndexed { index, dayLabel ->
                                val dayNum = index + 1
                                val isSelected = dayNum in selectedDays
                                val pShape = when (index) {
                                    0 -> RoundedCornerShape(bottomStart = 28.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                    dayNames.lastIndex -> RoundedCornerShape(bottomEnd = 28.dp, topEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp)
                                    else -> RoundedCornerShape(8.dp)
                                }
                                ZenithButtonWeighted(
                                    onClick = {
                                        val newDays = if (isSelected) selectedDays - dayNum else selectedDays + dayNum
                                        selectedDays = newDays
                                    },
                                    text = dayLabel,
                                    type = if (isSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                                    size = ZenithButtonSize.Small,
                                    selected = isSelected,
                                    shape = pShape,
                                    isFirst = index == 0,
                                    isLast = index == dayNames.lastIndex,
                                    contentScaleEnabled = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        PreferenceCategory(title = "Snooze")

                        CardGroup(shape = RoundedCornerShape(28.dp), containerColor = containerColor) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Snooze,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Snooze Duration",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Minutes before alarm rings again",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    ZenithDropdown(
                                        options = snoozeDurationOptions.map { "${it} min" to it },
                                        selectedOption = snoozeDurationMinutes,
                                        onOptionSelected = { snoozeDurationMinutes = it },
                                        width = 120.dp
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Repeat,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Max Snooze Count",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Maximum times you can snooze",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    ZenithDropdown(
                                        options = snoozeCountOptions.map { if (it == Int.MAX_VALUE) "Unlimited" to it else "${it}x" to it },
                                        selectedOption = snoozeMaxCount,
                                        onOptionSelected = { snoozeMaxCount = it },
                                        width = 120.dp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        PreferenceCategory(title = "Settings")

                        CardGroup(shape = topShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Alarm Sound",
                                description = "Play sound when alarm rings",
                                checked = alarmSoundEnabled,
                                onCheckedChange = { alarmSoundEnabled = it },
                                icon = if (alarmSoundEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                                shape = topShape
                            )
                        }

                        AnimatedVisibility(
                            visible = alarmSoundEnabled,
                            enter = expandVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                            ) + fadeIn(),
                            exit = shrinkVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                            ) + fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(4.dp))
                                CardGroup(shape = middleShape, containerColor = containerColor) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Sound Source",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isDefault = alarmSoundUri == null
                                            val isSystem = alarmSoundUri != null && alarmSoundUri?.startsWith("content://media") == true
                                            val isFile = alarmSoundUri != null && alarmSoundUri?.startsWith("content://media") == false

                                            GroupedOptionButton(
                                                label = "Default",
                                                selected = isDefault,
                                                onClick = { alarmSoundUri = null },
                                                isFirst = true,
                                                isLast = false
                                            )
                                            GroupedOptionButton(
                                                label = "System",
                                                selected = isSystem,
                                                onClick = {
                                                    val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, alarmSoundUri?.toUri())
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

                                        if (alarmSoundUri != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Custom sound selected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        val previewIcon = if (isPlayingPreview) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle
                                        val previewText = if (isPlayingPreview) "Stop Preview" else "Preview Sound"
                                        val previewColor = if (isPlayingPreview) MaterialTheme.colorScheme.error
                                                           else MaterialTheme.colorScheme.primary

                                        Surface(
                                            onClick = {
                                                if (isPlayingPreview) {
                                                    previewPlayer.stop()
                                                    previewPlayer.reset()
                                                    isPlayingPreview = false
                                                } else {
                                                    try {
                                                        val uri = if (alarmSoundUri != null) alarmSoundUri!!.toUri()
                                                                   else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                                                        previewPlayer.reset()
                                                        previewPlayer.setDataSource(context, uri)
                                                        previewPlayer.setAudioAttributes(
                                                            android.media.AudioAttributes.Builder()
                                                                .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                                                .build()
                                                        )
                                                        previewPlayer.setOnPreparedListener {
                                                            it.start()
                                                            isPlayingPreview = true
                                                        }
                                                        previewPlayer.setOnCompletionListener {
                                                            isPlayingPreview = false
                                                            previewPlayer.reset()
                                                        }
                                                        previewPlayer.prepareAsync()
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp),
                                            shape = RoundedCornerShape(22.dp),
                                            color = previewColor.copy(alpha = 0.12f)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = previewIcon,
                                                    contentDescription = null,
                                                    tint = previewColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = previewText,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = previewColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Gradual Volume",
                                description = "Volume increases slowly from 0 to full",
                                checked = gradualVolumeEnabled,
                                onCheckedChange = { gradualVolumeEnabled = it },
                                icon = Icons.Outlined.TrendingUp,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Vibrate",
                                description = "Vibrate device when alarm rings",
                                checked = vibrateEnabled,
                                onCheckedChange = { vibrateEnabled = it },
                                icon = Icons.Outlined.NotificationsActive,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = middleShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Auto Repeat Alarm",
                                description = "Re-trigger alarm every 5 min if you don't use your phone within 1 min after dismissing",
                                checked = alarmAutoRepeatEnabled,
                                onCheckedChange = { alarmAutoRepeatEnabled = it },
                                icon = Icons.Outlined.Repeat,
                                shape = middleShape
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        CardGroup(shape = bottomShape, containerColor = containerColor) {
                            SettingsToggle(
                                title = "Math Challenge",
                                description = "Solve a math problem before dismissing alarm",
                                checked = mathChallengeEnabled,
                                onCheckedChange = { mathChallengeEnabled = it },
                                icon = Icons.Outlined.Calculate,
                                shape = bottomShape
                            )
                        }

                        Spacer(modifier = Modifier.height(120.dp))
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                                )
                            )
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
                            startY = 0f,
                            endY = 40f
                        )
                    )
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                ZenithButton(
                    onClick = {
                        onSave(
                            alarmName,
                            alarmSoundUri,
                            alarmSoundEnabled,
                            alarmAutoRepeatEnabled,
                            selectedDays,
                            vibrateEnabled,
                            snoozeDurationMinutes,
                            snoozeMaxCount,
                            gradualVolumeEnabled,
                            mathChallengeEnabled
                        )
                        scope.launch {
                            sheetState.hide()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Save Settings"
                )
            }
        }
    }
}
