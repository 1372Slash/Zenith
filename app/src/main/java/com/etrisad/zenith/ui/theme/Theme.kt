package com.etrisad.zenith.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ZenithPrimary,
    onPrimary = ZenithOnPrimary,
    primaryContainer = ZenithPrimaryContainer,
    onPrimaryContainer = ZenithOnPrimaryContainer,
    secondary = ZenithSecondary,
    onSecondary = ZenithOnSecondary,
    secondaryContainer = ZenithSecondaryContainer,
    onSecondaryContainer = ZenithOnSecondaryContainer,
    tertiary = ZenithTertiary,
    onTertiary = ZenithOnTertiary,
    tertiaryContainer = ZenithTertiaryContainer,
    onTertiaryContainer = ZenithOnTertiaryContainer,
    background = ZenithBackground,
    onBackground = ZenithOnBackground,
    surface = ZenithSurface,
    onSurface = ZenithOnSurface,
    error = ZenithError,
    onError = ZenithOnError
)

private val LightColorScheme = lightColorScheme(
    primary = ZenithPrimary,
    onPrimary = ZenithOnPrimary,
    primaryContainer = ZenithPrimaryContainer,
    onPrimaryContainer = ZenithOnPrimaryContainer,
    secondary = ZenithSecondary,
    onSecondary = ZenithOnSecondary,
    secondaryContainer = ZenithSecondaryContainer,
    onSecondaryContainer = ZenithOnSecondaryContainer,
    tertiary = ZenithTertiary,
    onTertiary = ZenithOnTertiary,
    tertiaryContainer = ZenithTertiaryContainer,
    onTertiaryContainer = ZenithOnTertiaryContainer,
    // Slightly lighter background for light mode
    background = Color(0xFFFBFDF8),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFBFDF8),
    onSurface = Color(0xFF191C19),
    error = ZenithError,
    onError = ZenithOnError
)

@Composable
fun ZenithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
