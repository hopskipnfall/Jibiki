package dev.jibiki.model

data class Profile(val id: Long, val name: String, val order: Int) {
    companion object {
        const val ENGLISH = 1L
        const val JAPANESE = 2L
    }
}

data class ProfileEntry(val dict: DictionaryInfo, val priority: Int, val enabled: Boolean)
