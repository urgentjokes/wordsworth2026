package com.example.wordsworth.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.FormedWord
import com.example.wordsworth.SavedGameEntry
import com.example.wordsworth.WordsWorthGameState
import com.example.wordsworth.WordsWorthLogic
import com.example.wordsworth.ui.theme.WordsWorthColors

/**
 * Renders whichever modal confirmation or selection dialog is currently active in [gameState].
 */
@Composable
fun WordsWorthDialogs(gameState: WordsWorthGameState) {
    if (gameState.showTimeAttackDialog) {
        TimeAttackDialog(
            currentTimeAttackMinutes = gameState.timeAttackMinutes,
            onSelectMinutes = { mins ->
                gameState.showTimeAttackDialog = false
                gameState.timeAttackMinutes = mins
                gameState.resetBoardAndTimer()
            },
            onSwitchToStandard = {
                gameState.showTimeAttackDialog = false
                gameState.timeAttackMinutes = null
                gameState.resetBoardAndTimer()
            },
            onDismiss = { gameState.showTimeAttackDialog = false }
        )
    }

    gameState.pendingWhitelistWord?.let { formedWord ->
        WhitelistWordDialog(
            formedWord = formedWord,
            onConfirmWhitelist = { gameState.confirmWhitelistWord(formedWord) },
            onDismiss = { gameState.pendingWhitelistWord = null }
        )
    }

    gameState.pendingPatternSwitch?.let { switchToRandom ->
        PatternSwitchConfirmDialog(
            switchToRandom = switchToRandom,
            onConfirmStartOver = {
                gameState.pendingPatternSwitch = null
                if (switchToRandom) {
                    gameState.applyRandomPattern()
                } else {
                    gameState.applyAllBlank()
                }
            },
            onDismiss = { gameState.pendingPatternSwitch = null }
        )
    }

    if (gameState.showQuitDialog) {
        val previewScore = remember(
            gameState.board,
            gameState.dictionary,
            gameState.scoringMode,
            gameState.validatedWordsHistory.size
        ) {
            if (gameState.isDictionaryLoaded) {
                WordsWorthLogic.tallyBoard(
                    board = gameState.board,
                    dictionary = gameState.dictionary,
                    scoringMode = gameState.scoringMode,
                    previouslyValidatedWords = gameState.validatedWordsHistory
                ).totalScore
            } else {
                0
            }
        }
        FinishGameQuitDialog(
            previewScore = previewScore,
            elapsedSeconds = gameState.elapsedSeconds,
            onSaveAndQuit = { gameState.saveAndFinishCurrentGame() },
            onQuitWithoutSaving = {
                gameState.showQuitDialog = false
                gameState.resetBoardAndTimer()
            },
            onDismiss = { gameState.showQuitDialog = false }
        )
    }

    gameState.pendingDeleteEntry?.let { entry ->
        DeleteSavedGameDialog(
            entry = entry,
            onConfirmDelete = { gameState.confirmDeleteSavedGame(entry) },
            onDismiss = { gameState.pendingDeleteEntry = null }
        )
    }

    gameState.previewSavedEntry?.let { entry ->
        SavedGamePreviewDialog(
            entry = entry,
            onDismiss = { gameState.previewSavedEntry = null }
        )
    }
}

/**
 * Dialog allowing the player to start a **1 min**, **3 mins**, or **5 mins** Time Attack game,
 * or return to untimed Standard Mode if Time Attack is currently active.
 */
@Composable
fun TimeAttackDialog(
    currentTimeAttackMinutes: Int?,
    onSelectMinutes: (Int) -> Unit,
    onSwitchToStandard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Time Attack Mode",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Choose a countdown duration to start a new Time Attack game:"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "1 min", 3 to "3 mins", 5 to "5 mins").forEach { (mins, label) ->
                        val isSelected = currentTimeAttackMinutes == mins
                        Button(
                            onClick = { onSelectMinutes(mins) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) {
                                    WordsWorthColors.TimeAttackActiveTeal
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                if (currentTimeAttackMinutes != null) {
                    OutlinedButton(
                        onClick = onSwitchToStandard,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Switch to Standard Mode (No Limit)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Confirmation dialog triggered when the player long-presses an invalid (red) word in the
 * Words list to add it to their custom whitelist.
 */
@Composable
fun WhitelistWordDialog(
    formedWord: FormedWord,
    onConfirmWhitelist: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Whitelist \"${formedWord.word}\"?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Add \"${formedWord.word}\" (${formedWord.rawPoints} pts) to your whitelist so it is accepted as a valid word?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmWhitelist,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Whitelist", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Confirmation dialog shown when switching between "All-blank" and "Random pattern"
 * while a game is already in progress.
 */
@Composable
fun PatternSwitchConfirmDialog(
    switchToRandom: Boolean,
    onConfirmStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Start Over?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val modeName = if (switchToRandom) "a random-pattern" else "an all-blank"
            Text(
                text = "A game is currently in progress. Do you want to start over with $modeName board? This will clear the board and reset the current game."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmStartOver,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Yes, start over", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("No, resume game", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Quit confirmation dialog allowing the player to either save their final board and score
 * to the "Previous Games" column or quit without saving.
 */
@Composable
fun FinishGameQuitDialog(
    previewScore: Int,
    elapsedSeconds: Int,
    onSaveAndQuit: () -> Unit,
    onQuitWithoutSaving: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Finish Current Game",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Current board score: $previewScore pts (Time: ${formatTimer(elapsedSeconds)}).\n\n" +
                    "How would you like to quit this round?"
            )
        },
        confirmButton = {
            Button(
                onClick = onSaveAndQuit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Save final board and score", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onQuitWithoutSaving,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Quit without saving", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Confirmation dialog shown when the player long-presses a saved game entry in the
 * "Previous Games" column to delete it.
 */
@Composable
fun DeleteSavedGameDialog(
    entry: SavedGameEntry,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Saved Game?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete this saved game (${entry.score} pts, ${formatTimer(entry.elapsedSeconds)})?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Fallback modal preview dialog for legacy saved game entries that only have a screenshot bitmap.
 */
@Composable
fun SavedGamePreviewDialog(
    entry: SavedGameEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Saved Board — ${entry.score} pts",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                entry.bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Saved board screenshot",
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Time: ${formatTimer(entry.elapsedSeconds)}  •  Saved: ${entry.timestamp}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
