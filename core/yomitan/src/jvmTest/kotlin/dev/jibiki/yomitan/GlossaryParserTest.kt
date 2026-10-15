package dev.jibiki.yomitan

import dev.jibiki.model.Glossary
import dev.jibiki.model.ScNode
import dev.jibiki.model.TermMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GlossaryParserTest {
    @Test fun plainAndTextEntries() {
        assertEquals(
            listOf(Glossary.Text("to eat"), Glossary.Text("to dine")),
            GlossaryParser.parse("""["to eat",{"type":"text","text":"to dine"}]"""),
        )
    }

    @Test fun structuredContentList() {
        val g = GlossaryParser.parse(
            """[{"type":"structured-content","content":{"tag":"ul","style":{"fontWeight":"bold"},"data":{"content":"glossary"},
               "content":[{"tag":"li","content":"one"},{"tag":"li","content":["two ",{"tag":"ruby","content":["漢",{"tag":"rt","content":"かん"}]}]}]}}]""",
        ).single()
        val ul = assertIs<ScNode.Element>(assertIs<Glossary.Structured>(g).content)
        assertEquals("ul", ul.tag)
        assertEquals("bold", ul.style["fontWeight"])
        assertEquals("glossary", ul.data["content"])
        assertEquals(2, ul.children.size)
        val li2 = assertIs<ScNode.Element>(ul.children[1])
        assertEquals("ruby", assertIs<ScNode.Element>(li2.children[1]).tag)
    }

    @Test fun imageInsideStructuredContent() {
        val g = GlossaryParser.parse("""[{"type":"structured-content","content":[{"tag":"img","path":"img/a.png","width":2,"height":1,"sizeUnits":"em"}]}]""")
        val span = assertIs<ScNode.Element>(assertIs<Glossary.Structured>(g.single()).content)
        val img = assertIs<ScNode.Image>(span.children.single())
        assertEquals("img/a.png", img.path)
        assertEquals(2.0, img.width)
        assertEquals("em", img.sizeUnits)
    }

    @Test fun deinflectionEntry() {
        assertEquals(listOf(Glossary.Deinflection("食べる", listOf("past"))), GlossaryParser.parse("""[["食べる",["past"]]]"""))
    }

    @Test fun frequencyVariants() {
        assertEquals(TermMetaData.Frequency(120, "120㋕", null), GlossaryParser.parseMeta("""{"value":120,"displayValue":"120㋕"}""", "freq"))
        assertEquals(TermMetaData.Frequency(42, "42", null), GlossaryParser.parseMeta("42", "freq"))
        assertEquals(TermMetaData.Frequency(7, "7/12", null), GlossaryParser.parseMeta("\"7/12\"", "freq"))
        assertEquals(TermMetaData.Frequency(5, "5", "たべる"), GlossaryParser.parseMeta("""{"reading":"たべる","frequency":5}""", "freq"))
    }

    @Test fun pitch() {
        val p = assertIs<TermMetaData.Pitch>(
            GlossaryParser.parseMeta("""{"reading":"たべる","pitches":[{"position":2},{"position":"LHH","nasal":1}]}""", "pitch"),
        )
        assertEquals(listOf(2, 0), p.accents.map { it.downstep })
        assertEquals(listOf(1), p.accents[1].nasal)
    }
}
