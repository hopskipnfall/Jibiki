package dev.jibiki.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

suspend fun testDb(): Database = Database.open(BundledSQLiteDriver(), ":memory:")
