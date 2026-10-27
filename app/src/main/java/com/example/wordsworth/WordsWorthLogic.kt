package com.example.wordsworth

import android.content.Context
import java.io.File

/**
 * Orientation of a formed word or the active board cursor.
 *
 * @property label Human-readable direction label displayed in the Words list ("Across" or "Down").
 */
enum class WordDirection(val label: String) {
    HORIZONTAL("Across"),
    VERTICAL("Down")
}

/**
 * Supported game language versions for WordsWorth.
 *
 * @property code ISO 639-1 language code ("en", "ja").
 * @property badge Short 1-2 character label displayed on the top-bar switch icon ("En", "日").
 * @property displayName Full display name for settings and descriptions.
 */
enum class GameLanguage(val code: String, val badge: String, val displayName: String) {
    ENGLISH("en", "En", "English"),
    JAPANESE("ja", "日", "日本語")
}

/**
 * Arrangement of the on-screen tile keyboard underneath the crossword board.
 *
 * @property label Display name shown in the Settings screen.
 */
enum class KeyboardLayout(val label: String) {
    ALPHABETICAL("Alphabetical"),
    QWERTY("QWERTY")
}

/**
 * Controls how extending a previously validated word on the board is scored during a game.
 *
 * - [EXTENDED_DIFF_ONLY]: Extending a validated word (e.g., `SOUND` -> `SOUNDLY`) replaces the
 *   shorter word so the player's score only increases by the newly added letters (`L` + `Y`).
 * - [CUMULATIVE_EXTENDED]: Extending a validated word keeps the original validated word (`SOUND`)
 *   and its points in the tally while also awarding full points for the extended word (`SOUNDLY`),
 *   provided the original word's letters remain intact inside the valid extended word.
 */
enum class ScoringMode(val label: String, val description: String) {
    EXTENDED_DIFF_ONLY(
        label = "New Letters Only",
        description = "Extending a validated word (e.g., SOUND → SOUNDLY) replaces the shorter word, adding only the new letters' points."
    ),
    CUMULATIVE_EXTENDED(
        label = "Keep Base Word Points",
        description = "Extending a validated word (e.g., SOUND → SOUNDLY) keeps the original word's points and also awards full points for the extended word."
    )
}

/**
 * Rule mode for Japanese terminal kana scoring:
 * - [CABOOSE]: All words ending in any kana from the Caboose set (-う row, て/で) score that final
 *   character as 1 point.
 * - [PRECISION_VERBS]: Only words identified as verbs (ending in う, く, す, つ, ぬ, む, る, matching
 *   the verb dictionary or compound suffixes, and not blocked by special exclusions) score the final
 *   character as 1 point.
 */
enum class JapaneseVerbRule(val label: String, val description: String) {
    CABOOSE(
        label = "Caboose Rule",
        description = "All words ending in -u row kana (and て/で) score the terminal letter as 1 pt."
    ),
    PRECISION_VERBS(
        label = "Precision Verbs",
        description = "Only identified verbs (ending in う、く/ぐ、す/ず、つ/づ、ぬ、ぶ、む、る) score the terminal letter as 1 pt."
    )
}

/**
 * Visual validation status of a single cell on the crossword board.
 */
enum class CellValidation {
    /** Letter has been placed or modified since the last board validation. */
    UNCHECKED,

    /** Cell belongs only to valid dictionary/whitelisted words (and no invalid words). */
    VALID,

    /** Cell belongs to at least one invalid word, or is an isolated single-letter tile. */
    INVALID
}

/**
 * Controls the density of random letters pre-filled (sprinkled) into random squares on new boards.
 *
 * - [NONE]: 0% (zero letters pre-filled).
 * - [FEW]: 5% of available squares pre-filled with random letters.
 * - [SOME]: 10% of available squares pre-filled with random letters.
 * - [MANY]: 15% of available squares pre-filled with random letters.
 */
enum class LetterSprinkleMode(val label: String, val percentage: Float) {
    NONE(label = "None", percentage = 0.0f),
    FEW(label = "Few", percentage = 0.05f),
    SOME(label = "Some", percentage = 0.10f),
    MANY(label = "Many", percentage = 0.15f);

    val description: String
        get() = when (this) {
            NONE -> "Zero letters (blank board)."
            FEW -> "5 percent of available squares."
            SOME -> "10 pct of available squares."
            MANY -> "15 pct of available squares."
        }
}

/**
 * Represents a contiguous horizontal or vertical run of 2 or more letters on the board.
 *
 * @property word Uppercase string formed by the contiguous letters.
 * @property direction Whether this word runs [WordDirection.HORIZONTAL] or [WordDirection.VERTICAL].
 * @property startRow 0-indexed row of the first letter in the word.
 * @property startCol 0-indexed column of the first letter in the word.
 * @property cells Ordered list of `(row, col)` coordinates occupied by the word's letters.
 * @property rawPoints Sum of individual tile point values in [word], regardless of validity.
 * @property isValid True if [word] exists in the loaded dictionary or user whitelist.
 * @property isDuplicateUnscored True when [word] is valid on the board but earns 0 points because
 *   an earlier instance of the same word is already scored and duplicate word scoring is disabled.
 */
data class FormedWord(
    val word: String,
    val direction: WordDirection,
    val startRow: Int,
    val startCol: Int,
    val cells: List<Pair<Int, Int>>,
    val rawPoints: Int,
    val isValid: Boolean,
    val isDuplicateUnscored: Boolean = false
) {
    /** Points awarded toward the player's score (0 if the word is invalid or an unscored duplicate). */
    val awardedPoints: Int
        get() = if (isValid && !isDuplicateUnscored) rawPoints else 0
}

/**
 * Complete result of validating and tallying the current crossword board.
 *
 * @property totalScore Sum of [FormedWord.awardedPoints] across all words in [words].
 * @property words All horizontal and vertical words evaluated (including retained base words when
 *   [ScoringMode.CUMULATIVE_EXTENDED] is active).
 * @property isolatedCells Coordinates of single letters that do not belong to any 2+ letter run.
 * @property cellValidations Per-cell [CellValidation] state used to color board squares green or red.
 */
data class TallyResult(
    val totalScore: Int,
    val words: List<FormedWord>,
    val isolatedCells: List<Pair<Int, Int>>,
    val cellValidations: Map<Pair<Int, Int>, CellValidation>
) {
    /** Subset of [words] that are valid dictionary/whitelisted words. */
    val validWords: List<FormedWord>
        get() = words.filter { it.isValid }

    /** Subset of [words] that are not recognized by the dictionary/whitelist. */
    val invalidWords: List<FormedWord>
        get() = words.filter { !it.isValid }
}

