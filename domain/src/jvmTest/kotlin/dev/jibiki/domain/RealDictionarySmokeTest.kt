package dev.jibiki.domain

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.jibiki.db.Database
import dev.jibiki.japanese.Deinflector
import dev.jibiki.model.Profile
import dev.jibiki.radicals.RadicalIndex
import dev.jibiki.yomitan.JavaZipArchive
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Opt-in smoke test against real dictionaries (never committed). Run with:
 *   JIBIKI_DICT_DIR=/path/to/zips JIBIKI_DICTS="jitendex,KANJIDIC,JPDB_v2,NHK" ./gradlew :domain:jvmTest --tests '*RealDictionarySmokeTest*'
 */
class RealDictionarySmokeTest {
    @Test fun importAndSearch() = runBlocking {
        val dir = System.getenv("JIBIKI_DICT_DIR")?.let(::File) ?: return@runBlocking
        val wanted = System.getenv("JIBIKI_DICTS")?.split(',').orEmpty()
        val zips = dir.listFiles { f -> f.name.endsWith(".zip") && wanted.any { w -> f.name.contains(w) } }!!.sortedBy { it.name }
        val dbFile = File.createTempFile("jibiki-smoke", ".db").also { it.delete(); it.deleteOnExit() }
        val db = Database.open(BundledSQLiteDriver(), dbFile.absolutePath)
        val importer = ImportService(db) { 0L }
        for (zip in zips) {
            val time = measureTime {
                val events = importer.import(JavaZipArchive(zip)).toList()
                println("  ${zip.name}: ${events.last()}")
            }
            println("IMPORT ${zip.name} (${zip.length() / 1_000_000} MB) took $time")
        }
        val radk = File("../app/src/commonMain/composeResources/files/radkfilex.utf8").readText()
        val search = SearchService(db, Deinflector()) { RadicalIndex.parse(radk) }
        for (q in listOf("taberu", "食べなかった", "行った", "日本", "たかくない", "勉強させられた", "食", "人", "の", "kon'ya")) {
            search.search(q, Profile.ENGLISH) // warm-up
            val (r, t) = measureTimedValue { search.search(q, Profile.ENGLISH) }
            val top = r.words.take(3).joinToString(" | ") { w ->
                "${w.expression}【${w.reading}】${w.matchType}${if (w.reasons.isNotEmpty()) w.reasons else ""} " +
                    "freq=${w.frequencies.firstOrNull()?.display} pitch=${w.pitches.firstOrNull()?.accents?.map { it.downstep }} " +
                    "dicts=${w.definitions.map { it.dictTitle }.distinct()}"
            }
            println("SEARCH '$q' ${t.inWholeMilliseconds}ms words=${r.words.size} kanji=${r.kanji.map { it.character + ":" + it.meaning }} containing=${r.containing.size}\n    $top")
        }
        assertTrue(true)
    }
}
