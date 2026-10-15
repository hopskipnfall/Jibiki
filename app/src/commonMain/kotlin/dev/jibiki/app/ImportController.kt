package dev.jibiki.app

import dev.jibiki.domain.DuplicatePolicy
import dev.jibiki.domain.ImportEvent
import dev.jibiki.yomitan.YomitanFormatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class JobState { QUEUED, RUNNING, DONE, FAILED, SKIPPED }

data class ImportJob(
    val id: Int,
    val fileName: String,
    val title: String? = null,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val detail: String = "Waiting…",
)

/** Asks the user what to do when a dictionary with the same title is already installed. */
class DuplicatePrompt(val title: String, val existingRevisions: List<String>, val newRevision: String) {
    internal val answer = CompletableDeferred<DuplicatePolicy>()
    fun choose(policy: DuplicatePolicy) {
        answer.complete(policy)
    }
}

/** Runs imports one at a time in the application scope so they continue across screens. */
class ImportController(private val graph: AppGraph) {
    private val _jobs = MutableStateFlow<List<ImportJob>>(emptyList())
    val jobs: StateFlow<List<ImportJob>> = _jobs.asStateFlow()

    private val _prompt = MutableStateFlow<DuplicatePrompt?>(null)
    val prompt: StateFlow<DuplicatePrompt?> = _prompt.asStateFlow()

    private val lock = Mutex()
    private var nextId = 1

    val busy: Boolean get() = _jobs.value.any { it.state == JobState.RUNNING || it.state == JobState.QUEUED }

    fun pickAndImport() {
        graph.scope.launch {
            val files = graph.platform.filePicker.pickZips()
            val queued = files.map { it to ImportJob(nextId++, it.name) }
            _jobs.update { it + queued.map { q -> q.second } }
            for ((file, job) in queued) {
                // JSON parsing is CPU-heavy: keep it off the Android main thread.
                lock.withLock { withContext(Dispatchers.Default) { process(file, job.id) } }
            }
        }
    }

    fun dismissFinished() {
        _jobs.update { jobs -> jobs.filter { it.state == JobState.RUNNING || it.state == JobState.QUEUED } }
    }

    private fun update(id: Int, change: (ImportJob) -> ImportJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
    }

    private suspend fun process(file: PickedFile, id: Int) {
        update(id) { it.copy(state = JobState.RUNNING, detail = "Opening…") }
        try {
            runImport(file, id)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Never let one bad file crash the app or stall the queue.
            update(id) { it.copy(state = JobState.FAILED, detail = "Import failed: ${t.message ?: t::class.simpleName}") }
        } finally {
            file.dispose()
            graph.library.notifyChanged()
        }
    }

    private suspend fun runImport(file: PickedFile, id: Int) {
        val zip = try {
            file.open()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update(id) { it.copy(state = JobState.FAILED, detail = "Could not read the file as a zip archive.") }
            return
        }
        zip.use {
            val index = try {
                graph.importer.peek(zip)
            } catch (e: YomitanFormatException) {
                update(id) { it.copy(state = JobState.FAILED, detail = e.message ?: "Not a Yomitan dictionary.") }
                return
            }
            update(id) { it.copy(title = index.title) }
            val existing = graph.importer.existing(index.title)
            val policy = if (existing.isEmpty()) {
                DuplicatePolicy.KEEP_BOTH
            } else {
                val prompt = DuplicatePrompt(index.title, existing.map { it.revision }, index.revision)
                _prompt.value = prompt
                try {
                    prompt.answer.await()
                } finally {
                    _prompt.value = null
                }
            }
            graph.importer.import(zip, policy).collect { event ->
                when (event) {
                    is ImportEvent.Started -> update(id) { it.copy(detail = "Importing ${event.filesTotal} files…") }
                    is ImportEvent.Progress -> update(id) {
                        it.copy(
                            progress = event.filesDone.toFloat() / event.filesTotal.coerceAtLeast(1),
                            detail = "${event.filesDone}/${event.filesTotal} files · ${event.rows} entries",
                        )
                    }
                    is ImportEvent.Finished -> update(id) {
                        it.copy(
                            state = JobState.DONE,
                            progress = 1f,
                            detail = if (event.skipped > 0) "Imported (${event.skipped} malformed rows skipped)" else "Imported",
                        )
                    }
                    is ImportEvent.Skipped -> update(id) { it.copy(state = JobState.SKIPPED, detail = "Skipped — already installed") }
                    is ImportEvent.Failed -> update(id) { it.copy(state = JobState.FAILED, detail = event.message) }
                }
            }
        }
    }
}
