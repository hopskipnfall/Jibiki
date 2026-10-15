package dev.jibiki.domain

import dev.jibiki.db.allDictionaries
import dev.jibiki.db.setProfileEnabled
import dev.jibiki.japanese.Deinflector
import dev.jibiki.model.Glossary
import dev.jibiki.model.Profile
import dev.jibiki.radicals.RadicalIndex
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {
    private val radicals = RadicalIndex.parse("$ 人 2\n食\n$ 良 7\n食\n")

    private suspend fun setup(vararg zips: () -> dev.jibiki.yomitan.ZipArchive): SearchService {
        val db = testDb()
        val importer = ImportService(db) { 0L }
        zips.forEach { importer.import(it()).last() }
        return SearchService(db, Deinflector()) { radicals }.also { lastDb = db }
    }

    private lateinit var lastDb: dev.jibiki.db.Database

    @Test fun romajiExactMatchFirst() = runTest {
        val search = setup(TestDictionaries::english)
        val r = search.search("taberu", Profile.ENGLISH)
        assertEquals("たべる", r.kanaQuery)
        assertEquals("食べる", r.words.first().expression)
        assertEquals(MatchType.EXACT, r.words.first().matchType)
        assertEquals("Test EN", r.words.first().definitions.single().dictTitle)
        assertEquals(listOf(Glossary.Text("to eat")), r.words.first().definitions.single().glossary)
        assertEquals("Ichidan verb", r.words.first().definitions.single().tags.first { it.name == "v1" }.notes)
    }

    @Test fun deinflectedMatchWithReasons() = runTest {
        val search = setup(TestDictionaries::english)
        val top = search.search("食べなかった", Profile.ENGLISH).words.first()
        assertEquals("食べる", top.expression)
        assertEquals(MatchType.DEINFLECTED, top.matchType)
        assertEquals(listOf("negative", "-た"), top.reasons)
        assertEquals("高い", search.search("たかくない", Profile.ENGLISH).words.first().expression)
    }

    @Test fun latinHeadwordFoundDespiteRomajiConversion() = runTest {
        val search = setup(TestDictionaries::english)
        assertEquals("Tシャツ", search.search("Tシャツ", Profile.ENGLISH).words.first().expression)
    }

    @Test fun emptyReadingMergesWithKanaReading() = runTest {
        val db = testDb()
        val importer = ImportService(db) { 0L }
        importer.import(TestDictionaries.english()).last()
        importer.import(
            zipOf(
                mapOf(
                    "index.json" to """{"title":"Other","revision":"1","format":3,"targetLanguage":"en"}""",
                    "term_bank_1.json" to """[["ねこ","ねこ","n","",1,["cat (other)"],1,""]]""",
                ),
            ),
        ).last()
        val words = SearchService(db, Deinflector()) { radicals }.search("ねこ", Profile.ENGLISH).words
        assertEquals(1, words.count { it.expression == "ねこ" })
        assertEquals(2, words.first { it.expression == "ねこ" }.definitions.size)
    }

    @Test fun suruVerbStem() = runTest {
        val search = setup(TestDictionaries::english)
        val top = search.search("勉強させられた", Profile.ENGLISH).words.first()
        assertEquals("勉強", top.expression)
        assertEquals(MatchType.DEINFLECTED, top.matchType)
    }

    @Test fun partialMatchForPhrases() = runTest {
        val search = setup(TestDictionaries::english)
        val top = search.search("食べ物がおいしい", Profile.ENGLISH).words.first()
        assertEquals("食べ物", top.expression)
        assertEquals(MatchType.PARTIAL, top.matchType)
    }

    @Test fun prefixMatches() = runTest {
        val search = setup(TestDictionaries::english)
        val words = search.search("たべ", Profile.ENGLISH).words
        assertEquals(listOf("食べる", "食べ物"), words.map { it.expression })
        // たべ is also the continuative stem of 食べる, which outranks a plain prefix match.
        assertEquals(listOf(MatchType.DEINFLECTED, MatchType.PREFIX), words.map { it.matchType })
    }

    @Test fun singleKanjiShowsKanjiAndContainingWords() = runTest {
        val search = setup(TestDictionaries::english, TestDictionaries::kanji, TestDictionaries::frequency)
        val r = search.search("食", Profile.ENGLISH)
        assertEquals("食", r.kanji.first().character)
        assertEquals("eat", r.kanji.first().meaning)
        assertEquals(listOf("人", "良"), r.kanji.first().components.map { it.key })
        // Ordered by frequency rank: 食べ物 (50) < 食べる (900) < 食堂 (3000), then unknown 食.
        assertEquals(listOf("食べ物", "食べる", "食堂", "食"), r.containing.map { it.expression })
        assertEquals("食", r.words.first().expression)
    }

    @Test fun kanjiFromTopWordsAppearInKanjiSection() = runTest {
        val search = setup(TestDictionaries::english)
        assertEquals(listOf("食"), search.search("たべる", Profile.ENGLISH).kanji.map { it.character })
    }

    @Test fun mergesDictionariesInPriorityOrder() = runTest {
        val search = setup(TestDictionaries::english, TestDictionaries::japanese)
        val all = search.search("食べる", null).words.first()
        assertEquals(listOf("Test EN", "テスト国語"), all.definitions.map { it.dictTitle })
        val ja = search.search("食べる", Profile.JAPANESE).words.first()
        assertEquals(listOf("テスト国語"), ja.definitions.map { it.dictTitle })
    }

    @Test fun frequencyBadgesAndRanking() = runTest {
        val search = setup(TestDictionaries::english, TestDictionaries::frequency)
        val words = search.search("た", Profile.ENGLISH).words
        assertTrue(words.all { it.matchType == MatchType.PREFIX })
        assertEquals(listOf("食べ物", "食べる", "高い"), words.map { it.expression })
        assertEquals(listOf("900"), words[1].frequencies.map { it.display })
        assertEquals("Test Freq", words[1].frequencies.single().dictTitle)
    }

    @Test fun disabledDictionaryExcluded() = runTest {
        val search = setup(TestDictionaries::english)
        val id = lastDb.read { allDictionaries() }.single().id
        lastDb.write { setProfileEnabled(Profile.ENGLISH, id, false) }
        assertTrue(search.search("taberu", Profile.ENGLISH).words.isEmpty())
    }

    @Test fun kanjiDetail() = runTest {
        val search = setup(TestDictionaries::kanji)
        val d = search.kanjiDetail("食", Profile.ENGLISH)
        assertEquals(listOf("Test Kanji"), d.entries.map { it.first })
        assertEquals("320", d.frequencies.single().display)
    }
}
