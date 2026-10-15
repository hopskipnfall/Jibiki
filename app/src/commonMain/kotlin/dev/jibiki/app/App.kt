package dev.jibiki.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jibiki.app.screens.kanji.KanjiDetailContent
import dev.jibiki.app.screens.library.DictionariesScreen
import dev.jibiki.app.screens.library.ProfilesScreen
import dev.jibiki.app.screens.onboarding.OnboardingScreen
import dev.jibiki.app.screens.search.SearchScreen
import dev.jibiki.app.screens.settings.LicensesScreen
import dev.jibiki.app.screens.settings.SettingsScreen
import dev.jibiki.app.ui.JibikiTheme
import dev.jibiki.app.ui.ScreenScaffold
import dev.jibiki.domain.DuplicatePolicy

@Composable
fun JibikiApp(graph: AppGraph, onBack: () -> Unit = { graph.navigator.pop() }) {
    val settings by graph.settings.collectAsState()
    val startup by graph.startup.collectAsState()
    LaunchedEffect(Unit) { graph.start() }

    JibikiTheme(settings.theme, settings.fontScale, graph.platform.fontFamily) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = startup) {
                Startup.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Startup.OtherTab -> Message(
                    "Jibiki is open in another tab",
                    "Close the other tab, then try again. Only one tab can use the dictionary database at a time.",
                    "Try again",
                ) { graph.platform.reload() }
                is Startup.Failed -> Message("Jibiki couldn't start", s.message, null) {}
                is Startup.Ready -> {
                    Screens(graph, onBack)
                    DuplicateDialog(graph)
                }
            }
        }
    }
}

@Composable
private fun Screens(graph: AppGraph, onBack: () -> Unit) {
    when (val screen = graph.navigator.current) {
        Screen.Search -> SearchScreen(graph)
        Screen.Onboarding -> OnboardingScreen(graph)
        Screen.Dictionaries -> DictionariesScreen(graph, onBack)
        Screen.Profiles -> ProfilesScreen(graph, onBack)
        Screen.Settings -> SettingsScreen(graph, onBack)
        Screen.Licenses -> LicensesScreen(graph, onBack)
        is Screen.Kanji -> ScreenScaffold(screen.character, onBack) {
            KanjiDetailContent(
                graph, screen.character,
                onSearch = { q ->
                    graph.searchController.setQuery(q)
                    graph.navigator.resetTo(Screen.Search)
                },
                onOpenRadicals = { selection ->
                    graph.searchController.requestRadicals(selection)
                    graph.navigator.resetTo(Screen.Search)
                },
            )
        }
    }
}

@Composable
private fun DuplicateDialog(graph: AppGraph) {
    val prompt by graph.imports.prompt.collectAsState()
    val p = prompt ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("${p.title} is already installed") },
        text = {
            Text(
                "Installed revision: ${p.existingRevisions.joinToString(", ")}. New file: ${p.newRevision}.\n\n" +
                    "Replace the installed copy, keep both, or skip this file?",
            )
        },
        confirmButton = { TextButton(onClick = { p.choose(DuplicatePolicy.REPLACE) }) { Text("Replace") } },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { p.choose(DuplicatePolicy.KEEP_BOTH) }) { Text("Keep both") }
                TextButton(onClick = { p.choose(DuplicatePolicy.SKIP) }) { Text("Skip") }
            }
        },
    )
}

@Composable
private fun Message(title: String, body: String, action: String?, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}
