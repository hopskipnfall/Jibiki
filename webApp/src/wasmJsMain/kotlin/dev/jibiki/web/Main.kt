package dev.jibiki.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import dev.jibiki.app.AppGraph
import dev.jibiki.app.JibikiApp
import dev.jibiki.web.resources.Res
import dev.jibiki.web.resources.noto_sans_jp_regular
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.preloadFont

@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    val platform = WebPlatform()
    val graph = AppGraph(platform, MainScope())
    val history = BrowserHistory(graph)
    ComposeViewport {
        val font by preloadFont(Res.font.noto_sans_jp_regular)
        val loaded = font
        if (loaded != null) {
            platform.fontFamily = FontFamily(loaded)
            LaunchedEffect(Unit) { history.attach() }
            JibikiApp(graph, onBack = history::back)
        }
    }
}

/** Mirrors the in-app back stack into browser history so the browser back button works. */
private class BrowserHistory(private val graph: AppGraph) {
    private var depth = 1
    private var expectingPop = false

    suspend fun attach() {
        onPopState { entryDepth ->
            when {
                expectingPop -> expectingPop = false
                entryDepth < depth -> {
                    // Browser Back: drop the screens above the entry we landed on.
                    repeat(depth - entryDepth) { graph.navigator.pop() }
                    depth = graph.navigator.stack.size
                }
                entryDepth > depth -> {
                    // Browser Forward to a screen we no longer have: undo it.
                    expectingPop = true
                    goBack(entryDepth - depth)
                }
            }
        }
        snapshotFlow { graph.navigator.stack.size }.collect { size ->
            when {
                size > depth -> repeat(size - depth) { pushHistory(depth + it + 1) }
                size < depth -> {
                    expectingPop = true
                    goBack(depth - size)
                }
            }
            depth = size
        }
    }

    fun back() {
        graph.navigator.pop()
    }
}
