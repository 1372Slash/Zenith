package com.etrisad.zenith.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.FontOption
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences
import com.etrisad.zenith.ui.onboarding.onboardingAnchor

@Composable
fun AppearanceSettings(
    preferences: UserPreferences,
    onThemeChange: (ThemeConfig) -> Unit,
    onFontChange: (FontOption) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExpressiveColorsChange: (Boolean) -> Unit,
    onHeroGradientBallsChange: (Boolean) -> Unit,
    onHeroBallsIdleAnimationChange: (Boolean) -> Unit,
    onHeroAmbientGlowScaleChange: (Float) -> Unit,
    onFloatingTabBarEnabledChange: (Boolean) -> Unit,
    onHeaderInfoButtonEnabledChange: (Boolean) -> Unit,
    onNavigateToGSFlexCustomizer: () -> Unit,
    onNavigateToOverlayAppearance: () -> Unit
) {
    Column(modifier = Modifier.onboardingAnchor("settings.appearance")) {
        PreferenceCategory(title = "Theming")

        ThemeSelector(
            selectedTheme = preferences.themeConfig,
            onThemeChange = onThemeChange,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        FontSelector(
            selectedFont = preferences.fontOption,
            onFontChange = onFontChange,
            onCustomizeGSFlex = onNavigateToGSFlexCustomizer,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Dynamic Color",
            description = "Apply system wallpaper colors (Android 12+)",
            checked = preferences.dynamicColor,
            onCheckedChange = onDynamicColorChange,
            icon = Icons.Outlined.Palette,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Expressive Color Set",
            description = "Tone down backgrounds and make containers standout",
            checked = preferences.expressiveColors,
            onCheckedChange = onExpressiveColorsChange,
            icon = Icons.Outlined.Layers,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Hero Ambient Glow",
            description = "Category-colored ambient glow behind the home hero card; larger and brighter with today's usage",
            checked = preferences.heroGradientBallsEnabled,
            onCheckedChange = onHeroGradientBallsChange,
            icon = Icons.Outlined.Radar,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        GlowScaleSliderItem(
            title = "Base Glow Scale",
            description = "Adjust the default base size scale for the hero ambient glow",
            scale = preferences.heroAmbientGlowScale,
            onScaleChange = onHeroAmbientGlowScaleChange,
            enabled = preferences.heroGradientBallsEnabled,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Glow Motion Effect",
            description = "Let hero ambient glow float gently while the home hero card is visible",
            checked = preferences.heroBallsIdleAnimationEnabled,
            onCheckedChange = onHeroBallsIdleAnimationChange,
            icon = Icons.Outlined.Animation,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp),
            enabled = preferences.heroGradientBallsEnabled
        )

        Spacer(modifier = Modifier.height(16.dp))
        PreferenceCategory(title = "Layout & Navigation")

        SettingsToggle(
            title = "Floating Tab Bar",
            description = "Use the new Material 3 Expressive floating navigation",
            checked = preferences.floatingTabBarEnabled,
            onCheckedChange = onFloatingTabBarEnabledChange,
            icon = Icons.Outlined.Flaky,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggle(
            title = "Header Info Button",
            description = "Show the info button in the top bar for help about each screen",
            checked = preferences.headerInfoButtonEnabled,
            onCheckedChange = onHeaderInfoButtonEnabledChange,
            icon = Icons.Outlined.Info,
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))
        SettingsActionItem(
            title = "Overlay Appearance",
            summary = "Customize bottom sheet colors, opacity, and sizing",
            onClick = onNavigateToOverlayAppearance,
            icon = Icons.Outlined.Style,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
        )
    }
}

@Composable
private fun GlowScaleSliderItem(
    title: String,
    description: String,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glowScaleCardAlpha"
    )

    var sliderValue by remember(scale) { mutableFloatStateOf(scale) }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AspectRatio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(java.util.Locale.US, "%.1fx", sliderValue),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ZoomOut,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onScaleChange(sliderValue) },
                    valueRange = 0.8f..2.5f,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Icon(
                    imageVector = Icons.Outlined.ZoomIn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
