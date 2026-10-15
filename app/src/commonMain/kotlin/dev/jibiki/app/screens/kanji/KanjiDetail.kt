package dev.jibiki.app.screens.kanji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.screens.search.WordCard
import dev.jibiki.app.ui.Label
import dev.jibiki.app.ui.SectionHeader
import dev.jibiki.domain.KanjiDetail
import dev.jibiki.domain.WordGroup
import kotlinx.coroutines.launch

private val STAT_LABELS = mapOf(
    "strokes" to "Strokes", "grade" to "Grade", "jlpt" to "JLPT", "freq" to "Frequency",
    "skip" to "SKIP", "heisig" to "Heisig", "heisig6" to "Heisig (6th)",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KanjiDetailContent(
    graph: AppGraph,
    character: String,
    onSearch: (String) -> Unit,
    onOpenRadicals: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = graph.settings.value.activeProfile
    var detail by remember(character) { mutableStateOf<KanjiDetail?>(null) }
    var words by remember(character) { mutableStateOf<List<WordGroup>>(emptyList()) }
    var exhausted by remember(character) { mutableStateOf(false) }
    var loading by remember(character) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val media = remember { dev.jibiki.app.render.MediaLoader { id, path -> graph.library.media(id, path) } }

    fun loadMore() {
        if (loading || exhausted) return
        loading = true
        scope.launch {
            try {
                val more = graph.search.wordsContaining(character, profile, words.size)
                words = words + more
                exhausted = more.size < 50
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(character) {
        detail = graph.search.kanjiDetail(character, profile)
        loadMore()
    }
    val colors = MaterialTheme.colorScheme

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(character, fontSize = 88.sp, modifier = Modifier.padding(end = 20.dp))
                val d = detail
                if (d != null && d.components.isNotEmpty()) {
                    Column {
                        Text("Parts", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in d.components) AssistChip(onClick = { onOpenRadicals(setOf(c.key)) }, label = { Text(c.display) })
                        }
                    }
                }
            }
        }
        val d = detail
        if (d == null) {
            item { CircularProgressIndicator() }
        } else {
            if (d.frequencies.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (f in d.frequencies) Label("${f.dictTitle} ${f.display}", colors.surfaceContainerHigh, colors.onSurfaceVariant)
                    }
                }
            }
            if (d.entries.isEmpty()) {
                item {
                    Text(
                        "No kanji dictionary in this profile has an entry for $character.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(d.entries) { (title, row) ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label(title, colors.primaryContainer, colors.onPrimaryContainer, bold = true)
                        if (row.meanings.isNotEmpty()) Text(row.meanings.joinToString("; "), style = MaterialTheme.typography.bodyLarge)
                        if (row.onyomi.isNotEmpty()) Reading("音", row.onyomi.joinToString("、"))
                        if (row.kunyomi.isNotEmpty()) Reading("訓", row.kunyomi.joinToString("、"))
                        if (row.stats.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for ((k, v) in row.stats) Label("${STAT_LABELS[k] ?: k} $v", colors.surfaceContainerHigh, colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Words containing $character") }
        items(words, key = { it.key }) { WordCard(it, media, onSearch) }
        item {
            if (!exhausted && words.isNotEmpty()) {
                OutlinedButton(onClick = ::loadMore, enabled = !loading, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(if (loading) "Loading…" else "Load more")
                }
            } else if (exhausted && words.isEmpty()) {
                Text("No words found.", color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Reading(label: String, text: String) {
    Row {
        Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
