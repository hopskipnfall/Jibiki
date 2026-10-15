package dev.jibiki.japanese

internal data class ConditionDef(val name: String, val isDictionaryForm: Boolean, val subConditions: List<String>?)

internal data class RuleDef(
    val wholeWord: Boolean,
    val inflected: String,
    val deinflected: String,
    val conditionsIn: List<String>,
    val conditionsOut: List<String>,
)

internal data class TransformDef(val name: String, val rules: List<RuleDef>)

/**
 * A candidate dictionary form. [conditions] is a bit set of grammatical conditions the
 * dictionary entry must satisfy (0 = the unmodified input, which matches anything).
 * [reasons] lists transform names from the dictionary form outward, e.g. ["negative", "-た"].
 */
data class Deinflection(val text: String, val conditions: Int, val reasons: List<String>)

/** Port of Yomitan's LanguageTransformer, using the Japanese rule set. */
class Deinflector {
    private class Rule(
        val wholeWord: Boolean,
        val inflected: String,
        val deinflected: String,
        val conditionsIn: Int,
        val conditionsOut: Int,
    )

    private class Transform(val name: String, val rules: List<Rule>)

    private data class Frame(val transform: Int, val rule: Int, val text: String)

    private data class Candidate(val text: String, val conditions: Int, val trace: List<Frame>)

    private val conditionFlags: Map<String, Int>
    private val partOfSpeechFlags: Map<String, Int>
    private val transforms: List<Transform>

    init {
        val flags = HashMap<String, Int>()
        var nextFlag = 0
        var pending = JapaneseTransforms.conditions
        while (pending.isNotEmpty()) {
            val deferred = ArrayList<ConditionDef>()
            for (c in pending) {
                if (c.subConditions == null) {
                    check(nextFlag < 32) { "Too many conditions" }
                    flags[c.name] = 1 shl nextFlag++
                } else if (c.subConditions.all { it in flags }) {
                    flags[c.name] = c.subConditions.fold(0) { acc, s -> acc or flags.getValue(s) }
                } else {
                    deferred += c
                }
            }
            check(deferred.size < pending.size) { "Cycle in condition definitions" }
            pending = deferred
        }
        conditionFlags = flags
        partOfSpeechFlags = JapaneseTransforms.conditions.filter { it.isDictionaryForm }.associate { it.name to flags.getValue(it.name) }
        transforms = JapaneseTransforms.transforms.map { t ->
            Transform(
                t.name,
                t.rules.map { r ->
                    Rule(r.wholeWord, r.inflected, r.deinflected, strictFlags(r.conditionsIn), strictFlags(r.conditionsOut))
                },
            )
        }
    }

    private fun strictFlags(names: List<String>): Int =
        names.fold(0) { acc, n -> acc or (conditionFlags[n] ?: error("Unknown condition $n")) }

    fun deinflect(source: String): List<Deinflection> {
        val results = arrayListOf(Candidate(source, 0, emptyList()))
        var i = 0
        while (i < results.size) {
            val (text, conditions, trace) = results[i++]
            transforms.forEachIndexed { ti, transform ->
                transform.rules.forEachIndexed { ri, rule ->
                    if (!conditionsMatch(conditions, rule.conditionsIn)) return@forEachIndexed
                    val inflected = if (rule.wholeWord) text == rule.inflected else text.endsWith(rule.inflected)
                    if (!inflected) return@forEachIndexed
                    if (trace.any { it.transform == ti && it.rule == ri && it.text == text }) return@forEachIndexed
                    val next = if (rule.wholeWord) rule.deinflected else text.dropLast(rule.inflected.length) + rule.deinflected
                    results += Candidate(next, rule.conditionsOut, listOf(Frame(ti, ri, text)) + trace)
                }
            }
        }
        return results.map { c -> Deinflection(c.text, c.conditions, c.trace.map { transforms[it.transform].name }) }
    }

    /** Condition flags for a dictionary entry's part-of-speech rules (e.g. "v1", "adj-i"). */
    fun flagsForRules(rules: Collection<String>): Int = rules.fold(0) { acc, r -> acc or (partOfSpeechFlags[r] ?: 0) }

    /** Whether a dictionary entry with [termRules] is a valid result for [deinflection]. */
    fun accepts(deinflection: Deinflection, termRules: Collection<String>): Boolean =
        conditionsMatch(deinflection.conditions, flagsForRules(termRules))

    companion object {
        fun conditionsMatch(current: Int, next: Int): Boolean = current == 0 || (current and next) != 0
    }
}
