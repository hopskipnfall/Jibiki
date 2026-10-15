package dev.jibiki.yomitan

import dev.jibiki.model.Glossary
import dev.jibiki.model.PitchAccent
import dev.jibiki.model.ScNode
import dev.jibiki.model.TermMetaData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Turns stored glossary / meta JSON into model types for rendering. */
object GlossaryParser {
    private val json = Json { isLenient = true }

    fun parse(glossaryJson: String): List<Glossary> {
        val root = runCatching { json.parseToJsonElement(glossaryJson) }.getOrNull() as? JsonArray
            ?: return listOf(Glossary.Text(glossaryJson))
        return root.map(::entry)
    }

    private fun entry(e: JsonElement): Glossary = when (e) {
        is JsonPrimitive -> Glossary.Text(e.content)
        is JsonArray -> {
            val uninflected = e.getOrNull(0)?.primitiveContent()
            val rules = (e.getOrNull(1) as? JsonArray)?.mapNotNull { it.primitiveContent() }
            if (uninflected != null && rules != null) Glossary.Deinflection(uninflected, rules) else Glossary.Text(e.toString())
        }
        is JsonObject -> when (e.string("type")) {
            "text" -> Glossary.Text(e.string("text") ?: "")
            "image" -> image(e)?.let(Glossary::Image) ?: Glossary.Text("")
            "structured-content" -> Glossary.Structured(content(e["content"]))
            else -> Glossary.Text(e.toString())
        }
        else -> Glossary.Text(e.toString())
    }

    /** Structured content: a string, an array of content, or an element object. */
    fun content(e: JsonElement?): ScNode = when (e) {
        null, is JsonNull -> ScNode.Text("")
        is JsonPrimitive -> ScNode.Text(e.content)
        is JsonArray -> ScNode.Element("span", e.map(::content))
        is JsonObject -> element(e)
    }

    private fun element(o: JsonObject): ScNode {
        val tag = o.string("tag") ?: return ScNode.Text("")
        if (tag == "img") return image(o) ?: ScNode.Text("")
        val children = when (val c = o["content"]) {
            null, is JsonNull -> emptyList()
            is JsonArray -> c.map(::content)
            else -> listOf(content(c))
        }
        val attrs = buildMap {
            for (key in ATTRIBUTE_KEYS) o[key]?.primitiveContent()?.let { put(key, it) }
        }
        return ScNode.Element(tag, children, o.stringMap("style"), o.stringMap("data"), attrs)
    }

    private fun image(o: JsonObject): ScNode.Image? {
        val path = o.string("path") ?: return null
        return ScNode.Image(
            path = path,
            width = o.number("width"),
            height = o.number("height"),
            title = o.string("title"),
            alt = o.string("alt") ?: o.string("description"),
            verticalAlign = o.string("verticalAlign"),
            sizeUnits = o.string("sizeUnits"),
            appearance = o.string("appearance"),
            collapsed = (o["collapsed"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    /** Parses `term_meta` data for `freq` and `pitch`; returns null for other modes. */
    fun parseMeta(dataJson: String, mode: String): TermMetaData? {
        val data = runCatching { json.parseToJsonElement(dataJson) }.getOrNull() ?: return null
        return when (mode) {
            "freq" -> frequency(data)
            "pitch" -> pitch(data)
            else -> null
        }
    }

    fun frequency(data: JsonElement): TermMetaData.Frequency? {
        if (data is JsonObject && "frequency" in data) {
            val inner = frequency(data.getValue("frequency")) ?: return null
            return inner.copy(reading = data.string("reading"))
        }
        return when (data) {
            is JsonPrimitive -> {
                val n = data.doubleOrNull
                TermMetaData.Frequency(n?.toInt() ?: leadingInt(data.content), data.content, null)
            }
            is JsonObject -> {
                val value = data.number("value")?.toInt()
                val display = data.string("displayValue") ?: value?.toString() ?: return null
                TermMetaData.Frequency(value ?: leadingInt(display), display, null)
            }
            else -> null
        }
    }

    private fun pitch(data: JsonElement): TermMetaData.Pitch? {
        val o = data as? JsonObject ?: return null
        val reading = o.string("reading") ?: return null
        val accents = (o["pitches"] as? JsonArray)?.mapNotNull { p ->
            val po = p as? JsonObject ?: return@mapNotNull null
            val pos = po["position"] as? JsonPrimitive ?: return@mapNotNull null
            val downstep = pos.intOrNull ?: downstepFromPattern(pos.content) ?: return@mapNotNull null
            PitchAccent(downstep, intList(po["nasal"]), intList(po["devoice"]), (po["tags"] as? JsonArray)?.mapNotNull { it.primitiveContent() }.orEmpty())
        }.orEmpty()
        return TermMetaData.Pitch(reading, accents)
    }

    /** "LHHL" → 3 (drop after the third mora); "LHHH" → 0 (heiban); "HLL" → 1. */
    private fun downstepFromPattern(pattern: String): Int? {
        if (pattern.isEmpty() || pattern.any { it != 'H' && it != 'L' }) return null
        for (i in 0 until pattern.length - 1) if (pattern[i] == 'H' && pattern[i + 1] == 'L') return i + 1
        return 0
    }

    private fun intList(e: JsonElement?): List<Int> = when (e) {
        is JsonPrimitive -> listOfNotNull(e.intOrNull)
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
        else -> emptyList()
    }

    private fun leadingInt(s: String): Int? = Regex("""\d+""").find(s)?.value?.toIntOrNull()

    private fun JsonObject.string(key: String): String? = this[key]?.primitiveContent()
    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.stringMap(key: String): Map<String, String> =
        (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()

    private val ATTRIBUTE_KEYS = listOf("href", "lang", "title", "colSpan", "rowSpan", "open")
}
