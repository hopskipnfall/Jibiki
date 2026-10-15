package dev.jibiki.domain

import dev.jibiki.model.Glossary
import dev.jibiki.model.KanjiRow
import dev.jibiki.model.PitchAccent
import dev.jibiki.model.Tag
import dev.jibiki.radicals.Radical
import dev.jibiki.yomitan.GlossaryParser

/** PARTIAL: the longest leading part of the query that matched (for pasted phrases). */
enum class MatchType { EXACT, DEINFLECTED, PARTIAL, PREFIX, CONTAINS }

data class FrequencyBadge(val dictTitle: String, val display: String, val value: Int?)

data class PitchInfo(val dictTitle: String, val reading: String, val accents: List<PitchAccent>)

data class DefinitionBlock(
    val dictId: Long,
    val dictTitle: String,
    val tags: List<Tag>,
    val glossaryJson: String,
    val termTags: List<Tag>,
) {
    /** Parsed on first access, so only rendered cards pay for (often large) structured content. */
    val glossary: List<Glossary> by lazy { GlossaryParser.parse(glossaryJson) }
}

data class WordGroup(
    val expression: String,
    val reading: String,
    val matchType: MatchType,
    val reasons: List<String>,
    val frequencies: List<FrequencyBadge>,
    val pitches: List<PitchInfo>,
    val definitions: List<DefinitionBlock>,
) {
    val key: String get() = "$expression\u0000$reading"
}

data class KanjiSummary(val character: String, val meaning: String?, val components: List<Radical>)

data class SearchResults(
    val query: String,
    /** The kana actually searched when the input was romaji; null otherwise. */
    val kanaQuery: String?,
    val words: List<WordGroup>,
    val kanji: List<KanjiSummary>,
    val containing: List<WordGroup>,
    val hasMoreWords: Boolean,
) {
    val isEmpty: Boolean get() = words.isEmpty() && kanji.isEmpty() && containing.isEmpty()

    companion object {
        val EMPTY = SearchResults("", null, emptyList(), emptyList(), emptyList(), false)
    }
}

data class KanjiDetail(
    val character: String,
    val entries: List<Pair<String, KanjiRow>>,
    val frequencies: List<FrequencyBadge>,
    val components: List<Radical>,
)
