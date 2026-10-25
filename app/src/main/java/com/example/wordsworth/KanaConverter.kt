package com.example.wordsworth

/**
 * Handles Japanese Kana point values, Caboose scoring, voicing (dakuten/handakuten),
 * small letter toggling (sokuon), and dynamic Romaji-to-Hiragana input conversion.
 */
object KanaConverter {

    /**
     * Point assignments according to J-DESIGN.MD:
     * - 0 Pts: ゃ, ゅ, ょ, っ (Structural Bridge Tiles)
     * - 1 Pt: の, い, た, に, て, と, し, は, な, を, か
     * - 2 Pts: ら, れ, ま, そ, り, こ, ん, あ
     * - 3 Pts: す, お, き, け, つ, ち
     * - 4 Pts: ほ, わ, よ, め, み, う
     * - 5 Pts: や, さ, ひ, せ, る, く
     * - 6 Pts: ゆ, も
     * - 7 Pts: ね, む
     * - 8 Pts: ろ, ふ
     * - 9 Pts: へ, え
     * - 10 Pts: ぬ
     */
    val KANA_BASE_VALUES: Map<Char, Int> = mapOf(
        // 0 Pts
        'ゃ' to 0, 'ゅ' to 0, 'ょ' to 0, 'っ' to 0,
        'ぁ' to 0, 'ぃ' to 0, 'ぅ' to 0, 'ぇ' to 0, 'ぉ' to 0, 'ゎ' to 0,
        'ー' to 0,

        // 1 Pt
        'の' to 1, 'い' to 1, 'た' to 1, 'に' to 1, 'て' to 1, 'と' to 1,
        'し' to 1, 'は' to 1, 'な' to 1, 'を' to 1, 'か' to 1,

        // 2 Pts
        'ら' to 2, 'れ' to 2, 'ま' to 2, 'そ' to 2, 'り' to 2, 'こ' to 2,
        'ん' to 2, 'あ' to 2,

        // 3 Pts
        'す' to 3, 'お' to 3, 'き' to 3, 'け' to 3, 'つ' to 3, 'ち' to 3,

        // 4 Pts
        'ほ' to 4, 'わ' to 4, 'よ' to 4, 'め' to 4, 'み' to 4, 'う' to 4,

        // 5 Pts
        'や' to 5, 'さ' to 5, 'ひ' to 5, 'せ' to 5, 'る' to 5, 'く' to 5,

        // 6 Pts
        'ゆ' to 6, 'も' to 6,

        // 7 Pts
        'ね' to 7, 'む' to 7,

        // 8 Pts
        'ろ' to 8, 'ふ' to 8,

        // 9 Pts
        'へ' to 9, 'え' to 9,

        // 10 Pts
        'ぬ' to 10
    )

    /**
     * Maps voiced (dakuten) and semi-voiced (handakuten) kana to their base unvoiced kana
     * to inherit point values and Caboose classification.
     */
    val BASE_UNVOICED_MAP: Map<Char, Char> = mapOf(
        'が' to 'か', 'ぎ' to 'き', 'ぐ' to 'く', 'げ' to 'け', 'ご' to 'こ',
        'ざ' to 'さ', 'じ' to 'し', 'ず' to 'す', 'ぜ' to 'せ', 'ぞ' to 'そ',
        'だ' to 'た', 'ぢ' to 'ち', 'づ' to 'つ', 'で' to 'て', 'ど' to 'と',
        'ば' to 'は', 'び' to 'ひ', 'ぶ' to 'ふ', 'べ' to 'へ', 'ぼ' to 'ほ',
        'ぱ' to 'は', 'ぴ' to 'ひ', 'ぷ' to 'ふ', 'ぺ' to 'へ', 'ぽ' to 'ほ',
        'ゔ' to 'う'
    )

    /**
     * Caboose Rule kana from J-DESIGN.MD:
     * - The entire -う列 (う, く, ぐ, す, ず, つ, づ, ぬ, ふ, ぶ, ぷ, む, ゆ, る, ゔ)
     * - て (and で)
     * When any of these appears at the end of a word, it is scored as 1 point.
     */
    val CABOOSE_KANA: Set<Char> = setOf(
        'う', 'く', 'ぐ', 'す', 'ず', 'つ', 'づ', 'ぬ',
        'ふ', 'ぶ', 'ぷ', 'む', 'ゆ', 'る', 'ゔ',
        'て', 'で'
    )

