package dev.jibiki.domain

import dev.jibiki.db.Database
import dev.jibiki.db.allDictionaries
import dev.jibiki.db.assignToProfile
import dev.jibiki.db.deleteDictionary
import dev.jibiki.db.insertDictionary
import dev.jibiki.db.insertKanji
import dev.jibiki.db.insertKanjiMeta
import dev.jibiki.db.insertMedia
import dev.jibiki.db.insertTags
import dev.jibiki.db.insertTermMeta
import dev.jibiki.db.insertTerms
import dev.jibiki.db.setDictionaryStatus
import dev.jibiki.db.updateDictionaryStats
import dev.jibiki.japanese.Kana
import dev.jibiki.model.DictKind
import dev.jibiki.model.DictStatus
import dev.jibiki.model.DictionaryInfo
import dev.jibiki.yomitan.BankParser
import dev.jibiki.yomitan.DictionaryReader
import dev.jibiki.yomitan.YomitanFormatException
import dev.jibiki.yomitan.YomitanIndex
import dev.jibiki.yomitan.ZipArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

sealed interface ImportEvent {
    data class Started(val title: String, val filesTotal: Int) : ImportEvent
    data class Progress(val title: String, val file: String, val filesDone: Int, val filesTotal: Int, val rows: Long) : ImportEvent
    data class Finished(val dictId: Long, val title: String, val skipped: Int) : ImportEvent
    data class Skipped(val title: String) : ImportEvent
    data class Failed(val title: String?, val message: String) : ImportEvent
}

enum class DuplicatePolicy { REPLACE, KEEP_BOTH, SKIP }

class ImportService(private val db: Database, private val now: () -> Long) {

    suspend fun peek(zip: ZipArchive): YomitanIndex = DictionaryReader(zip).index()

    suspend fun existing(title: String): List<DictionaryInfo> =
        db.read { allDictionaries() }.filter { it.title == title && it.status == DictStatus.READY }

    /** Removes dictionaries left behind by an interrupted or failed import. Returns how many. */
    suspend fun cleanupIncomplete(): Int {
        val stale = db.read { allDictionaries() }.filter { it.status != DictStatus.READY }
        stale.forEach { db.deleteDictionary(it.id) }
        return stale.size
    }

