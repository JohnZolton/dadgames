package dadgames.sudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private val on = PlayOptions()
    private fun game() = GameState(Fixtures.classic)
    private val sol = Fixtures.classicSolution
    private val empties = (0 until 81).filter { Fixtures.classicGivens[it] == 0 }
    private val c0 = empties.first() // cell 2, answer 4
    private fun wrong(c: Int) = (1..9).first { it != sol[c] }

    @Test fun correctEntry() {
        val r = game().enter(c0, sol[c0], on)
        assertEquals(Outcome.PLACED, r.outcome)
        assertEquals(sol[c0], r.game.values[c0])
        assertEquals(0, r.game.mistakes)
        assertEquals(1, r.game.moves)
    }

    @Test fun givensCannotChange() {
        val g = game()
        assertEquals(Outcome.LOCKED, g.enter(0, 1, on).outcome)
        assertEquals(Outcome.LOCKED, g.erase(0, on).outcome)
        assertEquals(Outcome.NOTHING, g.toggleNote(0, 1).outcome)
        assertEquals(Outcome.LOCKED, g.enter(0, 1, on.copy(autoCheck = false)).outcome)
    }

    @Test fun rightAnswersLockOnlyWithAutoCheck() {
        val g = game().enter(c0, sol[c0], on).game
        assertEquals(Outcome.LOCKED, g.enter(c0, wrong(c0), on).outcome)
        assertEquals(Outcome.LOCKED, g.erase(c0, on).outcome)
        val off = on.copy(autoCheck = false)
        assertEquals(Outcome.ERASED, g.erase(c0, off).outcome)
    }

    @Test fun mistakesCountAndLimitEndsGame() {
        var g = game()
        for (i in 0 until 2) {
            val r = g.enter(empties[i], wrong(empties[i]), on)
            assertEquals(Outcome.MISTAKE, r.outcome)
            g = r.game
        }
        assertEquals(2, g.mistakes)
        assertTrue(g.isWrong(empties[0]))
        // A wrong number can be replaced.
        g = g.enter(empties[0], sol[empties[0]], on).game
        val r = g.enter(empties[5], wrong(empties[5]), on)
        assertEquals(Outcome.OUT_OF_MISTAKES, r.outcome)
        assertEquals(Status.OUT_OF_MISTAKES, r.game.status)
        assertEquals(Outcome.NOTHING, r.game.enter(empties[6], sol[empties[6]], on).outcome)
        // Second chance: last move taken back, one mistake to spare.
        val sc = r.game.secondChance()
        assertEquals(Status.PLAYING, sc.status)
        assertEquals(2, sc.mistakes)
        assertEquals(0, sc.values[empties[5]])
    }

    @Test fun takeItBackClearsEarlierWrongNumberInThatSquare() {
        val c = empties[0]
        val wrongs = (1..9).filter { it != sol[c] }.take(3)
        var g = game().toggleNote(c, 4).game
        for (w in wrongs) g = g.enter(c, w, on).game // three mistakes in the same square
        assertEquals(Status.OUT_OF_MISTAKES, g.status)
        val sc = g.secondChance()
        assertEquals("square is back to before the mistakes", 0, sc.values[c])
        assertEquals(Board.bit(4), sc.notes[c])
        assertEquals(2, sc.mistakes)
        assertEquals(Status.PLAYING, sc.status)
    }

    @Test fun noLimitWhenTurnedOff() {
        var g = game()
        val o = on.copy(mistakeLimit = false)
        for (i in 0 until 6) g = g.enter(empties[i], wrong(empties[i]), o).game
        assertEquals(6, g.mistakes)
        assertEquals(Status.PLAYING, g.status)
    }

    @Test fun autoCheckOffCountsNothingAndRevealsOnlyWhenFull() {
        val o = on.copy(autoCheck = false)
        var g = game()
        val r = g.enter(c0, wrong(c0), o)
        assertEquals(Outcome.PLACED, r.outcome)
        assertEquals(0, r.game.mistakes)
        g = r.game
        for (c in empties.drop(1)) g = g.enter(c, sol[c], o).game
        assertTrue(g.revealWrong)
        assertEquals(Status.PLAYING, g.status)
        val fixed = g.enter(c0, sol[c0], o)
        assertEquals(Outcome.SOLVED, fixed.outcome)
        assertEquals(Status.WON, fixed.game.status)
    }

    @Test fun solvingWins() {
        var g = game()
        var last: MoveResult? = null
        for (c in empties) { last = g.enter(c, sol[c], on); g = last.game }
        assertEquals(Outcome.SOLVED, last!!.outcome)
        assertEquals(Status.WON, g.status)
        assertFalse(g.canUndo)
    }

    @Test fun notesToggleAndOnlyOnEmptyCells() {
        var g = game().toggleNote(c0, 3).game.toggleNote(c0, 7).game
        assertEquals(Board.bit(3) or Board.bit(7), g.notes[c0])
        g = g.toggleNote(c0, 3).game
        assertEquals(Board.bit(7), g.notes[c0])
        g = g.enter(c0, sol[c0], on).game
        assertEquals(0, g.notes[c0])
        assertEquals(Outcome.NOTHING, g.toggleNote(c0, 1).outcome)
    }

    @Test fun placingRemovesNotesFromPeersAndUndoBringsThemBack() {
        val d = sol[c0]
        val peer = Board.peers[c0].first { Fixtures.classicGivens[it] == 0 }
        val far = empties.first { !Board.sees(it, c0) && it != c0 }
        var g = game().toggleNote(peer, d).game.toggleNote(far, d).game
        val before = g
        g = g.enter(c0, d, on).game
        assertEquals(0, g.notes[peer])
        assertEquals(Board.bit(d), g.notes[far])
        assertEquals(before.values, g.undoLast().game.values)
        assertEquals(before.notes, g.undoLast().game.notes)
        // Option off: notes stay.
        val keep = before.enter(c0, d, on.copy(autoRemoveNotes = false)).game
        assertEquals(Board.bit(d), keep.notes[peer])
    }

    @Test fun eraseClearsValueOrNotes() {
        var g = game().enter(c0, wrong(c0), on).game
        g = g.erase(c0, on).game
        assertEquals(0, g.values[c0])
        assertEquals(1, g.mistakes) // mistakes stay
        g = g.toggleNote(c0, 2).game.erase(c0, on).game
        assertEquals(0, g.notes[c0])
        assertEquals(Outcome.NOTHING, g.erase(c0, on).outcome)
    }

    @Test fun undoWalksBackEverything() {
        var g = game()
        val states = mutableListOf(g)
        g = g.toggleNote(c0, 1).game; states += g
        g = g.enter(empties[1], sol[empties[1]], on).game; states += g
        g = g.enter(c0, wrong(c0), on).game; states += g
        g = g.erase(c0, on).game; states += g
        for (i in states.size - 2 downTo 0) {
            g = g.undoLast().game
            assertEquals(states[i].values, g.values)
            assertEquals(states[i].notes, g.notes)
        }
        assertFalse(g.canUndo)
        assertEquals(1, g.mistakes)
    }

    @Test fun remainingCounts() {
        val g = game()
        assertEquals(9 - Fixtures.classicGivens.count { it == 5 }, g.remaining(5, true))
        val c = empties.first { sol[it] == 5 }
        val g2 = g.enter(c, 5, on).game
        assertEquals(g.remaining(5, true) - 1, g2.remaining(5, true))
        // A wrong 5 doesn't count when auto-check is on (it would give nothing away anyway).
        val w = empties.first { sol[it] != 5 && Board.peers[it].none { p -> g.values[p] == 5 } }
        assertEquals(g.remaining(5, true), g.enter(w, 5, on).game.remaining(5, true))
        assertEquals(g.remaining(5, false) - 1, g.enter(w, 5, on.copy(autoCheck = false)).game.remaining(5, false))
    }

    @Test fun conflictsFound() {
        // Put a 5 in row 0 where a 5 already is.
        val g = game().enter(2, 5, on).game
        assertTrue(0 in g.conflicts())
        assertTrue(2 in g.conflicts())
        assertTrue(game().conflicts().isEmpty())
    }

    @Test fun hintFillsRightCellWithReason() {
        val r = game().hint(null, on)
        val h = assertNotNull(r.hint).let { r.hint!! }
        assertEquals(sol[h.cell], h.digit)
        assertEquals(h.digit, r.game.values[h.cell])
        assertEquals(1, r.game.hints)
        assertEquals(0, r.game.mistakes)
        assertTrue(h.reason, h.reason.contains("${h.digit}"))
        assertTrue(h.focus.contains(h.cell))
    }

    @Test fun hintPrefersSelectedCellWhenItIsASingle() {
        // Cell 2 (row 0) in the classic puzzle: hidden or naked single available right away?
        val s = LogicSolver(Fixtures.classicGivens.toIntArray())
        val cell = (0 until 81).first { Fixtures.classicGivens[it] == 0 && Board.count(s.cand[it]) == 1 }
        val r = game().hint(cell, on)
        assertEquals(cell, r.hint!!.cell)
    }

    @Test fun hintFixesWrongNumberFirst() {
        val g = game().enter(c0, wrong(c0), on).game
        val r = g.hint(null, on)
        assertTrue(r.hint!!.fixed)
        assertEquals(c0, r.hint!!.cell)
        assertEquals(sol[c0], r.game.values[c0])
        assertTrue(r.hint!!.reason.startsWith("This ${wrong(c0)} isn't right"))
    }

    @Test fun hintsAloneSolveEveryCorpusPuzzle() {
        for (p in Fixtures.corpus.values.flatten()) {
            var g = GameState(p)
            var n = 0
            while (g.status == Status.PLAYING) {
                val r = g.hint(null, on)
                assertTrue(r.outcome == Outcome.HINT || r.outcome == Outcome.SOLVED)
                assertFalse(r.hint!!.reason.contains("deep thinking"))
                g = r.game
                assertTrue(++n <= 81)
            }
            assertEquals(Status.WON, g.status)
            assertEquals(0, g.mistakes)
        }
    }

    @Test fun hintReasonsArePlainEnglish() {
        val single = Step(Technique.HIDDEN_SINGLE, 5, 4, unit = 2)
        assertEquals("This is the only place for 4 in row 3.", Hints.placementText(single))
        assertEquals("This is the only place for 4 in the top-left box.", Hints.placementText(Step(Technique.HIDDEN_SINGLE, 5, 4, unit = 18)))
        assertEquals("This is the only place for 4 in column 9.", Hints.placementText(Step(Technique.HIDDEN_SINGLE, 5, 4, unit = 17)))
        assertTrue(Hints.placementText(Step(Technique.NAKED_SINGLE, 5, 4)).startsWith("Only 4 fits here"))
        val pair = Step(Technique.NAKED_PAIR, eliminations = listOf(3 to 7), unit = 22, cells = listOf(30, 31), digits = listOf(3, 7))
        assertEquals("Two shaded squares in the centre box can only hold 3 or 7, so the rest of it can't.", Hints.eliminationText(pair))
        val pointing = Step(Technique.POINTING, eliminations = listOf(5 to 8), unit = 18, cells = listOf(0, 1), digits = listOf(8), lines = listOf(0))
        assertEquals("In the top-left box, the 8 must go in row 1, so the rest of row 1 can't be 8.", Hints.eliminationText(pointing))
        val xwing = Step(Technique.X_WING, eliminations = listOf(5 to 8), cells = listOf(10, 16, 46, 52), digits = listOf(8), lines = listOf(1, 5))
        assertEquals("In rows 2 and 6, the 8 must go in columns 2 and 8, so the rest of those can't be 8.", Hints.eliminationText(xwing))
        assertEquals("${Hints.eliminationText(pair)} So this is the only place for 4 in row 3.", Hints.explain(single, listOf(pair)))
        assertTrue(Hints.explain(single, listOf(pointing, pair)).contains("With one more step, this is the only place"))
        // The hardest step is the one explained.
        assertTrue(Hints.explain(single, listOf(pair, xwing, pointing)).startsWith("In rows 2 and 6"))
    }

    /** The message area under the board fits about 170 characters at 16sp on his 360 dp screen (and shrinks to fit beyond that). */
    @Test fun hintReasonsFitTheMessageArea() {
        // Worst cases: longest unit names (bottom-middle box, column 9) and the most digits.
        val bm = 25
        val steps = listOf(
            Step(Technique.POINTING, unit = bm, cells = listOf(66, 67), digits = listOf(9), lines = listOf(17)),
            Step(Technique.BOX_LINE, unit = 17, cells = listOf(62, 71), digits = listOf(9), lines = listOf(26)),
            Step(Technique.NAKED_PAIR, unit = bm, cells = listOf(66, 67), digits = listOf(8, 9)),
            Step(Technique.NAKED_TRIPLE, unit = bm, cells = listOf(66, 67, 68), digits = listOf(7, 8, 9)),
            Step(Technique.NAKED_QUAD, unit = bm, cells = listOf(66, 67, 68, 75), digits = listOf(6, 7, 8, 9)),
            Step(Technique.HIDDEN_PAIR, unit = bm, cells = listOf(66, 67), digits = listOf(8, 9)),
            Step(Technique.HIDDEN_TRIPLE, unit = bm, cells = listOf(66, 67, 68), digits = listOf(7, 8, 9)),
            Step(Technique.X_WING, cells = listOf(15, 17, 69, 71), digits = listOf(9), lines = listOf(1, 7)),
            Step(Technique.SWORDFISH, cells = listOf(15, 17, 42, 44, 69, 71), digits = listOf(9), lines = listOf(1, 4, 7)),
            Step(Technique.XY_WING, cells = listOf(0, 1, 9), digits = listOf(9)),
            Step(Technique.XYZ_WING, cells = listOf(0, 1, 9), digits = listOf(9)),
            Step(Technique.COLORING, cells = listOf(0, 1, 9, 10), digits = listOf(9)),
            Step(Technique.XY_CHAIN, cells = listOf(0, 1, 9), digits = listOf(9)),
        )
        val places = listOf(Step(Technique.HIDDEN_SINGLE, 70, 9, unit = bm), Step(Technique.NAKED_SINGLE, 70, 9))
        var longest = ""
        for (st in steps) for (pl in places) for (n in 1..3) {
            val s = Hints.explain(pl, List(n) { st })
            if (s.length > longest.length) longest = s
        }
        println("longest hint (${longest.length}): $longest")
        assertTrue(longest.length <= 175)
    }

    /** 12f: every step a hint explains is needed for its answer, and the answer follows from them. */
    @Test fun hintStepsAreAllNeeded() {
        var multi = 0
        var withSteps = 0
        for (p in Fixtures.corpus.values.flatten()) {
            var g = GameState(p)
            while (g.status == Status.PLAYING) {
                val r = g.hint(null, on)
                val h = r.hint!!
                val base = Board.candidates(g.values.toIntArray())
                if (h.target != null) {
                    assertTrue("answer follows", Hints.follows(h.steps, h.target!!, base))
                    for (i in h.steps.indices) {
                        assertFalse("step $i (${h.steps[i].technique}) not needed", Hints.follows(h.steps.filterIndexed { j, _ -> j != i }, h.target!!, base))
                    }
                    if (h.steps.isNotEmpty()) {
                        withSteps++
                        val hardest = h.steps.maxOf { it.technique.level }
                        val key = h.steps.last { it.technique.level == hardest }
                        assertTrue(h.reason.startsWith(Hints.eliminationText(key)))
                        assertEquals(key.cells, h.pattern)
                    }
                    if (h.steps.size > 1) multi++
                }
                g = r.game
            }
        }
        println("hints with reasoning steps: $withSteps, with more than one: $multi")
        assertTrue(withSteps > 50)
    }

    @Test fun jsonRoundTripKeepsEverything() {
        var g = game().toggleNote(c0, 4).game.enter(empties[1], wrong(empties[1]), on).game.withTime(123_456)
        g = g.copy(daily = "2026-10-11")
        val back = GameState.fromJson(g.toJson())
        assertEquals(g, back)
    }

    @Test fun nothingHappensForBadInput() {
        val g = game()
        assertSame(g, g.enter(c0, 0, on).game)
        assertSame(g, g.enter(c0, 10, on).game)
        assertSame(g, g.undoLast().game)
    }
}