    const val CHOONPU = 'ー'

    /** Returns true if [ch] is a Hiragana, Katakana, or chōonpu character. */
    fun isKana(ch: Char): Boolean = ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' || ch == CHOONPU

    /**
     * Returns the normal intrinsic point value for a single kana character.
     */
    fun kanaValue(ch: Char): Int {
        val base = BASE_UNVOICED_MAP[ch] ?: ch
        return KANA_BASE_VALUES[base] ?: 0
    }

    /**
     * Calculates the raw score of a word applying the Caboose Rule to the terminal kana.
     * e.g., ぬき -> 13 (10 + 3), せぬ -> 6 (5 + 1), はっけん -> 6 (1 + 0 + 3 + 2).
     */
    fun wordRawScore(word: String): Int {
        if (word.isEmpty()) return 0
        var total = 0
        val lastIndex = word.length - 1
        for (i in word.indices) {
            val ch = word[i]
            val baseVal = kanaValue(ch)
            if (i == lastIndex && ch in CABOOSE_KANA) {
                total += minOf(baseVal, 1)
            } else {
                total += baseVal
            }
        }
        return total
    }

    private val SMALL_TO_NORMAL = mapOf(
        'っ' to 'つ', 'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ',
        'ぁ' to 'あ', 'ぃ' to 'い', 'ぅ' to 'う', 'ぇ' to 'え', 'ぉ' to 'お', 'ゎ' to 'わ'
    )

    private val NORMAL_TO_SMALL = mapOf(
        'つ' to 'っ', 'や' to 'ゃ', 'ゆ' to 'ゅ', 'よ' to 'ょ',
        'あ' to 'ぁ', 'い' to 'ぃ', 'う' to 'ぅ', 'え' to 'ぇ', 'お' to 'ぉ', 'わ' to 'ゎ'
    )

    /**
     * Toggles a kana between its small (sokuon/youon) and normal size.
     * e.g., つ <-> っ, や <-> ゃ, ゆ <-> ゅ, よ <-> ょ.
     */
    fun toggleSmall(ch: Char): Char {
        return NORMAL_TO_SMALL[ch] ?: SMALL_TO_NORMAL[ch] ?: ch
    }

    /**
     * Cycles a kana character through its modifier states:
     * - For kana with small & dakuten (つ): つ -> っ (小) -> づ (゛) -> つ
     * - For う: う -> ぅ (小) -> ゔ (゛) -> う
     * - For kana with small only (や, ゆ, よ, etc.): や -> ゃ (小) -> や
     * - For kana with dakuten & handakuten (は, ひ, ふ, へ, ほ): は -> ば (゛) -> ぱ (゜) -> は
     * - For kana with dakuten only (か, さ, た rows): か -> が (゛) -> か
     * - Others: remains unchanged.
     */
    fun cycleModifier(ch: Char): Char = when (ch) {
        // つ / っ / づ
        'つ' -> 'っ'
        'っ' -> 'づ'
        'づ' -> 'つ'

        // う / ぅ / ゔ
        'う' -> 'ぅ'
        'ぅ' -> 'ゔ'
        'ゔ' -> 'う'

        // は・ひ・ふ・へ・ほ (unvoiced -> voiced -> semi-voiced -> unvoiced)
        'は' -> 'ば'; 'ば' -> 'ぱ'; 'ぱ' -> 'は'
        'ひ' -> 'び'; 'び' -> 'ぴ'; 'ぴ' -> 'ひ'
        'ふ' -> 'ぶ'; 'ぶ' -> 'ぷ'; 'ぷ' -> 'ふ'
        'へ' -> 'べ'; 'べ' -> 'ぺ'; 'ぺ' -> 'へ'
        'ほ' -> 'ぼ'; 'ぼ' -> 'ぽ'; 'ぽ' -> 'ほ'

        // か行
        'か' -> 'が'; 'が' -> 'か'
        'き' -> 'ぎ'; 'ぎ' -> 'き'
        'く' -> 'ぐ'; 'ぐ' -> 'く'
        'け' -> 'げ'; 'げ' -> 'け'
        'こ' -> 'ご'; 'ご' -> 'こ'

        // さ行
        'さ' -> 'ざ'; 'ざ' -> 'さ'
        'し' -> 'じ'; 'じ' -> 'し'
        'す' -> 'ず'; 'ず' -> 'す'
        'せ' -> 'ぜ'; 'ぜ' -> 'せ'
        'そ' -> 'ぞ'; 'ぞ' -> 'そ'

        // た行 (excluding つ)
        'た' -> 'だ'; 'だ' -> 'た'
        'ち' -> 'ぢ'; 'ぢ' -> 'ち'
        'て' -> 'で'; 'で' -> 'て'
        'と' -> 'ど'; 'ど' -> 'と'

        // や行 (small)
        'や' -> 'ゃ'; 'ゃ' -> 'や'
        'ゆ' -> 'ゅ'; 'ゅ' -> 'ゆ'
        'よ' -> 'ょ'; 'ょ' -> 'よ'

        // あ行 (small, excluding う)
        'あ' -> 'ぁ'; 'ぁ' -> 'あ'
        'い' -> 'ぃ'; 'ぃ' -> 'い'
        'え' -> 'ぇ'; 'ぇ' -> 'え'
        'お' -> 'ぉ'; 'ぉ' -> 'お'

        // わ行 (small)
        'わ' -> 'ゎ'; 'ゎ' -> 'わ'

        else -> ch
    }

