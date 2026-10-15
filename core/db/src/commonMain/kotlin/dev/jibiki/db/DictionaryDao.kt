package dev.jibiki.db

import androidx.sqlite.SQLiteStatement
import dev.jibiki.model.DictKind
import dev.jibiki.model.DictStatus
import dev.jibiki.model.DictionaryInfo
import dev.jibiki.yomitan.YomitanIndex

private const val DICT_COLUMNS =
    "id, title, revision, author, url, description, attribution, sourceLang, targetLang, frequencyMode, kinds, " +
        "termCount, kanjiCount, metaCount, mediaBytes, importedAt, status"

private fun SQLiteStatement.toDictionary() = DictionaryInfo(
    id = getLong(0),
    title = getText(1),
    revision = getText(2),
    author = textOrNull(3),
    url = textOrNull(4),
    description = textOrNull(5),
    attribution = textOrNull(6),
    sourceLang = textOrNull(7),
    targetLang = textOrNull(8),
    frequencyMode = textOrNull(9),
    kinds = getText(10).split(',').filter { it.isNotEmpty() }.mapNotNull { k -> DictKind.entries.firstOrNull { it.name == k } }.toSet(),
    termCount = getLong(11),
    kanjiCount = getLong(12),
    metaCount = getLong(13),
    mediaBytes = getLong(14),
    importedAt = getLong(15),
    status = DictStatus.valueOf(getText(16)),
)

suspend fun Sql.insertDictionary(index: YomitanIndex, importedAt: Long): Long {
    exec(
        "INSERT INTO dictionary(title, revision, author, url, description, attribution, sourceLang, targetLang, frequencyMode, importedAt, status) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        index.title, index.revision, index.author, index.url, index.description, index.attribution,
        index.sourceLanguage, index.targetLanguage, index.frequencyMode, importedAt, DictStatus.IMPORTING.name,
    )
    return lastInsertId()
}

suspend fun Sql.setDictionaryStatus(id: Long, status: DictStatus) =
    exec("UPDATE dictionary SET status = ? WHERE id = ?", status.name, id)

suspend fun Sql.updateDictionaryStats(id: Long, kinds: Set<DictKind>, termCount: Long, kanjiCount: Long, metaCount: Long, mediaBytes: Long) =
    exec(
        "UPDATE dictionary SET kinds = ?, termCount = ?, kanjiCount = ?, metaCount = ?, mediaBytes = ? WHERE id = ?",
        kinds.joinToString(",") { it.name }, termCount, kanjiCount, metaCount, mediaBytes, id,
    )

suspend fun Sql.allDictionaries(): List<DictionaryInfo> =
    query("SELECT $DICT_COLUMNS FROM dictionary ORDER BY id") { it.toDictionary() }

suspend fun Sql.dictionary(id: Long): DictionaryInfo? =
    query("SELECT $DICT_COLUMNS FROM dictionary WHERE id = ?", id) { it.toDictionary() }.firstOrNull()

private val DICT_TABLES = listOf("term_kanji", "term", "term_meta", "kanji", "kanji_meta", "tag", "media", "profile_dictionary")

/**
 * Deletes a dictionary in small transactions so the UI (and, on the web, other queries) stay
 * responsive. [onProgress] receives the number of rows deleted so far.
 */
suspend fun Database.deleteDictionary(id: Long, onProgress: (Long) -> Unit = {}) {
    write { exec("UPDATE dictionary SET status = ? WHERE id = ?", DictStatus.FAILED.name, id) }
    var total = 0L
    for (table in DICT_TABLES) {
        while (true) {
            val deleted = write {
                exec("DELETE FROM $table WHERE rowid IN (SELECT rowid FROM $table WHERE dictId = ? LIMIT $DELETE_CHUNK)", id)
                long("SELECT changes()")
            }
            total += deleted
            onProgress(total)
            if (deleted < DELETE_CHUNK) break
        }
    }
    write { exec("DELETE FROM dictionary WHERE id = ?", id) }
}

private const val DELETE_CHUNK = 5_000
