package dev.jibiki.app.screens.library

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.ImportJob
import dev.jibiki.app.JobState
import dev.jibiki.app.ui.Banner
import dev.jibiki.app.ui.Label
import dev.jibiki.app.ui.ScreenScaffold
import dev.jibiki.app.ui.formatBytes
import dev.jibiki.model.DictKind
import dev.jibiki.model.DictionaryInfo
import dev.jibiki.model.Profile
import kotlinx.coroutines.launch

private val KIND_LABELS = mapOf(
    DictKind.TERMS to "Words", DictKind.KANJI to "Kanji", DictKind.TERM_FREQ to "Frequency",
    DictKind.TERM_PITCH to "Pitch accent", DictKind.TERM_IPA to "IPA", DictKind.KANJI_FREQ to "Kanji frequency",
)

@Composable
fun DictionariesScreen(graph: AppGraph, onBack: () -> Unit) {
    ScreenScaffold("Dictionaries", onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { StorageStatus(graph) }
            item { ImportSection(graph) }
            item { InstalledHeader() }
            dictionaryItems(graph)
        }
    }
}

@Composable
private fun InstalledHeader() {
    Text("Installed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

/** Import button, running/finished jobs and the duplicate prompt. Also used by onboarding. */
@Composable
fun ImportSection(graph: AppGraph) {
    val jobs by graph.imports.jobs.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = graph.imports::pickAndImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Import dictionaries", Modifier.padding(start = 6.dp))
            }
            if (jobs.any { it.state == JobState.DONE || it.state == JobState.FAILED || it.state == JobState.SKIPPED }) {
                TextButton(onClick = graph.imports::dismissFinished) { Text("Clear finished") }
            }
        }
        Text(
            "Choose one or more Yomitan dictionary .zip files. Imports keep running while you use the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (job in jobs) JobCard(job)
    }
}

@Composable
private fun JobCard(job: ImportJob) {
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = if (job.state == JobState.FAILED) colors.errorContainer else colors.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.title ?: job.fileName, fontWeight = FontWeight.SemiBold)
            if (job.state == JobState.RUNNING) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            Text(
                job.detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.state == JobState.FAILED) colors.onErrorContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageStatus(graph: AppGraph) {
    val version by graph.library.version.collectAsState()
    var persistedOverride by remember { mutableStateOf<Boolean?>(null) }
    val persisted by produceState<Boolean?>(null, version, persistedOverride) {
        value = persistedOverride ?: graph.platform.storage.isPersisted()
    }
    val estimate by produceState<Pair<Long, Long>?>(null, version) { value = graph.platform.storage.estimate() }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        estimate?.let { (used, quota) ->
            Text("Storage: ${formatBytes(used)} used of ${formatBytes(quota)} available", style = MaterialTheme.typography.bodySmall)
        }
        if (graph.platform.isWeb && persisted == false) {
            Banner(
                "Your browser may delete imported dictionaries when space runs low.",
                action = "Keep data",
                onAction = { scope.launch { persistedOverride = graph.platform.storage.requestPersist() } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.dictionaryItems(graph: AppGraph) {
    item {
        val version by graph.library.version.collectAsState()
        val data by produceState<Pair<List<DictionaryInfo>, Map<Long, Set<Long>>>?>(null, version) {
            val dicts = graph.library.dictionaries()
            val membership = HashMap<Long, MutableSet<Long>>()
            for (p in listOf(Profile.ENGLISH, Profile.JAPANESE)) {
                graph.library.entries(p).forEach { membership.getOrPut(it.dict.id) { mutableSetOf() } += p }
            }
            value = dicts to membership
        }
        val scope = rememberCoroutineScope()
        var confirmDelete by remember { mutableStateOf<DictionaryInfo?>(null) }
        var deleting by remember { mutableStateOf<Pair<Long, Long>?>(null) }
        val colors = MaterialTheme.colorScheme
        val (dicts, membership) = data ?: return@item
        if (dicts.isEmpty()) {
            Text("No dictionaries installed yet.", color = colors.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (d in dicts) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    buildString {
                                        append("rev. ${d.revision}")
                                        if (d.termCount > 0) append(" · ${d.termCount} words")
                                        if (d.kanjiCount > 0) append(" · ${d.kanjiCount} kanji")
                                        if (d.mediaBytes > 0) append(" · ${formatBytes(d.mediaBytes)} media")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { confirmDelete = d }, enabled = deleting == null) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${d.title}")
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (k in d.kinds) Label(KIND_LABELS.getValue(k), colors.surfaceContainerHigh, colors.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Show in:", style = MaterialTheme.typography.bodySmall)
                            for ((pid, name) in listOf(Profile.ENGLISH to "English", Profile.JAPANESE to "日本語")) {
                                val member = pid in membership[d.id].orEmpty()
                                FilterChip(
                                    selected = member,
                                    onClick = { scope.launch { graph.library.setMember(pid, d.id, !member) } },
                                    label = { Text(name) },
                                )
                            }
                        }
                        if (deleting?.first == d.id) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Deleting… ${deleting?.second} rows", style = MaterialTheme.typography.bodySmall)
                        }
                        d.attribution?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        confirmDelete?.let { d ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Delete ${d.title}?") },
                text = { Text("This removes the dictionary and all of its entries from this device. You can import it again later.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = null
                        graph.scope.launch { // outlives this screen so a delete always finishes
                            deleting = d.id to 0L
                            graph.library.delete(d.id) { n -> deleting = d.id to n }
                            deleting = null
                        }
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            )
        }
    }
}
