package dev.jibiki.model

data class KanjiRow(
    val dictId: Long,
    val character: String,
    val onyomi: List<String>,
    val kunyomi: List<String>,
    val tags: List<String>,
    val meanings: List<String>,
    val stats: Map<String, String>,
)
