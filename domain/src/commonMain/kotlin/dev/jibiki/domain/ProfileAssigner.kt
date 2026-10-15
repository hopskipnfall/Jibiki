package dev.jibiki.domain

import dev.jibiki.japanese.Kana
import dev.jibiki.model.DictKind
import dev.jibiki.model.Glossary
import dev.jibiki.model.Profile
import dev.jibiki.model.ScNode
import dev.jibiki.yomitan.GlossaryParser
import dev.jibiki.yomitan.YomitanIndex

/** Decides which default profiles (English / 日本語) a newly imported dictionary joins. */
object ProfileAssigner {
    fun profilesFor(index: YomitanIndex, kinds: Set<DictKind>, sampleGlossaries: List<String>): Set<Long> {
        if (DictKind.TERMS !in kinds) return setOf(Profile.ENGLISH, Profile.JAPANESE)
        when (index.targetLanguage?.lowercase()) {
            "en" -> return setOf(Profile.ENGLISH)
            "ja" -> return setOf(Profile.JAPANESE)
        }
        var latin = 0
        var japanese = 0
        for (json in sampleGlossaries) {
            for (g in GlossaryParser.parse(json)) {
                for (cp in Kana.codePoints(text(g))) {
                    when {
                        cp in 'a'.code..'z'.code || cp in 'A'.code..'Z'.code -> latin++
                        Kana.isKana(cp) || Kana.isKanji(cp) -> japanese++
                    }
                }
            }
        }
        val total = latin + japanese
        return if (total > 0 && latin * 100 / total >= 60) setOf(Profile.ENGLISH) else setOf(Profile.JAPANESE)
    }

    internal fun text(g: Glossary): String = when (g) {
        is Glossary.Text -> g.text
        is Glossary.Structured -> text(g.content)
        is Glossary.Deinflection -> ""
        is Glossary.Image -> ""
    }

    private fun text(node: ScNode): String = when (node) {
        is ScNode.Text -> node.text
        is ScNode.Element -> node.children.joinToString("") { text(it) }
        is ScNode.Image -> ""
    }
}
