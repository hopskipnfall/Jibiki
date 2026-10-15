package dev.jibiki.japanese

private const val HALF_WIDTH_KANA =
    "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟ"
private const val FULL_WIDTH_KANA =
    "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゛゜"

private const val DAKUTEN_BASES = "カキクケコサシスセソタチツテトハヒフヘホ"
private const val HANDAKUTEN_BASES = "ハヒフヘホ"

/**
 * Query normalization (a focused subset of NFKC): full-width ASCII → ASCII, ideographic space →
 * space, half-width katakana → full-width (combining voiced marks), trimmed with inner
 * whitespace collapsed.
 */
fun normalizeQuery(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        val code = c.code
        when {
            code in 0xFF01..0xFF5E -> sb.append((code - 0xFEE0).toChar())
            code == 0x3000 -> sb.append(' ')
            c == 'ﾞ' && sb.isNotEmpty() && voiced(sb.last()) != null -> sb[sb.length - 1] = voiced(sb.last())!!
            c == 'ﾟ' && sb.isNotEmpty() && sb.last() in HANDAKUTEN_BASES -> sb[sb.length - 1] = sb.last() + 2
            else -> {
                val idx = HALF_WIDTH_KANA.indexOf(c)
                sb.append(if (idx >= 0) FULL_WIDTH_KANA[idx] else c)
            }
        }
    }
    return sb.toString().trim().replace(Regex("\\s+"), " ")
}

private fun voiced(c: Char): Char? = when (c) {
    in DAKUTEN_BASES -> c + 1
    'ウ' -> 'ヴ'
    'ワ' -> 'ヷ'
    'ヲ' -> 'ヺ'
    else -> null
}
