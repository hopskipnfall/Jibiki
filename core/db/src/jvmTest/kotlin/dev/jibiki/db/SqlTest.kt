package dev.jibiki.db

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlTest {
    @Test fun schemaSeedsProfiles() = runTest {
        val db = testDb()
        val names = db.read { query("SELECT name FROM profile ORDER BY sortOrder") { it.getText(0) } }
        assertEquals(listOf("English", "日本語"), names)
    }

    @Test fun insertManyHandlesChunksAndTail() = runTest {
        val db = testDb()
        val rows = List(1234) { listOf(it.toLong(), "k$it", "v$it") }
        db.write { insertMany("settings", listOf("rowid", "key", "value"), rows) }
        assertEquals(1234L, db.read { long("SELECT count(*) FROM settings") })
        assertEquals("v1233", db.read { query("SELECT value FROM settings WHERE key = ?", "k1233") { it.getText(0) }.single() })
    }

    @Test fun failedWriteRollsBack() = runTest {
        val db = testDb()
        assertFailsWith<IllegalStateException> {
            db.write {
                exec("INSERT INTO settings(key, value) VALUES ('a', 'b')")
                error("boom")
            }
        }
        assertEquals(0L, db.read { long("SELECT count(*) FROM settings") })
    }
}