/**
 * Pure domain logic, board generation algorithms, dictionary/whitelist management,
 * and preference persistence for WordsWorth.
 */
object WordsWorthLogic {
    /** Default board dimension (6x6). */
    const val BOARD_SIZE = 6

    /** Maximum supported board dimension (12x12), also used to filter dictionary word lengths. */
    const val MAX_BOARD_SIZE = 12

    /** Supported selectable square board dimensions. */
    val BOARD_SIZES = listOf(6, 9, 12)

    /** Sentinel character stored in a board cell to represent a blacked-out (blocked) square. */
    const val BLOCKED_CELL = '#'

    /** Returns true if [ch] is a placed letter (`A`..`Z`) rather than empty (`null`) or blocked (`#`). */
    fun isLetterCell(ch: Char?): Boolean = ch != null && ch != BLOCKED_CELL

    /** Returns true if [ch] represents a blacked-out crossword square ([BLOCKED_CELL]). */
    fun isBlockedCell(ch: Char?): Boolean = ch == BLOCKED_CELL

    /** Point values for each letter tile from A to Z. */
    val LETTER_VALUES: Map<Char, Int> = mapOf(
        'A' to 1,
        'B' to 3,
        'C' to 3,
        'D' to 2,
        'E' to 1,
        'F' to 4,
        'G' to 2,
        'H' to 4,
        'I' to 1,
        'J' to 8,
        'K' to 5,
        'L' to 2,
        'M' to 3,
        'N' to 1,
        'O' to 1,
        'P' to 3,
        'Q' to 10,
        'R' to 1,
        'S' to 1,
        'T' to 1,
        'U' to 2,
        'V' to 5,
        'W' to 4,
        'X' to 8,
        'Y' to 1,
        'Z' to 10
    )

    val ALPHABET: List<Char> = ('A'..'Z').toList()

    /** Special rack key identifiers for Japanese and board actions. */
    const val KEY_CLR = "CLR"
    const val KEY_MODIFIER = "小゛゜"
    const val KEY_CHOONPU = "ー"

