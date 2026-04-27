package com.etrisad.zenith.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.etrisad.zenith.R

// Konfigurasi Google Fonts Provider
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// 1. Google Sans Flex
val googleSansFlexName = GoogleFont("Google Sans Flex")
val GoogleSansFlexFontFamily = FontFamily(
    Font(googleFont = googleSansFlexName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = googleSansFlexName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = googleSansFlexName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = googleSansFlexName, fontProvider = provider, weight = FontWeight.Bold)
)

// 2. Nunito (Rounded)
val nunitoName = GoogleFont("Nunito")
val NunitoFontFamily = FontFamily(
    Font(googleFont = nunitoName, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = nunitoName, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = nunitoName, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = nunitoName, fontProvider = provider, weight = FontWeight.Bold)
)

// Fungsi untuk membuat Typography berdasarkan FontFamily
private fun createTypography(fontFamily: FontFamily): Typography {
    return Typography(
        displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
        displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
        bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
    )
}

val GoogleSansFlexTypography = createTypography(GoogleSansFlexFontFamily)
val NunitoTypography = createTypography(NunitoFontFamily)
val SystemTypography = createTypography(FontFamily.Default)

// Default export (keep for backward compatibility if needed)
val Typography = SystemTypography
