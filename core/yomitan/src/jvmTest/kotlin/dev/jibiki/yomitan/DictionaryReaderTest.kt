package dev.jibiki.yomitan

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DictionaryReaderTest {
    @Test fun classifiesFilesAndOrdersBanksNumerically() = runTest {
        val zip = JavaZipArchive(
            fixtureZip(
                mapOf(
                    "index.json" to """{"title":"Test","revision":"1","format":3}""",
                    "term_bank_10.json" to "[]",
                    "term_bank_2.json" to "[]",
                    "kanji_bank_1.json" to "[]",
                    "tag_bank_1.json" to "[]",
                    "img/a.png" to byteArrayOf(1, 2, 3),
                ),
            ),
        )
        val reader = DictionaryReader(zip)
        assertEquals("Test", reader.index().title)
        assertEquals(listOf("term_bank_2.json", "term_bank_10.json"), reader.termBanks)
        assertEquals(listOf("kanji_bank_1.json"), reader.kanjiBanks)
        assertEquals(listOf("img/a.png"), reader.mediaFiles)
    }

    @Test fun versionFieldAccepted() {
        assertEquals(3, YomitanIndex.parse("""{"title":"T","revision":"r","version":3}""").formatVersion)
    }

    @Test fun oldFormatRejected() {
        assertFailsWith<YomitanFormatException> { YomitanIndex.parse("""{"title":"T","revision":"r","format":2}""") }
    }

    @Test fun missingIndexRejected() = runTest {
        val reader = DictionaryReader(JavaZipArchive(fixtureZip(mapOf("term_bank_1.json" to "[]"))))
        assertFailsWith<YomitanFormatException> { reader.index() }
    }
}
