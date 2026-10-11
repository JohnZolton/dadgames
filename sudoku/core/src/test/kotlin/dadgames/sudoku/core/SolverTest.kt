package dadgames.sudoku.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SolverTest {
    @Test fun geometry() {
        assertEquals(27, Board.units.size)
        Board.units.forEach { u -> assertEquals(9, u.toSet().size) }
        for (c in 0 until 81) {
            assertEquals(20, Board.peers[c].size)
            assertEquals(3, Board.unitsOf[c].size)
            Board.unitsOf[c].forEach { assertTrue(c in Board.units[it]) }
        }
        assertEquals(0, Board.box(0)); assertEquals(4, Board.box(40)); assertEquals(8, Board.box(80)); assertEquals(2, Board.box(8))
        assertTrue(Board.sees(0, 80).not())
        assertTrue(Board.sees(0, 20)) // same box
    }

    @Test fun solvesClassicUniquely() {
        val g = Fixtures.classicGivens.toIntArray()
        assertEquals(1, Solver.countSolutions(g))
        assertArrayEquals(Fixtures.classicSolution.toIntArray(), Solver.solveUnique(g))
    }

    @Test fun detectsMultipleAndNoSolutions() {
        assertEquals(2, Solver.countSolutions(IntArray(81)))
        val bad = Fixtures.classicGivens.toIntArray().also { it[2] = 5 } // two 5s in row 0
        assertEquals(0, Solver.countSolutions(bad))
        assertNull(Solver.solveUnique(IntArray(81)))
    }

    @Test fun randomSolutionsAreValidAndVaried() {
        val rnd = Random(3)
        val seen = HashSet<List<Int>>()
        repeat(200) {
            val s = Solver.randomSolution(rnd)
            assertTrue(s.all { it in 1..9 })
            assertTrue(Board.consistent(s))
            seen += s.toList()
        }
        assertEquals(200, seen.size)
        // Every digit shows up in the top-left cell (no bias to 1).
        val firsts = (0 until 900).map { Solver.randomSolution(rnd)[0] }.toSet()
        assertEquals((1..9).toSet(), firsts)
    }

    @Test fun logicSolvesClassicWithSinglesOnly() {
        val g = LogicSolver.grade(Fixtures.classicGivens.toIntArray())
        assertTrue(g.solved)
        assertEquals(0, g.level)
    }

    @Test fun logicStopsOnEmptyGrid() {
        val g = LogicSolver.grade(IntArray(81))
        assertFalse(g.solved)
    }

    /** Every technique only ever places the right digit and never removes the right digit. */
    @Test fun everyStepIsSoundAndEveryTechniqueIsUsed() {
        val used = HashSet<Technique>()
        val rnd = Random(77)
        val puzzles = Fixtures.corpus.values.flatten().toMutableList()
        // Plus minimal puzzles, which need the harder techniques more often.
        repeat(1500) { Generator.attempt(Difficulty.EXPERT, rnd)?.let { puzzles += it } }
        repeat(600) { Generator.attempt(Difficulty.HARD, rnd)?.let { puzzles += it } }
        for (p in puzzles) {
            val s = LogicSolver(p.givens.toIntArray())
            var steps = 0
            while (!s.solved) {
                val step = s.next() ?: break
                used += step.technique
                if (step.isPlacement) assertEquals("${step.technique} placement", p.solution[step.placeCell], step.placeDigit)
                for ((c, d) in step.eliminations) {
                    assertTrue("${step.technique} eliminated the answer", p.solution[c] != d)
                    assertTrue("${step.technique} eliminated a missing candidate", s.cand[c] and Board.bit(d) != 0)
                }
                assertTrue("${step.technique} made no progress", step.isPlacement || step.eliminations.isNotEmpty())
                s.apply(step)
                assertTrue(++steps < 2000)
            }
            assertTrue(s.solved)
            assertEquals(p.solution, s.values.toList())
        }
        val expected = Technique.entries.toSet() - Technique.NAKED_QUAD // quads are rare; allowed but not required
        assertTrue("unused: ${expected - used}", used.containsAll(expected))
    }

    @Test fun combinations() {
        assertEquals(10, LogicSolver.combinations((1..5).toList(), 3).count())
        assertEquals(listOf(listOf(1, 2), listOf(1, 3), listOf(2, 3)), LogicSolver.combinations(listOf(1, 2, 3), 2).toList())
        assertEquals(0, LogicSolver.combinations(listOf(1), 2).count())
    }
}
