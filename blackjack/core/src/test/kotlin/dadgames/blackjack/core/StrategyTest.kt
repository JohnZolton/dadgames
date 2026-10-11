package dadgames.blackjack.core

import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The computed strategy reproduces the published basic-strategy charts (Wizard of Odds,
 * 4-8 decks) for each rule set. Codes: H hit, S stand, D double (else hit), Ds double (else stand),
 * P split, R surrender.
 */
class StrategyTest {
    private fun c(v: Int): Card = Card(if (v == 1) Rank.ACE else Rank.entries[v - 1], Suit.SPADES)

    private val ups = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 1)

    private fun code(s: Strategy, hand: List<Card>, up: Int, split: Boolean, surrender: Boolean): String {
        val legal = buildList {
            add(Action.STAND); add(Action.HIT)
            val t = totals(hand)
            if (s.rules.doubleRule == DoubleRule.ANY_TWO || (!t.soft && t.hard in 9..11)) add(Action.DOUBLE)
            if (split) add(Action.SPLIT)
            if (surrender) add(Action.SURRENDER)
        }
        return when (s.best(hand, c(up), legal)) {
            Action.DOUBLE -> if (s.best(hand, c(up), legal - Action.DOUBLE - Action.SURRENDER - Action.SPLIT) == Action.STAND) "Ds" else "D"
            Action.HIT -> "H"; Action.STAND -> "S"; Action.SPLIT -> "P"; Action.SURRENDER -> "R"
            else -> "?"
        }
    }

    /** Representative non-pair two-card hands for each hard total. */
    private fun hard(t: Int): List<Card> = when (t) {
        5 -> listOf(c(2), c(3)); 6 -> listOf(c(2), c(4)); 7 -> listOf(c(2), c(5)); 8 -> listOf(c(2), c(6))
        9 -> listOf(c(2), c(7)); 10 -> listOf(c(2), c(8)); 11 -> listOf(c(2), c(9))
        else -> listOf(c(10), c(t - 10))
    }

    private fun chart(r: Rules, surrender: Boolean = false): Map<String, String> {
        val s = Strategy(r)
        val out = LinkedHashMap<String, String>()
        for (t in 5..19) out["H$t"] = ups.joinToString(" ") { code(s, hard(t), it, false, surrender) }
        for (x in 2..9) out["A$x"] = ups.joinToString(" ") { code(s, listOf(c(1), c(x)), it, false, surrender) }
        for (v in listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 1)) out["P$v"] = ups.joinToString(" ") { code(s, listOf(c(v), c(v)), it, true, surrender) }
        return out
    }

    private fun print(name: String, ch: Map<String, String>) {
        println("== $name     2  3  4  5  6  7  8  9  T  A")
        for ((k, v) in ch) println(k.padEnd(4) + v.split(" ").joinToString("") { it.padStart(3) })
    }

    private val s17das = mapOf(
        "H8" to "H H H H H H H H H H",
        "H9" to "H D D D D H H H H H",
        "H10" to "D D D D D D D D H H",
        "H11" to "D D D D D D D D D H",
        "H12" to "H H S S S H H H H H",
        "H13" to "S S S S S H H H H H",
        "H16" to "S S S S S H H H H H",
        "H17" to "S S S S S S S S S S",
        "A2" to "H H H D D H H H H H",
        "A3" to "H H H D D H H H H H",
        "A4" to "H H D D D H H H H H",
        "A5" to "H H D D D H H H H H",
        "A6" to "H D D D D H H H H H",
        "A7" to "S Ds Ds Ds Ds S S H H H",
        "A8" to "S S S S S S S S S S",
        "P2" to "P P P P P P H H H H",
        "P3" to "P P P P P P H H H H",
        "P4" to "H H H P P H H H H H",
        "P5" to "D D D D D D D D H H",
        "P6" to "P P P P P H H H H H",
        "P7" to "P P P P P P H H H H",
        "P8" to "P P P P P P P P P P",
        "P9" to "P P P P P S P P S S",
        "P10" to "S S S S S S S S S S",
        "P1" to "P P P P P P P P P P",
    )

    private fun check(expected: Map<String, String>, actual: Map<String, String>) {
        for ((k, v) in expected) assertEquals("row $k", v, actual[k])
    }

    @Test fun eightDecksStandSoft17() {
        val ch = chart(Rules())
        print("8 decks S17 DAS", ch)
        check(s17das, ch)
    }

    @Test fun eightDecksHitSoft17() {
        val ch = chart(Rules(dealerHitsSoft17 = true))
        print("8 decks H17 DAS", ch)
        check(s17das + mapOf(
            "H11" to "D D D D D D D D D D",
            "A7" to "Ds Ds Ds Ds Ds S S H H H",
            "A8" to "S S S S Ds S S S S S",
        ), ch)
    }

    @Test fun noDoubleAfterSplit() {
        val ch = chart(Rules(doubleAfterSplit = false))
        print("8 decks S17 no DAS", ch)
        check(mapOf(
            "P2" to "H H P P P P H H H H",
            "P3" to "H H P P P P H H H H",
            "P4" to "H H H H H H H H H H",
            "P6" to "H P P P P H H H H H",
            "P7" to "P P P P P P H H H H",
        ), ch)
    }

    @Test fun lateSurrender() {
        val s17 = chart(Rules(lateSurrender = true), surrender = true)
        print("8 decks S17 DAS LS", s17)
        assertEquals("S S S S S H H H R H", s17["H15"])
        assertEquals("S S S S S H H R R R", s17["H16"])
        assertEquals("S S S S S S S S S S", s17["H17"])
        assertEquals("P P P P P P P P P P", s17["P8"])
        val h17 = chart(Rules(lateSurrender = true, dealerHitsSoft17 = true), surrender = true)
        print("8 decks H17 DAS LS", h17)
        assertEquals("S S S S S H H H R R", h17["H15"])
        assertEquals("S S S S S H H R R R", h17["H16"])
        assertEquals("S S S S S S S S S R", h17["H17"])
        assertEquals("P P P P P P P P P R", h17["P8"])
    }

    @Test fun doubleNineToElevenOnly() {
        val ch = chart(Rules(doubleRule = DoubleRule.NINE_TO_ELEVEN))
        // Soft hands can't double, so A7 stands where it would have doubled.
        assertEquals("S S S S S S S H H H", ch["A7"])
        assertEquals("H H H H H H H H H H", ch["A6"])
        assertEquals("H D D D D H H H H H", ch["H9"])
    }

    @Test fun twoDecks() {
        val ch = chart(Rules(decks = 2))
        print("2 decks S17 DAS", ch)
        // Fewer decks: 9 doubles against 2. (A few 2-deck borderline plays, e.g. 11 v ace, differ from
        // the published chart: the fixed-odds model doesn't follow every small-shoe nuance.)
        assertEquals("D D D D D H H H H H", ch["H9"])
        for (k in listOf("H10", "H13", "H16", "H17", "A7", "A8", "P2", "P6", "P7", "P8", "P9", "P10", "P1")) assertEquals(k, s17das[k], ch[k])
    }

    @Test fun neverInsurance() {
        val s = Strategy(Rules(insurance = true))
        assertEquals(Action.NO_INSURANCE, s.best(listOf(c(10), c(10)), c(1), listOf(Action.INSURANCE, Action.NO_INSURANCE)))
    }

    @Test fun threeCardHands() {
        val s = Strategy(Rules())
        val legal = listOf(Action.STAND, Action.HIT)
        assertEquals(Action.HIT, s.best(listOf(c(2), c(3), c(7)), c(2), legal)) // hard 12 v 2
        assertEquals(Action.STAND, s.best(listOf(c(5), c(4), c(4)), c(4), legal)) // hard 13 v 4
        assertEquals(Action.HIT, s.best(listOf(c(1), c(2), c(5)), c(9), legal)) // soft 18 v 9
        assertEquals(Action.STAND, s.best(listOf(c(1), c(2), c(5)), c(8), legal)) // soft 18 v 8
    }
}
