package dev.jibiki.app.screens.kanji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.radicals.RadicalIndex

/**
 * Multi-radical kanji lookup: tap radicals you can see in the kanji; candidates narrow down,
 * impossible radicals grey out. Tapping a candidate hands it to [onPick].
 */
@Composable
fun RadicalPicker(
    graph: AppGraph,
    initialSelection: Set<String>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index by produceState<RadicalIndex?>(null) { value = graph.radicals() }
    var selected by remember(initialSelection) { mutableStateOf(initialSelection) }
    var strokeFilter by remember { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Find a kanji by its parts", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet(); strokeFilter = null }) { Text("Clear") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }
        val idx = index
        if (idx == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val candidates = remember(idx, selected) { idx.candidates(selected) }
        val available = remember(idx, selected) { idx.available(selected) }
        var strokes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
        LaunchedEffect(candidates) {
            strokes = if (candidates.isEmpty()) emptyMap() else graph.library.strokeCounts(candidates)
        }
        fun strokeOf(k: String) = strokes[k]
        val sorted = remember(candidates, strokes) {
            candidates.sortedWith(compareBy({ strokeOf(it) ?: (idx.radicalCount(it) * 3) }, { it }))
        }
        val strokeOptions = remember(sorted, strokes) { sorted.mapNotNull { strokeOf(it) }.distinct().sorted() }
        val shown = if (strokeFilter == null) sorted else sorted.filter { strokeOf(it) == strokeFilter }

        // Candidate strip.
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
            if (selected.isEmpty()) {
                Text("Select the parts you recognise below.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else if (shown.isEmpty()) {
                Text("No kanji contain all of these parts.", color = colors.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(shown.take(400)) { k ->
                        Box(
                            Modifier.size(48.dp).background(colors.primaryContainer, RoundedCornerShape(10.dp)).clickable { onPick(k) },
                            contentAlignment = Alignment.Center,
                        ) { Text(k, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer) }
                    }
                }
            }
        }
        if (strokeOptions.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                items(strokeOptions) { n ->
                    FilterChip(selected = strokeFilter == n, onClick = { strokeFilter = if (strokeFilter == n) null else n }, label = { Text("${n}画") })
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text("${candidates.size} kanji", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }

        val groups = remember(idx) { idx.radicals.groupBy { it.strokes }.entries.sortedBy { it.key }.map { it.key to it.value } }
        LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.weight(1f).padding(top = 4.dp)) {
            for ((n, radicals) in groups) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("${n}画", Modifier.padding(top = 8.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
                items(radicals, key = { it.key }) { r ->
                    val isSelected = r.key in selected
                    val enabled = isSelected || r.key in available
                    Box(
                        Modifier
                            .padding(2.dp)
                            .size(40.dp)
                            .alpha(if (enabled) 1f else 0.25f)
                            .background(if (isSelected) colors.secondary else colors.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) {
                                selected = if (isSelected) selected - r.key else selected + r.key
                                strokeFilter = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            r.display,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onSecondary else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}
