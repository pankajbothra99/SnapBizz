package com.bothrametals.cardscanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Steel blue / aluminium palette
private val Steel = Color(0xFF1F4E79)
private val SteelLight = Color(0xFF9CC3E8)
private val Copper = Color(0xFFB8733D)

private val LightColors = lightColorScheme(
    primary = Steel,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Color(0xFF0B2740),
    secondary = Copper,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E0CF),
    onSecondaryContainer = Color(0xFF3D2210),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EBF0),
)

private val DarkColors = darkColorScheme(
    primary = SteelLight,
    onPrimary = Color(0xFF0B2740),
    primaryContainer = Color(0xFF173B5C),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFE8B48A),
    onSecondary = Color(0xFF3D2210),
    background = Color(0xFF111418),
    surface = Color(0xFF181C21),
    surfaceVariant = Color(0xFF2A3038),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
