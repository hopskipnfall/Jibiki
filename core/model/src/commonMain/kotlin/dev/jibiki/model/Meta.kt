package dev.jibiki.model

sealed interface TermMetaData {
    /** [value] is the numeric rank/occurrence used for sorting; [display] is what to show. */
    data class Frequency(val value: Int?, val display: String, val reading: String?) : TermMetaData

    data class Pitch(val reading: String, val accents: List<PitchAccent>) : TermMetaData
}

/** [downstep] is the mora index after which pitch drops (0 = heiban). */
data class PitchAccent(
    val downstep: Int,
    val nasal: List<Int>,
    val devoice: List<Int>,
    val tags: List<String>,
)
