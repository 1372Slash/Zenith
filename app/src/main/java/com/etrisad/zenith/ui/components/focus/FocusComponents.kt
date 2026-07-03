package com.etrisad.zenith.ui.components.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.etrisad.zenith.data.website.WebsiteRepository
import com.etrisad.zenith.ui.viewmodel.AppInfo

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiAppIconGroup(
    packageNames: List<String>,
    totalCount: Int,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val sunnyShape = MaterialShapes.Sunny.toShape()

    fun iconModel(pkg: String): String {
        return "app-icon://$pkg"
    }

    @Composable fun iconShape(pkg: String) = appIconShape(WebsiteRepository.isWebsitePackageName(pkg))

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (packageNames.size <= 1) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            packageNames.isEmpty() -> {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.6f)
                )
            }
            packageNames.size == 1 -> {
                val pkg = packageNames[0]
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconModel(pkg))
                        .crossfade(500)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(size)
                        .clip(iconShape(pkg))
                        .then(
                            if (WebsiteRepository.isWebsitePackageName(pkg)) Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                iconShape(pkg)
                            ) else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                val iconSize = (size / 2) - 2.dp
                val spacing = 1.dp
                val context = LocalContext.current
                Column(
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                        packageNames.take(2).forEach { pkg ->
                            val shape = iconShape(pkg)
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(iconModel(pkg))
                                    .crossfade(500)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(iconSize)
                                    .clip(shape)
                                    .then(
                                        if (WebsiteRepository.isWebsitePackageName(pkg)) Modifier.background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            shape
                                        ) else Modifier
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    if (packageNames.size > 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                            packageNames.drop(2).take(1).forEach { pkg ->
                                val shape = iconShape(pkg)
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(iconModel(pkg))
                                        .crossfade(500)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(iconSize)
                                        .clip(shape)
                                        .then(
                                            if (WebsiteRepository.isWebsitePackageName(pkg)) Modifier.background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape
                                            ) else Modifier
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (packageNames.size == 4 && totalCount <= 4) {
                                packageNames.drop(3).forEach { pkg ->
                                    val shape = iconShape(pkg)
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(iconModel(pkg))
                                            .crossfade(500)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(iconSize)
                                            .clip(shape)
                                            .then(
                                                if (WebsiteRepository.isWebsitePackageName(pkg)) Modifier.background(
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                    shape
                                                ) else Modifier
                                            ),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else if (totalCount > 3) {
                                Box(
                                    modifier = Modifier
                                        .size(iconSize)
                                        .clip(sunnyShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${totalCount - 3}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = androidx.compose.ui.text.TextStyle(fontSize = (size.value * 0.2f).sp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatRemainingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun PreferenceCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        content = content
    )
}

@Composable
fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    showDivider: Boolean = false
) {
    val alpha = if (enabled) 1f else 0.38f
    
    Column {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = {
                    val thumbSize by animateDpAsState(
                        targetValue = if (checked) 28.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "thumb_size"
                    )

                    val iconColor by animateColorAsState(
                        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "switch_icon_color"
                    )

                    Box(
                        modifier = Modifier.size(thumbSize),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = checked,
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
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                )
            )
        }
    }
}

@Composable
fun EmptyFocusMessage(message: String) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f)) + fadeIn()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun PickerSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun RowScope.GroupedOptionButton(
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ZenithDropdown(
    options: List<Pair<String, T>>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.find { it.second == selectedOption }?.first ?: "Select"

    val menuScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "menuScale"
    )

    val menuAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "menuAlpha"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(width)
                .height(44.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "Rotation"
                )
                
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .graphicsLayer {
                    scaleX = menuScale
                    scaleY = menuScale
                    alpha = menuAlpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .width(width + 40.dp),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp)
        ) {
                options.forEach { option ->
                    val isSelected = option.second == selectedOption

                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.tertiary
                                      else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "itemBg"
                    )

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onTertiary
                                      else MaterialTheme.colorScheme.onTertiaryContainer,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "itemContent"
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + scaleIn(initialScale = 0.5f),
                                    exit = fadeOut() + scaleOut(targetScale = 0.5f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = contentColor
                                    )
                                }
                                if (isSelected) Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option.first,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                            }
                        },
                        onClick = {
                            onOptionSelected(option.second)
                            expanded = false
                        },
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(backgroundColor)
                    )
                }
            }
        }
    }


@Composable
fun AppPickerItem(
    app: AppInfo,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    itemScale: Float = 1f,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    showCheckbox: Boolean = false,
    isTopApp: Boolean = false
) {
    Card(
        onClick = { onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .scale(itemScale),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                val iconSize = 44.dp
                val imageModel = "app-icon://${app.packageName}"
                val isWebsite = app.packageName.startsWith("zenith-web:")
                val shape = appIconShape(isWebsite)
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageModel)
                        .crossfade(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .then(
                            if (isWebsite) Modifier.background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape
                            ) else Modifier
                        )
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isWebsite) Icons.Outlined.Language else Icons.Outlined.Android,
                                contentDescription = null
                            )
                        }
                    }
                )
            },
            trailingContent = {
                if (showCheckbox) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                } else if (isTopApp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { content() }
    )
}
