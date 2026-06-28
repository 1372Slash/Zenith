package com.etrisad.zenith.ui.components.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.etrisad.zenith.data.CurrentCalendarEvent
import com.etrisad.zenith.data.model.IncentiveTier
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.components.ZenithButton
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.components.ZenithButtonType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

data class OverlayColorPalette(
    val id: String,
    val seed: Color,
    val label: String,
    val isDynamic: Boolean = false
)

val PREDEFINED_PALETTES = listOf(
    OverlayColorPalette("dynamic", Color.Transparent, "Dynamic", isDynamic = true),
    OverlayColorPalette("custom", Color.Transparent, "Custom"),
    OverlayColorPalette("monochrome", Color(0xFF616161), "Monochrome"),
    OverlayColorPalette("zenith", Color(0xFF4D568D), "Zenith"),
    OverlayColorPalette("ocean", Color(0xFF0061A4), "Ocean"),
    OverlayColorPalette("forest", Color(0xFF386B01), "Forest"),
    OverlayColorPalette("rose", Color(0xFF9C4146), "Rose"),
    OverlayColorPalette("sunset", Color(0xFF825500), "Sunset"),
    OverlayColorPalette("lavender", Color(0xFF6750A4), "Lavender"),
    OverlayColorPalette("mint", Color(0xFF006B5D), "Mint")
)

