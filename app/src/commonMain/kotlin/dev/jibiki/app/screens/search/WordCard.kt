package dev.jibiki.app.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jibiki.app.render.GlossaryView
import dev.jibiki.app.render.MediaLoader
import dev.jibiki.app.render.RenderContext
import dev.jibiki.app.ui.Label
import dev.jibiki.app.ui.pitchNotation
import dev.jibiki.domain.MatchType
import dev.jibiki.domain.WordGroup
import dev.jibiki.model.Tag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordCard(word: WordGroup, media: MediaLoader, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (word.reading.isNotEmpty() && word.reading != word.expression) {
                        Text(word.reading, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Text(word.expression, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                }
            }
            val pitchLines = word.pitches.flatMap { p -> p.accents.map { pitchNotation(p.reading, it.downstep) + " [${it.downstep}]" } }.distinct()
            if (word.reasons.isNotEmpty() || word.frequencies.isNotEmpty() || pitchLines.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (word.reasons.isNotEmpty()) {
                        Label("← " + word.reasons.joinToString(" « "), colors.tertiaryContainer, colors.onTertiaryContainer)
                    }
                    for (p in pitchLines) Label(p, colors.surfaceContainerHigh, colors.onSurface)
                    for (f in word.frequencies) Label("${f.dictTitle} ${f.display}", colors.surfaceContainerHigh, colors.onSurfaceVariant)
                }
            }
            word.definitions.forEachIndexed { i, block ->
                if (i > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label(block.dictTitle, colors.primaryContainer, colors.onPrimaryContainer, bold = true)
                        for (tag in block.tags + block.termTags) TagLabel(tag)
                    }
                    GlossaryView(block.glossary, RenderContext(block.dictId, media, onSearch))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TagLabel(tag: Tag) {
    val colors = MaterialTheme.colorScheme
    val label = @Composable { Label(tag.name, colors.surfaceContainerHigh, colors.onSurfaceVariant) }
    if (tag.notes.isBlank()) {
        label()
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tag.notes) } },
            state = rememberTooltipState(),
        ) { label() }
    }
}
