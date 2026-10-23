package com.example.wordsworth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WordsWorthColorScheme = lightColorScheme(
    primary = Color(0xFF5D4037),
    onPrimary = Color(0xFFFFF8E7),
    secondary = Color(0xFF2E7D32),
    onSecondary = Color.White,
    tertiary = Color(0xFF8D6E63),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF2C1E18),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF2C1E18),
    surfaceVariant = Color(0xFFE8DEC8),
    onSurfaceVariant = Color(0xFF4A3728),
    error = Color(0xFFC62828),
    onError = Color.White
)

@Composable
fun WordsWorthTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WordsWorthColorScheme,
        typography = Typography(),
        content = content
    )
}
