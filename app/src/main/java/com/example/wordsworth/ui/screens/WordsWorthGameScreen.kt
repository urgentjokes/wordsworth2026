package com.example.wordsworth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.wordsworth.FormedWord
import com.example.wordsworth.KeyboardLayout
import com.example.wordsworth.SavedGameEntry
import com.example.wordsworth.SavedGamesManager
import com.example.wordsworth.TallyResult
import com.example.wordsworth.WordDirection
import com.example.wordsworth.WordsWorthLogic
import com.example.wordsworth.ui.components.BoardOptionsColumn
import com.example.wordsworth.ui.components.CompactScoreAndTimerRow
import com.example.wordsworth.ui.components.CrosswordBoardGrid
import com.example.wordsworth.ui.components.FloatingDraggedTile
import com.example.wordsworth.ui.components.PreviousGamesColumn
import com.example.wordsworth.ui.components.ScoreAndWordsColumn
import com.example.wordsworth.ui.components.TileKeyboardRack
import com.example.wordsworth.ui.components.WordsListCard
import com.example.wordsworth.ui.theme.WordsWorthColors

/**
 * Main gameplay screen of WordsWorth with a responsive layout that adapts to:
 * - **Compact Portrait Phones (`maxWidth < 600.dp`)**:
 *   Single-column scrollable layout with a compact top bar, horizontal 3-card Score/High Score/Timer
 *   bar ([CompactScoreAndTimerRow]), height-aware [CrosswordBoardGrid] and [TileKeyboardRack] so both
 *   board and keyboard fit on screen at once, `Validate`/`Quit` buttons, a side-by-side row for
 *   [WordsListCard] and [BoardOptionsColumn], and [PreviousGamesColumn] below.
 * - **Landscape Phones (`maxWidth >= 600.dp && maxHeight < 520.dp`)**:
 *   Side-by-side layout placing the [CrosswordBoardGrid] on the left, the score row + keyboard +
 *   actions + words/history in the center, and [BoardOptionsColumn] on the right.
 * - **Medium Tablets (`600.dp <= maxWidth < 960.dp && maxHeight >= 520.dp`)**:
 *   Three-column gameplay area ([ScoreAndWordsColumn], center board + keyboard, [BoardOptionsColumn])
 *   with [PreviousGamesColumn] stacked underneath so the board never gets squeezed.
 * - **Large Tablets / Desktop (`maxWidth >= 960.dp && maxHeight >= 520.dp`)**:
 *   Full 4-column layout ([PreviousGamesColumn], [ScoreAndWordsColumn], center board + keyboard,
 *   and [BoardOptionsColumn]).
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
    onDeleteSavedGame: (SavedGameEntry) -> Unit,
    pendingRomaji: String = "",
    onKeyAction: (String) -> Unit = {}
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
    val highScoreEntry = savedGames.maxByOrNull { it.score }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val isCompactWidth = screenWidth < 600.dp
            val isShortLandscape = screenWidth >= 600.dp && screenHeight < 520.dp
            val isCompactBoard = isCompactWidth || isShortLandscape

            val cellSpacing = when {
                isCompactBoard && boardSize >= 12 -> 1.5.dp
                isCompactBoard && boardSize >= 9 -> 2.dp
                isCompactBoard -> 3.dp
                boardSize >= 12 -> 2.dp
                boardSize >= 9 -> 3.dp
                else -> 4.dp
            }
            val boardPadding = when {
                isCompactBoard && boardSize >= 12 -> 3.dp
                isCompactBoard && boardSize >= 9 -> 4.dp
                isCompactBoard -> 5.dp
                boardSize >= 12 -> 5.dp
                boardSize >= 9 -> 6.dp
                else -> 7.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isCompactBoard) 8.dp else 10.dp,
                        vertical = if (isCompactBoard) 4.dp else 6.dp
                    )
            ) {
                // Top Bar: App Title, Example/Clear actions, and Settings Gear Icon
                WordsWorthTopBar(
                    onLoadExample = onLoadExample,
                    onClearBoard = onClearBoard,
                    onOpenSettings = onOpenSettings,
                    isCompact = isCompactBoard
                )

                Spacer(modifier = Modifier.height(if (isCompactBoard) 2.dp else 4.dp))

                when {
                    // =========================================================================
                    // 1. PHONE PORTRAIT / COMPACT WIDTH (< 600.dp)
                    // =========================================================================
                    isCompactWidth -> {
                        // Size the board so the Top Bar + Score Row + Board + Keyboard + Validate/Quit
                        // all fit comfortably on screen without needing to scroll during active play.
                        val maxBoardHeight = (screenHeight - 295.dp).coerceIn(210.dp, 370.dp)
                        val boardSideDp = min(screenWidth - 16.dp, maxBoardHeight)

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Compact 3-card row: High Score | Score | Timer / Time Attack
                            CompactScoreAndTimerRow(
                                highScoreEntry = highScoreEntry,
                                onClickHighScore = onClickSavedGame,
                                tallyResult = tallyResult,
                                elapsedSecondsProvider = elapsedSecondsProvider,
                                timeAttackMinutes = timeAttackMinutes,
                                isBoardLocked = isBoardLocked
                            )

                            // Square Crossword Board
                            Box(
                                modifier = Modifier.size(boardSideDp),
                                contentAlignment = Alignment.Center
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
                                    onClearCell = { r, c -> onUpdateCell(r, c, null) },
                                    isCompact = true,
                                    pendingRomaji = pendingRomaji
                                )
                            }

                            // Direction / Locked Tip
                            Text(
                                text = if (isBoardLocked) {
                                    "Time's up! Board locked — tap Quit to save or Clear to play again."
                                } else {
                                    "Tip: Tap the arrow to toggle direction."
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBoardLocked) {
                                    WordsWorthColors.InvalidWordText
                                } else {
                                    WordsWorthColors.FocusedCursorBlue
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Compact On-Screen Tile Keyboard
                            TileKeyboardRack(
                                keyboardLayout = keyboardLayout,
                                selectedLetter = selectedLetter,
                                isBoardLocked = isBoardLocked,
                                dragThresholdPx = dragThresholdPx,
                                onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                onKeyboardInput = onKeyboardInput,
                                onKeyAction = onKeyAction,
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
                                },
                                isCompact = true
                            )

                            // Validate & Quit Action Buttons
                            ValidateAndQuitRow(
                                isDictionaryLoaded = isDictionaryLoaded,
                                onValidate = onValidate,
                                onQuitClick = onQuitClick,
                                isCompact = true
                            )

                            // Side-by-side Words Card (left) + Board Options Column (right)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                WordsListCard(
                                    tallyResult = tallyResult,
                                    onRequestWhitelistWord = onRequestWhitelistWord,
                                    modifier = Modifier.weight(1f),
                                    inScrollableParent = true
                                )

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
                                    isCompact = true,
                                    modifier = Modifier.width(136.dp)
                                )
                            }

                            // Previous Games History Card (full width at bottom)
                            PreviousGamesColumn(
                                savedGames = savedGames,
                                onClickSavedGame = onClickSavedGame,
                                onDeleteSavedGame = onDeleteSavedGame,
                                thumbnailSize = 96.dp,
                                inScrollableParent = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                        }
                    }

                    // =========================================================================
                    // 2. PHONE LANDSCAPE / SHORT HEIGHT (maxWidth >= 600.dp && maxHeight < 520.dp)
                    // =========================================================================
                    isShortLandscape -> {
                        val boardSideDp = (screenHeight - 52.dp).coerceIn(190.dp, 360.dp)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left: Square Board fitted to landscape height
                            Box(
                                modifier = Modifier.size(boardSideDp),
                                contentAlignment = Alignment.Center
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
                                    onClearCell = { r, c -> onUpdateCell(r, c, null) },
                                    isCompact = true,
                                    pendingRomaji = pendingRomaji
                                )
                            }

                            // Center: Score Row + Keyboard + Validate/Quit + Words + Previous Games
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CompactScoreAndTimerRow(
                                    highScoreEntry = highScoreEntry,
                                    onClickHighScore = onClickSavedGame,
                                    tallyResult = tallyResult,
                                    elapsedSecondsProvider = elapsedSecondsProvider,
                                    timeAttackMinutes = timeAttackMinutes,
                                    isBoardLocked = isBoardLocked
                                )

                                Text(
                                    text = if (isBoardLocked) {
                                        "Time's up! Board locked — tap Quit to save or Clear to play again."
                                    } else {
                                        "Tip: Tap the arrow to toggle direction."
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBoardLocked) {
                                        WordsWorthColors.InvalidWordText
                                    } else {
                                        WordsWorthColors.FocusedCursorBlue
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                TileKeyboardRack(
                                    keyboardLayout = keyboardLayout,
                                    selectedLetter = selectedLetter,
                                    isBoardLocked = isBoardLocked,
                                    dragThresholdPx = dragThresholdPx,
                                    onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                    onKeyboardInput = onKeyboardInput,
                                    onKeyAction = onKeyAction,
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
                                    },
                                    isCompact = true
                                )

                                ValidateAndQuitRow(
                                    isDictionaryLoaded = isDictionaryLoaded,
                                    onValidate = onValidate,
                                    onQuitClick = onQuitClick,
                                    isCompact = true
                                )

                                WordsListCard(
                                    tallyResult = tallyResult,
                                    onRequestWhitelistWord = onRequestWhitelistWord,
                                    inScrollableParent = true
                                )

                                PreviousGamesColumn(
                                    savedGames = savedGames,
                                    onClickSavedGame = onClickSavedGame,
                                    onDeleteSavedGame = onDeleteSavedGame,
                                    thumbnailSize = 96.dp,
                                    inScrollableParent = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Right: Scrollable Board Options column
                            Column(
                                modifier = Modifier
                                    .width(132.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            ) {
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
                                    isCompact = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // =========================================================================
                    // 3. MEDIUM TABLET / FOLDABLE (600.dp <= maxWidth < 960.dp)
                    // =========================================================================
                    screenWidth < 960.dp -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ScoreAndWordsColumn(
                                    highScoreEntry = highScoreEntry,
                                    onClickHighScore = onClickSavedGame,
                                    tallyResult = tallyResult,
                                    elapsedSecondsProvider = elapsedSecondsProvider,
                                    timeAttackMinutes = timeAttackMinutes,
                                    isBoardLocked = isBoardLocked,
                                    onRequestWhitelistWord = onRequestWhitelistWord,
                                    modifier = Modifier
                                        .width(156.dp)
                                        .height(540.dp)
                                        .padding(top = boardPadding)
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(max = 460.dp),
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
                                        onClearCell = { r, c -> onUpdateCell(r, c, null) },
                                        pendingRomaji = pendingRomaji
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
                                        onKeyAction = onKeyAction,
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

                                    ValidateAndQuitRow(
                                        isDictionaryLoaded = isDictionaryLoaded,
                                        onValidate = onValidate,
                                        onQuitClick = onQuitClick,
                                        isCompact = false
                                    )
                                }

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
                                    isCompact = true,
                                    modifier = Modifier
                                        .width(140.dp)
                                        .padding(top = boardPadding)
                                )
                            }

                            PreviousGamesColumn(
                                savedGames = savedGames,
                                onClickSavedGame = onClickSavedGame,
                                onDeleteSavedGame = onDeleteSavedGame,
                                thumbnailSize = 128.dp,
                                inScrollableParent = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // =========================================================================
                    // 4. LARGE TABLET / DESKTOP (maxWidth >= 960.dp)
                    // =========================================================================
                    else -> {
                        val isExtraWide = screenWidth >= 1180.dp
                        val prevGamesWidth = if (isExtraWide) 428.dp else 280.dp
                        val prevGamesThumbSize = if (isExtraWide) 208.dp else 132.dp

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // LEFT COLUMN: Running list of saved board mini-screenshots + scores
                            PreviousGamesColumn(
                                savedGames = savedGames,
                                onClickSavedGame = onClickSavedGame,
                                onDeleteSavedGame = onDeleteSavedGame,
                                thumbnailSize = prevGamesThumbSize,
                                inScrollableParent = false,
                                modifier = Modifier
                                    .width(prevGamesWidth)
                                    .fillMaxHeight()
                            )

                            // RIGHT AREA: Score/Words column, Center Board + Keyboard, Right Board Options column
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                // 1. Score, High Score, Timer, and Validated Words column
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
                                        onClearCell = { r, c -> onUpdateCell(r, c, null) },
                                        pendingRomaji = pendingRomaji
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
                                        onKeyAction = onKeyAction,
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

                                    ValidateAndQuitRow(
                                        isDictionaryLoaded = isDictionaryLoaded,
                                        onValidate = onValidate,
                                        onQuitClick = onQuitClick,
                                        isCompact = false
                                    )
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
 * Shared row containing the side-by-side `Validate` and `Quit` buttons.
 */
@Composable
private fun ValidateAndQuitRow(
    isDictionaryLoaded: Boolean,
    onValidate: () -> Unit,
    onQuitClick: () -> Unit,
    isCompact: Boolean
) {
    val buttonHeight = if (isCompact) 40.dp else 46.dp
    val fontSize = if (isCompact) 14.sp else 15.sp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp)
    ) {
        Button(
            onClick = onValidate,
            enabled = isDictionaryLoaded,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Validate",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onQuitClick,
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Quit",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold
            )
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
    onOpenSettings: () -> Unit,
    isCompact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "WordsWorth-J",
            fontSize = if (isCompact) 19.sp else 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
            TextButton(
                onClick = onLoadExample,
                contentPadding = PaddingValues(
                    horizontal = if (isCompact) 8.dp else 12.dp,
                    vertical = if (isCompact) 4.dp else 6.dp
                )
            ) {
                Text("Example", fontSize = if (isCompact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onClearBoard,
                contentPadding = PaddingValues(
                    horizontal = if (isCompact) 8.dp else 12.dp,
                    vertical = if (isCompact) 4.dp else 6.dp
                )
            ) {
                Text("Clear", fontSize = if (isCompact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = if (isCompact) Modifier.size(36.dp) else Modifier
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