fun generateDynamicColorScheme(seed: Color, isDark: Boolean, currentScheme: ColorScheme, isForPreview: Boolean = false): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    
    val hue = hsv[0]
    val sat = hsv[1]
    val isMonochrome = sat < 0.05f
    val primaryV = if (isMonochrome) {
        if (isDark) 0.95f else 0.1f
    } else {
        if (isDark) 0.82f else 0.50f
    }
    val primarySat = if (isMonochrome) 0f else sat.coerceIn(0.1f, 0.8f)
    val primary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, primarySat, primaryV)))
    val secondaryV = if (isMonochrome) {
        if (isDark) 0.7f else 0.8f
    } else {
        if (isDark) 0.90f else 0.78f
    }
    val secondarySat = if (isMonochrome) 0f else (sat * 0.2f).coerceAtMost(0.15f)
    val secondary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, secondarySat, secondaryV)))
    val tertiaryV = if (isMonochrome) {
        if (isDark) 0.4f else 0.5f
    } else {
        if (isDark) {
            if (isForPreview) 0.88f else 0.65f
        } else {
            if (isForPreview) 0.75f else 0.45f
        }
    }
    val tertiaryHue = if (isMonochrome) hue else (hue + 60f) % 360f
    val tertiarySat = if (isMonochrome) 0f else (sat * 0.4f).coerceIn(0.1f, 0.4f)
    val tertiary = Color(android.graphics.Color.HSVToColor(floatArrayOf(tertiaryHue, tertiarySat, tertiaryV)))

    val surfaceV = if (isDark) 0.12f else if (isForPreview) 0.98f else 0.97f
    val surfaceSat = if (isMonochrome) 0f else if (isDark) (sat * 0.45f).coerceIn(0.12f, 0.32f) else if (isForPreview) (sat * 0.05f).coerceAtMost(0.04f) else (sat * 0.12f).coerceIn(0.02f, 0.08f)
    val surface = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, surfaceSat, surfaceV)))
    
    val surfaceContainerLowest = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isMonochrome) 0f else sat * 0.08f, if (isDark) 0.07f else 1.0f)))
    val surfaceContainerLow = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isMonochrome) 0f else sat * 0.18f, if (isDark) 0.15f else 0.96f)))
    val surfaceContainer = if (isForPreview) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isMonochrome) 0f else sat * 0.25f, if (isDark) 0.18f else 0.94f)))
    } else {
        surface 
    }
    val surfaceContainerHigh = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, if (isMonochrome) 0f else sat * 0.35f, if (isDark) 0.24f else 0.9f)))
    val surfaceContainerHighest = surfaceContainerHigh.copy(alpha = 0.9f)

    val secondaryContainerV = if (isDark) 0.25f else 0.92f
    val secondaryContainerSat = if (isMonochrome) 0f else (sat * 0.3f).coerceIn(0.1f, 0.25f)
    val secondaryContainer = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, secondaryContainerSat, secondaryContainerV)))

    val tertiaryContainerV = if (isDark) 0.25f else 0.92f
    val tertiaryContainerSat = if (isMonochrome) 0f else (tertiarySat * 0.6f).coerceIn(0.1f, 0.25f)
    val tertiaryContainer = Color(android.graphics.Color.HSVToColor(floatArrayOf(tertiaryHue, tertiaryContainerSat, tertiaryContainerV)))

    val outlineVariantHue = (hue + 30f) % 360f
    val outlineVariantSat = if (isMonochrome) 0f else (sat * 0.15f).coerceIn(0.03f, 0.08f)
    val outlineVariantV = if (isDark) 0.35f else 0.85f
    val outlineVariantColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(outlineVariantHue, outlineVariantSat, outlineVariantV)))

    val surfaceVariantSat = if (isMonochrome) 0f else (sat * 0.3f).coerceIn(0.08f, 0.18f)
    val surfaceVariantV = if (isDark) 0.18f else 0.94f
    val surfaceVariantColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, surfaceVariantSat, surfaceVariantV)))

    return currentScheme.copy(
        primary = primary,
        onPrimary = if (isDark) Color.Black else Color.White,
        primaryContainer = primary.copy(alpha = if (isDark) 0.3f else 0.15f),
        onPrimaryContainer = primary,
        
        secondary = secondary,
        onSecondary = if (isDark) Color.Black else Color.White,
        secondaryContainer = if (isForPreview) secondary.copy(alpha = if (isDark) 0.25f else 0.2f) else secondaryContainer,
        onSecondaryContainer = if (isForPreview) secondary else (if (isDark) secondary else Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, (secondaryContainerSat * 2f).coerceAtMost(0.8f), 0.3f)))),
        
        tertiary = tertiary,
        onTertiary = if (isDark) Color.Black else Color.White,
        tertiaryContainer = if (isForPreview) tertiary.copy(alpha = if (isDark) 0.25f else 0.14f) else tertiaryContainer,
        onTertiaryContainer = if (isForPreview) tertiary else (if (isDark) tertiary else Color(android.graphics.Color.HSVToColor(floatArrayOf(tertiaryHue, (tertiaryContainerSat * 2f).coerceAtMost(0.8f), 0.3f)))),

        surface = surface,
        onSurface = if (isDark) Color.White else Color.Black,
        surfaceVariant = surfaceVariantColor,
        onSurfaceVariant = if (isDark) Color.LightGray else Color.DarkGray,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outlineVariant = outlineVariantColor
    )
}

