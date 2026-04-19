package com.etrisad.zenith.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Outlined.Home)
    object Focus : Screen("focus", "Focus", Icons.Outlined.Security)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
    object UsageStats : Screen("usage_stats", "Usage Stats", Icons.AutoMirrored.Outlined.List)
}

val navItems = listOf(
    Screen.Home,
    Screen.Focus,
    Screen.Settings
)
