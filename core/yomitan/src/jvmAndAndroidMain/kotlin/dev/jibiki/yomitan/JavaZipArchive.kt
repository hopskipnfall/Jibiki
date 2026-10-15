package dev.jibiki.yomitan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

class JavaZipArchive(file: File) : ZipArchive {
    private val zip = ZipFile(file)

    override val entryNames: List<String> =
        zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()

    override suspend fun readText(name: String): String = readBytes(name).decodeToString()

    override suspend fun readBytes(name: String): ByteArray = withContext(Dispatchers.IO) {
        val entry = zip.getEntry(name) ?: throw YomitanFormatException("Missing file $name")
        zip.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() = zip.close()
}
