package com.chatflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF6C63FF)
private val IndigoDark = Color(0xFF4B46B6)
private val Teal = Color(0xFF00C2A8)
private val Coral = Color(0xFFFF6B6B)
private val Ink = Color(0xFF0E1220)
private val Ash = Color(0xFFF5F6FA)

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E1FF), onPrimaryContainer = IndigoDark,
    secondary = Teal, onSecondary = Color.White,
    tertiary = Coral, onTertiary = Color.White,
    background = Ash, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEDEEF6), onSurfaceVariant = Color(0xFF4A4D5E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9C94FF), onPrimary = Ink,
    primaryContainer = IndigoDark, onPrimaryContainer = Color(0xFFE4E1FF),
    secondary = Teal, onSecondary = Ink,
    tertiary = Coral, onTertiary = Ink,
    background = Color(0xFF0B0E1A), onBackground = Ash,
    surface = Color(0xFF151A2C), onSurface = Ash,
    surfaceVariant = Color(0xFF1E2337), onSurfaceVariant = Color(0xFFBEC2D4)
)

@Composable
fun ChatFlowTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
