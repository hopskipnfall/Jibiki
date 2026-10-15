package dev.jibiki.yomitan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class YomitanFormatException(message: String) : Exception(message)

@Serializable
data class YomitanIndex(
    val title: String,
    val revision: String,
    val format: Int? = null,
    val version: Int? = null,
    val author: String? = null,
    val url: String? = null,
    val description: String? = null,
    val attribution: String? = null,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val sequenced: Boolean? = null,
    val frequencyMode: String? = null,
) {
    val formatVersion: Int get() = format ?: version ?: 0

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String): YomitanIndex {
            val index = try {
                json.decodeFromString(serializer(), text)
            } catch (e: Exception) {
                throw YomitanFormatException("index.json is not a valid Yomitan index: ${e.message}")
            }
            if (index.formatVersion != 3) {
                throw YomitanFormatException(
                    "Unsupported dictionary format ${index.formatVersion}; only Yomitan format 3 is supported",
                )
            }
            return index
        }
    }
}
