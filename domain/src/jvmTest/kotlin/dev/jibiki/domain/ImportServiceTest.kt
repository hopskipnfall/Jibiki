package dev.jibiki.domain

import dev.jibiki.db.activeDictionaries
import dev.jibiki.db.allDictionaries
import dev.jibiki.db.insertDictionary
import dev.jibiki.db.media
import dev.jibiki.model.DictKind
import dev.jibiki.model.DictStatus
import dev.jibiki.model.Profile
import dev.jibiki.yomitan.YomitanIndex
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImportServiceTest {
    @Test fun importsTermsTagsAndMedia() = runTest {
        val db = testDb()
        val service = ImportService(db) { 42L }
        val events = service.import(TestDictionaries.english()).toList()
        val finished = assertIs<ImportEvent.Finished>(events.last())
        assertEquals(1, finished.skipped)
        assertIs<ImportEvent.Started>(events.first())
        assertTrue(events.any { it is ImportEvent.Progress })

        val dict = db.read { allDictionaries() }.single()
        assertEquals(DictStatus.READY, dict.status)
        assertEquals(9, dict.termCount)
        assertEquals(setOf(DictKind.TERMS), dict.kinds)
        assertEquals(42L, dict.importedAt)
        assertEquals(mapOf(dict.id to 0), db.read { activeDictionaries(Profile.ENGLISH) })
        assertContentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), db.read { media(dict.id, "img/a.png") })
        assertEquals(4L, db.read { long("SELECT count(*) FROM term_kanji WHERE kanji = '食'") })
    }

    @Test fun profileAssignment() = runTest {
        val db = testDb()
        val service = ImportService(db) { 0L }
        service.import(TestDictionaries.japanese()).last()
        service.import(TestDictionaries.kanji()).last()
        val dicts = db.read { allDictionaries() }.associateBy { it.title }
        val ja = dicts.getValue("テスト国語").id
        val kanji = dicts.getValue("Test Kanji").id
        assertEquals(setOf(ja, kanji), db.read { activeDictionaries(Profile.JAPANESE) }.keys)
        assertEquals(setOf(kanji), db.read { activeDictionaries(Profile.ENGLISH) }.keys)
        assertEquals(setOf(DictKind.KANJI, DictKind.KANJI_FREQ), dicts.getValue("Test Kanji").kinds)
    }

    @Test fun rejectsOldFormatWithoutWriting() = runTest {
        val db = testDb()
        val zip = zipOf(mapOf("index.json" to """{"title":"Old","revision":"1","format":2}""", "term_bank_1.json" to "[]"))
        val event = ImportService(db) { 0L }.import(zip).last()
        assertIs<ImportEvent.Failed>(event)
        assertEquals(0L, db.read { long("SELECT count(*) FROM dictionary") })
    }

    @Test fun brokenBankFailsAndCleansUp() = runTest {
        val db = testDb()
        val zip = zipOf(
            mapOf(
                "index.json" to """{"title":"Broken","revision":"1","format":3}""",
                "term_bank_1.json" to """[["食べる","たべる","","v1",0,["x"],1,""]]""",
                "term_bank_2.json" to "{not json",
            ),
        )
        val event = ImportService(db) { 0L }.import(zip).last()
        assertIs<ImportEvent.Failed>(event)
        assertEquals(0L, db.read { long("SELECT count(*) FROM term") })
        assertEquals(0L, db.read { long("SELECT count(*) FROM dictionary") })
    }

    @Test fun duplicatePolicies() = runTest {
        val db = testDb()
        val service = ImportService(db) { 0L }
        service.import(TestDictionaries.english(revision = "1")).last()
        assertIs<ImportEvent.Skipped>(service.import(TestDictionaries.english(), DuplicatePolicy.SKIP).last())
        service.import(TestDictionaries.english(revision = "2"), DuplicatePolicy.REPLACE).last()
        assertEquals(listOf("2"), db.read { allDictionaries() }.map { it.revision })
        service.import(TestDictionaries.english(revision = "3"), DuplicatePolicy.KEEP_BOTH).last()
        assertEquals(2, db.read { allDictionaries() }.size)
    }

    @Test fun cleanupRemovesInterruptedImports() = runTest {
        val db = testDb()
        db.write { insertDictionary(YomitanIndex("Half", "1", 3), 0L) }
        assertEquals(1, ImportService(db) { 0L }.cleanupIncomplete())
        assertEquals(0L, db.read { long("SELECT count(*) FROM dictionary") })
    }
}
