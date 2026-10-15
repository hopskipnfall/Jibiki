package dev.jibiki.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.async.prepare
import androidx.sqlite.async.step

/** Thin query helper over a connection, with a small prepared-statement cache. */
class Sql internal constructor(private val conn: SQLiteConnection) {
    // Insertion-ordered; re-inserting on hit keeps it least-recently-used first.
    private val cache = LinkedHashMap<String, SQLiteStatement>()

    private suspend fun statement(sql: String): SQLiteStatement {
        cache.remove(sql)?.let {
            cache[sql] = it
            return it
        }
        val stmt = conn.prepare(sql)
        cache[sql] = stmt
        if (cache.size > MAX_CACHED) {
            val eldest = cache.keys.first()
            cache.remove(eldest)?.close()
        }
        return stmt
    }

    internal fun closeStatements() {
        cache.values.forEach { it.close() }
        cache.clear()
    }

    suspend fun exec(sql: String, vararg args: Any?) {
        val stmt = statement(sql)
        try {
            bindAll(stmt, args.asList())
            while (stmt.step()) { /* drain */ }
        } finally {
            stmt.reset()
            stmt.clearBindings()
        }
    }

    suspend fun <T> query(sql: String, vararg args: Any?, map: (SQLiteStatement) -> T): List<T> =
        queryList(sql, args.asList(), map)

    suspend fun <T> queryList(sql: String, args: List<Any?>, map: (SQLiteStatement) -> T): List<T> {
        val stmt = statement(sql)
        try {
            bindAll(stmt, args)
            val out = ArrayList<T>()
            while (stmt.step()) out += map(stmt)
            return out
        } finally {
            stmt.reset()
            stmt.clearBindings()
        }
    }

    suspend fun long(sql: String, vararg args: Any?): Long =
        queryList(sql, args.asList()) { it.getLong(0) }.firstOrNull() ?: 0L

    suspend fun lastInsertId(): Long = long("SELECT last_insert_rowid()")

    /** Inserts rows with multi-row VALUES statements; one round trip per chunk on the web. */
    suspend fun insertMany(table: String, columns: List<String>, rows: List<List<Any?>>) {
        if (rows.isEmpty()) return
        val perRow = columns.size
        val chunk = minOf(MAX_ROWS_PER_INSERT, MAX_VARIABLES / perRow)
        val placeholders = columns.joinToString(",", "(", ")") { "?" }
        val head = "INSERT INTO $table(${columns.joinToString(",")}) VALUES "
        var i = 0
        while (i < rows.size) {
            val part = rows.subList(i, minOf(rows.size, i + chunk))
            val sql = head + List(part.size) { placeholders }.joinToString(",")
            val args = ArrayList<Any?>(part.size * perRow)
            part.forEach { row ->
                require(row.size == perRow) { "Row has ${row.size} values, expected $perRow" }
                args.addAll(row)
            }
            if (part.size == chunk) {
                execList(sql, args)
            } else {
                // Odd-sized tail: don't pollute the statement cache.
                val stmt = conn.prepare(sql)
                try {
                    bindAll(stmt, args)
                    while (stmt.step()) { }
                } finally {
                    stmt.close()
                }
            }
            i += part.size
        }
    }

    private suspend fun execList(sql: String, args: List<Any?>) {
        val stmt = statement(sql)
        try {
            bindAll(stmt, args)
            while (stmt.step()) { }
        } finally {
            stmt.reset()
            stmt.clearBindings()
        }
    }

    private fun bindAll(stmt: SQLiteStatement, args: List<Any?>) {
        args.forEachIndexed { index, value ->
            val i = index + 1
            when (value) {
                null -> stmt.bindNull(i)
                is String -> stmt.bindText(i, value)
                is Long -> stmt.bindLong(i, value)
                is Int -> stmt.bindLong(i, value.toLong())
                is Boolean -> stmt.bindLong(i, if (value) 1L else 0L)
                is Double -> stmt.bindDouble(i, value)
                is ByteArray -> stmt.bindBlob(i, value)
                else -> error("Unsupported bind type ${value::class}")
            }
        }
    }

    private companion object {
        const val MAX_CACHED = 64
        const val MAX_VARIABLES = 32_000
        const val MAX_ROWS_PER_INSERT = 500
    }
}

fun SQLiteStatement.textOrNull(index: Int): String? = if (isNull(index)) null else getText(index)
