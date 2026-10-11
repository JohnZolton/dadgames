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
            assertEquals("grade of ${d.label}", d.level, grade.level)
            assertEquals(d, p.difficulty)
        }
    }

    @Test fun easierLevelsHaveMoreClues() {
        val avg = Fixtures.corpus.mapValues { (_, l) -> l.map { it.clues }.average() }
        println("average clues: " + avg.entries.joinToString { "${it.key.label} %.1f".format(it.value) })
        assertTrue(avg[Difficulty.EASY]!! >= 36.0)
        assertTrue(avg[Difficulty.EASY]!! > avg[Difficulty.MEDIUM]!!)
        assertTrue(avg[Difficulty.MEDIUM]!! > avg[Difficulty.HARD]!!)
        Fixtures.corpus.getValue(Difficulty.EASY).forEach { assertTrue(it.clues >= 36) }
    }

    @Test fun easyNeedsOnlySinglesAndHarderLevelsNeedTheirTechniques() {
        for (p in Fixtures.corpus.getValue(Difficulty.EASY)) assertTrue(LogicSolver.grade(p.givens.toIntArray(), 0).solved)
        for (d in listOf(Difficulty.MEDIUM, Difficulty.HARD, Difficulty.EXPERT)) for (p in Fixtures.corpus.getValue(d)) {
            assertTrue("${d.label} solvable at its level", LogicSolver.grade(p.givens.toIntArray(), d.level).solved)
            assertTrue("${d.label} not solvable a level lower", !LogicSolver.grade(p.givens.toIntArray(), d.level - 1).solved)
        }
    }

    @Test fun givensAreSymmetric() {
        for (p in Fixtures.corpus.values.flatten()) for (c in 0 until 81) assertEquals(p.givens[c] == 0, p.givens[80 - c] == 0)
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

    @Test fun dailyDifficultyCoversTheWeek() {
        assertEquals(listOf("Easy", "Easy", "Medium", "Medium", "Medium", "Hard", "Hard"), (1..7).map { Generator.dailyDifficulty(it).label })
    }

    @Test fun encodeRoundTrip() {
        for (p in Fixtures.corpus.values.flatten()) assertEquals(p, Puzzle.decode(p.encode()))
        assertEquals(null, Puzzle.decode("garbage"))
    }
}
