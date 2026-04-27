package com.etrisad.zenith.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.etrisad.zenith.data.preferences.FontOption

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

@Composable
fun ZenithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    fontOption: FontOption = FontOption.SYSTEM,
    expressiveColors: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (expressiveColors) {
                if (darkTheme) {
                    base.copy(
                        background = base.surface, // Toned background from Material You
                        surface = base.surface,
                        surfaceContainer = base.surfaceContainerLowest, // Blackish container from Material You
                        surfaceContainerLow = base.surfaceContainerLowest,
                        surfaceContainerLowest = base.surfaceContainerLowest
                    )
                } else {
                    base.copy(
                        background = Color(0xFFFBF8FF), // Tone base
                        surface = Color(0xFFFBF8FF),
                        surfaceContainer = Color.White,
                        surfaceContainerLow = Color.White,
                        surfaceContainerLowest = Color.White
                    )
                }
            } else base
        }

        darkTheme -> {
            if (expressiveColors) {
                DarkColorScheme.copy(
                    background = SurfaceDark, // Toned background (Indigo Slate)
                    surface = SurfaceDark,
                    surfaceContainer = BackgroundDark, // Blackish container (Deep Charcoal)
                    surfaceContainerLow = BackgroundDark,
                    surfaceContainerLowest = BackgroundDark
                )
            } else DarkColorScheme
        }

        else -> {
            if (expressiveColors) {
                LightColorScheme.copy(
                    background = BackgroundLight, // Tone base
                    surface = SurfaceLight,
                    surfaceContainer = Color.White,
                    surfaceContainerLow = Color.White,
                    surfaceContainerLowest = Color.White
                )
            } else LightColorScheme
        }
    }

    val typography = when (fontOption) {
        FontOption.SYSTEM -> SystemTypography
        FontOption.GOOGLE_SANS_FLEX -> GoogleSansFlexTypography
        FontOption.NUNITO -> NunitoTypography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
