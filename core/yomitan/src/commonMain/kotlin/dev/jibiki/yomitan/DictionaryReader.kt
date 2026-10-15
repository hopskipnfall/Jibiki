package dev.jibiki.yomitan

/** Classifies the files of a Yomitan dictionary zip. */
class DictionaryReader(private val zip: ZipArchive) {
    private val bankPattern = Regex("""^(term_bank|term_meta_bank|kanji_bank|kanji_meta_bank|tag_bank)_(\d+)\.json$""")

    private val banks: Map<String, List<String>> = zip.entryNames
        .mapNotNull { name -> bankPattern.matchEntire(name)?.let { Triple(it.groupValues[1], it.groupValues[2].toInt(), name) } }
        .groupBy({ it.first }) { it }
        .mapValues { (_, list) -> list.sortedBy { it.second }.map { it.third } }

    val termBanks: List<String> get() = banks["term_bank"].orEmpty()
    val termMetaBanks: List<String> get() = banks["term_meta_bank"].orEmpty()
    val kanjiBanks: List<String> get() = banks["kanji_bank"].orEmpty()
    val kanjiMetaBanks: List<String> get() = banks["kanji_meta_bank"].orEmpty()
    val tagBanks: List<String> get() = banks["tag_bank"].orEmpty()

    val mediaFiles: List<String> = zip.entryNames.filter { name ->
        name != "index.json" && !bankPattern.matches(name) && !name.endsWith("/") && !name.endsWith(".json")
    }

    suspend fun index(): YomitanIndex {
        if ("index.json" !in zip.entryNames) {
            throw YomitanFormatException("Not a Yomitan dictionary: index.json is missing")
        }
        return YomitanIndex.parse(zip.readText("index.json"))
    }
}
