package dev.jibiki.db

import androidx.sqlite.SQLiteStatement
import dev.jibiki.model.TermRow
import dev.jibiki.yomitan.RawTerm

private const val TERM_COLUMNS = "id, dictId, expression, reading, defTags, rules, score, glossaryJson, sequence, termTags"

private fun SQLiteStatement.toTerm() = TermRow(
    id = getLong(0),
    dictId = getLong(1),
    expression = getText(2),
    reading = getText(3),
    definitionTags = getText(4).splitSpaces(),
    rules = getText(5).splitSpaces().toSet(),
    score = getLong(6).toInt(),
    glossaryJson = getText(7),
    sequence = getLong(8),
    termTags = getText(9).splitSpaces(),
)

internal fun String.splitSpaces(): List<String> = split(' ').filter { it.isNotEmpty() }

private fun placeholders(n: Int) = List(n) { "?" }.joinToString(",")

/**
 * Inserts terms and their kanji index rows. [readingKey] and [kanjiOf] are supplied by the caller
 * so the DB layer stays free of Japanese text rules.
 */
suspend fun Sql.insertTerms(
    dictId: Long,
    terms: List<RawTerm>,
    readingKey: (RawTerm) -> String,
    kanjiOf: (String) -> List<String>,
) {
    if (terms.isEmpty()) return
    val firstId = long("SELECT coalesce(max(id), 0) + 1 FROM term")
    val termRows = ArrayList<List<Any?>>(terms.size)
    val kanjiRows = ArrayList<List<Any?>>()
    terms.forEachIndexed { i, t ->
        val id = firstId + i
        termRows += listOf(
            id, dictId, t.expression, t.reading, readingKey(t), t.definitionTags.joinToString(" "),
            t.rules.joinToString(" "), t.score, t.sequence, t.termTags.joinToString(" "), t.glossaryJson,
        )
        for (k in kanjiOf(t.expression)) kanjiRows += listOf(k, id, dictId)
    }
    insertMany(
        "term",
        listOf("id", "dictId", "expression", "reading", "readingKey", "defTags", "rules", "score", "sequence", "termTags", "glossaryJson"),
        termRows,
    )
    insertMany("term_kanji", listOf("kanji", "termId", "dictId"), kanjiRows)
}

/** Exact match on expression or (hiragana) reading key. */
suspend fun Sql.termsByKeys(keys: Collection<String>, dictIds: Collection<Long>): List<TermRow> {
    if (keys.isEmpty() || dictIds.isEmpty()) return emptyList()
    val k = placeholders(keys.size)
    val d = placeholders(dictIds.size)
    return queryList(
        "SELECT $TERM_COLUMNS FROM term WHERE (expression IN ($k) OR readingKey IN ($k)) AND +dictId IN ($d)",
        keys.toList() + keys.toList() + dictIds.toList(),
    ) { it.toTerm() }
}

/** Prefix match using index range scans on expression and reading key. */
suspend fun Sql.termsByPrefix(prefix: String, dictIds: Collection<Long>, limit: Int): List<TermRow> {
    if (prefix.isEmpty() || dictIds.isEmpty()) return emptyList()
    val d = placeholders(dictIds.size)
    val upper = prefix + "\uFFFF"
    // Each side is an index range scan that stops after `limit` rows (in index order).
    val byExpression = queryList(
        "SELECT $TERM_COLUMNS FROM term WHERE expression >= ? AND expression < ? AND +dictId IN ($d) LIMIT ?",
        listOf<Any?>(prefix, upper) + dictIds.toList() + limit,
    ) { it.toTerm() }
    val byReading = queryList(
        "SELECT $TERM_COLUMNS FROM term WHERE readingKey >= ? AND readingKey < ? AND +dictId IN ($d) LIMIT ?",
        listOf<Any?>(prefix, upper) + dictIds.toList() + limit,
    ) { it.toTerm() }
    return (byExpression + byReading).distinctBy { it.id }
}

data class TermKey(val id: Long, val expression: String, val reading: String)

/** Enough of a term to rank it without loading its glossary. */
data class TermRef(val dictId: Long, val expression: String, val reading: String, val score: Int)

/**
 * Lightweight prefix candidates (index range scans, up to [limit] rows per column), so callers
 * can rank many candidates by frequency before loading full rows for one page.
 */
suspend fun Sql.termRefsByPrefix(prefix: String, dictIds: Collection<Long>, limit: Int): List<TermRef> {
    if (prefix.isEmpty() || dictIds.isEmpty()) return emptyList()
    val d = placeholders(dictIds.size)
    val upper = prefix + "\uFFFF"
    return listOf("expression", "readingKey").flatMap { column ->
        queryList(
            "SELECT dictId, expression, reading, score FROM term WHERE $column >= ? AND $column < ? AND +dictId IN ($d) LIMIT ?",
            listOf<Any?>(prefix, upper) + dictIds.toList() + limit,
        ) { TermRef(it.getLong(0), it.getText(1), it.getText(2), it.getLong(3).toInt()) }
    }
}

/** Lightweight keys of every term containing [kanji]; callers rank and then page via [termsByIds]. */
suspend fun Sql.termKeysContainingKanji(kanji: String, dictIds: Collection<Long>): List<TermKey> {
    if (dictIds.isEmpty()) return emptyList()
    val d = placeholders(dictIds.size)
    return queryList(
        "SELECT t.id, t.expression, t.reading FROM term_kanji k JOIN term t ON t.id = k.termId " +
            "WHERE k.kanji = ? AND +k.dictId IN ($d)",
        listOf<Any?>(kanji) + dictIds.toList(),
    ) { TermKey(it.getLong(0), it.getText(1), it.getText(2)) }
}

suspend fun Sql.termsByIds(ids: Collection<Long>): List<TermRow> {
    if (ids.isEmpty()) return emptyList()
    return queryList("SELECT $TERM_COLUMNS FROM term WHERE id IN (${placeholders(ids.size)})", ids.toList()) { it.toTerm() }
}

/** All terms with these exact (expression, reading) pairs, for assembling complete word groups. */
suspend fun Sql.termsByExpressions(expressions: Collection<String>, dictIds: Collection<Long>): List<TermRow> {
    if (expressions.isEmpty() || dictIds.isEmpty()) return emptyList()
    return queryList(
        "SELECT $TERM_COLUMNS FROM term WHERE expression IN (${placeholders(expressions.size)}) AND +dictId IN (${placeholders(dictIds.size)})",
        expressions.toList() + dictIds.toList(),
    ) { it.toTerm() }
}
