package dadgames.blackjack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Fuzzer (random rules, random legal moves: invariants hold, rounds always end) and a
 * basic-strategy simulation showing the house edge lands where it should for the rules.
 */
class Simulation {
    private fun conserved(g: Game) = assertEquals(g.totalCards, g.shoe.size + g.discards.size + g.tableCards.size)

    @Test fun fuzz() {
        val rnd = Random(2026)
        var rounds = 0
        repeat(40) { gameNo ->
            val rules = Rules(
                decks = listOf(1, 2, 4, 6, 8).random(rnd),
                dealerHitsSoft17 = rnd.nextBoolean(),
                doubleRule = DoubleRule.entries.random(rnd),
                doubleAfterSplit = rnd.nextBoolean(),
                maxHands = rnd.nextInt(1, 5),
                resplitAces = rnd.nextBoolean(),
                insurance = rnd.nextBoolean(),
                lateSurrender = rnd.nextBoolean(),
                penetration = listOf(0.6, 0.75, 0.9).random(rnd),
            )
            val g = Game.new(rules, 5_000_000, rnd.nextLong())
            repeat(1500) {
                if (g.balance < 500) g.balance += 5_000_000
                val before = g.balance
                val bet = (rnd.nextLong(1, minOf(g.balance, 200_000) + 1) / 2) * 2
                if (bet <= 0) return@repeat
                g.deal(bet)
                var steps = 0
                while (g.phase != Phase.ROUND_OVER) {
                    // Chips are only moved between balance and table, never created mid-round.
                    assertEquals(before, g.balance + g.onTable)
                    conserved(g)
                    if (g.phase == Phase.DEALER) g.dealerStep()
                    else {
                        val legal = g.legal()
                        assertTrue(legal.isNotEmpty())
                        g.act(legal.random(rnd))
                    }
                    assertTrue("round never ends (game $gameNo)", ++steps < 200)
                }
                val s = g.summary!!
                assertEquals(before + s.net, g.balance)
                assertEquals(s.staked, g.hands.sumOf { it.bet } + s.insuranceBet)
                assertTrue(g.hands.size <= rules.maxHands)
                for (h in g.hands) {
                    assertTrue(h.outcome != null && h.done)
                    if (h.doubled) assertEquals(3, h.cards.size)
                    if (h.splitAces && !rules.resplitAces) assertEquals(2, h.cards.size)
                    assertTrue(h.payout in 0..(h.bet * 5 / 2))
                }
                assertTrue(!g.holeHidden)
                conserved(g)
                g.collect()
                conserved(g)
                rounds++
            }
        }
        println("fuzz: $rounds rounds, invariants held")
    }

    /** Plays [hands] rounds of flat-bet basic strategy; returns the house edge in percent of initial bets. */
    private fun edge(rules: Rules, hands: Int, seed: Long): Double {
        val g = Game.new(rules, Long.MAX_VALUE / 4, seed)
        val s = Strategy(rules)
        val bet = 1000L
        val start = g.balance
        repeat(hands) {
            g.deal(bet)
            while (g.phase == Phase.INSURANCE || g.phase == Phase.PLAYER) g.act(s.best(g)!!)
            g.playDealer()
            g.collect()
        }
        return -(g.balance - start).toDouble() / (hands.toDouble() * bet) * 100
    }

    @Test fun millionHandsHouseEdge() {
        val n = 1_000_000
        val t0 = System.currentTimeMillis()
        val e = edge(Rules(), n, 12345)
        val ms = System.currentTimeMillis() - t0
        // Standard error of one hand is about 1.15 bets, so 1M hands is +-0.12% per sigma.
        println("house edge, 8 decks S17 DAS, ${n / 1000}k hands: %.3f%% (%d ms)".format(e, ms))
        // Published: 0.46% for these rules. Allow 2.5 sigma.
        assertTrue("edge $e", e in 0.15..0.75)
    }

    @Test fun tenMillionHandsHouseEdge() {
        val n = 10_000_000
        val e = edge(Rules(), n, 2026)
        println("house edge, 8 decks S17 DAS, 10M hands: %.3f%%".format(e))
        // +-0.036% per sigma at 10M hands.
        assertTrue("edge $e", e in 0.35..0.60)
    }

    @Test fun rulesMoveTheEdgeTheRightWay() {
        val n = 5_000_000
        // Same cards (same seed) so the difference is down to the rules, not luck.
        val base = edge(Rules(), n, 777)
        val h17 = edge(Rules(dealerHitsSoft17 = true), n, 777)
        val noDas = edge(Rules(doubleAfterSplit = false), n, 777)
        println("edges (%%): S17 %.3f  H17 %.3f  noDAS %.3f".format(base, h17, noDas))
        assertTrue("H17 should cost the player", h17 > base)
        assertTrue("no DAS should cost the player", noDas > base)
    }
}
