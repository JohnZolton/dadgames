package dadgames.sudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeneratorTest {
    @Test fun puzzlesHaveUniqueSolutionsAndTheRightGrade() {
        for ((d, list) in Fixtures.corpus) for (p in list) {
            val g = p.givens.toIntArray()
            assertEquals("unique ${d.label}", 1, Solver.countSolutions(g))
            assertEquals(p.solution, Solver.solveUnique(g)!!.toList())
            assertTrue(p.givens.withIndex().all { (i, v) -> v == 0 || v == p.solution[i] })
            val grade = LogicSolver.grade(g)
            assertTrue(grade.solved)
            assertEquals("grade of ${d.label}", d, grade.difficulty)
            assertEquals(d, p.difficulty)
        }
    }

    @Test fun easierLevelsHaveMoreClues() {
        val avg = Fixtures.corpus.mapValues { (_, l) -> l.map { it.clues }.average() }
        println("average clues: " + avg.entries.joinToString { "${it.key.label} %.1f".format(it.value) })
        assertTrue(avg[Difficulty.EASY]!! in 30.0..34.0)
        assertTrue(avg[Difficulty.EASY]!! > avg[Difficulty.MEDIUM]!!)
        assertTrue(avg[Difficulty.MEDIUM]!! > avg[Difficulty.HARD]!!)
        Fixtures.corpus.getValue(Difficulty.EASY).forEach { assertTrue(it.clues in 30..34) }
    }

    @Test fun easyNeedsOnlySinglesAndHarderLevelsNeedTheirTechniques() {
        for (p in Fixtures.corpus.getValue(Difficulty.EASY)) assertTrue(LogicSolver.grade(p.givens.toIntArray(), 0).solved)
        for (p in Fixtures.corpus.getValue(Difficulty.MEDIUM)) {
            val g = LogicSolver.grade(p.givens.toIntArray())
            assertTrue(g.counts[1] in 1..Difficulty.MEDIUM_MAX_STEPS && g.level == 1)
        }
        for (p in Fixtures.corpus.getValue(Difficulty.HARD)) {
            val g = LogicSolver.grade(p.givens.toIntArray())
            assertTrue(g.level == 2 || (g.level == 1 && g.counts[1] > Difficulty.MEDIUM_MAX_STEPS))
            assertTrue(LogicSolver.grade(p.givens.toIntArray(), 2).solved)
        }
        for (p in Fixtures.corpus.getValue(Difficulty.EXPERT)) {
            assertEquals(3, LogicSolver.grade(p.givens.toIntArray()).level)
            assertTrue(!LogicSolver.grade(p.givens.toIntArray(), 2).solved)
        }
    }

    @Test fun givensAreSymmetric() {
        // Expert gets a second pass that may break symmetry for a leaner grid.
        for (p in Fixtures.corpus.values.flatten()) if (p.difficulty != Difficulty.EXPERT) for (c in 0 until 81) assertEquals(p.givens[c] == 0, p.givens[80 - c] == 0)
    }

    @Test fun generationIsFast() {
        val rnd = Random(9)
        for (d in Difficulty.entries) {
            val t = System.nanoTime()
            repeat(5) { Generator.generate(d, rnd) }
            val ms = (System.nanoTime() - t) / 5e6
            println("generate ${d.label}: %.0f ms each".format(ms))
            assertTrue("${d.label} too slow: $ms ms", ms < 2000)
        }
    }

    @Test fun dailyIsDeterministicPerDate() {
        val a = Generator.daily("2026-10-11", 7)
        val b = Generator.daily("2026-10-11", 7)
        val c = Generator.daily("2026-10-12", 1)
        assertEquals(a, b)
        assertNotEquals(a.givens, c.givens)
        assertEquals(Difficulty.HARD, a.difficulty)
        assertEquals(Difficulty.EASY, c.difficulty)
        assertEquals(1, Solver.countSolutions(a.givens.toIntArray()))
    }

    /** 12h: engine changes must not silently rewrite past daily puzzles. */
    @Test fun dailyPuzzlesArePinned() {
        val pins = mapOf(
            ("2026-10-11" to 7) to "0190400030000080203009020040070204950000000005240908007006030090602000009000502602197465834765389213589126746378214958914657325243978167426831591652793489831542672",
            ("2026-10-12" to 1) to "0060700309040002700009050013002506002000000080050680026003020000420005030300901005264718399148362758739254613812596472671439584957683126593127841426875937385941260",
            ("2026-10-14" to 3) to "5041000800860004000000400030030100270003070007500206009000800000050003400600038025741392863867524911296485736438159272983671547519246389324817658152763494675938121",
        )
        for ((k, v) in pins) assertEquals("daily ${k.first}", v, Generator.daily(k.first, k.second).encode())
    }

    @Test fun dailyDifficultyCoversTheWeek() {
        assertEquals(listOf("Easy", "Easy", "Medium", "Medium", "Medium", "Hard", "Hard"), (1..7).map { Generator.dailyDifficulty(it).label })
    }

    @Test fun encodeRoundTrip() {
        for (p in Fixtures.corpus.values.flatten()) assertEquals(p, Puzzle.decode(p.encode()))
        assertEquals(null, Puzzle.decode("garbage"))
    }
}
