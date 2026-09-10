package com.richang.todo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Pine = Color(0xFF316B57)
val WarmPaper = Color(0xFFF7F6F0)
val Apricot = Color(0xFFD78A53)

private val LightColors = lightColorScheme(
    primary = Pine, onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EDE4), onPrimaryContainer = Color(0xFF204C3C),
    secondary = Color(0xFF79624D), secondaryContainer = Color(0xFFF6E6D6),
    background = WarmPaper, onBackground = Color(0xFF25362E),
    surface = Color(0xFFFEFDF8), onSurface = Color(0xFF25362E),
    surfaceVariant = Color(0xFFEDEEE7), onSurfaceVariant = Color(0xFF747C72),
    outlineVariant = Color(0xFFE3E6DC), error = Color(0xFFB75A40),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA3D3B8), primaryContainer = Color(0xFF284D3C),
    onPrimaryContainer = Color(0xFFD8EFDF),
    background = Color(0xFF141D18), surface = Color(0xFF1D2821),
    surfaceVariant = Color(0xFF2A372E), onSurfaceVariant = Color(0xFFB2BDAF),
)

@Composable
fun RichangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