    /**
     * Cycles voicing on a kana (unvoiced -> voiced -> semi-voiced -> unvoiced).
     */
    fun cycleVoicing(ch: Char): Char = when (ch) {
        'か' -> 'が'; 'が' -> 'か'
        'き' -> 'ぎ'; 'ぎ' -> 'き'
        'く' -> 'ぐ'; 'ぐ' -> 'く'
        'け' -> 'げ'; 'げ' -> 'け'
        'こ' -> 'ご'; 'ご' -> 'こ'

        'さ' -> 'ざ'; 'ざ' -> 'さ'
        'し' -> 'じ'; 'じ' -> 'し'
        'す' -> 'ず'; 'ず' -> 'す'
        'せ' -> 'ぜ'; 'ぜ' -> 'せ'
        'そ' -> 'ぞ'; 'ぞ' -> 'そ'

        'た' -> 'だ'; 'だ' -> 'た'
        'ち' -> 'ぢ'; 'ぢ' -> 'ち'
        'つ' -> 'づ'; 'づ' -> 'つ'
        'て' -> 'で'; 'で' -> 'て'
        'と' -> 'ど'; 'ど' -> 'と'

        'は' -> 'ば'; 'ば' -> 'ぱ'; 'ぱ' -> 'は'
        'ひ' -> 'び'; 'び' -> 'ぴ'; 'ぴ' -> 'ひ'
        'ふ' -> 'ぶ'; 'ぶ' -> 'ぷ'; 'ぷ' -> 'ふ'
        'へ' -> 'べ'; 'べ' -> 'ぺ'; 'ぺ' -> 'へ'
        'ほ' -> 'ぼ'; 'ぼ' -> 'ぽ'; 'ぽ' -> 'ほ'

        'う' -> 'ゔ'; 'ゔ' -> 'う'
        else -> ch
    }

    // -------------------------------------------------------------------------
    // Romaji to Hiragana conversion mapping
    // -------------------------------------------------------------------------

    private val ROMAJI_TABLE: Map<String, String> = mapOf(
        // 3-letter combinations
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "she" to "しぇ",
        "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ", "che" to "ちぇ",
        "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
        "zya" to "じゃ", "zyu" to "じゅ", "zyo" to "じょ",
        "dya" to "ぢゃ", "dyu" to "ぢゅ", "dyo" to "ぢょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        "tsu" to "つ",

        // Small kana prefixes (x/l)
        "xtsu" to "っ", "ltsu" to "っ", "xtu" to "っ", "ltu" to "っ",
        "xya" to "ゃ", "lya" to "ゃ", "xyu" to "ゅ", "lyu" to "ゅ", "xyo" to "ょ", "lyo" to "ょ",
        "xa" to "ぁ", "la" to "ぁ", "xi" to "ぃ", "li" to "ぃ", "xu" to "ぅ", "lu" to "ぅ",
        "xe" to "ぇ", "le" to "ぇ", "xo" to "ぉ", "lo" to "ぉ", "xwa" to "ゎ", "lwa" to "ゎ",

        // 2-letter combinations
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "sa" to "さ", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "ta" to "た", "ti" to "ち", "tu" to "つ", "te" to "て", "to" to "と",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を",

        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "za" to "ざ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",

        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ", "je" to "じぇ",
        "ji" to "じ", "chi" to "ち", "shi" to "し", "fu" to "ふ",
        "vu" to "ゔ", "va" to "ゔぁ", "vi" to "ゔぃ", "ve" to "ゔぇ", "vo" to "ゔぉ",
        "fa" to "ふぁ", "fi" to "ふぃ", "fe" to "ふぇ", "fo" to "ふぉ",

        "nn" to "ん",

        // Chōonpu (long vowel mark)
        "-" to "ー",

        // 1-letter vowels
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お"
    )

