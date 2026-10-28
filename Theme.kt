package com.nearchat.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF0B1F4B)
val Electric = Color(0xFF4F6BFF)
val Violet = Color(0xFF7C4DFF)
val Online = Color(0xFF22C55E)
val Offline = Color(0xFFEF4444)

val brand = Brush.horizontalGradient(listOf(Electric, Violet))

private val LightScheme = lightColorScheme(
    primary = Navy, onPrimary = Color.White, secondary = Electric,
    background = Color.White, onBackground = Color(0xFF0E1630),
    surface = Color(0xFFF1F3FA), onSurface = Color(0xFF0E1630),
    surfaceVariant = Color(0xFFE4E8F6), onSurfaceVariant = Color(0xFF55607F),
    error = Offline,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FA2FF), onPrimary = Navy, secondary = Electric,
    background = Color(0xFF1B1D21), onBackground = Color(0xFFEDEFF7),
    surface = Color(0xFF272A31), onSurface = Color(0xFFEDEFF7),
    surfaceVariant = Color(0xFF343944), onSurfaceVariant = Color(0xFFA9B1C6),
    error = Offline,
)

@Composable
fun NearChatTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
