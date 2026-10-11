package solitaire.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What do wins score? Replays solver wins through the real engine with the clock spread evenly so each
 * game takes a set time, and prints the result next to dad's records (his ~2 minute wins scored 5195-5371).
 */
class ScoringCheck {
    @Test fun winsAtDadsPace() {
        val rows = mutableListOf<String>()
        for (target in listOf(90L, 120L, 140L)) {
            val finals = mutableListOf<Int>()
            val bases = mutableListOf<Int>()
            val moves = mutableListOf<Int>()
            val recycles = mutableListOf<Int>()
            var seed = 1L
            while (finals.size < 30) {
                val g0 = Klondike.deal(seed++)
                val r = Solver().solve(g0)
                if (r.verdict != Solver.Verdict.WIN) continue
                var g = g0
                r.moves.forEachIndexed { i, m ->
                    g = g.tick((i + 1) * target * 1000 / r.moves.size - 1).apply(m)!!
                }
                assertTrue(g.won)
                finals += g.finalScore; bases += g.score; moves += g.moves; recycles += g.recycles
            }
            rows += "${clock(target)} win: final ${finals.min()}..${finals.max()} (avg ${finals.average().toInt()}), " +
                "points before bonus avg ${bases.average().toInt()}, bonus avg ${(finals.average() - bases.average()).toInt()}, moves avg ${moves.average().toInt()}, " +
                "stock turned over avg ${"%.1f".format(recycles.average())} times (-200 each)"
        }
        rows.forEach { println("scoring: $it") }
    }
}
