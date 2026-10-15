package dev.jibiki.app.ui

private const val SMALL = "ゃゅょぁぃぅぇぉゎャュョァィゥェォヮ"

/** Splits kana into morae (small kana attach to the previous mora). */
fun morae(reading: String): List<String> {
    val out = ArrayList<String>()
    for (c in reading) {
        if (c in SMALL && out.isNotEmpty()) out[out.lastIndex] = out.last() + c else out += c.toString()
    }
    return out
}

/** "たべꜜる" for downstep 2; heiban (0) is marked with a trailing "￣". */
fun pitchNotation(reading: String, downstep: Int): String {
    val m = morae(reading)
    if (downstep <= 0 || downstep > m.size) return reading + "￣"
    return m.take(downstep).joinToString("") + "ꜜ" + m.drop(downstep).joinToString("")
}