fun lightenColorHSV(color: Color, targetV: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[2] = targetV
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BedtimeAlertPill(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier
) {
    val bedtimeInfo = remember(userPreferences) {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayDay = now.get(Calendar.DAY_OF_WEEK)
        
        val startParts = userPreferences.bedtimeStartTime.split(":")
        val endParts = userPreferences.bedtimeEndTime.split(":")
        val startMinutes = (startParts.getOrNull(0)?.toIntOrNull() ?: 22) * 60 + (startParts.getOrNull(1)?.toIntOrNull() ?: 0)
        val endMinutes = (endParts.getOrNull(0)?.toIntOrNull() ?: 7) * 60 + (endParts.getOrNull(1)?.toIntOrNull() ?: 0)

        var isBedtime = false
        if (userPreferences.bedtimeEnabled) {
            if (startMinutes <= endMinutes) {
                if (currentDay in userPreferences.bedtimeDays) {
                    isBedtime = currentMinutes in startMinutes until endMinutes
                }
            } else {
                if (currentDay in userPreferences.bedtimeDays && currentMinutes >= startMinutes) {
                    isBedtime = true
                } else if (yesterdayDay in userPreferences.bedtimeDays && currentMinutes < endMinutes) {
                    isBedtime = true
                }
            }
        }

        if (!isBedtime) return@remember Triple(0f, "", false)

        val totalDuration = if (endMinutes > startMinutes) {
            endMinutes - startMinutes
        } else {
            (1440 - startMinutes) + endMinutes
        }

        val elapsed = if (startMinutes <= endMinutes) {
            (currentMinutes - startMinutes).coerceAtLeast(0)
        } else {
            if (currentMinutes >= startMinutes) {
                currentMinutes - startMinutes
            } else {
                (1440 - startMinutes) + currentMinutes
            }
        }

        val progress = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        val remaining = (totalDuration - elapsed).coerceAtLeast(0)
        val h = remaining / 60
        val m = remaining % 60
        
        val formattedTime = if (h > 0) "${h}h ${m}m" else "${m}m"

        Triple(progress, formattedTime, true)
    }

    if (!bedtimeInfo.third) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 1.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "It's Bedtime!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Finish up soon and continue tomorrow.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = bedtimeInfo.second,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            CircularWavyProgressIndicator(
                progress = { bedtimeInfo.first },
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                stroke = Stroke(width = 6.dp.value),
                trackStroke = Stroke(width = 6.dp.value),
                wavelength = 12.dp
            )
        }
    }
}