    fun import(zip: ZipArchive, policy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH): Flow<ImportEvent> = flow {
        val reader = DictionaryReader(zip)
        val index = try {
            reader.index()
        } catch (e: YomitanFormatException) {
            emit(ImportEvent.Failed(null, e.message ?: "Invalid dictionary"))
            return@flow
        }
        val title = index.title
        val previous = existing(title)
        if (policy == DuplicatePolicy.SKIP && previous.isNotEmpty()) {
            emit(ImportEvent.Skipped(title))
            return@flow
        }

        val banks: List<Pair<String, BankType>> =
            reader.tagBanks.map { it to BankType.TAG } +
                reader.termBanks.map { it to BankType.TERM } +
                reader.termMetaBanks.map { it to BankType.TERM_META } +
                reader.kanjiBanks.map { it to BankType.KANJI } +
                reader.kanjiMetaBanks.map { it to BankType.KANJI_META }
        val media = reader.mediaFiles
        val filesTotal = banks.size + media.size
        emit(ImportEvent.Started(title, filesTotal))

        val dictId = db.write { insertDictionary(index, now()) }
        val stats = Stats()
        try {
            var filesDone = 0
            for ((file, type) in banks) {
                stats.skipped += importBank(dictId, zip.readText(file), type, stats)
                filesDone++
                emit(ImportEvent.Progress(title, file, filesDone, filesTotal, stats.rows))
                yield() // on the single-threaded web runtime, let the UI draw between files
            }
            for (chunk in media.chunked(MEDIA_PER_TRANSACTION)) {
                val loaded = zip.readMany(chunk).toList()
                db.write { loaded.forEach { (path, bytes) -> insertMedia(dictId, path, mimeType(path), bytes) } }
                stats.mediaBytes += loaded.sumOf { it.second.size.toLong() }
                filesDone += chunk.size
                emit(ImportEvent.Progress(title, chunk.last(), filesDone, filesTotal, stats.rows))
            }

            val kinds = stats.kinds()
            val profiles = ProfileAssigner.profilesFor(index, kinds, stats.sampleGlossaries)
            db.write {
                updateDictionaryStats(dictId, kinds, stats.terms, stats.kanji, stats.meta, stats.mediaBytes)
                profiles.forEach { assignToProfile(it, dictId) }
                setDictionaryStatus(dictId, DictStatus.READY)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { discard(dictId) }
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: on wasm, JS errors (e.g. corrupt zip data) aren't Exceptions.
            withContext(NonCancellable) { discard(dictId) }
            emit(ImportEvent.Failed(title, failureMessage(e)))
            return@flow
        }

        // The new dictionary is READY; failures from here on must not delete it.
        if (policy == DuplicatePolicy.REPLACE) {
            withContext(NonCancellable) { previous.forEach { runCatching { db.deleteDictionary(it.id) } } }
        }
        runCatching { db.read { exec("PRAGMA optimize") } } // refresh planner statistics
        emit(ImportEvent.Finished(dictId, title, stats.skipped))
    }

    /** Best-effort removal of a failed import; leftovers are removed by [cleanupIncomplete]. */
    private suspend fun discard(dictId: Long) {
        runCatching { db.deleteDictionary(dictId) }
    }

    private enum class BankType { TAG, TERM, TERM_META, KANJI, KANJI_META }

    private class Stats {
        var terms = 0L
        var kanji = 0L
        var meta = 0L
        var mediaBytes = 0L
        var skipped = 0
        val metaModes = HashSet<String>()
        var kanjiMeta = false
        val sampleGlossaries = ArrayList<String>()
        val rows get() = terms + kanji + meta

        fun kinds(): Set<DictKind> = buildSet {
            if (terms > 0) add(DictKind.TERMS)
            if (kanji > 0) add(DictKind.KANJI)
            if ("freq" in metaModes) add(DictKind.TERM_FREQ)
            if ("pitch" in metaModes) add(DictKind.TERM_PITCH)
            if ("ipa" in metaModes) add(DictKind.TERM_IPA)
            if (kanjiMeta) add(DictKind.KANJI_FREQ)
        }
    }

    /** Parses and stores one bank file in a single transaction; returns the skipped row count. */
    private suspend fun importBank(dictId: Long, text: String, type: BankType, stats: Stats): Int = when (type) {
        BankType.TAG -> BankParser.tags(text).let { p -> db.write { insertTags(dictId, p.rows) }; p.skipped }
        BankType.TERM -> BankParser.terms(text).let { p ->
            // An empty reading means "same as the expression"; normalise so kana-only words
            // from different dictionaries merge into one result.
            val rows = p.rows.map { if (it.reading.isEmpty()) it.copy(reading = it.expression) else it }
            db.write {
                insertTerms(
                    dictId, rows,
                    readingKey = { Kana.toHiragana(it.reading) },
                    kanjiOf = Kana::distinctKanji,
                )
            }
            stats.terms += p.rows.size
            if (stats.sampleGlossaries.size < SAMPLE_SIZE) {
                stats.sampleGlossaries += p.rows.take(SAMPLE_SIZE - stats.sampleGlossaries.size).map { it.glossaryJson }
            }
            p.skipped
        }
        BankType.TERM_META -> BankParser.termMeta(text).let { p ->
            db.write { insertTermMeta(dictId, p.rows) }
            stats.meta += p.rows.size
            p.rows.mapTo(stats.metaModes) { it.mode }
            p.skipped
        }
        BankType.KANJI -> BankParser.kanji(text).let { p ->
            db.write { insertKanji(dictId, p.rows) }
            stats.kanji += p.rows.size
            p.skipped
        }
        BankType.KANJI_META -> BankParser.kanjiMeta(text).let { p ->
            db.write { insertKanjiMeta(dictId, p.rows) }
            stats.meta += p.rows.size
            if (p.rows.isNotEmpty()) stats.kanjiMeta = true
            p.skipped
        }
    }

    private fun failureMessage(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is YomitanFormatException -> msg
            msg.contains("quota", ignoreCase = true) || msg.contains("SQLITE_FULL") || msg.contains("full", ignoreCase = true) ->
                "Not enough storage space to import this dictionary."
            else -> "Import failed: ${msg.ifEmpty { e::class.simpleName ?: "unknown error" }}"
        }
    }

    private companion object {
        const val MEDIA_PER_TRANSACTION = 50
        const val SAMPLE_SIZE = 200
    }
}

internal fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "svg" -> "image/svg+xml"
    "avif" -> "image/avif"
    "bmp" -> "image/bmp"
    else -> "application/octet-stream"
}
