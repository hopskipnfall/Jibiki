package dev.jibiki.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.jibiki.domain.SearchResults
import dev.jibiki.japanese.Romaji
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SearchUi(
    val results: SearchResults = SearchResults.EMPTY,
    val loading: Boolean = false,
    val error: String? = null,
    val loadingMore: Boolean = false,
    val containingExhausted: Boolean = false,
)

class SearchController(private val graph: AppGraph) {
    private val _field = MutableStateFlow(TextFieldValue(""))
    val field: StateFlow<TextFieldValue> = _field.asStateFlow()

    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()

    private val refresh = MutableStateFlow(0)

    private val _radicalRequest = MutableStateFlow<Set<String>?>(null)

    /** Radicals to open the picker with (set by kanji detail); the search screen consumes it. */
    val radicalRequest: StateFlow<Set<String>?> = _radicalRequest.asStateFlow()

    fun requestRadicals(selection: Set<String>) {
        _radicalRequest.value = selection
    }

    fun consumeRadicalRequest() {
        _radicalRequest.value = null
    }

    fun onFieldChange(value: TextFieldValue) {
        _field.value = value
    }

    fun setQuery(text: String) {
        _field.value = TextFieldValue(text, TextRange(text.length))
    }

    /** Inserts text at the cursor (used by the radical picker). */
    fun insertAtCursor(text: String) {
        val v = _field.value
        val start = minOf(v.selection.start, v.selection.end)
        val end = maxOf(v.selection.start, v.selection.end)
        val newText = v.text.substring(0, start) + text + v.text.substring(end)
        _field.value = TextFieldValue(newText, TextRange(start + text.length))
    }

    /** Re-runs the current search (e.g. after dictionaries changed). */
    fun refresh() {
        refresh.value++
    }

    /** Hint shown while typing romaji, e.g. "たべ" for "taber". */
    fun romajiPreview(text: String): String? {
        if (!graph.settings.value.romaji || !Romaji.containsLatin(text)) return null
        val r = Romaji.toHiragana(text.trim())
        return (r.kana + r.pending).takeIf { it.isNotBlank() }
    }

    @OptIn(FlowPreview::class)
    fun start(initialQuery: String?) {
        if (!initialQuery.isNullOrBlank()) setQuery(initialQuery)
        graph.scope.launch {
            combine(
                _field.map { it.text }.distinctUntilChanged().debounce(150),
                graph.settings.map { it.activeProfile to it.romaji }.distinctUntilChanged(),
                graph.library.version,
                refresh,
            ) { text, (profile, romaji), _, _ -> Triple(text, profile, romaji) }
                .collectLatest { (text, profile, romaji) ->
                    graph.platform.publishQuery(text)
                    if (text.isBlank()) {
                        _ui.value = SearchUi()
                        return@collectLatest
                    }
                    _ui.value = _ui.value.copy(loading = true, error = null)
                    _ui.value = try {
                        SearchUi(results = graph.search.search(text, profile, romaji))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        SearchUi(error = e.message ?: "Search failed")
                    }
                }
        }
    }

    fun loadMoreContaining() {
        val current = _ui.value
        val q = current.results.query
        if (current.loadingMore || current.containingExhausted || current.results.containing.isEmpty()) return
        _ui.value = current.copy(loadingMore = true)
        graph.scope.launch {
            val more = try {
                graph.search.wordsContaining(q, graph.settings.value.activeProfile, current.results.containing.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(loadingMore = false)
                return@launch
            }
            val latest = _ui.value
            if (latest.results.query != q) return@launch
            _ui.value = latest.copy(
                results = latest.results.copy(containing = latest.results.containing + more),
                loadingMore = false,
                containingExhausted = more.isEmpty(),
            )
        }
    }
}
