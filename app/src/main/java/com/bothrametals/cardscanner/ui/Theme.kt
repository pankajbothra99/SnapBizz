package com.bothrametals.cardscanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Brand palette: steel blue (metal) with a copper accent
internal val Steel = Color(0xFF1F4E79)
internal val SteelDeep = Color(0xFF123456)
internal val SteelLight = Color(0xFF9CC3E8)
internal val Copper = Color(0xFFC0773A)
internal val CopperLight = Color(0xFFF3D2B5)
internal val Success = Color(0xFF2E7D4F)

private val LightColors = lightColorScheme(
    primary = Steel,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F6),
    onPrimaryContainer = Color(0xFF0B2740),
    secondary = Copper,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E6D6),
    onSecondaryContainer = Color(0xFF3D2210),
    tertiary = Success,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4EEDD),
    onTertiaryContainer = Color(0xFF0D3320),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF181C20),
    surface = Color.White,
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFF55606B),
    outline = Color(0xFFB4BEC8),
    outlineVariant = Color(0xFFDCE2E8),
)

private val DarkColors = darkColorScheme(
    primary = SteelLight,
    onPrimary = Color(0xFF0B2740),
    primaryContainer = Color(0xFF173B5C),
    onPrimaryContainer = Color(0xFFD6E6F6),
    secondary = Color(0xFFE8B48A),
    onSecondary = Color(0xFF3D2210),
    secondaryContainer = Color(0xFF5A3A20),
    onSecondaryContainer = Color(0xFFF8E6D6),
    tertiary = Color(0xFF8ED3A8),
    onTertiary = Color(0xFF0D3320),
    tertiaryContainer = Color(0xFF1E4A31),
    onTertiaryContainer = Color(0xFFD4EEDD),
    background = Color(0xFF101418),
    surface = Color(0xFF181D23),
    surfaceVariant = Color(0xFF29313A),
    onSurfaceVariant = Color(0xFFB9C3CD),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val Base = Typography()
private val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** The steel-blue colour used behind the top bars and headers, in light and dark mode. */
@Composable
fun brandBar(): Color = if (isSystemInDarkTheme()) Color(0xFF173B5C) else Steel