    /** Japanese Alphabetical keyboard arrangement. */
    val JAPANESE_ALPHABETICAL_KEYBOARD_ROWS: List<List<String>> = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"),
        listOf("J", "K", "L", "M", "N", "O", "P", "Q", "R", KEY_CHOONPU),
        listOf("S", "T", "U", "V", "W", "X", "Y", "Z", KEY_CLR)
    )

    /** Japanese QWERTY keyboard arrangement. */
    val JAPANESE_QWERTY_KEYBOARD_ROWS: List<List<String>> = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", KEY_CHOONPU),
        listOf("Z", "X", "C", "V", "B", "N", "M", KEY_CLR)
    )

    /** English Alphabetical keyboard arrangement. */
    val ENGLISH_ALPHABETICAL_KEYBOARD_ROWS: List<List<String>> = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G", "H", "I"),
        listOf("J", "K", "L", "M", "N", "O", "P", "Q", "R"),
        listOf("S", "T", "U", "V", "W", "X", "Y", "Z", KEY_CLR)
    )

    /** English QWERTY keyboard arrangement. */
    val ENGLISH_QWERTY_KEYBOARD_ROWS: List<List<String>> = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M", KEY_CLR)
    )

    /** Backward-compatible default keyboard matrices. */
    val ALPHABETICAL_KEYBOARD_ROWS: List<List<String>> = JAPANESE_ALPHABETICAL_KEYBOARD_ROWS
    val QWERTY_KEYBOARD_ROWS: List<List<String>> = JAPANESE_QWERTY_KEYBOARD_ROWS

    /** Returns the row/column key arrangement for the given [KeyboardLayout] and [GameLanguage]. */
    fun keyboardRows(layout: KeyboardLayout, language: GameLanguage = GameLanguage.ENGLISH): List<List<String>> =
        when (language) {
            GameLanguage.ENGLISH -> when (layout) {
                KeyboardLayout.ALPHABETICAL -> ENGLISH_ALPHABETICAL_KEYBOARD_ROWS
                KeyboardLayout.QWERTY -> ENGLISH_QWERTY_KEYBOARD_ROWS
            }
            GameLanguage.JAPANESE -> when (layout) {
                KeyboardLayout.ALPHABETICAL -> JAPANESE_ALPHABETICAL_KEYBOARD_ROWS
                KeyboardLayout.QWERTY -> JAPANESE_QWERTY_KEYBOARD_ROWS
            }
        }

    private const val PREFS_NAME = "wordsworth_prefs"
    private const val KEY_GAME_LANGUAGE = "game_language"
    private const val KEY_KEYBOARD_LAYOUT = "keyboard_layout"
    private const val KEY_SCORING_MODE = "scoring_mode"
    private const val KEY_ALLOW_DUPLICATE_WORD_POINTS = "allow_duplicate_word_points"
    private const val KEY_JAPANESE_VERB_RULE = "japanese_verb_rule"
    private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    private const val KEY_LETTER_SPRINKLE_MODE = "letter_sprinkle_mode"

    /** Loads the user's saved [GameLanguage] from SharedPreferences. */
    fun loadGameLanguage(context: Context): GameLanguage {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GAME_LANGUAGE, GameLanguage.ENGLISH.name)
        return try {
            GameLanguage.valueOf(saved ?: GameLanguage.ENGLISH.name)
        } catch (_: Exception) {
            GameLanguage.ENGLISH
        }
    }

    /** Persists the user's selected [GameLanguage] to SharedPreferences. */
    fun saveGameLanguage(context: Context, lang: GameLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GAME_LANGUAGE, lang.name)
            .apply()
    }

    /** Loads the user's saved [KeyboardLayout] from SharedPreferences. */
    fun loadKeyboardLayout(context: Context): KeyboardLayout {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_KEYBOARD_LAYOUT, KeyboardLayout.ALPHABETICAL.name)
        return try {
            KeyboardLayout.valueOf(saved ?: KeyboardLayout.ALPHABETICAL.name)
        } catch (_: Exception) {
            KeyboardLayout.ALPHABETICAL
        }
    }

    /** Persists the user's selected [KeyboardLayout] to SharedPreferences. */
    fun saveKeyboardLayout(context: Context, layout: KeyboardLayout) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KEYBOARD_LAYOUT, layout.name)
            .apply()
    }

    /** Loads the user's saved [ScoringMode] from SharedPreferences. */
    fun loadScoringMode(context: Context): ScoringMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SCORING_MODE, ScoringMode.EXTENDED_DIFF_ONLY.name)
        return try {
            ScoringMode.valueOf(saved ?: ScoringMode.EXTENDED_DIFF_ONLY.name)
        } catch (_: Exception) {
            ScoringMode.EXTENDED_DIFF_ONLY
        }
    }

    /** Persists the user's selected [ScoringMode] to SharedPreferences. */
    fun saveScoringMode(context: Context, mode: ScoringMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCORING_MODE, mode.name)
            .apply()
    }

    /**
     * Loads whether placing the same valid word more than once awards points after the first time.
     */
    fun loadAllowDuplicateWordPoints(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ALLOW_DUPLICATE_WORD_POINTS, true)
    }

    /**
     * Persists whether placing the same valid word more than once awards points after the first time.
     */
    fun saveAllowDuplicateWordPoints(context: Context, allow: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ALLOW_DUPLICATE_WORD_POINTS, allow)
            .apply()
    }

    /** Loads the user's saved [JapaneseVerbRule] from SharedPreferences. */
    fun loadJapaneseVerbRule(context: Context): JapaneseVerbRule {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_JAPANESE_VERB_RULE, JapaneseVerbRule.CABOOSE.name)
        return try {
            JapaneseVerbRule.valueOf(saved ?: JapaneseVerbRule.CABOOSE.name)
        } catch (_: Exception) {
            JapaneseVerbRule.CABOOSE
        }
    }

    /** Persists the user's selected [JapaneseVerbRule] to SharedPreferences. */
    fun saveJapaneseVerbRule(context: Context, rule: JapaneseVerbRule) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JAPANESE_VERB_RULE, rule.name)
            .apply()
    }

    /** Loads whether keyboard haptic feedback vibration is enabled from SharedPreferences. */
    fun loadHapticFeedbackEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
    }

    /** Persists whether keyboard haptic feedback vibration is enabled to SharedPreferences. */
    fun saveHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled)
            .apply()
    }

    /** Loads the user's saved [LetterSprinkleMode] from SharedPreferences. */
    fun loadLetterSprinkleMode(context: Context): LetterSprinkleMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_LETTER_SPRINKLE_MODE, LetterSprinkleMode.NONE.name)
        return try {
            LetterSprinkleMode.valueOf(name ?: LetterSprinkleMode.NONE.name)
        } catch (_: Exception) {
            LetterSprinkleMode.NONE
        }
    }

    /** Persists the user's selected [LetterSprinkleMode] to SharedPreferences. */
    fun saveLetterSprinkleMode(context: Context, mode: LetterSprinkleMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LETTER_SPRINKLE_MODE, mode.name)
            .apply()
    }

    /** Terminal kana eligible as triggers for the Precision Verbs rule. */
    val VERB_TERMINAL_KANA: Set<Char> = setOf(
        'う', 'く', 'ぐ', 'す', 'ず', 'つ', 'づ', 'ぬ', 'ぶ', 'む', 'る'
    )

    /** 2-kana (and special) compound verb endings. */
    val VERB_SUFFIXES_2: List<String> = listOf(
        "だす", "こむ", "あう", "たつ", "つく", "きる", "とる",
        "おく", "よる", "いる", "ます", "ぬく", "やる", "そめる"
    )

    /** 3-kana compound verb endings. */
    val VERB_SUFFIXES_3: List<String> = listOf(
        "あげる", "さげる", "かける", "つける", "すぎる", "なおす",
        "おとす", "はらう", "ちらす", "まわす", "ならう"
    )

    /**
     * Checks if [word] is classified as a Japanese verb under the Precision Verbs rule.
     *
     * 1. Word must end with one of the trigger kana:
     *    (う, く/ぐ, す/ず, つ/づ, ぬ, ぶ, む, る).
     * 2. For a word ending in 'ぬ':
     *    - If [word] is "うぬ", "きぬ", "おぬ", or "かんぬ", or ends with "いぬ", then verb = false.
     *    - Else verb = true.
     * 3. For words ending in any other trigger kana:
     *    - [word] is in [verbDictionary]
     *    - [word] ends with one of: "だす", "こむ", "あう", "たつ", "つく", "きる", "とる",
     *      "おく", "よる", "いる", "ます", "ぬく", "やる", "そめる"
     *    - [word] ends with one of: "あげる", "さげる", "かける", "つける", "すぎる", "なおす",
     *      "おとす", "はらう", "ちらす", "まわす", "ならう"
     */
    fun isJapaneseVerb(word: String, verbDictionary: Set<String>): Boolean {
        if (word.isEmpty()) return false
        val lastChar = word.last()
        if (lastChar !in VERB_TERMINAL_KANA) {
            return false
        }

        // Special rule for words ending in ぬ:
        // If (word is うぬ、きぬ、おぬ、or かんぬ) or (ends with いぬ) then verb = false, Else verb = true
        if (lastChar == 'ぬ') {
            return !(word == "うぬ" || word == "きぬ" || word == "おぬ" || word == "かんぬ" || word.endsWith("いぬ"))
        }

        return verbDictionary.contains(word) ||
            VERB_SUFFIXES_2.any { word.endsWith(it) } ||
            VERB_SUFFIXES_3.any { word.endsWith(it) }
    }

    /**
     * Loads the full Japanese verb dictionary from `all_japanese_verbs_hiragana.txt`.
     */
    fun loadJapaneseVerbDictionary(context: Context): Set<String> {
        val assetFileName = "all_japanese_verbs_hiragana.txt"
        val words = HashSet<String>(15000)
        try {
            val stream = try {
                context.assets.open(assetFileName)
            } catch (_: Exception) {
                File("app/src/main/assets/$assetFileName").takeIf { it.exists() }?.inputStream()
                    ?: File(assetFileName).takeIf { it.exists() }?.inputStream()
            }
            stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        words.add(trimmed)
                    }
                }
            }
        } catch (_: Exception) {
            // Return empty set on failure
        }
        return words
    }

    /**
     * Loads the Japanese verb dictionary directly from file (useful for unit tests without Android Context).
     */
    fun loadJapaneseVerbDictionaryFromFile(): Set<String> {
        val assetFileName = "all_japanese_verbs_hiragana.txt"
        val words = HashSet<String>(15000)
        val file = File("app/src/main/assets/$assetFileName").takeIf { it.exists() }
            ?: File("src/main/assets/$assetFileName").takeIf { it.exists() }
            ?: File(assetFileName).takeIf { it.exists() }
            ?: File("../$assetFileName").takeIf { it.exists() }
            ?: File("../app/src/main/assets/$assetFileName").takeIf { it.exists() }
        file?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    words.add(trimmed)
                }
            }
        }
        return words
    }

    /** Returns true if [ch] is a Japanese Kana or chōonpu character. */
    fun isJapaneseChar(ch: Char): Boolean = KanaConverter.isKana(ch) || ch == KanaConverter.CHOONPU

    /** Returns true if [text] contains Japanese Kana characters. */
    fun isJapaneseText(text: String): Boolean = text.any { isJapaneseChar(it) }

    /** Returns the point value for a single character tile. */
    fun letterValue(ch: Char, language: GameLanguage? = null): Int {
        val isJa = if (language != null) language == GameLanguage.JAPANESE else isJapaneseChar(ch)
        return if (isJa) {
            KanaConverter.kanaValue(ch)
        } else {
            LETTER_VALUES[ch.uppercaseChar()] ?: 0
        }
    }

    /**
     * Determines whether the letter at ([r], [c]) on [board] is acting strictly as a Caboose/reduced-verb letter in Japanese
     * without simultaneously continuing as a non-terminal letter in another word.
     */
    fun isCabooseCell(
        board: Array<Array<Char?>>,
        r: Int,
        c: Int,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): Boolean {
        val size = board.size
        val ch = board.getOrNull(r)?.getOrNull(c) ?: return false
        if (!isLetterCell(ch)) return false

        // Check horizontal run of letters containing (r, c)
        var startC = c
        while (startC > 0 && isLetterCell(board[r][startC - 1])) startC--
        var endC = c
        while (endC < size - 1 && isLetterCell(board[r][endC + 1])) endC++
        val horizLen = endC - startC + 1

        // Check vertical run of letters containing (r, c)
        var startR = r
        while (startR > 0 && isLetterCell(board[startR - 1][c])) startR--
        var endR = r
        while (endR < size - 1 && isLetterCell(board[endR + 1][c])) endR++
        val vertLen = endR - startR + 1

        val isCabooseHoriz = if (horizLen >= 2 && c == endC) {
            when (japaneseVerbRule) {
                JapaneseVerbRule.CABOOSE -> ch in KanaConverter.CABOOSE_KANA
                JapaneseVerbRule.PRECISION_VERBS -> {
                    val word = (startC..endC).map { board[r][it]!! }.joinToString("")
                    isJapaneseVerb(word, verbDictionary)
                }
            }
        } else {
            false
        }
        val isNonTerminalHoriz = horizLen >= 2 && c < endC

        val isCabooseVert = if (vertLen >= 2 && r == endR) {
            when (japaneseVerbRule) {
                JapaneseVerbRule.CABOOSE -> ch in KanaConverter.CABOOSE_KANA
                JapaneseVerbRule.PRECISION_VERBS -> {
                    val word = (startR..endR).map { board[it][c]!! }.joinToString("")
                    isJapaneseVerb(word, verbDictionary)
                }
            }
        } else {
            false
        }
        val isNonTerminalVert = vertLen >= 2 && r < endR

        val scoresCaboose = isCabooseHoriz || isCabooseVert
        val scoresOriginal = isNonTerminalHoriz || isNonTerminalVert

        return scoresCaboose && !scoresOriginal
    }

    /**
     * Returns the formatted string representation of the displayed point value for a cell
     * at ([r], [c]) on [board].
     *
     * In Japanese mode, when a tile with base value > 1 terminates one word (scored as 1 point per
     * the active Caboose or Precision Verbs Rule) AND simultaneously continues into another
     * intersecting word (scored at its original base value), both values are displayed separated
     * by a middle dot: `"1・$baseVal"`, e.g., `"1・6"`.
     */
    fun cellDisplayString(
        board: Array<Array<Char?>>,
        r: Int,
        c: Int,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): String {
        val ch = board.getOrNull(r)?.getOrNull(c) ?: return ""
        if (!isLetterCell(ch)) return ""
        val isJa = if (language != null) language == GameLanguage.JAPANESE else isJapaneseChar(ch)
        if (!isJa) {
            return (LETTER_VALUES[ch.uppercaseChar()] ?: 0).toString()
        }

        val baseVal = KanaConverter.kanaValue(ch)

        val size = board.size

        // Find horizontal run of letters containing (r, c)
        var startC = c
        while (startC > 0 && isLetterCell(board[r][startC - 1])) startC--
        var endC = c
        while (endC < size - 1 && isLetterCell(board[r][endC + 1])) endC++
        val horizLen = endC - startC + 1

        // Find vertical run of letters containing (r, c)
        var startR = r
        while (startR > 0 && isLetterCell(board[startR - 1][c])) startR--
        var endR = r
        while (endR < size - 1 && isLetterCell(board[endR + 1][c])) endR++
        val vertLen = endR - startR + 1

        val isCabooseHoriz = if (horizLen >= 2 && c == endC) {
            when (japaneseVerbRule) {
                JapaneseVerbRule.CABOOSE -> ch in KanaConverter.CABOOSE_KANA
                JapaneseVerbRule.PRECISION_VERBS -> {
                    val word = (startC..endC).map { board[r][it]!! }.joinToString("")
                    isJapaneseVerb(word, verbDictionary)
                }
            }
        } else {
            false
        }
        val isNonTerminalHoriz = horizLen >= 2 && c < endC

        val isCabooseVert = if (vertLen >= 2 && r == endR) {
            when (japaneseVerbRule) {
                JapaneseVerbRule.CABOOSE -> ch in KanaConverter.CABOOSE_KANA
                JapaneseVerbRule.PRECISION_VERBS -> {
                    val word = (startR..endR).map { board[it][c]!! }.joinToString("")
                    isJapaneseVerb(word, verbDictionary)
                }
            }
        } else {
            false
        }
        val isNonTerminalVert = vertLen >= 2 && r < endR

        val scoresCaboose = isCabooseHoriz || isCabooseVert
        val scoresOriginal = isNonTerminalHoriz || isNonTerminalVert

        return if (scoresCaboose && scoresOriginal && baseVal > 1) {
            "1・$baseVal"
        } else if (scoresCaboose) {
            minOf(baseVal, 1).toString()
        } else {
            baseVal.toString()
        }
    }

    /**
     * Returns the displayed point value for a cell at ([r], [c]) on [board].
     * If the cell contains a terminal kana reduced to 1 point under the active Japanese verb rule,
     * it evaluates to 1 point; otherwise its intrinsic base tile value.
     */
    fun cellDisplayValue(
        board: Array<Array<Char?>>,
        r: Int,
        c: Int,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): Int {
        val ch = board.getOrNull(r)?.getOrNull(c) ?: return 0
        if (!isLetterCell(ch)) return 0
        val isJa = if (language != null) language == GameLanguage.JAPANESE else isJapaneseChar(ch)
        return if (isJa) {
            val baseVal = KanaConverter.kanaValue(ch)
            if (isCabooseCell(board, r, c, japaneseVerbRule, verbDictionary)) {
                minOf(baseVal, 1)
            } else {
                baseVal
            }
        } else {
            LETTER_VALUES[ch.uppercaseChar()] ?: 0
        }
    }

    /** Returns the total raw point value of all letters in [word], applying language rules. */
    fun wordRawScore(
        word: String,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): Int {
        val isJa = if (language != null) language == GameLanguage.JAPANESE else isJapaneseText(word)
        return if (isJa) {
            val isVerb = if (japaneseVerbRule == JapaneseVerbRule.PRECISION_VERBS) {
                isJapaneseVerb(word, verbDictionary)
            } else {
                false
            }
            KanaConverter.wordRawScore(word, japaneseVerbRule, isVerb)
        } else {
            word.sumOf { LETTER_VALUES[it.uppercaseChar()] ?: 0 }
        }
    }

    /**
     * Calculates the raw score of [word] across [cells], doubling the point value of any
     * cell in [sprinkledCells] that is not already in [claimedSprinkles].
     *
     * In Japanese mode, terminal letters reduced to 1 point under the active verb rule
     * double from 1 point to 2 points, respecting "(subject to the verb-related rules)".
     *
     * @return Pair of (awarded raw points, set of newly claimed sprinkle coordinates).
     */
    fun calculateWordRawScore(
        word: String,
        cells: List<Pair<Int, Int>>,
        sprinkledCells: Set<Pair<Int, Int>> = emptySet(),
        claimedSprinkles: Set<Pair<Int, Int>> = emptySet(),
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): Pair<Int, Set<Pair<Int, Int>>> {
        if (word.isEmpty()) return 0 to emptySet()
        val isJa = if (language != null) language == GameLanguage.JAPANESE else isJapaneseText(word)
        val isVerb = if (isJa && japaneseVerbRule == JapaneseVerbRule.PRECISION_VERBS) {
            isJapaneseVerb(word, verbDictionary)
        } else {
            false
        }

        var total = 0
        val newlyClaimed = mutableSetOf<Pair<Int, Int>>()
        val lastIndex = word.length - 1

        for (i in word.indices) {
            val ch = word[i]
            val cellPos = cells.getOrNull(i)
            val isEligibleSprinkle = cellPos != null && cellPos in sprinkledCells && cellPos !in claimedSprinkles

            val effectiveVal: Int
            if (isJa) {
                val baseVal = KanaConverter.kanaValue(ch)
                val isTerminalReduced = when (japaneseVerbRule) {
                    JapaneseVerbRule.CABOOSE -> (i == lastIndex && ch in KanaConverter.CABOOSE_KANA)
                    JapaneseVerbRule.PRECISION_VERBS -> (i == lastIndex && isVerb)
                }
                effectiveVal = if (isTerminalReduced) minOf(baseVal, 1) else baseVal
            } else {
                effectiveVal = LETTER_VALUES[ch.uppercaseChar()] ?: 0
            }

            if (isEligibleSprinkle) {
                total += effectiveVal * 2
                newlyClaimed.add(cellPos!!)
            } else {
                total += effectiveVal
            }
        }

        return total to newlyClaimed
    }

    private fun getWhitelistFileName(language: GameLanguage): String =
        if (language == GameLanguage.JAPANESE) "whitelisted_words_ja.txt" else "whitelisted_words.txt"

    /**
     * Loads the base word list from assets (`words.txt` or `words_ja.txt`, filtered to 2..[MAX_BOARD_SIZE] letters)
     * and merges in any custom words added to the user's local whitelist.
     */
    fun loadDictionary(context: Context, language: GameLanguage = GameLanguage.ENGLISH): Set<String> {
        val assetFileName = if (language == GameLanguage.JAPANESE) "words_ja.txt" else "words.txt"
        val baseWords = try {
            val stream = try {
                context.assets.open(assetFileName)
            } catch (_: Exception) {
                context.assets.open("words.txt")
            }
            stream.bufferedReader().useLines { lines ->
                lines.map { line ->
                    val trimmed = line.trim()
                    if (language == GameLanguage.ENGLISH) trimmed.uppercase() else trimmed
                }
                    .filter { it.length in 2..MAX_BOARD_SIZE }
                    .toHashSet()
            }
        } catch (_: Exception) {
            if (language == GameLanguage.JAPANESE) {
                mutableSetOf("はっけん", "ぬき", "せぬ", "はな", "けむり", "さくら", "にほん")
            } else {
                mutableSetOf("SALUTE", "SHOES", "OVERT", "TOT", "ER", "RAVE", "AT", "SALVE", "OR")
            }
        }
        baseWords.addAll(loadWhitelist(context, language))
        return baseWords
    }

    /** Reads the user's custom whitelisted words from internal storage for [language]. */
    fun loadWhitelist(context: Context, language: GameLanguage = GameLanguage.ENGLISH): Set<String> {
        val file = File(context.filesDir, getWhitelistFileName(language))
        if (!file.exists()) return emptySet()
        return try {
            file.readLines()
                .map { line ->
                    val trimmed = line.trim()
                    if (language == GameLanguage.ENGLISH) trimmed.uppercase() else trimmed
                }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Adds [word] to the persistent user whitelist file for [language] and returns the updated set. */
    fun addWordToWhitelist(context: Context, word: String, language: GameLanguage = GameLanguage.ENGLISH): Set<String> {
        val normalized = if (language == GameLanguage.ENGLISH) word.trim().uppercase() else word.trim()
        if (normalized.isEmpty()) return loadWhitelist(context, language)
        val current = loadWhitelist(context, language).toMutableSet()
        current.add(normalized)
        try {
            val file = File(context.filesDir, getWhitelistFileName(language))
            file.writeText(current.sorted().joinToString("\n"))
        } catch (_: Exception) {
        }
        return current
    }

    /** Removes [word] from the whitelist file for [language]. */
    fun removeWordFromWhitelist(context: Context, word: String, language: GameLanguage = GameLanguage.ENGLISH): Set<String> {
        val normalized = if (language == GameLanguage.ENGLISH) word.trim().uppercase() else word.trim()
        val current = loadWhitelist(context, language).toMutableSet()
        current.remove(normalized)
        try {
            val file = File(context.filesDir, getWhitelistFileName(language))
            file.writeText(current.sorted().joinToString("\n"))
        } catch (_: Exception) {
        }
        return current
    }

    /** Clears all whitelisted words for [language]. */
    fun clearWhitelist(context: Context, language: GameLanguage = GameLanguage.ENGLISH) {
        try {
            val file = File(context.filesDir, getWhitelistFileName(language))
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Creates an all-blank `size x size` board where every cell is `null`.
     */
    fun createBlankBoard(size: Int): Array<Array<Char?>> {
        return Array(size) {
            Array<Char?>(size) { null }
        }
    }

    /**
     * Generates a random-pattern `size x size` board with blacked-out squares ([BLOCKED_CELL]),
     * guaranteeing that:
     * 1. All remaining white squares form a single orthogonally connected component.
     * 2. Every white square has at least 2 orthogonal white neighbors (preventing isolated
     *    1-cell squares or dead-end 1-cell pockets).
     */
    fun generateRandomPatternBoard(size: Int): Array<Array<Char?>> {
        val board = createBlankBoard(size)
        val totalCells = size * size
        val minBlack = (totalCells * 0.18).toInt().coerceAtLeast(3)
        val maxBlack = (totalCells * 0.34).toInt().coerceAtLeast(minBlack + 2)
        val targetBlackCount = (minBlack..maxBlack).random()

        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                candidates.add(r to c)
            }
        }
        candidates.shuffle()

        var placedBlack = 0
        for ((r, c) in candidates) {
            if (placedBlack >= targetBlackCount) break
            board[r][c] = BLOCKED_CELL
            if (isValidWhitePattern(board, size)) {
                placedBlack++
            } else {
                // Revert if placing a black square here would disconnect the board or create a singleton pocket
                board[r][c] = null
            }
        }

        return board
    }

    /** The 45 standard gojūon Hiragana characters used for Japanese letter sprinkling (excluding を). */
    val SPRINKLE_KANA: List<Char> = listOf(
        'あ', 'い', 'う', 'え', 'お',
        'か', 'き', 'く', 'け', 'こ',
        'さ', 'し', 'す', 'せ', 'そ',
        'た', 'ち', 'つ', 'て', 'と',
        'な', 'に', 'ぬ', 'ね', 'の',
        'は', 'ひ', 'ふ', 'へ', 'ほ',
        'ま', 'み', 'む', 'め', 'も',
        'や', 'ゆ', 'よ',
        'ら', 'り', 'る', 'れ', 'ろ',
        'わ', 'ん'
    )

    /** Standard English uppercase letters ('A'..'Z') used for English letter sprinkling. */
    val SPRINKLE_LETTERS_EN: List<Char> = ALPHABET

    /**
     * Greedily selects up to [count] squares from [availableCells] such that no two selected
     * squares are orthogonally adjacent to each other.
     */
    fun selectNonAdjacentSprinkleCells(
        availableCells: List<Pair<Int, Int>>,
        count: Int,
        random: java.util.Random
    ): List<Pair<Int, Int>> {
        if (count <= 0 || availableCells.isEmpty()) return emptyList()

        var bestSelection: List<Pair<Int, Int>> = emptyList()
        val maxAttempts = 100

        for (attempt in 0 until maxAttempts) {
            val shuffled = availableCells.shuffled(random)
            val current = mutableListOf<Pair<Int, Int>>()

            for (cell in shuffled) {
                val (r, c) = cell
                val isAdjacent = current.any { (sr, sc) ->
                    kotlin.math.abs(sr - r) + kotlin.math.abs(sc - c) == 1
                }
                if (!isAdjacent) {
                    current.add(cell)
                    if (current.size == count) {
                        return current
                    }
                }
            }

            if (current.size > bestSelection.size) {
                bestSelection = current
            }
        }

        return bestSelection
    }

    /**
     * Calculates the exact number of squares to pre-fill given the [availableSquareCount] and [mode].
     *
     * - [LetterSprinkleMode.NONE]: 0 (zero letters).
     * - [LetterSprinkleMode.FEW]: 5% of available squares (rounded to nearest integer, minimum 1 if available > 0).
     * - [LetterSprinkleMode.SOME]: 10% of available squares (rounded to nearest integer, minimum 1 if available > 0).
     * - [LetterSprinkleMode.MANY]: 15% of available squares (rounded to nearest integer, minimum 1 if available > 0).
     */
    fun sprinkleLetterCount(availableSquareCount: Int, mode: LetterSprinkleMode): Int {
        if (mode == LetterSprinkleMode.NONE || availableSquareCount <= 0) return 0
        val count = kotlin.math.round(availableSquareCount * mode.percentage).toInt()
        return count.coerceIn(1, availableSquareCount)
    }

    /**
     * Sprinkles random letters into random squares on [board] according to [mode] and [language].
     *
     * Adheres to the following refinement rules:
     * 1. を is never used as a sprinkle kana.
     * 3. ん can't be placed on the top row (r == 0) or left-most column (c == 0).
     * 4. Two sprinkled letters can't be orthogonally adjacent to each other.
     * 5. Q can't be placed on the bottom row (r == board.size - 1) or right-most column (c == board.size - 1).
     *
     * @param board Matrix of board squares. Blocked squares (`'#'`) are strictly preserved.
     * @param mode Selected sprinkle density ([LetterSprinkleMode]).
     * @param language Active game language ([GameLanguage]).
     * @param random Optional [java.util.Random] instance for deterministic testing.
     * @return Set of `(row, col)` coordinates of squares that were populated with sprinkled letters.
     */
    fun sprinkleLetters(
        board: Array<Array<Char?>>,
        mode: LetterSprinkleMode,
        language: GameLanguage,
        random: java.util.Random = java.util.Random()
    ): Set<Pair<Int, Int>> {
        if (mode == LetterSprinkleMode.NONE) return emptySet()

        val availableCells = mutableListOf<Pair<Int, Int>>()
        for (r in board.indices) {
            for (c in board[r].indices) {
                if (!isBlockedCell(board[r][c]) && board[r][c] == null) {
                    availableCells.add(r to c)
                }
            }
        }

        val count = sprinkleLetterCount(availableCells.size, mode)
        if (count <= 0) return emptySet()

        // Rule 4: Two sprinkles can't be adjacent to each other
        val selectedCells = selectNonAdjacentSprinkleCells(availableCells, count, random)

        val boardSize = board.size
        for ((r, c) in selectedCells) {
            val pool = if (language == GameLanguage.JAPANESE) {
                // Rule 1: 'を' already excluded from SPRINKLE_KANA
                // Rule 3: 'ん' can't be on the top row (r == 0) or left-most column (c == 0)
                if (r == 0 || c == 0) {
                    SPRINKLE_KANA.filter { it != 'ん' }
                } else {
                    SPRINKLE_KANA
                }
            } else {
                // Rule 5: 'Q' can't be on the bottom row (r == boardSize - 1) or right-most column (c == boardSize - 1)
                if (r == boardSize - 1 || c == boardSize - 1) {
                    SPRINKLE_LETTERS_EN.filter { it != 'Q' }
                } else {
                    SPRINKLE_LETTERS_EN
                }
            }
            board[r][c] = pool[random.nextInt(pool.size)]
        }

        return selectedCells.toSet()
    }

    /**
     * Verifies that all white (non-blocked) squares on [board] are orthogonally contiguous
     * and that every white square has at least 2 orthogonal white neighbors.
     */
    private fun isValidWhitePattern(board: Array<Array<Char?>>, size: Int): Boolean {
        var firstWhite: Pair<Int, Int>? = null
        var whiteCount = 0
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!isBlockedCell(board[r][c])) {
                    whiteCount++
                    if (firstWhite == null) {
                        firstWhite = r to c
                    }
                    var orthogonalWhiteNeighbors = 0
                    for ((dr, dc) in dirs) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until size && nc in 0 until size && !isBlockedCell(board[nr][nc])) {
                            orthogonalWhiteNeighbors++
                        }
                    }
                    // Disallow singleton white squares and 1-cell dead-end pockets
                    if (orthogonalWhiteNeighbors < 2) {
                        return false
                    }
                }
            }
        }

        val start = firstWhite ?: return false
        if (whiteCount < 4) return false

        // Breadth-first search (BFS) to ensure all white squares belong to one connected component
        val visited = Array(size) { BooleanArray(size) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(start)
        visited[start.first][start.second] = true
        var reached = 0

        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            reached++
            for ((dr, dc) in dirs) {
                val nr = cr + dr
                val nc = cc + dc
                if (nr in 0 until size && nc in 0 until size &&
                    !visited[nr][nc] && !isBlockedCell(board[nr][nc])
                ) {
                    visited[nr][nc] = true
                    queue.add(nr to nc)
                }
            }
        }

        return reached == whiteCount
    }

    /**
     * Sample 6x6 patterned board used to render the static mini-thumbnail on the "Random pattern" button.
     */
    fun samplePatternThumbnailBoard(): Array<Array<Char?>> {
        val pattern = listOf(
            "..#...",
            "......",
            "#...#.",
            ".#...#",
            "......",
            "...#.."
        )
        return Array(6) { r ->
            Array(6) { c ->
                if (pattern[r][c] == '#') BLOCKED_CELL else null
            }
        }
    }

    /**
     * Scans an NxN [board] for all horizontal (left-to-right) and vertical (top-to-bottom)
     * contiguous letter sequences of length >= 2, validates them against [dictionary],
     * and computes the total score and per-cell validation states.
     *
     * - When [scoringMode] is [ScoringMode.CUMULATIVE_EXTENDED], any word in [previouslyValidatedWords]
     *   whose letters remain intact inside a longer valid word in the same direction is retained
     *   in the returned [TallyResult.words] and counted toward [TallyResult.totalScore].
     * - When [allowDuplicateWordPoints] is `false`, placing the same valid word multiple times on
     *   the board remains valid (green on the board), but only the first occurrence of each word
     *   receives points; subsequent occurrences have [FormedWord.isDuplicateUnscored] set to `true`
     *   and award `0` points.
     */
    fun tallyBoard(
        board: Array<Array<Char?>>,
        dictionary: Set<String>,
        scoringMode: ScoringMode = ScoringMode.EXTENDED_DIFF_ONLY,
        previouslyValidatedWords: Collection<FormedWord> = emptyList(),
        allowDuplicateWordPoints: Boolean = true,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet(),
        sprinkledCells: Set<Pair<Int, Int>> = emptySet()
    ): TallyResult {
        val size = board.size

        data class RawCandidate(
            val word: String,
            val direction: WordDirection,
            val startRow: Int,
            val startCol: Int,
            val cells: List<Pair<Int, Int>>,
            val isValid: Boolean
        )

        val rawCandidates = mutableListOf<RawCandidate>()

        // 1. Scan Horizontal words (row by row, left to right)
        for (r in 0 until size) {
            var c = 0
            while (c < size) {
                if (isLetterCell(board[r][c])) {
                    val startC = c
                    val sb = StringBuilder()
                    val cells = mutableListOf<Pair<Int, Int>>()
                    while (c < size && isLetterCell(board[r][c])) {
                        val rawCh = board[r][c]!!
                        val ch = if (isJapaneseChar(rawCh)) rawCh else rawCh.uppercaseChar()
                        sb.append(ch)
                        cells.add(r to c)
                        c++
                    }
                    if (sb.length >= 2) {
                        val text = sb.toString()
                        rawCandidates.add(
                            RawCandidate(
                                word = text,
                                direction = WordDirection.HORIZONTAL,
                                startRow = r,
                                startCol = startC,
                                cells = cells,
                                isValid = dictionary.contains(text)
                            )
                        )
                    }
                } else {
                    c++
                }
            }
        }

        // 2. Scan Vertical words (column by column, top to bottom)
        for (c in 0 until size) {
            var r = 0
            while (r < size) {
                if (isLetterCell(board[r][c])) {
                    val startR = r
                    val sb = StringBuilder()
                    val cells = mutableListOf<Pair<Int, Int>>()
                    while (r < size && isLetterCell(board[r][c])) {
                        val rawCh = board[r][c]!!
                        val ch = if (isJapaneseChar(rawCh)) rawCh else rawCh.uppercaseChar()
                        sb.append(ch)
                        cells.add(r to c)
                        r++
                    }
                    if (sb.length >= 2) {
                        val text = sb.toString()
                        rawCandidates.add(
                            RawCandidate(
                                word = text,
                                direction = WordDirection.VERTICAL,
                                startRow = startR,
                                startCol = c,
                                cells = cells,
                                isValid = dictionary.contains(text)
                            )
                        )
                    }
                } else {
                    r++
                }
            }
        }

        // Score valid words: the first valid word to use each sprinkle claims double points for it
        fun candidateKey(c: RawCandidate) = "${c.direction}_${c.startRow}_${c.startCol}_${c.word}"
        fun formedWordKey(fw: FormedWord) = "${fw.direction}_${fw.startRow}_${fw.startCol}_${fw.word}"

        val prevWordKeys = previouslyValidatedWords
            .filter { it.isValid && !it.isDuplicateUnscored }
            .map { formedWordKey(it) }
            .toSet()

        val validCandidates = rawCandidates.filter { it.isValid }
        // Preserve claimed sprinkles for words previously validated in earlier turns
        val sortedValidCandidates = validCandidates.sortedBy { cand ->
            if (candidateKey(cand) in prevWordKeys) 0 else 1
        }

        val claimedSprinkles = mutableSetOf<Pair<Int, Int>>()
        val rawPointsByCandidateKey = mutableMapOf<String, Int>()

        for (cand in sortedValidCandidates) {
            val (points, newlyClaimed) = calculateWordRawScore(
                word = cand.word,
                cells = cand.cells,
                sprinkledCells = sprinkledCells,
                claimedSprinkles = claimedSprinkles,
                language = language,
                japaneseVerbRule = japaneseVerbRule,
                verbDictionary = verbDictionary
            )
            claimedSprinkles.addAll(newlyClaimed)
            rawPointsByCandidateKey[candidateKey(cand)] = points
        }

        val foundWords = rawCandidates.map { cand ->
            val points = if (cand.isValid) {
                rawPointsByCandidateKey[candidateKey(cand)] ?: 0
            } else {
                calculateWordRawScore(
                    word = cand.word,
                    cells = cand.cells,
                    sprinkledCells = emptySet(),
                    claimedSprinkles = emptySet(),
                    language = language,
                    japaneseVerbRule = japaneseVerbRule,
                    verbDictionary = verbDictionary
                ).first
            }
            FormedWord(
                word = cand.word,
                direction = cand.direction,
                startRow = cand.startRow,
                startCol = cand.startCol,
                cells = cand.cells,
                rawPoints = points,
                isValid = cand.isValid
            )
        }

        // 3. Determine per-cell validation state (invalid words override valid words on shared intersections)
        val cellsInValidWords = foundWords
            .filter { it.isValid }
            .flatMap { it.cells }
            .toSet()

        val cellsInInvalidWords = foundWords
            .filter { !it.isValid }
            .flatMap { it.cells }
            .toSet()

        val isolated = mutableListOf<Pair<Int, Int>>()
        val cellValidations = mutableMapOf<Pair<Int, Int>, CellValidation>()

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isLetterCell(board[r][c])) {
                    val pos = r to c
                    when {
                        pos in cellsInInvalidWords -> {
                            cellValidations[pos] = CellValidation.INVALID
                        }
                        pos in cellsInValidWords -> {
                            cellValidations[pos] = CellValidation.VALID
                        }
                        else -> {
                            isolated.add(pos)
                            cellValidations[pos] = CellValidation.INVALID
                        }
                    }
                }
            }
        }

        // 4. Combine retained base words if CUMULATIVE_EXTENDED scoring mode is active
        val combinedWords = if (scoringMode == ScoringMode.CUMULATIVE_EXTENDED && previouslyValidatedWords.isNotEmpty()) {
            val combined = mutableListOf<FormedWord>()
            val seenKeys = HashSet<String>()
            for (curr in foundWords) {
                if (curr.isValid) {
                    val currCellSet = curr.cells.toSet()
                    val extendedBaseWords = previouslyValidatedWords
                        .filter { prev ->
                            dictionary.contains(prev.word) &&
                                prev.direction == curr.direction &&
                                prev.cells.size < curr.cells.size &&
                                currCellSet.containsAll(prev.cells) &&
                                prev.cells.indices.all { idx ->
                                    val (pr, pc) = prev.cells[idx]
                                    pr in 0 until size && pc in 0 until size &&
                                        board[pr][pc]?.let {
                                            val c = if (isJapaneseChar(it)) it else it.uppercaseChar()
                                            c == prev.word[idx]
                                        } == true
                                }
                        }
                        .sortedBy { it.cells.size }

                    for (baseWord in extendedBaseWords) {
                        val key = "${baseWord.direction}_${baseWord.startRow}_${baseWord.startCol}_${baseWord.word}"
                        if (seenKeys.add(key)) {
                            combined.add(
                                baseWord.copy(
                                    rawPoints = wordRawScore(baseWord.word, language, japaneseVerbRule, verbDictionary),
                                    isValid = true,
                                    isDuplicateUnscored = false
                                )
                            )
                        }
                    }
                }
                val currKey = "${curr.direction}_${curr.startRow}_${curr.startCol}_${curr.word}"
                if (seenKeys.add(currKey)) {
                    combined.add(curr)
                }
            }
            combined
        } else {
            foundWords
        }

        // 5. Apply duplicate-word scoring rule when allowDuplicateWordPoints == false:
        //    Only the first validated instance of each word receives points; additional instances
        //    remain valid on the board but receive 0 points (isDuplicateUnscored = true).
        val allScoredWords = if (!allowDuplicateWordPoints) {
            fun instanceKey(fw: FormedWord) = "${fw.direction}_${fw.startRow}_${fw.startCol}_${fw.word}"

            val validByWord = combinedWords.filter { it.isValid }.groupBy { it.word }
            val primaryKeyByWord = HashMap<String, String>()

            for ((wordText, instances) in validByWord) {
                if (instances.size == 1) {
                    primaryKeyByWord[wordText] = instanceKey(instances[0])
                } else {
                    val instanceKeys = instances.map { instanceKey(it) }.toSet()
                    // Prefer whichever current instance was validated earliest during this game
                    val earliestHistorical = previouslyValidatedWords.firstOrNull { prev ->
                        prev.word == wordText && instanceKey(prev) in instanceKeys
                    }
                    primaryKeyByWord[wordText] = if (earliestHistorical != null) {
                        instanceKey(earliestHistorical)
                    } else {
                        instanceKey(instances[0])
                    }
                }
            }

            combinedWords.map { fw ->
                if (!fw.isValid) {
                    fw
                } else {
                    val isPrimary = primaryKeyByWord[fw.word] == instanceKey(fw)
                    fw.copy(isDuplicateUnscored = !isPrimary)
                }
            }
        } else {
            combinedWords.map { fw ->
                if (fw.isDuplicateUnscored) fw.copy(isDuplicateUnscored = false) else fw
            }
        }

        val totalScore = allScoredWords.sumOf { it.awardedPoints }

        return TallyResult(
            totalScore = totalScore,
            words = allScoredWords,
            isolatedCells = isolated,
            cellValidations = cellValidations
        )
    }

    /**
     * Populates a sample crossword layout onto a board of the given [size] and [language].
     */
    fun exampleBoard(size: Int = BOARD_SIZE, language: GameLanguage = GameLanguage.ENGLISH): Array<Array<Char?>> {
        val rows = if (language == GameLanguage.JAPANESE) {
            listOf(
                "はっけん  ",
                "な む   ",
                "  りつみん",
                "    ず ",
                "   ぬき ",
                "      "
            )
        } else {
            listOf(
                "SALUTE",
                "H   OR",
                "OVERT ",
                "E  A  ",
                "SALVE ",
                " T E  "
            )
        }
        return Array(size) { r ->
            Array(size) { c ->
                val ch = rows.getOrNull(r)?.getOrNull(c) ?: ' '
                if (ch == ' ') null else ch
            }
        }
    }
}
