package com.example.wordsworth

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import com.example.wordsworth.ui.components.WordsWorthDialogs
import com.example.wordsworth.ui.screens.SettingsScreen
import com.example.wordsworth.ui.screens.WordsWorthGameScreen
import com.example.wordsworth.ui.theme.WordsWorthTheme

/**
 * Main Android activity for WordsWorth.
 *
 * Forwards physical/Bluetooth keyboard events to [hardwareKeyHandler] (registered by
 * [rememberWordsWorthGameState]) so the player can type letters, backspace, toggle direction,
 * navigate with arrow keys, and press Enter to validate without needing focus on a TextField.
 */
class MainActivity : ComponentActivity() {
    /** Active hardware keyboard listener registered while [WordsWorthApp] is in composition. */
    var hardwareKeyHandler: ((KeyEvent) -> Boolean)? = null

    /** ViewModel instance hosted at the Activity level to manage state persistence across process death. */
    private val gameState: WordsWorthGameState by viewModels()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hardwareKeyHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            WordsWorthTheme {
                WordsWorthApp(rememberWordsWorthGameState(gameState))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 1. Auto-save active in-progress game session to survive unexpected process death
        gameState.saveActiveSession()
        // 2. Safely update the dynamic launcher icon alias (Approach B) when the app is in the background
        val currentLang = WordsWorthLogic.loadGameLanguage(this)
        WordsWorthLogic.updateLauncherIcon(this, currentLang)
    }
}

/**
 * Root application composable that connects [WordsWorthGameState] to [WordsWorthGameScreen],
 * [SettingsScreen], and [WordsWorthDialogs].
 */
@Composable
fun WordsWorthApp(gameState: WordsWorthGameState = rememberWordsWorthGameState()) {

    if (gameState.showSettingsScreen) {
        SettingsScreen(
            keyboardLayout = gameState.keyboardLayout,
            onChangeKeyboardLayout = gameState::changeKeyboardLayout,
            scoringMode = gameState.scoringMode,
            onChangeScoringMode = gameState::changeScoringMode,
            japaneseVerbRule = gameState.japaneseVerbRule,
            onChangeJapaneseVerbRule = gameState::changeJapaneseVerbRule,
            isHapticFeedbackEnabled = gameState.isHapticFeedbackEnabled,
            onChangeHapticFeedbackEnabled = gameState::changeHapticFeedbackEnabled,
            letterSprinkleMode = gameState.letterSprinkleMode,
            onChangeLetterSprinkleMode = gameState::changeLetterSprinkleMode,
            allowDuplicateWordPoints = gameState.allowDuplicateWordPoints,
            onChangeAllowDuplicateWordPoints = gameState::changeAllowDuplicateWordPoints,
            savedGamesCount = gameState.savedGames.size,
            onClearSavedGames = gameState::clearAllSavedGames,
            onClearBoard = gameState::resetBoardAndTimer,
            onLoadExample = gameState::loadExample,
            onBack = { gameState.showSettingsScreen = false }
        )
    } else {
        WordsWorthGameScreen(
            board = gameState.board,
            boardSize = gameState.boardSize,
            onChangeBoardSize = gameState::changeBoardSize,
            isRandomPatternMode = gameState.isRandomPatternMode,
            onSelectAllBlank = gameState::selectAllBlank,
            onSelectRandomPattern = gameState::selectRandomPattern,
            timeAttackMinutes = gameState.timeAttackMinutes,
            isBoardLocked = gameState.isBoardLocked,
            onClickTimeAttack = { gameState.showTimeAttackDialog = true },
            keyboardLayout = gameState.keyboardLayout,
            focusedCell = gameState.focusedCell,
            focusDirection = gameState.focusDirection,
            onBoardCellTap = gameState::onBoardCellTap,
            selectedLetter = gameState.selectedLetter,
            onKeyboardInput = gameState::onKeyboardInput,
            onDropLetterOnCell = gameState::onDropLetterOnCell,
            onUpdateCell = gameState::updateCell,
            onClearBoard = gameState::resetBoardAndTimer,
            onLoadExample = gameState::loadExample,
            elapsedSecondsProvider = { gameState.elapsedSeconds },
            isTimerRunning = gameState.isTimerRunning,
            isDictionaryLoaded = gameState.isDictionaryLoaded,
            tallyResult = gameState.tallyResult,
            onValidate = {
                gameState.tallyResult = gameState.evaluateBoard()
            },
            onRequestWhitelistWord = { formedWord ->
                gameState.pendingWhitelistWord = formedWord
            },
            onQuitClick = {
                gameState.showQuitDialog = true
            },
            onOpenSettings = {
                gameState.showSettingsScreen = true
            },
            savedGames = gameState.savedGames,
            onClickSavedGame = gameState::resumeSavedGame,
            onDeleteSavedGame = { entry ->
                gameState.pendingDeleteEntry = entry
            },
            gameLanguage = gameState.gameLanguage,
            japaneseVerbRule = gameState.japaneseVerbRule,
            verbDictionary = gameState.japaneseVerbDictionary,
            isHapticFeedbackEnabled = gameState.isHapticFeedbackEnabled,
            sprinkledCells = gameState.sprinkledCells,
            onToggleLanguage = gameState::toggleGameLanguage,
            pendingRomaji = gameState.pendingRomaji,
            onKeyAction = gameState::onKeyAction
        )
    }

    // Render any active modal dialog (Time Attack, Whitelist, Pattern Switch, Quit, Delete, Preview)
    WordsWorthDialogs(gameState = gameState)
}
