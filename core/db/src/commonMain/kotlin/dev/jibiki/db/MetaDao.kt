package dev.jibiki.db

import dev.jibiki.model.KanjiRow
import dev.jibiki.model.Tag
import dev.jibiki.model.TermMetaData
import dev.jibiki.yomitan.GlossaryParser
import dev.jibiki.yomitan.RawKanji
import dev.jibiki.yomitan.RawKanjiMeta
import dev.jibiki.yomitan.RawTermMeta
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class TermMetaRow(val dictId: Long, val expression: String, val reading: String?, val mode: String, val dataJson: String)

data class KanjiMetaRow(val dictId: Long, val character: String, val mode: String, val dataJson: String)

private fun placeholders(n: Int) = List(n) { "?" }.joinToString(",")

/** Frequency rows also store their numeric value so ranking needn't parse JSON. */
suspend fun Sql.insertTermMeta(dictId: Long, rows: List<RawTermMeta>) =
    insertMany(
        "term_meta",
        listOf("dictId", "expression", "reading", "mode", "dataJson", "freqValue"),
        rows.map {
            val value = if (it.mode == "freq") (GlossaryParser.parseMeta(it.dataJson, "freq") as? TermMetaData.Frequency)?.value else null
            listOf(dictId, it.expression, it.reading, it.mode, it.dataJson, value)
        },
    )

data class FrequencyRow(val dictId: Long, val expression: String, val reading: String?, val value: Long)

suspend fun Sql.termFrequencies(expressions: Collection<String>, dictIds: Collection<Long>): List<FrequencyRow> {
    if (expressions.isEmpty() || dictIds.isEmpty()) return emptyList()
    return queryList(
        "SELECT dictId, expression, reading, freqValue FROM term_meta WHERE mode = 'freq' AND freqValue IS NOT NULL " +
            "AND expression IN (${placeholders(expressions.size)}) AND +dictId IN (${placeholders(dictIds.size)})",
        expressions.toList() + dictIds.toList(),
    ) { FrequencyRow(it.getLong(0), it.getText(1), it.textOrNull(2), it.getLong(3)) }
}

suspend fun Sql.insertKanji(dictId: Long, rows: List<RawKanji>) =
    insertMany(
        "kanji",
        listOf("dictId", "character", "onyomi", "kunyomi", "tags", "meaningsJson", "statsJson"),
        rows.map {
            listOf(
                dictId, it.character, it.onyomi.joinToString(" "), it.kunyomi.joinToString(" "), it.tags.joinToString(" "),
                JsonArray(it.meanings.map(::JsonPrimitive)).toString(),
                JsonObject(it.stats.mapValues { (_, v) -> JsonPrimitive(v) }).toString(),
            )
        },
    )

suspend fun Sql.insertKanjiMeta(dictId: Long, rows: List<RawKanjiMeta>) =
    insertMany(
        "kanji_meta",
        listOf("dictId", "character", "mode", "dataJson"),
        rows.map { listOf(dictId, it.character, it.mode, it.dataJson) },
    )

suspend fun Sql.insertTags(dictId: Long, rows: List<Tag>) =
    insertMany(
        "tag",
        listOf("dictId", "name", "category", "sortOrder", "notes", "score"),
        rows.map { listOf(dictId, it.name, it.category, it.order, it.notes, it.score) },
    )

suspend fun Sql.termMetaFor(expressions: Collection<String>, dictIds: Collection<Long>): List<TermMetaRow> {
    if (expressions.isEmpty() || dictIds.isEmpty()) return emptyList()
    return queryList(
        "SELECT dictId, expression, reading, mode, dataJson FROM term_meta " +
            "WHERE expression IN (${placeholders(expressions.size)}) AND +dictId IN (${placeholders(dictIds.size)})",
        expressions.toList() + dictIds.toList(),
    ) { TermMetaRow(it.getLong(0), it.getText(1), it.textOrNull(2), it.getText(3), it.getText(4)) }
}

suspend fun Sql.kanjiMetaFor(chars: Collection<String>, dictIds: Collection<Long>): List<KanjiMetaRow> {
    if (chars.isEmpty() || dictIds.isEmpty()) return emptyList()
    return queryList(
        "SELECT dictId, character, mode, dataJson FROM kanji_meta " +
            "WHERE character IN (${placeholders(chars.size)}) AND +dictId IN (${placeholders(dictIds.size)})",
        chars.toList() + dictIds.toList(),
    ) { KanjiMetaRow(it.getLong(0), it.getText(1), it.getText(2), it.getText(3)) }
}

private val json = Json { isLenient = true }

suspend fun Sql.kanjiFor(chars: Collection<String>, dictIds: Collection<Long>): List<KanjiRow> {
    if (chars.isEmpty() || dictIds.isEmpty()) return emptyList()
    return queryList(
        "SELECT dictId, character, onyomi, kunyomi, tags, meaningsJson, statsJson FROM kanji " +
            "WHERE character IN (${placeholders(chars.size)}) AND +dictId IN (${placeholders(dictIds.size)})",
        chars.toList() + dictIds.toList(),
    ) {
        KanjiRow(
            dictId = it.getLong(0),
            character = it.getText(1),
            onyomi = it.getText(2).splitSpaces(),
            kunyomi = it.getText(3).splitSpaces(),
            tags = it.getText(4).splitSpaces(),
            meanings = (json.parseToJsonElement(it.getText(5)) as JsonArray).mapNotNull { m -> (m as? JsonPrimitive)?.contentOrNull },
            stats = (json.parseToJsonElement(it.getText(6)) as JsonObject).mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() },
        )
    }
}

/** Stroke counts from any installed kanji dictionary's `stats.strokes`. */
suspend fun Sql.strokeCounts(chars: Collection<String>): Map<String, Int> {
    if (chars.isEmpty()) return emptyMap()
    return queryList(
        "SELECT character, json_extract(statsJson, '$.strokes') FROM kanji WHERE character IN (${placeholders(chars.size)}) " +
            "AND json_extract(statsJson, '$.strokes') IS NOT NULL",
        chars.toList(),
    ) { it.getText(0) to it.getText(1).toIntOrNull() }
        .mapNotNull { (c, n) -> n?.let { c to it } }
        .toMap()
}

suspend fun Sql.tagsFor(dictIds: Collection<Long>, names: Collection<String>): Map<Pair<Long, String>, Tag> {
    if (dictIds.isEmpty() || names.isEmpty()) return emptyMap()
    return queryList(
        "SELECT dictId, name, category, sortOrder, notes, score FROM tag " +
            "WHERE dictId IN (${placeholders(dictIds.size)}) AND name IN (${placeholders(names.size)})",
        dictIds.toList() + names.toList(),
    ) { (it.getLong(0) to it.getText(1)) to Tag(it.getText(1), it.getText(2), it.getLong(3).toInt(), it.getText(4), it.getLong(5).toInt()) }
        .toMap()
}

suspend fun Sql.insertMedia(dictId: Long, path: String, mime: String, bytes: ByteArray) =
    exec("INSERT OR REPLACE INTO media(dictId, path, mime, bytes) VALUES (?,?,?,?)", dictId, path, mime, bytes)

suspend fun Sql.media(dictId: Long, path: String): ByteArray? =
    query("SELECT bytes FROM media WHERE dictId = ? AND path = ?", dictId, path) { it.getBlob(0) }.firstOrNull()
