package com.pratul.mmplayer.utils

import java.text.Normalizer

/**
 * Forgiving text matching for search: ignores case, accents and file-name separators, matches
 * word prefixes ("aveng" -> "Avengers"), and tolerates a typo in longer words ("avangers").
 */
object SmartSearch {

    private val MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[._\\-\\[\\](){}]+")
    private val SPACES = Regex("\\s+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(SEPARATORS, " ")
            .replace(SPACES, " ")
            .trim()

    fun tokens(query: String): List<String> = normalize(query).split(' ').filter { it.isNotBlank() }

    /**
     * Score of [text] (already [normalize]d) for [queryTokens]; 0 means no match.
     * Every query word must match something, so results narrow as the user types.
     */
    fun score(queryTokens: List<String>, text: String): Int {
        if (queryTokens.isEmpty() || text.isEmpty()) return 0
        val phrase = queryTokens.joinToString(" ")
        if (text.startsWith(phrase)) return 200
        if (text.contains(phrase)) return 150
        val words = text.split(' ')
        var total = 0
        for (token in queryTokens) {
            total += when {
                words.any { it == token } -> 40
                words.any { it.startsWith(token) } -> 30
                text.contains(token) -> 20
                token.length >= 4 && words.any { closeEnough(token, it) } -> 10
                else -> return 0
            }
        }
        return total
    }

    /** One edit allowed for words of 4–6 letters, two for longer ones; compares against the word's prefix too. */
    private fun closeEnough(token: String, word: String): Boolean {
        val allowed = if (token.length >= 7) 2 else 1
        // Still typing a long word: compare with the same-length start of it ("avang" vs "aveng|ers").
        if (word.length > token.length + allowed) return editDistance(token, word.take(token.length)) <= allowed
        return kotlin.math.abs(word.length - token.length) <= allowed && editDistance(token, word) <= allowed
    }

    private fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return prev[b.length]
    }
}
