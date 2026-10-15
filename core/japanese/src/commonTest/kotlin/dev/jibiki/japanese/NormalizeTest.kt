package dev.jibiki.japanese

import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeTest {
    @Test fun fullWidthAsciiAndHalfWidthKana() {
        assertEquals("ABC アイウ", normalizeQuery("  ＡＢＣ　ｱｲｳ "))
    }

    @Test fun halfWidthVoicedMarksCombine() {
        assertEquals("ガギ", normalizeQuery("ｶﾞｷﾞ"))
        assertEquals("パ", normalizeQuery("ﾊﾟ"))
        assertEquals("ヴ", normalizeQuery("ｳﾞ"))
    }

    @Test fun collapsesInnerWhitespace() = assertEquals("a b", normalizeQuery("a   b"))
}
