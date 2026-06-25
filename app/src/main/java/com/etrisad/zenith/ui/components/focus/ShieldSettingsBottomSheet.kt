package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import com.etrisad.zenith.data.local.entity.LimitPeriod
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.viewmodel.AppInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSettingsBottomSheet(
    appInfo: AppInfo,
    usageToday: Long,
    existingShield: ShieldEntity?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean, Boolean, Boolean, Int, Int, Boolean, LimitPeriod) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
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
    val initialMinutes = existingShield?.timeLimitMinutes ?: 30
    var hourText by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minuteText by remember { mutableStateOf((initialMinutes % 60).toString()) }
    var maxEmergencyUses by remember { mutableStateOf(existingShield?.maxEmergencyUses?.toString() ?: "3") }
    var remindersEnabled by remember { mutableStateOf(existingShield?.isRemindersEnabled ?: true) }
    var strictModeEnabled by remember { mutableStateOf(existingShield?.isStrictModeEnabled ?: false) }
    var autoQuitEnabled by remember { mutableStateOf(existingShield?.isAutoQuitEnabled ?: false) }
    var isDelayAppEnabled by remember { mutableStateOf(existingShield?.isDelayAppEnabled ?: false) }
    var maxUses by remember { mutableStateOf(existingShield?.maxUsesPerPeriod?.toString() ?: "5") }
    var refreshPeriodMinutes by remember { mutableIntStateOf(existingShield?.refreshPeriodMinutes ?: 60) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(existingShield?.limitPeriod ?: LimitPeriod.DAILY) }

    val isPreventEdit = remember(existingShield, usageToday) {
        if (existingShield != null) {
            val limitMillis = existingShield.timeLimitMinutes * 60 * 1000L
            limitMillis > 0 && usageToday >= (limitMillis * 0.5)
        } else false
    }

    val refreshOptions = listOf(
        "Every 30 Minutes" to 30,
        "Every 1 Hour" to 60,
        "Every 2 Hours" to 120,
        "Every 6 Hours" to 360,
        "Every 12 Hours" to 720,
        "Every 24 Hours" to 1440
    )

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
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SubcomposeAsyncImage(
                            model = "app-icon://${appInfo.packageName}",
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Crop,
                            error = {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = appInfo.appName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Shield Settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Today: ${formatRemainingTime(usageToday)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
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

                        ZenithGroupedButton(size = ZenithButtonSize.Medium) {
                            val isDaily = selectedPeriod == LimitPeriod.DAILY
                            ZenithButtonWeighted(
                                onClick = { selectedPeriod = LimitPeriod.DAILY },
                                text = "Daily",
                                type = if (isDaily) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                                selected = isDaily,
                                size = ZenithButtonSize.Medium,
                                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                                isLast = false,
                                isDisableWeight = true
                            )
                            ZenithButtonWeighted(
                                onClick = { selectedPeriod = LimitPeriod.WEEKLY },
                                text = "Weekly",
                                type = if (!isDaily) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                                selected = !isDaily,
                                size = ZenithButtonSize.Medium,
                                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp),
                                isFirst = false,
                                isDisableWeight = true
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = middleShape,
                                color = containerColor
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Hours",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicTextField(
                                        value = hourText,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() } && newValue.length <= 2) {
                                                hourText = newValue
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = middleShape,
                                color = containerColor
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Minutes",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicTextField(
                                        value = minuteText,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() } && newValue.length <= 2) {
                                                if (newValue.isEmpty() || (newValue.toIntOrNull() ?: 0) < 60) {
                                                    minuteText = newValue
                                                }
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        ZenithGroupedButton(size = ZenithButtonSize.Small) {
                            val presets = when (selectedPeriod) {
                                LimitPeriod.DAILY -> listOf(15, 30, 60, 120)
                                LimitPeriod.WEEKLY -> listOf(60, 120, 300, 600)
                            }
                            presets.forEachIndexed { index, preset ->
                                val isPresetSelected = ((hourText.toIntOrNull() ?: 0) * 60 + (minuteText.toIntOrNull() ?: 0)) == preset
                                val pShape = when(index) {
                                    0 -> RoundedCornerShape(bottomStart = 28.dp, topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp)
                                    presets.lastIndex -> RoundedCornerShape(bottomEnd = 28.dp, topEnd = 8.dp, topStart = 8.dp, bottomStart = 8.dp)
                                    else -> RoundedCornerShape(8.dp)
                                }
                                ZenithButtonWeighted(
                                    onClick = {
                                        hourText = (preset / 60).toString()
                                        minuteText = (preset % 60).toString()
                                    },
                                    text = if (preset >= 60) "${preset / 60}h" else "${preset}m",
                                    type = if (isPresetSelected) ZenithButtonType.Filled else ZenithButtonType.Tonal,
                                    size = ZenithButtonSize.Small,
                                    selected = isPresetSelected,
                                    shape = pShape,
                                    isFirst = index == 0,
                                    isLast = index == presets.lastIndex,
                                    contentScaleEnabled = false
                                )
                            }
                        }

                Spacer(modifier = Modifier.height(16.dp))

                PreferenceCategory(title = "Limits")

                CardGroup(shape = topShape, containerColor = containerColor) {
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
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Times of Uses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Daily opening limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            modifier = Modifier.width(72.dp).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                BasicTextField(
                                    value = maxUses,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) maxUses = it },
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = middleShape, containerColor = containerColor) {
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
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Refresh Period",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Usage reset interval",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ExposedDropdownMenuBox(
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = it }
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(40.dp)
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = { isDropdownExpanded = true }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = refreshOptions.find { it.second == refreshPeriodMinutes }?.first ?: "Custom",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = if (isDropdownExpanded) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            ExposedDropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false }
                            ) {
                                refreshOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.first) },
                                        onClick = {
                                            refreshPeriodMinutes = option.second
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = bottomShape, containerColor = containerColor) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Emergency Uses",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Emergency access uses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            modifier = Modifier.width(72.dp).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                BasicTextField(
                                    value = maxEmergencyUses,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) maxEmergencyUses = it },
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                PreferenceCategory(title = "Settings")

                CardGroup(shape = topShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Show Reminders",
                        description = "Get notified before limit is reached",
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it },
                        icon = Icons.Outlined.NotificationsActive,
                        shape = topShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = middleShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Strict Mode",
                        description = "No extensions allowed after limit",
                        checked = strictModeEnabled,
                        onCheckedChange = { strictModeEnabled = it },
                        icon = Icons.Outlined.GppGood,
                        shape = middleShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = middleShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Auto Quit",
                        description = "Exit app automatically when session ends",
                        checked = autoQuitEnabled,
                        onCheckedChange = { autoQuitEnabled = it },
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        shape = middleShape
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                CardGroup(shape = bottomShape, containerColor = containerColor) {
                    SettingsToggle(
                        title = "Delay App",
                        description = "Wait before reopening after being kicked out",
                        checked = isDelayAppEnabled,
                        onCheckedChange = { isDelayAppEnabled = it },
                        icon = Icons.Outlined.History,
                        shape = bottomShape
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (isPreventEdit) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Shield settings are locked because your remaining limit is less than 50%. You can only save changes that make the shield more restrictive (e.g., lower limit) to prevent bypassing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
                    }

                    // Top Scroll Gradient
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

            val currentLimit = (hourText.toIntOrNull() ?: 0) * 60 + (minuteText.toIntOrNull() ?: 0)
            val currentMaxUses = maxUses.toIntOrNull() ?: 5
            val currentMaxEmergency = maxEmergencyUses.toIntOrNull() ?: 3

            val canSave = remember(
                existingShield, isPreventEdit, currentLimit, currentMaxUses, currentMaxEmergency,
                remindersEnabled, strictModeEnabled, autoQuitEnabled, isDelayAppEnabled,
                refreshPeriodMinutes, selectedPeriod
            ) {
                if (existingShield == null) {
                    currentLimit > 0
                } else {
                    val limitDecreased = currentLimit < existingShield.timeLimitMinutes
                    val limitIncreased = currentLimit > existingShield.timeLimitMinutes

                    val usesDecreased = currentMaxUses < existingShield.maxUsesPerPeriod
                    val usesIncreased = currentMaxUses > existingShield.maxUsesPerPeriod

                    val emergencyDecreased = currentMaxEmergency < existingShield.maxEmergencyUses
                    val emergencyIncreased = currentMaxEmergency > existingShield.maxEmergencyUses

                    val remindersEnabledNew = !existingShield.isRemindersEnabled && remindersEnabled
                    val strictEnabled = !existingShield.isStrictModeEnabled && strictModeEnabled
                    val strictDisabled = existingShield.isStrictModeEnabled && !strictModeEnabled

                    val autoQuitEnabledNew = !existingShield.isAutoQuitEnabled && autoQuitEnabled
                    val autoQuitDisabled = existingShield.isAutoQuitEnabled && !autoQuitEnabled

                    val delayEnabledNew = !existingShield.isDelayAppEnabled && isDelayAppEnabled
                    val delayDisabled = existingShield.isDelayAppEnabled && !isDelayAppEnabled

                    val remindersChanged = remindersEnabled != existingShield.isRemindersEnabled
                    val refreshChanged = refreshPeriodMinutes != existingShield.refreshPeriodMinutes
                    val periodChanged = selectedPeriod != existingShield.limitPeriod

                    val hasPositiveChange = limitDecreased || usesDecreased || emergencyDecreased ||
                            strictEnabled || autoQuitEnabledNew || delayEnabledNew || remindersEnabledNew

                    val hasNegativeChange = limitIncreased || usesIncreased || emergencyIncreased ||
                            strictDisabled || autoQuitDisabled || delayDisabled || (!remindersEnabledNew && remindersChanged)

                    val hasAnyChange = hasPositiveChange || hasNegativeChange || remindersChanged || refreshChanged || periodChanged

                    if (isPreventEdit) {
                        hasPositiveChange && !hasNegativeChange
                    } else {
                        hasAnyChange
                    }
                }
            }

            // Bottom Save Button with Gradient
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
                            currentLimit,
                            currentMaxEmergency,
                            remindersEnabled,
                            strictModeEnabled,
                            autoQuitEnabled,
                            currentMaxUses,
                            refreshPeriodMinutes,
                            isDelayAppEnabled,
                            selectedPeriod
                        )
                        scope.launch {
                            sheetState.hide()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Save Shield",
                    enabled = canSave
                )
            }
        }
    }
}
