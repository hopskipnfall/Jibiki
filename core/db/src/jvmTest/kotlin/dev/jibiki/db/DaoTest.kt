package dev.jibiki.db

import dev.jibiki.model.DictStatus
import dev.jibiki.model.Tag
import dev.jibiki.yomitan.RawKanji
import dev.jibiki.yomitan.RawTerm
import dev.jibiki.yomitan.YomitanIndex
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DaoTest {
    private fun term(expression: String, reading: String, score: Int = 0) =
        RawTerm(expression, reading, emptyList(), setOf("v1"), score, """["gloss $expression"]""", 0, emptyList())

    private suspend fun seeded(): Pair<Database, Long> {
        val db = testDb()
        val id = db.write {
            val id = insertDictionary(YomitanIndex(title = "Test", revision = "1", format = 3), importedAt = 1L)
            insertTerms(
                id,
                listOf(term("食べる", "たべる", 5), term("食べ物", "たべもの"), term("読む", "よむ")),
                readingKey = { it.reading },
                kanjiOf = { e -> e.filter { it.code >= 0x4E00 }.map { it.toString() }.distinct() },
            )
            insertKanji(id, listOf(RawKanji("食", listOf("ショク"), listOf("た.べる"), emptyList(), listOf("eat"), mapOf("strokes" to "9"))))
            insertTags(id, listOf(Tag("v1", "partOfSpeech", 0, "Ichidan verb", 0)))
            setDictionaryStatus(id, DictStatus.READY)
            assignToProfile(1, id)
            id
        }
        return db to id
    }

    @Test fun exactAndPrefixLookups() = runTest {
        val (db, id) = seeded()
        assertEquals(listOf("食べる"), db.read { termsByKeys(listOf("たべる"), listOf(id)) }.map { it.expression })
        assertEquals(listOf("食べる"), db.read { termsByKeys(listOf("食べる"), listOf(id)) }.map { it.expression })
        assertEquals(setOf("食べる", "食べ物"), db.read { termsByPrefix("たべ", listOf(id), 10) }.map { it.expression }.toSet())
        assertEquals(emptyList(), db.read { termsByKeys(listOf("たべる"), listOf(id + 1)) })
    }

    @Test fun kanjiContainmentAndStrokes() = runTest {
        val (db, id) = seeded()
        assertEquals(setOf("食べる", "食べ物"), db.read { termKeysContainingKanji("食", listOf(id)) }.map { it.expression }.toSet())
        assertEquals(mapOf("食" to 9), db.read { strokeCounts(listOf("食", "読")) })
        assertEquals(listOf("eat"), db.read { kanjiFor(listOf("食"), listOf(id)) }.single().meanings)
    }

    @Test fun tagsAndProfiles() = runTest {
        val (db, id) = seeded()
        assertEquals("Ichidan verb", db.read { tagsFor(listOf(id), listOf("v1")) }[id to "v1"]?.notes)
        assertEquals(mapOf(id to 0), db.read { activeDictionaries(1) })
        assertEquals(emptyMap(), db.read { activeDictionaries(2) })
        assertEquals(mapOf(id to 0), db.read { activeDictionaries(null) })
        db.write { setProfileEnabled(1, id, false) }
        assertEquals(emptyMap(), db.read { activeDictionaries(1) })
    }

    @Test fun deleteRemovesEverything() = runTest {
        val (db, id) = seeded()
        db.deleteDictionary(id)
        for (table in listOf("term", "term_kanji", "kanji", "tag", "profile_dictionary", "dictionary")) {
            assertEquals(0L, db.read { long("SELECT count(*) FROM $table") }, table)
        }
        assertNull(db.read { dictionary(id) })
    }

    @Test fun settingsRoundTrip() = runTest {
        val db = testDb()
        db.write { setSetting("a", "1") }
        db.write { setSetting("a", "2") }
        assertEquals("2", db.read { setting("a") })
        db.write { setSetting("a", null) }
        assertNull(db.read { setting("a") })
    }
}
