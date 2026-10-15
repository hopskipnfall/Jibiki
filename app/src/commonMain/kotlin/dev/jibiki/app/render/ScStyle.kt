package dev.jibiki.app.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Maps the subset of CSS that Yomitan structured content uses onto Compose span styles. */
internal object ScStyle {
    fun span(style: Map<String, String>): SpanStyle {
        var s = SpanStyle()
        style["fontWeight"]?.let { w ->
            s = s.merge(SpanStyle(fontWeight = if (w == "bold" || (w.toIntOrNull() ?: 400) >= 600) FontWeight.Bold else FontWeight.Normal))
        }
        if (style["fontStyle"] == "italic") s = s.merge(SpanStyle(fontStyle = FontStyle.Italic))
        style["fontSize"]?.let { fontSize(it) }?.let { s = s.merge(SpanStyle(fontSize = it)) }
        style["color"]?.let(::color)?.let { s = s.merge(SpanStyle(color = it)) }
        style["backgroundColor"]?.let(::color)?.let { s = s.merge(SpanStyle(background = it)) }
        style["textDecorationLine"]?.let { d ->
            val decorations = buildList {
                if ("underline" in d) add(TextDecoration.Underline)
                if ("line-through" in d) add(TextDecoration.LineThrough)
            }
            if (decorations.isNotEmpty()) s = s.merge(SpanStyle(textDecoration = TextDecoration.combine(decorations)))
        }
        when (style["verticalAlign"]) {
            "super" -> s = s.merge(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em))
            "sub" -> s = s.merge(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em))
        }
        return s
    }

    fun fontSize(v: String): TextUnit? = when (v.trim()) {
        "xx-small" -> 0.6.em
        "x-small" -> 0.75.em
        "small", "smaller" -> 0.85.em
        "medium" -> 1.em
        "large", "larger" -> 1.15.em
        "x-large" -> 1.4.em
        "xx-large" -> 1.7.em
        else -> length(v)?.let { (n, unit) ->
            when (unit) {
                "em", "rem" -> n.em
                "%" -> (n / 100).em
                "px", "pt" -> n.sp
                else -> null
            }
        }
    }

    /** Converts a CSS length to em (1em ≈ 16px), for margins and image sizes. */
    fun toEm(v: String?): Double? = v?.let(::length)?.let { (n, unit) ->
        when (unit) {
            "em", "rem", "" -> n
            "px" -> n / 16
            "%" -> n / 100
            else -> null
        }
    }

    private fun length(v: String): Pair<Double, String>? {
        val m = Regex("""^(-?[\d.]+)\s*([a-z%]*)$""").find(v.trim()) ?: return null
        return (m.groupValues[1].toDoubleOrNull() ?: return null) to m.groupValues[2]
    }

    private val named = mapOf(
        "black" to 0xFF000000, "white" to 0xFFFFFFFF, "red" to 0xFFD32F2F, "green" to 0xFF2E7D32,
        "blue" to 0xFF1565C0, "gray" to 0xFF757575, "grey" to 0xFF757575, "orange" to 0xFFEF6C00,
        "purple" to 0xFF6A1B9A, "brown" to 0xFF5D4037, "navy" to 0xFF1A237E, "maroon" to 0xFF800000,
        "darkred" to 0xFF8B0000, "darkblue" to 0xFF00008B, "darkgreen" to 0xFF006400, "teal" to 0xFF00796B,
    )

    fun color(v: String): Color? {
        val s = v.trim().lowercase()
        named[s]?.let { return Color(it) }
        if (s.startsWith("#")) {
            val hex = s.drop(1)
            val full = when (hex.length) {
                3 -> hex.map { "$it$it" }.joinToString("")
                6, 8 -> hex
                else -> return null
            }
            val n = full.toLongOrNull(16) ?: return null
            return if (full.length == 6) Color(0xFF000000 or n) else Color(((n and 0xFF) shl 24) or (n shr 8))
        }
        Regex("""rgba?\(([^)]*)\)""").find(s)?.let { m ->
            val parts = m.groupValues[1].split(',').map { it.trim().toFloatOrNull() ?: return null }
            if (parts.size < 3) return null
            return Color(parts[0] / 255f, parts[1] / 255f, parts[2] / 255f, parts.getOrElse(3) { 1f })
        }
        return null
    }
}
