package dev.jibiki.japanese

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeinflectorTest {
    private val deinflector = Deinflector()

    private fun find(source: String, target: String, termRules: Set<String>): Deinflection {
        val hit = deinflector.deinflect(source).firstOrNull { it.text == target && deinflector.accepts(it, termRules) }
        return assertNotNull(hit, "$source should deinflect to $target [$termRules]")
    }

    @Test fun negativePast() {
        val d = find("食べなかった", "食べる", setOf("v1"))
        assertEquals(listOf("negative", "-た"), d.reasons)
    }

    @Test fun godanPast() {
        find("行った", "行く", setOf("v5"))
        find("読んでいる", "読む", setOf("v5"))
    }

    @Test fun adjective() = find("高くない", "高い", setOf("adj-i")).let { }

    @Test fun suruCausativePassive() = find("勉強させられた", "勉強する", setOf("vs")).let { }

    @Test fun kuru() = find("来ない", "来る", setOf("vk")).let { }

    @Test fun originalTextIsIncludedAndMatchesAnything() {
        val first = deinflector.deinflect("食べる").first()
        assertEquals("食べる", first.text)
        assertEquals(emptyList(), first.reasons)
        assertTrue(deinflector.accepts(first, setOf("n")))
    }

    @Test fun wrongPartOfSpeechRejected() {
        val candidates = deinflector.deinflect("食べなかった").filter { it.text == "食べる" }
        assertTrue(candidates.isNotEmpty())
        assertFalse(candidates.any { deinflector.accepts(it, setOf("n")) })
    }
}
