package com.example.wordsworth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaConverterTest {

    @Test
    fun testPointValuesFromDesignDoc() {
        // 0 Pts: ゃ, ゅ, ょ, っ
        assertEquals(0, KanaConverter.kanaValue('ゃ'))
        assertEquals(0, KanaConverter.kanaValue('ゅ'))
        assertEquals(0, KanaConverter.kanaValue('ょ'))
        assertEquals(0, KanaConverter.kanaValue('っ'))

        // 1 Pt: の, い, た, に, て, と, し, は, な, を, か
        assertEquals(1, KanaConverter.kanaValue('の'))
        assertEquals(1, KanaConverter.kanaValue('い'))
        assertEquals(1, KanaConverter.kanaValue('た'))
        assertEquals(1, KanaConverter.kanaValue('に'))
        assertEquals(1, KanaConverter.kanaValue('て'))
        assertEquals(1, KanaConverter.kanaValue('と'))
        assertEquals(1, KanaConverter.kanaValue('し'))
        assertEquals(1, KanaConverter.kanaValue('は'))
        assertEquals(1, KanaConverter.kanaValue('な'))
        assertEquals(1, KanaConverter.kanaValue('を'))
        assertEquals(1, KanaConverter.kanaValue('か'))

        // 2 Pts: ら, れ, ま, そ, り, こ, ん, あ
        assertEquals(2, KanaConverter.kanaValue('ら'))
        assertEquals(2, KanaConverter.kanaValue('れ'))
        assertEquals(2, KanaConverter.kanaValue('ま'))
        assertEquals(2, KanaConverter.kanaValue('そ'))
        assertEquals(2, KanaConverter.kanaValue('り'))
        assertEquals(2, KanaConverter.kanaValue('こ'))
        assertEquals(2, KanaConverter.kanaValue('ん'))
        assertEquals(2, KanaConverter.kanaValue('あ'))

        // 3 Pts: す, お, き, け, つ, ち
        assertEquals(3, KanaConverter.kanaValue('す'))
        assertEquals(3, KanaConverter.kanaValue('お'))
        assertEquals(3, KanaConverter.kanaValue('き'))
        assertEquals(3, KanaConverter.kanaValue('け'))
        assertEquals(3, KanaConverter.kanaValue('つ'))
        assertEquals(3, KanaConverter.kanaValue('ち'))

        // 4 Pts: ほ, わ, よ, め, み, う
        assertEquals(4, KanaConverter.kanaValue('ほ'))
        assertEquals(4, KanaConverter.kanaValue('わ'))
        assertEquals(4, KanaConverter.kanaValue('よ'))
        assertEquals(4, KanaConverter.kanaValue('め'))
        assertEquals(4, KanaConverter.kanaValue('み'))
        assertEquals(4, KanaConverter.kanaValue('う'))

        // 5 Pts: や, さ, ひ, せ, る, く
        assertEquals(5, KanaConverter.kanaValue('や'))
        assertEquals(5, KanaConverter.kanaValue('さ'))
        assertEquals(5, KanaConverter.kanaValue('ひ'))
        assertEquals(5, KanaConverter.kanaValue('せ'))
        assertEquals(5, KanaConverter.kanaValue('る'))
        assertEquals(5, KanaConverter.kanaValue('く'))

        // 6 Pts: ゆ, も
        assertEquals(6, KanaConverter.kanaValue('ゆ'))
        assertEquals(6, KanaConverter.kanaValue('も'))

        // 7 Pts: ね, む
        assertEquals(7, KanaConverter.kanaValue('ね'))
        assertEquals(7, KanaConverter.kanaValue('む'))

        // 8 Pts: ろ, ふ
        assertEquals(8, KanaConverter.kanaValue('ろ'))
        assertEquals(8, KanaConverter.kanaValue('ふ'))

        // 9 Pts: へ, え
        assertEquals(9, KanaConverter.kanaValue('へ'))
        assertEquals(9, KanaConverter.kanaValue('え'))

        // 10 Pts: ぬ
        assertEquals(10, KanaConverter.kanaValue('ぬ'))
    }

    @Test
    fun testCabooseRuleFromDesignDoc() {
        // ぬき: 13 points (10 + 3)
        assertEquals(13, KanaConverter.wordRawScore("ぬき"))

        // せぬ: 6 points (5 + 1)
        assertEquals(6, KanaConverter.wordRawScore("せぬ"))

        // はっけん: 6 points (1 + 0 + 3 + 2)
        assertEquals(6, KanaConverter.wordRawScore("はっけん"))
    }

    @Test
    fun testVoicedKanaValues() {
        // が shares か (1)
        assertEquals(1, KanaConverter.kanaValue('が'))
        // ぎ shares き (3)
        assertEquals(3, KanaConverter.kanaValue('ぎ'))
        // ば shares は (1)
        assertEquals(1, KanaConverter.kanaValue('ば'))
        // ぱ shares は (1)
        assertEquals(1, KanaConverter.kanaValue('ぱ'))
    }

    @Test
    fun testSmallAndVoicingToggles() {
        assertEquals('っ', KanaConverter.toggleSmall('つ'))
        assertEquals('つ', KanaConverter.toggleSmall('っ'))
        assertEquals('ゃ', KanaConverter.toggleSmall('や'))
        assertEquals('や', KanaConverter.toggleSmall('ゃ'))

        assertEquals('が', KanaConverter.cycleVoicing('か'))
        assertEquals('か', KanaConverter.cycleVoicing('が'))

        assertEquals('ば', KanaConverter.cycleVoicing('は'))
        assertEquals('ぱ', KanaConverter.cycleVoicing('ば'))
        assertEquals('は', KanaConverter.cycleVoicing('ぱ'))
    }

    @Test
    fun testRomajiStreamConversion() {
        fun convertString(input: String): String {
            val sb = java.lang.StringBuilder()
            var buf = ""
            for (ch in input) {
                val res = KanaConverter.feedRomaji(buf, ch)
                res.producedKana.forEach { sb.append(it) }
                buf = res.remainingBuffer
            }
            KanaConverter.commitBuffer(buf).forEach { sb.append(it) }
            return sb.toString()
        }

        assertEquals("きゃく", convertString("kyaku"))
        assertEquals("はっけん", convertString("hakken"))
        assertEquals("せぬ", convertString("senu"))
        assertEquals("ぬき", convertString("nuki"))
        assertEquals("ごはん", convertString("gohan"))
    }

    @Test
    fun testCycleModifier() {
        // つ -> っ -> づ -> つ
        assertEquals('っ', KanaConverter.cycleModifier('つ'))
        assertEquals('づ', KanaConverter.cycleModifier('っ'))
        assertEquals('つ', KanaConverter.cycleModifier('づ'))

        // は -> ば -> ぱ -> は
        assertEquals('ば', KanaConverter.cycleModifier('は'))
        assertEquals('ぱ', KanaConverter.cycleModifier('ば'))
        assertEquals('は', KanaConverter.cycleModifier('ぱ'))

        // か -> が -> か
        assertEquals('が', KanaConverter.cycleModifier('か'))
        assertEquals('か', KanaConverter.cycleModifier('が'))

        // や -> ゃ -> や
        assertEquals('ゃ', KanaConverter.cycleModifier('や'))
        assertEquals('や', KanaConverter.cycleModifier('ゃ'))

        // う -> ぅ -> ゔ -> う
        assertEquals('ぅ', KanaConverter.cycleModifier('う'))
        assertEquals('ゔ', KanaConverter.cycleModifier('ぅ'))
        assertEquals('う', KanaConverter.cycleModifier('ゔ'))
    }

    @Test
    fun testCabooseCellDisplayValue() {
        val board = WordsWorthLogic.createBlankBoard(6)

        // Test 1: せぬ (horizontal word) -> ぬ is terminal caboose (1 pt), せ is main (5 pts)
        board[0][0] = 'せ'
        board[0][1] = 'ぬ'
        assertEquals(5, WordsWorthLogic.cellDisplayValue(board, 0, 0))
        assertEquals(1, WordsWorthLogic.cellDisplayValue(board, 0, 1))

        // Test 2: ぬき (horizontal word) -> ぬ is initial (10 pts), き is not caboose (3 pts)
        board[1][0] = 'ぬ'
        board[1][1] = 'き'
        assertEquals(10, WordsWorthLogic.cellDisplayValue(board, 1, 0))
        assertEquals(3, WordsWorthLogic.cellDisplayValue(board, 1, 1))

        // Test 3: けむり (horizontal word) -> む is in the middle, so not caboose (7 pts)
        board[2][0] = 'け'
        board[2][1] = 'む'
        board[2][2] = 'り'
        assertEquals(3, WordsWorthLogic.cellDisplayValue(board, 2, 0))
        assertEquals(7, WordsWorthLogic.cellDisplayValue(board, 2, 1))
        assertEquals(2, WordsWorthLogic.cellDisplayValue(board, 2, 2))

        // Test 4: のむ (vertical word) -> む is terminal caboose at bottom (1 pt)
        board[3][0] = 'の'
        board[4][0] = 'む'
        assertEquals(1, WordsWorthLogic.cellDisplayValue(board, 3, 0))
        assertEquals(1, WordsWorthLogic.cellDisplayValue(board, 4, 0))

        // Test 5: Single isolated letter ぬ -> not a word (length < 2), displays main value (10 pts)
        board[5][5] = 'ぬ'
        assertEquals(10, WordsWorthLogic.cellDisplayValue(board, 5, 5))
    }

    @Test
    fun testKeyboardLayoutRows() {
        val qwerty = WordsWorthLogic.QWERTY_KEYBOARD_ROWS
        assertEquals(3, qwerty.size)
        assertEquals(10, qwerty[0].size) // Q..P
        assertEquals(10, qwerty[1].size) // A..L + ー
        assertEquals(9, qwerty[2].size)  // 小゛゜, Z..M, CLR

        assertEquals(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "ー"), qwerty[1])
        assertEquals("小゛゜", qwerty[2][0])
        assertEquals("CLR", qwerty[2][8])
    }

    @Test
    fun testChoonpu() {
        assertEquals(0, KanaConverter.kanaValue('ー'))

        val res = KanaConverter.feedRomaji("ra", '-')
        assertEquals(listOf('ら', 'ー'), res.producedKana)
        assertEquals("", res.remainingBuffer)
    }

    @Test
    fun testWordsWorthLogicBoardTally() {
        val board = WordsWorthLogic.exampleBoard(language = GameLanguage.JAPANESE)
        val dict = setOf("はっけん", "りつみん", "ぬき", "はな", "けむり", "みずき")
        val result = WordsWorthLogic.tallyBoard(board, dict, language = GameLanguage.JAPANESE)

        assertEquals(54, result.totalScore)
        assertEquals(6, result.validWords.size)
        assertEquals(0, result.invalidWords.size)
        assertEquals(0, result.isolatedCells.size)
    }

    @Test
    fun testEnglishBoardTally() {
        val board = WordsWorthLogic.exampleBoard(language = GameLanguage.ENGLISH)
        val dict = setOf("SALUTE", "SHOES", "OVERT", "TOT", "ER", "RAVE", "AT", "SALVE", "OR")
        val result = WordsWorthLogic.tallyBoard(board, dict, language = GameLanguage.ENGLISH)

        assertEquals(9, result.validWords.size)
        assertEquals(0, result.invalidWords.size)
        assertTrue(result.totalScore > 0)
    }
}
