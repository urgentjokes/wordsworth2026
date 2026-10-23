package com.example.wordsworth

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wordsworth.ui.theme.WordsWorthTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    var hardwareKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hardwareKeyHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WordsWorthTheme {
                WordsWorthApp()
            }
        }
    }
}

fun formatTimer(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

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

@Composable
fun WordsWorthApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showSettingsScreen by remember { mutableStateOf(false) }
    var dictionary by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isDictionaryLoaded by remember { mutableStateOf(false) }
    val savedGames = remember { mutableStateListOf<SavedGameEntry>() }

    LaunchedEffect(Unit) {
        val loadedDict = withContext(Dispatchers.IO) {
            WordsWorthLogic.loadDictionary(context)
        }
        val loadedGames = withContext(Dispatchers.IO) {
            SavedGamesManager.loadSavedGames(context)
        }
        dictionary = loadedDict
        savedGames.clear()
        savedGames.addAll(loadedGames)
        isDictionaryLoaded = true
    }

    // Board size state: 6 (6x6), 9 (9x9), or 12 (12x12)
    var boardSize by remember { mutableIntStateOf(WordsWorthLogic.BOARD_SIZE) }

    // Board pattern mode: false = All-blank, true = Random pattern
    var isRandomPatternMode by remember { mutableStateOf(false) }

    // Keyboard layout preference: ALPHABETICAL or QWERTY
    var keyboardLayout by remember { mutableStateOf(WordsWorthLogic.loadKeyboardLayout(context)) }

    // NxN board state
    var board by remember {
        mutableStateOf(
            WordsWorthLogic.createBlankBoard(WordsWorthLogic.BOARD_SIZE)
        )
    }

    // Timer state: starts at 0:00 and begins counting when the first letter is placed
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Time Attack mode: null = standard untimed mode, or 1, 3, or 5 minutes
    var timeAttackMinutes by remember { mutableStateOf<Int?>(null) }
    var showTimeAttackDialog by remember { mutableStateOf(false) }
    var isBoardLocked by remember { mutableStateOf(false) }

    // Selected letter in rack ('A'..'Z'), or null for Eraser mode
    var selectedLetter by remember { mutableStateOf<Char?>('S') }

    fun firstAvailableCell(targetBoard: Array<Array<Char?>>): Pair<Int, Int>? {
        for (r in targetBoard.indices) {
            for (c in targetBoard[r].indices) {
                if (!WordsWorthLogic.isBlockedCell(targetBoard[r][c])) {
                    return r to c
                }
            }
        }
        return null
    }

    // Focused square on the board (cursor) and current word direction (HORIZONTAL or VERTICAL)
    var focusedCell by remember { mutableStateOf<Pair<Int, Int>?>(0 to 0) }
    var focusDirection by remember { mutableStateOf(WordDirection.HORIZONTAL) }

    // Tally result when "Validate" is clicked
    var tallyResult by remember { mutableStateOf<TallyResult?>(null) }

    LaunchedEffect(isTimerRunning, timeAttackMinutes) {
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
                    tallyResult = WordsWorthLogic.tallyBoard(board, dictionary)
                }
            }
        }
    }

    // Quit confirmation dialog state
    var showQuitDialog by remember { mutableStateOf(false) }

    // Optional enlarged preview of a saved board from the left column
    var previewSavedEntry by remember { mutableStateOf<SavedGameEntry?>(null) }

    // Entry pending deletion confirmation after press-and-hold
    var pendingDeleteEntry by remember { mutableStateOf<SavedGameEntry?>(null) }

    // Pending pattern switch confirmation when a game is in progress (false = All-blank, true = Random pattern)
    var pendingPatternSwitch by remember { mutableStateOf<Boolean?>(null) }

    // Invalid word pending whitelist confirmation after long-press
    var pendingWhitelistWord by remember { mutableStateOf<FormedWord?>(null) }

    // ID of the saved game currently resumed on the board (null if playing a brand-new game)
    var resumedGameId by remember { mutableStateOf<Long?>(null) }

    fun isGameInProgress(): Boolean {
        return isTimerRunning || board.any { row -> row.any { WordsWorthLogic.isLetterCell(it) } }
    }

    fun resetBoardAndTimer() {
        val size = board.size
        // Clear placed letters while preserving any blacked-out squares on the current board
        val newBoard = Array(size) { r ->
            Array(size) { c ->
                if (WordsWorthLogic.isBlockedCell(board[r][c])) WordsWorthLogic.BLOCKED_CELL else null
            }
        }
        board = newBoard
        resumedGameId = null
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    fun applyAllBlank() {
        isRandomPatternMode = false
        val newBoard = WordsWorthLogic.createBlankBoard(boardSize)
        board = newBoard
        resumedGameId = null
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    fun applyRandomPattern() {
        isRandomPatternMode = true
        val newBoard = WordsWorthLogic.generateRandomPatternBoard(boardSize)
        board = newBoard
        resumedGameId = null
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

    fun selectAllBlank() {
        if (isGameInProgress()) {
            pendingPatternSwitch = false
        } else {
            applyAllBlank()
        }
    }

    fun selectRandomPattern() {
        if (isGameInProgress()) {
            pendingPatternSwitch = true
        } else {
            applyRandomPattern()
        }
    }

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
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        tallyResult = null
        isTimerRunning = false
        elapsedSeconds = 0
    }

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
        tallyResult = tallyResult?.let { prev ->
            prev.copy(cellValidations = prev.cellValidations - (row to col))
        }

        // Start timer from 0:00 as soon as the first letter is placed
        if (letter != null && !isTimerRunning) {
            isTimerRunning = true
        }
    }

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

    fun onBoardCellTap(row: Int, col: Int) {
        if (isBoardLocked) return
        if (WordsWorthLogic.isBlockedCell(board.getOrNull(row)?.getOrNull(col))) {
            return
        }
        val current = focusedCell
        if (current != null && current.first == row && current.second == col) {
            // Tapping the already-focused square toggles Horizontal <-> Vertical cursor direction
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
            // Backspace / CLR behavior on the focused square
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

    val latestKeyHandler by rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        val isModalOrSettingsOpen = showSettingsScreen ||
            showQuitDialog ||
            showTimeAttackDialog ||
            pendingDeleteEntry != null ||
            pendingPatternSwitch != null ||
            pendingWhitelistWord != null ||
            previewSavedEntry != null

        if (isModalOrSettingsOpen) {
            false
        } else if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
            false
        } else {
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
                false
            } else {
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
                                tallyResult = WordsWorthLogic.tallyBoard(board, dictionary)
                            }
                        }
                    }
                }
                true
            }
        }
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

    fun firstEmptyOrAvailableCell(targetBoard: Array<Array<Char?>>): Pair<Int, Int>? {
        for (r in targetBoard.indices) {
            for (c in targetBoard[r].indices) {
                if (targetBoard[r][c] == null) {
                    return r to c
                }
            }
        }
        return firstAvailableCell(targetBoard)
    }

    fun resumeSavedGame(entry: SavedGameEntry) {
        val savedBoard = entry.board
        if (savedBoard != null && savedBoard.isNotEmpty()) {
            val size = savedBoard.size
            val restoredBoard = Array(size) { r ->
                Array(size) { c -> savedBoard[r][c] }
            }
            resumedGameId = entry.id
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
                tallyResult = WordsWorthLogic.tallyBoard(restoredBoard, dictionary)
            }
        } else {
            previewSavedEntry = entry
        }
    }

    fun loadExample() {
        isRandomPatternMode = false
        val newBoard = WordsWorthLogic.exampleBoard(boardSize)
        board = newBoard
        resumedGameId = null
        isBoardLocked = false
        focusedCell = firstAvailableCell(newBoard)
        focusDirection = WordDirection.HORIZONTAL
        if (!isTimerRunning) {
            isTimerRunning = true
        }
        if (isDictionaryLoaded) {
            tallyResult = WordsWorthLogic.tallyBoard(newBoard, dictionary)
        }
    }

    if (showSettingsScreen) {
        SettingsScreen(
            keyboardLayout = keyboardLayout,
            onChangeKeyboardLayout = { newLayout ->
                keyboardLayout = newLayout
                WordsWorthLogic.saveKeyboardLayout(context, newLayout)
            },
            savedGamesCount = savedGames.size,
            onClearSavedGames = {
                coroutineScope.launch(Dispatchers.IO) {
                    SavedGamesManager.clearAllSavedGames(context)
                    withContext(Dispatchers.Main) {
                        savedGames.clear()
                    }
                }
            },
            onBack = { showSettingsScreen = false }
        )
    } else {
        WordsWorthGameScreen(
            board = board,
            boardSize = boardSize,
            onChangeBoardSize = ::changeBoardSize,
            isRandomPatternMode = isRandomPatternMode,
            onSelectAllBlank = ::selectAllBlank,
            onSelectRandomPattern = ::selectRandomPattern,
            timeAttackMinutes = timeAttackMinutes,
            isBoardLocked = isBoardLocked,
            onClickTimeAttack = { showTimeAttackDialog = true },
            keyboardLayout = keyboardLayout,
            focusedCell = focusedCell,
            focusDirection = focusDirection,
            onBoardCellTap = ::onBoardCellTap,
            selectedLetter = selectedLetter,
            onKeyboardInput = ::onKeyboardInput,
            onDropLetterOnCell = { r, c, letter, undoPlacement ->
                if (!isBoardLocked) {
                    // If touch-down on the keyboard had already placed the letter into the previous focusedCell,
                    // restore that cell before dropping onto the dragged target cell
                    undoPlacement?.let { (placedCell, oldChar) ->
                        if (placedCell.first != r || placedCell.second != c) {
                            updateCell(placedCell.first, placedCell.second, oldChar)
                        }
                    }
                    val newDir = inferDirectionForCell(r, c, null)
                    focusDirection = newDir
                    updateCell(r, c, letter)
                    advanceFocusFrom(r, c, newDir)
                }
            },
            onUpdateCell = ::updateCell,
            onClearBoard = ::resetBoardAndTimer,
            onLoadExample = ::loadExample,
            elapsedSecondsProvider = { elapsedSeconds },
            isTimerRunning = isTimerRunning,
            isDictionaryLoaded = isDictionaryLoaded,
            tallyResult = tallyResult,
            onValidate = {
                tallyResult = WordsWorthLogic.tallyBoard(board, dictionary)
            },
            onRequestWhitelistWord = { formedWord ->
                pendingWhitelistWord = formedWord
            },
            onQuitClick = {
                showQuitDialog = true
            },
            onOpenSettings = {
                showSettingsScreen = true
            },
            savedGames = savedGames,
            onClickSavedGame = ::resumeSavedGame,
            onDeleteSavedGame = { entry ->
                pendingDeleteEntry = entry
            }
        )
    }

    if (showTimeAttackDialog) {
        AlertDialog(
            onDismissRequest = { showTimeAttackDialog = false },
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
                            val isSelected = timeAttackMinutes == mins
                            Button(
                                onClick = {
                                    showTimeAttackDialog = false
                                    timeAttackMinutes = mins
                                    resetBoardAndTimer()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) {
                                        Color(0xFF005B6E)
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
                    if (timeAttackMinutes != null) {
                        OutlinedButton(
                            onClick = {
                                showTimeAttackDialog = false
                                timeAttackMinutes = null
                                resetBoardAndTimer()
                            },
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
                TextButton(onClick = { showTimeAttackDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    pendingWhitelistWord?.let { fw ->
        AlertDialog(
            onDismissRequest = { pendingWhitelistWord = null },
            title = {
                Text(
                    text = "Whitelist \"${fw.word}\"?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Add \"${fw.word}\" (${fw.rawPoints} pts) to your whitelist so it is accepted as a valid word?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val wordToAdd = fw.word.uppercase()
                        pendingWhitelistWord = null
                        val updatedDict = dictionary + wordToAdd
                        dictionary = updatedDict
                        tallyResult = WordsWorthLogic.tallyBoard(board, updatedDict)
                        coroutineScope.launch(Dispatchers.IO) {
                            WordsWorthLogic.addWordToWhitelist(context, wordToAdd)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Whitelist", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingWhitelistWord = null }
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    pendingPatternSwitch?.let { switchToRandom ->
        AlertDialog(
            onDismissRequest = { pendingPatternSwitch = null },
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
                    onClick = {
                        pendingPatternSwitch = null
                        if (switchToRandom) {
                            applyRandomPattern()
                        } else {
                            applyAllBlank()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, start over", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingPatternSwitch = null }
                ) {
                    Text("No, resume game", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            title = {
                Text(
                    text = "Finish Current Game",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val previewScore = remember(board, dictionary) {
                    if (isDictionaryLoaded) {
                        WordsWorthLogic.tallyBoard(board, dictionary).totalScore
                    } else 0
                }
                Text(
                    text = "Current board score: $previewScore pts (Time: ${formatTimer(elapsedSeconds)}).\n\n" +
                        "How would you like to quit this round?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitDialog = false
                        val finalTally = WordsWorthLogic.tallyBoard(board, dictionary)
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Save final board and score", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showQuitDialog = false
                        resetBoardAndTimer()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Quit without saving", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
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
                    onClick = {
                        pendingDeleteEntry = null
                        savedGames.remove(entry)
                        coroutineScope.launch(Dispatchers.IO) {
                            SavedGamesManager.deleteSavedGame(context, entry.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDeleteEntry = null }
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    previewSavedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { previewSavedEntry = null },
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
                TextButton(onClick = { previewSavedEntry = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    // Thumbnails for the "All-blank" and "Random pattern" options
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

    // Drag-and-drop state in root coordinates
    var gridBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var keyboardOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var draggingLetter by remember { mutableStateOf<Char?>(null) }
    var dragPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val currentBoard by rememberUpdatedState(board)
    val currentBoardSize by rememberUpdatedState(boardSize)
    val currentIsBoardLocked by rememberUpdatedState(isBoardLocked)
    val currentOnBoardCellTap by rememberUpdatedState(onBoardCellTap)
    val currentOnKeyboardInput by rememberUpdatedState(onKeyboardInput)
    val currentOnDropLetterOnCell by rememberUpdatedState(onDropLetterOnCell)
    val currentOnUpdateCell by rememberUpdatedState(onUpdateCell)

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
                // Top Bar: App Title, Example/Clear, and Settings Gear Icon
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

                Spacer(modifier = Modifier.height(4.dp))

                // Main Content: Left-hand column of saved board mini-screenshots + scores, Right-hand game area
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // LEFT COLUMN: Running list of mini screenshots of the game board + score next to it
                    Card(
                        modifier = Modifier
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
                                color = Color(0xFFC8B496),
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

                    // RIGHT AREA:
                    // 1) Left of Board: High Score, Score, Timer (aligned with top row of squares) + Scrollable list of words & values
                    // 2) Center: Game Board + Keyboard + Validate/Quit
                    // 3) Right of Board: Board Size (6x6, 9x9, 12x12) + Pattern (All-blank, Random pattern) + Time Attack
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

                        // Column to the LEFT of the board, aligned with the top row of squares:
                        // Shows High Score, Score, and Timer at the top and a scrollable list of all words and their values below.
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

                        // Column with Game Board and Keyboard centered directly underneath at the exact same width
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 507.dp)
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // NxN Crossword Matrix — Zero-dead-zone touch handling on the grid container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shadow(6.dp, RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF4A3222))
                                    .padding(boardPadding)
                                    .onGloballyPositioned { coords ->
                                        gridBoundsInRoot = coords.boundsInRoot()
                                    }
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()

                                            val size = currentBoardSize
                                            val w = this.size.width.coerceAtLeast(1)
                                            val h = this.size.height.coerceAtLeast(1)
                                            val col = ((down.position.x / w) * size).toInt().coerceIn(0, size - 1)
                                            val row = ((down.position.y / h) * size).toInt().coerceIn(0, size - 1)

                                            val boardSnap = currentBoard
                                            val cellChar = boardSnap.getOrNull(row)?.getOrNull(col)
                                            if (!WordsWorthLogic.isBlockedCell(cellChar)) {
                                                val hadLetterInitially = WordsWorthLogic.isLetterCell(cellChar)
                                                // Immediately focus the tapped square (or toggle Across/Down if already focused)
                                                currentOnBoardCellTap(row, col)

                                                // Support press-and-hold on an existing letter cell to clear it
                                                var upOrCancel: PointerInputChange? = null
                                                try {
                                                    upOrCancel = withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                                        waitForUpOrCancellation()
                                                    }
                                                } catch (_: PointerEventTimeoutCancellationException) {
                                                    if (hadLetterInitially) {
                                                        currentOnUpdateCell(row, col, null)
                                                    }
                                                }

                                                if (upOrCancel == null) {
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        event.changes.forEach { it.consume() }
                                                    } while (event.changes.any { it.pressed })
                                                } else {
                                                    upOrCancel.consume()
                                                }
                                            } else {
                                                do {
                                                    val event = awaitPointerEvent()
                                                    event.changes.forEach { it.consume() }
                                                } while (event.changes.any { it.pressed })
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(cellSpacing)
                                ) {
                                    for (r in 0 until boardSize) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(cellSpacing)
                                        ) {
                                            for (c in 0 until boardSize) {
                                                val cellChar = board.getOrNull(r)?.getOrNull(c)
                                                val isHovered = hoveredCell == (r to c)
                                                val isFocused = focusedCell == (r to c)
                                                val validation = tallyResult?.cellValidations?.get(r to c)
                                                    ?: CellValidation.UNCHECKED

                                                BoardCell(
                                                    letter = cellChar,
                                                    boardSize = boardSize,
                                                    isFocused = isFocused,
                                                    focusDirection = focusDirection,
                                                    isHovered = isHovered,
                                                    validation = validation,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (isBoardLocked) {
                                    "Time's up! Board locked — tap Quit to save or Clear to play again."
                                } else {
                                    "Tip: Tap the arrow to toggle direction."
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBoardLocked) Color(0xFFB71C1C) else Color(0xFF1565C0),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Keyboard of Tiles (Alphabetical 3x9 or QWERTY 10/9/8)
                            // Tapping a letter immediately populates the focused square and advances focus to the next square!
                            val keyboardRowsList = WordsWorthLogic.keyboardRows(keyboardLayout)
                            val currentKeyboardRows by rememberUpdatedState(keyboardRowsList)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp)
                                        .onGloballyPositioned { coords ->
                                            keyboardOriginInRoot = coords.boundsInRoot().topLeft
                                        }
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()

                                                val rows = currentKeyboardRows
                                                val w = this.size.width.coerceAtLeast(1)
                                                val h = this.size.height.coerceAtLeast(1)
                                                val row = ((down.position.y / h) * rows.size).toInt()
                                                    .coerceIn(0, rows.size - 1)
                                                val rowKeys = rows[row]
                                                val col = ((down.position.x / w) * rowKeys.size).toInt()
                                                    .coerceIn(0, rowKeys.size - 1)
                                                val touchedLetter: Char? = rowKeys[col]

                                                // Immediately populate the focused cell and advance focus to the next square
                                                val undoPlacement = currentOnKeyboardInput(touchedLetter)

                                                var isDragging = false
                                                var lastPos = down.position
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                        ?: event.changes.firstOrNull()
                                                    if (change != null) {
                                                        change.consume()
                                                        lastPos = change.position
                                                        if (!currentIsBoardLocked &&
                                                            touchedLetter != null &&
                                                            !isDragging &&
                                                            (lastPos - down.position).getDistance() > dragThresholdPx
                                                        ) {
                                                            isDragging = true
                                                            draggingLetter = touchedLetter
                                                        }
                                                        if (isDragging) {
                                                            dragPositionInRoot = keyboardOriginInRoot + lastPos
                                                        }
                                                    }
                                                } while (event.changes.any { it.pressed })

                                                if (isDragging && touchedLetter != null) {
                                                    val target = cellAtRootPosition(keyboardOriginInRoot + lastPos)
                                                    if (target != null) {
                                                        currentOnDropLetterOnCell(
                                                            target.first,
                                                            target.second,
                                                            touchedLetter,
                                                            undoPlacement
                                                        )
                                                    }
                                                    draggingLetter = null
                                                }
                                            }
                                        },
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (rowKeys in keyboardRowsList) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (letter in rowKeys) {
                                                if (letter != null) {
                                                    val value = WordsWorthLogic.letterValue(letter)
                                                    val isSelected = selectedLetter == letter
                                                    LetterRackTile(
                                                        letter = letter,
                                                        value = value,
                                                        isSelected = isSelected,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                } else {
                                                    EraserRackTile(
                                                        isSelected = selectedLetter == null,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

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

                        // Options Column Next to the Game Board (outside verticalScroll so taps never get stolen):
                        // 1) Board Size Options (6x6, 9x9, 12x12)
                        // 2) Beneath them: Board Pattern Options (All-blank, Random pattern) with mini screenshots
                        // 3) One button's worth of space below Random pattern: Time Attack button
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(156.dp)
                                .padding(top = boardPadding)
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
                                color = Color(0xFFC8B496),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Option 1: All-blank (with small screenshot next to it)
                            PatternOptionButton(
                                label = "All-blank",
                                thumbnail = allBlankThumb,
                                isSelected = !isRandomPatternMode,
                                onClick = onSelectAllBlank
                            )

                            // Option 2: Random pattern (with small screenshot next to it)
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
                                        containerColor = Color(0xFF005B6E)
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
                }
            }

            // Floating dragged letter preview when dragging onto the board
            draggingLetter?.let { ch ->
                val tileSizePx = with(density) { 50.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (dragPositionInRoot.x - tileSizePx / 2f).roundToInt(),
                                y = (dragPositionInRoot.y - tileSizePx * 1.2f).roundToInt()
                            )
                        }
                        .size(50.dp)
                        .shadow(10.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFF3CD))
                        .border(2.dp, Color(0xFF5D4037), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Text(
                        text = ch.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2C1E18),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Text(
                        text = WordsWorthLogic.letterValue(ch).toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D4037),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
    }
}

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
        // High Score Box (tracks highest score across saved games; tapping opens the saved game preview)
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

        // Score Box
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

        // Timer Box under Score (same look and feel, distinct color)
        TimerCard(
            elapsedSecondsProvider = elapsedSecondsProvider,
            timeAttackMinutes = timeAttackMinutes,
            isBoardLocked = isBoardLocked
        )

        // Scrollable list of all words and their values below the score
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
                        color = Color(0xFFC62828),
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp)
                    )
                }
                HorizontalDivider(
                    color = Color(0xFFC8B496),
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
                            val bgColor = if (fw.isValid) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            val borderColor = if (fw.isValid) Color(0xFFA5D6A7) else Color(0xFFEF9A9A)
                            val textColor = if (fw.isValid) Color(0xFF1B5E20) else Color(0xFFB71C1C)

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
                                    color = Color(0xFFC62828),
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
            // Mini screenshot of the board
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
                        .background(Color(0xFF4A3222)),
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

            // Score shown right next to the mini screenshot
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

@Composable
fun SettingsScreen(
    keyboardLayout: KeyboardLayout,
    onChangeKeyboardLayout: (KeyboardLayout) -> Unit,
    savedGamesCount: Int,
    onClearSavedGames: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
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
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KeyboardLayout.entries.forEach { option ->
                            val isSelected = keyboardLayout == option
                            if (isSelected) {
                                Button(
                                    onClick = { onChangeKeyboardLayout(option) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = option.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onChangeKeyboardLayout(option) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        text = "Currently storing $savedGamesCount saved game board(s) in the left-hand column.",
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
        }
    }
}

@Composable
fun PatternOptionButton(
    label: String,
    thumbnail: androidx.compose.ui.graphics.ImageBitmap,
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

@Composable
fun BoardCell(
    letter: Char?,
    boardSize: Int,
    isFocused: Boolean,
    focusDirection: WordDirection,
    isHovered: Boolean,
    validation: CellValidation,
    modifier: Modifier = Modifier
) {
    val isBlocked = WordsWorthLogic.isBlockedCell(letter)
    val isLetter = WordsWorthLogic.isLetterCell(letter)

    val bgColor = when {
        isBlocked -> Color(0xFF161311)
        isHovered -> Color(0xFFFFE082)
        isFocused && letter == null -> Color(0xFFE3F2FD)
        letter == null -> Color(0xFFEDE4D3)
        validation == CellValidation.VALID -> Color(0xFFE8F5E9)
        validation == CellValidation.INVALID -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF8E1)
    }

    // Colored square border cursor for the focused cell
    val borderColor = when {
        isBlocked -> Color(0xFF0B0908)
        isHovered -> Color(0xFFFFA000)
        isFocused -> Color(0xFF1565C0)
        validation == CellValidation.VALID -> Color(0xFF2E7D32)
        validation == CellValidation.INVALID -> Color(0xFFC62828)
        isLetter -> Color(0xFFB89B72)
        else -> Color(0xFFC5B69E)
    }

    val borderWidth = when {
        !isBlocked && isFocused -> if (boardSize >= 12) 2.3.dp else 3.dp
        !isBlocked && (isHovered || validation != CellValidation.UNCHECKED) -> 1.8.dp
        else -> 1.dp
    }

    val cornerRadius = when {
        boardSize >= 12 -> 4.dp
        boardSize >= 9 -> 5.dp
        else -> 7.dp
    }

    val letterFontSize = when {
        boardSize >= 12 -> 13.sp
        boardSize >= 9 -> 17.sp
        else -> 24.sp
    }

    val valueFontSize = when {
        boardSize >= 12 -> 7.sp
        boardSize >= 9 -> 8.sp
        else -> 10.sp
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius))
            .padding(if (boardSize >= 12) 1.dp else 2.dp)
    ) {
        if (isFocused && !isBlocked && !isLetter) {
            Text(
                text = if (focusDirection == WordDirection.HORIZONTAL) "→" else "↓",
                fontSize = letterFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1565C0),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (isLetter && letter != null) {
            val textColor = when (validation) {
                CellValidation.VALID -> Color(0xFF1B5E20)
                CellValidation.INVALID -> Color(0xFFB71C1C)
                CellValidation.UNCHECKED -> Color(0xFF2C1E18)
            }
            Text(
                text = letter.toString(),
                fontSize = letterFontSize,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = WordsWorthLogic.letterValue(letter).toString(),
                fontSize = valueFontSize,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 1.dp)
            )
        }
    }
}

@Composable
fun LetterRackTile(
    letter: Char,
    value: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFFFFE082) else Color(0xFFFFF9E6)
    val borderColor = if (isSelected) Color(0xFF5D4037) else Color(0xFFC8B496)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .height(46.dp)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = letter.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF2C1E18),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-2).dp)
        )
        Text(
            text = value.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
fun EraserRackTile(
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) Color(0xFFFFCDD2) else Color(0xFFF5ECE0)
    val borderColor = if (isSelected) Color(0xFFC62828) else Color(0xFFC8B496)
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .height(46.dp)
            .shadow(if (isSelected) 3.dp else 1.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⌫",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB71C1C)
            )
            Text(
                text = "CLR",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB71C1C)
            )
        }
    }
}