@Composable
fun OverlayDragHandleWithIndicators(
    modifier: Modifier = Modifier,
    currentUses: Int? = null,
    maxUses: Int? = null,
    emergencyCount: Int? = null,
    isIncentiveLocked: Boolean = false,
    incentiveTier: IncentiveTier? = null,
    bonusUsesLeft: Int = 0
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.weight(1f)) {
            val hasUses = currentUses != null && maxUses != null
            if (isIncentiveLocked && hasUses) {
                var showingUses by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(3000)
                        showingUses = !showingUses
                    }
                }
                Crossfade(targetState = showingUses, label = "dragHandle") { showUses ->
                    if (showUses) {
                        ZenithButton(
                            onClick = { },
                            text = "$currentUses/$maxUses",
                            icon = Icons.Outlined.Timer,
                            type = ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            modifier = Modifier.padding(start = 16.dp).widthIn(max = 110.dp),
                            isDisableWeight = true,
                            isDisableExpand = true,
                            backgroundProgressProvider = { ((maxUses - currentUses).toFloat() / maxUses.toFloat()).coerceIn(0f, 1f) },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        val tier = incentiveTier ?: IncentiveTier.LOCKED
                        val displayText = when {
                            tier == IncentiveTier.LOCKED -> "Locked"
                            tier.bonusUses < Int.MAX_VALUE -> "$bonusUsesLeft left"
                            else -> "Unlocked"
                        }
                        val icon = when {
                            tier == IncentiveTier.LOCKED -> Icons.Outlined.Lock
                            tier == IncentiveTier.LIMITED -> Icons.Outlined.Lock
                            tier == IncentiveTier.MODERATE -> Icons.Outlined.Timer
                            else -> Icons.Outlined.Bolt
                        }
                        val container = when {
                            tier == IncentiveTier.LOCKED -> MaterialTheme.colorScheme.errorContainer
                            tier.bonusUses < Int.MAX_VALUE && bonusUsesLeft <= 0 -> MaterialTheme.colorScheme.errorContainer
                            tier.bonusUses < Int.MAX_VALUE -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val content = when {
                            tier == IncentiveTier.LOCKED -> MaterialTheme.colorScheme.error
                            tier.bonusUses < Int.MAX_VALUE && bonusUsesLeft <= 0 -> MaterialTheme.colorScheme.error
                            tier.bonusUses < Int.MAX_VALUE -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.primary
                        }
                        ZenithButton(
                            onClick = { },
                            text = displayText,
                            icon = icon,
                            type = ZenithButtonType.Tonal,
                            size = ZenithButtonSize.Small,
                            modifier = Modifier.padding(start = 16.dp).widthIn(max = 110.dp),
                            isDisableWeight = true,
                            isDisableExpand = true,
                            containerColor = container,
                            contentColor = content
                        )
                    }
                }
            } else if (isIncentiveLocked) {
                val tier = incentiveTier ?: IncentiveTier.LOCKED
                val displayText = when {
                    tier == IncentiveTier.LOCKED -> "Locked"
                    tier.bonusUses < Int.MAX_VALUE -> "$bonusUsesLeft left"
                    else -> "Unlocked"
                }
                val icon = when {
                    tier == IncentiveTier.LOCKED -> Icons.Outlined.Lock
                    tier == IncentiveTier.LIMITED -> Icons.Outlined.Lock
                    tier == IncentiveTier.MODERATE -> Icons.Outlined.Timer
                    else -> Icons.Outlined.Bolt
                }
                val container = when {
                    tier == IncentiveTier.LOCKED -> MaterialTheme.colorScheme.errorContainer
                    tier.bonusUses < Int.MAX_VALUE && bonusUsesLeft <= 0 -> MaterialTheme.colorScheme.errorContainer
                    tier.bonusUses < Int.MAX_VALUE -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val content = when {
                    tier == IncentiveTier.LOCKED -> MaterialTheme.colorScheme.error
                    tier.bonusUses < Int.MAX_VALUE && bonusUsesLeft <= 0 -> MaterialTheme.colorScheme.error
                    tier.bonusUses < Int.MAX_VALUE -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.primary
                }
                ZenithButton(
                    onClick = { },
                    text = displayText,
                    icon = icon,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    modifier = Modifier.padding(start = 16.dp).widthIn(max = 110.dp),
                    isDisableWeight = true,
                    isDisableExpand = true,
                    containerColor = container,
                    contentColor = content
                )
            } else if (hasUses) {
                ZenithButton(
                    onClick = { },
                    text = "$currentUses/$maxUses",
                    icon = Icons.Outlined.Timer,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    modifier = Modifier.padding(start = 16.dp).widthIn(max = 110.dp),
                    isDisableWeight = true,
                    isDisableExpand = true,
                    backgroundProgressProvider = { ((maxUses - currentUses).toFloat() / maxUses.toFloat()).coerceIn(0f, 1f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (emergencyCount != null) {
                ZenithButton(
                    onClick = { },
                    text = "$emergencyCount",
                    icon = Icons.Outlined.Bolt,
                    type = ZenithButtonType.Tonal,
                    size = ZenithButtonSize.Small,
                    modifier = Modifier.padding(end = 16.dp).widthIn(max = 110.dp),
                    isDisableWeight = true,
                    isDisableExpand = true,
                    backgroundProgressProvider = { (emergencyCount.toFloat() / 3f).coerceIn(0f, 1f) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun TotalUsagePill(totalGlobalUsageToday: Long, userPrefs: UserPreferences, modifier: Modifier = Modifier) {
    if (!userPrefs.totalUsagePillEnabled) return

    val screenTimeTargetMinutes = userPrefs.screenTimeTargetMinutes
    val totalUsageMinutes = totalGlobalUsageToday / 60000
    val isTargetExceeded = totalUsageMinutes >= screenTimeTargetMinutes && screenTimeTargetMinutes > 0

    val totalPillColor = if (isTargetExceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val totalPillContentColor = if (isTargetExceeded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary

    val baseColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.tertiary
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val cornerRadiusLarge = 24.dp
    val cornerRadiusSmall = 4.dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            color = totalPillColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusLarge,
                bottomStart = cornerRadiusLarge,
                topEnd = cornerRadiusSmall,
                bottomEnd = cornerRadiusSmall
            )
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.labelSmall,
                color = totalPillContentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        val remainingMinutes = (screenTimeTargetMinutes - totalUsageMinutes).coerceAtLeast(0)
        val fillRatio = if (screenTimeTargetMinutes > 0) {
            remainingMinutes.toFloat() / screenTimeTargetMinutes
        } else 0f

        Surface(
            color = baseColor,
            shape = RoundedCornerShape(
                topStart = cornerRadiusSmall,
                bottomStart = cornerRadiusSmall,
                topEnd = cornerRadiusLarge,
                bottomEnd = cornerRadiusLarge
            )
        ) {
            val separatorColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier.drawBehind {
                    val progressWidth = size.width * fillRatio.coerceIn(0f, 1f)
                    drawRect(
                        color = fillColor,
                        size = Size(width = progressWidth, height = size.height)
                    )

                    if (fillRatio > 0f && fillRatio < 1f) {
                        drawRect(
                            color = separatorColor,
                            topLeft = Offset(progressWidth - 1.dp.toPx(), 0f),
                            size = Size(width = 2.dp.toPx(), height = size.height)
                        )
                    }
                }
            ) {
                Text(
                    text = formatMillis(totalGlobalUsageToday),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EmergencyButton(onEmergencyUse: () -> Unit, onHoldingChange: (Boolean) -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        onHoldingChange(isPressed)
        if (isPressed) {
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val p = (elapsed.toFloat() / 5000f).coerceIn(0f, 1f)
                holdProgress.snapTo(p)
                if (p >= 1f) break
                delay(16)
            }
            onEmergencyUse()
            holdProgress.snapTo(0f)
        } else {
            holdProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }

    ZenithButton(
        onClick = {},
        type = ZenithButtonType.Filled,
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        backgroundProgressProvider = { holdProgress.value },
        interactionSource = interactionSource,
        fillMaxWidth = true,
        size = ZenithButtonSize.ExtraLarge,
        content = {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            val secondsRemaining = kotlin.math.ceil(5 * (1f - holdProgress.value)).toInt()
            Text(
                text = if (isPressed) "Hold for ${secondsRemaining}s..." else "Hold for 5s to use Emergency",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@Composable
fun CloseAppTextButton(
    onCloseApp: () -> Unit,
    autoKickProgress: () -> Float = { 0f },
    size: ZenithButtonSize = ZenithButtonSize.ExtraLarge
) {
    ZenithButton(
        onClick = onCloseApp,
        text = "Close App",
        type = ZenithButtonType.Text,
        contentColor = MaterialTheme.colorScheme.error,
        backgroundProgressProvider = autoKickProgress,
        fillMaxWidth = true,
        size = size
    )
}

@Composable
fun DurationButtonsGrid(remainingMinutes: Int?, onAllowUse: (Int) -> Unit) {
    val b20 = if (remainingMinutes != null && remainingMinutes < 20) remainingMinutes else 20
    val b10 = if (remainingMinutes != null && remainingMinutes < 10) (remainingMinutes * 0.5).toInt().coerceAtLeast(1) else 10
    val b5 = if (remainingMinutes != null && remainingMinutes < 5) {
        (remainingMinutes * 0.75).toInt().coerceAtLeast(1)
    } else 5
    val b2 = if (remainingMinutes != null && remainingMinutes < 2) remainingMinutes else 2

    val outerRadius = 24.dp
    val innerRadius = 4.dp

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DurationButton(
                minutes = b2,
                delaySeconds = 0,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = outerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
            DurationButton(
                minutes = b5,
                delaySeconds = 3,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = outerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DurationButton(
                minutes = b10,
                delaySeconds = 6,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = outerRadius, bottomEnd = innerRadius)
            )
            DurationButton(
                minutes = b20,
                delaySeconds = 10,
                onAllowUse = onAllowUse,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = innerRadius, bottomEnd = outerRadius)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DurationButton(
    minutes: Int,
    delaySeconds: Int,
    onAllowUse: (Int) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large
) {
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    val progressState = remember { Animatable(0f) }
    var isEnabled by remember { mutableStateOf(delaySeconds <= 0) }

    LaunchedEffect(startAnimation) {
        if (startAnimation) {
            if (delaySeconds > 0) {
                val totalMillis = delaySeconds * 1000L
                val startTime = System.currentTimeMillis()
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val p = (elapsed.toFloat() / totalMillis).coerceIn(0f, 1f)
                    progressState.snapTo(p)
                    if (p >= 1f) break
                    delay(16)
                }
                isEnabled = true
            } else {
                progressState.snapTo(1f)
                isEnabled = true
            }
        }
    }
    val buttonScale = remember { Animatable(1f) }

    LaunchedEffect(isEnabled) {
        if (isEnabled) {
            buttonScale.animateTo(
                targetValue = 1f,
                initialVelocity = 1.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    FilledTonalButton(
        onClick = { if (isEnabled) onAllowUse(minutes) },
        enabled = isEnabled,
        modifier = modifier
            .height(64.dp)
            .graphicsLayer {
                scaleX = buttonScale.value
                scaleY = buttonScale.value
                alpha = if (isEnabled) 1f else 0.8f
            },
        shape = shape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AnimatedContent(
            targetState = isEnabled,
            transitionSpec = {
                fadeIn(animationSpec = tween(400))
                    .togetherWith(fadeOut(animationSpec = tween(200)))
            },
            label = "buttonContent"
        ) { enabled ->
            if (!enabled) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progressState.value },
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        stroke = Stroke(width = 6.dp.value),
                        trackStroke = Stroke(width = 6.dp.value),
                        wavelength = 12.dp
                    )
                    val secondsLeft by remember(delaySeconds) {
                        derivedStateOf {
                            if (delaySeconds > 0) {
                                kotlin.math.ceil(delaySeconds * (1f - progressState.value)).toInt().coerceAtLeast(1)
                            } else 0
                        }
                    }

                    if (secondsLeft > 0) {
                        Text(
                            text = secondsLeft.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            } else {
                Text(
                    text = "$minutes mins",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h ${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CurrentEventPill(
    currentEvent: CurrentCalendarEvent,
    modifier: Modifier = Modifier
) {
    val eventProgress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentEvent) {
        while (true) {
            val now = System.currentTimeMillis()
            val duration = currentEvent.eventEndMillis - currentEvent.eventStartMillis
            eventProgress.floatValue = if (duration > 0) {
                ((now - currentEvent.eventStartMillis).toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            delay(100)
        }
    }

    val pillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val pillLarge = 24.dp
    val pillSmall = 4.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxHeight(),
            shape = RoundedCornerShape(
                topStart = pillLarge, bottomStart = pillLarge,
                topEnd = pillSmall, bottomEnd = pillSmall
            )
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Surface(
            color = pillColor,
            shape = RoundedCornerShape(pillSmall),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                RollingText(
                    text = currentEvent.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!currentEvent.description.isNullOrBlank()) {
                    RollingText(
                        text = currentEvent.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            color = pillColor,
            modifier = Modifier.fillMaxHeight(),
            shape = RoundedCornerShape(
                topStart = pillSmall, bottomStart = pillSmall,
                topEnd = pillLarge, bottomEnd = pillLarge
            )
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(
                    progress = { eventProgress.floatValue.coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    stroke = Stroke(width = 6.dp.value),
                    trackStroke = Stroke(width = 6.dp.value),
                    wavelength = 8.dp
                )
            }
        }
    }
}

@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight? = null,
    color: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val textWidth = remember(text, style, fontWeight) {
        val mergedStyle = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = mergedStyle,
            maxLines = 1,
            softWrap = false
        ).size.width
    }
    var containerWidth by remember { mutableIntStateOf(0) }
    val needsScroll = textWidth > containerWidth
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(needsScroll, textWidth, containerWidth) {
        if (needsScroll && textWidth > 0 && containerWidth > 0) {
            val maxScroll = -(textWidth - containerWidth + 24f).coerceAtMost(0f)
            while (true) {
                offsetX.animateTo(
                    targetValue = maxScroll,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                delay(1500)
                offsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                delay(1500)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer { clip = true }
            .onSizeChanged { containerWidth = it.width },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.graphicsLayer { translationX = offsetX.value }
        )
    }
}
