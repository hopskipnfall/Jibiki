package dev.jibiki.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.async.open
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The single SQLite connection used by the app.
 *
 * All access is serialized through a mutex: on the web every statement is a round trip to the
 * worker, and on Android one writer connection keeps import and search simple.
 */
/**
 * [dispatcher] runs every statement; use an IO dispatcher where the driver blocks (Android/JVM).
 */
class Database private constructor(private val conn: SQLiteConnection, private val dispatcher: CoroutineDispatcher) {
    private val mutex = Mutex()
    private val sql = Sql(conn)

    suspend fun <T> read(block: suspend Sql.() -> T): T = withContext(dispatcher) { mutex.withLock { sql.block() } }

    /**
     * Runs [block] in a transaction. BEGIN/COMMIT/ROLLBACK are non-cancellable: on the web each is
     * a worker round trip, and abandoning one would leave the connection inside a transaction.
     */
    suspend fun <T> write(block: suspend Sql.() -> T): T = withContext(dispatcher) {
        mutex.withLock {
            withContext(NonCancellable) { sql.exec("BEGIN IMMEDIATE") }
            try {
                val result = sql.block()
                withContext(NonCancellable) { sql.exec("COMMIT") }
                result
            } catch (t: Throwable) {
                withContext(NonCancellable) { runCatching { sql.exec("ROLLBACK") } }
                throw t
            }
        }
    }

    fun close() {
        sql.closeStatements()
        conn.close()
    }

    companion object {
        suspend fun open(driver: SQLiteDriver, fileName: String, dispatcher: CoroutineDispatcher = Dispatchers.Default): Database {
            val conn = withContext(dispatcher) { driver.open(fileName) }
            val db = Database(conn, dispatcher)
            withContext(dispatcher) {
                db.mutex.withLock {
                    db.sql.exec("PRAGMA foreign_keys = ON")
                    Schema.migrate(db.sql)
                }
            }
            return db
        }
    }
}
