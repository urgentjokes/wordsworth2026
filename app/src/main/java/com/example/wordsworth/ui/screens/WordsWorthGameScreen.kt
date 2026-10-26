package com.example.wordsworth.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.wordsworth.FormedWord
import com.example.wordsworth.GameLanguage
import com.example.wordsworth.JapaneseVerbRule
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
import com.example.wordsworth.ui.components.ModesTabContent
import com.example.wordsworth.ui.components.PreviousGamesColumn
import com.example.wordsworth.ui.components.ScoreAndWordsColumn
import com.example.wordsworth.ui.components.TileKeyboardRack
import com.example.wordsworth.ui.components.WordsListCard
import com.example.wordsworth.ui.components.WordsListDialog
import com.example.wordsworth.ui.theme.WordsWorthColors

/**
 * Main gameplay screen of WordsWorth featuring three tabs along the top under the title bar:
 * - **"Previous"**: Displays that language's list of previous saved games ([PreviousGamesColumn]).
 * - **"Current"**: Displays the active game board ([CrosswordBoardGrid]), the keyboard rack
 *   ([TileKeyboardRack]), the score and timer row, action buttons, and the list of formed words
 *   ([WordsListCard]).
 * - **"Modes"**: Displays grid-size options (6x6, 9x9, 12x12), board pattern options (All-blank,
 *   Random pattern), and Time Attack mode configuration ([ModesTabContent]).
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
    gameLanguage: GameLanguage = GameLanguage.ENGLISH,
    japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
    verbDictionary: Set<String> = emptySet(),
    isHapticFeedbackEnabled: Boolean = true,
    onToggleLanguage: () -> Unit = {},
    pendingRomaji: String = "",
    onKeyAction: (String) -> Unit = {}
) {
    // Cached thumbnails for the "All-blank" and "Random pattern" option buttons
    val allBlankThumb = remember(japaneseVerbRule, verbDictionary) {
        SavedGamesManager.createBoardScreenshot(
            WordsWorthLogic.createBlankBoard(6),
            emptyMap(),
            japaneseVerbRule = japaneseVerbRule,
            verbDictionary = verbDictionary
        ).asImageBitmap()
    }
    val randomPatternThumb = remember(japaneseVerbRule, verbDictionary) {
        SavedGamesManager.createBoardScreenshot(
            WordsWorthLogic.samplePatternThumbnailBoard(),
            emptyMap(),
            japaneseVerbRule = japaneseVerbRule,
            verbDictionary = verbDictionary
        ).asImageBitmap()
    }

    // Drag-and-drop coordinate tracking in root window space
    var gridBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var keyboardOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var draggingLetter by remember { mutableStateOf<Char?>(null) }
    var dragPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var showWordsDialog by remember { mutableStateOf(false) }

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
    val currentLanguageSavedGames = remember(savedGames.toList(), gameLanguage) {
        savedGames.filter { it.language == gameLanguage }
    }
    val highScoreEntry = currentLanguageSavedGames.maxByOrNull { it.score }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(1) }

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
                // Top Bar: App Title, Language Toggle ("En"/"日"), Example/Clear actions, and Settings
                WordsWorthTopBar(
                    gameLanguage = gameLanguage,
                    onToggleLanguage = onToggleLanguage,
                    onLoadExample = onLoadExample,
                    onClearBoard = onClearBoard,
                    onOpenSettings = onOpenSettings,
                    isCompact = isCompactBoard
                )

                Spacer(modifier = Modifier.height(if (isCompactBoard) 2.dp else 4.dp))

                // Three tabs along the top, under the title row: Previous, Current, Modes
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(
                            color = WordsWorthColors.WarmDivider,
                            thickness = 1.dp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabTitles = listOf("Previous", "Current", "Modes")
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    fontSize = if (isCompactBoard) 14.sp else 16.sp,
                                    color = if (selectedTabIndex == index) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactBoard) 4.dp else 6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTabIndex) {
                        // =====================================================================
                        // TAB 0: PREVIOUS (Language's list of previous games)
                        // =====================================================================
                        0 -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                PreviousGamesColumn(
                                    savedGames = currentLanguageSavedGames,
                                    onClickSavedGame = { entry ->
                                        onClickSavedGame(entry)
                                        selectedTabIndex = 1
                                    },
                                    onDeleteSavedGame = onDeleteSavedGame,
                                    thumbnailSize = if (isCompactBoard) 100.dp else 160.dp,
                                    inScrollableParent = false,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .widthIn(max = 680.dp)
                                )
                            }
                        }

                        // =====================================================================
                        // TAB 1: CURRENT (Game board, keyboard, score row, and words list)
                        // =====================================================================
                        1 -> {
                            when {
                                // --- A. Compact Phone Portrait (< 600.dp) ---
                                isCompactWidth -> {
                                    val maxBoardHeight = (screenHeight - 340.dp).coerceIn(210.dp, 370.dp)
                                    val boardSideDp = min(screenWidth - 16.dp, maxBoardHeight)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        horizontalAlignment = Alignment.CenterHorizontally
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

                                        Spacer(modifier = Modifier.height(6.dp))

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
                                                pendingRomaji = pendingRomaji,
                                                gameLanguage = gameLanguage,
                                                japaneseVerbRule = japaneseVerbRule,
                                                verbDictionary = verbDictionary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        // Direction / Locked Tip Row with Words Pill
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isBoardLocked) {
                                                    "Time's up! Board locked."
                                                } else {
                                                    "Tip: Tap arrow to toggle → ↓."
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isBoardLocked) {
                                                    WordsWorthColors.InvalidWordText
                                                } else {
                                                    WordsWorthColors.FocusedCursorBlue
                                                },
                                                modifier = Modifier.weight(1f, fill = false)
                                            )

                                            WordsPill(
                                                wordCount = tallyResult?.words?.size ?: 0,
                                                fontSize = 11.sp,
                                                onClick = { showWordsDialog = true }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        // Compact On-Screen Tile Keyboard
                                        TileKeyboardRack(
                                            keyboardLayout = keyboardLayout,
                                            selectedLetter = selectedLetter,
                                            isBoardLocked = isBoardLocked,
                                            dragThresholdPx = dragThresholdPx,
                                            onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                            onKeyboardInput = onKeyboardInput,
                                            isCompact = true,
                                            gameLanguage = gameLanguage,
                                            isHapticFeedbackEnabled = isHapticFeedbackEnabled,
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

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Validate & Quit Action Buttons
                                        ValidateAndQuitRow(
                                            isDictionaryLoaded = isDictionaryLoaded,
                                            onValidate = onValidate,
                                            onQuitClick = onQuitClick,
                                            isCompact = true
                                        )
                                    }
                                }

                                // --- B. Phone Landscape / Short Height (maxWidth >= 600.dp && maxHeight < 520.dp) ---
                                isShortLandscape -> {
                                    val boardSideDp = (screenHeight - 96.dp).coerceIn(190.dp, 360.dp)
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
                                                pendingRomaji = pendingRomaji,
                                                gameLanguage = gameLanguage,
                                                japaneseVerbRule = japaneseVerbRule,
                                                verbDictionary = verbDictionary
                                            )
                                        }

                                        // Right: Score Row + Keyboard + Validate/Quit + Words Card
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            CompactScoreAndTimerRow(
                                                highScoreEntry = highScoreEntry,
                                                onClickHighScore = onClickSavedGame,
                                                tallyResult = tallyResult,
                                                elapsedSecondsProvider = elapsedSecondsProvider,
                                                timeAttackMinutes = timeAttackMinutes,
                                                isBoardLocked = isBoardLocked
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Direction / Locked Tip Row with Words Pill
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isBoardLocked) {
                                                        "Time's up! Board locked."
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
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                WordsPill(
                                                    wordCount = tallyResult?.words?.size ?: 0,
                                                    fontSize = 11.sp,
                                                    onClick = { showWordsDialog = true }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(3.dp))

                                            TileKeyboardRack(
                                                keyboardLayout = keyboardLayout,
                                                selectedLetter = selectedLetter,
                                                isBoardLocked = isBoardLocked,
                                                dragThresholdPx = dragThresholdPx,
                                                onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                                onKeyboardInput = onKeyboardInput,
                                                isCompact = true,
                                                gameLanguage = gameLanguage,
                                                isHapticFeedbackEnabled = isHapticFeedbackEnabled,
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

                                            Spacer(modifier = Modifier.height(6.dp))

                                            ValidateAndQuitRow(
                                                isDictionaryLoaded = isDictionaryLoaded,
                                                onValidate = onValidate,
                                                onQuitClick = onQuitClick,
                                                isCompact = true
                                            )
                                        }
                                    }
                                }

                                // --- C. Tablet & Desktop (maxWidth >= 600.dp && maxHeight >= 520.dp) ---
                                else -> {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // Left column: Score, High Score, Timer, and Words list
                                        ScoreAndWordsColumn(
                                            highScoreEntry = highScoreEntry,
                                            onClickHighScore = onClickSavedGame,
                                            tallyResult = tallyResult,
                                            elapsedSecondsProvider = elapsedSecondsProvider,
                                            timeAttackMinutes = timeAttackMinutes,
                                            isBoardLocked = isBoardLocked,
                                            onRequestWhitelistWord = onRequestWhitelistWord,
                                            modifier = Modifier
                                                .width(if (screenWidth >= 960.dp) 240.dp else 190.dp)
                                                .fillMaxHeight()
                                                .padding(top = boardPadding)
                                        )

                                        // Center/Right column: Board, Tip, Keyboard, Validate & Quit
                                        Column(
                                            modifier = Modifier
                                                .widthIn(max = 507.dp)
                                                .fillMaxWidth(if (screenWidth >= 960.dp) 0.65f else 0.72f)
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
                                                pendingRomaji = pendingRomaji,
                                                gameLanguage = gameLanguage,
                                                japaneseVerbRule = japaneseVerbRule,
                                                verbDictionary = verbDictionary
                                            )

                                            Spacer(modifier = Modifier.height(3.dp))

                                            // Direction / Locked Tip Row with Words Pill
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isBoardLocked) {
                                                        "Time's up! Board locked."
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
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                WordsPill(
                                                    wordCount = tallyResult?.words?.size ?: 0,
                                                    fontSize = 12.sp,
                                                    onClick = { showWordsDialog = true }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            TileKeyboardRack(
                                                keyboardLayout = keyboardLayout,
                                                selectedLetter = selectedLetter,
                                                isBoardLocked = isBoardLocked,
                                                dragThresholdPx = dragThresholdPx,
                                                onKeyboardOriginChanged = { keyboardOriginInRoot = it },
                                                onKeyboardInput = onKeyboardInput,
                                                gameLanguage = gameLanguage,
                                                isHapticFeedbackEnabled = isHapticFeedbackEnabled,
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
                                    }
                                }
                            }
                        }

                        // =====================================================================
                        // TAB 2: MODES (Grid-size options, blank/random options, and Time Attack)
                        // =====================================================================
                        2 -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                ModesTabContent(
                                    boardSize = boardSize,
                                    onChangeBoardSize = onChangeBoardSize,
                                    isRandomPatternMode = isRandomPatternMode,
                                    allBlankThumb = allBlankThumb,
                                    randomPatternThumb = randomPatternThumb,
                                    onSelectAllBlank = onSelectAllBlank,
                                    onSelectRandomPattern = onSelectRandomPattern,
                                    timeAttackMinutes = timeAttackMinutes,
                                    onClickTimeAttack = onClickTimeAttack,
                                    isCompact = isCompactBoard,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 520.dp)
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

            // Modal pop-up words list dialog
            if (showWordsDialog) {
                WordsListDialog(
                    tallyResult = tallyResult,
                    onRequestWhitelistWord = { formedWord ->
                        showWordsDialog = false
                        onRequestWhitelistWord(formedWord)
                    },
                    onDismiss = { showWordsDialog = false }
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
    gameLanguage: GameLanguage,
    onToggleLanguage: () -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "WordsWorth",
                fontSize = if (isCompact) 18.sp else 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Language switch icon/button at the top of the screen ("En" for English, "日" for Japanese)
            Surface(
                onClick = onToggleLanguage,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                shadowElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 8.dp else 10.dp,
                        vertical = if (isCompact) 3.dp else 5.dp
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (gameLanguage == GameLanguage.ENGLISH) "En" else "日",
                        fontSize = if (isCompact) 13.sp else 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
        ) {
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

/**
 * Compact pill button placed directly next to the direction tip.
 * Sized to fit comfortably in the slot between gameboard and keyboard.
 */
@Composable
private fun WordsPill(
    wordCount: Int,
    fontSize: TextUnit = 11.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (wordCount > 0) "Words ($wordCount)" else "Words",
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
