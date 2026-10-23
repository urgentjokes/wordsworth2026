package com.example.wordsworth.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.FormedWord
import com.example.wordsworth.KeyboardLayout
import com.example.wordsworth.SavedGameEntry
import com.example.wordsworth.SavedGamesManager
import com.example.wordsworth.TallyResult
import com.example.wordsworth.WordDirection
import com.example.wordsworth.WordsWorthLogic
import com.example.wordsworth.ui.components.BoardOptionsColumn
import com.example.wordsworth.ui.components.CrosswordBoardGrid
import com.example.wordsworth.ui.components.FloatingDraggedTile
import com.example.wordsworth.ui.components.PreviousGamesColumn
import com.example.wordsworth.ui.components.ScoreAndWordsColumn
import com.example.wordsworth.ui.components.TileKeyboardRack
import com.example.wordsworth.ui.theme.WordsWorthColors

/**
 * Main gameplay screen of WordsWorth.
 *
 * Layout structure:
 * - **Top Bar**: App title ("WordsWorth") on the left; `Example`, `Clear`, and Settings gear icon on the right.
 * - **Left Column** ([PreviousGamesColumn]): Running history of saved game mini-screenshots + scores.
 * - **Right Area**:
 *   1. **Left of Board** ([ScoreAndWordsColumn]): High Score, Score, Timer/Time Attack card, and scrollable Words list.
 *   2. **Center**: Interactive NxN crossword board ([CrosswordBoardGrid]), direction tip banner,
 *      on-screen tile keyboard ([TileKeyboardRack]), and `Validate` / `Quit` buttons.
 *   3. **Right of Board** ([BoardOptionsColumn]): Board size options (`6x6`, `9x9`, `12x12`),
 *      board pattern options (`All-blank`, `Random pattern`), and the `Time Attack` button.
 */
