package io.github.dulle88.atrium.util

import kotlin.math.abs

/** Like CSS `text-wrap: balance` for two lines, which Android before 13 lacks. */
object TextBalance {
    /** Extra cost, in characters, of breaking somewhere other than after a sentence's punctuation. */
    private const val MID_SENTENCE_PENALTY = 8

    /** [text] with its middle-most space turned into a line break, preferring a break after `!`, `.`, `?` or `,`. */
    fun twoLines(text: String): String {
        var best = -1
        var bestCost = Int.MAX_VALUE
        for (i in text.indices) {
            if (text[i] != ' ') continue
            val cost = abs(i - (text.length - i - 1)) + if (text[i - 1] in "!.?,") 0 else MID_SENTENCE_PENALTY
            if (cost < bestCost) { best = i; bestCost = cost }
        }
        return if (best <= 0) text else text.substring(0, best) + "\n" + text.substring(best + 1)
    }
}
