package dev.jibiki.app.render

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.jibiki.model.Glossary
import dev.jibiki.model.ScNode

class RenderContext(
    val dictId: Long,
    val media: MediaLoader,
    val onSearch: (String) -> Unit,
)

private val BLOCK_TAGS = setOf("div", "ul", "ol", "li", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "details", "summary")

private fun isBlock(node: ScNode): Boolean = when (node) {
    is ScNode.Element -> node.tag in BLOCK_TAGS
    is ScNode.Image -> !isInlineImage(node)
    is ScNode.Text -> false
}

private fun isInlineImage(img: ScNode.Image): Boolean =
    img.sizeUnits == "em" || ((img.width ?: 0.0) in 0.1..48.0 && (img.height ?: 0.0) in 0.1..48.0)

/** Renders a definition's glossary entries. Multiple plain entries become a bullet list. */
@Composable
fun GlossaryView(glossary: List<Glossary>, ctx: RenderContext, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val bullets = glossary.size > 1
        for (g in glossary) {
            when (g) {
                is Glossary.Text -> Text(if (bullets) "• ${g.text}" else g.text, style = MaterialTheme.typography.bodyLarge)
                is Glossary.Structured -> ScBlocks(listOf(g.content), ctx)
                is Glossary.Image -> ScImageBlock(g.node, ctx)
                is Glossary.Deinflection -> Text(
                    "→ ${g.uninflected} (${g.rules.joinToString(" « ")})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Lays out a sequence of nodes, grouping consecutive inline nodes into paragraphs. */
@Composable
private fun ScBlocks(nodes: List<ScNode>, ctx: RenderContext, listStyle: String? = null) {
    val groups = remember(nodes) {
        val out = ArrayList<List<ScNode>>()
        var inline = ArrayList<ScNode>()
        for (n in nodes) {
            if (isBlock(n)) {
                if (inline.isNotEmpty()) out += inline.also { inline = ArrayList() }
                out += listOf(n)
            } else {
                inline += n
            }
        }
        if (inline.isNotEmpty()) out += inline
        out
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var itemIndex = 0
        for (group in groups) {
            val single = group.singleOrNull()
            if (single != null && isBlock(single)) {
                if (single is ScNode.Element && single.tag == "li") itemIndex++
                ScBlock(single, ctx, listStyle, itemIndex)
            } else {
                ScParagraph(group, ctx)
            }
        }
    }
}

@Composable
private fun ScBlock(node: ScNode, ctx: RenderContext, listStyle: String?, itemIndex: Int) {
    when (node) {
        is ScNode.Image -> ScImageBlock(node, ctx)
        is ScNode.Text -> ScParagraph(listOf(node), ctx)
        is ScNode.Element -> {
            val margin = Modifier.padding(
                start = ((ScStyle.toEm(node.style["marginLeft"]) ?: 0.0) * 16).dp.coerceAtLeast(0.dp),
                top = ((ScStyle.toEm(node.style["marginTop"]) ?: 0.0) * 16).dp.coerceAtLeast(0.dp),
                bottom = ((ScStyle.toEm(node.style["marginBottom"]) ?: 0.0) * 16).dp.coerceAtLeast(0.dp),
            )
            val spanStyle = ScStyle.span(node.style)
            val textStyle = LocalTextStyle.current.merge(spanStyle)
            androidx.compose.runtime.CompositionLocalProvider(LocalTextStyle provides textStyle) {
                when (node.tag) {
                    "ul", "ol" -> Column(margin.padding(start = 4.dp)) {
                        val type = node.style["listStyleType"] ?: if (node.tag == "ol") "decimal" else "disc"
                        ScBlocks(node.children, ctx, type)
                    }
                    "li" -> Row(margin) {
                        val marker = listMarker(node.style["listStyleType"] ?: listStyle ?: "disc", itemIndex)
                        if (marker.isNotEmpty()) Text(marker, Modifier.padding(end = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) { ScBlocks(node.children, ctx) }
                    }
                    "table" -> ScTable(node, ctx, margin)
                    "details" -> ScDetails(node, ctx, margin)
                    else -> Column(margin) { ScBlocks(node.children, ctx, listStyle) }
                }
            }
        }
    }
}

private fun listMarker(type: String, index: Int): String = when {
    type == "none" -> ""
    type == "decimal" -> "$index."
    type == "circle" -> "◦"
    type == "square" -> "▪"
    type.length >= 2 && (type.first() == '"' || type.first() == '\'') -> type.trim('"', '\'')
    else -> "•"
}

@Composable
private fun ScDetails(node: ScNode.Element, ctx: RenderContext, modifier: Modifier) {
    var open by remember(node) { mutableStateOf(node.attrs["open"] == "true") }
    val summary = node.children.firstOrNull { it is ScNode.Element && it.tag == "summary" } as? ScNode.Element
    Column(modifier) {
        Row(Modifier.clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "▾ " else "▸ ", color = MaterialTheme.colorScheme.primary)
            if (summary != null) ScBlocks(summary.children, ctx) else Text("Details")
        }
        if (open) Column(Modifier.padding(start = 12.dp)) { ScBlocks(node.children.filter { it !== summary }, ctx) }
    }
}

@Composable
private fun ScTable(node: ScNode.Element, ctx: RenderContext, modifier: Modifier) {
    val rows = node.children.flatMap { c ->
        if (c is ScNode.Element && c.tag in setOf("thead", "tbody", "tfoot")) c.children else listOf(c)
    }.filterIsInstance<ScNode.Element>().filter { it.tag == "tr" }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.border(1.dp, line)) {
        for (row in rows) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (cell in row.children.filterIsInstance<ScNode.Element>()) {
                    val span = cell.attrs["colSpan"]?.toFloatOrNull() ?: 1f
                    val header = cell.tag == "th"
                    Column(Modifier.weight(span).fillMaxHeight().border(0.5.dp, line).padding(4.dp)) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalTextStyle provides LocalTextStyle.current.merge(
                                ScStyle.span(cell.style).merge(if (header) SpanStyle(fontWeight = FontWeight.Bold) else SpanStyle()),
                            ),
                        ) { ScBlocks(cell.children, ctx) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScImageBlock(img: ScNode.Image, ctx: RenderContext) {
    val w = img.width?.let { if (img.sizeUnits == "em") it * 16 else it }
    val h = img.height?.let { if (img.sizeUnits == "em") it * 16 else it }
    val mod = if (w != null && h != null) Modifier.widthIn(max = 480.dp).size(w.dp, h.dp) else Modifier.widthIn(max = 320.dp).height(160.dp)
    MediaImage(ctx.dictId, img.path, ctx.media, img.alt ?: img.title, mod)
}

/** One paragraph of inline content: text spans, links, ruby and small images. */
@Composable
private fun ScParagraph(nodes: List<ScNode>, ctx: RenderContext) {
    val primary = MaterialTheme.colorScheme.primary
    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        fun walk(n: ScNode) {
            when (n) {
                is ScNode.Text -> append(n.text)
                is ScNode.Image -> {
                    val id = "img${inline.size}"
                    val wEm = (if (n.sizeUnits == "em") n.width else n.width?.div(16)) ?: 1.0
                    val hEm = (if (n.sizeUnits == "em") n.height else n.height?.div(16)) ?: 1.0
                    inline[id] = InlineTextContent(Placeholder(wEm.em, hEm.em, PlaceholderVerticalAlign.TextCenter)) {
                        MediaImage(ctx.dictId, n.path, ctx.media, n.alt, Modifier.fillMaxWidth().fillMaxHeight())
                    }
                    appendInlineContent(id, n.alt ?: "□")
                }
                is ScNode.Element -> when (n.tag) {
                    "br" -> append('\n')
                    "rt", "rp" -> Unit
                    "ruby" -> {
                        val base = n.children.filterNot { it is ScNode.Element && (it.tag == "rt" || it.tag == "rp") }.joinToString("") { plain(it) }
                        val reading = n.children.filter { it is ScNode.Element && it.tag == "rt" }.joinToString("") { plain(it) }
                        if (reading.isEmpty()) {
                            append(base)
                        } else {
                            val id = "ruby${inline.size}"
                            val width = maxOf(base.length * 1.0, reading.length * 0.55) + 0.1
                            inline[id] = InlineTextContent(Placeholder(width.em, 1.75.em, PlaceholderVerticalAlign.TextBottom)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(reading, style = LocalTextStyle.current.copy(fontSize = LocalTextStyle.current.fontSize * 0.55))
                                    Text(base)
                                }
                            }
                            appendInlineContent(id, base)
                        }
                    }
                    "a" -> {
                        val href = n.attrs["href"].orEmpty()
                        val query = searchQueryFromHref(href)
                        val styles = TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline))
                        val link = when {
                            query != null -> LinkAnnotation.Clickable("q", styles) { ctx.onSearch(query) }
                            href.startsWith("http") -> LinkAnnotation.Url(href, styles)
                            else -> null
                        }
                        if (link != null) withLink(link) { n.children.forEach(::walk) } else n.children.forEach(::walk)
                    }
                    else -> withStyle(ScStyle.span(n.style)) { n.children.forEach(::walk) }
                }
            }
        }
        nodes.forEach(::walk)
    }
    if (text.text.isNotBlank()) Text(text, inlineContent = inline, style = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge))
}

private fun plain(n: ScNode): String = when (n) {
    is ScNode.Text -> n.text
    is ScNode.Element -> n.children.joinToString("") { plain(it) }
    is ScNode.Image -> ""
}

/** Yomitan internal links look like `?query=食べる&wildcards=off`. */
internal fun searchQueryFromHref(href: String): String? {
    if (!href.startsWith("?")) return null
    val param = href.drop(1).split('&').firstOrNull { it.startsWith("query=") } ?: return null
    return percentDecode(param.removePrefix("query="))
}

internal fun percentDecode(s: String): String {
    val bytes = ArrayList<Byte>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%' && i + 2 < s.length) {
            val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                bytes += hex.toByte()
                i += 3
                continue
            }
        }
        if (c == '+') bytes += ' '.code.toByte() else c.toString().encodeToByteArray().forEach { bytes += it }
        i++
    }
    return bytes.toByteArray().decodeToString()
}

