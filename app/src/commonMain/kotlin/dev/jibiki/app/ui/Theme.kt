package dev.jibiki.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import dev.jibiki.app.ThemeMode

// 藍 (indigo) primary with a warm 朱 (vermilion) accent.
private val Light = lightColorScheme(
    primary = Color(0xFF2E4A7D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF0B1B3A),
    secondary = Color(0xFFB2462E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD1),
    onSecondaryContainer = Color(0xFF3B0900),
    tertiary = Color(0xFF52634F),
    tertiaryContainer = Color(0xFFD4E8CF),
    background = Color(0xFFFBF9F4),
    surface = Color(0xFFFBF9F4),
    surfaceVariant = Color(0xFFE6E2D9),
    surfaceContainer = Color(0xFFF1EEE6),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    outlineVariant = Color(0xFFCBC6BB),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF102F5E),
    primaryContainer = Color(0xFF2B4677),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFFFB4A1),
    onSecondary = Color(0xFF5F1604),
    secondaryContainer = Color(0xFF8A2F18),
    onSecondaryContainer = Color(0xFFFFDBD1),
    tertiary = Color(0xFFB8CCB4),
    tertiaryContainer = Color(0xFF3B4B38),
    background = Color(0xFF141413),
    surface = Color(0xFF141413),
    surfaceVariant = Color(0xFF47463F),
    surfaceContainer = Color(0xFF201F1D),
    surfaceContainerHigh = Color(0xFF2A2927),
    outlineVariant = Color(0xFF47463F),
)

@Composable
fun JibikiTheme(mode: ThemeMode, fontScale: Float, fontFamily: FontFamily, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.font(fontFamily),
        displayMedium = base.displayMedium.font(fontFamily),
        displaySmall = base.displaySmall.font(fontFamily),
        headlineLarge = base.headlineLarge.font(fontFamily),
        headlineMedium = base.headlineMedium.font(fontFamily),
        headlineSmall = base.headlineSmall.font(fontFamily),
        titleLarge = base.titleLarge.font(fontFamily),
        titleMedium = base.titleMedium.font(fontFamily),
        titleSmall = base.titleSmall.font(fontFamily),
        bodyLarge = base.bodyLarge.font(fontFamily),
        bodyMedium = base.bodyMedium.font(fontFamily),
        bodySmall = base.bodySmall.font(fontFamily),
        labelLarge = base.labelLarge.font(fontFamily),
        labelMedium = base.labelMedium.font(fontFamily),
        labelSmall = base.labelSmall.font(fontFamily),
    )
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * fontScale)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography, content = content)
    }
}

private fun TextStyle.font(family: FontFamily) = copy(fontFamily = family)
