package dev.jibiki.domain

import dev.jibiki.model.Profile
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryServiceTest {
    @Test fun reorderAndMembership() = runTest {
        val db = testDb()
        val importer = ImportService(db) { 0L }
        importer.import(TestDictionaries.english("A")).last()
        importer.import(TestDictionaries.english("B")).last()
        val library = LibraryService(db)
        val (a, b) = library.entries(Profile.ENGLISH).map { it.dict.id }
        library.move(Profile.ENGLISH, b, up = true)
        assertEquals(listOf(b, a), library.entries(Profile.ENGLISH).map { it.dict.id })
        library.setMember(Profile.JAPANESE, a, true)
        assertEquals(listOf(a), library.entries(Profile.JAPANESE).map { it.dict.id })
        library.delete(a)
        assertEquals(listOf(b), library.dictionaries().map { it.id })
        assertEquals(3, library.version.value)
    }
}