    private val PREFIX_SET: Set<String> = buildSet {
        for (key in ROMAJI_TABLE.keys) {
            for (len in 1..key.length) {
                add(key.substring(0, len))
            }
        }
        // Double consonant prefixes (kk, ss, tt, etc.)
        for (c in 'a'..'z') {
            if (c !in "aiueon") {
                add("$c$c")
            }
        }
        add("n")
        add("-")
    }

    /** Result of feeding a Romaji keystroke into the converter. */
    data class FeedResult(
        val producedKana: List<Char>,
        val remainingBuffer: String
    )

    /**
     * Feeds [nextChar] into [currentBuffer] and returns any newly formed kana
     * characters along with the updated pending buffer.
     */
    fun feedRomaji(currentBuffer: String, nextChar: Char): FeedResult {
        val lower = nextChar.lowercaseChar()
        if (lower == '-' || lower == 'ー') {
            val prefix = if (currentBuffer.isNotEmpty()) commitBuffer(currentBuffer) else emptyList()
            return FeedResult(prefix + listOf('ー'), "")
        }
        if (lower !in 'a'..'z') {
            return FeedResult(emptyList(), currentBuffer)
        }

        val testBuffer = currentBuffer + lower

        // 1. Double consonant (sokuon): e.g. "kk" -> produces 'っ', leaves 'k'
        if (testBuffer.length == 2 && testBuffer[0] == testBuffer[1] && testBuffer[0] !in "aiueon") {
            return FeedResult(
                producedKana = listOf('っ'),
                remainingBuffer = lower.toString()
            )
        }

        // 2. 'n' followed by a consonant (except 'y' or vowels) -> produces 'ん'
        if (testBuffer.length == 2 && testBuffer[0] == 'n' && testBuffer[1] !in "aiueoy") {
            if (testBuffer == "nn") {
                return FeedResult(
                    producedKana = listOf('ん'),
                    remainingBuffer = ""
                )
            } else {
                return FeedResult(
                    producedKana = listOf('ん'),
                    remainingBuffer = lower.toString()
                )
            }
        }

        // 3. Exact match in ROMAJI_TABLE
        val matched = ROMAJI_TABLE[testBuffer]
        if (matched != null) {
            return FeedResult(
                producedKana = matched.toList(),
                remainingBuffer = ""
            )
        }

        // 4. Valid prefix for future input (e.g. "k", "ky", "sh")
        if (testBuffer in PREFIX_SET) {
            return FeedResult(
                producedKana = emptyList(),
                remainingBuffer = testBuffer
            )
        }

        // 5. Buffer not recognized: if lower by itself is a valid prefix, reset to it
        val singleMatch = ROMAJI_TABLE[lower.toString()]
        return if (singleMatch != null) {
            FeedResult(
                producedKana = singleMatch.toList(),
                remainingBuffer = ""
            )
        } else if (lower.toString() in PREFIX_SET) {
            FeedResult(
                producedKana = emptyList(),
                remainingBuffer = lower.toString()
            )
        } else {
            FeedResult(
                producedKana = emptyList(),
                remainingBuffer = ""
            )
        }
    }

    /**
     * Commits any pending buffer characters (e.g., pending "n" -> 'ん')
     * when the player moves focus or validates.
     */
    fun commitBuffer(buffer: String): List<Char> {
        if (buffer == "n") {
            return listOf('ん')
        }
        val match = ROMAJI_TABLE[buffer]
        return match?.toList() ?: emptyList()
    }
}
