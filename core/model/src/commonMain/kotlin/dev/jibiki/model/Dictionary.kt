package dev.jibiki.model

enum class DictKind { TERMS, KANJI, TERM_FREQ, TERM_PITCH, TERM_IPA, KANJI_FREQ }

enum class DictStatus { IMPORTING, READY, FAILED }

data class DictionaryInfo(
    val id: Long,
    val title: String,
    val revision: String,
    val author: String?,
    val url: String?,
    val description: String?,
    val attribution: String?,
    val sourceLang: String?,
    val targetLang: String?,
    /** "rank-based" (lower = more common, the default) or "occurrence-based". */
    val frequencyMode: String?,
    val kinds: Set<DictKind>,
    val termCount: Long,
    val kanjiCount: Long,
    val metaCount: Long,
    val mediaBytes: Long,
    val importedAt: Long,
    val status: DictStatus,
)

data class Tag(
    val name: String,
    val category: String,
    val order: Int,
    val notes: String,
    val score: Int,
)
