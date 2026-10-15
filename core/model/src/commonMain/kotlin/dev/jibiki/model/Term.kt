package dev.jibiki.model

data class TermRow(
    val id: Long,
    val dictId: Long,
    val expression: String,
    val reading: String,
    val definitionTags: List<String>,
    val rules: Set<String>,
    val score: Int,
    val glossaryJson: String,
    val sequence: Long,
    val termTags: List<String>,
)
