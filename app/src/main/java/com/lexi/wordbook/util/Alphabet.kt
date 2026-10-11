package com.lexi.wordbook.util

/**
 * Keeps the A–Z index, grouping and scroll-position logic in one place so the UI layers
 * stay declarative.
 */
object Alphabet {

    val LETTERS: List<String> = ('A'..'Z').map { it.toString() }

    /** Sort/group keys that actually have content, in A–Z then # order. */
    fun activeKeys(words: List<com.lexi.wordbook.data.Word>): List<String> {
        val present = words.map { it.initial }.toSet()
        return LETTERS.filter { it in present } + if ("#" in present) listOf("#") else emptyList()
    }

    fun group(words: List<com.lexi.wordbook.data.Word>): List<Pair<String, List<com.lexi.wordbook.data.Word>>> {
        val byInitial = words.groupBy { it.initial }
        return activeKeys(words).map { key -> key to byInitial.getValue(key) }
    }

    fun counts(words: List<com.lexi.wordbook.data.Word>): Map<String, Int> =
        words.groupingBy { it.initial }.eachCount()
}
