package dadgames.sudoku.core

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Prints what each level asks of the player (the table in README.md) and checks the levels step up. */
class DistributionReport {
    private class Row(var n: Int = 0, var clues: MutableList<Int> = mutableListOf(), val uses: MutableMap<String, Int> = mutableMapOf(), var hardSteps: Int = 0)

    private fun kinds(p: Puzzle): Pair<Set<String>, Int> {
        val s = LogicSolver(p.givens.toIntArray())
        val out = HashSet<String>()
        var hard = 0
        while (!s.solved) {
            val st = s.next()!!
            out += when (st.technique) {
                Technique.HIDDEN_SINGLE -> if (st.unit >= 18) "box single" else "row/col single"
                Technique.NAKED_SINGLE -> "naked single"
                Technique.POINTING, Technique.BOX_LINE -> "pointing/box-line"
                Technique.NAKED_PAIR, Technique.HIDDEN_PAIR -> "pairs"
                Technique.NAKED_TRIPLE, Technique.HIDDEN_TRIPLE -> "triples"
                Technique.X_WING -> "X-wing"
                Technique.XY_WING, Technique.XYZ_WING -> "wings"
                Technique.SWORDFISH, Technique.NAKED_QUAD -> "swordfish/quad"
                Technique.COLORING, Technique.XY_CHAIN -> "colouring/chains"
            }
            if (st.technique.level > 0) hard++
            s.apply(st)
        }
        return out to hard
    }

    @Test fun report() {
        val n = 200
        val rnd = Random(42)
        val cols = listOf("box single", "row/col single", "naked single", "pointing/box-line", "pairs", "triples", "X-wing", "wings", "swordfish/quad", "colouring/chains")
        val rows = Difficulty.entries.associateWith { Row() }
        for (d in Difficulty.entries) repeat(n) {
            val p = Generator.generate(d, rnd)
            val r = rows.getValue(d)
            val (k, hard) = kinds(p)
            r.n++; r.clues += p.clues; r.hardSteps += hard
            for (x in k) r.uses[x] = (r.uses[x] ?: 0) + 1
        }
        val sb = StringBuilder()
        sb.append("| Level | Clues (avg, range) | Non-single steps (avg) | " + cols.joinToString(" | ") + " |\n")
        sb.append("|---|---|---|" + cols.joinToString("") { "---|" } + "\n")
        for ((d, r) in rows) {
            sb.append("| ${d.label} | %.1f (%d-%d) | %.1f | ".format(r.clues.average(), r.clues.min(), r.clues.max(), r.hardSteps.toDouble() / r.n))
            sb.append(cols.joinToString(" | ") { c -> "${(r.uses[c] ?: 0) * 100 / r.n}%" } + " |\n")
        }
        println("DISTRIBUTION ($n puzzles per level; % of puzzles whose solve uses each technique)\n$sb")
        val avg = rows.mapValues { it.value.clues.average() }
        assertTrue(avg[Difficulty.EASY]!! > avg[Difficulty.MEDIUM]!!)
        assertTrue(avg[Difficulty.MEDIUM]!! > avg[Difficulty.HARD]!!)
        assertTrue(avg[Difficulty.HARD]!! > avg[Difficulty.EXPERT]!!)
        val steps = rows.mapValues { it.value.hardSteps.toDouble() / it.value.n }
        assertTrue(steps[Difficulty.MEDIUM]!! < steps[Difficulty.HARD]!!)
    }
}
