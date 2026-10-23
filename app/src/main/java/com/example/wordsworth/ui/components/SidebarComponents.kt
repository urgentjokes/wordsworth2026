package com.example.wordsworth.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.FormedWord
import com.example.wordsworth.SavedGameEntry
import com.example.wordsworth.TallyResult
import com.example.wordsworth.WordsWorthLogic
import com.example.wordsworth.ui.theme.WordsWorthColors
import java.util.Locale

/**
 * Formats a duration in seconds as `M:SS` (e.g., `0:00`, `1:05`, `12:34`).
 */
fun formatTimer(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

/**
 * Displays the game timer card directly beneath the Score card.
 *
 * - In standard untimed mode (`timeAttackMinutes == null`), displays `TIMER` and counts up from `0:00`.
 * - In Time Attack mode, displays `TIME ATTACK` and counts down from the chosen duration.
 * - Turns red ([MaterialTheme.colorScheme.error]) when 10 seconds or fewer remain (`0:10`..`0:00`),
 *   and displays `TIME'S UP` once the countdown expires and locks the board.
 */
@Composable
fun TimerCard(
    timeAttackMinutes: Int?,
    isBoardLocked: Boolean,
    elapsedSecondsProvider: () -> Int
) {
    val elapsed = elapsedSecondsProvider()
    val displaySeconds = if (timeAttackMinutes != null) {
        (timeAttackMinutes * 60 - elapsed).coerceAtLeast(0)
    } else {
        elapsed
    }
    val isTimeUp = timeAttackMinutes != null && isBoardLocked && displaySeconds == 0
    val isLowTime = timeAttackMinutes != null && displaySeconds <= 10
    val containerColor = if (isLowTime) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isLowTime) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val labelText = when {
        isTimeUp -> "TIME'S UP"
        timeAttackMinutes != null -> "TIME ATTACK"
        else -> "TIMER"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = labelText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.85f)
            )
            Text(
                text = formatTimer(displaySeconds),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor
            )
        }
    }
}

/**
 * Left-hand column displaying a scrollable list of saved game board thumbnails,
 * their scores, and elapsed times.
 *
 * - Automatically scrolls to the top whenever a game is newly saved or updated.
 * - Tapping a saved game resumes it on the active board.
 * - Long-pressing a saved game prompts to delete it.
 */
