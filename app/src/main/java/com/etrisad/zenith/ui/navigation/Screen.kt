package com.etrisad.zenith.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Focus : Screen("focus", "Focus", Icons.Filled.Shield, Icons.Outlined.Shield)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object UsageStats :
        Screen("usage_stats", "Usage Stats", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List)

    object Bedtime : Screen("bedtime", "Bedtime", Icons.Filled.Bedtime, Icons.Outlined.Bedtime)

    object AppDetail : Screen("app_detail/{packageName}", "App Detail", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
}

val navItems = listOf(
    Screen.Home,
    Screen.Focus,
    Screen.Settings
)
