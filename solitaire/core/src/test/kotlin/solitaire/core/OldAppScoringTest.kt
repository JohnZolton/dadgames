package solitaire.core

import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Rank.*
import dadgames.cards.Suit
import dadgames.cards.Suit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dad's old app's scoring, decompiled from its APK. [research/solitaire.md "Exact scoring"] */
class OldAppScoringTest {
    private fun c(r: Rank, s: Suit) = Card(r, s)
    private fun board(
        cols: List<List<Card>> = List(7) { emptyList() },
        down: List<Int> = List(7) { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        found: List<List<Card>> = List(4) { emptyList() },
        draw: Int = 1,
        score: Int = 0,
    ) = Klondike(seed = 0, draw = draw, scoring = ScoringMode.OLD_APP, stock = stock, waste = waste, foundations = found, tableau = cols, faceDown = down, score = score)
    private fun cols(vararg c: List<Card>) = c.toList() + List(7 - c.size) { emptyList() }

    @Test fun defaultIsOldApp() {
        assertEquals(ScoringMode.OLD_APP, Klondike.deal(1).scoring)
    }

    /** His screenshots s08 → s09: one draw, then the Seven of Spades onto the Eight of Hearts = 45 at 0:22. */
    @Test fun drawThenWasteToTableauIs45() {
        val g = board(cols = cols(listOf(c(EIGHT, HEARTS))), stock = listOf(c(SEVEN, SPADES)))
        val a = g.apply(Move.Draw)!!.tick(10_000)
        assertEquals(0, a.score)
        val b = a.apply(Move.Transfer(Loc.WASTE, Loc.tableau(0)))!!.tick(22_000)
        assertEquals(45, b.score) // no time penalty
        assertEquals(2, b.moves)
    }

    @Test fun movePoints() {
        val f = listOf(listOf(c(ACE, CLUBS)), listOf(c(ACE, HEARTS), c(TWO, HEARTS), c(THREE, HEARTS), c(FOUR, HEARTS), c(FIVE, HEARTS), c(SIX, HEARTS), c(SEVEN, HEARTS)), emptyList(), emptyList())
        val g = board(
            cols = cols(listOf(c(EIGHT, HEARTS)), listOf(c(TWO, CLUBS)), listOf(c(EIGHT, SPADES)), listOf(c(NINE, HEARTS))),
            waste = listOf(c(SEVEN, SPADES)), found = f, score = 100,
        )
        assertEquals(145, g.apply(Move.Transfer(Loc.WASTE, Loc.tableau(0)))!!.score) // waste → column +45
        assertEquals(160, g.apply(Move.Transfer(Loc.tableau(1), Loc.foundation(0)))!!.score) // column → foundation +60
        assertEquals(25, g.apply(Move.Transfer(Loc.foundation(1), Loc.tableau(2)))!!.score) // foundation → column −75
        assertEquals(100, g.apply(Move.Transfer(Loc.tableau(2), Loc.tableau(3)))!!.score) // column → column 0
        val w = board(waste = listOf(c(TWO, CLUBS)), found = f, score = 100)
        assertEquals(205, w.apply(Move.Transfer(Loc.WASTE, Loc.foundation(0)))!!.score) // waste → foundation +105
        assertEquals(100, board(stock = listOf(c(TWO, CLUBS)), score = 100).apply(Move.Draw)!!.score) // drawing 0
    }

    @Test fun turnOverAndUndo() {
        val g = board(cols = cols(listOf(c(ACE, CLUBS), c(NINE, SPADES)), listOf(c(TEN, DIAMONDS))), down = listOf(1, 0, 0, 0, 0, 0, 0), score = 100)
        val a = g.apply(Move.Transfer(Loc.tableau(0), Loc.tableau(1)))!!
        assertEquals(125, a.score) // column → column 0, card turned up +25
        // Undo takes back the +25 for the reveal and costs 25 for the undo itself.
        assertEquals(75, a.undo()!!.score)
        // Undo of a plain move: points back, then −25.
        val w = board(cols = cols(listOf(c(EIGHT, HEARTS))), waste = listOf(c(SEVEN, SPADES)), score = 100)
        val b = w.apply(Move.Transfer(Loc.WASTE, Loc.tableau(0)))!!
        assertEquals(145, b.score)
        assertEquals(75, b.undo()!!.score)
        // Undo of a draw costs 25.
        val d = board(stock = listOf(c(TWO, CLUBS)), score = 100).apply(Move.Draw)!!
        assertEquals(75, d.undo()!!.score)
        // Never below zero.
        assertEquals(0, board(stock = listOf(c(TWO, CLUBS)), score = 10).apply(Move.Draw)!!.undo()!!.score)
    }

    @Test fun deckPasses() {
        // Draw 1: −200 every pass, clamped at 0; undoing a pass refunds what it took, then −25.
        var g = board(stock = listOf(c(TWO, CLUBS)), score = 450)
        g = g.apply(Move.Draw)!!.apply(Move.Draw)!!
        assertEquals(250, g.score)
        assertEquals(425, g.undo()!!.score)
        g = g.apply(Move.Draw)!!.apply(Move.Draw)!!
        assertEquals(50, g.score)
        g = g.apply(Move.Draw)!!.apply(Move.Draw)!!
        assertEquals(0, g.score)
        assertEquals(25, g.undo()!!.score) // refunds the 50 it actually took, then −25
        // Draw 3: first three passes free, then −200.
        var t = board(stock = listOf(c(TWO, CLUBS), c(THREE, CLUBS)), draw = 3, score = 500)
        repeat(3) { t = t.apply(Move.Draw)!!.apply(Move.Draw)!!; assertEquals(500, t.score) }
        t = t.apply(Move.Draw)!!.apply(Move.Draw)!!
        assertEquals(300, t.score)
    }

    @Test fun noTimePenaltyAndWinBonus() {
        val found = Suit.entries.map { s -> Rank.entries.filter { it != KING }.map { c(it, s) } }
        val g = board(cols = cols(listOf(c(KING, CLUBS)), listOf(c(KING, DIAMONDS)), listOf(c(KING, HEARTS)), listOf(c(KING, SPADES))), found = found, score = 4465)
        var x = g.apply(Move.Transfer(Loc.tableau(0), Loc.foundation(0)))!!.tick(116_900)
        assertEquals(4525, x.score) // +60, no time penalty after almost 2 minutes
        while (!x.won) x = x.apply(x.autoCompleteMove()!!)!!
        assertEquals(4705, x.score)
        assertEquals(116, x.seconds)
        assertEquals(20 * 4705 / 116, x.bonus) // integer division, whole seconds, no cap
        assertEquals(OldAppScoring.winBonus(4525, 116), 780)
        assertEquals(OldAppScoring.winBonus(100, 0), 2000) // seconds floored at 1
    }

    /** Every one of his top-10 scores is base + (20 × base) / seconds for a whole-5 base. */
    @Test fun dadsTopTenFitTheFormula() {
        for (e in Stats.seeded.top) {
            val bases = (0..4725 step 5).filter { b -> b + OldAppScoring.winBonus(b, e.seconds) == e.score }
            assertTrue("${e.score} at ${e.seconds}s", bases.isNotEmpty())
        }
        val b5305 = (0..4725 step 5).first { it + OldAppScoring.winBonus(it, 116) == 5305 }
        assertEquals(4525, b5305)
        val b5371 = (0..4725 step 5).first { it + OldAppScoring.winBonus(it, 140) == 5371 }
        assertEquals(4700, b5371)
    }
}
