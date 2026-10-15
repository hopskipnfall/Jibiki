package dev.jibiki.app.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jibiki.app.AppGraph
import dev.jibiki.app.ui.ScreenScaffold
import dev.jibiki.app.ui.SectionHeader

@Composable
fun LicensesScreen(graph: AppGraph, onBack: () -> Unit) {
    val dictionaries by produceState(emptyList<dev.jibiki.model.DictionaryInfo>()) { value = graph.library.dictionaries() }
    ScreenScaffold("Licenses & attributions", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Jibiki")
            Para(
                "Jibiki is free software, licensed under the GNU General Public License v3.0 or later. " +
                    "It contains no dictionary content; you import your own dictionaries.",
            )
            SectionHeader("Radical data — RADKFILE")
            Para(
                "Radical lookup uses RADKFILE/KRADFILE, Copyright © Michael Raine, James Breen and the " +
                    "Electronic Dictionary Research and Development Group (EDRDG), used under the Creative Commons " +
                    "Attribution-ShareAlike 4.0 licence. See https://www.edrdg.org/edrdg/licence.html",
            )
            SectionHeader("Deinflection rules — Yomitan")
            Para("Japanese deinflection rules are ported from Yomitan (https://github.com/yomidevs/yomitan), Copyright © Yomitan Authors, GPL-3.0-or-later.")
            if (graph.platform.isWeb) {
                SectionHeader("Font — Noto Sans JP")
                Para("Noto Sans JP, Copyright © Google LLC, licensed under the SIL Open Font License 1.1.")
            }
            SectionHeader("SQLite & libraries")
            Para("SQLite (public domain), AndroidX SQLite and Jetpack Compose (Apache 2.0), Kotlin & Compose Multiplatform (Apache 2.0), fflate (MIT).")
            if (dictionaries.isNotEmpty()) {
                SectionHeader("Your dictionaries")
                for (d in dictionaries) {
                    Text(d.title, style = MaterialTheme.typography.titleSmall)
                    Para(listOfNotNull(d.author?.let { "By $it" }, d.attribution, d.url).joinToString("\n").ifEmpty { "No attribution provided." })
                }
            }
        }
    }
}

@Composable
private fun Para(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
