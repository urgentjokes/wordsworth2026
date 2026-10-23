package com.example.wordsworth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Centralized palette constants used across WordsWorth screens and components.
 *
 * Grouping semantic colors here keeps visual styling consistent across the board grid,
 * tile rack, score cards, and dialogs.
 */
object WordsWorthColors {
    // Board frame and divider tones
    val BoardFrameBackground = Color(0xFF4A3222)
    val WarmDivider = Color(0xFFC8B496)

    // Board cell backgrounds
    val BlockedCellBackground = Color(0xFF161311)
    val HoveredCellBackground = Color(0xFFFFE082)
    val FocusedEmptyCellBackground = Color(0xFFE3F2FD)
    val EmptyCellBackground = Color(0xFFEDE4D3)
    val ValidCellBackground = Color(0xFFE8F5E9)
    val InvalidCellBackground = Color(0xFFFFEBEE)
    val UncheckedLetterCellBackground = Color(0xFFFFF8E1)

    // Board cell borders and cursor accents
    val BlockedCellBorder = Color(0xFF0B0908)
    val HoveredCellBorder = Color(0xFFFFA000)
    val FocusedCursorBlue = Color(0xFF1565C0)
    val ValidCellBorder = Color(0xFF2E7D32)
    val InvalidCellBorder = Color(0xFFC62828)
    val UncheckedLetterCellBorder = Color(0xFFB89B72)
    val EmptyCellBorder = Color(0xFFC5B69E)

    // Word validation list item colors
    val ValidWordSurface = Color(0xFFE8F5E9)
    val ValidWordBorder = Color(0xFFA5D6A7)
    val ValidWordText = Color(0xFF1B5E20)
    val InvalidWordSurface = Color(0xFFFFEBEE)
    val InvalidWordBorder = Color(0xFFEF9A9A)
    val InvalidWordText = Color(0xFFB71C1C)

    // Rack tile colors
    val TileSelectedBackground = Color(0xFFFFE082)
    val TileDefaultBackground = Color(0xFFFFF9E6)
    val FloatingTileBackground = Color(0xFFFFF3CD)
    val TileSelectedBorder = Color(0xFF5D4037)
    val TileDefaultBorder = Color(0xFFC8B496)
    val TilePrimaryText = Color(0xFF2C1E18)
    val TileSubscriptText = Color(0xFF5D4037)
    val EraserSelectedBackground = Color(0xFFFFCDD2)
    val EraserDefaultBackground = Color(0xFFF5ECE0)

    // Time Attack active button accent
    val TimeAttackActiveTeal = Color(0xFF005B6E)
}

/**
 * Warm crossword-inspired Material 3 light color scheme for WordsWorth.
 */
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

/**
 * Root Material 3 theme wrapper for the WordsWorth application.
 */
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
