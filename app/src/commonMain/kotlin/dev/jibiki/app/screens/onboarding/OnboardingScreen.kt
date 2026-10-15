package dev.jibiki.app.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.Screen
import dev.jibiki.app.screens.library.ImportSection
import dev.jibiki.app.screens.library.ProfileEditor
import dev.jibiki.model.Profile
import kotlinx.coroutines.launch

private const val YOMITAN_DICTIONARIES = "https://yomitan.wiki/dictionaries/"

@Composable
fun OnboardingScreen(graph: AppGraph) {
    val steps = buildList {
        add("welcome"); add("byod"); add("import"); add("profiles")
        if (graph.platform.isWeb) add("storage")
    }
    var step by remember { mutableIntStateOf(0) }
    fun finish() {
        graph.updateSettings { it.copy(onboardingDone = true) }
        graph.navigator.resetTo(Screen.Search)
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
        Column(
            Modifier.widthIn(max = 640.dp).weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (steps[step]) {
                "welcome" -> Welcome()
                "byod" -> BringYourOwn()
                "import" -> ImportStep(graph)
                "profiles" -> ProfilesStep(graph)
                "storage" -> StorageStep(graph)
            }
        }
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::finish) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.padding(end = 8.dp)) { Text("Back") }
            Button(onClick = { if (step < steps.lastIndex) step++ else finish() }) {
                Text(if (step < steps.lastIndex) "Next" else "Start searching")
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Welcome() {
    Text("字引", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Light)
    Title("Welcome to Jibiki")
    Body(
        "A Japanese dictionary for looking things up quickly — by word, by reading (kana or romaji), " +
            "by conjugated form, or by the parts of a kanji you can see.",
    )
}

@Composable
private fun BringYourOwn() {
    val uri = LocalUriHandler.current
    Title("Bring your own dictionaries")
    Body(
        "Jibiki doesn't include any dictionary content. Instead you import dictionaries in the Yomitan " +
            "format — the same .zip files used by the Yomitan browser extension. Both bilingual (Japanese → English) " +
            "and monolingual (Japanese → Japanese) dictionaries work, as do kanji, frequency and pitch-accent dictionaries.",
    )
    Body("Yomitan's documentation lists where to find dictionaries:")
    OutlinedButton(onClick = { uri.openUri(YOMITAN_DICTIONARIES) }) { Text("Open Yomitan's dictionary guide") }
}

@Composable
private fun ImportStep(graph: AppGraph) {
    Title("Import dictionaries")
    Body("Pick one or more Yomitan .zip files. Large dictionaries can take a few minutes; you can continue while they import.")
    ImportSection(graph)
}

@Composable
private fun ProfilesStep(graph: AppGraph) {
    Title("Check your profiles")
    Body(
        "Jibiki sorted your dictionaries into an English and a 日本語 profile. Switch between them from the search bar. " +
            "Reorder or turn dictionaries off here, or later from Profiles.",
    )
    for ((id, name) in listOf(Profile.ENGLISH to "English", Profile.JAPANESE to "日本語")) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.height(220.dp)) { ProfileEditor(graph, id) }
    }
}

@Composable
private fun StorageStep(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    var persisted by remember { mutableStateOf<Boolean?>(null) }
    val initial by produceState<Boolean?>(null) { value = graph.platform.storage.isPersisted() }
    val state = persisted ?: initial
    Title("Keep your data safe")
    Body(
        "Browsers may clear site data when your disk is nearly full. Allowing persistent storage keeps your " +
            "imported dictionaries until you remove them. Your browser may ask for permission.",
    )
    when (state) {
        true -> Text("✓ Persistent storage is on.", color = MaterialTheme.colorScheme.primary)
        else -> Button(onClick = { scope.launch { persisted = graph.platform.storage.requestPersist() } }) { Text("Allow persistent storage") }
    }
    if (persisted == false) Body("The browser declined. Jibiki will still work, and will remind you on the Dictionaries screen.")
}
