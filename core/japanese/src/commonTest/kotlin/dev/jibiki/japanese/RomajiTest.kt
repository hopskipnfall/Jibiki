package dev.jibiki.japanese

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RomajiTest {
    private fun kana(s: String) = Romaji.finalize(s)

    @Test fun basicWords() {
        assertEquals("たべる", kana("taberu"))
        assertEquals("しんぶん", kana("shinbun"))
        assertEquals("しんぶん", kana("sinbun"))
        assertEquals("しんぶん", kana("shinnbun"))
        assertEquals("つくえ", kana("tsukue"))
        assertEquals("つくえ", kana("tukue"))
        assertEquals("ふじさん", kana("fujisan"))
        assertEquals("ふじさん", kana("huzisan"))
    }

    @Test fun doubledConsonantsAndYoon() {
        assertEquals("きって", kana("kitte"))
        assertEquals("ちょっと", kana("chotto"))
        assertEquals("まっちゃ", kana("matcha"))
        assertEquals("じゃ", kana("ja"))
        assertEquals("じゃ", kana("zya"))
        assertEquals("じゃ", kana("jya"))
        assertEquals("きょう", kana("kyou"))
    }

    @Test fun syllabicN() {
        assertEquals("こんや", kana("kon'ya"))
        assertEquals("おんな", kana("onna"))
        assertEquals("こんにゃ", kana("konnya"))
        assertEquals("かん", kana("kan"))
        assertEquals("ほん", kana("honn"))
    }

    @Test fun smallKanaAndLongVowelMark() {
        assertEquals("っ", kana("xtu"))
        assertEquals("ぁ", kana("la"))
        assertEquals("らーめん", kana("ra-men"))
        assertEquals("ゔぁ", kana("va"))
        assertEquals("てぃ", kana("thi"))
    }

    @Test fun uppercaseAndMixedInput() {
        assertEquals("たべる", kana("TABERU"))
        assertEquals("食べる", kana("食べる"))
    }

    @Test fun pendingWhileTyping() {
        assertEquals(RomajiResult("たべ", "r"), Romaji.toHiragana("taber"))
        assertEquals(RomajiResult("", "n"), Romaji.toHiragana("n"))
        assertEquals(RomajiResult("か", "n"), Romaji.toHiragana("kan"))
        assertEquals(RomajiResult("き", "t"), Romaji.toHiragana("kit"))
        assertEquals(RomajiResult("", "ky"), Romaji.toHiragana("ky"))
    }

    @Test fun latinDetection() {
        assertTrue(Romaji.containsLatin("taberu"))
        assertTrue(Romaji.containsLatin("食べru"))
        assertFalse(Romaji.containsLatin("食べる"))
    }
}
