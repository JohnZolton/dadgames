package solitaire.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * A bot that only ever follows the hint button, across thousands of deals. Following hints must never visit
 * the same position twice (no going round in circles), and must end in a win, auto-complete, or an honest
 * "No moves left". [review: hint loop on the Sept 8 daily]
 */
class HintBotTest {
    private fun Klondike.key(): String =
        tableau.joinToString("|") { it.joinToString(",") } + "/" + faceDown + "/" + stock + "/" + waste + "/" + foundations.map { it.size }

    enum class End { WON, AUTO, STUCK }

    private fun followHints(start: Klondike, label: String): Pair<End, Int> {
        var g = start
        val seen = HashSet<String>()
        var steps = 0
        while (true) {
            if (g.won) return End.WON to steps
            if (g.canAutoComplete) {
                while (!g.won) g = g.apply(g.autoCompleteMove() ?: fail("$label: auto-complete stalled").let { error("") })!!
                return End.AUTO to steps
            }
            if (!seen.add(g.key())) fail("$label: hints went round in circles after $steps moves (last: ${g.history.lastOrNull()?.move})")
            val h = g.hint()
            if (h == null) {
                assertTrue(g.noMovesLeft())
                return End.STUCK to steps
            }
            g = g.apply(h.move) ?: fail("$label: hint suggested an illegal move ${h.move}").let { error("") }
            steps++
            assertTrue("$label: too long", steps < 3000)
        }
    }

    @Test fun reviewCases() {
        followHints(Daily.deal("2026-09-08").game, "daily 2026-09-08")
        followHints(Klondike.deal(5), "seed 5")
        followHints(Klondike.deal(24), "seed 24")
    }

    @Test fun thousandsOfDeals() {
        for (draw in listOf(1, 3)) {
            val rnd = Random(77L + draw)
            val ends = HashMap<End, Int>()
            repeat(2500) { n ->
                val seed = if (n < 800) n.toLong() else rnd.nextLong()
                val (end, _) = followHints(Klondike.deal(seed, draw), "draw $draw seed $seed")
                ends[end] = (ends[end] ?: 0) + 1
            }
            println("hint bot, draw $draw, 2500 deals: $ends")
        }
    }

    @Test fun bringDownThenNotStraightBackUp() {
        val g = Klondike(
            seed = 0, stock = emptyList(), waste = emptyList(),
            foundations = listOf(
                listOf(dadgames.cards.Card(dadgames.cards.Rank.ACE, dadgames.cards.Suit.SPADES), dadgames.cards.Card(dadgames.cards.Rank.TWO, dadgames.cards.Suit.SPADES), dadgames.cards.Card(dadgames.cards.Rank.THREE, dadgames.cards.Suit.SPADES)),
                emptyList(), emptyList(), emptyList(),
            ),
            tableau = listOf(
                listOf(dadgames.cards.Card(dadgames.cards.Rank.FOUR, dadgames.cards.Suit.HEARTS)),
                listOf(dadgames.cards.Card(dadgames.cards.Rank.ACE, dadgames.cards.Suit.HEARTS), dadgames.cards.Card(dadgames.cards.Rank.TWO, dadgames.cards.Suit.HEARTS)),
            ) + List(5) { emptyList() },
            faceDown = listOf(0, 1, 0, 0, 0, 0, 0),
        )
        val down = g.hint()!!.move
        assertEquals(Move.Transfer(Loc.foundation(0), Loc.tableau(0)), down)
        val g2 = g.apply(down)!!
        // Next hint must use the Three (Two of Hearts onto it), not send the Three back up.
        val next = g2.hint()!!.move as Move.Transfer
        assertFalse(next.to.type == PileType.FOUNDATION && next.from == Loc.tableau(0))
    }
}

class AutoCompleteTest {
    /** Auto-complete is offered as soon as every column card is face up, stock or not, and always finishes. */
    @Test fun finishesWithStockLeft() {
        var tested = 0
        for (seed in 1L..400L) for (draw in listOf(1, 3)) {
            var g = Klondike.deal(seed, draw)
            val r = Solver(60_000).solve(g)
            if (r.verdict != Solver.Verdict.WIN) continue
            for (m in r.moves) { if (g.canAutoComplete) break; g = g.apply(m)!! }
            if (g.stock.size + g.waste.size == 0) continue
            tested++
            val before = g
            var n = 0
            while (!g.won) { g = g.apply(g.autoCompleteMove()!!)!!; n++; assertTrue(n < 60) }
            // Every card went up exactly once, scoring like a normal play to the foundation (60 from a
            // column, 105 from the stock or waste, as in his old app).
            val cards = 52 - before.foundations.sumOf { it.size }
            assertEquals(cards, n)
            val steps = g.history.drop(before.history.size)
            for (st in steps) assertTrue(st.points == OldAppScoring.TABLEAU_TO_FOUNDATION || st.points == OldAppScoring.WASTE_TO_FOUNDATION)
            assertEquals(before.score + steps.sumOf { it.points }, g.score)
            // Undo of a collect puts the card back where it was.
            val c1 = before.apply(before.autoCompleteMove()!!)!!
            assertEquals(before.stock, c1.undo()!!.stock); assertEquals(before.waste, c1.undo()!!.waste)
        }
        println("auto-complete with stock left: $tested games")
        assertTrue(tested > 20)
    }
}
