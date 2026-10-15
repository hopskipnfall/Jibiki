package dev.jibiki.yomitan

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a zip from in-memory entries (String → UTF-8). Used to build invented test dictionaries. */
fun fixtureZip(entries: Map<String, Any>): File {
    val file = File.createTempFile("jibiki-fixture", ".zip")
    file.deleteOnExit()
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(if (content is ByteArray) content else content.toString().encodeToByteArray())
            zip.closeEntry()
        }
    }
    return file
}
