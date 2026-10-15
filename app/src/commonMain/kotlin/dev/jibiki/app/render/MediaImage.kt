package dev.jibiki.app.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Loads dictionary media bytes by (dictId, path). */
fun interface MediaLoader {
    suspend fun load(dictId: Long, path: String): ByteArray?
}

private object PainterCache {
    private const val MAX = 64
    private val map = LinkedHashMap<String, Painter?>()

    fun get(key: String): Painter? = map[key]
    fun contains(key: String) = key in map

    fun put(key: String, painter: Painter?) {
        map.remove(key)
        map[key] = painter
        if (map.size > MAX) map.remove(map.keys.first())
    }
}

// SVG media is not supported yet; it falls back to the placeholder box.
private fun decode(bytes: ByteArray, path: String): Painter? =
    if (path.endsWith(".svg", ignoreCase = true)) null
    else runCatching { BitmapPainter(bytes.decodeToImageBitmap()) }.getOrNull()

@Composable
fun MediaImage(dictId: Long, path: String, loader: MediaLoader, description: String?, modifier: Modifier = Modifier) {
    val key = "$dictId:$path"
    var painter by remember(key) { mutableStateOf(PainterCache.get(key)) }
    var failed by remember(key) { mutableStateOf(PainterCache.contains(key) && PainterCache.get(key) == null) }
    LaunchedEffect(key) {
        if (PainterCache.contains(key)) return@LaunchedEffect
        val p = loader.load(dictId, path)?.let { decode(it, path) }
        PainterCache.put(key, p)
        painter = p
        failed = p == null
    }
    val p = painter
    if (p != null) {
        Image(p, contentDescription = description, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        // Placeholder while loading, or a muted box when the file is missing/undecodable.
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (failed) 0.8f else 0.3f)))
    }
}
