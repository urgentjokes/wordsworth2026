package com.example.wordsworth

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centralized state holder for the WordsWorth game session.
 *
 * Separating all game state, cursor navigation, timer management, hardware key handling,
 * and persistence operations into this dedicated class keeps UI composables declarative,
 * focused on presentation, and easy to maintain.
 */
@Stable
class WordsWorthGameState(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    // -------------------------------------------------------------------------
    // Navigation & Dictionary State
    // -------------------------------------------------------------------------

    /** Controls whether the Settings screen is currently displayed instead of the main board. */
    var showSettingsScreen by mutableStateOf(false)

    /** Set of uppercase valid words loaded from `assets/words.txt` plus user whitelisted words. */
    var dictionary by mutableStateOf<Set<String>>(emptySet())

    /** True once [dictionary] and [savedGames] have finished loading from disk. */
    var isDictionaryLoaded by mutableStateOf(false)

    /** Running history of saved games displayed in the left-hand "Previous Games" column. */
    val savedGames = mutableStateListOf<SavedGameEntry>()

    // -------------------------------------------------------------------------
    // Board & User Preferences State
    // -------------------------------------------------------------------------

    /** Active board dimension: `6` (6x6), `9` (9x9), or `12` (12x12). */
    var boardSize by mutableIntStateOf(WordsWorthLogic.BOARD_SIZE)

    /** Board pattern mode: `false` = All-blank, `true` = Random pattern with blacked-out squares. */
    var isRandomPatternMode by mutableStateOf(false)

    /** On-screen keyboard arrangement (`ALPHABETICAL` or `QWERTY`). */
    var keyboardLayout by mutableStateOf(WordsWorthLogic.loadKeyboardLayout(context))

    /** Extended word scoring behavior (`EXTENDED_DIFF_ONLY` or `CUMULATIVE_EXTENDED`). */
    var scoringMode by mutableStateOf(WordsWorthLogic.loadScoringMode(context))

    /**
     * Validated words recorded earlier in the current game session.
     * Used when [scoringMode] is [ScoringMode.CUMULATIVE_EXTENDED] so extending a previously
     * validated word (e.g., `SOUND` -> `SOUNDLY`) retains the base word's points.
     */
    val validatedWordsHistory = mutableStateListOf<FormedWord>()

    /** Current 2D crossword board matrix (`null` = empty, `'#'` = blocked, `'A'..'Z'` = letter). */
    var board by mutableStateOf(WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE))

    // -------------------------------------------------------------------------
    // Timer & Time Attack State
    // -------------------------------------------------------------------------

    /** Elapsed seconds on the game timer (starts counting when the first letter is placed). */
    var elapsedSeconds by mutableIntStateOf(0)

    /** True while the per-second game timer coroutine is actively ticking. */
    var isTimerRunning by mutableStateOf(false)

    /** Selected Time Attack countdown duration in minutes (`1`, `3`, or `5`), or `null` for untimed mode. */
    var timeAttackMinutes by mutableStateOf<Int?>(null)

    /** Controls visibility of the Time Attack duration picker dialog. */
    var showTimeAttackDialog by mutableStateOf(false)

    /**
     * True when a Time Attack countdown reaches `0:00`.
     * While locked, board edits are disabled so the player can inspect validated words
     * (and whitelist invalid words if desired) before saving via Quit.
     */
    var isBoardLocked by mutableStateOf(false)

    // -------------------------------------------------------------------------
    // Cursor, Input, & Validation State
    // -------------------------------------------------------------------------

    /** Currently highlighted letter tile in the rack (`'A'..'Z'`), or `null` for the `CLR` eraser tile. */
    var selectedLetter by mutableStateOf<Char?>('S')

    /** Coordinates `(row, col)` of the currently focused square on the board. */
    var focusedCell by mutableStateOf<Pair<Int, Int>?>(0 to 0)

    /** Active typing direction (`HORIZONTAL` = Across `→`, `VERTICAL` = Down `↓`). */
    var focusDirection by mutableStateOf(WordDirection.HORIZONTAL)

    /** Latest board validation and score tally result, or `null` if not yet validated. */
    var tallyResult by mutableStateOf<TallyResult?>(null)

    // -------------------------------------------------------------------------
    // Modal Dialog & Saved Game Resume State
    // -------------------------------------------------------------------------

    /** Controls visibility of the "Finish Current Game" Quit dialog. */
    var showQuitDialog by mutableStateOf(false)

    /** Legacy saved game entry being viewed in a modal screenshot preview dialog (if any). */
    var previewSavedEntry by mutableStateOf<SavedGameEntry?>(null)

    /** Saved game entry pending deletion confirmation after a long-press. */
    var pendingDeleteEntry by mutableStateOf<SavedGameEntry?>(null)

    /**
     * Pending board pattern switch (`false` = switch to All-blank, `true` = switch to Random pattern)
     * awaiting confirmation when a game is already in progress.
     */
    var pendingPatternSwitch by mutableStateOf<Boolean?>(null)

    /** Invalid [FormedWord] pending user confirmation to be added to the custom whitelist. */
    var pendingWhitelistWord by mutableStateOf<FormedWord?>(null)

    /**
     * ID of the saved game that was resumed onto the active board, or `null` if playing a new round.
     * When non-null, saving via Quit updates that existing entry in place rather than duplicating it.
     */
    var resumedGameId by mutableStateOf<Long?>(null)

    // -------------------------------------------------------------------------
    // Board Helpers & Cursor Navigation
    // -------------------------------------------------------------------------

    /** Returns the first non-blocked square `(row, col)` in row-major order. */
    fun firstAvailableCell(targetBoard: Array<Array<Char?>> = board): Pair<Int, Int>? {
        for (r in targetBoard.indices) {
            for (c in targetBoard[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(targetBoard[r][c])) {
                    return r to c
                }
            }
        }
        return null
    }

    /** Returns the first empty (`null`) square on [targetBoard], falling back to [firstAvailableCell]. */
    fun firstEmptyOrAvailableCell(targetBoard: Array<Array<Char?>> = board): Pair<Int, Int>? {
        for (r in targetBoard.indices) {
            for (c in targetBoard[r].indices) {
                if (targetBoard[r][c] == null) {
                    return r to c
                }
            }
        }
        return firstAvailableCell(targetBoard)
    }

    /** Returns true if a round has started (timer running or at least one letter placed on the board). */
    fun isGameInProgress(): Boolean {
        return isTimerRunning || board.any { row -> row.any { WordsWorthLogic.isLetterCell(it) } }
    }

    /**
     * Evaluates [targetBoard] against [targetDict] using [targetMode], pruning any previously
     * validated words whose letters were modified/erased and recording newly validated words.
     */
    fun evaluateBoard(
        targetBoard: Array<Array<Char?>> = board,
        targetDict: Set<String> = dictionary,
        targetMode: ScoringMode = scoringMode
    ): TallyResult {
        val size = targetBoard.size
        // Prune any historical word whose cells no longer hold its original letters
        validatedWordsHistory.retainAll { prev ->
            prev.cells.indices.all { idx ->
                val (pr, pc) = prev.cells[idx]
                pr in 0 until size && pc in 0 until size &&
                    targetBoard[pr][pc]?.uppercaseChar() == prev.word[idx]
            }
        }
        val result = WordsWorthLogic.tallyBoard(
            board = targetBoard,
            dictionary = targetDict,
            scoringMode = targetMode,
            previouslyValidatedWords = validatedWordsHistory
        )
        // Record all currently valid words into session history for future extensions
        for (fw in result.validWords) {
            val exists = validatedWordsHistory.any {
                it.direction == fw.direction &&
                    it.startRow == fw.startRow &&
                    it.startCol == fw.startCol &&
                    it.word == fw.word
            }
            if (!exists) {
                validatedWordsHistory.add(fw)
            }
        }
        return result
    }

    /**
     * Clears all placed letters while preserving any blacked-out pattern squares,
     * and resets the timer, score tally, and session history.
     */
    fun resetBoardAndTimer() {
        val size = board.size
        val newBoard = Array(size) { r ->
            Array(size) { c ->
                if (WordsWorthLogic.isBlockedCell(board[r][c])) WordsWorthLogic.BLOCKED_CELL else null
            }
        }
        board = newBoard
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    /** Starts a fresh game on an all-blank board of the current [boardSize]. */
    fun applyAllBlank() {
        isRandomPatternMode = false
        val newBoard = WordsWorthLogic.createBlankBoard(boardSize)
        board = newBoard
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    /** Starts a fresh game on a newly generated random-pattern board of the current [boardSize]. */
    fun applyRandomPattern() {
        isRandomPatternMode = true
        val newBoard = WordsWorthLogic.generateRandomPatternBoard(boardSize)
        board = newBoard
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    /** Handles tapping the "All-blank" pattern button (prompts for confirmation if a game is in progress). */
    fun selectAllBlank() {
        if (isGameInProgress()) {
            pendingPatternSwitch = false
        } else {
            applyAllBlank()
        }
    }

    /** Handles tapping the "Random pattern" button (prompts for confirmation if a game is in progress). */
    fun selectRandomPattern() {
        if (isGameInProgress()) {
            pendingPatternSwitch = true
        } else {
            applyRandomPattern()
        }
    }

    /** Switches the board dimension (`6x6`, `9x9`, or `12x12`) and resets the round. */
    fun changeBoardSize(newSize: Int) {
        if (newSize == boardSize) return
        boardSize = newSize
        val newBoard = if (isRandomPatternMode) {
            WordsWorthLogic.generateRandomPatternBoard(newSize)
        } else {
            WordsWorthLogic.createBlankBoard(newSize)
        }
        board = newBoard
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    /**
     * Places or clears a [letter] at `(row, col)` on the board, pruning any invalidated
     * entries from [validatedWordsHistory] and starting the timer on the first placed letter.
     */
    fun updateCell(row: Int, col: Int, letter: Char?) {
        if (isBoardLocked) return
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(row)?.getOrNull(col))) {
            return
        }
        val size = board.size
        val copy = Array(size) { r ->
            Array(size) { c ->
                if (r == row && c == col) letter else board[r][c]
            }
        }
        board = copy
        validatedWordsHistory.retainAll { prev ->
            prev.cells.indices.all { idx ->
                val (pr, pc) = prev.cells[idx]
                pr in 0 until size && pc in 0 until size &&
                    copy[pr][pc]?.uppercaseChar() == prev.word[idx]
            }
        }
        tallyResult = tallyResult?.let { prev ->
            prev.copy(cellValidations = prev.cellValidations - (row to col))
        }

        // Automatically start the timer from 0:00 when the first letter is placed
        if (letter != null && !isTimerRunning) {
            isTimerRunning = true
        }
    }

    /**
     * Infers the most natural typing direction (`HORIZONTAL` or `VERTICAL`) when the user
     * taps or drops a tile onto `(row, col)` based on surrounding letters and open squares.
     */
    fun inferDirectionForCell(row: Int, col: Int, prevFocus: Pair<Int, Int>?): WordDirection {
        val size = board.size
        if (prevFocus != null) {
            if (row == prevFocus.first + 1 && col == prevFocus.second) {
                return WordDirection.VERTICAL
            }
            if (row == prevFocus.first && col == prevFocus.second + 1) {
                return WordDirection.HORIZONTAL
            }
        }
        val hasUpLetter = row > 0 && WordsWorthLogic.isLetterCell(board[row - 1][col])
        val hasLeftLetter = col > 0 && WordsWorthLogic.isLetterCell(board[row][col - 1])
        val hasDownLetter = row + 1 < size && WordsWorthLogic.isLetterCell(board[row + 1][col])
        val hasRightLetter = col + 1 < size && WordsWorthLogic.isLetterCell(board[row][col + 1])
        val hasDownOpen = row + 1 < size && !WordsWorthLogic.isBlockedCell(board[row + 1][col])
        val hasRightOpen = col + 1 < size && !WordsWorthLogic.isBlockedCell(board[row][col + 1])

        val cellChar = board.getOrNull(row)?.getOrNull(col)
        if (cellChar == null) {
            if (hasUpLetter && !hasLeftLetter) return WordDirection.VERTICAL
            if (hasLeftLetter && !hasUpLetter) return WordDirection.HORIZONTAL
            if (!hasRightOpen && hasDownOpen) return WordDirection.VERTICAL
            if (!hasDownOpen && hasRightOpen) return WordDirection.HORIZONTAL
        } else if (WordsWorthLogic.isLetterCell(cellChar)) {
            if ((hasLeftLetter || hasRightLetter) && !hasUpLetter && !hasDownLetter && hasDownOpen) {
                return WordDirection.VERTICAL
            }
            if ((hasUpLetter || hasDownLetter) && !hasLeftLetter && !hasRightLetter && hasRightOpen) {
                return WordDirection.HORIZONTAL
            }
        }
        return focusDirection
    }

    /**
     * Handles tapping a square on the crossword board:
     * - Tapping the already-focused square toggles between `HORIZONTAL` (`→`) and `VERTICAL` (`↓`).
     * - Tapping a different square moves focus there and infers the best typing direction.
     */
    fun onBoardCellTap(row: Int, col: Int) {
        if (isBoardLocked) return
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(row)?.getOrNull(col))) {
            return
        }
        val current = focusedCell
        if (current != null && current.first == row && current.second == col) {
            focusDirection = if (focusDirection == WordDirection.HORIZONTAL) {
                WordDirection.VERTICAL
            } else {
                WordDirection.HORIZONTAL
            }
        } else {
            focusDirection = inferDirectionForCell(row, col, current)
            focusedCell = row to col
        }
    }

    /** Advances [focusedCell] one square forward along [direction] if the next square is open. */
    fun advanceFocusFrom(row: Int, col: Int, direction: WordDirection = focusDirection) {
        val size = board.size
        when (direction) {
            WordDirection.HORIZONTAL -> {
                val nextCol = col + 1
                if (nextCol < size && !WordsWorthLogic.isBlockedCell(board[row][nextCol])) {
                    focusedCell = row to nextCol
                } else {
                    focusedCell = row to col
                }
            }
            WordDirection.VERTICAL -> {
                val nextRow = row + 1
                if (nextRow < size && !WordsWorthLogic.isBlockedCell(board[nextRow][col])) {
                    focusedCell = nextRow to col
                } else {
                    focusedCell = row to col
                }
            }
        }
    }

    /**
     * Handles a letter or `CLR` (`null`) input from either the on-screen rack or a hardware keyboard.
     *
     * @return The modified cell coordinates and its previous character (used to undo touch-down
     *   placement if the user's touch turns into a drag-and-drop gesture), or `null` if erasing.
     */
    fun onKeyboardInput(letter: Char?): Pair<Pair<Int, Int>, Char?>? {
        if (isBoardLocked) return null
        selectedLetter = letter
        val target = focusedCell ?: firstAvailableCell(board) ?: return null
        val (r, c) = target
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(r)?.getOrNull(c))) return null

        if (letter != null) {
            val previousChar = board[r][c]
            updateCell(r, c, letter)
            advanceFocusFrom(r, c, focusDirection)
            return (r to c) to previousChar
        } else {
            // Backspace / CLR behavior: clear current square if occupied, else step back one square and clear
            if (WordsWorthLogic.isLetterCell(board[r][c])) {
                updateCell(r, c, null)
                focusedCell = r to c
            } else {
                val prevCell = when (focusDirection) {
                    WordDirection.HORIZONTAL -> if (c > 0) r to (c - 1) else null
                    WordDirection.VERTICAL -> if (r > 0) (r - 1) to c else null
                }
                if (prevCell != null && !WordsWorthLogic.isBlockedCell(board[prevCell.first][prevCell.second])) {
                    focusedCell = prevCell
                    updateCell(prevCell.first, prevCell.second, null)
                }
            }
            return null
        }
    }

    /**
     * Completes a drag-and-drop gesture of [letter] onto `(row, col)`, restoring any preliminary
     * touch-down placement recorded in [undoPlacement].
     */
    fun onDropLetterOnCell(
        row: Int,
        col: Int,
        letter: Char,
        undoPlacement: Pair<Pair<Int, Int>, Char?>?
    ) {
        if (isBoardLocked) return
        undoPlacement?.let { (placedCell, oldChar) ->
            if (placedCell.first != row || placedCell.second != col) {
                updateCell(placedCell.first, placedCell.second, oldChar)
            }
        }
        val newDir = inferDirectionForCell(row, col, null)
        focusDirection = newDir
        updateCell(row, col, letter)
        advanceFocusFrom(row, col, newDir)
    }

    /** Moves [focusedCell] along `(dRow, dCol)` when an arrow/D-pad key is pressed, skipping blocked cells. */
    fun moveFocusByArrow(dRow: Int, dCol: Int, newDirection: WordDirection) {
        if (isBoardLocked) return
        focusDirection = newDirection
        val size = board.size
        val start = focusedCell ?: firstAvailableCell(board) ?: return
        var r = start.first + dRow
        var c = start.second + dCol
        while (r in 0 until size && c in 0 until size) {
            if (!WordsWorthLogic.isBlockedCell(board[r][c])) {
                focusedCell = r to c
                return
            }
            r += dRow
            c += dCol
        }
    }

    /**
     * Processes physical/Bluetooth keyboard [KeyEvent]s:
     * - `A`–`Z`: Places the typed letter and advances cursor focus.
     * - `Backspace` / `Delete`: Clears the focused or preceding square.
     * - `Space` / `Tab`: Toggles Across (`→`) and Down (`↓`) cursor direction.
     * - `Arrow Keys`: Moves the cursor across the grid (skipping blacked-out squares).
     * - `Enter`: Validates and tallies the current board.
     */
    fun handleHardwareKeyEvent(event: KeyEvent): Boolean {
        val isModalOrSettingsOpen = showSettingsScreen ||
            showQuitDialog ||
            showTimeAttackDialog ||
            pendingDeleteEntry != null ||
            pendingPatternSwitch != null ||
            pendingWhitelistWord != null ||
            previewSavedEntry != null

        if (isModalOrSettingsOpen) {
            return false
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            return false
        }

        val code = event.keyCode
        val isHandledKey = code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ||
            code == KeyEvent.KEYCODE_DEL ||
            code == KeyEvent.KEYCODE_FORWARD_DEL ||
            code == KeyEvent.KEYCODE_SPACE ||
            code == KeyEvent.KEYCODE_TAB ||
            code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER

        if (!isHandledKey) {
            return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when {
                code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                    val letter = ('A'.code + (code - KeyEvent.KEYCODE_A)).toChar()
                    onKeyboardInput(letter)
                }
                code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL -> {
                    onKeyboardInput(null)
                }
                code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_TAB -> {
                    if (!isBoardLocked) {
                        focusDirection = if (focusDirection == WordDirection.HORIZONTAL) {
                            WordDirection.VERTICAL
                        } else {
                            WordDirection.HORIZONTAL
                        }
                    }
                }
                code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    moveFocusByArrow(0, -1, WordDirection.HORIZONTAL)
                }
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    moveFocusByArrow(0, 1, WordDirection.HORIZONTAL)
                }
                code == KeyEvent.KEYCODE_DPAD_UP -> {
                    moveFocusByArrow(-1, 0, WordDirection.VERTICAL)
                }
                code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                    moveFocusByArrow(1, 0, WordDirection.VERTICAL)
                }
                code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (isDictionaryLoaded) {
                        tallyResult = evaluateBoard()
                    }
                }
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Saved Game & Example Board Actions
    // -------------------------------------------------------------------------

    /**
     * Resumes a saved game when tapped in the "Previous Games" column or via the High Score card.
     * Falls back to showing a screenshot preview dialog for legacy entries without board data.
     */
    fun resumeSavedGame(entry: SavedGameEntry) {
        val savedBoard = entry.board
        if (savedBoard != null && savedBoard.isNotEmpty()) {
            val size = savedBoard.size
            val restoredBoard = Array(size) { r ->
                Array(size) { c -> savedBoard[r][c] }
            }
            resumedGameId = entry.id
            validatedWordsHistory.clear()
            validatedWordsHistory.addAll(entry.validatedWords)
            isBoardLocked = false
            timeAttackMinutes = null
            boardSize = size
            isRandomPatternMode = restoredBoard.any { row -> row.any { WordsWorthLogic.isBlockedCell(it) } }
            board = restoredBoard
            focusedCell = firstEmptyOrAvailableCell(restoredBoard)
            focusDirection = WordDirection.HORIZONTAL
            elapsedSeconds = entry.elapsedSeconds
            isTimerRunning = true
            if (isDictionaryLoaded) {
                tallyResult = evaluateBoard(restoredBoard)
            }
        } else {
            previewSavedEntry = entry
        }
    }

    /** Loads the pre-populated sample crossword board and immediately tallies its words. */
    fun loadExample() {
        isRandomPatternMode = false
        val newBoard = WordsWorthLogic.exampleBoard(boardSize)
        board = newBoard
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        if (!isTimerRunning) {
            isTimerRunning = true
        }
        if (isDictionaryLoaded) {
            tallyResult = evaluateBoard(newBoard)
        }
    }

    /** Updates the user's [KeyboardLayout] preference and saves it to SharedPreferences. */
    fun changeKeyboardLayout(newLayout: KeyboardLayout) {
        keyboardLayout = newLayout
        WordsWorthLogic.saveKeyboardLayout(context, newLayout)
    }

    /**
     * Updates the user's [ScoringMode] preference, saves it to SharedPreferences, and
     * immediately recomputes [tallyResult] if a validated board is currently displayed.
     */
    fun changeScoringMode(newMode: ScoringMode) {
        scoringMode = newMode
        WordsWorthLogic.saveScoringMode(context, newMode)
        if (tallyResult != null && isDictionaryLoaded) {
            tallyResult = evaluateBoard(board, dictionary, newMode)
        }
    }

    /** Clears all saved games from disk and from the in-memory [savedGames] list. */
    fun clearAllSavedGames() {
        coroutineScope.launch(Dispatchers.IO) {
            SavedGamesManager.clearAllSavedGames(context)
            withContext(Dispatchers.Main) {
                savedGames.clear()
            }
        }
    }

    /** Adds [pendingWhitelistWord] to the persistent whitelist and re-validates the board. */
    fun confirmWhitelistWord(formedWord: FormedWord) {
        val wordToAdd = formedWord.word.uppercase()
        pendingWhitelistWord = null
        val updatedDict = dictionary + wordToAdd
        dictionary = updatedDict
        tallyResult = evaluateBoard(board, updatedDict, scoringMode)
        coroutineScope.launch(Dispatchers.IO) {
            WordsWorthLogic.addWordToWhitelist(context, wordToAdd)
        }
    }

    /** Saves the current board and score to "Previous Games" and resets the active board. */
    fun saveAndFinishCurrentGame() {
        showQuitDialog = false
        val finalTally = evaluateBoard()
        val size = board.size
        val boardSnapshot = Array(size) { r ->
            Array(size) { c -> board[r][c] }
        }
        val finalSeconds = elapsedSeconds
        val replacingId = resumedGameId
        resetBoardAndTimer()

        coroutineScope.launch {
            val savedEntry = withContext(Dispatchers.IO) {
                SavedGamesManager.saveGame(
                    context = context,
                    board = boardSnapshot,
                    tallyResult = finalTally,
                    elapsedSeconds = finalSeconds,
                    replaceId = replacingId
                )
            }
            if (replacingId != null) {
                savedGames.removeAll { it.id == replacingId }
            }
            savedGames.add(0, savedEntry)
        }
    }

    /** Deletes [entry] from disk and removes it from [savedGames]. */
    fun confirmDeleteSavedGame(entry: SavedGameEntry) {
        pendingDeleteEntry = null
        savedGames.remove(entry)
        coroutineScope.launch(Dispatchers.IO) {
            SavedGamesManager.deleteSavedGame(context, entry.id)
        }
    }
}

