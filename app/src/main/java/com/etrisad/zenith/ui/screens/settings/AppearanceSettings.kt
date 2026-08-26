package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.data.preferences.FontOption
import com.etrisad.zenith.data.preferences.ThemeConfig
import com.etrisad.zenith.data.preferences.UserPreferences

@Composable
fun AppearanceSettings(
    preferences: UserPreferences,
    onThemeChange: (ThemeConfig) -> Unit,
    onFontChange: (FontOption) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExpressiveColorsChange: (Boolean) -> Unit,
    onFloatingTabBarEnabledChange: (Boolean) -> Unit,
    onHeaderInfoButtonEnabledChange: (Boolean) -> Unit,
    onNavigateToGSFlexCustomizer: () -> Unit,
    onNavigateToOverlayAppearance: () -> Unit
) {
    Column {
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
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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
