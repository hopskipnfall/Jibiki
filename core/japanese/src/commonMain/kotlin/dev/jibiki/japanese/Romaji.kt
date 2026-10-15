package dev.jibiki.japanese

/** [kana] is the converted text; [pending] is a trailing romaji fragment that may still grow. */
data class RomajiResult(val kana: String, val pending: String)

/**
 * Romaji → hiragana, accepting Hepburn, Kunrei-shiki and common IME spellings.
 * `n` handling follows WanaKana: `nn` before a vowel/y yields ん + n-row (`onna` → おんな).
 */
object Romaji {
    private val table: Map<String, String> = buildTable()
    private val prefixes: Set<String> = table.keys.flatMap { k -> (1 until k.length).map { k.substring(0, it) } }.toSet()
    private const val MAX_KEY = 4
    private const val VOWELS = "aiueo"

    fun containsLatin(s: String): Boolean = s.any { it in 'a'..'z' || it in 'A'..'Z' }

    fun toHiragana(input: String): RomajiResult = convert(input, final = false)

    /** Converts for submission: a trailing `n` becomes ん and other fragments are dropped. */
    fun finalize(input: String): String = convert(input, final = true).kana

    private fun convert(input: String, final: Boolean): RomajiResult {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)

            if (c == 'n') {
                when {
                    next == null -> {
                        if (final) out.append('ん') else return RomajiResult(out.toString(), "n")
                        i++
                        continue
                    }
                    next == '\'' -> { out.append('ん'); i += 2; continue }
                    next == 'n' -> {
                        val after = s.getOrNull(i + 2)
                        out.append('ん')
                        i += if (after != null && (after in VOWELS || after == 'y')) 1 else 2
                        continue
                    }
                    next !in VOWELS && next != 'y' -> { out.append('ん'); i++; continue }
                }
            }

            if (c in 'a'..'z' && c !in VOWELS && c != 'n') {
                if (next == c) { out.append('っ'); i++; continue }
                if (c == 't' && next == 'c' && s.getOrNull(i + 2) == 'h') { out.append('っ'); i++; continue }
            }

            var matched = false
            for (len in minOf(MAX_KEY, s.length - i) downTo 1) {
                val key = s.substring(i, i + len)
                val kana = table[key] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (matched) continue

            val rest = s.substring(i)
            if (rest in prefixes) {
                return if (final) RomajiResult(out.toString(), "") else RomajiResult(out.toString(), rest)
            }
            // Keep the original character (preserves case/script of non-romaji input).
            out.append(input[i])
            i++
        }
        return RomajiResult(out.toString(), "")
    }

    private fun buildTable(): Map<String, String> {
        val t = HashMap<String, String>()
        fun row(consonant: String, kana: String) {
            // kana: five characters for a i u e o (space = no entry)
            VOWELS.forEachIndexed { idx, v -> if (kana[idx] != ' ') t[consonant + v] = kana[idx].toString() }
        }
        fun yoon(prefix: String, base: String) {
            t[prefix + "a"] = base + "ゃ"
            t[prefix + "u"] = base + "ゅ"
            t[prefix + "o"] = base + "ょ"
        }
        row("", "あいうえお")
        row("k", "かきくけこ"); row("g", "がぎぐげご")
        row("s", "さしすせそ"); row("z", "ざじずぜぞ")
        row("t", "たちつてと"); row("d", "だぢづでど")
        row("n", "なにぬねの"); row("h", "はひふへほ")
        row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ")
        row("m", "まみむめも"); row("y", "や ゆ よ")
        row("r", "らりるれろ"); row("w", "わ   を")
        row("c", "かしくせこ")
        t["shi"] = "し"; t["chi"] = "ち"; t["tsu"] = "つ"; t["fu"] = "ふ"; t["ji"] = "じ"
        t["ye"] = "いぇ"; t["wi"] = "うぃ"; t["we"] = "うぇ"; t["wo"] = "を"
        yoon("ky", "き"); yoon("gy", "ぎ"); yoon("sy", "し"); yoon("sh", "し"); yoon("zy", "じ")
        yoon("j", "じ"); yoon("jy", "じ"); yoon("ty", "ち"); yoon("ch", "ち"); yoon("cy", "ち")
        yoon("dy", "ぢ"); yoon("ny", "に"); yoon("hy", "ひ"); yoon("by", "び"); yoon("py", "ぴ")
        yoon("my", "み"); yoon("ry", "り"); yoon("fy", "ふ")
        t["she"] = "しぇ"; t["sye"] = "しぇ"; t["je"] = "じぇ"; t["jye"] = "じぇ"; t["zye"] = "じぇ"
        t["che"] = "ちぇ"; t["tye"] = "ちぇ"; t["cye"] = "ちぇ"
        t["fa"] = "ふぁ"; t["fi"] = "ふぃ"; t["fe"] = "ふぇ"; t["fo"] = "ふぉ"
        t["va"] = "ゔぁ"; t["vi"] = "ゔぃ"; t["vu"] = "ゔ"; t["ve"] = "ゔぇ"; t["vo"] = "ゔぉ"
        t["tsa"] = "つぁ"; t["tsi"] = "つぃ"; t["tse"] = "つぇ"; t["tso"] = "つぉ"
        t["thi"] = "てぃ"; t["thu"] = "てゅ"; t["dhi"] = "でぃ"; t["dhu"] = "でゅ"
        t["twu"] = "とぅ"; t["dwu"] = "どぅ"
        for (p in listOf("x", "l")) {
            row(p, "ぁぃぅぇぉ")
            yoon(p + "y", "")
            t[p + "tu"] = "っ"; t[p + "tsu"] = "っ"; t[p + "wa"] = "ゎ"; t[p + "ka"] = "ゕ"; t[p + "ke"] = "ゖ"
        }
        t["-"] = "ー"
        return t
    }
}
