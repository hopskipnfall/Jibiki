package dev.jibiki.japanese

object Kana {
    private const val HIRA_START = 0x3041
    private const val HIRA_END = 0x3096
    private const val KATA_START = 0x30A1
    private const val KATA_END = 0x30F6
    private const val OFFSET = KATA_START - HIRA_START

    fun toHiragana(s: String): String = mapChars(s) { c ->
        when (c.code) {
            in KATA_START..KATA_END -> (c.code - OFFSET).toChar()
            0x30FD, 0x30FE -> (c.code - OFFSET).toChar() // ヽヾ → ゝゞ
            else -> c
        }
    }

    fun toKatakana(s: String): String = mapChars(s) { c ->
        when (c.code) {
            in HIRA_START..HIRA_END -> (c.code + OFFSET).toChar()
            0x309D, 0x309E -> (c.code + OFFSET).toChar()
            else -> c
        }
    }

    private inline fun mapChars(s: String, f: (Char) -> Char): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(f(c))
        return sb.toString()
    }

    fun isKana(cp: Int): Boolean =
        cp in 0x3041..0x309F || cp in 0x30A0..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F

    fun isKanji(cp: Int): Boolean =
        cp in 0x4E00..0x9FFF ||
            cp in 0x3400..0x4DBF ||
            cp in 0x20000..0x2EBEF ||
            cp in 0x30000..0x3134F ||
            cp in 0xF900..0xFAFF

    fun hasKanji(s: String): Boolean = codePoints(s).any { isKanji(it) }

    fun isAllKana(s: String): Boolean = s.isNotEmpty() && codePoints(s).all { isKana(it) }

    /** Distinct kanji in first-seen order, as strings (supplementary-plane safe). */
    fun distinctKanji(s: String): List<String> {
        val seen = LinkedHashSet<String>()
        for (cp in codePoints(s)) if (isKanji(cp)) seen += codePointToString(cp)
        return seen.toList()
    }

    fun codePoints(s: String): Sequence<Int> = sequence {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
                yield(((c.code - 0xD800) shl 10) + (s[i + 1].code - 0xDC00) + 0x10000)
                i += 2
            } else {
                yield(c.code)
                i++
            }
        }
    }

    fun codePointToString(cp: Int): String =
        if (cp < 0x10000) {
            cp.toChar().toString()
        } else {
            val v = cp - 0x10000
            charArrayOf(((v shr 10) + 0xD800).toChar(), ((v and 0x3FF) + 0xDC00).toChar()).concatToString()
        }
}
