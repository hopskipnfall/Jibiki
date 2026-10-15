package dev.jibiki.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.jibiki.app.JibikiApp
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var pendingPick: CompletableDeferred<List<Uri>>? = null

    private val openDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingPick?.complete(uris)
        pendingPick = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as JibikiApplication
        app.platform.filePicker.launch = { result ->
            pendingPick?.complete(emptyList())
            pendingPick = result
            openDocuments.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        val graph = app.graph
        setContent {
            BackHandler(enabled = graph.navigator.canGoBack) { graph.navigator.pop() }
            JibikiApp(graph)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) (application as JibikiApplication).platform.filePicker.launch = null
    }
}
