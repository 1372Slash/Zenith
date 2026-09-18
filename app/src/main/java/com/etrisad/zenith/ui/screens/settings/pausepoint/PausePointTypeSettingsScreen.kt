package com.etrisad.zenith.ui.screens.settings.pausepoint

import android.provider.Settings
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.service.InterceptOverlayManager
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.pausepoint.PausePointTaskType
import com.etrisad.zenith.ui.components.pausepoint.PausePointVariant
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PausePointTypeSettingsScreen(
    taskType: PausePointTaskType,
    preferences: UserPreferences,
    innerPadding: PaddingValues,
    preferencesRepository: UserPreferencesRepository,
    onOpenQrSettings: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val variants = preferences.pausePointConfig.variantsFor(taskType).distinct()

    val saveVariants: (List<PausePointVariant>) -> Unit = { newList ->
        coroutineScope.launch { preferencesRepository.setPausePointVariants(taskType, newList) }
    }

    val launchTest: () -> Unit = {
        if (Settings.canDrawOverlays(context)) {
            val manager = InterceptOverlayManager(context.applicationContext, preferencesRepository)
            manager.showOverlay(
                packageName = context.packageName,
                appName = "Zenith",
                shield = null,
                totalUsageToday = 0,
                totalGlobalUsageToday = 0,
                delayDurationSeconds = 0,
                forcedTaskType = taskType,
                onAllowUse = { _, _ -> },
                onCloseApp = {},
                onGoalDismiss = {}
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            PausePointTypeHeader(taskType, variantCount = variants.size)
            Spacer(modifier = Modifier.height(16.dp))
            PausePointTestButton(onClick = launchTest)
            Spacer(modifier = Modifier.height(24.dp))
        }

        when {
            taskType == PausePointTaskType.QR_SCAN -> {
                item {
                    QrCodeConfigCard(
                        savedCount = preferences.pausePointQrCodes.size,
                        onOpenQrSettings = onOpenQrSettings
                    )
                }
            }
            taskType == PausePointTaskType.CHOOSE_APP -> {
                item {
                    ChooseAppConfigCard()
                }
            }
            else -> {
                if (variants.isEmpty()) {
                    item {
                        EmptyVariantsHintCard(
                            hint = when (taskType) {
                                PausePointTaskType.TYPING -> "No custom texts yet. The default sentences will be picked randomly."
                                else -> "No custom sub tasks yet. The default value will be used."
                            }
                        )
                    }
                } else {
                    itemsIndexed(
                        items = variants,
                        key = { _, variant -> variant.
                        composesKey() }
                    ) { index, variant ->
                        PausePointVariantCard(
                            index = index,
                            total = variants.size,
                            variant = variant,
                            taskType = taskType,
                            onDelete = { saveVariants(variants.filterIndexed { i, _ -> i != index }) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = spring(stiffness = Spring.StiffnessLow),
                                fadeOutSpec = spring(stiffness = Spring.StiffnessLow),
                                placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                            )
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    AddVariantEditor(
                        taskType = taskType,
                        onAdd = { newVariant ->
                            if (variants.none { it == newVariant }) {
                                saveVariants(variants + newVariant)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun PausePointVariant.composesKey(): String =
    buildString {
        append(seconds).append(';')
        append(rounds).append(';')
        append(steps).append(';')
        append(size).append(';')
        append(levers).append(';')
        append(maxOperand).append(';')
        append(target).append(';')
        append(label.length).append(':').append(label).append(';')
        append(text.length).append(':').append(text)
    }

@Composable
internal fun PausePointTestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZenithButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        text = "Test",
        icon = Icons.Filled.PlayArrow,
        type = ZenithButtonType.Tonal,
        size = ZenithButtonSize.Large
    )
}

@Composable
private fun PausePointTypeHeader(taskType: PausePointTaskType, variantCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = taskType.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = taskType.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = taskType.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = CircleShape,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = when {
                    variantCount == 0 -> "Default value"
                    variantCount == 1 -> "1 sub task • picked randomly"
                    else -> "$variantCount sub tasks • picked randomly"
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PausePointVariantCard(
    index: Int,
    total: Int,
    variant: PausePointVariant,
    taskType: PausePointTaskType,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = when {
            total == 1 -> RoundedCornerShape(24.dp)
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            index == total - 1 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(8.dp)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = variantSummary(taskType, variant),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = taskType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        CircleShape
                    ),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete sub task",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyVariantsHintCard(hint: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddVariantEditor(
    taskType: PausePointTaskType,
    onAdd: (PausePointVariant) -> Unit
) {
    var sliderValue by remember(taskType) {
        mutableFloatStateOf(defaultEditorValue(taskType).toFloat())
    }
    var label by remember(taskType) { mutableStateOf("") }
    var text by remember(taskType) { mutableStateOf("") }
    var switchTimeEnabled by remember(taskType) { mutableStateOf(false) }
    var switchTimeValue by remember(taskType) { mutableFloatStateOf(15f) }

    val canAdd = when (taskType) {
        PausePointTaskType.TYPING -> text.isNotBlank()
        else -> true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = taskType.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Add Sub Task",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = addEditorHint(taskType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (taskType) {
                PausePointTaskType.WAITING -> EditorSlider(
                    title = "Duration",
                    value = sliderValue,
                    valueRange = 5f..60f,
                    steps = 54,
                    unit = "sec",
                    onValueChange = { sliderValue = it }
                )
                PausePointTaskType.BREATHING -> EditorSlider(
                    title = "Breathing Rounds",
                    value = sliderValue,
                    valueRange = 1f..10f,
                    steps = 8,
                    unit = "rounds",
                    onValueChange = { sliderValue = it }
                )
                PausePointTaskType.WALK -> EditorSlider(
                    title = "Steps",
                    value = sliderValue,
                    valueRange = 1f..50f,
                    steps = 48,
                    unit = "steps",
                    onValueChange = { sliderValue = it }
                )
                PausePointTaskType.NUMBER_SLIDE -> EditorSlider(
                    title = "Grid Size",
                    value = sliderValue,
                    valueRange = 3f..6f,
                    steps = 3,
                    unit = "x",
                    onValueChange = { sliderValue = it }
                )
                PausePointTaskType.SWITCH -> {
                    EditorSlider(
                        title = "Lever Count",
                        value = sliderValue,
                        valueRange = 2f..8f,
                        steps = 6,
                        unit = "levers",
                        onValueChange = { sliderValue = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Time limit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Reset the puzzle if not solved in time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = switchTimeEnabled,
                            onCheckedChange = { switchTimeEnabled = it }
                        )
                    }
                    if (switchTimeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        EditorSlider(
                            title = "Time Limit",
                            value = switchTimeValue,
                            valueRange = 5f..60f,
                            steps = 54,
                            unit = "sec",
                            onValueChange = { switchTimeValue = it }
                        )
                    }
                }
                PausePointTaskType.MATH -> EditorSlider(
                    title = "Max Operand",
                    value = sliderValue,
                    valueRange = 1f..100f,
                    steps = 98,
                    unit = "",
                    onValueChange = { sliderValue = it }
                )
                PausePointTaskType.COUNTING -> {
                    EditorSlider(
                        title = "Target Number",
                        value = sliderValue,
                        valueRange = 5f..30f,
                        steps = 24,
                        unit = "",
                        onValueChange = { sliderValue = it }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Exercise name (optional)") },
                        placeholder = { Text("e.g. push-ups") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                }
                PausePointTaskType.TYPING -> {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Sentence to type") },
                        placeholder = { Text("e.g. Stay focused and mindful.") },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent
                        )
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(20.dp))

            ZenithButton(
                onClick = {
                    onAdd(
                        when (taskType) {
                            PausePointTaskType.WAITING -> PausePointVariant(seconds = sliderValue.toInt())
                            PausePointTaskType.BREATHING -> PausePointVariant(rounds = sliderValue.toInt())
                            PausePointTaskType.WALK -> PausePointVariant(steps = sliderValue.toInt())
                            PausePointTaskType.NUMBER_SLIDE -> PausePointVariant(size = sliderValue.toInt())
                            PausePointTaskType.SWITCH -> PausePointVariant(
                                levers = sliderValue.toInt(),
                                seconds = if (switchTimeEnabled) switchTimeValue.toInt() else 0
                            )
                            PausePointTaskType.MATH -> PausePointVariant(maxOperand = sliderValue.toInt())
                            PausePointTaskType.COUNTING -> PausePointVariant(target = sliderValue.toInt(), label = label.trim())
                            PausePointTaskType.TYPING -> PausePointVariant(text = text.trim())
                            else -> PausePointVariant()
                        }
                    )
                    label = ""
                    text = ""
                },
                modifier = Modifier.fillMaxWidth(),
                text = "Add Sub Task",
                icon = Icons.Filled.Add,
                size = ZenithButtonSize.Large,
                enabled = canAdd
            )
        }
    }
}

@Composable
private fun EditorSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        text = if (unit.isEmpty()) "${value.toInt()}" else "${value.toInt()} $unit",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun addEditorHint(taskType: PausePointTaskType): String = when (taskType) {
    PausePointTaskType.WAITING -> "Pick a duration to add to the rotation"
    PausePointTaskType.BREATHING -> "Pick a round count to add to the rotation"
    PausePointTaskType.WALK -> "Pick a step count to add to the rotation"
    PausePointTaskType.NUMBER_SLIDE -> "Pick a grid size to add to the rotation"
    PausePointTaskType.SWITCH -> "Pick a lever count and an optional time limit"
    PausePointTaskType.MATH -> "Pick a difficulty to add to the rotation"
    PausePointTaskType.COUNTING -> "Give it a name (like push-ups) or leave it plain"
    PausePointTaskType.TYPING -> "Write your own sentence to type"
    else -> ""
}

private fun defaultEditorValue(taskType: PausePointTaskType): Int = when (taskType) {
    PausePointTaskType.WAITING -> 15
    PausePointTaskType.BREATHING -> 3
    PausePointTaskType.WALK -> 10
    PausePointTaskType.NUMBER_SLIDE -> 3
    PausePointTaskType.SWITCH -> 4
    PausePointTaskType.MATH -> 20
    PausePointTaskType.COUNTING -> 15
    else -> 0
}

private fun variantSummary(taskType: PausePointTaskType, variant: PausePointVariant): String = when (taskType) {
    PausePointTaskType.WAITING -> "Wait ${variant.seconds} sec"
    PausePointTaskType.BREATHING -> "${variant.rounds} breath${if (variant.rounds == 1) "" else "s"}"
    PausePointTaskType.WALK -> "Walk ${variant.steps} steps"
    PausePointTaskType.NUMBER_SLIDE -> "${variant.size}x${variant.size} slide puzzle"
    PausePointTaskType.SWITCH ->
        if (variant.seconds > 0) "${variant.levers} lever puzzle • ${variant.seconds}s limit"
        else "${variant.levers} lever puzzle"
    PausePointTaskType.MATH -> "Max operand ${variant.maxOperand}"
    PausePointTaskType.COUNTING ->
        if (variant.label.isNotBlank()) "${variant.target} ${variant.label}" else "Count to ${variant.target}"
    PausePointTaskType.TYPING -> "\u201C${variant.text}\u201D"
    else -> "Default"
}

@Composable
private fun QrCodeConfigCard(
    savedCount: Int,
    onOpenQrSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.QrCode2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Saved QR Codes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    savedCount == 0 -> "No codes saved yet — add one to enable this task."
                    savedCount == 1 -> "1 code saved — any of them passes."
                    else -> "$savedCount codes saved — any of them passes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            ZenithButton(
                onClick = onOpenQrSettings,
                modifier = Modifier.fillMaxWidth(),
                text = if (savedCount == 0) "Add QR Codes" else "Manage QR Codes",
                icon = Icons.Outlined.QrCodeScanner,
                type = if (savedCount == 0) ZenithButtonType.Filled else ZenithButtonType.Outlined,
                size = ZenithButtonSize.Large
            )
        }
    }
}

@Composable
private fun ChooseAppConfigCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "This task suggests one of your goal apps from the Focus tab whenever you pause. No extra settings are needed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}