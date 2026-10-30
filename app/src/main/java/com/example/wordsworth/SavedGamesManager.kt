package com.example.wordsworth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a completed or paused game saved in the "Previous Games" column.
 *
 * @property id Unique millisecond timestamp identifier of this saved entry.
 * @property score Tallied score at the moment the game was saved.
 * @property elapsedSeconds Total elapsed seconds on the timer when saved.
 * @property timestamp Human-readable date/time string (e.g., `"Oct 22, 21:30"`).
 * @property screenshotPath Absolute path to the PNG board thumbnail on internal storage.
 * @property bitmap Decoded [Bitmap] thumbnail for fast rendering in the UI.
 * @property board Optional 2D array snapshot of the board's cells (`null` = empty, `'#'` = blocked, `'A'..'Z'` = letter)
 *   allowing the player to tap the entry and resume playing.
 * @property validatedWords Previously validated [FormedWord]s from that session, preserved so
 *   [ScoringMode.CUMULATIVE_EXTENDED] retains base word points after resuming.
 */
data class SavedGameEntry(
    val id: Long,
    val score: Int,
    val elapsedSeconds: Int,
    val timestamp: String,
    val screenshotPath: String,
    val bitmap: Bitmap?,
    val board: Array<Array<Char?>>? = null,
    val validatedWords: List<FormedWord> = emptyList(),
    val language: GameLanguage = GameLanguage.ENGLISH,
    val sprinkledCells: Set<Pair<Int, Int>> = emptySet()
)

/**
 * State snapshot for a single language session (English or Japanese) to survive process death.
 */
data class LanguageSessionState(
    val board: Array<Array<Char?>>,
    val sprinkledCells: Set<Pair<Int, Int>> = emptySet(),
    val elapsedSeconds: Int = 0,
    val validatedWords: List<FormedWord> = emptyList()
)

/**
 * Full state snapshot of active boards and game options to restore uninterrupted play across process death.
 */
data class ActiveSessionState(
    val activeLanguage: GameLanguage,
    val boardSize: Int,
    val isRandomPatternMode: Boolean,
    val resumedGameId: Long?,
    val timeAttackMinutes: Int?,
    val isBoardLocked: Boolean,
    val enSession: LanguageSessionState,
    val jaSession: LanguageSessionState
)

/**
 * Manages persistence, JSON serialization, and off-screen Canvas thumbnail rendering
 * for saved WordsWorth games.
 */
object SavedGamesManager {
    private const val DIR_NAME = "saved_boards"
    private const val META_FILE = "saved_games.json"
    private const val ACTIVE_SESSION_FILE = "active_session.json"

    /**
     * Serializes a 2D board array into a nested [JSONArray] of single-character strings
     * (or `""` for empty squares).
     */
    private fun serializeBoard(board: Array<Array<Char?>>): JSONArray {
        val rowsArray = JSONArray()
        for (row in board) {
            val colsArray = JSONArray()
            for (cell in row) {
                colsArray.put(cell?.toString() ?: "")
            }
            rowsArray.put(colsArray)
        }
        return rowsArray
    }

    /**
     * Reconstructs a 2D board array from a serialized [JSONArray], or returns `null`
     * for legacy entries saved before board state persistence was added.
     */
    private fun deserializeBoard(rowsArray: JSONArray?): Array<Array<Char?>>? {
        if (rowsArray == null || rowsArray.length() == 0) return null
        val size = rowsArray.length()
        return Array(size) { r ->
            val colsArray = rowsArray.optJSONArray(r)
            Array(size) { c ->
                val str = colsArray?.optString(c, "") ?: ""
                if (str.isNotEmpty()) str[0] else null
            }
        }
    }

    /**
     * Serializes sprinkled letter coordinates `(row, col)` into a [JSONArray].
     */
    private fun serializeSprinkledCells(cells: Set<Pair<Int, Int>>): JSONArray {
        val arr = JSONArray()
        for ((r, c) in cells) {
            val obj = JSONObject().apply {
                put("r", r)
                put("c", c)
            }
            arr.put(obj)
        }
        return arr
    }

