package solitaire.core

import kotlinx.serialization.Serializable

/**
 * Daily challenges: one deal per calendar day, the same for everyone, always Draw 1. The deal is a fair
 * shuffle from a seed made from the date; if the solver can't prove that shuffle winnable, the next seed is
 * tried, so every daily deal can be won.
 */
object Daily {
    /** Months have trophies at 10 and 20 days and for solving every day of the month. */
    val trophySteps = listOf(10, 20)

    fun key(year: Int, month: Int, day: Int): String = "%04d-%02d-%02d".format(year, month, day)

    private fun baseSeed(key: String): Long {
        var h = 0x5D1C_2026_0000_0001L
        for (ch in key) h = (h xor ch.code.toLong()) * 0x100000001B3L
        return h xor (h ushr 31)
    }

    class Deal(val game: Klondike, val attempts: Int)

    /** The winnable deal for a date key ("2026-10-11"). Deterministic. */
    fun deal(key: String, nodeLimit: Int = 150_000): Deal {
        val base = baseSeed(key)
        for (attempt in 0 until 200) {
            val g = Klondike.deal(base + attempt * 7919L, draw = 1, daily = key)
            if (Solver(nodeLimit).solve(g).verdict == Solver.Verdict.WIN) return Deal(g, attempt + 1)
        }
        return Deal(Klondike.deal(base, draw = 1, daily = key), 0)
    }
}

/** Which daily challenges have been solved (date keys). */
@Serializable
data class DailyProgress(val solved: Set<String> = emptySet()) {
    fun solvedIn(year: Int, month: Int): Int = solved.count { it.startsWith("%04d-%02d-".format(year, month)) }
    fun with(key: String) = copy(solved = solved + key)
}
