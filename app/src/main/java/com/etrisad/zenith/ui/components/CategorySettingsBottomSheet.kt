package com.etrisad.zenith.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.model.AppCategory
import com.etrisad.zenith.ui.components.appCategoryIcon
import com.etrisad.zenith.ui.components.focus.MultiAppIconGroup
import com.etrisad.zenith.ui.components.focus.MultiAppPickerBottomSheet
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import com.etrisad.zenith.ui.components.ZenithButtonWeighted
import com.etrisad.zenith.ui.components.ZenithGroupedButton
import com.etrisad.zenith.ui.viewmodel.AppInfo
import com.etrisad.zenith.ui.viewmodel.FocusUiState
import com.etrisad.zenith.ui.viewmodel.PickerTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySettingsBottomSheet(
    existingCategory: AppCategory? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, iconKey: String, colorArgb: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    apps: List<AppInfo> = emptyList(),
    assignedApps: Set<String> = emptySet(),
    onAppsChange: ((Set<String>) -> Unit)? = null
) {
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val containerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "categorySheetContainerColor"
    )

    val screenHeight = configuration.screenHeightDp.dp

    var name by remember { mutableStateOf(existingCategory?.label ?: "") }
    var description by remember { mutableStateOf(existingCategory?.description ?: "Your custom category") }
    var iconKey by remember { mutableStateOf(existingCategory?.iconKey ?: customCategoryIcons.first().first) }
    var colorArgb by remember { mutableStateOf(existingCategory?.colorArgb ?: customCategoryColors.first()) }

    var selectedApps by remember { mutableStateOf(assignedApps) }
    var showAppPicker by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }

    val initialHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(colorArgb, it) } }
    var showCustomColor by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    val previewCategory = AppCategory(
        id = existingCategory?.id ?: "preview",
        label = name.ifEmpty { "Category" },
        description = description,
        iconKey = iconKey,
        colorArgb = colorArgb,
        isCustom = existingCategory?.isCustom ?: true
    )

    val topShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleShape = RoundedCornerShape(8.dp)
    val bottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    val iconGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = customCategoryIcons.indexOfFirst { it.first == iconKey }.coerceAtLeast(0)
    )
    val colorGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = customCategoryColors.indexOf(colorArgb).coerceAtLeast(0)
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
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(colorArgb).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = appCategoryIcon(previewCategory),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color(colorArgb)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        textStyle = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (name.isEmpty()) {
                                    Text(
                                        text = "Name your category",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Category Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
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
                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            shape = topShape,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Description",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Describe what this category is for",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it.take(100) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        if (onAppsChange != null) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Card(
                                shape = middleShape,
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Apps,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Select Apps",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Apps that belong to this category",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    AnimatedContent(
                                        targetState = selectedApps.isEmpty(),
                                        transitionSpec = {
                                            fadeIn(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) + scaleIn(
                                                initialScale = 0.9f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) togetherWith fadeOut(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            ) + scaleOut(
                                                targetScale = 0.9f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        },
                                        label = "categoryAppSelectionContainer"
                                    ) { isEmpty ->
                                        if (isEmpty) {
                                            ZenithButton(
                                                onClick = { showAppPicker = true },
                                                text = "Choose Apps",
                                                type = ZenithButtonType.Outlined,
                                                size = ZenithButtonSize.Medium,
                                                fillMaxWidth = true,
                                                enabled = apps.isNotEmpty()
                                            )
                                        } else {
                                            val selectedCount = selectedApps.size
                                            val topAppNames = remember(selectedApps, apps) {
                                                selectedApps.take(2).map { packageName ->
                                                    apps.firstOrNull { it.packageName == packageName }?.appName
                                                        ?: packageName
                                                }
                                            }

                                            Surface(
                                                onClick = { showAppPicker = true },
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surface
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    MultiAppIconGroup(
                                                        packageNames = selectedApps.take(4).toList(),
                                                        totalCount = selectedCount,
                                                        size = 48.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "$selectedCount selected",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = buildString {
                                                                topAppNames.forEachIndexed { index, appNameText ->
                                                                    if (index > 0) append(", ")
                                                                    append(appNameText)
                                                                }
                                                                if (selectedCount > 2) {
                                                                    append(" +${selectedCount - 2} Other Apps")
                                                                }
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Outlined.Edit,
                                                        contentDescription = "Edit",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            shape = middleShape,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Icon",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Swipe for more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(3),
                                    state = iconGridState,
                                    modifier = Modifier.fillMaxWidth().height(136.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(customCategoryIcons, key = { it.first }) { (key, icon) ->
                                        val selectedIcon = key == iconKey
                                        Surface(
                                            onClick = { iconKey = key },
                                            shape = CircleShape,
                                            color = if (selectedIcon) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = key,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = if (selectedIcon) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            shape = bottomShape,
                            colors = CardDefaults.cardColors(containerColor = containerColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Color",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Swipe or pick custom",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(2),
                                    state = colorGridState,
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(customCategoryColors) { option ->
                                        val selectedColor = option == colorArgb && !showCustomColor
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(option))
                                                .clickable {
                                                    colorArgb = option
                                                    showCustomColor = false
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selectedColor) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = "Selected color",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!showCustomColor) {
                                                val hsv = FloatArray(3)
                                                android.graphics.Color.colorToHSV(colorArgb, hsv)
                                                hue = hsv[0]
                                                saturation = hsv[1]
                                                brightness = hsv[2]
                                            }
                                            showCustomColor = !showCustomColor
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorArgb)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Palette,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Custom color",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (showCustomColor) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                AnimatedVisibility(visible = showCustomColor) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(colorArgb))
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Hue ${hue.toInt()}° · Saturation ${(saturation * 100).toInt()}% · Brightness ${(brightness * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Hue",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        CategoryHueSlider(
                                            hue = hue,
                                            onHueChange = {
                                                hue = it
                                                colorArgb = customColorArgb(hue, saturation, brightness)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Saturation",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Slider(
                                            value = saturation,
                                            onValueChange = {
                                                saturation = it
                                                colorArgb = customColorArgb(hue, saturation, brightness)
                                            },
                                            valueRange = 0f..1f
                                        )
                                        Text(
                                            text = "Brightness",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Slider(
                                            value = brightness,
                                            onValueChange = {
                                                brightness = it
                                                colorArgb = customColorArgb(hue, saturation, brightness)
                                            },
                                            valueRange = 0f..1f
                                        )
                                    }
                                }
                            }
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
                ZenithGroupedButton {
                    if (onDelete != null && existingCategory != null && existingCategory.isCustom) {
                        ZenithButtonWeighted(
                            onClick = {
                                scope.launch { sheetState.hide() }
                                onDelete()
                            },
                            text = "Delete",
                            type = ZenithButtonType.Tonal,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            weight = 1f,
                            isFirst = true,
                            isLast = false
                        )
                        ZenithButtonWeighted(
                            onClick = {
                                if (onAppsChange != null) {
                                    onAppsChange(selectedApps)
                                }
                                scope.launch { sheetState.hide() }
                                onSave(name.trim(), description.trim(), iconKey, colorArgb)
                            },
                            text = "Save",
                            type = ZenithButtonType.Filled,
                            enabled = name.isNotBlank(),
                            weight = 1f,
                            isFirst = false,
                            isLast = true
                        )
                    } else {
                        ZenithButtonWeighted(
                            onClick = {
                                if (onAppsChange != null) {
                                    onAppsChange(selectedApps)
                                }
                                scope.launch { sheetState.hide() }
                                onSave(name.trim(), description.trim(), iconKey, colorArgb)
                            },
                            text = "Save",
                            type = ZenithButtonType.Filled,
                            enabled = name.isNotBlank(),
                            weight = 1f,
                            isFirst = true,
                            isLast = true
                        )
                    }
                }
            }
        }
    }

    if (showAppPicker && onAppsChange != null) {
        MultiAppPickerBottomSheet(
            uiState = FocusUiState(
                installedApps = apps,
                selectedAppsForSchedule = selectedApps,
                searchQuery = pickerQuery,
                pickerTab = PickerTab.APPS
            ),
            onDismiss = {
                showAppPicker = false
                pickerQuery = ""
            },
            onAppToggled = { packageName ->
                selectedApps = if (packageName in selectedApps) {
                    selectedApps - packageName
                } else {
                    selectedApps + packageName
                }
            },
            onConfirm = {
                showAppPicker = false
                pickerQuery = ""
            },
            onSearchQueryChange = { pickerQuery = it },
            showTabs = false,
            title = "Select Apps",
            allowEmptySelection = true
        )
    }
}

private fun customColorArgb(hue: Float, saturation: Float, brightness: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

@Composable
private fun CategoryHueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentColor = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }
    Box(modifier = modifier.fillMaxWidth().height(40.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(8.dp))
        ) {
            val hueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { h ->
                Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f)))
            }
            val brush = Brush.horizontalGradient(hueStops, 0f, size.width)
            val thumbPad = 2.dp.toPx()
            val thumbX = thumbPad + (hue / 360f) * (size.width - 2 * thumbPad)
            val gap = 8.dp.toPx()
            if (thumbX - gap > 0f) {
                clipRect(right = thumbX - gap) { drawRect(brush = brush, size = size) }
            }
            if (thumbX + gap < size.width) {
                clipRect(left = thumbX + gap) { drawRect(brush = brush, size = size) }
            }
        }
        Slider(
            value = hue,
            onValueChange = onHueChange,
            valueRange = 0f..360f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = currentColor,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}
