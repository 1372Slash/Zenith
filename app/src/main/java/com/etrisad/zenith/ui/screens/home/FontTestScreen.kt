package com.etrisad.zenith.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.etrisad.zenith.data.preferences.GSFlexPreset
import com.etrisad.zenith.ui.components.ZenithToggleButtonGroup
import com.etrisad.zenith.ui.components.ZenithToggleOption
import com.etrisad.zenith.ui.components.ZenithButtonSize
import com.etrisad.zenith.ui.theme.GSFlexSettings
import com.etrisad.zenith.ui.theme.VariableFontFactory
import com.etrisad.zenith.ui.screens.settings.VariableSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontTestScreen(
    onBack: () -> Unit,
    innerPadding: PaddingValues
) {
    var settings by remember { mutableStateOf(GSFlexSettings(preset = GSFlexPreset.ZENITH)) }
    var customText by remember { mutableStateOf("Zenith") }
    var selectedTab by remember { mutableIntStateOf(0) }

    val dynamicTypography = VariableFontFactory.createTypography(settings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = innerPadding.calculateTopPadding())
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Custom") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Scale") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Presets") })
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Interactive Text") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        shape = MaterialTheme.shapes.large
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = customText,
                                style = dynamicTypography.displayLarge.copy(
                                    fontSize = 64.sp,
                                    lineHeight = 64.sp,
                                    letterSpacing = (-2).sp
                                )
                            )
                            Text(
                                text = "Custom Editorial Treatment",
                                style = dynamicTypography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                1 -> {
                    Text("Dynamic Typescale", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    TypescaleItem("Display Large", dynamicTypography.displayLarge)
                    TypescaleItem("Headline Medium", dynamicTypography.headlineMedium)
                    TypescaleItem("Title Large", dynamicTypography.titleLarge)
                    TypescaleItem("Body Large", dynamicTypography.bodyLarge)
                    TypescaleItem("Label Small", dynamicTypography.labelSmall)
                }
                2 -> {
                    ZenithProposeContent(
                        currentPreset = settings.preset,
                        onPresetChange = { settings = settings.copy(preset = it) },
                        typography = dynamicTypography
                    )
                }
            }

            if (selectedTab != 2) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Global Axis Override (Simple Custom)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                
                val currentAxes = settings.display
                fun updateGlobal(newVal: (com.etrisad.zenith.ui.theme.FontAxes) -> com.etrisad.zenith.ui.theme.FontAxes) {
                    settings = settings.copy(
                        preset = GSFlexPreset.CUSTOM,
                        display = newVal(settings.display),
                        headline = newVal(settings.headline),
                        body = newVal(settings.body)
                    )
                }

                VariableSlider(label = "Weight", value = currentAxes.weight, range = 100f..1000f, onValueChange = { w -> updateGlobal { it.copy(weight = w) } })
                VariableSlider(label = "Width", value = currentAxes.width, range = 25f..150f, onValueChange = { w -> updateGlobal { it.copy(width = w) } })
                VariableSlider(label = "Optical Size", value = currentAxes.opsz, range = 6f..72f, onValueChange = { v -> updateGlobal { it.copy(opsz = v) } })
                VariableSlider(label = "Grade", value = currentAxes.grade, range = -200f..200f, onValueChange = { v -> updateGlobal { it.copy(grade = v) } })
                VariableSlider(label = "Slant", value = currentAxes.slant, range = -10f..0f, onValueChange = { v -> updateGlobal { it.copy(slant = v) } })
                VariableSlider(label = "Roundness", value = currentAxes.roundness, range = 0f..100f, onValueChange = { v -> updateGlobal { it.copy(roundness = v) } })
            }

            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 32.dp))
        }
    }
}

@Composable
fun ZenithProposeContent(
    currentPreset: GSFlexPreset,
    onPresetChange: (GSFlexPreset) -> Unit,
    typography: Typography
) {
    val presetOptions = listOf(
        GSFlexPreset.ZENITH to "Zenith",
        GSFlexPreset.NEO to "Neo",
        GSFlexPreset.COMPACT to "Impact",
        GSFlexPreset.AIRY to "Airy"
    )

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Pro Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            ZenithToggleButtonGroup(
                options = presetOptions.map { ZenithToggleOption(text = it.second) },
                selectedIndices = setOf(presetOptions.indexOfFirst { it.first == currentPreset }.coerceAtLeast(0)),
                onToggle = { onPresetChange(presetOptions[it].first) },
                size = ZenithButtonSize.Medium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(currentPreset.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Material 3 Expressive preset optimized for the Zenith design system.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ZenithProposeItem(
            level = "Display (Impact)",
            style = typography.displayLarge,
            description = "Editorial hero treatments."
        )

        ZenithProposeItem(
            level = "Headline (Expressive)",
            style = typography.headlineMedium,
            description = "Section headers and navigation."
        )

        ZenithProposeItem(
            level = "Body (Optimized)",
            style = typography.bodyMedium,
            description = "Primary reading experience."
        )
    }
}

@Composable
fun ZenithProposeItem(
    level: String,
    style: androidx.compose.ui.text.TextStyle,
    description: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = level, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        Text(
            text = "Zenith Design",
            style = style
        )
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@Composable
fun TypescaleItem(label: String, style: androidx.compose.ui.text.TextStyle) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            text = "Google Sans Flex",
            style = style
        )
    }
}
