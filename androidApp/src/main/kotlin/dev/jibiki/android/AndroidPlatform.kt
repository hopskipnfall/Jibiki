package dev.jibiki.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.jibiki.app.FilePicker
import dev.jibiki.app.PickedFile
import dev.jibiki.app.PlatformServices
import dev.jibiki.app.StorageAccess
import dev.jibiki.yomitan.JavaZipArchive
import dev.jibiki.yomitan.ZipArchive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidPlatform(private val context: Context) : PlatformServices {
    override val driver: SQLiteDriver = BundledSQLiteDriver()
    override val dbName: String = context.getDatabasePath("jibiki.db").apply { parentFile?.mkdirs() }.absolutePath
    override val isWeb = false
    override val dbDispatcher: CoroutineDispatcher = Dispatchers.IO
    override val filePicker = AndroidFilePicker(context)

    override val storage = object : StorageAccess {
        override suspend fun estimate(): Pair<Long, Long> = withContext(Dispatchers.IO) {
            val dir = File(dbName).parentFile!!
            val used = dir.listFiles()?.sumOf { it.length() } ?: 0L
            used to (used + dir.usableSpace)
        }
    }
}

/** Storage Access Framework picker; the current activity supplies [launch]. */
class AndroidFilePicker(private val context: Context) : FilePicker {
    var launch: ((CompletableDeferred<List<Uri>>) -> Unit)? = null

    override suspend fun pickZips(): List<PickedFile> {
        val launcher = launch ?: return emptyList()
        val result = CompletableDeferred<List<Uri>>()
        withContext(Dispatchers.Main) { launcher(result) }
        return result.await().map { AndroidPickedFile(context, it) }
    }
}

private class AndroidPickedFile(private val context: Context, private val uri: Uri) : PickedFile {
    private var copy: File? = null

    override val name: String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "dictionary.zip"

    /** ZipFile needs random access, so the document is copied to the cache first. */
    override suspend fun open(): ZipArchive = withContext(Dispatchers.IO) {
        val file = File.createTempFile("import-", ".zip", context.cacheDir)
        copy = file
        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        JavaZipArchive(file)
    }

    override fun dispose() {
        copy?.delete()
        copy = null
    }
}
