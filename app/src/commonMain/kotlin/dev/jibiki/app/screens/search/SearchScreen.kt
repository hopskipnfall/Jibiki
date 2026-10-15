package dev.jibiki.app.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.Screen
import dev.jibiki.app.Startup
import dev.jibiki.app.render.MediaLoader
import dev.jibiki.app.screens.kanji.KanjiDetailContent
import dev.jibiki.app.screens.kanji.RadicalPicker
import dev.jibiki.app.ui.Banner
import dev.jibiki.app.ui.SectionHeader
import dev.jibiki.domain.KanjiSummary
import dev.jibiki.model.DictStatus
import dev.jibiki.model.Profile

private sealed interface Pane {
    data object None : Pane
    data class Radicals(val selection: Set<String>) : Pane
    data class Kanji(val character: String) : Pane
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(graph: AppGraph) {
    var pane by remember { mutableStateOf<Pane>(Pane.None) }
    val radicalRequest by graph.searchController.radicalRequest.collectAsState()
    androidx.compose.runtime.LaunchedEffect(radicalRequest) {
        radicalRequest?.let {
            pane = Pane.Radicals(it)
            graph.searchController.consumeRadicalRequest()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val wide = maxWidth >= 840.dp
        val openKanji: (String) -> Unit = { c -> if (wide) pane = Pane.Kanji(c) else graph.navigator.push(Screen.Kanji(c)) }
        val openRadicals: (Set<String>) -> Unit = { sel -> pane = Pane.Radicals(sel) }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                SearchPane(graph, openKanji, { openRadicals(emptySet()) }, Modifier.widthIn(max = 640.dp).weight(1f).fillMaxHeight())
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                    when (val p = pane) {
                        Pane.None -> WidePlaceholder()
                        is Pane.Radicals -> RadicalPicker(
                            graph, p.selection,
                            onPick = { graph.searchController.insertAtCursor(it) },
                            onClose = { pane = Pane.None },
                        )
                        is Pane.Kanji -> KanjiDetailContent(
                            graph, p.character,
                            onSearch = graph.searchController::setQuery,
                            onOpenRadicals = openRadicals,
                        )
                    }
                }
            }
        } else {
            SearchPane(graph, openKanji, { openRadicals(emptySet()) }, Modifier.fillMaxSize())
            val p = pane
            if (p is Pane.Radicals) {
                ModalBottomSheet(
                    onDismissRequest = { pane = Pane.None },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    RadicalPicker(
                        graph, p.selection,
                        onPick = {
                            graph.searchController.insertAtCursor(it)
                            pane = Pane.None
                        },
                        onClose = { pane = Pane.None },
                        modifier = Modifier.fillMaxHeight(0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidePlaceholder() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("字引", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap a kanji to see its details here,\nor use 部 to find a kanji by its parts.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchPane(graph: AppGraph, openKanji: (String) -> Unit, openRadicals: () -> Unit, modifier: Modifier) {
    val controller = graph.searchController
    val field by controller.field.collectAsState()
    val ui by controller.ui.collectAsState()
    val settings by graph.settings.collectAsState()
    val startup by graph.startup.collectAsState()
    val version by graph.library.version.collectAsState()
    val dictionaries by produceState(emptyList<dev.jibiki.model.DictionaryInfo>(), version) { value = graph.library.dictionaries() }
    val media = remember { MediaLoader { id, path -> graph.library.media(id, path) } }
    var menu by remember { mutableStateOf(false) }

    Column(modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field,
                onValueChange = controller::onFieldChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search 日本語, かな or romaji") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (field.text.isNotEmpty()) {
                        IconButton(onClick = { controller.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.width(6.dp))
            FilledTonalButton(onClick = openRadicals, modifier = Modifier.size(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("部", style = MaterialTheme.typography.titleLarge)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Dictionaries") }, onClick = { menu = false; graph.navigator.push(Screen.Dictionaries) })
                    DropdownMenuItem(text = { Text("Profiles") }, onClick = { menu = false; graph.navigator.push(Screen.Profiles) })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; graph.navigator.push(Screen.Settings) })
                }
            }
        }
        ProfileToggle(settings.activeProfile) { p -> graph.updateSettings { it.copy(activeProfile = p) } }

        val preview = controller.romajiPreview(field.text)
        if (preview != null) {
            Text("Searching $preview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        val ready = dictionaries.filter { it.status == DictStatus.READY }
        val results = ui.results
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if ((startup as? Startup.Ready)?.inMemory == true) {
                item { Banner("Storage isn't available in this window (e.g. a private window). Dictionaries won't survive a reload.") }
            }
            if (ready.isEmpty()) {
                item { EmptyLibrary(onImport = { graph.navigator.push(Screen.Dictionaries) }) }
            } else if (field.text.isBlank()) {
                item {
                    Text(
                        "Type a word in kanji, kana or romaji. Conjugated forms work too.",
                        Modifier.padding(top = 24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (ui.error != null) {
                item { Text("Search failed: ${ui.error}", color = MaterialTheme.colorScheme.error) }
            } else if (!ui.loading && results.isEmpty && results.query.isNotEmpty()) {
                item {
                    Text(
                        "No results for “${results.query}”.",
                        Modifier.padding(top = 24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (results.kanji.isNotEmpty()) {
                item { SectionHeader("Kanji") }
                item { KanjiStrip(results.kanji, openKanji) }
            }
            if (results.words.isNotEmpty()) {
                item { SectionHeader("Words") }
                items(results.words, key = { "w" + it.key }) { WordCard(it, media, controller::setQuery) }
                if (results.hasMoreWords) {
                    item { Text("Showing the top ${results.words.size} matches — refine your search to see others.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (results.containing.isNotEmpty()) {
                item { SectionHeader("Words containing ${results.query}") }
                items(results.containing, key = { "c" + it.key }) { WordCard(it, media, controller::setQuery) }
                if (!ui.containingExhausted) {
                    item {
                        OutlinedButton(onClick = controller::loadMoreContaining, enabled = !ui.loadingMore) {
                            Text(if (ui.loadingMore) "Loading…" else "Load more")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileToggle(active: Long?, onChange: (Long?) -> Unit) {
    val options = listOf<Pair<Long?, String>>(Profile.ENGLISH to "English", Profile.JAPANESE to "日本語", null to "All")
    SingleChoiceSegmentedButtonRow(Modifier.padding(vertical = 8.dp)) {
        options.forEachIndexed { i, (id, label) ->
            SegmentedButton(
                selected = active == id,
                onClick = { onChange(id) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
private fun KanjiStrip(kanji: List<KanjiSummary>, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(kanji, key = { it.character }) { k ->
            Column(
                Modifier
                    .width(96.dp)
                    .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
                    .clickable { onOpen(k.character) }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(k.character, style = MaterialTheme.typography.displaySmall)
                Text(
                    k.meaning ?: k.components.joinToString(" ") { it.display },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("字引", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text("No dictionaries yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Jibiki doesn't ship with dictionary data. Import Yomitan dictionaries to start searching.",
            Modifier.padding(16.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onImport) { Text("Import dictionaries") }
    }
}
