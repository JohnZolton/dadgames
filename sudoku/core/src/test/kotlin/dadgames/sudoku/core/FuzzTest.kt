package dadgames.sudoku.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Thousands of random games with random inputs and settings: the invariants always hold. */
class FuzzTest {
    private fun check(g: GameState, o: PlayOptions) {
        val p = g.puzzle
        for (c in 0 until 81) {
            if (p.givens[c] != 0) assertEquals("given changed", p.givens[c], g.values[c])
            assertTrue(g.values[c] in 0..9)
            if (g.values[c] != 0) assertEquals("notes on a filled cell", 0, g.notes[c])
            assertEquals(0, g.notes[c] and Board.ALL.inv())
        }
        if (o.limitActive) assertTrue(g.mistakes <= MISTAKE_LIMIT)
        if (!o.autoCheck) assertEquals(0, g.mistakes)
        assertEquals(g.status == Status.WON, g.values == p.solution)
        assertTrue(g.undo.size <= GameState.UNDO_DEPTH)
        for (d in 1..9) assertTrue(g.remaining(d, o.autoCheck) in 0..9)
    }

    @Test fun randomPlay() {
        val rnd = Random(11)
        val puzzles = Fixtures.corpus.values.flatten()
        var won = 0
        var lost = 0
        repeat(3000) { i ->
            val o = PlayOptions(rnd.nextBoolean(), rnd.nextBoolean(), rnd.nextBoolean())
            var g = GameState(puzzles[i % puzzles.size])
            var steps = 0
            val sol = g.puzzle.solution
            while (g.status == Status.PLAYING) {
                val c = rnd.nextInt(81)
                val before = g
                // After a while the player only makes right moves, so every game reaches an end.
                val r = when (if (steps > 400) 9 else rnd.nextInt(10)) {
                    0, 1 -> g.enter(c, rnd.nextInt(1, 10), o)
                    2, 3, 4 -> g.enter(c, sol[c], o) // mostly-right player so games finish
                    5 -> g.toggleNote(c, rnd.nextInt(1, 10))
                    6 -> g.erase(c, o)
                    7 -> g.undoLast()
                    8 -> if (rnd.nextInt(4) == 0) g.hint(if (rnd.nextBoolean()) c else null, o) else g.enter(c, sol[c], o)
                    else -> g.enter(c, sol[c], o)
                }
                g = r.game
                check(g, o)
                if (r.outcome == Outcome.UNDONE) {
                    // Undo restores exactly the board before the undone input.
                    assertTrue(g.undo.size == before.undo.size - 1)
                }
                if (r.outcome == Outcome.MISTAKE) assertEquals(before.mistakes + 1, g.mistakes)
                if (r.outcome == Outcome.HINT || r.outcome == Outcome.SOLVED && r.hint != null) assertEquals(sol[r.hint!!.cell], g.values[r.hint!!.cell])
                if (g.status == Status.OUT_OF_MISTAKES) {
                    if (rnd.nextBoolean()) { g = g.secondChance(); check(g, o) } else { lost++; break }
                }
                assertTrue("game too long", ++steps < 20_000)
            }
            if (g.status == Status.WON) won++
            // The saved form always restores the same game.
            assertEquals(g, GameState.fromJson(g.toJson()))
        }
        println("fuzz: won $won, gave up $lost of 3000")
        assertTrue(won > 1000)
    }

    @Test fun undoIsExactInverse() {
        val rnd = Random(12)
        val puzzles = Fixtures.corpus.values.flatten()
        repeat(500) { i ->
            val o = PlayOptions(rnd.nextBoolean(), false, rnd.nextBoolean())
            var g = GameState(puzzles[i % puzzles.size])
            val history = mutableListOf(g.values to g.notes)
            repeat(60) {
                val c = rnd.nextInt(81)
                val r = when (rnd.nextInt(3)) {
                    0 -> g.enter(c, rnd.nextInt(1, 10), o)
                    1 -> g.toggleNote(c, rnd.nextInt(1, 10))
                    else -> g.erase(c, o)
                }
                if (r.game.undo.size > g.undo.size && r.game.status == Status.PLAYING) { g = r.game; history += g.values to g.notes }
            }
            while (g.canUndo) {
                history.removeAt(history.lastIndex)
                g = g.undoLast().game
                assertEquals(history.last(), g.values to g.notes)
            }
            assertEquals(1, history.size)
        }
    }
}
