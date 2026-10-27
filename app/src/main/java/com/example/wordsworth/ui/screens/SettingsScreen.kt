package com.example.wordsworth.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.JapaneseVerbRule
import com.example.wordsworth.KeyboardLayout
import com.example.wordsworth.LetterSprinkleMode
import com.example.wordsworth.ScoringMode
import kotlin.math.roundToInt

/**
 * Settings screen accessible via the gear icon in the top-right corner of the main screen.
 *
 * Allows the player to configure:
 * 1. **Keyboard Layout**: Toggle between [KeyboardLayout.ALPHABETICAL] and [KeyboardLayout.QWERTY].
 * 2. **Extended Word Scoring**: Toggle between [ScoringMode.EXTENDED_DIFF_ONLY] ("New Letters Only")
 *    and [ScoringMode.CUMULATIVE_EXTENDED] ("Keep Base Word Points").
 * 3. **Duplicate Word Scoring**: Toggle whether placing the same valid word more than once awards
 *    points every time or only the first time it is used.
 * 4. **Previous Games History**: Clear all saved game entries and screenshots from disk.
 */
@Composable
fun SettingsScreen(
    keyboardLayout: KeyboardLayout,
    onChangeKeyboardLayout: (KeyboardLayout) -> Unit,
    scoringMode: ScoringMode,
    onChangeScoringMode: (ScoringMode) -> Unit,
    allowDuplicateWordPoints: Boolean,
    onChangeAllowDuplicateWordPoints: (Boolean) -> Unit,
    japaneseVerbRule: JapaneseVerbRule,
    onChangeJapaneseVerbRule: (JapaneseVerbRule) -> Unit,
    isHapticFeedbackEnabled: Boolean = true,
    onChangeHapticFeedbackEnabled: (Boolean) -> Unit = {},
    letterSprinkleMode: LetterSprinkleMode = LetterSprinkleMode.NONE,
    onChangeLetterSprinkleMode: (LetterSprinkleMode) -> Unit = {},
    savedGamesCount: Int,
    onClearSavedGames: () -> Unit,
    onClearBoard: () -> Unit = {},
    onLoadExample: () -> Unit = {},
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header row with Back button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Settings",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Keyboard Layout Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Keyboard Layout",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose how the letter keys are arranged underneath the game board.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KeyboardLayout.entries.forEach { option ->
                                val isSelected = keyboardLayout == option
                                if (isSelected) {
                                    Button(
                                        onClick = { onChangeKeyboardLayout(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onChangeKeyboardLayout(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Keyboard Haptic Feedback Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = "Keyboard Haptic Feedback",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isHapticFeedbackEnabled) {
                                    "Vibrate slightly on keyboard tile taps and drags."
                                } else {
                                    "Keyboard vibration is turned off."
                                },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isHapticFeedbackEnabled,
                            onCheckedChange = onChangeHapticFeedbackEnabled
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Extended Word Scoring Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Extended Word Scoring",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scoringMode.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ScoringMode.entries.forEach { option ->
                                val isSelected = scoringMode == option
                                if (isSelected) {
                                    Button(
                                        onClick = { onChangeScoringMode(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onChangeScoringMode(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Duplicate Word Scoring Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Receive points more than once for the same word?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allowDuplicateWordPoints) {
                                "Placing the same valid word multiple times awards points for every instance."
                            } else {
                                "You can place the same valid word more than once, but you only get points the first time you use it."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                true to "Yes (Every Time)",
                                false to "No (First Time Only)"
                            ).forEach { (optionValue, optionLabel) ->
                                val isSelected = allowDuplicateWordPoints == optionValue
                                if (isSelected) {
                                    Button(
                                        onClick = { onChangeAllowDuplicateWordPoints(optionValue) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = optionLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onChangeAllowDuplicateWordPoints(optionValue) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = optionLabel,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Japanese Verb / Caboose Rule Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Japanese Verb / Caboose Rule",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = japaneseVerbRule.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            JapaneseVerbRule.entries.forEach { option ->
                                val isSelected = japaneseVerbRule == option
                                if (isSelected) {
                                    Button(
                                        onClick = { onChangeJapaneseVerbRule(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onChangeJapaneseVerbRule(option) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = option.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 5. Sprinkle Random Letters Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Sprinkle Letters",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pre-fill random squares on the board with random letters (applies to English and Japanese when starting or clearing a board).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Current selected position label & description
                        Text(
                            text = when (letterSprinkleMode) {
                                LetterSprinkleMode.NONE -> "None: zero"
                                LetterSprinkleMode.FEW -> "Few: 5 percent of available squares"
                                LetterSprinkleMode.SOME -> "Some: 10 pct of available squares"
                                LetterSprinkleMode.MANY -> "Many: 15 pct"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val sliderInteractionSource = remember { MutableInteractionSource() }
                        val isSliderPressed by sliderInteractionSource.collectIsPressedAsState()
                        val isSliderDragged by sliderInteractionSource.collectIsDraggedAsState()
                        val isInteracting = isSliderPressed || isSliderDragged
                        val activeTrackColor = MaterialTheme.colorScheme.primary
                        val inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer

                        // Discrete 4-step Slider: None (0), Few (1), Some (2), Many (3)
                        @OptIn(ExperimentalMaterial3Api::class)
                        Slider(
                            value = letterSprinkleMode.ordinal.toFloat(),
                            onValueChange = { floatVal ->
                                val index = floatVal.roundToInt().coerceIn(0, LetterSprinkleMode.entries.size - 1)
                                onChangeLetterSprinkleMode(LetterSprinkleMode.entries[index])
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = sliderInteractionSource,
                            thumb = { sliderState ->
                                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                val fraction = if (range > 0f) {
                                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                } else 0f
                                val cornerRadius = 8.dp
                                // Aligns the thumb center squarely with the track stop indicator circle
                                // at each position, eliminating the leftward overshoot at None.
                                val xOffset = cornerRadius * (1f - 2f * fraction)
                                val thumbWidth = if (isInteracting) 2.dp else 4.dp
                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset)
                                        .size(width = thumbWidth, height = 44.dp)
                                        .background(
                                            color = activeTrackColor,
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            },
                            track = { sliderState ->
                                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                val fraction = if (range > 0f) {
                                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                } else 0f
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                ) {
                                    val trackWidth = size.width
                                    val trackHeight = size.height
                                    val cornerRadius = trackHeight / 2f
                                    val gap = 6.dp.toPx()
                                    val insideCornerRadius = 2.dp.toPx()
                                    val tickRadius = 2.dp.toPx()
                                    val thumbCenter = cornerRadius + (trackWidth - 2f * cornerRadius) * fraction

                                    if (fraction <= 0f) {
                                        // None: full inactive track with rounded ends
                                        drawRoundRect(
                                            color = inactiveTrackColor,
                                            topLeft = Offset(0f, 0f),
                                            size = Size(trackWidth, trackHeight),
                                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                        )
                                    } else if (fraction >= 1f) {
                                        // Many: full active track with rounded ends
                                        drawRoundRect(
                                            color = activeTrackColor,
                                            topLeft = Offset(0f, 0f),
                                            size = Size(trackWidth, trackHeight),
                                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                        )
                                    } else {
                                        // Few / Some: Active section to the left of the thumb
                                        val activeEnd = thumbCenter - gap
                                        if (activeEnd > 0f) {
                                            val activeRect = RoundRect(
                                                rect = Rect(Offset(0f, 0f), Size(activeEnd, trackHeight)),
                                                topLeft = CornerRadius(cornerRadius, cornerRadius),
                                                bottomLeft = CornerRadius(cornerRadius, cornerRadius),
                                                topRight = CornerRadius(insideCornerRadius, insideCornerRadius),
                                                bottomRight = CornerRadius(insideCornerRadius, insideCornerRadius)
                                            )
                                            val activePath = Path().apply { addRoundRect(activeRect) }
                                            drawPath(activePath, activeTrackColor)
                                        }

                                        // Inactive section to the right of the thumb
                                        val inactiveStart = thumbCenter + gap
                                        if (inactiveStart < trackWidth) {
                                            val inactiveRect = RoundRect(
                                                rect = Rect(Offset(inactiveStart, 0f), Size(trackWidth - inactiveStart, trackHeight)),
                                                topLeft = CornerRadius(insideCornerRadius, insideCornerRadius),
                                                bottomLeft = CornerRadius(insideCornerRadius, insideCornerRadius),
                                                topRight = CornerRadius(cornerRadius, cornerRadius),
                                                bottomRight = CornerRadius(cornerRadius, cornerRadius)
                                            )
                                            val inactivePath = Path().apply { addRoundRect(inactiveRect) }
                                            drawPath(inactivePath, inactiveTrackColor)
                                        }
                                    }

                                    // Draw stop indicator circles at each discrete step (None, Few, Some, Many)
                                    for (i in 0..3) {
                                        val tickFraction = i / 3f
                                        val tickX = cornerRadius + (trackWidth - 2f * cornerRadius) * tickFraction
                                        val dotColor = if (fraction <= 0f) {
                                            activeTrackColor
                                        } else if (fraction >= 1f) {
                                            inactiveTrackColor
                                        } else {
                                            if (tickFraction <= fraction) inactiveTrackColor else activeTrackColor
                                        }
                                        drawCircle(
                                            color = dotColor,
                                            radius = tickRadius,
                                            center = Offset(tickX, trackHeight / 2f)
                                        )
                                    }
                                }
                            }
                        )

                        // Labels below the slider
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LetterSprinkleMode.entries.forEach { mode ->
                                val isSelected = mode == letterSprinkleMode
                                Text(
                                    text = mode.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 6. Previous Games History Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Previous Games History",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Currently storing $savedGamesCount saved game board(s).",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onClearSavedGames,
                            enabled = savedGamesCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear Previous Games History")
                        }
                    }
                }

                // 5. Board Actions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Board Actions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clear the current game board or load the example puzzle.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onClearBoard()
                                    onBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear Board")
                            }
                            OutlinedButton(
                                onClick = {
                                    onLoadExample()
                                    onBack()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load Example")
                            }
                        }
                    }
                }
            }
        }
    }
}