    /**
     * Deserializes sprinkled letter coordinates from [JSONArray], returning empty set if absent.
     */
    private fun deserializeSprinkledCells(arr: JSONArray?): Set<Pair<Int, Int>> {
        if (arr == null || arr.length() == 0) return emptySet()
        val result = mutableSetOf<Pair<Int, Int>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val r = obj.optInt("r", -1)
            val c = obj.optInt("c", -1)
            if (r >= 0 && c >= 0) {
                result.add(r to c)
            }
        }
        return result
    }

    /**
     * Serializes the list of valid [FormedWord]s so base words validated before an extension
     * can be restored when a saved game is resumed.
     */
    private fun serializeValidatedWords(words: List<FormedWord>): JSONArray {
        val arr = JSONArray()
        for (fw in words) {
            if (!fw.isValid) continue
            val obj = JSONObject().apply {
                put("word", fw.word)
                put("direction", fw.direction.name)
                put("startRow", fw.startRow)
                put("startCol", fw.startCol)
                put("rawPoints", fw.rawPoints)
            }
            arr.put(obj)
        }
        return arr
    }

    /**
     * Deserializes previously validated [FormedWord]s from a saved game's JSON metadata.
     */
    private fun deserializeValidatedWords(arr: JSONArray?, language: GameLanguage = GameLanguage.ENGLISH): List<FormedWord> {
        if (arr == null || arr.length() == 0) return emptyList()
        val result = mutableListOf<FormedWord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawWord = obj.optString("word", "")
            val word = if (language == GameLanguage.ENGLISH) rawWord.uppercase() else rawWord
            if (word.length < 2) continue
            val dirName = obj.optString("direction", WordDirection.HORIZONTAL.name)
            val direction = try {
                WordDirection.valueOf(dirName)
            } catch (_: Exception) {
                WordDirection.HORIZONTAL
            }
            val startRow = obj.optInt("startRow", 0)
            val startCol = obj.optInt("startCol", 0)
            val cells = word.indices.map { idx ->
                when (direction) {
                    WordDirection.HORIZONTAL -> startRow to (startCol + idx)
                    WordDirection.VERTICAL -> (startRow + idx) to startCol
                }
            }
            val savedPoints = if (obj.has("rawPoints")) {
                obj.optInt("rawPoints", 0)
            } else {
                WordsWorthLogic.wordRawScore(word, language)
            }
            result.add(
                FormedWord(
                    word = word,
                    direction = direction,
                    startRow = startRow,
                    startCol = startCol,
                    cells = cells,
                    rawPoints = savedPoints,
                    isValid = true
                )
            )
        }
        return result
    }

    /** Returns (and creates if necessary) the internal storage directory for saved game assets. */
    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Renders a crisp 420x420px [Bitmap] screenshot of the NxN [board] matching the on-screen
     * board's colors, cell borders, letters, point subscripts, and validation states.
     */
    fun createBoardScreenshot(
        board: Array<Array<Char?>>,
        cellValidations: Map<Pair<Int, Int>, CellValidation>,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet()
    ): Bitmap {
        val boardSize = board.size
        val sizePx = 420
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Outer board frame background (#4A3222)
        paint.color = 0xFF4A3222.toInt()
        val outerRect = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.drawRoundRect(outerRect, 20f, 20f, paint)

        val padding = 10f
        val gap = when {
            boardSize >= 12 -> 3f
            boardSize >= 9 -> 4.5f
            else -> 6f
        }
        val cornerRadius = when {
            boardSize >= 12 -> 5f
            boardSize >= 9 -> 7f
            else -> 10f
        }
        val totalGap = gap * (boardSize - 1)
        val cellSize = (sizePx - padding * 2 - totalGap) / boardSize

        letterPaint.textSize = cellSize * 0.50f
        valuePaint.textSize = cellSize * 0.22f

        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val left = padding + c * (cellSize + gap)
                val top = padding + r * (cellSize + gap)
                val cellRect = RectF(left, top, left + cellSize, top + cellSize)

                val ch = board[r][c]
                val isBlocked = WordsWorthLogic.isBlockedCell(ch)
                val validation = cellValidations[r to c] ?: CellValidation.UNCHECKED

                val bgColor = when {
                    isBlocked -> 0xFF161311.toInt()
                    ch == null -> 0xFFEDE4D3.toInt()
                    validation == CellValidation.VALID -> 0xFFE8F5E9.toInt()
                    validation == CellValidation.INVALID -> 0xFFFFEBEE.toInt()
                    else -> 0xFFFFF8E1.toInt()
                }

                val strokeColor = when {
                    isBlocked -> 0xFF0B0908.toInt()
                    validation == CellValidation.VALID -> 0xFF2E7D32.toInt()
                    validation == CellValidation.INVALID -> 0xFFC62828.toInt()
                    ch != null -> 0xFFB89B72.toInt()
                    else -> 0xFFC5B69E.toInt()
                }

                paint.color = bgColor
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, paint)

                borderPaint.color = strokeColor
                borderPaint.strokeWidth = if (validation != CellValidation.UNCHECKED) 2.5f else 1.2f
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, borderPaint)

                if (WordsWorthLogic.isLetterCell(ch)) {
                    val letterChar = ch!!
                    val textColor = when (validation) {
                        CellValidation.VALID -> 0xFF1B5E20.toInt()
                        CellValidation.INVALID -> 0xFFB71C1C.toInt()
                        CellValidation.UNCHECKED -> 0xFF2C1E18.toInt()
                    }
                    letterPaint.color = textColor
                    val centerX = cellRect.centerX()
                    val centerY = cellRect.centerY() - (letterPaint.descent() + letterPaint.ascent()) / 2f - 1f
                    canvas.drawText(letterChar.toString(), centerX, centerY, letterPaint)

                    valuePaint.color = textColor
                    val pts = WordsWorthLogic.cellDisplayString(board, r, c, language, japaneseVerbRule, verbDictionary)
                    if (pts.length > 2) {
                        valuePaint.textSize = (cellSize * 0.22f).coerceIn(7f, 18f)
                    } else {
                        valuePaint.textSize = (cellSize * 0.28f).coerceIn(10f, 24f)
                    }
                    val inset = (cellSize * 0.08f).coerceAtLeast(2f)
                    val yPos = if (pts.contains('・')) {
                        cellRect.bottom - (inset * 0.5f)
                    } else {
                        cellRect.bottom - inset
                    }
                    canvas.drawText(
                        pts,
                        cellRect.right - inset,
                        yPos,
                        valuePaint
                    )
                }
            }
        }

        return bitmap
    }

    /**
     * Saves the given [board], [tallyResult], and [elapsedSeconds] to disk and returns the
     * resulting [SavedGameEntry].
     *
     * @param replaceId Optional ID of an existing saved game that was resumed; if non-null,
     *   the old entry and its PNG screenshot are replaced so re-saving updates in place.
     */
    fun saveGame(
        context: Context,
        board: Array<Array<Char?>>,
        tallyResult: TallyResult,
        elapsedSeconds: Int,
        replaceId: Long? = null,
        language: GameLanguage? = null,
        japaneseVerbRule: JapaneseVerbRule = JapaneseVerbRule.CABOOSE,
        verbDictionary: Set<String> = emptySet(),
        sprinkledCells: Set<Pair<Int, Int>> = emptySet()
    ): SavedGameEntry {
        val dir = getStorageDir(context)
        val id = System.currentTimeMillis()
        val bitmap = createBoardScreenshot(board, tallyResult.cellValidations, language, japaneseVerbRule, verbDictionary)
        val imageFile = File(dir, "board_$id.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val boardCopy = Array(board.size) { r ->
            Array(board.size) { c -> board[r][c] }
        }
        val validWordsSnapshot = tallyResult.validWords
        val timestamp = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(id))
        val gameLang = language ?: GameLanguage.ENGLISH
        val newEntry = SavedGameEntry(
            id = id,
            score = tallyResult.totalScore,
            elapsedSeconds = elapsedSeconds,
            timestamp = timestamp,
            screenshotPath = imageFile.absolutePath,
            bitmap = bitmap,
            board = boardCopy,
            validatedWords = validWordsSnapshot,
            language = gameLang,
            sprinkledCells = sprinkledCells
        )

        val currentArray = readMetadataArray(context)
        val newObj = JSONObject().apply {
            put("id", newEntry.id)
            put("score", newEntry.score)
            put("elapsedSeconds", newEntry.elapsedSeconds)
            put("timestamp", newEntry.timestamp)
            put("screenshotPath", newEntry.screenshotPath)
            put("board", serializeBoard(boardCopy))
            put("validatedWords", serializeValidatedWords(validWordsSnapshot))
            put("language", gameLang.name)
            put("sprinkledCells", serializeSprinkledCells(sprinkledCells))
        }

        val updatedArray = JSONArray()
        updatedArray.put(newObj)
        for (i in 0 until currentArray.length()) {
            val existingObj = currentArray.optJSONObject(i) ?: continue
            if (replaceId != null && existingObj.optLong("id", -1L) == replaceId) {
                val oldPath = existingObj.optString("screenshotPath", "")
                if (oldPath.isNotEmpty() && oldPath != imageFile.absolutePath) {
                    File(oldPath).delete()
                }
            } else {
                updatedArray.put(existingObj)
            }
        }
        File(dir, META_FILE).writeText(updatedArray.toString(2))

        return newEntry
    }

    /**
     * Loads all saved game entries from `saved_games.json` in reverse-chronological order.
     * Bitmaps are decoded lazily on demand when displayed to keep startup memory low.
     */
    fun loadSavedGames(context: Context, decodeBitmaps: Boolean = false): List<SavedGameEntry> {
        val array = readMetadataArray(context)
        val result = mutableListOf<SavedGameEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val path = obj.optString("screenshotPath", "")
            val bmp = if (decodeBitmaps && path.isNotEmpty() && File(path).exists()) {
                BitmapFactory.decodeFile(path)
            } else {
                null
            }
            val savedBoard = deserializeBoard(obj.optJSONArray("board"))
            val langStr = obj.optString("language", "")
            val entryLanguage = if (langStr.isNotEmpty()) {
                try {
                    GameLanguage.valueOf(langStr)
                } catch (_: Exception) {
                    GameLanguage.ENGLISH
                }
            } else {
                if (savedBoard?.any { row -> row.any { ch -> ch != null && WordsWorthLogic.isJapaneseChar(ch) } } == true) {
                    GameLanguage.JAPANESE
                } else {
                    GameLanguage.ENGLISH
                }
            }
            val savedValidatedWords = deserializeValidatedWords(obj.optJSONArray("validatedWords"), entryLanguage)
            val sprinkled = deserializeSprinkledCells(obj.optJSONArray("sprinkledCells"))
            result.add(
                SavedGameEntry(
                    id = obj.optLong("id", 0L),
                    score = obj.optInt("score", 0),
                    elapsedSeconds = obj.optInt("elapsedSeconds", 0),
                    timestamp = obj.optString("timestamp", ""),
                    screenshotPath = path,
                    bitmap = bmp,
                    board = savedBoard,
                    validatedWords = savedValidatedWords,
                    language = entryLanguage,
                    sprinkledCells = sprinkled
                )
            )
        }
        return result
    }

    /** Deletes a single saved game entry and its PNG screenshot by [id]. */
    fun deleteSavedGame(context: Context, id: Long) {
        val dir = getStorageDir(context)
        val currentArray = readMetadataArray(context)
        val updatedArray = JSONArray()
        for (i in 0 until currentArray.length()) {
            val obj = currentArray.optJSONObject(i) ?: continue
            if (obj.optLong("id", -1L) == id) {
                val path = obj.optString("screenshotPath", "")
                if (path.isNotEmpty()) {
                    File(path).delete()
                }
            } else {
                updatedArray.put(obj)
            }
        }
        File(dir, META_FILE).writeText(updatedArray.toString(2))
    }

    /** Deletes all saved game metadata and screenshot files. */
    fun clearAllSavedGames(context: Context) {
        val dir = getStorageDir(context)
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Persists active board states, sprinkled cells, and timer progression to disk so games
     * can be seamlessly resumed without progress loss if the OS terminates the process in the background.
     */
    fun saveActiveSession(context: Context, session: ActiveSessionState) {
        try {
            val root = JSONObject().apply {
                put("activeLanguage", session.activeLanguage.name)
                put("boardSize", session.boardSize)
                put("isRandomPatternMode", session.isRandomPatternMode)
                put("resumedGameId", session.resumedGameId ?: JSONObject.NULL)
                put("timeAttackMinutes", session.timeAttackMinutes ?: JSONObject.NULL)
                put("isBoardLocked", session.isBoardLocked)

                val enObj = JSONObject().apply {
                    put("board", serializeBoard(session.enSession.board))
                    put("sprinkledCells", serializeSprinkledCells(session.enSession.sprinkledCells))
                    put("elapsedSeconds", session.enSession.elapsedSeconds)
                    put("validatedWords", serializeValidatedWords(session.enSession.validatedWords))
                }
                put("en", enObj)

                val jaObj = JSONObject().apply {
                    put("board", serializeBoard(session.jaSession.board))
                    put("sprinkledCells", serializeSprinkledCells(session.jaSession.sprinkledCells))
                    put("elapsedSeconds", session.jaSession.elapsedSeconds)
                    put("validatedWords", serializeValidatedWords(session.jaSession.validatedWords))
                }
                put("ja", jaObj)
            }
            val file = File(context.filesDir, ACTIVE_SESSION_FILE)
            file.writeText(root.toString())
        } catch (_: Exception) {
            // Silently ignore disk write issues on background transitions
        }
    }

    /**
     * Restores active board session state previously serialized during [saveActiveSession],
     * or returns `null` if no active session file is present or it fails to parse.
     */
    fun loadActiveSession(context: Context): ActiveSessionState? {
        val file = File(context.filesDir, ACTIVE_SESSION_FILE)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            val activeLang = try {
                GameLanguage.valueOf(root.optString("activeLanguage", "ENGLISH"))
            } catch (_: Exception) {
                GameLanguage.ENGLISH
            }
            val boardSize = root.optInt("boardSize", WordsWorthLogic.BOARD_SIZE)
            val isRandomPattern = root.optBoolean("isRandomPatternMode", false)
            val resumedGameId = if (root.isNull("resumedGameId")) null else root.optLong("resumedGameId")
            val timeAttackMinutes = if (root.isNull("timeAttackMinutes")) null else root.optInt("timeAttackMinutes")
            val isBoardLocked = root.optBoolean("isBoardLocked", false)

            val enObj = root.optJSONObject("en")
            val enBoard = deserializeBoard(enObj?.optJSONArray("board")) ?: WordsWorthLogic.createBlankBoard(boardSize)
            val enSprinkled = deserializeSprinkledCells(enObj?.optJSONArray("sprinkledCells"))
            val enElapsed = enObj?.optInt("elapsedSeconds", 0) ?: 0
            val enValidated = deserializeValidatedWords(enObj?.optJSONArray("validatedWords"))

            val jaObj = root.optJSONObject("ja")
            val jaBoard = deserializeBoard(jaObj?.optJSONArray("board")) ?: WordsWorthLogic.createBlankBoard(boardSize)
            val jaSprinkled = deserializeSprinkledCells(jaObj?.optJSONArray("sprinkledCells"))
            val jaElapsed = jaObj?.optInt("elapsedSeconds", 0) ?: 0
            val jaValidated = deserializeValidatedWords(jaObj?.optJSONArray("validatedWords"))

            ActiveSessionState(
                activeLanguage = activeLang,
                boardSize = boardSize,
                isRandomPatternMode = isRandomPattern,
                resumedGameId = resumedGameId,
                timeAttackMinutes = timeAttackMinutes,
                isBoardLocked = isBoardLocked,
                enSession = LanguageSessionState(enBoard, enSprinkled, enElapsed, enValidated),
                jaSession = LanguageSessionState(jaBoard, jaSprinkled, jaElapsed, jaValidated)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears any active game session file (e.g. when the player resets the board or completes the round).
     */
    fun clearActiveSession(context: Context) {
        try {
            val file = File(context.filesDir, ACTIVE_SESSION_FILE)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun readMetadataArray(context: Context): JSONArray {
        val metaFile = File(getStorageDir(context), META_FILE)
        if (!metaFile.exists()) return JSONArray()
        return try {
            JSONArray(metaFile.readText())
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
