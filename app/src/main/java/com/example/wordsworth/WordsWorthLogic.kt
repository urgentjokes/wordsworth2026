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

    /**
     * 3x9 alphabetical key matrix (`null` in the bottom-right corner represents the `CLR` key).
     */
    val ALPHABETICAL_KEYBOARD_ROWS: List<List<Char?>> = listOf(
        listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'),
        listOf('J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R'),
        listOf('S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', null)
    )

    /**
     * Standard QWERTY key matrix (`null` in the bottom-right corner represents the `CLR` key).
     */
    val QWERTY_KEYBOARD_ROWS: List<List<Char?>> = listOf(
        listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
        listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
        listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M', null)
    )

    /** Returns the row/column key arrangement for the given [KeyboardLayout]. */
    fun keyboardRows(layout: KeyboardLayout): List<List<Char?>> = when (layout) {
        KeyboardLayout.ALPHABETICAL -> ALPHABETICAL_KEYBOARD_ROWS
        KeyboardLayout.QWERTY -> QWERTY_KEYBOARD_ROWS
    }

    private const val PREFS_NAME = "wordsworth_prefs"
    private const val KEY_KEYBOARD_LAYOUT = "keyboard_layout"
    private const val KEY_SCORING_MODE = "scoring_mode"
    private const val KEY_ALLOW_DUPLICATE_WORD_POINTS = "allow_duplicate_word_points"

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

    /** Returns the point value for a single character tile (case-insensitive). */
    fun letterValue(ch: Char): Int = LETTER_VALUES[ch.uppercaseChar()] ?: 0

    /** Returns the total raw point value of all letters in [word]. */
    fun wordRawScore(word: String): Int = word.sumOf { letterValue(it) }

    private const val WHITELIST_FILE = "whitelisted_words.txt"

    /**
     * Loads the base word list from `assets/words.txt` (filtered to 2..[MAX_BOARD_SIZE] letters)
     * and merges in any custom words added to the user's local whitelist.
     */
    fun loadDictionary(context: Context): Set<String> {
        val baseWords = try {
            context.assets.open("words.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().uppercase() }
                    .filter { it.length in 2..MAX_BOARD_SIZE }
                    .toHashSet()
            }
        } catch (_: Exception) {
            mutableSetOf("SALUTE", "SHOES", "OVERT", "TOT", "ER", "RAVE", "AT", "SALVE", "OR")
        }
        baseWords.addAll(loadWhitelist(context))
        return baseWords
    }

    /** Reads the user's custom whitelisted words from internal storage. */
    fun loadWhitelist(context: Context): Set<String> {
        val file = File(context.filesDir, WHITELIST_FILE)
        if (!file.exists()) return emptySet()
        return try {
            file.readLines()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Adds [word] to the persistent user whitelist file and returns the updated set. */
    fun addWordToWhitelist(context: Context, word: String): Set<String> {
        val normalized = word.trim().uppercase()
        if (normalized.isEmpty()) return loadWhitelist(context)
        val current = loadWhitelist(context).toMutableSet()
        current.add(normalized)
        try {
            val file = File(context.filesDir, WHITELIST_FILE)
            file.writeText(current.sorted().joinToString("\n"))
        } catch (_: Exception) {
        }
        return current
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
        allowDuplicateWordPoints: Boolean = true
    ): TallyResult {
        val size = board.size
        val foundWords = mutableListOf<FormedWord>()

        // 1. Scan Horizontal words (row by row, left to right)
        for (r in 0 until size) {
            var c = 0
            while (c < size) {
                if (isLetterCell(board[r][c])) {
                    val startC = c
                    val sb = StringBuilder()
                    val cells = mutableListOf<Pair<Int, Int>>()
                    while (c < size && isLetterCell(board[r][c])) {
                        val ch = board[r][c]!!.uppercaseChar()
                        sb.append(ch)
                        cells.add(r to c)
                        c++
                    }
                    if (sb.length >= 2) {
                        val text = sb.toString()
                        foundWords.add(
                            FormedWord(
                                word = text,
                                direction = WordDirection.HORIZONTAL,
                                startRow = r,
                                startCol = startC,
                                cells = cells,
                                rawPoints = wordRawScore(text),
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
                        val ch = board[r][c]!!.uppercaseChar()
                        sb.append(ch)
                        cells.add(r to c)
                        r++
                    }
                    if (sb.length >= 2) {
                        val text = sb.toString()
                        foundWords.add(
                            FormedWord(
                                word = text,
                                direction = WordDirection.VERTICAL,
                                startRow = startR,
                                startCol = c,
                                cells = cells,
                                rawPoints = wordRawScore(text),
                                isValid = dictionary.contains(text)
                            )
                        )
                    }
                } else {
                    r++
                }
            }
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
                                        board[pr][pc]?.uppercaseChar() == prev.word[idx]
                                }
                        }
                        .sortedBy { it.cells.size }

                    for (baseWord in extendedBaseWords) {
                        val key = "${baseWord.direction}_${baseWord.startRow}_${baseWord.startCol}_${baseWord.word}"
                        if (seenKeys.add(key)) {
                            combined.add(
                                baseWord.copy(
                                    rawPoints = wordRawScore(baseWord.word),
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
     * Populates a sample crossword layout onto a board of the given [size]:
     * ```
     * SALUTE
     * H   OR
     * OVERT
     * E  A
     * SALVE
     *  T E
     * ```
     */
    fun exampleBoard(size: Int = BOARD_SIZE): Array<Array<Char?>> {
        val rows = listOf(
            "SALUTE",
            "H   OR",
            "OVERT ",
            "E  A  ",
            "SALVE ",
            " T E  "
        )
        return Array(size) { r ->
            Array(size) { c ->
                val ch = rows.getOrNull(r)?.getOrNull(c) ?: ' '
                if (ch == ' ') null else ch
            }
        }
    }
}
