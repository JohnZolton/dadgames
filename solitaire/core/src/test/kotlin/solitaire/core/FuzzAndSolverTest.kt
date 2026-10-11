package solitaire.core

import dadgames.cards.standardDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FuzzAndSolverTest {

    private fun invariants(g: Klondike) {
        val all = g.allCards()
        assertEquals("52 cards", 52, all.size)
        assertEquals("no duplicates", standardDeck().toSet(), all.toSet())
        for (f in g.foundations) f.forEachIndexed { i, c -> assertEquals(i + 1, c.rank.value); assertEquals(f[0].suit, c.suit) }
        for (t in 0..6) {
            val col = g.tableau[t]
            assertTrue(g.faceDown[t] >= 0)
            if (col.isEmpty()) assertEquals(0, g.faceDown[t]) else {
                assertTrue("top card face up", g.faceDown[t] < col.size)
                // Face-up part of every column is a run, except the card dealt face up is free.
                assertTrue("face-up run in col $t", g.isRun(t, g.faceDown[t]))
            }
        }
        assertTrue(g.score >= 0)
        assertEquals(g.moves, g.history.size)
    }

    /** Thousands of random games with random moves, undos and clock ticks. */
    @Test fun fuzz() {
        val rnd = Random(2026)
        var wins = 0
        var totalActions = 0
        repeat(3000) { n ->
            var g = Klondike.deal(rnd.nextLong(), draw = if (n % 4 == 0) 3 else 1)
            var clock = 0L
            var actions = 0
            invariants(g)
            while (!g.won && actions < 600) {
                actions++
                val r = rnd.nextInt(100)
                g = when {
                    r < 8 && g.canUndo -> {
                        val before = g.history.size
                        g.undo()!!.also { assertEquals(before - 1, it.history.size) }
                    }
                    r < 12 -> { clock += rnd.nextLong(3000); g.tick(clock) }
                    else -> {
                        // Prefer useful moves so plenty of games get deep; otherwise anything legal.
                        val useful = g.usefulMoves()
                        val m = if (useful.isNotEmpty() && rnd.nextInt(3) > 0) useful.first() else g.legalMoves().randomOrNull(rnd) ?: break
                        val next = g.apply(m)
                        assertTrue("legal move applies: $m", next != null)
                        // Undo restores exactly (except clock-related fields, which don't change here).
                        val back = next!!.undo()
                        if (back != null) assertEquals(g.copy(), back.copy(started = g.started))
                        next
                    }
                }
                invariants(g)
                if (g.canAutoComplete) {
                    while (!g.won) g = g.apply(g.autoCompleteMove()!!)!!
                }
            }
            if (g.won) { wins++; assertEquals(g.score + g.bonus, g.finalScore); assertTrue(g.foundations.all { it.size == 13 }) }
            totalActions += actions
        }
        println("fuzz: 3000 games, $totalActions actions, $wins won")
    }

    @Test fun undoAllTheWayBackRestoresTheDeal() {
        val rnd = Random(5)
        repeat(200) {
            val start = Klondike.deal(rnd.nextLong())
            var g = start
            repeat(150) { g = g.legalMoves().randomOrNull(rnd)?.let { m -> g.apply(m) } ?: g }
            while (g.canUndo) g = g.undo()!!
            assertEquals(start.tableau, g.tableau)
            assertEquals(start.stock, g.stock)
            assertEquals(start.faceDown, g.faceDown)
            assertEquals(0, g.score)
            assertEquals(0, g.moves)
        }
    }

    @Test fun solverSolutionsReplayToAWin() {
        var wins = 0; var losses = 0; var unknown = 0
        val t0 = System.currentTimeMillis()
        for (seed in 1L..200L) {
            val g = Klondike.deal(seed)
            val r = Solver().solve(g)
            when (r.verdict) {
                Solver.Verdict.WIN -> {
                    wins++
                    var x = g
                    for (m in r.moves) x = x.apply(m)!!
                    assertTrue("solution wins seed $seed", x.won)
                }
                Solver.Verdict.LOSS -> losses++
                Solver.Verdict.UNKNOWN -> unknown++
            }
        }
        println("solver draw 1, 200 deals: $wins win, $losses proven loss, $unknown unknown, ${System.currentTimeMillis() - t0} ms")
        assertTrue("most Draw 1 deals are winnable with full knowledge", wins >= 140)
    }

    @Test fun solverDrawThree() {
        var wins = 0
        for (seed in 1L..60L) {
            val g = Klondike.deal(seed, draw = 3)
            val r = Solver().solve(g)
            if (r.verdict == Solver.Verdict.WIN) {
                wins++
                var x = g
                for (m in r.moves) x = x.apply(m)!!
                assertTrue(x.won)
            }
        }
        println("solver draw 3, 60 deals: $wins win")
        assertTrue(wins >= 20)
    }

    @Test fun solverProvesALoss() {
        // A tiny position with the ace stuck under the two: no moves, so the solver must answer LOSS.
        val tiny = Klondike(
            seed = 0, stock = emptyList(), waste = emptyList(), foundations = List(4) { emptyList() },
            tableau = listOf(listOf(dadgames.cards.Card(dadgames.cards.Rank.ACE, dadgames.cards.Suit.CLUBS), dadgames.cards.Card(dadgames.cards.Rank.TWO, dadgames.cards.Suit.CLUBS))) + List(6) { emptyList() },
            faceDown = listOf(1, 0, 0, 0, 0, 0, 0),
        )
        assertEquals(Solver.Verdict.LOSS, Solver().solve(tiny).verdict)
        assertFalse(tiny.won)
    }

    @Test fun dailyDealsAreWinnableAndStable() {
        val t0 = System.currentTimeMillis()
        var maxAttempts = 0
        var days = 0
        for (m in 1..12) for (d in 1..java.time.YearMonth.of(2026, m).lengthOfMonth()) {
            val key = Daily.key(2026, m, d)
            val deal = Daily.deal(key)
            assertTrue("daily $key found", deal.attempts > 0)
            assertEquals(key, deal.game.daily)
            assertEquals(1, deal.game.draw)
            maxAttempts = maxOf(maxAttempts, deal.attempts)
            days++
        }
        val ms = System.currentTimeMillis() - t0
        println("daily: $days days of 2026 all winnable, worst needed $maxAttempts shuffles, ${ms / days} ms per day")
        assertEquals(Daily.deal("2026-10-11").game, Daily.deal("2026-10-11").game)
        assertTrue(Daily.deal("2026-10-11").game != Daily.deal("2026-10-12").game)
    }
}
