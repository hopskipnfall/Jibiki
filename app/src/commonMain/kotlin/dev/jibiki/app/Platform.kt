package dev.jibiki.app

import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.font.FontFamily
import dev.jibiki.yomitan.ZipArchive

/** Everything the shared app needs from Android or the browser. */
interface PlatformServices {
    val driver: SQLiteDriver
    val dbName: String
    val filePicker: FilePicker
    val storage: StorageAccess
    val isWeb: Boolean

    /** Where database work runs; Android uses an IO dispatcher because its driver blocks. */
    val dbDispatcher: CoroutineDispatcher get() = Dispatchers.Default

    /** Font family for all text; the web build supplies a bundled Japanese font. */
    val fontFamily: FontFamily get() = FontFamily.Default

    /** Returns false when another tab already owns the database (web only). */
    suspend fun acquireSingleInstance(): Boolean = true

    /** Initial query from the URL (web `#q=`), if any. */
    fun initialQuery(): String? = null

    /** Reflects the current query in the URL (web only). */
    fun publishQuery(query: String) {}

    /** Reloads the app (web: page reload). */
    fun reload() {}
}

interface FilePicker {
    /** Lets the user choose one or more dictionary zips. Empty when cancelled. */
    suspend fun pickZips(): List<PickedFile>
}

interface PickedFile {
    val name: String
    suspend fun open(): ZipArchive

    /** Frees temporary resources (e.g. a cache copy) after import. */
    fun dispose() {}
}

interface StorageAccess {
    /** Whether the browser has granted persistent storage (always true on Android). */
    suspend fun isPersisted(): Boolean = true

    /** Asks for persistent storage; returns the resulting state. */
    suspend fun requestPersist(): Boolean = true

    /** (usedBytes, quotaBytes), or null when unknown. */
    suspend fun estimate(): Pair<Long, Long>? = null
}
