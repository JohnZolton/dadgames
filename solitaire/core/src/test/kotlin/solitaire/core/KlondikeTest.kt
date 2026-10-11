package solitaire.core

import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Rank.*
import dadgames.cards.Suit
import dadgames.cards.Suit.*
import dadgames.cards.standardDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KlondikeTest {
    private fun c(r: Rank, s: Suit) = Card(r, s)

    /** A hand-built position: [cols] bottom-to-top, first [down] cards of each face down. */
    private fun board(
        cols: List<List<Card>> = List(7) { emptyList() },
        down: List<Int> = List(7) { 0 },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        found: List<List<Card>> = List(4) { emptyList() },
        draw: Int = 1,
        score: Int = 0,
    ) = Klondike(seed = 0, draw = draw, stock = stock, waste = waste, foundations = found, tableau = cols, faceDown = down, score = score)

    private fun col(vararg cards: Card) = cards.toList()
    private fun cols(vararg c: List<Card>) = c.toList() + List(7 - c.size) { emptyList() }
    private fun check(g: Klondike) {
        assertEquals(standardDeck().toSet(), g.allCards().toSet())
        assertEquals(52, g.allCards().size)
    }

    @Test fun dealIsStandard() {
        val g = Klondike.deal(42)
        check(g)
        assertEquals((1..7).toList(), g.tableau.map { it.size })
        assertEquals((0..6).toList(), g.faceDown)
        assertEquals(24, g.stock.size)
        assertTrue(g.waste.isEmpty())
        assertEquals(0, g.score); assertEquals(0, g.moves); assertFalse(g.started)
        assertEquals(Klondike.deal(42), g) // same seed, same deal
        assertTrue(Klondike.deal(43) != g)
    }

    @Test fun drawOneAndRecycle() {
        val g = board(stock = listOf(c(TWO, CLUBS), c(THREE, CLUBS)), score = 150)
        val a = g.apply(Move.Draw)!!
        assertEquals(listOf(c(THREE, CLUBS)), a.waste)
        assertEquals(1, a.moves); assertTrue(a.started)
        val b = a.apply(Move.Draw)!!
        assertEquals(listOf(c(THREE, CLUBS), c(TWO, CLUBS)), b.waste)
        assertTrue(b.stock.isEmpty())
        val r = b.apply(Move.Draw)!!
        assertEquals(listOf(c(TWO, CLUBS), c(THREE, CLUBS)), r.stock) // turned back over: same order as at the start
        assertTrue(r.waste.isEmpty())
        assertEquals(1, r.recycles)
        assertEquals(50, r.score) // Draw 1: -100 per pass
        assertNull(board().apply(Move.Draw))
    }

    @Test fun drawOneRecyclesAreUnlimitedAndScoreNeverNegative() {
        var g = board(stock = listOf(c(TWO, CLUBS)), score = 30)
        repeat(20) { g = g.apply(Move.Draw)!! }
        assertEquals(0, g.score)
        assertEquals(10, g.recycles)
    }

    @Test fun drawThree() {
        val s = listOf(c(TWO, CLUBS), c(THREE, CLUBS), c(FOUR, CLUBS), c(FIVE, CLUBS))
        val g = board(stock = s, draw = 3, score = 100)
        val a = g.apply(Move.Draw)!!
        assertEquals(listOf(c(FIVE, CLUBS), c(FOUR, CLUBS), c(THREE, CLUBS)), a.waste)
        val b = a.apply(Move.Draw)!!
        assertEquals(c(TWO, CLUBS), b.waste.last())
        assertEquals(4, b.waste.size)
        // First three recycles are free in Draw 3, then -20 each.
        var r = b
        repeat(3) { r = r.apply(Move.Draw)!!; assertEquals(100, r.score); r = r.apply(Move.Draw)!!.apply(Move.Draw)!! }
        r = r.apply(Move.Draw)!!
        assertEquals(4, r.recycles)
        assertEquals(80, r.score)
        // Undo of a draw puts all three back.
        val u = a.undo()!!
        assertEquals(g.stock, u.stock); assertTrue(u.waste.isEmpty())
    }

    @Test fun tableauRules() {
        val g = board(cols = cols(col(c(EIGHT, HEARTS)), col(c(SEVEN, SPADES)), col(c(SEVEN, DIAMONDS)), col(), col(c(KING, CLUBS))))
        assertTrue(g.tableauAccepts(0, c(SEVEN, SPADES)))
        assertTrue(g.tableauAccepts(0, c(SEVEN, CLUBS)))
        assertFalse(g.tableauAccepts(0, c(SEVEN, DIAMONDS))) // same colour
        assertFalse(g.tableauAccepts(0, c(SIX, SPADES))) // wrong rank
        assertTrue(g.tableauAccepts(3, c(KING, HEARTS)))
        assertFalse(g.tableauAccepts(3, c(QUEEN, HEARTS))) // only kings into a space
        assertNotNull(g.apply(Move.Transfer(Loc.tableau(1), Loc.tableau(0))))
        assertNull(g.apply(Move.Transfer(Loc.tableau(2), Loc.tableau(1))))
        assertNotNull(g.apply(Move.Transfer(Loc.tableau(4), Loc.tableau(3))))
    }

    @Test fun foundationRules() {
        val g = board(
            cols = cols(col(c(ACE, HEARTS)), col(c(TWO, HEARTS)), col(c(TWO, SPADES))),
            found = listOf(listOf(c(ACE, SPADES)), emptyList(), emptyList(), emptyList()),
        )
        assertEquals(1, g.foundationFor(c(ACE, HEARTS))) // first empty slot
        assertEquals(0, g.foundationFor(c(TWO, SPADES)))
        assertNull(g.foundationFor(c(TWO, HEARTS))) // its ace isn't up yet
        assertNull(g.apply(Move.Transfer(Loc.tableau(1), Loc.foundation(0))))
        val a = g.apply(Move.Transfer(Loc.tableau(0), Loc.foundation(2)))!!
        assertEquals(10, a.score)
        val b = a.apply(Move.Transfer(Loc.tableau(1), Loc.foundation(2)))!!
        assertEquals(20, b.score)
        // Only one card at a time to a foundation.
        val run = board(cols = cols(col(c(THREE, CLUBS), c(TWO, HEARTS))), found = listOf(listOf(c(ACE, HEARTS)), emptyList(), emptyList(), emptyList()))
        assertNull(run.apply(Move.Transfer(Loc.tableau(0), Loc.foundation(0), 2)))
    }

    @Test fun moveRunsAndTurnOver() {
        val g = board(
            cols = cols(col(c(ACE, CLUBS), c(NINE, SPADES), c(EIGHT, HEARTS), c(SEVEN, CLUBS)), col(c(TEN, DIAMONDS))),
            down = listOf(1, 0, 0, 0, 0, 0, 0),
        )
        assertTrue(g.isRun(0, 1))
        assertFalse(g.isRun(0, 0)) // face down
        val a = g.apply(Move.Transfer(Loc.tableau(0), Loc.tableau(1), 3))!!
        assertEquals(4, a.tableau[1].size)
        assertEquals(0, a.faceDown[0]) // ace turned over
        assertEquals(5, a.score) // +5 for turning a card over
        val u = a.undo()!!
        assertEquals(g.tableau, u.tableau); assertEquals(g.faceDown, u.faceDown); assertEquals(0, u.score); assertEquals(0, u.moves)
    }

    @Test fun scoringTable() {
        val g = board(
            cols = cols(col(c(EIGHT, HEARTS)), col(c(TWO, CLUBS))),
            waste = listOf(c(SEVEN, SPADES)),
            found = listOf(listOf(c(ACE, CLUBS)), emptyList(), emptyList(), emptyList()),
            score = 100,
        )
        assertEquals(105, g.apply(Move.Transfer(Loc.WASTE, Loc.tableau(0)))!!.score) // waste -> tableau +5
        assertEquals(110, g.apply(Move.Transfer(Loc.tableau(1), Loc.foundation(0)))!!.score) // to foundation +10
        val fromFound = board(cols = cols(col(c(EIGHT, SPADES))), found = listOf(listOf(c(ACE, HEARTS), c(TWO, HEARTS), c(THREE, HEARTS), c(FOUR, HEARTS), c(FIVE, HEARTS), c(SIX, HEARTS), c(SEVEN, HEARTS)), emptyList(), emptyList(), emptyList()), score = 100)
        assertEquals(85, fromFound.apply(Move.Transfer(Loc.foundation(0), Loc.tableau(0)))!!.score) // foundation -> tableau -15
        // Tableau to tableau is worth nothing by itself.
        val tt = board(cols = cols(col(c(EIGHT, HEARTS)), col(c(SEVEN, SPADES))), score = 100)
        assertEquals(100, tt.apply(Move.Transfer(Loc.tableau(1), Loc.tableau(0)))!!.score)
    }

    @Test fun timePenaltyAndClock() {
        val g = board(cols = cols(col(c(EIGHT, HEARTS)), col(c(SEVEN, SPADES))), score = 11)
        assertEquals(g, g.tick(60_000)) // clock doesn't run before the first move
        val a = g.apply(Move.Transfer(Loc.tableau(1), Loc.tableau(0)))!!
        val t = a.tick(9_999)
        assertEquals(11, t.score)
        assertEquals(9, t.tick(10_000).score)
        assertEquals(3, t.tick(40_000).score)
        assertEquals(0, t.tick(60_000).score) // never below zero
        assertEquals(60, t.tick(60_500).seconds)
    }

    @Test fun undoKeepsTimePenalty() {
        val g = board(cols = cols(col(c(EIGHT, HEARTS)), col(c(SEVEN, SPADES))), waste = listOf(c(SEVEN, CLUBS)), score = 50)
        val a = g.apply(Move.Transfer(Loc.WASTE, Loc.tableau(0)))!!.tick(20_000) // 55, then -4
        assertEquals(51, a.score)
        val u = a.undo()!!
        assertEquals(46, u.score) // back to 50, penalty already paid stays paid
        assertEquals(20_000, u.elapsedMs)
        assertEquals(listOf(c(SEVEN, CLUBS)), u.waste)
    }

    @Test fun winAndTimeBonus() {
        // Everything up but the four kings, each on its own column.
        val found = Suit.entries.map { s -> Rank.entries.filter { it != KING }.map { c(it, s) } }
        val g = board(cols = cols(col(c(KING, CLUBS)), col(c(KING, DIAMONDS)), col(c(KING, HEARTS)), col(c(KING, SPADES))), found = found, score = 600)
        assertTrue(g.canAutoComplete)
        var x = g.apply(Move.Transfer(Loc.tableau(0), Loc.foundation(0)))!!.tick(119_400)
        while (!x.won) x = x.apply(x.autoCompleteMove()!!)!!
        assertEquals(119, x.seconds)
        assertEquals(4666, x.bonus)
        assertEquals(x.score + 4666, x.finalScore)
        assertFalse(x.canUndo)
        assertNull(x.apply(Move.Draw))
        assertEquals(0, Scoring.timeBonus(30))
        assertEquals(4666, Scoring.timeBonus(31))
        assertEquals(4666, Scoring.timeBonus(150))
        assertEquals(3500, Scoring.timeBonus(200))
    }

    @Test fun autoCompleteNeedsEverythingFaceUp() {
        val g = Klondike.deal(1)
        assertFalse(g.canAutoComplete)
        assertNull(g.autoCompleteMove())
    }

    @Test fun tapMovesToBestPlace() {
        val g = board(
            cols = cols(col(c(EIGHT, HEARTS)), col(c(TWO, DIAMONDS), c(NINE, SPADES), c(EIGHT, DIAMONDS), c(SEVEN, SPADES)), col(), col(c(KING, CLUBS)), col(c(ACE, SPADES))),
            down = listOf(0, 1, 0, 0, 0, 0, 0),
            stock = listOf(c(FOUR, CLUBS)),
            waste = listOf(c(SEVEN, CLUBS)),
        )
        // Ace goes up.
        assertEquals(Move.Transfer(Loc.tableau(4), Loc.foundation(0)), g.autoMove(Loc.tableau(4), 0))
        // Waste 7 of clubs goes onto the 8 of hearts.
        assertEquals(Move.Transfer(Loc.WASTE, Loc.tableau(0)), g.autoMove(Loc.WASTE, 0))
        // Tapping the 8 of diamonds moves it with the 7 on top... nowhere to go (no black 9 available).
        assertNull(g.autoMove(Loc.tableau(1), 2))
        // Tapping the 9 of spades moves the whole run; nowhere to go either (no red 10).
        assertNull(g.autoMove(Loc.tableau(1), 1))
        // A king already at the bottom doesn't hop to another empty column.
        assertNull(g.autoMove(Loc.tableau(3), 0))
        // Face-down cards can't be tapped.
        assertNull(g.autoMove(Loc.tableau(1), 0))
        assertEquals(Move.Draw, g.autoMove(Loc.STOCK, 0))
        // A run that can go somewhere moves whole.
        val h = board(cols = cols(col(c(TEN, HEARTS)), col(c(TWO, DIAMONDS), c(NINE, SPADES), c(EIGHT, DIAMONDS))), down = listOf(0, 1, 0, 0, 0, 0, 0))
        assertEquals(Move.Transfer(Loc.tableau(1), Loc.tableau(0), 2), h.autoMove(Loc.tableau(1), 1))
        // A king with cards under it goes into the space.
        val k = board(cols = cols(col(c(TWO, DIAMONDS), c(KING, SPADES)), col()), down = listOf(1, 0, 0, 0, 0, 0, 0))
        assertEquals(Move.Transfer(Loc.tableau(0), Loc.tableau(1)), k.autoMove(Loc.tableau(0), 1))
    }

    @Test fun hintsAndNoMoves() {
        val g = board(
            cols = cols(col(c(TWO, DIAMONDS), c(NINE, SPADES)), col(c(TEN, HEARTS))),
            down = listOf(1, 0, 0, 0, 0, 0, 0),
        )
        val h = g.hint()!!
        assertEquals(Move.Transfer(Loc.tableau(0), Loc.tableau(1)), h.move)
        assertEquals("Move the Nine of Spades onto the Ten of Hearts.", h.text)
        // Only a draw helps.
        val d = board(cols = cols(col(c(TEN, HEARTS))), stock = listOf(c(FOUR, CLUBS), c(NINE, CLUBS)))
        assertEquals(Move.Draw, d.hint()!!.move)
        // Stuck: stock and board give nothing.
        val stuck = board(cols = cols(col(c(TEN, HEARTS)), col(c(TEN, SPADES))), stock = listOf(c(FOUR, CLUBS), c(FIVE, CLUBS)))
        assertNull(stuck.hint())
        assertTrue(stuck.noMovesLeft())
    }

    @Test fun jsonRoundTrip() {
        var g = Klondike.deal(7)
        repeat(10) { g = g.apply(g.legalMoves().first())!! }
        val back = Klondike.fromJson(g.toJson())
        assertEquals(g, back)
    }

    @Test fun clocks() {
        assertEquals("1:28", clock(88))
        assertEquals("1:00:05", clock(3605))
        assertEquals("9 d 13:14:57", longClock(Stats.seeded.totalTime))
    }

    @Test fun statsTopTen() {
        val s = Stats.seeded
        assertEquals(29, s.winRate)
        assertNull(s.rankOf(5000))
        assertEquals(0, s.rankOf(5400))
        val w = s.recordWin(5300, 100, 95, "2026-10-11", 0, false)
        assertEquals(10, w.top.size)
        assertEquals(5300, w.top[2].score)

        assertEquals(5221, w.top.last().score)
        assertEquals(1070, w.won); assertEquals(3648, w.played); assertEquals(90, w.leastMoves); assertEquals(88, w.bestTime)
        assertEquals(1, w.streak)
        val l = w.recordLoss(100, 50, 30, 0, false)
        assertEquals(0, l.streak); assertEquals(1, l.bestStreak); assertEquals(3649, l.played)
        val fresh = Stats().recordWin(5000, 120, 110, "2026-10-11", 0, false)
        assertEquals(110, fresh.leastMoves); assertEquals(120, fresh.bestTime)
    }
}
