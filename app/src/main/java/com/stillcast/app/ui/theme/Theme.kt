package com.stillcast.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF173E35)
val Lime = Color(0xFFDDEA9B)

private val LightColors = lightColorScheme(
    primary = Color(0xFF245F50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8CA),
    onPrimaryContainer = Forest,
    secondary = Color(0xFF536244),
    secondaryContainer = Color(0xFFE5EACF),
    onSecondaryContainer = Forest,
    background = Color(0xFFF7F7F0),
    onBackground = Color(0xFF1F302A),
    surface = Color(0xFFF7F7F0),
    onSurface = Color(0xFF1F302A),
    surfaceVariant = Color(0xFFEBEDE3),
    onSurfaceVariant = Color(0xFF566258),
    surfaceContainer = Color(0xFFEEF0E6),
    surfaceContainerLow = Color(0xFFF1F2E9),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF748073),
    outlineVariant = Color(0xFFD5DACC),
    error = Color(0xFF9B3D24),
    errorContainer = Color(0xFFFFE4D9),
    onErrorContainer = Color(0xFF6A291A),
)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Forest,
    primaryContainer = Color(0xFF2B4B40),
    onPrimaryContainer = Color(0xFFE0ECC9),
    secondary = Color(0xFFC4D0AF),
    secondaryContainer = Color(0xFF344A38),
    onSecondaryContainer = Color(0xFFE5EACF),
    background = Color(0xFF111C18),
    onBackground = Color(0xFFE7EBDF),
    surface = Color(0xFF111C18),
    onSurface = Color(0xFFE7EBDF),
    surfaceVariant = Color(0xFF28372E),
    onSurfaceVariant = Color(0xFFB7C5B6),
    surfaceContainer = Color(0xFF1A2922),
    surfaceContainerLow = Color(0xFF17251F),
    surfaceContainerLowest = Color(0xFF0C1510),
    outline = Color(0xFF8B9B88),
    outlineVariant = Color(0xFF3F5142),
    error = Color(0xFFFFB59C),
    errorContainer = Color(0xFF532E23),
    onErrorContainer = Color(0xFFFFDCCD),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 38.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
)

@Composable
fun StillCastTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
}
