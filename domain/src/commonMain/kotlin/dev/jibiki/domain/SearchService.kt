package dev.jibiki.domain

import dev.jibiki.db.Database
import dev.jibiki.db.Sql
import dev.jibiki.db.TermMetaRow
import dev.jibiki.db.activeDictionaries
import dev.jibiki.db.allDictionaries
import dev.jibiki.db.kanjiFor
import dev.jibiki.db.kanjiMetaFor
import dev.jibiki.db.setting
import dev.jibiki.db.tagsFor
import dev.jibiki.db.termKeysContainingKanji
import dev.jibiki.db.termFrequencies
import dev.jibiki.db.termMetaFor
import dev.jibiki.db.termsByExpressions
import dev.jibiki.db.termsByKeys
import dev.jibiki.db.termRefsByPrefix
import dev.jibiki.japanese.Deinflector
import dev.jibiki.japanese.Kana
import dev.jibiki.japanese.Romaji
import dev.jibiki.japanese.normalizeQuery
import dev.jibiki.model.DictionaryInfo
import dev.jibiki.model.Tag
import dev.jibiki.model.TermMetaData
import dev.jibiki.model.TermRow
import dev.jibiki.radicals.RadicalIndex
import dev.jibiki.yomitan.GlossaryParser

class SearchService(
    private val db: Database,
    private val deinflector: Deinflector,
    private val radicals: suspend () -> RadicalIndex?,
) {
    private data class Match(val type: MatchType, val reasons: List<String>)

    /** Dictionaries in scope for one query, with titles and priorities. */
    private class Scope(val priorities: Map<Long, Int>, val dicts: Map<Long, DictionaryInfo>, val primaryFrequency: Long?) {
        val ids: Collection<Long> get() = priorities.keys
        fun title(id: Long) = dicts[id]?.title ?: "?"
        fun priority(id: Long) = priorities[id] ?: Int.MAX_VALUE
    }

    suspend fun search(raw: String, profileId: Long?, romajiAutoConvert: Boolean = true, limit: Int = 50): SearchResults {
        val query = normalizeQuery(raw)
        if (query.isEmpty()) return SearchResults.EMPTY
        val kanaQuery = if (romajiAutoConvert && Romaji.containsLatin(query)) Romaji.finalize(query) else null
        val lookup = kanaQuery ?: query

        return db.read {
            val scope = scope(profileId)
            val words = if (scope.ids.isEmpty()) emptyList() else findWords(lookup, query.takeIf { kanaQuery != null }, scope, limit + 1)
            val shown = words.take(limit)
            val kanjiChars = (Kana.distinctKanji(query) + shown.take(3).flatMap { Kana.distinctKanji(it.expression) }).distinct()
            val containing = if (Kana.codePoints(query).count() == 1 && Kana.hasKanji(query) && scope.ids.isNotEmpty()) {
                containing(query, scope, 0, limit)
            } else {
                emptyList()
            }
            SearchResults(query, kanaQuery, shown, kanjiSummaries(kanjiChars, scope), containing, words.size > limit)
        }
    }

    suspend fun wordsContaining(kanji: String, profileId: Long?, offset: Int, limit: Int = 50): List<WordGroup> = db.read {
        val scope = scope(profileId)
        if (scope.ids.isEmpty()) emptyList() else containing(kanji, scope, offset, limit)
    }

    suspend fun kanjiDetail(character: String, profileId: Long?): KanjiDetail = db.read {
        val scope = scope(profileId)
        val entries = kanjiFor(listOf(character), scope.ids).sortedBy { scope.priority(it.dictId) }.map { scope.title(it.dictId) to it }
        val freqs = kanjiMetaFor(listOf(character), scope.ids)
            .filter { it.mode == "freq" }
            .sortedBy { scope.priority(it.dictId) }
            .mapNotNull { row ->
                (GlossaryParser.parseMeta(row.dataJson, "freq") as? TermMetaData.Frequency)?.let {
                    FrequencyBadge(scope.title(row.dictId), it.display, it.value)
                }
            }
        KanjiDetail(character, entries, freqs, radicals()?.components(character).orEmpty())
    }

    private suspend fun Sql.scope(profileId: Long?): Scope {
        val priorities = activeDictionaries(profileId)
        val dicts = allDictionaries().associateBy { it.id }
        val primary = setting(Settings.PRIMARY_FREQUENCY_DICT)?.toLongOrNull()
        return Scope(priorities, dicts, primary)
    }

    /** Best match plus ranking tiebreaks for one (expression, reading) group. */
    private class Candidate(var match: Match, var priority: Int, var score: Int)

    private suspend fun Sql.findWords(lookup: String, raw: String?, scope: Scope, limit: Int): List<WordGroup> {
        val groups = HashMap<String, Candidate>()

        fun offer(key: String, dictId: Long, score: Int, match: Match) {
            val priority = scope.priority(dictId)
            val c = groups[key]
            if (c == null) {
                groups[key] = Candidate(match, priority, score)
            } else {
                if (better(match, c.match)) c.match = match
                c.priority = minOf(c.priority, priority)
                c.score = maxOf(c.score, score)
            }
        }
        val offerTerm = { t: TermRow, m: Match -> offer(groupKey(t), t.dictId, t.score, m) }

        exactAndDeinflected(lookup, scope, offerTerm)
        // Romaji input may also be a real headword with Latin letters (Eメール, Tシャツ).
        if (raw != null && raw != lookup) {
            for (t in termsByKeys(listOf(raw), scope.ids)) offerTerm(t, Match(MatchType.EXACT, emptyList()))
        }
        if (groups.isEmpty()) {
            // A pasted phrase: like Yomitan, look up the longest leading part that is a word.
            val cps = Kana.codePoints(lookup).toList()
            for (n in minOf(cps.size - 1, MAX_PARTIAL_LENGTH) downTo 1) {
                val part = cps.take(n).joinToString("") { Kana.codePointToString(it) }
                exactAndDeinflected(part, scope) { t, m -> offerTerm(t, Match(MatchType.PARTIAL, m.reasons)) }
                if (groups.isNotEmpty()) break
            }
        }

        val prefixes = listOf(lookup, Kana.toHiragana(lookup)).distinct()
        for (p in prefixes) {
            for (r in termRefsByPrefix(p, scope.ids, PREFIX_CANDIDATES)) {
                offer("${r.expression}\u0000${r.reading}", r.dictId, r.score, Match(MatchType.PREFIX, emptyList()))
            }
        }

        // Rank cheaply before loading full groups, so only the shown page is assembled.
        val freq = frequencyRanks(groups.keys.map { it.substringBefore('\u0000') }.distinct(), scope)
        val top = groups.keys.sortedWith(
            compareBy<String>(
                { groups.getValue(it).match.type.ordinal },
                { groups.getValue(it).match.reasons.size },
                { rankOf(freq, it) },
                { groups.getValue(it).priority },
                { -groups.getValue(it).score },
                { it.substringBefore('\u0000').length },
                { it },
            ),
        ).take(limit)
        return buildGroups(top.toSet(), scope) { groups.getValue(it).match }.sortedBy { top.indexOf(it.key) }
    }

    /** Exact matches, deinflected matches, and する-verb stems (勉強させられた → 勉強 [vs]). */
    private suspend fun Sql.exactAndDeinflected(text: String, scope: Scope, offer: (TermRow, Match) -> Unit) {
        val hira = Kana.toHiragana(text)
        for (t in termsByKeys(listOf(text, hira).distinct(), scope.ids)) offer(t, Match(MatchType.EXACT, emptyList()))

        val all = deinflector.deinflect(text) + (if (hira != text) deinflector.deinflect(hira) else emptyList())
        val deinflections = all.filter { it.reasons.isNotEmpty() }
        if (deinflections.isNotEmpty()) {
            val keys = deinflections.flatMap { listOf(it.text, Kana.toHiragana(it.text)) }.distinct()
            for (t in termsByKeys(keys, scope.ids)) {
                val readingKey = Kana.toHiragana(t.reading.ifEmpty { t.expression })
                deinflections
                    .filter { (it.text == t.expression || Kana.toHiragana(it.text) == readingKey) && deinflector.accepts(it, t.rules) }
                    .minByOrNull { it.reasons.size }
                    ?.let { offer(t, Match(MatchType.DEINFLECTED, it.reasons)) }
            }
        }

        val suru = all.filter { it.text.length > 2 && it.text.endsWith("する") && deinflector.accepts(it, listOf("vs")) }
        if (suru.isNotEmpty()) {
            val stems = suru.map { it.text.dropLast(2) to it }
            for (t in termsByKeys(stems.flatMap { (stem, _) -> listOf(stem, Kana.toHiragana(stem)) }.distinct(), scope.ids)) {
                if (t.rules.none { it.startsWith("vs") }) continue
                val readingKey = Kana.toHiragana(t.reading.ifEmpty { t.expression })
                stems.filter { (stem, _) -> stem == t.expression || Kana.toHiragana(stem) == readingKey }
                    .minByOrNull { it.second.reasons.size }
                    ?.let { (_, d) -> offer(t, Match(MatchType.DEINFLECTED, d.reasons)) }
            }
        }
    }

    private suspend fun Sql.containing(kanji: String, scope: Scope, offset: Int, limit: Int): List<WordGroup> {
        val keys = termKeysContainingKanji(kanji, scope.ids)
        val groupKeys = keys.map { "${it.expression}\u0000${it.reading}" }.distinct()
        // Rank cheaply on frequency + length before loading full rows for just one page.
        val freq = frequencyRanks(keys.map { it.expression }.distinct(), scope)
        val page = groupKeys
            .sortedWith(
                compareBy<String>(
                    { rankOf(freq, it) },
                    { it.substringBefore('\u0000').length },
                    { it },
                ),
            )
            .drop(offset)
            .take(limit)
        return buildGroups(page.toSet(), scope) { Match(MatchType.CONTAINS, emptyList()) }
            .sortedBy { page.indexOf(it.key) }
    }

    /** Loads every term, tag and meta row for the given group keys and assembles [WordGroup]s. */
    private suspend fun Sql.buildGroups(keys: Set<String>, scope: Scope, matchOf: (String) -> Match): List<WordGroup> {
        if (keys.isEmpty()) return emptyList()
        val expressions = keys.map { it.substringBefore('\u0000') }.distinct()
        val rows = termsByExpressions(expressions, scope.ids).filter { groupKey(it) in keys }
        val tagNames = rows.flatMap { it.definitionTags + it.termTags }.distinct()
        val tags = tagsFor(rows.map { it.dictId }.distinct(), tagNames)
        val meta = termMetaFor(expressions, scope.ids)

        return rows.groupBy(::groupKey).map { (key, groupRows) ->
            val first = groupRows.first()
            val sorted = groupRows.sortedWith(compareBy<TermRow>({ scope.priority(it.dictId) }, { -it.score }))
            val match = matchOf(key)
            WordGroup(
                expression = first.expression,
                reading = first.reading,
                matchType = match.type,
                reasons = match.reasons,
                frequencies = frequencyBadges(first.expression, first.reading, meta, scope),
                pitches = pitches(first.expression, first.reading, meta, scope),
                definitions = sorted.map { t ->
                    DefinitionBlock(
                        dictId = t.dictId,
                        dictTitle = scope.title(t.dictId),
                        tags = t.definitionTags.map { tags[t.dictId to it] ?: Tag(it, "", 0, "", 0) },
                        glossaryJson = t.glossaryJson,
                        termTags = t.termTags.map { tags[t.dictId to it] ?: Tag(it, "", 0, "", 0) },
                    )
                },
            )
        }
    }

    private fun frequencyBadges(expression: String, reading: String, meta: List<TermMetaRow>, scope: Scope): List<FrequencyBadge> =
        meta.filter { it.expression == expression && it.mode == "freq" }
            .sortedBy { scope.priority(it.dictId) }
            .mapNotNull { row ->
                val f = GlossaryParser.parseMeta(row.dataJson, "freq") as? TermMetaData.Frequency ?: return@mapNotNull null
                // An empty term reading means the expression is its own reading.
                val effective = reading.ifEmpty { expression }
                val metaReading = f.reading
                if (metaReading != null && metaReading != effective && Kana.toHiragana(metaReading) != Kana.toHiragana(effective)) return@mapNotNull null
                val occurrence = scope.dicts[row.dictId]?.frequencyMode == "occurrence-based"
                // Store a rank-like value (lower = more common) for sorting.
                val rank = f.value?.let { if (occurrence) Int.MAX_VALUE - it else it }
                FrequencyBadge(scope.title(row.dictId), f.display, rank)
            }

    private fun pitches(expression: String, reading: String, meta: List<TermMetaRow>, scope: Scope): List<PitchInfo> =
        meta.filter { it.expression == expression && it.mode == "pitch" }
            .sortedBy { scope.priority(it.dictId) }
            .mapNotNull { row ->
                val p = GlossaryParser.parseMeta(row.dataJson, "pitch") as? TermMetaData.Pitch ?: return@mapNotNull null
                if (p.reading != reading.ifEmpty { expression }) return@mapNotNull null
                PitchInfo(scope.title(row.dictId), p.reading, p.accents)
            }

    /**
     * Rank-like frequency (lower = more common) per "expression\u0000reading"; rows without a
     * reading are keyed "expression\u0000". Prefers the primary frequency dictionary when set.
     */
    private suspend fun Sql.frequencyRanks(expressions: List<String>, scope: Scope): Map<String, Int> {
        val out = HashMap<String, Int>()
        val primary = HashMap<String, Int>()
        for (chunk in expressions.chunked(500)) {
            for (row in termFrequencies(chunk, scope.ids)) {
                val raw = row.value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                val rank = if (scope.dicts[row.dictId]?.frequencyMode == "occurrence-based") Int.MAX_VALUE - raw else raw
                val target = if (row.dictId == scope.primaryFrequency) primary else out
                // Rows without a reading apply to every reading of the expression.
                val k = "${row.expression}\u0000${row.reading ?: ""}"
                target[k] = minOf(target[k] ?: Int.MAX_VALUE, rank)
            }
        }
        return if (primary.isEmpty()) out else out + primary
    }

    private fun rankOf(freq: Map<String, Int>, key: String): Int {
        val expression = key.substringBefore('\u0000')
        val reading = key.substringAfter('\u0000').ifEmpty { expression }
        return freq["$expression\u0000$reading"]
            ?: freq["$expression\u0000${Kana.toHiragana(reading)}"]
            ?: freq["$expression\u0000"]
            ?: Int.MAX_VALUE
    }

    private suspend fun Sql.kanjiSummaries(chars: List<String>, scope: Scope): List<KanjiSummary> {
        if (chars.isEmpty()) return emptyList()
        val rows = kanjiFor(chars, scope.ids).sortedBy { scope.priority(it.dictId) }
        val index = radicals()
        return chars.map { c ->
            KanjiSummary(c, rows.firstOrNull { it.character == c }?.meanings?.firstOrNull(), index?.components(c).orEmpty())
        }
    }

    private fun groupKey(t: TermRow) = "${t.expression}\u0000${t.reading}"

    private fun better(a: Match, b: Match): Boolean =
        a.type.ordinal < b.type.ordinal || (a.type == b.type && a.reasons.size < b.reasons.size)
}

private const val MAX_PARTIAL_LENGTH = 12
private const val PREFIX_CANDIDATES = 500

object Settings {
    const val PRIMARY_FREQUENCY_DICT = "primaryFrequencyDictId"
    const val ACTIVE_PROFILE = "activeProfile"
    const val ROMAJI = "romajiAutoConvert"
    const val THEME = "theme"
    const val FONT_SCALE = "fontScale"
    const val ONBOARDING_DONE = "onboardingDone"
}
