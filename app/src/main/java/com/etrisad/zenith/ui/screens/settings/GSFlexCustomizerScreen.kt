package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.GSFlexPreset
import com.etrisad.zenith.data.preferences.UserPreferencesRepository
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.theme.FontAxes
import com.etrisad.zenith.ui.theme.VariableFontFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GSFlexCustomizerScreen(
    repository: UserPreferencesRepository,
    onBack: () -> Unit,
    innerPadding: PaddingValues
) {
    val preferences by repository.userPreferencesFlow.collectAsState(initial = com.etrisad.zenith.data.preferences.UserPreferences())
    val scope = rememberCoroutineScope()

    var tempSettings by remember(preferences.gsFlexSettings) { mutableStateOf(preferences.gsFlexSettings) }
    var lastNonCustomPreset by remember(preferences.gsFlexSettings) {
        mutableStateOf(if (preferences.gsFlexSettings.preset == GSFlexPreset.CUSTOM) GSFlexPreset.ZENITH else preferences.gsFlexSettings.preset)
    }
    var selectedLevelTab by remember { mutableIntStateOf(0) }

    val previewTypography = VariableFontFactory.createTypography(tempSettings)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Zenith", 
                        style = previewTypography.displayLarge.copy(
                            fontSize = 64.sp,
                            lineHeight = 64.sp,
                            letterSpacing = (-2).sp
                        ), 
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Headline Treatment", style = previewTypography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Standard body text for optimized reading experience in the Zenith app.", style = previewTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp), textAlign = TextAlign.Center)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Design System Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                val presetOptions = listOf(GSFlexPreset.ZENITH to "Zenith", GSFlexPreset.NEO to "Neo", GSFlexPreset.COMPACT to "Impact", GSFlexPreset.AIRY to "Airy")
                ZenithToggleButtonGroup(
                    options = presetOptions.map { ZenithToggleOption(text = it.second) },
                    selectedIndices = setOf(presetOptions.indexOfFirst { it.first == lastNonCustomPreset }),
                    onToggle = {
                        val newPreset = presetOptions[it].first
                        lastNonCustomPreset = newPreset
                        if (tempSettings.preset != GSFlexPreset.CUSTOM) {
                            tempSettings = tempSettings.copy(preset = newPreset)
                        }
                    },
                    size = ZenithButtonSize.Medium
                )
            }

            Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Customize", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Custom Variable", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Override preset with your own fine-tuned axes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = tempSettings.preset == GSFlexPreset.CUSTOM,
                        onCheckedChange = { checked ->
                            tempSettings = tempSettings.copy(
                                preset = if (checked) GSFlexPreset.CUSTOM else lastNonCustomPreset
                            )
                        },
                        thumbContent = {
                            val isChecked = tempSettings.preset == GSFlexPreset.CUSTOM
                            val thumbSize by animateDpAsState(
                                targetValue = if (isChecked) 28.dp else 24.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "thumb_size"
                            )

                            val iconColor by animateColorAsState(
                                targetValue = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                label = "switch_icon_color"
                            )

                            Box(
                                modifier = Modifier.size(thumbSize),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = isChecked,
                                    transitionSpec = {
                                        (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMediumLow)))
                                            .togetherWith(fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                    scaleOut(targetScale = 0.5f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                                    },
                                    label = "switch_icon_anim"
                                ) { targetChecked ->
                                    Icon(
                                        imageVector = if (targetChecked) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (targetChecked) 18.dp else 16.dp),
                                        tint = iconColor
                                    )
                                }
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(visible = tempSettings.preset == GSFlexPreset.CUSTOM, modifier = Modifier.animateContentSize()) {
                Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            val presetAxes = VariableFontFactory.getPresetFontAxes(lastNonCustomPreset)
                            tempSettings = tempSettings.copy(
                                display = presetAxes.first,
                                headline = presetAxes.second,
                                body = presetAxes.third
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy from Current Preset")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SettingsSuggest, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fine-tune Axes per Level", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    PrimaryTabRow(selectedTabIndex = selectedLevelTab, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.clip(RoundedCornerShape(16.dp))) {
                        Tab(selected = selectedLevelTab == 0, onClick = { selectedLevelTab = 0 }, text = { Text("Display") })
                        Tab(selected = selectedLevelTab == 1, onClick = { selectedLevelTab = 1 }, text = { Text("Headline") })
                        Tab(selected = selectedLevelTab == 2, onClick = { selectedLevelTab = 2 }, text = { Text("Body") })
                    }
                    
                    val currentAxes = when(selectedLevelTab) { 0 -> tempSettings.display; 1 -> tempSettings.headline; else -> tempSettings.body }
                    
                    fun updateAxes(newAxes: FontAxes) {
                        tempSettings = when(selectedLevelTab) {
                            0 -> tempSettings.copy(display = newAxes)
                            1 -> tempSettings.copy(headline = newAxes)
                            else -> tempSettings.copy(body = newAxes)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VariableSlider("Weight", currentAxes.weight, 100f..1000f) { updateAxes(currentAxes.copy(weight = it)) }
                        VariableSlider("Width", currentAxes.width, 25f..150f) { updateAxes(currentAxes.copy(width = it)) }
                        VariableSlider("Optical Size", currentAxes.opsz, 6f..72f) { updateAxes(currentAxes.copy(opsz = it)) }
                        VariableSlider("Grade", currentAxes.grade, -200f..200f) { updateAxes(currentAxes.copy(grade = it)) }
                        VariableSlider("Slant", currentAxes.slant, -10f..0f) { updateAxes(currentAxes.copy(slant = it)) }
                        VariableSlider("Roundness", currentAxes.roundness, 0f..100f) { updateAxes(currentAxes.copy(roundness = it)) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 80.dp))
        }

        ExtendedFloatingActionButton(
            onClick = { scope.launch { repository.setGSFlexSettings(tempSettings); onBack() } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    end = 24.dp
                ),
            icon = { Icon(Icons.Outlined.Check, null) },
            text = { Text("Apply Settings") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
fun VariableSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(java.util.Locale.getDefault(), "%.0f", value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant))
    }
}
