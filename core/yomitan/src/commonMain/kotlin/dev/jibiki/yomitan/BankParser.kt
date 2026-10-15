package dev.jibiki.yomitan

import dev.jibiki.model.Tag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

data class Parsed<T>(val rows: List<T>, val skipped: Int)

data class RawTerm(
    val expression: String,
    val reading: String,
    val definitionTags: List<String>,
    val rules: Set<String>,
    val score: Int,
    val glossaryJson: String,
    val sequence: Long,
    val termTags: List<String>,
)

data class RawTermMeta(val expression: String, val mode: String, val reading: String?, val dataJson: String)

data class RawKanji(
    val character: String,
    val onyomi: List<String>,
    val kunyomi: List<String>,
    val tags: List<String>,
    val meanings: List<String>,
    val stats: Map<String, String>,
)

data class RawKanjiMeta(val character: String, val mode: String, val dataJson: String)

/** Parses Yomitan v3 bank files. Malformed rows are counted in [Parsed.skipped], never thrown. */
object BankParser {
    fun terms(json: String): Parsed<RawTerm> = rows(json) { r ->
        RawTerm(
            expression = r.str(0)!!,
            reading = r.str(1) ?: "",
            definitionTags = splitTags(r.str(2)),
            rules = splitTags(r.str(3)).toSet(),
            score = r.num(4)?.toInt() ?: 0,
            glossaryJson = (r[5] as JsonArray).toString(),
            sequence = r.num(6)?.toLong() ?: 0L,
            termTags = splitTags(r.str(7)),
        )
    }

    fun termMeta(json: String): Parsed<RawTermMeta> = rows(json) { r ->
        val data = r[2]
        val reading = (data as? JsonObject)?.get("reading")?.let { (it as? JsonPrimitive)?.contentOrNull }
        RawTermMeta(r.str(0)!!, r.str(1)!!, reading, data.toString())
    }

    fun kanji(json: String): Parsed<RawKanji> = rows(json) { r ->
        RawKanji(
            character = r.str(0)!!,
            onyomi = splitTags(r.str(1)),
            kunyomi = splitTags(r.str(2)),
            tags = splitTags(r.str(3)),
            meanings = (r.getOrNull(4) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList(),
            stats = (r.getOrNull(5) as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.contentOrNull ?: v.toString() }
                ?: emptyMap(),
        )
    }

    fun kanjiMeta(json: String): Parsed<RawKanjiMeta> = rows(json) { r ->
        RawKanjiMeta(r.str(0)!!, r.str(1)!!, r[2].toString())
    }

    fun tags(json: String): Parsed<Tag> = rows(json) { r ->
        Tag(
            name = r.str(0)!!,
            category = r.str(1) ?: "",
            order = r.num(2)?.toInt() ?: 0,
            notes = r.str(3) ?: "",
            score = r.num(4)?.toInt() ?: 0,
        )
    }

    private val json = Json { isLenient = true }

    private inline fun <T> rows(text: String, map: (JsonArray) -> T): Parsed<T> {
        val root = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw YomitanFormatException("Bank file is not valid JSON: ${e.message}")
        }
        if (root !is JsonArray) throw YomitanFormatException("Bank file must be a JSON array")
        val out = ArrayList<T>(root.size)
        var skipped = 0
        for (row in root) {
            val parsed = if (row is JsonArray) runCatching { map(row) }.getOrNull() else null
            if (parsed == null) skipped++ else out += parsed
        }
        return Parsed(out, skipped)
    }

    private fun JsonArray.str(i: Int): String? {
        val e = getOrNull(i) ?: return null
        if (e is JsonNull) return null
        return (e as JsonPrimitive).let { if (it.isString) it.content else it.content }
    }

    private fun JsonArray.num(i: Int): Double? {
        val e = getOrNull(i) as? JsonPrimitive ?: return null
        return e.longOrNull?.toDouble() ?: e.doubleOrNull
    }

    private fun splitTags(s: String?): List<String> = s?.split(' ')?.filter { it.isNotEmpty() } ?: emptyList()
}

internal fun JsonElement.primitiveContent(): String? = (this as? JsonPrimitive)?.contentOrNull
