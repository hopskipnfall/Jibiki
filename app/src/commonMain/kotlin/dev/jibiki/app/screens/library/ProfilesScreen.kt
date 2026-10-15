package dev.jibiki.app.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.ui.ScreenScaffold
import dev.jibiki.model.DictKind
import dev.jibiki.model.Profile
import dev.jibiki.model.ProfileEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(graph: AppGraph, onBack: () -> Unit) {
    ScreenScaffold("Profiles", onBack) {
        var profile by remember { mutableStateOf(Profile.ENGLISH) }
        Text(
            "Each profile is an ordered list of dictionaries. Results show definitions in this order. " +
                "Switch profiles from the search bar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.padding(vertical = 12.dp)) {
            listOf(Profile.ENGLISH to "English", Profile.JAPANESE to "日本語").forEachIndexed { i, (id, label) ->
                SegmentedButton(selected = profile == id, onClick = { profile = id }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        ProfileEditor(graph, profile)
        PrimaryFrequencyPicker(graph)
    }
}

@Composable
fun ProfileEditor(graph: AppGraph, profileId: Long) {
    val version by graph.library.version.collectAsState()
    val entries by produceState(emptyList<ProfileEntry>(), version, profileId) { value = graph.library.entries(profileId) }
    val scope = rememberCoroutineScope()
    if (entries.isEmpty()) {
        Text("No dictionaries in this profile. Add them from the Dictionaries screen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(entries, key = { _, e -> e.dict.id }) { i, e ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(e.dict.title)
                        Text(e.dict.kinds.joinToString(", ") { it.name.lowercase().replace('_', ' ') }, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { scope.launch { graph.library.move(profileId, e.dict.id, up = true) } }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { scope.launch { graph.library.move(profileId, e.dict.id, up = false) } }, enabled = i < entries.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    Switch(
                        checked = e.enabled,
                        onCheckedChange = { on -> scope.launch { graph.library.setEnabled(profileId, e.dict.id, on) } },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryFrequencyPicker(graph: AppGraph) {
    val version by graph.library.version.collectAsState()
    val settings by graph.settings.collectAsState()
    val freqDicts by produceState(emptyList<dev.jibiki.model.DictionaryInfo>(), version) {
        value = graph.library.dictionaries().filter { DictKind.TERM_FREQ in it.kinds }
    }
    if (freqDicts.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Text("Rank results by frequency from", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { open = true }) {
            Text(freqDicts.firstOrNull { it.id == settings.primaryFrequencyDict }?.title ?: "Most common across all lists")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Most common across all lists") }, onClick = {
                open = false
                graph.updateSettings { it.copy(primaryFrequencyDict = null) }
            })
            for (d in freqDicts) {
                DropdownMenuItem(text = { Text(d.title) }, onClick = {
                    open = false
                    graph.updateSettings { it.copy(primaryFrequencyDict = d.id) }
                    graph.searchController.refresh()
                })
            }
        }
    }
}