/**
 * Creates and remembers a [WordsWorthGameState] instance, wiring up asynchronous dictionary/history
 * loading, the per-second timer coroutine, and hardware keyboard event dispatch on [MainActivity].
 */
@Composable
fun rememberWordsWorthGameState(): WordsWorthGameState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember(context, coroutineScope) {
        WordsWorthGameState(context, coroutineScope)
    }

    // Load dictionary and saved games on startup
    LaunchedEffect(state) {
        val loadedDict = withContext(Dispatchers.IO) {
            WordsWorthLogic.loadDictionary(context)
        }
        val loadedGames = withContext(Dispatchers.IO) {
            SavedGamesManager.loadSavedGames(context)
        }
        state.dictionary = loadedDict
        state.savedGames.clear()
        state.savedGames.addAll(loadedGames)
        state.isDictionaryLoaded = true
    }

    // Per-second game timer & Time Attack countdown expiration handler
    LaunchedEffect(state.isTimerRunning, state.timeAttackMinutes) {
        while (state.isTimerRunning) {
            delay(1000L)
            state.elapsedSeconds += 1
            val limitSeconds = state.timeAttackMinutes?.times(60)
            if (limitSeconds != null && state.elapsedSeconds >= limitSeconds) {
                state.elapsedSeconds = limitSeconds
                state.isTimerRunning = false
                state.isBoardLocked = true
                state.focusedCell = null
                if (state.isDictionaryLoaded) {
                    state.tallyResult = state.evaluateBoard()
                }
            }
        }
    }

    // Register hardware/Bluetooth keyboard handler with the hosting MainActivity
    val latestKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        state.handleHardwareKeyEvent(event)
    }
    DisposableEffect(context) {
        val activity = context as? MainActivity
        activity?.hardwareKeyHandler = { event -> latestKeyHandler(event) }
        onDispose {
            if (activity != null) {
                activity.hardwareKeyHandler = null
            }
        }
    }

    return state
}
