package dev.jibiki.radicals

import dev.jibiki.japanese.Kana

/** [key] is the RADKFILE element character; [display] is the glyph to show the user. */
data class Radical(val key: String, val display: String, val strokes: Int)

/** Multi-radical kanji lookup built from EDRDG RADKFILE data. */
class RadicalIndex(val radicals: List<Radical>, private val kanjiByRadical: Map<String, Set<String>>) {
    private val radicalsByKanji: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        for (r in radicals) for (k in kanjiByRadical[r.key].orEmpty()) getOrPut(k) { mutableListOf() } += r.key
    }
    private val byKey = radicals.associateBy { it.key }

    /** Kanji containing every selected radical; empty selection → empty. */
    fun candidates(selected: Set<String>): Set<String> {
        if (selected.isEmpty()) return emptySet()
        val sets = selected.map { kanjiByRadical[it].orEmpty() }.sortedBy { it.size }
        return sets.drop(1).fold(sets.first()) { acc, s -> acc intersect s }
    }

    /** Radicals that still lead to at least one kanji together with [selected]. */
    fun available(selected: Set<String>): Set<String> {
        if (selected.isEmpty()) return radicals.mapTo(HashSet()) { it.key }
        return candidates(selected).flatMapTo(HashSet()) { radicalsByKanji[it].orEmpty() }
    }

    /** Component radicals of [kanji], ordered by stroke count. */
    fun components(kanji: String): List<Radical> =
        radicalsByKanji[kanji].orEmpty().mapNotNull { byKey[it] }.sortedBy { it.strokes }

    fun radicalCount(kanji: String): Int = radicalsByKanji[kanji]?.size ?: 0

    companion object {
        fun parse(radkText: String): RadicalIndex {
            val radicals = ArrayList<Radical>()
            val kanji = HashMap<String, MutableSet<String>>()
            var current: String? = null
            for (line in radkText.lineSequence()) {
                when {
                    line.startsWith("#") || line.isBlank() -> Unit
                    line.startsWith("$") -> {
                        val parts = line.trim().split(Regex("\\s+"))
                        val key = parts[1]
                        radicals += Radical(key, RadicalGlyphs.display(key), parts[2].toInt())
                        current = key
                        kanji[key] = LinkedHashSet()
                    }
                    else -> current?.let { key ->
                        Kana.codePoints(line.trim()).forEach { kanji.getValue(key) += Kana.codePointToString(it) }
                    }
                }
            }
            return RadicalIndex(radicals, kanji)
        }
    }
}

/** RADKFILE uses stand-in kanji for some elements; map them to the glyph users recognise. */
object RadicalGlyphs {
    private val display = mapOf(
        "｜" to "丨", "化" to "亻", "个" to "𠆢", "并" to "丷", "刈" to "刂", "乞" to "𠂉",
        "込" to "辶", "尚" to "⺌", "忙" to "忄", "扎" to "扌", "汁" to "氵", "犯" to "犭",
        "艾" to "艹", "邦" to "阝", "阡" to "阝", "老" to "耂", "杰" to "灬", "礼" to "礻",
        "疔" to "疒", "禹" to "禸", "初" to "衤", "買" to "罒", "滴" to "啇",
    )

    fun display(key: String): String = display[key] ?: key
}
