package dev.jibiki.web

import androidx.compose.ui.text.font.FontFamily
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import dev.jibiki.app.FilePicker
import dev.jibiki.app.PickedFile
import dev.jibiki.app.PlatformServices
import dev.jibiki.app.StorageAccess
import dev.jibiki.yomitan.ZipArchive
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.File

class WebPlatform : PlatformServices {
    // Created on first use, i.e. after acquireSingleInstance() holds the tab lock, so a second
    // tab never starts a worker that competes for the OPFS access handles.
    override val driver: SQLiteDriver by lazy { WebWorkerSQLiteDriver(createSqliteWorker()) }
    override val dbName = "jibiki.db"
    override val isWeb = true
    override var fontFamily: FontFamily = FontFamily.Default

    override val filePicker = object : FilePicker {
        override suspend fun pickZips(): List<PickedFile> {
            val files = pickFiles(".zip,application/zip").await<JsArray<File>>()
            return (0 until files.length).map { WebPickedFile(files[it]!!) }
        }
    }

    override val storage = object : StorageAccess {
        override suspend fun isPersisted() = storagePersisted().await<JsBoolean>().toBoolean()
        override suspend fun requestPersist() = storagePersist().await<JsBoolean>().toBoolean()
        override suspend fun estimate(): Pair<Long, Long>? {
            val r = storageEstimate().await<JsArray<JsNumber>?>() ?: return null
            return r[0]!!.toDouble().toLong() to r[1]!!.toDouble().toLong()
        }
    }

    override suspend fun acquireSingleInstance(): Boolean = acquireAppLock().await<JsBoolean>().toBoolean()

    override fun initialQuery(): String? = readHashQuery().ifEmpty { null }

    override fun publishQuery(query: String) = writeHashQuery(query)

    override fun reload() = reloadPage()
}

private class WebPickedFile(private val file: File) : PickedFile {
    override val name: String = fileName(file)

    override suspend fun open(): ZipArchive = FflateZipArchive(zipOpen(fileArrayBuffer(file).await()))
}

private fun Uint8Array.toByteArray(): ByteArray = ByteArray(length) { this[it] }

/** Zip reader over an in-memory ArrayBuffer, backed by fflate. */
private class FflateZipArchive(private val handle: JsAny) : ZipArchive {
    override val entryNames: List<String> = zipNames(handle).let { a -> (0 until a.length).map { a[it].toString() } }

    override suspend fun readText(name: String): String = zipReadText(handle, name)

    override suspend fun readBytes(name: String): ByteArray = zipReadBytes(handle, name).toByteArray()

    override suspend fun readMany(names: List<String>): Map<String, ByteArray> {
        val arr = JsArray<JsString>()
        names.forEachIndexed { i, n -> arr[i] = n.toJsString() }
        val out = zipReadMany(handle, arr)
        return names.indices.associate { names[it] to out[it]!!.toByteArray() }
    }

    override fun close() {}
}
