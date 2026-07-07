package com.etrisad.zenith.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FreeBreakfast
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RemoveRedEye
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

    object Alarm : Screen("alarm", "Alarm", Icons.Filled.Alarm, Icons.Outlined.Alarm)

    object GracePeriod : Screen("grace_period", "Grace Period", Icons.Filled.FreeBreakfast, Icons.Outlined.FreeBreakfast)

    object EyeCare : Screen("eye_care", "Eye Care", Icons.Filled.Settings, Icons.Outlined.RemoveRedEye)

    object AppDetail : Screen("app_detail/{packageName}", "App Detail", Icons.Filled.Home, Icons.Outlined.Home) {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }

    object DatabaseDebug : Screen("database_debug", "Database Records", Icons.Filled.Settings, Icons.Outlined.Settings)

    object DataRepairment : Screen("data_repairment", "Data Repairment", Icons.Filled.Settings, Icons.Outlined.Settings)

    object FontTest : Screen("font_test", "Font Test", Icons.Filled.Settings, Icons.Outlined.Settings)

    object GSFlexCustomizer : Screen("gs_flex_customizer", "GS Flex Customizer", Icons.Filled.Settings, Icons.Outlined.Settings)

    object SystemUsageDebug : Screen("system_usage_debug", "System Usage Fetch", Icons.Filled.Settings, Icons.Outlined.Settings)

    object OverlayAppearance : Screen("overlay_appearance", "Overlay Appearance", Icons.Filled.Settings, Icons.Outlined.Settings)

    object SettingsCategory : Screen("settings_category/{category}", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings) {
        fun createRoute(category: String) = "settings_category/$category"
    }
}

val navItems = listOf(
    Screen.Home,
    Screen.Focus,
    Screen.Settings
)
