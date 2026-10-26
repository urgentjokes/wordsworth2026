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
        assertEquals("10", WordsWorthLogic.cellDisplayString(board, 5, 5, GameLanguage.JAPANESE))
    }

    @Test
    fun testDualCabooseAndOriginalDisplayValue() {
        val board = WordsWorthLogic.createBlankBoard(6)

        // Case 1: ふゆ (horizontal, ゆ terminal caboose) intersecting ゆき (vertical, ゆ initial)
        // ゆ base value is 6. It scores as caboose (1) horizontally and original (6) vertically -> "1・6"
        board[0][0] = 'ふ'
        board[0][1] = 'ゆ'
        board[1][1] = 'き'

        assertEquals("1・6", WordsWorthLogic.cellDisplayString(board, 0, 1, GameLanguage.JAPANESE))
        assertEquals("8", WordsWorthLogic.cellDisplayString(board, 0, 0, GameLanguage.JAPANESE))
        assertEquals("3", WordsWorthLogic.cellDisplayString(board, 1, 1, GameLanguage.JAPANESE))

        // Case 2: のむ (vertical, む terminal caboose) intersecting むかし (horizontal, む initial)
        // む base value is 7. It scores as caboose (1) vertically and original (7) horizontally -> "1・7"
        board[2][2] = 'の'
        board[3][2] = 'む'
        board[3][3] = 'か'
        board[3][4] = 'し'

        assertEquals("1・7", WordsWorthLogic.cellDisplayString(board, 3, 2, GameLanguage.JAPANESE))
        assertEquals("1", WordsWorthLogic.cellDisplayString(board, 2, 2, GameLanguage.JAPANESE))
        assertEquals("1", WordsWorthLogic.cellDisplayString(board, 3, 3, GameLanguage.JAPANESE))
        assertEquals("1", WordsWorthLogic.cellDisplayString(board, 3, 4, GameLanguage.JAPANESE))

        // Case 3: Two words both ending in ぬ (both score as caboose) -> "1"
        board[4][1] = 'せ'
        board[4][2] = 'ぬ'
        // board[3][2] is already 'む', so 'む' -> 'ぬ' is a 2-letter vertical word ending at 'ぬ'!
        // Horizontal: せ -> ぬ (ends at ぬ, caboose)
        // Vertical: む -> ぬ (ends at ぬ, caboose)
        // Both words score ぬ as caboose, so it displays "1"
        assertEquals("1", WordsWorthLogic.cellDisplayString(board, 4, 2, GameLanguage.JAPANESE))
    }

    @Test
    fun testKeyboardLayoutRows() {
        val qwerty = WordsWorthLogic.QWERTY_KEYBOARD_ROWS
        assertEquals(3, qwerty.size)
        assertEquals(10, qwerty[0].size) // Q..P
        assertEquals(10, qwerty[1].size) // A..L + ー
        assertEquals(8, qwerty[2].size)  // Z..M, CLR

        assertEquals(listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "ー"), qwerty[1])
        assertEquals("Z", qwerty[2][0])
        assertEquals("CLR", qwerty[2][7])
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
    fun testJapaneseBoardTallyPrecisionVerbs() {
        val board: Array<Array<Char?>> = Array(3) { Array(3) { null } }
        // Row 0: い ぬ
        board[0][0] = 'い'
        board[0][1] = 'ぬ'
        // Row 1: し ぬ
        board[1][0] = 'し'
        board[1][1] = 'ぬ'

        val dict = setOf("いぬ", "しぬ")
        val verbDict = setOf("しぬ") // "しぬ" is a verb, "いぬ" is not a verb

        val cabooseResult = WordsWorthLogic.tallyBoard(
            board = board,
            dictionary = dict,
            language = GameLanguage.JAPANESE,
            japaneseVerbRule = JapaneseVerbRule.CABOOSE,
            verbDictionary = verbDict
        )
        // Under Caboose:
        // いぬ: い(1) + ぬ(1) = 2
        // しぬ: し(1) + ぬ(1) = 2
        // Total = 4
        assertEquals(4, cabooseResult.totalScore)

        val precisionResult = WordsWorthLogic.tallyBoard(
            board = board,
            dictionary = dict,
            language = GameLanguage.JAPANESE,
            japaneseVerbRule = JapaneseVerbRule.PRECISION_VERBS,
            verbDictionary = verbDict
        )
        // Under Precision Verbs:
        // いぬ: い(1) + ぬ(10) = 11 (not a verb because ends in いぬ)
        // しぬ: し(1) + ぬ(1) = 2 (is a verb)
        // Total = 13
        assertEquals(13, precisionResult.totalScore)
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

    @Test
    fun testPrecisionVerbsIsJapaneseVerb() {
        val mockVerbDict = setOf("しぬ", "たべる", "あう", "いく", "かく", "うぬ", "きぬ", "およぐ", "とぶ", "あそぶ", "もえいず", "うなづく")

        // 1. Terminal kana check: Non-verbs ending in non-terminal kana must be false
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("ねこ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("くるま", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("はな", mockVerbDict))

        // 2. Exact match in verb dictionary ending in terminal kana
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("しぬ", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("たべる", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("いく", mockVerbDict))

        // 2b. Triggers for dakuten (ぐ, ず, づ) and ぶ
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("およぐ", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("とぶ", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("あそぶ", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("もえいず", mockVerbDict))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("うなづく", mockVerbDict))
        // Non-verbs ending in ぐ, ず, づ, ぶ not in dict
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("まぐ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("こぶ", mockVerbDict))

        // 3. 2-kana suffix matches ("だす", "こむ", "あう", "たつ", "つく", "きる", "とる", "おく", "よる", "いる", "ます", "ぬく", "やる", "そめる")
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("とびだす", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("すいこむ", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("はなしあう", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おもいたつ", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おいつく", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おもいきる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("うけとる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("すえおく", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("たちよる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おもいいる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("たべます", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("ひきぬく", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おもいやる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("あかそめる", emptySet()))

        // 4. 3-kana suffix matches ("あげる", "さげる", "かける", "つける", "すぎる", "なおす", "おとす", "はらう", "ちらす", "まわす", "ならう")
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("もちあげる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("ひきさげる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("といかける", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おもいつける", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("やりすぎる", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("みなおす", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("ひきおとす", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("おいちらす", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("みまわす", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("みならう", emptySet()))

        // 5. Excluded ぬ words must be false: (word is うぬ、きぬ、おぬ、かんぬ) or (ends with いぬ)
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("うぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("きぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("おぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("かんぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("いぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("のらいぬ", mockVerbDict))
        assertEquals(false, WordsWorthLogic.isJapaneseVerb("あきたいぬ", mockVerbDict))

        // 6. Other words ending in ぬ are verb=true (even if not in dictionary)
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("しぬ", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("せぬ", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("よまぬ", emptySet()))
        assertEquals(true, WordsWorthLogic.isJapaneseVerb("たべぬ", emptySet()))
    }

    @Test
    fun testWordRawScoreCabooseVsPrecisionVerbs() {
        val verbDict = setOf("しぬ", "たべる", "かく", "とぶ")

        // CABOOSE rule:
        // Any word ending in terminal kana has last letter scored as 1 point
        // "きぬ": 'き'(3) + caboose 'ぬ'(1) = 4
        assertEquals(4, KanaConverter.wordRawScore("きぬ", JapaneseVerbRule.CABOOSE))
        // "いぬ": 'い'(1) + caboose 'ぬ'(1) = 2
        assertEquals(2, KanaConverter.wordRawScore("いぬ", JapaneseVerbRule.CABOOSE))
        // "しぬ": 'し'(1) + caboose 'ぬ'(1) = 2
        assertEquals(2, KanaConverter.wordRawScore("しぬ", JapaneseVerbRule.CABOOSE))
        // "とぶ": 'と'(1) + caboose 'ぶ'(1) = 2
        assertEquals(2, KanaConverter.wordRawScore("とぶ", JapaneseVerbRule.CABOOSE))

        // PRECISION_VERBS rule:
        // "きぬ": not a verb -> 'き'(3) + normal 'ぬ'(10) = 13
        val kinusVerb = WordsWorthLogic.isJapaneseVerb("きぬ", verbDict)
        assertEquals(false, kinusVerb)
        assertEquals(13, KanaConverter.wordRawScore("きぬ", JapaneseVerbRule.PRECISION_VERBS, kinusVerb))

        // "いぬ": not a verb -> 'い'(1) + normal 'ぬ'(10) = 11
        val inuIsVerb = WordsWorthLogic.isJapaneseVerb("いぬ", verbDict)
        assertEquals(false, inuIsVerb)
        assertEquals(11, KanaConverter.wordRawScore("いぬ", JapaneseVerbRule.PRECISION_VERBS, inuIsVerb))

        // "しぬ": IS a verb -> 'し'(1) + verb-last 'ぬ'(1) = 2
        val shinuIsVerb = WordsWorthLogic.isJapaneseVerb("しぬ", verbDict)
        assertEquals(true, shinuIsVerb)
        assertEquals(2, KanaConverter.wordRawScore("しぬ", JapaneseVerbRule.PRECISION_VERBS, shinuIsVerb))

        // "とぶ": IS a verb -> 'と'(1) + verb-last 'ぶ'(1, normally 8) = 2
        val tobuIsVerb = WordsWorthLogic.isJapaneseVerb("とぶ", verbDict)
        assertEquals(true, tobuIsVerb)
        assertEquals(2, KanaConverter.wordRawScore("とぶ", JapaneseVerbRule.PRECISION_VERBS, tobuIsVerb))

        // "こぶ": NOT a verb -> 'こ'(2) + normal 'ぶ'(8) = 10
        val kobuIsVerb = WordsWorthLogic.isJapaneseVerb("こぶ", verbDict)
        assertEquals(false, kobuIsVerb)
        assertEquals(10, KanaConverter.wordRawScore("こぶ", JapaneseVerbRule.PRECISION_VERBS, kobuIsVerb))

        // Compound verb ending in だす: "とびだす"
        // 'と'(1) + 'び'(5) + 'だ'(1) + 'す'(1 if verb, 3 if not)
        val tobiIsVerb = WordsWorthLogic.isJapaneseVerb("とびだす", verbDict)
        assertEquals(true, tobiIsVerb)
        assertEquals(8, KanaConverter.wordRawScore("とびだす", JapaneseVerbRule.PRECISION_VERBS, tobiIsVerb))
    }

    @Test
    fun testLoadJapaneseVerbDictionaryFromFile() {
        val verbs = WordsWorthLogic.loadJapaneseVerbDictionaryFromFile()
        assertTrue("Verb dictionary should load words from file/assets", verbs.isNotEmpty())
        assertTrue("Verb dictionary should contain common verb しぬ", verbs.contains("しぬ"))
        assertTrue("Verb dictionary should contain common verb たべる", verbs.contains("たべる"))
        assertTrue("Verb dictionary should contain common verb いく", verbs.contains("いく"))
    }
}
