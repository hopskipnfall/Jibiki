package dev.jibiki.japanese

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KanaTest {
    @Test fun katakanaToHiragana() {
        assertEquals("たべる", Kana.toHiragana("タベル"))
        assertEquals("ゔ", Kana.toHiragana("ヴ"))
        assertEquals("らーめん", Kana.toHiragana("ラーメン"))
        assertEquals("食べる", Kana.toHiragana("食べる"))
    }

    @Test fun hiraganaToKatakana() = assertEquals("タベル", Kana.toKatakana("たべる"))

    @Test fun kanjiDetection() {
        assertTrue(Kana.isKanji('食'.code))
        assertTrue(Kana.isKanji(0x3400))
        assertTrue(Kana.isKanji(0x20B9F))
        assertFalse(Kana.isKanji('々'.code))
        assertFalse(Kana.isKanji('あ'.code))
        assertTrue(Kana.hasKanji("食べる"))
        assertFalse(Kana.hasKanji("たべる"))
    }

    @Test fun distinctKanjiInOrder() {
        assertEquals(listOf("食", "物"), Kana.distinctKanji("食べ物を食べる"))
        assertEquals(listOf("𠮟"), Kana.distinctKanji("𠮟る"))
    }

    @Test fun kanaDetection() {
        assertTrue(Kana.isAllKana("たべる"))
        assertTrue(Kana.isAllKana("ラーメン"))
        assertFalse(Kana.isAllKana("食べる"))
    }
}
