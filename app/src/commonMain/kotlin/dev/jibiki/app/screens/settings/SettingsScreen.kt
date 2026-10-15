package dev.jibiki.app.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.Screen
import dev.jibiki.app.ThemeMode
import dev.jibiki.app.ui.ScreenScaffold
import dev.jibiki.app.ui.SectionHeader
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(graph: AppGraph, onBack: () -> Unit) {
    val settings by graph.settings.collectAsState()
    ScreenScaffold("Settings", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("Appearance")
            SingleChoiceSegmentedButtonRow {
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = settings.theme == mode,
                        onClick = { graph.updateSettings { it.copy(theme = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
            Text("Text size: ${(settings.fontScale * 100).roundToInt()}%")
            Slider(
                value = settings.fontScale,
                onValueChange = { v -> graph.updateSettings { it.copy(fontScale = (v * 20).roundToInt() / 20f) } },
                valueRange = 0.85f..1.4f,
            )

            SectionHeader("Search")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Convert romaji to kana")
                    Text("Typing “taberu” searches たべる.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.romaji, onCheckedChange = { on -> graph.updateSettings { it.copy(romaji = on) } })
            }

            SectionHeader("Storage")
            val estimate by produceState<Pair<Long, Long>?>(null) { value = graph.platform.storage.estimate() }
            estimate?.let { (used, quota) ->
                Text("${dev.jibiki.app.ui.formatBytes(used)} used of ${dev.jibiki.app.ui.formatBytes(quota)}")
            }
            OutlinedButton(onClick = { graph.navigator.push(Screen.Dictionaries) }) { Text("Manage dictionaries") }

            SectionHeader("About")
            OutlinedButton(onClick = {
                graph.updateSettings { it.copy(onboardingDone = false) }
                graph.navigator.resetTo(Screen.Onboarding)
            }) { Text("Show the welcome guide again") }
            OutlinedButton(onClick = { graph.navigator.push(Screen.Licenses) }) { Text("Licenses & attributions") }
            Text(
                "Jibiki 字引 · GPL-3.0",
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
