package dev.jibiki.yomitan

/** Read access to a dictionary zip. Implementations: JavaZipArchive (JVM/Android), web in webApp. */
interface ZipArchive : AutoCloseable {
    val entryNames: List<String>
    suspend fun readText(name: String): String
    suspend fun readBytes(name: String): ByteArray

    /** Reads several entries; implementations may do it in one pass over the archive. */
    suspend fun readMany(names: List<String>): Map<String, ByteArray> = names.associateWith { readBytes(it) }
}
