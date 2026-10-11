package blocks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class FuzzTest {
    private val games = System.getProperty("games")?.toInt() ?: 3000

    /** Plays one game with [pick] choosing moves; checks invariants after every move. */
    private fun play(start: GameState, maxMoves: Int = Int.MAX_VALUE, pick: (GameState) -> Move): GameState {
        var g = start
        var expectedScore = g.score
        val startBlocks = g.board.count
        while (!g.over && g.moves < maxMoves) {
            assertTrue("game must end", g.moves < 5000)
            val legal = g.playableSlots()
            assertTrue("not over means something fits", legal.isNotEmpty())
            val m = pick(g)
            val shape = Shapes[g.tray[m.slot]]
            val before = g
            val r = g.place(m.slot, m.anchor)!!
            g = r.state
            expectedScore += r.points
            assertEquals(shape.size + Scoring.clearBonus(r.combo, r.streak), r.points)
            assertEquals("score adds up", expectedScore, g.score)
            assertEquals("placed cells were empty", Bits.EMPTY, before.board and r.placed)
            assertEquals(shape.size, r.placed.count)
            // Cells: start + placed - cleared == on board.
            assertEquals(startBlocks + g.blocksPlaced - g.cellsCleared, g.board.count)
            assertTrue("no full region left on the board", Regions.full(g.board).isEmpty())
            assertTrue("gems only sit on blocks", g.board.containsAll(g.gems))
            assertTrue(g.streak == 0 || r.combo > 0)
            assertEquals(if (r.combo > 0) before.streak + 1 else 0, g.streak)
            val pieces = g.tray.count { it >= 0 }
            assertTrue(pieces in 1..3 || g.over)
            if (r.refilled) assertEquals(3, pieces)
            assertEquals(g.over && !g.won, g.playableSlots().isEmpty() && !(g.mode == Mode.DAILY && g.gems.isEmpty))
            if (g.mode == Mode.CLASSIC) assertFalse(g.won)
            // Saved and restored mid-game, the state is identical.
            if (g.moves % 7 == 0) assertEquals(g, GameState.fromJson(g.toJson()))
        }
        return g
    }

    private fun randomPlayer(rnd: Random): (GameState) -> Move = { g ->
        val slot = g.playableSlots().random(rnd)
        Move(slot, Shapes[g.tray[slot]].fits(g.board).random(rnd))
    }

    @Test fun randomClassicGames() {
        val rnd = Random(2026)
        var total = 0L
        var moves = 0L
        repeat(games) {
            val g = play(GameState.newClassic(rnd.nextLong()), pick = randomPlayer(rnd))
            total += g.score; moves += g.moves
        }
        println("random play: $games games, avg score ${total / games}, avg moves ${moves / games}")
    }

    @Test fun botBeatsRandomByFar() {
        val rnd = Random(7)
        val n = 20
        val bot = Bot()
        var botTotal = 0L
        var randTotal = 0L
        var best = 0
        repeat(n) {
            val seed = rnd.nextLong()
            // Capped: an unhurried bot survives thousands of moves.
            val b = play(GameState.newClassic(seed), maxMoves = 300) { bot.choose(it)!! }
            botTotal += b.score; best = maxOf(best, b.score)
            randTotal += play(GameState.newClassic(seed), pick = randomPlayer(rnd)).score
        }
        println("bot avg ${botTotal / n} (best $best) vs random avg ${randTotal / n}")
        assertTrue(botTotal > randTotal * 3)
    }

    @Test fun dailyPuzzles() {
        var day = LocalDate.of(2026, 9, 1)
        var solved = 0
        val n = 90
        val t0 = System.currentTimeMillis()
        repeat(n) {
            val date = day.toString()
            val g = Daily.generate(date)
            assertEquals("deterministic", g, Daily.generate(date))
            assertEquals(Mode.DAILY, g.mode)
            assertEquals(date, g.date)
            assertTrue(g.gemsTotal in 6..10)
            assertTrue(g.board.containsAll(g.gems))
            assertTrue(Regions.full(g.board).isEmpty())
            assertFalse(g.over)
            if (Daily.solvable(g)) solved++
            // Random play on dailies keeps the invariants too.
            play(g, pick = randomPlayer(Random(it)))
            day = day.plusDays(1)
        }
        println("daily: $solved / $n solvable by the bot, ${(System.currentTimeMillis() - t0) / n} ms per day")
        assertEquals(n, solved)
    }
}
