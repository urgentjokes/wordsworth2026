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
import kotlinx.coroutines.async
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
            val dictEnDeferred = async(Dispatchers.IO) { WordsWorthLogic.loadDictionary(context, GameLanguage.ENGLISH) }
            val dictJaDeferred = async(Dispatchers.IO) { WordsWorthLogic.loadDictionary(context, GameLanguage.JAPANESE) }
            val verbsDeferred = async(Dispatchers.IO) { WordsWorthLogic.loadJapaneseVerbDictionary(context) }
            val gamesDeferred = async(Dispatchers.IO) { SavedGamesManager.loadSavedGames(context) }
            val activeSessionDeferred = async(Dispatchers.IO) { SavedGamesManager.loadActiveSession(context) }

            englishDictionary = dictEnDeferred.await()
            japaneseDictionary = dictJaDeferred.await()
            japaneseVerbDictionary = verbsDeferred.await()
            val loadedGames = gamesDeferred.await()
            savedGames.clear()
            savedGames.addAll(loadedGames)
            isDictionaryLoaded = true

            val activeSession = activeSessionDeferred.await()
            if (activeSession != null) {
                boardEn = activeSession.enSession.board
                sprinkledCellsEn = activeSession.enSession.sprinkledCells
                elapsedSecondsEn = activeSession.enSession.elapsedSeconds
                validatedWordsHistoryEn.clear()
                validatedWordsHistoryEn.addAll(activeSession.enSession.validatedWords)

                boardJa = activeSession.jaSession.board
                sprinkledCellsJa = activeSession.jaSession.sprinkledCells
                elapsedSecondsJa = activeSession.jaSession.elapsedSeconds
                validatedWordsHistoryJa.clear()
                validatedWordsHistoryJa.addAll(activeSession.jaSession.validatedWords)

                boardSize = activeSession.boardSize
                isRandomPatternMode = activeSession.isRandomPatternMode
                resumedGameId = activeSession.resumedGameId
                timeAttackMinutes = activeSession.timeAttackMinutes
                isBoardLocked = activeSession.isBoardLocked
                gameLanguage = activeSession.activeLanguage

                val targetBoard = if (gameLanguage == GameLanguage.ENGLISH) boardEn else boardJa
                val targetSprinkled = if (gameLanguage == GameLanguage.ENGLISH) sprinkledCellsEn else sprinkledCellsJa
                val targetElapsed = if (gameLanguage == GameLanguage.ENGLISH) elapsedSecondsEn else elapsedSecondsJa

                board = copyBoard(targetBoard)
                sprinkledCells = targetSprinkled
                elapsedSeconds = targetElapsed
                validatedWordsHistory.clear()
                validatedWordsHistory.addAll(if (gameLanguage == GameLanguage.ENGLISH) validatedWordsHistoryEn else validatedWordsHistoryJa)

                if (targetElapsed > 0 && !isBoardLocked) {
                    isTimerRunning = true
                }
            }

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

    /** Controls whether keyboard taps and drags produce subtle haptic feedback vibration. */
    var isHapticFeedbackEnabled by mutableStateOf(WordsWorthLogic.loadHapticFeedbackEnabled(context))

    /** Loaded Japanese verb dictionary from all_japanese_verbs_hiragana.txt. */
    var japaneseVerbDictionary by mutableStateOf<Set<String>>(emptySet())

    /** Active letter sprinkle density mode ([LetterSprinkleMode]). */
    var letterSprinkleMode by mutableStateOf(WordsWorthLogic.loadLetterSprinkleMode(context))

    /**
     * Validated words recorded earlier in the current game session.
     * Used when [scoringMode] is [ScoringMode.CUMULATIVE_EXTENDED] so extending a previously
     * validated word retains the base word's points, and when [allowDuplicateWordPoints] is `false`
     * to identify which duplicate instance was placed first.
     */
    val validatedWordsHistory = mutableStateListOf<FormedWord>()

    // Preserved boards, sprinkled cells, and elapsed time per language to prevent loss when toggling languages
    private var sprinkledCellsEn: Set<Pair<Int, Int>> = emptySet()
    private var sprinkledCellsJa: Set<Pair<Int, Int>> = emptySet()

    private var boardEn: Array<Array<Char?>> = WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE).also {
        sprinkledCellsEn = WordsWorthLogic.sprinkleLetters(it, letterSprinkleMode, GameLanguage.ENGLISH)
    }
    private var boardJa: Array<Array<Char?>> = WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE).also {
        sprinkledCellsJa = WordsWorthLogic.sprinkleLetters(it, letterSprinkleMode, GameLanguage.JAPANESE)
    }
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

    /** Coordinates `(row, col)` of squares currently populated with non-deletable sprinkled letters. */
    var sprinkledCells by mutableStateOf<Set<Pair<Int, Int>>>(
        if (gameLanguage == GameLanguage.ENGLISH) sprinkledCellsEn else sprinkledCellsJa
    )

    /** Returns `true` if the square at ([row], [col]) contains a non-deletable sprinkled letter. */
    fun isSprinkledCell(row: Int, col: Int): Boolean = (row to col) in sprinkledCells

    /** Current 2D crossword board matrix (`null` = empty, `'#'` = blocked, letter/kana = placed). */
    var board by mutableStateOf(
        if (gameLanguage == GameLanguage.ENGLISH) boardEn else boardJa
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
            sprinkledCellsEn = sprinkledCells
            elapsedSecondsEn = elapsedSeconds
            validatedWordsHistoryEn.clear()
            validatedWordsHistoryEn.addAll(validatedWordsHistory)
        } else {
            boardJa = copyBoard(board)
            sprinkledCellsJa = sprinkledCells
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
        var targetSprinkled = if (nextLang == GameLanguage.ENGLISH) sprinkledCellsEn else sprinkledCellsJa
        val hasTargetLetters = targetBoard.any { row -> row.any { WordsWorthLogic.isLetterCell(it) } }
        val boardToRestore = copyBoard(targetBoard)
        if (!hasTargetLetters && letterSprinkleMode != LetterSprinkleMode.NONE) {
            targetSprinkled = WordsWorthLogic.sprinkleLetters(boardToRestore, letterSprinkleMode, nextLang)
            if (nextLang == GameLanguage.ENGLISH) sprinkledCellsEn = targetSprinkled else sprinkledCellsJa = targetSprinkled
        }
        board = boardToRestore
        sprinkledCells = targetSprinkled
        boardSize = boardToRestore.size
        elapsedSeconds = if (nextLang == GameLanguage.ENGLISH) elapsedSecondsEn else elapsedSecondsJa
        validatedWordsHistory.clear()
        validatedWordsHistory.addAll(if (nextLang == GameLanguage.ENGLISH) validatedWordsHistoryEn else validatedWordsHistoryJa)

        focusedCell = firstEmptyOrAvailableCell(boardToRestore)
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
    var focusedCell by mutableStateOf<Pair<Int, Int>?>(firstEmptyOrAvailableCell(board))

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

    /** Finds the first unoccupied non-blocked square on [board] that is not a sprinkled letter, falling back to [firstAvailableCell]. */
    fun firstEmptyOrAvailableCell(b: Array<Array<Char?>>): Pair<Int, Int>? {
        for (r in b.indices) {
            for (c in b[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(b[r][c]) && b[r][c] == null && !isSprinkledCell(r, c)) {
                    return r to c
                }
            }
        }
        for (r in b.indices) {
            for (c in b[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(b[r][c]) && !isSprinkledCell(r, c)) {
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
        val baseBoard = WordsWorthLogic.createBlankBoard(boardSize)
        val newSprinkled = WordsWorthLogic.sprinkleLetters(baseBoard, letterSprinkleMode, gameLanguage)
        board = baseBoard
        sprinkledCells = newSprinkled
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstEmptyOrAvailableCell(baseBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = if (isDictionaryLoaded && letterSprinkleMode != LetterSprinkleMode.NONE) evaluateBoard(baseBoard) else null
        isTimerRunning = false
        elapsedSeconds = 0
        pendingRomaji = ""
        lastPlacedCell = null
        clearActiveSession()
    }

    /** Generates a new random-pattern board of the current [boardSize] with blacked-out squares. */
    fun applyRandomPattern() {
        commitPendingRomaji()
        isRandomPatternMode = true
        val baseBoard = WordsWorthLogic.generateRandomPatternBoard(boardSize)
        val newSprinkled = WordsWorthLogic.sprinkleLetters(baseBoard, letterSprinkleMode, gameLanguage)
        board = baseBoard
        sprinkledCells = newSprinkled
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstEmptyOrAvailableCell(baseBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = if (isDictionaryLoaded && letterSprinkleMode != LetterSprinkleMode.NONE) evaluateBoard(baseBoard) else null
        isTimerRunning = false
        elapsedSeconds = 0
        pendingRomaji = ""
        lastPlacedCell = null
        clearActiveSession()
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
        val baseBoard = if (isRandomPatternMode) {
            WordsWorthLogic.generateRandomPatternBoard(newSize)
        } else {
            WordsWorthLogic.createBlankBoard(newSize)
        }
        val newSprinkled = WordsWorthLogic.sprinkleLetters(baseBoard, letterSprinkleMode, gameLanguage)
        board = baseBoard
        sprinkledCells = newSprinkled
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstEmptyOrAvailableCell(baseBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = if (isDictionaryLoaded && letterSprinkleMode != LetterSprinkleMode.NONE) evaluateBoard(baseBoard) else null
        isTimerRunning = false
        elapsedSeconds = 0
        pendingRomaji = ""
        lastPlacedCell = null
        clearActiveSession()
    }

    /**
     * Places or clears a [letter] at `(row, col)` on the board, pruning any invalidated
     * entries from [validatedWordsHistory] and starting the timer on the first placed letter.
     */
    fun updateCell(row: Int, col: Int, letter: Char?) {
        if (isBoardLocked) return
        if (isSprinkledCell(row, col)) return
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

    /** Finds the next non-blocked, non-sprinkled square along [direction] from `(row, col)`. */
    fun getNextEditableCell(row: Int, col: Int, direction: WordDirection = focusDirection): Pair<Int, Int>? {
        val size = board.size
        var r = row
        var c = col
        while (true) {
            when (direction) {
                WordDirection.HORIZONTAL -> c++
                WordDirection.VERTICAL -> r++
            }
            if (r !in 0 until size || c !in 0 until size) return null
            if (WordsWorthLogic.isBlockedCell(board[r][c])) return null
            if (!isSprinkledCell(r, c)) {
                return r to c
            }
        }
    }

    /** Advances [focusedCell] forward along [direction], skipping any sprinkled letters. */
    fun advanceFocusFrom(row: Int, col: Int, direction: WordDirection = focusDirection) {
        val next = getNextEditableCell(row, col, direction)
        if (next != null) {
            focusedCell = next
        } else {
            focusedCell = row to col
        }
    }

    fun getNextOpenCell(row: Int, col: Int, direction: WordDirection = focusDirection): Pair<Int, Int>? =
        getNextEditableCell(row, col, direction)

    /** Returns the current focused cell if editable, or the next editable cell along [focusDirection]. */
    fun getEditableTargetCell(): Pair<Int, Int>? {
        val curr = focusedCell ?: return firstEmptyOrAvailableCell(board)
        val (r, c) = curr
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(r)?.getOrNull(c))) {
            return firstEmptyOrAvailableCell(board)
        }
        if (isSprinkledCell(r, c)) {
            return getNextEditableCell(r, c, focusDirection) ?: firstEmptyOrAvailableCell(board)
        }
        return curr
    }

    /** Commits any pending buffer characters (e.g. pending "n" -> 'ん') when tapping away or validating. */
    fun commitPendingRomaji() {
        if (pendingRomaji.isEmpty()) return
        val target = getEditableTargetCell() ?: return
        val (r, c) = target
        val kanaList = KanaConverter.commitBuffer(pendingRomaji)
        pendingRomaji = ""
        if (kanaList.isNotEmpty()) {
            var currR = r
            var currC = c
            for (kana in kanaList) {
                updateCell(currR, currC, kana)
                lastPlacedCell = currR to currC
                val next = getNextEditableCell(currR, currC, focusDirection)
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
        val start = getEditableTargetCell() ?: return
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
                val next = getNextEditableCell(currR, currC, focusDirection)
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
        if (currentCh != null && WordsWorthLogic.isLetterCell(currentCh) && !isSprinkledCell(r, c)) {
            val modified = KanaConverter.cycleModifier(currentCh)
            updateCell(r, c, modified)
        } else {
            val last = lastPlacedCell
            if (last != null && !isSprinkledCell(last.first, last.second) && WordsWorthLogic.isLetterCell(board[last.first][last.second])) {
                val modified = KanaConverter.cycleModifier(board[last.first][last.second]!!)
                updateCell(last.first, last.second, modified)
            } else if (!isSprinkledCell(r, c)) {
                updateCell(r, c, 'っ')
                lastPlacedCell = r to c
                advanceFocusFrom(r, c, focusDirection)
            }
        }
    }

    /**
     * Erases pending buffer or current cell, or steps back skipping sprinkled cells and erases.
     */
    fun onClearInput() {
        if (isBoardLocked) return
        if (pendingRomaji.isNotEmpty()) {
            pendingRomaji = pendingRomaji.dropLast(1)
            return
        }
        val target = focusedCell ?: firstEmptyOrAvailableCell(board) ?: return
        val (r, c) = target

        // If currently focused on an editable cell containing a player-placed letter, clear it.
        if (!isSprinkledCell(r, c) && WordsWorthLogic.isLetterCell(board[r][c])) {
            updateCell(r, c, null)
            focusedCell = r to c
            return
        }

        // Otherwise (focused cell is empty or sprinkled), step backward skipping sprinkled cells
        // until an editable cell is found.
        val size = board.size
        var currR = r
        var currC = c
        while (true) {
            when (focusDirection) {
                WordDirection.HORIZONTAL -> currC--
                WordDirection.VERTICAL -> currR--
            }
            if (currR !in 0 until size || currC !in 0 until size) return
            if (WordsWorthLogic.isBlockedCell(board[currR][currC])) return
            if (!isSprinkledCell(currR, currC)) {
                focusedCell = currR to currC
                if (WordsWorthLogic.isLetterCell(board[currR][currC])) {
                    updateCell(currR, currC, null)
                }
                return
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
                            val target = getEditableTargetCell() ?: return
                            updateCell(target.first, target.second, ch)
                            lastPlacedCell = target
                            advanceFocusFrom(target.first, target.second, focusDirection)
                        }
                    } else {
                        if (ch in 'A'..'Z' || ch in 'a'..'z') {
                            val target = getEditableTargetCell() ?: return
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
            val target = getEditableTargetCell() ?: return null
            val (r, c) = target
            val old = board[r][c]
            updateCell(r, c, letter)
            lastPlacedCell = r to c
            advanceFocusFrom(r, c, focusDirection)
            return (r to c) to old
        } else {
            val target = getEditableTargetCell() ?: return null
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
        if (isSprinkledCell(row, col)) {
            undoPlacement?.let { (placedCell, oldChar) ->
                updateCell(placedCell.first, placedCell.second, oldChar)
            }
            return
        }
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
                        val target = getEditableTargetCell() ?: return true
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
            sprinkledCells = entry.sprinkledCells

            // Restore language from saved entry
            val entryLang = entry.language
            if (entryLang != gameLanguage) {
                gameLanguage = entryLang
                WordsWorthLogic.saveGameLanguage(context, entryLang)
            }

            // Restore scoring mode & verb rule from saved entry so tallying matches the game's rules
            scoringMode = entry.scoringMode
            japaneseVerbRule = entry.japaneseVerbRule

            if (entryLang == GameLanguage.ENGLISH) {
                boardEn = copyBoard(restoredBoard)
                sprinkledCellsEn = entry.sprinkledCells
                elapsedSecondsEn = entry.elapsedSeconds
                validatedWordsHistoryEn.clear()
                validatedWordsHistoryEn.addAll(entry.validatedWords)
            } else {
                boardJa = copyBoard(restoredBoard)
                sprinkledCellsJa = entry.sprinkledCells
                elapsedSecondsJa = entry.elapsedSeconds
                validatedWordsHistoryJa.clear()
                validatedWordsHistoryJa.addAll(entry.validatedWords)
            }

            focusedCell = firstEmptyOrAvailableCell(restoredBoard)
            focusDirection = WordDirection.HORIZONTAL
            elapsedSeconds = entry.elapsedSeconds
            isTimerRunning = true
            if (isDictionaryLoaded) {
                val evaluated = evaluateBoard(
                    evalBoard = restoredBoard,
                    mode = entry.scoringMode,
                    verbRule = entry.japaneseVerbRule
                )
                tallyResult = if (entry.score > evaluated.totalScore && entry.validatedWords.isNotEmpty()) {
                    // For legacy games where sprinkledCells or rawPoints were not serialized to JSON,
                    // retain the authentic recorded score and validated words list from the saved entry
                    evaluated.copy(
                        totalScore = entry.score,
                        words = entry.validatedWords
                    )
                } else {
                    evaluated
                }
            } else {
                val cellMap = mutableMapOf<Pair<Int, Int>, CellValidation>()
                for (word in entry.validatedWords) {
                    for (cell in word.cells) {
                        cellMap[cell] = CellValidation.VALID
                    }
                }
                tallyResult = TallyResult(
                    totalScore = entry.score,
                    words = entry.validatedWords,
                    isolatedCells = emptyList(),
                    cellValidations = cellMap
                )
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
        sprinkledCells = emptySet()
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
        val baseBoard = if (isRandomPatternMode) {
            WordsWorthLogic.generateRandomPatternBoard(boardSize)
        } else {
            WordsWorthLogic.createBlankBoard(boardSize)
        }
        val newSprinkled = WordsWorthLogic.sprinkleLetters(baseBoard, letterSprinkleMode, gameLanguage)
        board = baseBoard
        sprinkledCells = newSprinkled
        resumedGameId = null
        validatedWordsHistory.clear()
        isBoardLocked = false
        focusedCell = firstEmptyOrAvailableCell(baseBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = if (isDictionaryLoaded && letterSprinkleMode != LetterSprinkleMode.NONE) evaluateBoard(baseBoard) else null
        isTimerRunning = false
        elapsedSeconds = 0
        pendingRomaji = ""
        lastPlacedCell = null
        clearActiveSession()
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

    /** Updates the keyboard haptic feedback preference and saves it to SharedPreferences. */
    fun changeHapticFeedbackEnabled(enabled: Boolean) {
        isHapticFeedbackEnabled = enabled
        WordsWorthLogic.saveHapticFeedbackEnabled(context, enabled)
    }

    /** Updates the letter sprinkle density preference and saves it to SharedPreferences. */
    fun changeLetterSprinkleMode(newMode: LetterSprinkleMode) {
        letterSprinkleMode = newMode
        WordsWorthLogic.saveLetterSprinkleMode(context, newMode)
        // If the player has not started playing yet in this round, update the board immediately
        if (lastPlacedCell == null && elapsedSeconds == 0 && validatedWordsHistory.isEmpty() && !isBoardLocked) {
            resetBoardAndTimer()
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
                verbDictionary = japaneseVerbDictionary,
                sprinkledCells = sprinkledCells,
                scoringMode = scoringMode
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
            verbDictionary = verbs,
            sprinkledCells = sprinkledCells
        )
        // Record all valid words formed during this session into history
        for (word in result.validWords) {
            if (validatedWordsHistory.none { it.direction == word.direction && it.cells == word.cells && it.word == word.word }) {
                validatedWordsHistory.add(word)
            }
        }
        return result
    }

    /**
     * Persists the current in-progress board state, sprinkled letters, and elapsed timer
     * to disk so they survive unexpected process death while the app is in the background.
     */
    fun saveActiveSession() {
        // Sync active board state to language slot
        if (gameLanguage == GameLanguage.ENGLISH) {
            boardEn = copyBoard(board)
            sprinkledCellsEn = sprinkledCells
            elapsedSecondsEn = elapsedSeconds
            validatedWordsHistoryEn.clear()
            validatedWordsHistoryEn.addAll(validatedWordsHistory)
        } else {
            boardJa = copyBoard(board)
            sprinkledCellsJa = sprinkledCells
            elapsedSecondsJa = elapsedSeconds
            validatedWordsHistoryJa.clear()
            validatedWordsHistoryJa.addAll(validatedWordsHistory)
        }

        // Determine if there is any user progress worth saving
        val hasEnLetters = boardEn.indices.any { r ->
            boardEn[r].indices.any { c -> WordsWorthLogic.isLetterCell(boardEn[r][c]) && (r to c) !in sprinkledCellsEn }
        }
        val hasJaLetters = boardJa.indices.any { r ->
            boardJa[r].indices.any { c -> WordsWorthLogic.isLetterCell(boardJa[r][c]) && (r to c) !in sprinkledCellsJa }
        }
        val hasActiveProgress = hasEnLetters || hasJaLetters || elapsedSecondsEn > 0 || elapsedSecondsJa > 0

        if (!hasActiveProgress) {
            SavedGamesManager.clearActiveSession(context)
            return
        }

        val sessionState = ActiveSessionState(
            activeLanguage = gameLanguage,
            boardSize = boardSize,
            isRandomPatternMode = isRandomPatternMode,
            resumedGameId = resumedGameId,
            timeAttackMinutes = timeAttackMinutes,
            isBoardLocked = isBoardLocked,
            enSession = LanguageSessionState(
                board = boardEn,
                sprinkledCells = sprinkledCellsEn,
                elapsedSeconds = elapsedSecondsEn,
                validatedWords = validatedWordsHistoryEn.toList()
            ),
            jaSession = LanguageSessionState(
                board = boardJa,
                sprinkledCells = sprinkledCellsJa,
                elapsedSeconds = elapsedSecondsJa,
                validatedWords = validatedWordsHistoryJa.toList()
            )
        )
        SavedGamesManager.saveActiveSession(context, sessionState)
    }

    /**
     * Removes any persisted active session snapshot when the round is finished or reset.
     */
    fun clearActiveSession() {
        SavedGamesManager.clearActiveSession(context)
    }
}

/**
 * Retrieves the configuration-safe [WordsWorthGameState] ViewModel instance and registers
 * hardware keyboard event dispatch with [MainActivity].
 */
@Composable
fun rememberWordsWorthGameState(state: WordsWorthGameState = viewModel()): WordsWorthGameState {
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
