package dev.jibiki.model

sealed interface Glossary {
    data class Text(val text: String) : Glossary
    data class Image(val node: ScNode.Image) : Glossary
    data class Structured(val content: ScNode) : Glossary
    data class Deinflection(val uninflected: String, val rules: List<String>) : Glossary
}

/** Yomitan structured-content tree. */
sealed interface ScNode {
    data class Text(val text: String) : ScNode

    data class Element(
        val tag: String,
        val children: List<ScNode>,
        val style: Map<String, String> = emptyMap(),
        val data: Map<String, String> = emptyMap(),
        val attrs: Map<String, String> = emptyMap(),
    ) : ScNode

    data class Image(
        val path: String,
        val width: Double? = null,
        val height: Double? = null,
        val title: String? = null,
        val alt: String? = null,
        val verticalAlign: String? = null,
        val sizeUnits: String? = null,
        val appearance: String? = null,
        val collapsed: Boolean = false,
    ) : ScNode
}
