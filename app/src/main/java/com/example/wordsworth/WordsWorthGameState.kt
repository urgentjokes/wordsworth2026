package com.example.wordsworth

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Centralized state holder for the WordsWorth game session.
 *
 * Inherits from [AndroidViewModel] to survive configuration changes (e.g. screen orientation,
 * theme toggle, multi-window resize) without losing the active board, cursor, timer, or game history.
 *
 * Supports seamless switching between the English and Japanese versions of the game while preserving
 * active boards and dictionaries.
 */
@Stable
class WordsWorthGameState(
    application: Application
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication<Application>().applicationContext
    private val coroutineScope: CoroutineScope get() = viewModelScope

    // -------------------------------------------------------------------------
    // Game Language State
    // -------------------------------------------------------------------------

    /** Active game language version (English or Japanese). */
    var gameLanguage by mutableStateOf(WordsWorthLogic.loadGameLanguage(context))
        private set

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val loadedDictEn = withContext(Dispatchers.IO) {
                WordsWorthLogic.loadDictionary(context, GameLanguage.ENGLISH)
            }
            val loadedDictJa = withContext(Dispatchers.IO) {
                WordsWorthLogic.loadDictionary(context, GameLanguage.JAPANESE)
            }
            val loadedVerbs = withContext(Dispatchers.IO) {
                WordsWorthLogic.loadJapaneseVerbDictionary(context)
            }
            val loadedGames = withContext(Dispatchers.IO) {
                SavedGamesManager.loadSavedGames(context)
            }
            englishDictionary = loadedDictEn
            japaneseDictionary = loadedDictJa
            japaneseVerbDictionary = loadedVerbs
            savedGames.clear()
            savedGames.addAll(loadedGames)
            isDictionaryLoaded = true
            tallyResult = evaluateBoard()
        }
    }

    // -------------------------------------------------------------------------
    // Navigation & Dictionary State
    // -------------------------------------------------------------------------

    /** Controls whether the Settings screen is currently displayed instead of the main board. */
    var showSettingsScreen by mutableStateOf(false)

    /** Loaded dictionary words for English. */
    var englishDictionary by mutableStateOf<Set<String>>(emptySet())

    /** Loaded dictionary words for Japanese. */
    var japaneseDictionary by mutableStateOf<Set<String>>(emptySet())

    /** Active dictionary corresponding to [gameLanguage]. */
    val dictionary: Set<String>
        get() = if (gameLanguage == GameLanguage.ENGLISH) englishDictionary else japaneseDictionary

    /** True once dictionaries and [savedGames] have finished loading from disk. */
    var isDictionaryLoaded by mutableStateOf(false)

    /** Running history of saved games displayed in the left-hand "Previous Games" column. */
    val savedGames = mutableStateListOf<SavedGameEntry>()

    // -------------------------------------------------------------------------
    // Board & Language Session State Preservation
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
     * Whether placing the same valid word more than once on the board awards points after
     * the first instance (`true` = points every time, `false` = points only the first time).
     */
    var allowDuplicateWordPoints by mutableStateOf(WordsWorthLogic.loadAllowDuplicateWordPoints(context))

    /** Active Japanese verb scoring rule ([JapaneseVerbRule.CABOOSE] or [JapaneseVerbRule.PRECISION_VERBS]). */
    var japaneseVerbRule by mutableStateOf(WordsWorthLogic.loadJapaneseVerbRule(context))

    /** Loaded Japanese verb dictionary from all_japanese_verbs_hiragana.txt. */
    var japaneseVerbDictionary by mutableStateOf<Set<String>>(emptySet())

    /**
     * Validated words recorded earlier in the current game session.
     * Used when [scoringMode] is [ScoringMode.CUMULATIVE_EXTENDED] so extending a previously
     * validated word retains the base word's points, and when [allowDuplicateWordPoints] is `false`
     * to identify which duplicate instance was placed first.
     */
    val validatedWordsHistory = mutableStateListOf<FormedWord>()

    // Preserved boards and elapsed time per language to prevent loss when toggling languages
    private var boardEn: Array<Array<Char?>> = WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE)
    private var boardJa: Array<Array<Char?>> = WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE)
    private var elapsedSecondsEn: Int = 0
    private var elapsedSecondsJa: Int = 0
    private val validatedWordsHistoryEn = mutableListOf<FormedWord>()
    private val validatedWordsHistoryJa = mutableListOf<FormedWord>()

    private fun copyBoard(source: Array<Array<Char?>>): Array<Array<Char?>> {
        val size = source.size
        return Array(size) { r ->
            Array(size) { c -> source[r][c] }
        }
    }

    /** Current 2D crossword board matrix (`null` = empty, `'#'` = blocked, letter/kana = placed). */
    var board by mutableStateOf(
        if (WordsWorthLogic.loadGameLanguage(context) == GameLanguage.ENGLISH) boardEn else boardJa
    )

    /**
     * Switches between English and Japanese versions of the game.
     * Preserves the active board, cursor, timer, and history of the current language, and restores
     * the target language's board.
     */
    fun toggleGameLanguage() {
        commitPendingRomaji()

        // 1. Save active state to current language slot
        if (gameLanguage == GameLanguage.ENGLISH) {
            boardEn = copyBoard(board)
            elapsedSecondsEn = elapsedSeconds
            validatedWordsHistoryEn.clear()
            validatedWordsHistoryEn.addAll(validatedWordsHistory)
        } else {
            boardJa = copyBoard(board)
            elapsedSecondsJa = elapsedSeconds
            validatedWordsHistoryJa.clear()
            validatedWordsHistoryJa.addAll(validatedWordsHistory)
        }

        // 2. Toggle language & persist
        val nextLang = if (gameLanguage == GameLanguage.ENGLISH) GameLanguage.JAPANESE else GameLanguage.ENGLISH
        gameLanguage = nextLang
        WordsWorthLogic.saveGameLanguage(context, nextLang)

        // 3. Restore target language state
        val targetBoard = if (nextLang == GameLanguage.ENGLISH) boardEn else boardJa
        board = copyBoard(targetBoard)
        boardSize = targetBoard.size
        elapsedSeconds = if (nextLang == GameLanguage.ENGLISH) elapsedSecondsEn else elapsedSecondsJa
        validatedWordsHistory.clear()
        validatedWordsHistory.addAll(if (nextLang == GameLanguage.ENGLISH) validatedWordsHistoryEn else validatedWordsHistoryJa)

        focusedCell = null
        pendingRomaji = ""
        lastPlacedCell = null
        selectedLetter = if (nextLang == GameLanguage.ENGLISH) 'A' else null

        if (isDictionaryLoaded) {
            tallyResult = evaluateBoard()
        }
    }

    // -------------------------------------------------------------------------
    // Timer & Time Attack State
    // -------------------------------------------------------------------------

    private var timerJob: Job? = null

    private fun startTimerLoop() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (isTimerRunning) {
                delay(1000L)
                elapsedSeconds += 1
                val limitSeconds = timeAttackMinutes?.times(60)
                if (limitSeconds != null && elapsedSeconds >= limitSeconds) {
                    elapsedSeconds = limitSeconds
                    isTimerRunning = false
                    isBoardLocked = true
                    focusedCell = null
                    if (isDictionaryLoaded) {
                        tallyResult = evaluateBoard()
                    }
                    break
                }
            }
        }
    }

    private fun stopTimerLoop() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Elapsed seconds on the game timer (starts counting when the first letter is placed). */
    var elapsedSeconds by mutableIntStateOf(0)

    /** True while the per-second game timer coroutine is actively ticking. */
    private val _isTimerRunning = mutableStateOf(false)
    var isTimerRunning: Boolean
        get() = _isTimerRunning.value
        set(value) {
            _isTimerRunning.value = value
            if (value) {
                startTimerLoop()
            } else {
                stopTimerLoop()
            }
        }

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

    /** Currently highlighted letter tile in the rack. */
    var selectedLetter by mutableStateOf<Char?>('A')

    /** Pending uncommitted Romaji keystrokes (e.g., "k" or "ky") waiting for a vowel in Japanese. */
    var pendingRomaji by mutableStateOf("")

    /** Cell coordinates of the most recently placed/modified tile, used for modifier toggles after advance. */
    var lastPlacedCell by mutableStateOf<Pair<Int, Int>?>(null)

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

    /** Target pattern mode pending confirmation when switching during an active game. */
    var pendingPatternSwitch by mutableStateOf<Boolean?>(null)

    /** Formed word candidate selected by the player to be added to their persistent whitelist. */
    var pendingWhitelistWord by mutableStateOf<FormedWord?>(null)

    /** ID of the saved game currently resumed on the board (overwritten if saved again), or `null`. */
    var resumedGameId by mutableStateOf<Long?>(null)

    // -------------------------------------------------------------------------
    // Helper Queries & Focus Navigation
    // -------------------------------------------------------------------------

    /** Returns true if the active board contains at least one placed letter tile. */
    fun isGameInProgress(): Boolean {
        return board.any { row -> row.any { WordsWorthLogic.isLetterCell(it) } }
    }

    /** Finds the first non-blocked square on [board] in row-major order. */
    fun firstAvailableCell(b: Array<Array<Char?>>): Pair<Int, Int>? {
        for (r in b.indices) {
            for (c in b[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(b[r][c])) {
                    return r to c
                }
            }
        }
        return null
    }

    /** Finds the first unoccupied non-blocked square on [board], falling back to [firstAvailableCell]. */
    fun firstEmptyOrAvailableCell(b: Array<Array<Char?>>): Pair<Int, Int>? {
        for (r in b.indices) {
            for (c in b[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(b[r][c]) && b[r][c] == null) {
                    return r to c
                }
            }
        }
        return firstAvailableCell(b)
    }

    /** Clears the active board to an all-blank grid of the current [boardSize]. */
    fun applyAllBlank() {
        commitPendingRomaji()
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
        pendingRomaji = ""
        lastPlacedCell = null
    }

    /** Generates a new random-pattern board of the current [boardSize] with blacked-out squares. */
    fun applyRandomPattern() {
        commitPendingRomaji()
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
        pendingRomaji = ""
        lastPlacedCell = null
    }

    /** Handles tapping the "All-blank" pattern button. */
    fun selectAllBlank() {
        if (isGameInProgress()) {
            pendingPatternSwitch = false
        } else {
            applyAllBlank()
        }
    }

    /** Handles tapping the "Random pattern" button. */
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
        commitPendingRomaji()
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
        pendingRomaji = ""
        lastPlacedCell = null
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
                    copy[pr][pc]?.let {
                        if (WordsWorthLogic.isJapaneseChar(it)) it == prev.word[idx] else it.uppercaseChar() == prev.word[idx]
                    } == true
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
        commitPendingRomaji()
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

    fun getNextOpenCell(row: Int, col: Int, direction: WordDirection = focusDirection): Pair<Int, Int>? {
        val size = board.size
        return when (direction) {
            WordDirection.HORIZONTAL -> {
                val nextC = col + 1
                if (nextC < size && !WordsWorthLogic.isBlockedCell(board[row][nextC])) {
                    row to nextC
                } else null
            }
            WordDirection.VERTICAL -> {
                val nextR = row + 1
                if (nextR < size && !WordsWorthLogic.isBlockedCell(board[nextR][col])) {
                    nextR to col
                } else null
            }
        }
    }

    /** Commits any pending buffer characters (e.g. pending "n" -> 'ん') when tapping away or validating. */
    fun commitPendingRomaji() {
        if (pendingRomaji.isEmpty()) return
        val target = focusedCell ?: return
        val (r, c) = target
        val kanaList = KanaConverter.commitBuffer(pendingRomaji)
        pendingRomaji = ""
        if (kanaList.isNotEmpty()) {
            var currR = r
            var currC = c
            for (kana in kanaList) {
                updateCell(currR, currC, kana)
                lastPlacedCell = currR to currC
                val next = getNextOpenCell(currR, currC, focusDirection)
                if (next != null) {
                    currR = next.first
                    currC = next.second
                }
            }
            focusedCell = currR to currC
        }
    }

    /**
     * Handles Romaji letter input ('A'..'Z') from hardware or on-screen keyboard in Japanese mode.
     */
    fun onRomajiKeyInput(keyChar: Char) {
        if (isBoardLocked) return
        selectedLetter = keyChar.uppercaseChar()
        val start = focusedCell ?: firstAvailableCell(board) ?: return
        val (r, c) = start
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(r)?.getOrNull(c))) return

        val feedResult = KanaConverter.feedRomaji(pendingRomaji, keyChar)
        pendingRomaji = feedResult.remainingBuffer

        if (feedResult.producedKana.isNotEmpty()) {
            var currR = r
            var currC = c
            for (kana in feedResult.producedKana) {
                updateCell(currR, currC, kana)
                lastPlacedCell = currR to currC
                val next = getNextOpenCell(currR, currC, focusDirection)
                if (next != null) {
                    currR = next.first
                    currC = next.second
                }
            }
            focusedCell = currR to currC
        }
    }

    /**
     * Handles tapping the '小゛゜' modifier key in Japanese mode:
     * - Cycles the character through small, voiced (dakuten), and semi-voiced (handakuten) variants.
     */
    fun onModifierKeyInput() {
        if (isBoardLocked) return
        if (pendingRomaji.isNotEmpty()) {
            commitPendingRomaji()
        }
        val target = focusedCell ?: return
        val (r, c) = target
        val currentCh = board[r][c]
        if (currentCh != null && WordsWorthLogic.isLetterCell(currentCh)) {
            val modified = KanaConverter.cycleModifier(currentCh)
            updateCell(r, c, modified)
        } else {
            val last = lastPlacedCell
            if (last != null && WordsWorthLogic.isLetterCell(board[last.first][last.second])) {
                val modified = KanaConverter.cycleModifier(board[last.first][last.second]!!)
                updateCell(last.first, last.second, modified)
            } else {
                updateCell(r, c, 'っ')
                lastPlacedCell = r to c
                advanceFocusFrom(r, c, focusDirection)
            }
        }
    }

    /**
     * Erases pending buffer or current cell, or steps back and erases.
     */
    fun onClearInput() {
        if (isBoardLocked) return
        if (pendingRomaji.isNotEmpty()) {
            pendingRomaji = pendingRomaji.dropLast(1)
            return
        }
        val target = focusedCell ?: firstAvailableCell(board) ?: return
        val (r, c) = target
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
    }

    /**
     * Dispatches any rack key tap (letter or special modifier).
     */
    fun onKeyAction(key: String) {
        if (isBoardLocked) return
        when (key) {
            WordsWorthLogic.KEY_CLR -> onClearInput()
            WordsWorthLogic.KEY_MODIFIER, "小", "゛゜" -> {
                if (gameLanguage == GameLanguage.JAPANESE) {
                    onModifierKeyInput()
                }
            }
            WordsWorthLogic.KEY_CHOONPU, "-" -> {
                if (gameLanguage == GameLanguage.JAPANESE) {
                    onRomajiKeyInput('-')
                }
            }
            else -> {
                if (key.length == 1) {
                    val ch = key[0]
                    if (gameLanguage == GameLanguage.JAPANESE) {
                        if (ch in 'A'..'Z' || ch in 'a'..'z') {
                            onRomajiKeyInput(ch)
                        } else {
                            val target = focusedCell ?: firstAvailableCell(board) ?: return
                            updateCell(target.first, target.second, ch)
                            lastPlacedCell = target
                            advanceFocusFrom(target.first, target.second, focusDirection)
                        }
                    } else {
                        if (ch in 'A'..'Z' || ch in 'a'..'z') {
                            val target = focusedCell ?: firstAvailableCell(board) ?: return
                            updateCell(target.first, target.second, ch.uppercaseChar())
                            advanceFocusFrom(target.first, target.second, focusDirection)
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles a letter or `CLR` (`null`) input from either the on-screen rack or a hardware keyboard.
     */
    fun onKeyboardInput(letter: Char?): Pair<Pair<Int, Int>, Char?>? {
        if (letter == null) {
            onClearInput()
            return null
        }
        if (gameLanguage == GameLanguage.JAPANESE) {
            if (letter in 'A'..'Z' || letter in 'a'..'z' || letter == '-') {
                onRomajiKeyInput(letter)
                return null
            }
            val target = focusedCell ?: firstAvailableCell(board) ?: return null
            val (r, c) = target
            val old = board[r][c]
            updateCell(r, c, letter)
            lastPlacedCell = r to c
            advanceFocusFrom(r, c, focusDirection)
            return (r to c) to old
        } else {
            val target = focusedCell ?: firstAvailableCell(board) ?: return null
            val (r, c) = target
            val old = board[r][c]
            updateCell(r, c, letter.uppercaseChar())
            advanceFocusFrom(r, c, focusDirection)
            return (r to c) to old
        }
    }

    /**
     * Completes a drag-and-drop gesture of [letter] onto `(row, col)`.
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
        val finalLetter = if (gameLanguage == GameLanguage.ENGLISH) letter.uppercaseChar() else letter
        updateCell(row, col, finalLetter)
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
     * - `A`–`Z`: Places the typed letter (or converts Romaji to Kana) and advances cursor focus.
     * - `Backspace` / `Delete`: Clears the focused or preceding square (or pending Romaji buffer).
     * - `Space` / `Tab`: Commits pending Romaji or toggles Across (`→`) and Down (`↓`) cursor direction.
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
            code == KeyEvent.KEYCODE_MINUS ||
            code == KeyEvent.KEYCODE_NUMPAD_SUBTRACT ||
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
                    if (gameLanguage == GameLanguage.JAPANESE) {
                        onRomajiKeyInput(letter)
                    } else {
                        val target = focusedCell ?: firstAvailableCell(board) ?: return true
                        updateCell(target.first, target.second, letter)
                        advanceFocusFrom(target.first, target.second, focusDirection)
                    }
                }
                code == KeyEvent.KEYCODE_MINUS || code == KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                    if (gameLanguage == GameLanguage.JAPANESE) {
                        onRomajiKeyInput('-')
                    }
                }
                code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL -> {
                    onClearInput()
                }
                code == KeyEvent.KEYCODE_SPACE || code == KeyEvent.KEYCODE_TAB -> {
                    if (pendingRomaji.isNotEmpty()) {
                        commitPendingRomaji()
                    } else if (!isBoardLocked) {
                        focusDirection = if (focusDirection == WordDirection.HORIZONTAL) {
                            WordDirection.VERTICAL
                        } else {
                            WordDirection.HORIZONTAL
                        }
                    }
                }
                code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                    commitPendingRomaji()
                    moveFocusByArrow(0, -1, WordDirection.HORIZONTAL)
                }
                code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    commitPendingRomaji()
                    moveFocusByArrow(0, 1, WordDirection.HORIZONTAL)
                }
                code == KeyEvent.KEYCODE_DPAD_UP -> {
                    commitPendingRomaji()
                    moveFocusByArrow(-1, 0, WordDirection.VERTICAL)
                }
                code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                    commitPendingRomaji()
                    moveFocusByArrow(1, 0, WordDirection.VERTICAL)
                }
                code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    commitPendingRomaji()
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

            // Restore language from saved entry
            val entryLang = entry.language
            if (entryLang != gameLanguage) {
                gameLanguage = entryLang
                WordsWorthLogic.saveGameLanguage(context, entryLang)
            }

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
        commitPendingRomaji()
        isRandomPatternMode = false
        val newBoard = WordsWorthLogic.exampleBoard(boardSize, gameLanguage)
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

    /** Resets the active board and timer for the current game language. */
    fun resetBoardAndTimer() {
        commitPendingRomaji()
        val newBoard = if (isRandomPatternMode) {
            WordsWorthLogic.generateRandomPatternBoard(boardSize)
        } else {
            WordsWorthLogic.createBlankBoard(boardSize)
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
        pendingRomaji = ""
        lastPlacedCell = null
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
            tallyResult = evaluateBoard(board, dictionary, newMode, allowDuplicateWordPoints)
        }
    }

    /**
     * Updates whether duplicate instances of the same valid word receive points after the first time,
     * saves the preference, and immediately recomputes [tallyResult] if a validated board is displayed.
     */
    fun changeAllowDuplicateWordPoints(allow: Boolean) {
        allowDuplicateWordPoints = allow
        WordsWorthLogic.saveAllowDuplicateWordPoints(context, allow)
        if (tallyResult != null && isDictionaryLoaded) {
            tallyResult = evaluateBoard(allowDuplicates = allow)
        }
    }

    /**
     * Updates the user's [JapaneseVerbRule] preference, saves it to SharedPreferences, and
     * immediately recomputes [tallyResult] if a validated board is currently displayed.
     */
    fun changeJapaneseVerbRule(newRule: JapaneseVerbRule) {
        japaneseVerbRule = newRule
        WordsWorthLogic.saveJapaneseVerbRule(context, newRule)
        if (tallyResult != null && isDictionaryLoaded) {
            tallyResult = evaluateBoard(verbRule = newRule)
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
        val wordToAdd = if (gameLanguage == GameLanguage.ENGLISH) formedWord.word.uppercase() else formedWord.word
        pendingWhitelistWord = null
        if (gameLanguage == GameLanguage.ENGLISH) {
            englishDictionary = englishDictionary + wordToAdd
        } else {
            japaneseDictionary = japaneseDictionary + wordToAdd
        }
        tallyResult = evaluateBoard(board, dictionary, scoringMode)
        coroutineScope.launch(Dispatchers.IO) {
            WordsWorthLogic.addWordToWhitelist(context, wordToAdd, gameLanguage)
        }
    }

    /** Saves the current board and score to "Previous Games" and resets the active board. */
    fun saveAndFinishCurrentGame() {
        showQuitDialog = false
        val currentTally = tallyResult ?: evaluateBoard()
        val score = currentTally.totalScore
        val elapsed = elapsedSeconds

        coroutineScope.launch(Dispatchers.IO) {
            val newEntry = SavedGamesManager.saveGame(
                context = context,
                board = board,
                tallyResult = currentTally,
                elapsedSeconds = elapsed,
                replaceId = resumedGameId,
                language = gameLanguage,
                japaneseVerbRule = japaneseVerbRule,
                verbDictionary = japaneseVerbDictionary
            )
            withContext(Dispatchers.Main) {
                resumedGameId?.let { id ->
                    val idx = savedGames.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        savedGames.removeAt(idx)
                    }
                }
                savedGames.add(0, newEntry)
                resetBoardAndTimer()
            }
        }
    }

    /** Deletes [pendingDeleteEntry] or [entryToDelete] from disk and from [savedGames]. */
    fun confirmDeleteSavedGame(entryToDelete: SavedGameEntry? = null) {
        val entry = entryToDelete ?: pendingDeleteEntry ?: return
        pendingDeleteEntry = null
        coroutineScope.launch(Dispatchers.IO) {
            SavedGamesManager.deleteSavedGame(context, entry.id)
            withContext(Dispatchers.Main) {
                savedGames.removeAll { it.id == entry.id }
            }
        }
    }

    /**
     * Evaluates all horizontal and vertical words on [evalBoard] against [dict] using [mode],
     * and updates [validatedWordsHistory] with any new valid words formed.
     */
    fun evaluateBoard(
        evalBoard: Array<Array<Char?>> = board,
        dict: Set<String> = dictionary,
        mode: ScoringMode = scoringMode,
        allowDuplicates: Boolean = allowDuplicateWordPoints,
        verbRule: JapaneseVerbRule = japaneseVerbRule,
        verbs: Set<String> = japaneseVerbDictionary
    ): TallyResult {
        val result = WordsWorthLogic.tallyBoard(
            board = evalBoard,
            dictionary = dict,
            scoringMode = mode,
            previouslyValidatedWords = validatedWordsHistory,
            allowDuplicateWordPoints = allowDuplicates,
            language = gameLanguage,
            japaneseVerbRule = verbRule,
            verbDictionary = verbs
        )
        // Record all valid words formed during this session into history
        for (word in result.validWords) {
            if (validatedWordsHistory.none { it.direction == word.direction && it.cells == word.cells && it.word == word.word }) {
                validatedWordsHistory.add(word)
            }
        }
        return result
    }
}

/**
 * Retrieves the configuration-safe [WordsWorthGameState] ViewModel instance and registers
 * hardware keyboard event dispatch with [MainActivity].
 */
@Composable
fun rememberWordsWorthGameState(): WordsWorthGameState {
    val state: WordsWorthGameState = viewModel()
    val context = LocalContext.current

    // Register hardware/Bluetooth keyboard handler with the hosting MainActivity
    val latestKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        state.handleHardwareKeyEvent(event)
    }
    DisposableEffect(context) {
        val activity = context as? MainActivity
        activity?.hardwareKeyHandler = latestKeyHandler
        onDispose {
            activity?.hardwareKeyHandler = null
        }
    }

    return state
}