@Composable
fun PreviousGamesColumn(
    savedGames: List<SavedGameEntry>,
    onClickSavedGame: (SavedGameEntry) -> Unit,
    onDeleteSavedGame: (SavedGameEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(428.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Previous Games",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hold entry to delete",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                color = WordsWorthColors.WarmDivider,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (savedGames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved boards yet.\n\nTap Quit → Save final board and score.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val previousGamesListState = rememberLazyListState()
                LaunchedEffect(savedGames.firstOrNull()?.id, savedGames.size) {
                    if (savedGames.isNotEmpty()) {
                        previousGamesListState.scrollToItem(0)
                    }
                }
                LazyColumn(
                    state = previousGamesListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(savedGames, key = { it.id }) { entry ->
                        SavedBoardThumbnailItem(
                            entry = entry,
                            onClick = { onClickSavedGame(entry) },
                            onLongClick = { onDeleteSavedGame(entry) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual card in the "Previous Games" column showing a mini-screenshot of the saved board,
 * its score in points, and the elapsed timer value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedBoardThumbnailItem(
    entry: SavedGameEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry.bitmap != null) {
                Image(
                    bitmap = entry.bitmap.asImageBitmap(),
                    contentDescription = "Mini board screenshot",
                    modifier = Modifier
                        .size(208.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(208.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(WordsWorthColors.BoardFrameBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "6×6",
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${entry.score}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "pts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⏱ ${formatTimer(entry.elapsedSeconds)}",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Column to the immediate left of the crossword board displaying:
 * 1. **High Score** card (tracks the highest-scoring saved game; tapping resumes/displays that game).
 * 2. **Score** card (current board's validated point total).
 * 3. **Timer / Time Attack** card ([TimerCard]).
 * 4. **Words** card (scrollable list of all valid/invalid words formed on the board;
 *    long-pressing an invalid red word opens the whitelist confirmation dialog).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScoreAndWordsColumn(
    highScoreEntry: SavedGameEntry?,
    onClickHighScore: (SavedGameEntry) -> Unit,
    tallyResult: TallyResult?,
    elapsedSecondsProvider: () -> Int,
    timeAttackMinutes: Int?,
    isBoardLocked: Boolean,
    onRequestWhitelistWord: (FormedWord) -> Unit,
    modifier: Modifier = Modifier
) {
    val highScore = highScoreEntry?.score ?: 0
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High Score Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = highScoreEntry != null) {
                    highScoreEntry?.let(onClickHighScore)
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HIGH SCORE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$highScore",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Text(
                        text = "pts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // Current Score Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SCORE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${tallyResult?.totalScore ?: 0}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Text(
                        text = "pts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // Timer / Time Attack Box
        TimerCard(
            elapsedSecondsProvider = elapsedSecondsProvider,
            timeAttackMinutes = timeAttackMinutes,
            isBoardLocked = isBoardLocked
        )

        // Scrollable list of formed words and their point values
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tallyResult != null) {
                            "Words (${tallyResult.words.size})"
                        } else {
                            "Words"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (tallyResult != null && tallyResult.invalidWords.isNotEmpty()) {
                    Text(
                        text = "Hold red word to whitelist",
                        fontSize = 10.sp,
                        color = WordsWorthColors.InvalidCellBorder,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
                    )
                }
                HorizontalDivider(
                    color = WordsWorthColors.WarmDivider,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (tallyResult == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap Validate to check words and score.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (tallyResult.words.isEmpty() && tallyResult.isolatedCells.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No words formed yet.\nPlace 2+ connected letters.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(
                            items = tallyResult.words,
                            key = { "${it.direction}_${it.startRow}_${it.startCol}_${it.word}" }
                        ) { fw ->
                            val bgColor = if (fw.isValid) WordsWorthColors.ValidWordSurface else WordsWorthColors.InvalidWordSurface
                            val borderColor = if (fw.isValid) WordsWorthColors.ValidWordBorder else WordsWorthColors.InvalidWordBorder
                            val textColor = if (fw.isValid) WordsWorthColors.ValidWordText else WordsWorthColors.InvalidWordText

                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            if (!fw.isValid) {
                                                onRequestWhitelistWord(fw)
                                            }
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = fw.word,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textColor
                                        )
                                        Text(
                                            text = if (fw.isValid) {
                                                fw.direction.label
                                            } else {
                                                "${fw.direction.label} • Hold to allow"
                                            },
                                            fontSize = 10.sp,
                                            color = textColor.copy(alpha = 0.8f)
                                        )
                                    }
                                    Text(
                                        text = if (fw.isValid) "+${fw.awardedPoints}" else "0",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = textColor
                                    )
                                }
                            }
                        }

                        if (tallyResult.isolatedCells.isNotEmpty()) {
                            item(key = "isolated_cells_notice") {
                                Text(
                                    text = "${tallyResult.isolatedCells.size} isolated tile(s) (0 pts)",
                                    fontSize = 11.sp,
                                    color = WordsWorthColors.InvalidCellBorder,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Right-hand options column next to the crossword board containing:
 * 1. Board Size selector buttons (`6x6`, `9x9`, `12x12`).
 * 2. Board Pattern selector buttons (`All-blank`, `Random pattern`) with mini board thumbnails.
 * 3. `Time Attack` mode button spaced one button's height below `Random pattern`.
 */
@Composable
fun BoardOptionsColumn(
    boardSize: Int,
    onChangeBoardSize: (Int) -> Unit,
    isRandomPatternMode: Boolean,
    allBlankThumb: ImageBitmap,
    randomPatternThumb: ImageBitmap,
    onSelectAllBlank: () -> Unit,
    onSelectRandomPattern: () -> Unit,
    timeAttackMinutes: Int?,
    onClickTimeAttack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        WordsWorthLogic.BOARD_SIZES.forEach { sizeOption ->
            val isSelected = boardSize == sizeOption
            if (isSelected) {
                Button(
                    onClick = { onChangeBoardSize(sizeOption) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "${sizeOption}x${sizeOption}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onChangeBoardSize(sizeOption) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "${sizeOption}x${sizeOption}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(
            color = WordsWorthColors.WarmDivider,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Option 1: All-blank board
        PatternOptionButton(
            label = "All-blank",
            thumbnail = allBlankThumb,
            isSelected = !isRandomPatternMode,
            onClick = onSelectAllBlank
        )

        // Option 2: Random pattern board
        PatternOptionButton(
            label = "Random\npattern",
            thumbnail = randomPatternThumb,
            isSelected = isRandomPatternMode,
            onClick = onSelectRandomPattern
        )

        // Leave one button's worth of space between "Random pattern" and "Time Attack"
        Spacer(modifier = Modifier.height(50.dp))

        val isTimeAttackActive = timeAttackMinutes != null
        val timeAttackLabel = if (timeAttackMinutes != null) {
            "Time Attack\n(${timeAttackMinutes} min${if (timeAttackMinutes > 1) "s" else ""})"
        } else {
            "Time Attack"
        }
        if (isTimeAttackActive) {
            Button(
                onClick = onClickTimeAttack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WordsWorthColors.TimeAttackActiveTeal
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = timeAttackLabel,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            OutlinedButton(
                onClick = onClickTimeAttack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = timeAttackLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Selectable board pattern button ("All-blank" or "Random pattern") featuring a 46x46dp
 * mini board thumbnail alongside its label.
 */
@Composable
fun PatternOptionButton(
    label: String,
    thumbnail: ImageBitmap,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val currentOnClick by rememberUpdatedState(onClick)

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var lastPos = down.position
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            lastPos = it.position
                            it.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    val slopPx = 24.dp.toPx()
                    if (lastPos.x in -slopPx..(size.width + slopPx) &&
                        lastPos.y in -slopPx..(size.height + slopPx)
                    ) {
                        currentOnClick()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                bitmap = thumbnail,
                contentDescription = label,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor,
                lineHeight = 15.sp
            )
        }
    }
}
