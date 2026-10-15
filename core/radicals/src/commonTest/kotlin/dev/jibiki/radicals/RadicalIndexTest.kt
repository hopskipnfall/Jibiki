package dev.jibiki.radicals

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RadicalIndexTest {
    // Invented miniature RADKFILE.
    private val index = RadicalIndex.parse(
        """
        # comment
        $ 一 1
        木本
        $ 汁 3 4653
        海池
        $ 木 4
        木本林
        $ 毋 4
        海毎
        """.trimIndent(),
    )

    @Test fun parsesRadicalsWithDisplayGlyphs() {
        assertEquals(listOf("一", "氵", "木", "毋"), index.radicals.map { it.display })
        assertEquals(3, index.radicals[1].strokes)
    }

    @Test fun intersection() {
        assertEquals(setOf("海"), index.candidates(setOf("汁", "毋")))
        assertEquals(setOf("木", "本"), index.candidates(setOf("一", "木")))
        assertTrue(index.candidates(emptySet()).isEmpty())
    }

    @Test fun availabilityGreysOutImpossibleRadicals() {
        assertEquals(setOf("汁", "毋"), index.available(setOf("汁", "毋")))
        assertEquals(setOf("汁", "毋"), index.available(setOf("毋")))
        assertEquals(4, index.available(emptySet()).size)
    }

    @Test fun componentsOrderedByStrokes() {
        assertEquals(listOf("氵", "毋"), index.components("海").map { it.display })
    }
}
