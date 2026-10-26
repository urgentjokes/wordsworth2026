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
    val language: GameLanguage = GameLanguage.ENGLISH
)

/**
 * Manages persistence, JSON serialization, and off-screen Canvas thumbnail rendering
 * for saved WordsWorth games.
 */
object SavedGamesManager {
    private const val DIR_NAME = "saved_boards"
    private const val META_FILE = "saved_games.json"

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
            }
            arr.put(obj)
        }
        return arr
    }

    /**
     * Deserializes previously validated [FormedWord]s from a saved game's JSON metadata.
     */
    private fun deserializeValidatedWords(arr: JSONArray?): List<FormedWord> {
        if (arr == null || arr.length() == 0) return emptyList()
        val result = mutableListOf<FormedWord>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val word = obj.optString("word", "").uppercase()
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
            result.add(
                FormedWord(
                    word = word,
                    direction = direction,
                    startRow = startRow,
                    startCol = startCol,
                    cells = cells,
                    rawPoints = WordsWorthLogic.wordRawScore(word),
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
        verbDictionary: Set<String> = emptySet()
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
            language = gameLang
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
     */
    fun loadSavedGames(context: Context): List<SavedGameEntry> {
        val array = readMetadataArray(context)
        val result = mutableListOf<SavedGameEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val path = obj.optString("screenshotPath", "")
            val bmp = if (path.isNotEmpty() && File(path).exists()) {
                BitmapFactory.decodeFile(path)
            } else {
                null
            }
            val savedBoard = deserializeBoard(obj.optJSONArray("board"))
            val savedValidatedWords = deserializeValidatedWords(obj.optJSONArray("validatedWords"))
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
                    language = entryLanguage
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
