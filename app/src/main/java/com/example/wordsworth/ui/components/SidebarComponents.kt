package com.example.wordsworth.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    elapsedSecondsProvider: () -> Int,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
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
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isCompact) 10.dp else 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompact) 2.dp else 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isCompact) 6.dp else 12.dp,
                    vertical = if (isCompact) 6.dp else 10.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = labelText,
                fontSize = if (isCompact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                color = contentColor.copy(alpha = 0.85f)
            )
            Text(
                text = formatTimer(displaySeconds),
                fontSize = if (isCompact) 20.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                color = contentColor
            )
        }
    }
}

/**
 * Compact horizontal status bar used on phones and narrow screens, placing **High Score**,
 * **Score**, and **Timer / Time Attack** side-by-side in a single row above the board.
 */
@Composable
fun CompactScoreAndTimerRow(
    highScoreEntry: SavedGameEntry?,
    onClickHighScore: (SavedGameEntry) -> Unit,
    tallyResult: TallyResult?,
    elapsedSecondsProvider: () -> Int,
    timeAttackMinutes: Int?,
    isBoardLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val highScore = highScoreEntry?.score ?: 0
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High Score Card
        Card(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = highScoreEntry != null) {
                    highScoreEntry?.let(onClickHighScore)
                },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HIGH SCORE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$highScore",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Text(
                        text = "pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }

        // Current Score Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SCORE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.85f)
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${tallyResult?.totalScore ?: 0}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Text(
                        text = "pts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }

        // Timer / Time Attack Card
        TimerCard(
            timeAttackMinutes = timeAttackMinutes,
            isBoardLocked = isBoardLocked,
            elapsedSecondsProvider = elapsedSecondsProvider,
            isCompact = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Displays a list of saved game board thumbnails, their scores, and elapsed times.
 *
 * - On tablets (`inScrollableParent == false`), occupies the left-hand column and scrolls
 *   internally with a [LazyColumn] that automatically jumps to the top when a game is saved.
 * - On phones (`inScrollableParent == true`), renders inline within the outer page scroll
 *   using compact thumbnails.
 * - Tapping a saved game resumes it on the active board; long-pressing prompts to delete it.
 */
@Composable
fun PreviousGamesColumn(
    savedGames: List<SavedGameEntry>,
    onClickSavedGame: (SavedGameEntry) -> Unit,
    onDeleteSavedGame: (SavedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSize: androidx.compose.ui.unit.Dp = 208.dp,
    inScrollableParent: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = (if (inScrollableParent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
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
                    fontSize = if (inScrollableParent) 16.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Hold entry to delete",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                color = WordsWorthColors.WarmDivider,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (savedGames.isEmpty()) {
                Box(
                    modifier = (if (inScrollableParent) Modifier.fillMaxWidth().padding(vertical = 16.dp) else Modifier.fillMaxSize())
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved boards yet.\n\nTap Quit → Save final board and score.",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (inScrollableParent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedGames.forEach { entry ->
                        SavedBoardThumbnailItem(
                            entry = entry,
                            thumbnailSize = thumbnailSize,
                            onClick = { onClickSavedGame(entry) },
                            onLongClick = { onDeleteSavedGame(entry) }
                        )
                    }
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
                            thumbnailSize = thumbnailSize,
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
 * Individual card in the "Previous Games" list showing a mini-screenshot of the saved board,
 * its score in points, and the elapsed timer value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedBoardThumbnailItem(
    entry: SavedGameEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    thumbnailSize: androidx.compose.ui.unit.Dp = 208.dp
) {
    val isCompactThumb = thumbnailSize < 140.dp
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
                .padding(if (isCompactThumb) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompactThumb) 12.dp else 16.dp)
        ) {
            if (entry.bitmap != null) {
                Image(
                    bitmap = entry.bitmap.asImageBitmap(),
                    contentDescription = "Mini board screenshot",
                    modifier = Modifier
                        .size(thumbnailSize)
                        .clip(RoundedCornerShape(if (isCompactThumb) 8.dp else 10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(thumbnailSize)
                        .clip(RoundedCornerShape(if (isCompactThumb) 8.dp else 10.dp))
                        .background(WordsWorthColors.BoardFrameBackground),
                    contentAlignment = Alignment.Center
                ) {
                    val sz = entry.board?.size ?: 6
                    Text(
                        text = "${sz}×${sz}",
                        fontSize = if (isCompactThumb) 16.sp else 22.sp,
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
                    fontSize = if (isCompactThumb) 26.sp else 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "pts",
                    fontSize = if (isCompactThumb) 13.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(if (isCompactThumb) 4.dp else 6.dp))
                Text(
                    text = "⏱ ${formatTimer(entry.elapsedSeconds)}",
                    fontSize = if (isCompactThumb) 12.sp else 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Card displaying all formed words on the current board (valid in green, invalid in red)
 * along with their point values. Long-pressing an invalid word opens the whitelist dialog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordsListCard(
    tallyResult: TallyResult?,
    onRequestWhitelistWord: (FormedWord) -> Unit,
    modifier: Modifier = Modifier,
    inScrollableParent: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = (if (inScrollableParent) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
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
                    modifier = (if (inScrollableParent) Modifier.fillMaxWidth().padding(vertical = 14.dp) else Modifier.fillMaxSize())
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
                    modifier = (if (inScrollableParent) Modifier.fillMaxWidth().padding(vertical = 14.dp) else Modifier.fillMaxSize())
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
            } else if (inScrollableParent) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tallyResult.words.forEach { fw ->
                        FormedWordItemRow(
                            fw = fw,
                            onRequestWhitelistWord = onRequestWhitelistWord
                        )
                    }
                    if (tallyResult.isolatedCells.isNotEmpty()) {
                        Text(
                            text = "${tallyResult.isolatedCells.size} isolated tile(s) (0 pts)",
                            fontSize = 11.sp,
                            color = WordsWorthColors.InvalidCellBorder,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
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
                        FormedWordItemRow(
                            fw = fw,
                            onRequestWhitelistWord = onRequestWhitelistWord
                        )
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

/**
 * Single word row inside [WordsListCard].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FormedWordItemRow(
    fw: FormedWord,
    onRequestWhitelistWord: (FormedWord) -> Unit
) {
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
                    text = when {
                        !fw.isValid -> "${fw.direction.label} • Hold to allow"
                        fw.isDuplicateUnscored -> "${fw.direction.label} • Duplicate (0 pts)"
                        else -> fw.direction.label
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

/**
 * Column to the immediate left of the crossword board on tablet/wide screens displaying:
 * 1. **High Score** card (tracks the highest-scoring saved game; tapping resumes/displays that game).
 * 2. **Score** card (current board's validated point total).
 * 3. **Timer / Time Attack** card ([TimerCard]).
 * 4. **Words** card ([WordsListCard]).
 */
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
        WordsListCard(
            tallyResult = tallyResult,
            onRequestWhitelistWord = onRequestWhitelistWord,
            modifier = Modifier.weight(1f),
            inScrollableParent = false
        )
    }
}

/**
 * Options column containing:
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
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val verticalGap = if (isCompact) 6.dp else 8.dp
    val buttonVerticalPad = if (isCompact) 7.dp else 10.dp
    val buttonFontSize = if (isCompact) 13.sp else 15.sp

    Column(
        verticalArrangement = Arrangement.spacedBy(verticalGap),
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = buttonVerticalPad)
                ) {
                    Text(
                        text = "${sizeOption}x${sizeOption}",
                        fontSize = buttonFontSize,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onChangeBoardSize(sizeOption) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = buttonVerticalPad)
                ) {
                    Text(
                        text = "${sizeOption}x${sizeOption}",
                        fontSize = buttonFontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(
            color = WordsWorthColors.WarmDivider,
            modifier = Modifier.padding(vertical = if (isCompact) 2.dp else 4.dp)
        )

        // Option 1: All-blank board
        PatternOptionButton(
            label = "All-blank",
            thumbnail = allBlankThumb,
            isSelected = !isRandomPatternMode,
            onClick = onSelectAllBlank,
            isCompact = isCompact
        )

        // Option 2: Random pattern board
        PatternOptionButton(
            label = "Random\npattern",
            thumbnail = randomPatternThumb,
            isSelected = isRandomPatternMode,
            onClick = onSelectRandomPattern,
            isCompact = isCompact
        )

        // Leave one button's worth of space between "Random pattern" and "Time Attack"
        Spacer(modifier = Modifier.height(if (isCompact) 36.dp else 50.dp))

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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = buttonVerticalPad)
            ) {
                Text(
                    text = timeAttackLabel,
                    fontSize = if (isCompact) 12.sp else 14.sp,
                    lineHeight = if (isCompact) 14.sp else 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            OutlinedButton(
                onClick = onClickTimeAttack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = buttonVerticalPad)
            ) {
                Text(
                    text = timeAttackLabel,
                    fontSize = if (isCompact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Tab content for the "Modes" tab containing:
 * 1. Grid-size options (6x6, 9x9, 12x12).
 * 2. Board Pattern options ("All-blank", "Random pattern").
 * 3. Time Attack mode options.
 */
@Composable
fun ModesTabContent(
    boardSize: Int,
    onChangeBoardSize: (Int) -> Unit,
    isRandomPatternMode: Boolean,
    allBlankThumb: ImageBitmap,
    randomPatternThumb: ImageBitmap,
    onSelectAllBlank: () -> Unit,
    onSelectRandomPattern: () -> Unit,
    timeAttackMinutes: Int?,
    onClickTimeAttack: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Grid Size Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Grid Size",
                    fontSize = if (isCompact) 16.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Choose crossword board dimensions",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = WordsWorthColors.WarmDivider,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WordsWorthLogic.BOARD_SIZES.forEach { sizeOption ->
                        val isSelected = boardSize == sizeOption
                        if (isSelected) {
                            Button(
                                onClick = { onChangeBoardSize(sizeOption) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${sizeOption}×${sizeOption}",
                                    fontSize = if (isCompact) 14.sp else 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onChangeBoardSize(sizeOption) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Text(
                                    text = "${sizeOption}×${sizeOption}",
                                    fontSize = if (isCompact) 14.sp else 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Board Pattern Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Board Pattern",
                    fontSize = if (isCompact) 16.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Choose starting grid layout",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = WordsWorthColors.WarmDivider,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatternOptionButton(
                        label = "All-blank",
                        thumbnail = allBlankThumb,
                        isSelected = !isRandomPatternMode,
                        onClick = onSelectAllBlank,
                        isCompact = isCompact
                    )
                    PatternOptionButton(
                        label = "Random pattern",
                        thumbnail = randomPatternThumb,
                        isSelected = isRandomPatternMode,
                        onClick = onSelectRandomPattern,
                        isCompact = isCompact
                    )
                }
            }
        }

        // 3. Time Attack Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Time Attack",
                    fontSize = if (isCompact) 16.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (timeAttackMinutes != null) {
                        "Active countdown: $timeAttackMinutes minute${if (timeAttackMinutes > 1) "s" else ""}"
                    } else {
                        "Untimed: Standard untimed play"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    color = WordsWorthColors.WarmDivider,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                val isTimeAttackActive = timeAttackMinutes != null
                val timeAttackLabel = if (timeAttackMinutes != null) {
                    "Time Attack Active (${timeAttackMinutes} min${if (timeAttackMinutes > 1) "s" else ""}) — Change"
                } else {
                    "Start Time Attack Mode"
                }
                if (isTimeAttackActive) {
                    Button(
                        onClick = onClickTimeAttack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WordsWorthColors.TimeAttackActiveTeal
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = timeAttackLabel,
                            fontSize = if (isCompact) 14.sp else 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onClickTimeAttack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = timeAttackLabel,
                            fontSize = if (isCompact) 14.sp else 15.sp,
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

/**
 * Selectable board pattern button ("All-blank" or "Random pattern") featuring a
 * mini board thumbnail alongside its label.
 */
@Composable
fun PatternOptionButton(
    label: String,
    thumbnail: ImageBitmap,
    isSelected: Boolean,
    onClick: () -> Unit,
    isCompact: Boolean = false
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val currentOnClick by rememberUpdatedState(onClick)
    val thumbSize = if (isCompact) 36.dp else 46.dp

    Surface(
        onClick = onClick,
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = if (isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 6.dp else 8.dp, vertical = if (isCompact) 5.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp)
        ) {
            Image(
                bitmap = thumbnail,
                contentDescription = label,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = label,
                fontSize = if (isCompact) 12.sp else 13.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor,
                lineHeight = if (isCompact) 14.sp else 15.sp
            )
        }
    }
}
