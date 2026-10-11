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

class PinnedAndLimitsTest {
    /** Past daily deals must never change: pin a few dates to their exact deal. [BEST_PRACTICES #12h] */
    @Test fun dailyDealsArePinned() {
        val pins = mapOf(
            "2026-10-11" to PIN_1011,
            "2026-01-01" to PIN_0101,
            "2027-02-28" to PIN_0228,
        )
        for ((key, want) in pins) {
            val g = Daily.deal(key).game
            val got = g.tableau.joinToString("|") { col -> col.joinToString(",") } + "/" + g.stock.joinToString(",")
            assertEquals("daily $key", want, got)
        }
    }

    /** Hint text fits two lines of the hint banner on the 360 dp screen. [BEST_PRACTICES #12c] */
    @Test fun hintTextIsShort() {
        val rnd = Random(9)
        var longest = ""
        repeat(400) {
            var g = Klondike.deal(rnd.nextLong(), if (it % 2 == 0) 1 else 3)
            repeat(200) {
                g.hint()?.let { h -> if (h.text.length > longest.length) longest = h.text }
                g = g.legalMoves().randomOrNull(rnd)?.let { m -> g.apply(m) } ?: g
            }
        }
        println("longest hint (${longest.length}): $longest")
        assertTrue(longest.length <= 72)
    }
}

private const val PIN_1011 = "J♦|A♥,10♠|6♣,K♠,2♥|4♥,6♥,4♦,3♥|5♣,4♣,K♥,8♦,Q♦|A♠,10♣,3♦,A♣,2♦,J♥|9♥,2♠,3♠,K♣,5♦,7♣,Q♥/7♦,3♣,4♠,Q♠,9♦,10♥,8♥,8♣,7♥,A♦,Q♣,7♠,9♣,J♣,2♣,5♠,10♦,K♦,9♠,6♦,6♠,J♠,8♠,5♥"
private const val PIN_0101 = "7♠|4♦,6♥|J♥,9♠,8♦|6♣,3♠,7♥,K♠|8♣,5♠,J♦,J♣,J♠|5♣,Q♥,4♣,6♦,K♦,5♦|5♥,10♥,3♥,9♥,8♠,Q♠,4♠/10♣,K♥,6♠,10♠,K♣,2♣,4♥,2♥,Q♣,9♦,A♣,8♥,Q♦,7♣,2♠,7♦,3♣,3♦,A♠,10♦,2♦,9♣,A♦,A♥"
private const val PIN_0228 = "3♣|7♥,Q♣|5♠,7♠,3♥|J♣,6♥,6♠,4♣|5♣,Q♥,9♣,J♠,2♥|3♠,10♠,Q♦,8♣,7♣,9♠|8♠,10♥,6♣,5♦,J♥,4♦,3♦/K♥,Q♠,6♦,K♣,A♥,4♠,A♠,10♣,7♦,K♦,8♥,K♠,10♦,4♥,A♣,2♣,2♠,A♦,2♦,9♦,8♦,9♥,J♦,5♥"
