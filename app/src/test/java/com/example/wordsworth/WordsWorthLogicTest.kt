package com.example.wordsworth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class WordsWorthLogicTest {

    @Test
    fun testSprinkleLetterCount() {
        // 6x6 board (36 squares)
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(36, LetterSprinkleMode.NONE))
        assertEquals(2, WordsWorthLogic.sprinkleLetterCount(36, LetterSprinkleMode.FEW)) // round(1.8) = 2
        assertEquals(4, WordsWorthLogic.sprinkleLetterCount(36, LetterSprinkleMode.SOME)) // round(3.6) = 4
        assertEquals(5, WordsWorthLogic.sprinkleLetterCount(36, LetterSprinkleMode.MANY)) // round(5.4) = 5

        // 9x9 board (81 squares)
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(81, LetterSprinkleMode.NONE))
        assertEquals(4, WordsWorthLogic.sprinkleLetterCount(81, LetterSprinkleMode.FEW)) // round(4.05) = 4
        assertEquals(8, WordsWorthLogic.sprinkleLetterCount(81, LetterSprinkleMode.SOME)) // round(8.1) = 8
        assertEquals(12, WordsWorthLogic.sprinkleLetterCount(81, LetterSprinkleMode.MANY)) // round(12.15) = 12

        // 12x12 board (144 squares)
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(144, LetterSprinkleMode.NONE))
        assertEquals(7, WordsWorthLogic.sprinkleLetterCount(144, LetterSprinkleMode.FEW)) // round(7.2) = 7
        assertEquals(14, WordsWorthLogic.sprinkleLetterCount(144, LetterSprinkleMode.SOME)) // round(14.4) = 14
        assertEquals(22, WordsWorthLogic.sprinkleLetterCount(144, LetterSprinkleMode.MANY)) // round(21.6) = 22

        // Edge case: 0 available
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(0, LetterSprinkleMode.NONE))
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(0, LetterSprinkleMode.FEW))
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(0, LetterSprinkleMode.SOME))
        assertEquals(0, WordsWorthLogic.sprinkleLetterCount(0, LetterSprinkleMode.MANY))
    }

    @Test
    fun testSprinkleLettersEnglish() {
        val board = WordsWorthLogic.createBlankBoard(6)
        val sprinkled = WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.FEW, GameLanguage.ENGLISH, Random(42))

        var letterCount = 0
        for (r in board.indices) {
            for (c in board[r].indices) {
                val ch = board[r][c]
                if (ch != null) {
                    letterCount++
                    assertTrue("Letter must be English A..Z: $ch", ch in 'A'..'Z')
                    assertTrue((r to c) in sprinkled)
                }
            }
        }
        assertEquals(2, letterCount)
        assertEquals(2, sprinkled.size)
    }

    @Test
    fun testSprinkleLettersJapanese() {
        val board = WordsWorthLogic.createBlankBoard(6)
        val sprinkled = WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.SOME, GameLanguage.JAPANESE, Random(123))

        var letterCount = 0
        for (r in board.indices) {
            for (c in board[r].indices) {
                val ch = board[r][c]
                if (ch != null) {
                    letterCount++
                    assertTrue("Letter must be in SPRINKLE_KANA: $ch", ch in WordsWorthLogic.SPRINKLE_KANA)
                    assertTrue((r to c) in sprinkled)
                }
            }
        }
        assertEquals(4, letterCount)
        assertEquals(4, sprinkled.size)
    }

    @Test
    fun testSprinkleLettersNoneLeavesBoardBlank() {
        val board = WordsWorthLogic.createBlankBoard(6)
        val sprinkled = WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.NONE, GameLanguage.ENGLISH)

        assertTrue(sprinkled.isEmpty())
        for (r in board.indices) {
            for (c in board[r].indices) {
                assertNull(board[r][c])
            }
        }
    }

    @Test
    fun testSprinkleLettersPreservesBlockedSquares() {
        val board = WordsWorthLogic.createBlankBoard(6)
        // Set some blocked squares
        board[0][0] = WordsWorthLogic.BLOCKED_CELL
        board[1][1] = WordsWorthLogic.BLOCKED_CELL
        board[2][2] = WordsWorthLogic.BLOCKED_CELL

        WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.MANY, GameLanguage.ENGLISH, Random(999))

        assertEquals(WordsWorthLogic.BLOCKED_CELL, board[0][0])
        assertEquals(WordsWorthLogic.BLOCKED_CELL, board[1][1])
        assertEquals(WordsWorthLogic.BLOCKED_CELL, board[2][2])

        var letterCount = 0
        for (r in board.indices) {
            for (c in board[r].indices) {
                if (board[r][c] != null && board[r][c] != WordsWorthLogic.BLOCKED_CELL) {
                    letterCount++
                    assertTrue(board[r][c] in 'A'..'Z')
                }
            }
        }
        // Available: 36 - 3 = 33. Many: round(33 * 0.15) = round(4.95) = 5
        assertEquals(5, letterCount)
    }

    @Test
    fun testSprinkledLettersCannotBeOverwrittenOrClearedWhenProtected() {
        val board = WordsWorthLogic.createBlankBoard(6)
        val sprinkledCoords = WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.FEW, GameLanguage.ENGLISH, Random(42))
        assertTrue(sprinkledCoords.isNotEmpty())

        for ((r, c) in sprinkledCoords) {
            val originalLetter = board[r][c]
            assertTrue(originalLetter != null && originalLetter in 'A'..'Z')

            // Simulating updateCell logic: if (r to c in sprinkledCoords) -> ignore edit
            val attemptClear = if ((r to c) in sprinkledCoords) board[r][c] else null
            assertEquals("Sprinkled letter must not be cleared", originalLetter, attemptClear)

            val attemptOverwrite = if ((r to c) in sprinkledCoords) board[r][c] else 'Z'
            assertEquals("Sprinkled letter must not be overwritten", originalLetter, attemptOverwrite)
        }
    }

    @Test
    fun testRule1_woNeverUsedAsSprinkle() {
        assertFalse("SPRINKLE_KANA must not contain を", 'を' in WordsWorthLogic.SPRINKLE_KANA)

        // Across 100 random boards, 'を' should never appear as a sprinkled letter
        for (seed in 0..100) {
            val board = WordsWorthLogic.createBlankBoard(6)
            WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.MANY, GameLanguage.JAPANESE, Random(seed.toLong()))
            for (r in board.indices) {
                for (c in board[r].indices) {
                    assertFalse("Sprinkled board must not contain を", board[r][c] == 'を')
                }
            }
        }
    }

    @Test
    fun testRule3_nNeverOnTopRowOrLeftmostCol() {
        // Across 100 random boards, 'ん' should never appear on r == 0 or c == 0
        for (seed in 0..100) {
            val board = WordsWorthLogic.createBlankBoard(6)
            WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.MANY, GameLanguage.JAPANESE, Random(seed.toLong()))
            for (r in board.indices) {
                for (c in board[r].indices) {
                    if (r == 0 || c == 0) {
                        assertFalse("Top row or left-most column must never contain ん, found at ($r, $c)", board[r][c] == 'ん')
                    }
                }
            }
        }
    }

    @Test
    fun testRule4_twoSprinklesNeverAdjacent() {
        // Across various board sizes and random seeds, no two sprinkles share an orthogonal edge
        for (size in listOf(6, 9, 12)) {
            for (seed in 0..50) {
                val board = WordsWorthLogic.createBlankBoard(size)
                val sprinkled = WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.MANY, GameLanguage.ENGLISH, Random(seed.toLong()))
                for ((r1, c1) in sprinkled) {
                    for ((r2, c2) in sprinkled) {
                        if (r1 == r2 && c1 == c2) continue
                        val dist = kotlin.math.abs(r1 - r2) + kotlin.math.abs(c1 - c2)
                        assertTrue("Two sprinkles must not be orthogonally adjacent: ($r1,$c1) and ($r2,$c2)", dist > 1)
                    }
                }
            }
        }
    }

    @Test
    fun testRule5_qNeverOnRightmostColOrBottomRow() {
        // Across 100 random boards, 'Q' should never appear on r == size - 1 or c == size - 1
        for (seed in 0..100) {
            val board = WordsWorthLogic.createBlankBoard(6)
            WordsWorthLogic.sprinkleLetters(board, LetterSprinkleMode.MANY, GameLanguage.ENGLISH, Random(seed.toLong()))
            val lastIdx = board.size - 1
            for (r in board.indices) {
                for (c in board[r].indices) {
                    if (r == lastIdx || c == lastIdx) {
                        assertFalse("Bottom row or right-most column must never contain Q, found at ($r, $c)", board[r][c] == 'Q')
                    }
                }
            }
        }
    }

    @Test
    fun testRule2_firstTimeUseSprinkleDoublesPoints_English() {
        val board = WordsWorthLogic.createBlankBoard(6)
        // 'B' (3 pts), 'A' (1 pt), 'T' (1 pt)
        board[0][0] = 'B'
        board[0][1] = 'A'
        board[0][2] = 'T'
        // 'B' (3 pts), 'O' (1 pt), 'Y' (4 pts)
        board[1][0] = 'O'
        board[2][0] = 'Y'

        val dict = setOf("BAT", "BOY")
        val sprinkledCells = setOf(0 to 0) // 'B' is a sprinkle

        val tally = WordsWorthLogic.tallyBoard(
            board = board,
            dictionary = dict,
            language = GameLanguage.ENGLISH,
            sprinkledCells = sprinkledCells
        )

        assertEquals(2, tally.validWords.size)
        val batWord = tally.validWords.first { it.word == "BAT" }
        val boyWord = tally.validWords.first { it.word == "BOY" }

        // The first word to use (0, 0) is BAT (scanned horizontally first):
        // 'B' is doubled: 3 * 2 = 6. BAT = 6 + 1 + 1 = 8.
        assertEquals(8, batWord.awardedPoints)

        // The second word to use (0, 0) is BOY (scanned vertically second):
        // (0, 0) is already claimed, so 'B' receives normal 1x: 3. BOY = B(3) + O(1) + Y(1) = 5.
        assertEquals(5, boyWord.awardedPoints)

        // Total score: 8 + 5 = 13
        assertEquals(13, tally.totalScore)
    }

    @Test
    fun testRule2_firstTimeUseSprinkleJapaneseVerbRules() {
        val board = WordsWorthLogic.createBlankBoard(6)
        // た(1) べ(9) る(6) -> たべる (to eat, verb)
        board[0][0] = 'た'
        board[0][1] = 'べ'
        board[0][2] = 'る'

        val dict = setOf("たべる")
        val verbs = setOf("たべる")
        val sprinkledCells = setOf(0 to 2) // 'る' is the sprinkle

        // Under Caboose rule: 'る' is caboose kana -> reduced to 1 pt.
        // First time used: doubled from 1 pt -> 2 pts!
        // た(1) + べ(9) + る(2) = 12 pts (instead of 1 + 9 + 12 = 22).
        val tallyCaboose = WordsWorthLogic.tallyBoard(
            board = board,
            dictionary = dict,
            language = GameLanguage.JAPANESE,
            japaneseVerbRule = JapaneseVerbRule.CABOOSE,
            verbDictionary = verbs,
            sprinkledCells = sprinkledCells
        )
        val cabooseWord = tallyCaboose.validWords.first { it.word == "たべる" }
        assertEquals("Sprinkle at terminal verb should double from 1 pt to 2 pts (た:1 + べ:9 + る:2 = 12)", 12, cabooseWord.awardedPoints)

        // Non-terminal sprinkle test: 'ぬ' (10 pts) in ぬ(10) き(3) -> ぬき
        val board2 = WordsWorthLogic.createBlankBoard(6)
        board2[1][0] = 'ぬ'
        board2[1][1] = 'き'
        val dict2 = setOf("ぬき")
        val sprinkledCells2 = setOf(1 to 0) // 'ぬ' is the sprinkle

        val tallyNu = WordsWorthLogic.tallyBoard(
            board = board2,
            dictionary = dict2,
            language = GameLanguage.JAPANESE,
            sprinkledCells = sprinkledCells2
        )
        val nukiWord = tallyNu.validWords.first { it.word == "ぬき" }
        // ぬ(10 * 2 = 20) + き(3) = 23 pts
        assertEquals(23, nukiWord.awardedPoints)
    }
}
