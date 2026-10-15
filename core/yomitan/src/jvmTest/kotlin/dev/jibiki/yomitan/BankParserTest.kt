package dev.jibiki.yomitan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BankParserTest {
    @Test fun termsWithOneMalformedRow() {
        val parsed = BankParser.terms(
            """[
              ["食べる","たべる","v1 P","v1",100,["to eat"],1,"P ichi"],
              "garbage",
              ["読む","よむ",null,"v5",5,[{"type":"text","text":"to read"}],2,""]
            ]""",
        )
        assertEquals(1, parsed.skipped)
        assertEquals(2, parsed.rows.size)
        val t = parsed.rows[0]
        assertEquals("食べる", t.expression)
        assertEquals(listOf("v1", "P"), t.definitionTags)
        assertEquals(setOf("v1"), t.rules)
        assertEquals(100, t.score)
        assertEquals("""["to eat"]""", t.glossaryJson)
        assertEquals(listOf("P", "ichi"), t.termTags)
        assertEquals(emptyList(), parsed.rows[1].definitionTags)
    }

    @Test fun kanjiAndMeta() {
        val k = BankParser.kanji("""[["食","ショク ジキ","く.う た.べる","jouyou",["eat","food"],{"strokes":"9","grade":"2"}]]""").rows.single()
        assertEquals(listOf("ショク", "ジキ"), k.onyomi)
        assertEquals(listOf("eat", "food"), k.meanings)
        assertEquals("9", k.stats["strokes"])
        val m = BankParser.termMeta("""[["食べる","freq",{"reading":"たべる","frequency":{"value":120,"displayValue":"120㋕"}}]]""").rows.single()
        assertEquals("たべる", m.reading)
        assertEquals("freq", m.mode)
    }

    @Test fun tags() {
        val tag = BankParser.tags("""[["v1","partOfSpeech",-3,"Ichidan verb",0]]""").rows.single()
        assertEquals("Ichidan verb", tag.notes)
        assertEquals(-3, tag.order)
    }

    @Test fun nonArrayRootThrows() {
        assertFailsWith<YomitanFormatException> { BankParser.terms("""{"a":1}""") }
    }
}
