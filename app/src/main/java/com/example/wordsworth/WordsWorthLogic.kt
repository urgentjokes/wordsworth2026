package com.example.wordsworth

import android.content.Context

enum class WordDirection(val label: String) {
    HORIZONTAL("Across"),
    VERTICAL("Down")
}

enum class CellValidation {
    UNCHECKED,
    VALID,
    INVALID
}

data class FormedWord(
    val word: String,
    val direction: WordDirection,
    val startRow: Int,
    val startCol: Int,
    val cells: List<Pair<Int, Int>>,
    val rawPoints: Int,
    val isValid: Boolean
) {
    val awardedPoints: Int
        get() = if (isValid) rawPoints else 0
}

data class TallyResult(
    val totalScore: Int,
    val words: List<FormedWord>,
    val isolatedCells: List<Pair<Int, Int>>,
    val cellValidations: Map<Pair<Int, Int>, CellValidation>
) {
    val validWords: List<FormedWord>
        get() = words.filter { it.isValid }

    val invalidWords: List<FormedWord>
        get() = words.filter { !it.isValid }
}

object WordsWorthLogic {
    const val BOARD_SIZE = 6
    const val MAX_BOARD_SIZE = 12
    val BOARD_SIZES = listOf(6, 9, 12)
    const val BLOCKED_CELL = '#'

    fun isLetterCell(ch: Char?): Boolean = ch != null && ch != BLOCKED_CELL
    fun isBlockedCell(ch: Char?): Boolean = ch == BLOCKED_CELL

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

    fun letterValue(ch: Char): Int = LETTER_VALUES[ch.uppercaseChar()] ?: 0

    fun wordRawScore(word: String): Int = word.sumOf { letterValue(it) }

    private const val WHITELIST_FILE = "whitelisted_words.txt"

    fun loadDictionary(context: Context): Set<String> {
        val baseWords = try {
            context.assets.open("words.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim().uppercase() }
                    .filter { it.length in 2..MAX_BOARD_SIZE }
                    .toHashSet()
            }
        } catch (e: Exception) {
            mutableSetOf("SALUTE", "SHOES", "OVERT", "TOT", "ER", "RAVE", "AT", "SALVE", "OR")
        }
        baseWords.addAll(loadWhitelist(context))
        return baseWords
    }

    fun loadWhitelist(context: Context): Set<String> {
        val file = java.io.File(context.filesDir, WHITELIST_FILE)
        if (!file.exists()) return emptySet()
        return try {
            file.readLines()
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun addWordToWhitelist(context: Context, word: String): Set<String> {
        val normalized = word.trim().uppercase()
        if (normalized.isEmpty()) return loadWhitelist(context)
        val current = loadWhitelist(context).toMutableSet()
        current.add(normalized)
        try {
            val file = java.io.File(context.filesDir, WHITELIST_FILE)
            file.writeText(current.sorted().joinToString("\n"))
        } catch (_: Exception) {
        }
        return current
    }

    /**
     * Generates an all-blank board of the given size.
     */
    fun createBlankBoard(size: Int): Array<Array<Char?>> {
        return Array(size) {
            Array<Char?>(size) { null }
        }
    }

    /**
     * Generates a random-pattern board where a random number and set of squares are blacked-out
     * (BLOCKED_CELL), while guaranteeing that no white squares are singletons and all white
     * squares are orthogonally contiguous.
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
                board[r][c] = null
            }
        }

        return board
    }

    /**
     * Checks that:
     * 1. All white (non-blocked) squares form a single orthogonally contiguous component.
     * 2. No white square is a singleton (every white square has at least 2 orthogonal white
     *    neighbors, ensuring no isolated cells or 1-square dead-end pockets).
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
                    // Prevent singleton white squares and 1-cell dead-end pockets
                    if (orthogonalWhiteNeighbors < 2) {
                        return false
                    }
                }
            }
        }

        val start = firstWhite ?: return false
        if (whiteCount < 4) return false

        // BFS to verify all white cells are contiguous
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
     * Sample patterned 6x6 board used to render the thumbnail next to the "Random pattern" option.
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
     * Evaluates an NxN board and returns all horizontal (left-to-right) and vertical (top-to-bottom)
     * words of length >= 2, along with their validity and total score.
     */
    fun tallyBoard(
        board: Array<Array<Char?>>,
        dictionary: Set<String>
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

        // 2. Scan Vertical words (col by col, top to bottom)
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

        val totalScore = foundWords.sumOf { it.awardedPoints }

        return TallyResult(
            totalScore = totalScore,
            words = foundWords,
            isolatedCells = isolated,
            cellValidations = cellValidations
        )
    }

    /**
     * Example board from wordsworth-spec.txt placed onto a board of the given size:
     * SALUTE
     * H   OR
     * OVERT
     * E  A
     * SALVE
     *  T E
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