@Composable
fun WordsWorthGameScreen(
    board: Array<Array<Char?>>,
    boardSize: Int,
    onChangeBoardSize: (Int) -> Unit,
    isRandomPatternMode: Boolean,
    onSelectAllBlank: () -> Unit,
    onSelectRandomPattern: () -> Unit,
    timeAttackMinutes: Int?,
    isBoardLocked: Boolean,
    onClickTimeAttack: () -> Unit,
    keyboardLayout: KeyboardLayout,
    focusedCell: Pair<Int, Int>?,
    focusDirection: WordDirection,
    onBoardCellTap: (Int, Int) -> Unit,
    selectedLetter: Char?,
    onKeyboardInput: (Char?) -> Pair<Pair<Int, Int>, Char?>?,
    onDropLetterOnCell: (Int, Int, Char, Pair<Pair<Int, Int>, Char?>?) -> Unit,
    onUpdateCell: (Int, Int, Char?) -> Unit,
    onClearBoard: () -> Unit,
    onLoadExample: () -> Unit,
    elapsedSecondsProvider: () -> Int,
    isTimerRunning: Boolean,
    isDictionaryLoaded: Boolean,
    tallyResult: TallyResult?,
    onValidate: () -> Unit,
    onRequestWhitelistWord: (FormedWord) -> Unit,
    onQuitClick: () -> Unit,
    onOpenSettings: () -> Unit,
    savedGames: List<SavedGameEntry>,
    onClickSavedGame: (SavedGameEntry) -> Unit,
    onDeleteSavedGame: (SavedGameEntry) -> Unit
) {
    // Cached thumbnails for the "All-blank" and "Random pattern" option buttons
    val allBlankThumb = remember {
        SavedGamesManager.createBoardScreenshot(
            WordsWorthLogic.createBlankBoard(6),
            emptyMap()
        ).asImageBitmap()
    }
    val randomPatternThumb = remember {
        SavedGamesManager.createBoardScreenshot(
            WordsWorthLogic.samplePatternThumbnailBoard(),
            emptyMap()
        ).asImageBitmap()
    }

    // Drag-and-drop coordinate tracking in root window space
    var gridBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var keyboardOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var draggingLetter by remember { mutableStateOf<Char?>(null) }
    var dragPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val currentBoardSize by rememberUpdatedState(boardSize)

    /**
     * Maps a root-window touch position [pos] to the corresponding `(row, col)` cell on the board,
     * or returns `null` if [pos] lies outside the board bounds.
     */
    fun cellAtRootPosition(pos: Offset): Pair<Int, Int>? {
        val bounds = gridBoundsInRoot ?: return null
        if (!bounds.contains(pos)) return null
        val relX = pos.x - bounds.left
        val relY = pos.y - bounds.top
        val size = currentBoardSize
        val col = ((relX / bounds.width.coerceAtLeast(1f)) * size).toInt()
            .coerceIn(0, size - 1)
        val row = ((relY / bounds.height.coerceAtLeast(1f)) * size).toInt()
            .coerceIn(0, size - 1)
        return row to col
    }

    val hoveredCell: Pair<Int, Int>? = if (draggingLetter != null) {
        cellAtRootPosition(dragPositionInRoot)
    } else {
        null
    }

    val density = LocalDensity.current
    val dragThresholdPx = with(density) { 10.dp.toPx() }
    val floatingTileSizePx = with(density) { 50.dp.toPx() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // Top Bar: App Title, Example/Clear actions, and Settings Gear Icon
                WordsWorthTopBar(
                    onLoadExample = onLoadExample,
                    onClearBoard = onClearBoard,
                    onOpenSettings = onOpenSettings
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Main Content: Left-hand Saved Games column + Right-hand gameplay area
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // LEFT COLUMN: Running list of saved board mini-screenshots + scores
                    PreviousGamesColumn(
                        savedGames = savedGames,
                        onClickSavedGame = onClickSavedGame,
                        onDeleteSavedGame = onDeleteSavedGame
                    )

                    // RIGHT AREA: Score/Words column, Center Board + Keyboard, Right Board Options column
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        val cellSpacing = when {
                            boardSize >= 12 -> 2.dp
                            boardSize >= 9 -> 3.dp
                            else -> 4.dp
                        }
                        val boardPadding = when {
                            boardSize >= 12 -> 5.dp
                            boardSize >= 9 -> 6.dp
                            else -> 7.dp
                        }

                        // 1. Score, High Score, Timer, and Validated Words column
                        val highScoreEntry = savedGames.maxByOrNull { it.score }
                        ScoreAndWordsColumn(
                            highScoreEntry = highScoreEntry,
                            onClickHighScore = onClickSavedGame,
                            tallyResult = tallyResult,
                            elapsedSecondsProvider = elapsedSecondsProvider,
                            timeAttackMinutes = timeAttackMinutes,
                            isBoardLocked = isBoardLocked,
                            onRequestWhitelistWord = onRequestWhitelistWord,
                            modifier = Modifier
                                .width(172.dp)
                                .fillMaxHeight()
                                .padding(top = boardPadding)
                        )

                        // 2. Center column: Crossword Board, Direction Tip, Keyboard Rack, Validate & Quit buttons
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 507.dp)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CrosswordBoardGrid(
                                board = board,
                                boardSize = boardSize,
                                focusedCell = focusedCell,
                                focusDirection = focusDirection,
                                hoveredCell = hoveredCell,
                                tallyResult = tallyResult,
                                boardPadding = boardPadding,
                                cellSpacing = cellSpacing,
                                onGridBoundsChanged = { gridBoundsInRoot = it },
                                onBoardCellTap = onBoardCellTap,
                                onClearCell = { r, c -> onUpdateCell(r, c, null) }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (isBoardLocked) {
                                    "Time's up! Board locked — tap Quit to save or Clear to play again."
                                } else {
                                    "Tip: Tap the arrow to toggle direction."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBoardLocked) {
                                    WordsWorthColors.InvalidWordText
                                } else {
                                    WordsWorthColors.FocusedCursorBlue
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            TileKeyboardRack(
                                keyboardLayout = keyboardLayout,
                                selectedLetter = selectedLetter,
                                isBoardLocked = isBoardLocked,
                                dragThresholdPx = dragThresholdPx,
                                onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                onKeyboardInput = onKeyboardInput,
                                onDragStart = { letter, localPos ->
                                    draggingLetter = letter
                                    dragPositionInRoot = keyboardOriginInRoot + localPos
                                },
                                onDragMove = { localPos ->
                                    dragPositionInRoot = keyboardOriginInRoot + localPos
                                },
                                onDragEnd = { letter, localPos, undoPlacement ->
                                    val target = cellAtRootPosition(keyboardOriginInRoot + localPos)
                                    if (target != null) {
                                        onDropLetterOnCell(
                                            target.first,
                                            target.second,
                                            letter,
                                            undoPlacement
                                        )
                                    }
                                    draggingLetter = null
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action Buttons: "Validate" and "Quit"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onValidate,
                                    enabled = isDictionaryLoaded,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text(
                                        text = "Validate",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = onQuitClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(
                                        text = "Quit",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 3. Right column: Board Size, Pattern Options, and Time Attack button
                        BoardOptionsColumn(
                            boardSize = boardSize,
                            onChangeBoardSize = onChangeBoardSize,
                            isRandomPatternMode = isRandomPatternMode,
                            allBlankThumb = allBlankThumb,
                            randomPatternThumb = randomPatternThumb,
                            onSelectAllBlank = onSelectAllBlank,
                            onSelectRandomPattern = onSelectRandomPattern,
                            timeAttackMinutes = timeAttackMinutes,
                            onClickTimeAttack = onClickTimeAttack,
                            modifier = Modifier
                                .width(156.dp)
                                .padding(top = boardPadding)
                        )
                    }
                }
            }

            // Floating tile preview rendered when dragging a letter from the keyboard onto the board
            draggingLetter?.let { ch ->
                FloatingDraggedTile(
                    letter = ch,
                    dragPositionInRoot = dragPositionInRoot,
                    tileSizePx = floatingTileSizePx
                )
            }
        }
    }
}

/**
 * Top header bar displaying the "WordsWorth" title on the left and quick actions
 * (`Example`, `Clear`, and the `Settings` gear button) on the right.
 */
@Composable
private fun WordsWorthTopBar(
    onLoadExample: () -> Unit,
    onClearBoard: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "WordsWorth",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = onLoadExample,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Example", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onClearBoard,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Clear", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
