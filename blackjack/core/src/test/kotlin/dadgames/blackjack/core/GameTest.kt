package dadgames.blackjack.core

import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit
import dadgames.cards.standardDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private var suit = 0
    /** Card by blackjack value (1 = ace, 10 = king); suits rotate so stacked cards come out of a real shoe. */
    private fun c(v: Int): Card = Card(if (v == 1) Rank.ACE else if (v == 10) Rank.KING else Rank.entries[v - 1], Suit.entries[suit++ % 4])

    /**
     * A full shoe whose next cards (in deal order: player, dealer up, player, dealer hole, then
     * hits) are [next]. The rest of the shoe is everything else, so card conservation still holds.
     */
    private fun game(rules: Rules = Rules(), balance: Long = 100_000, vararg next: Int): Game {
        val g = Game.new(rules, balance, 1)
        val top = next.map { c(it) }
        val rest = List(rules.decks) { standardDeck() }.flatten().toMutableList()
        for (t in top) check(rest.remove(t))
        g.shoe = (rest + top.reversed()).toMutableList()
        return g
    }

    private fun conserved(g: Game) = assertEquals(g.totalCards, g.shoe.size + g.discards.size + g.tableCards.size)

    // ---------------------------------------------------------------- naturals and peeking

    @Test fun blackjackPaysThreeToTwo() {
        val g = game(next = intArrayOf(1, 9, 10, 7))
        g.deal(1000)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.BLACKJACK, g.hands[0].outcome)
        assertEquals(101_500, g.balance)
        assertEquals(2, g.dealer.size) // dealer doesn't draw against a natural
        assertFalse(g.holeHidden)
        assertEquals(1500, g.summary!!.net)
    }

    @Test fun blackjackOnOddBetRoundsDown() {
        val g = game(next = intArrayOf(1, 9, 10, 7))
        g.deal(5)
        assertEquals(100_000 - 5 + 5 + 7, g.balance)
    }

    @Test fun blackjackAgainstDealerBlackjackPushes() {
        val g = game(next = intArrayOf(1, 1, 10, 10))
        g.deal(1000)
        assertEquals(Outcome.PUSH, g.hands[0].outcome)
        assertEquals(100_000, g.balance)
    }

    @Test fun dealerPeeksWithTenUp() {
        val g = game(next = intArrayOf(10, 10, 10, 1))
        g.deal(1000)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.LOSE, g.hands[0].outcome)
        assertTrue(g.summary!!.dealerBlackjack)
        assertEquals(99_000, g.balance)
    }

    @Test fun dealerPeeksWithAceUpWhenInsuranceOff() {
        val g = game(next = intArrayOf(10, 1, 10, 10))
        g.deal(1000)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.LOSE, g.hands[0].outcome)
        assertEquals(99_000, g.balance)
    }

    @Test fun noPeekBlackjackWithSmallUpCard() {
        // A 9 up can't make blackjack: play goes on.
        val g = game(next = intArrayOf(10, 9, 9, 10))
        g.deal(1000)
        assertEquals(Phase.PLAYER, g.phase)
        assertTrue(g.holeHidden)
    }

    // ---------------------------------------------------------------- insurance

    @Test fun insuranceOnlyOfferedWhenEnabled() {
        val off = game(next = intArrayOf(10, 1, 9, 5))
        off.deal(1000)
        assertEquals(Phase.PLAYER, off.phase)
        val on = game(Rules(insurance = true), next = intArrayOf(10, 1, 9, 5))
        on.deal(1000)
        assertEquals(Phase.INSURANCE, on.phase)
        assertEquals(listOf(Action.INSURANCE, Action.NO_INSURANCE), on.legal())
    }

    @Test fun insurancePaysTwoToOne() {
        val g = game(Rules(insurance = true), next = intArrayOf(10, 1, 9, 10))
        g.deal(1000)
        g.act(Action.INSURANCE)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.LOSE, g.hands[0].outcome)
        assertTrue(g.summary!!.insuranceWon)
        assertEquals(100_000, g.balance) // lose 1000, insurance 500 wins 1000
        assertEquals(1500, g.summary!!.staked)
    }

    @Test fun insuranceLostWhenNoBlackjack() {
        val g = game(Rules(insurance = true), next = intArrayOf(10, 1, 9, 6, 10))
        g.deal(1000)
        g.act(Action.INSURANCE)
        assertEquals(Phase.PLAYER, g.phase)
        assertEquals(98_500, g.balance)
        g.act(Action.STAND)
        g.playDealer() // A6 = soft 17, stands
        assertEquals(Outcome.WIN, g.hands[0].outcome) // 19 beats 17
        assertEquals(100_000 - 500 + 1000, g.balance)
        assertFalse(g.summary!!.insuranceWon)
    }

    @Test fun insuranceWithBlackjackIsEvenMoney() {
        val g = game(Rules(insurance = true), next = intArrayOf(1, 1, 10, 10))
        g.deal(1000)
        g.act(Action.INSURANCE)
        assertEquals(Outcome.PUSH, g.hands[0].outcome)
        assertEquals(101_000, g.balance) // push + insurance 2:1 = even money
    }

    @Test fun declineInsuranceThenDealerBlackjack() {
        val g = game(Rules(insurance = true), next = intArrayOf(10, 1, 9, 10))
        g.deal(1000)
        g.act(Action.NO_INSURANCE)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(99_000, g.balance)
    }

    // ---------------------------------------------------------------- plain outcomes

    @Test fun winPushLose() {
        val win = game(next = intArrayOf(10, 10, 9, 7))
        win.deal(1000); win.act(Action.STAND); win.playDealer()
        assertEquals(Outcome.WIN, win.hands[0].outcome); assertEquals(101_000, win.balance)

        val push = game(next = intArrayOf(10, 10, 8, 8))
        push.deal(1000); push.act(Action.STAND); push.playDealer()
        assertEquals(Outcome.PUSH, push.hands[0].outcome); assertEquals(100_000, push.balance)

        val lose = game(next = intArrayOf(10, 10, 7, 9))
        lose.deal(1000); lose.act(Action.STAND); lose.playDealer()
        assertEquals(Outcome.LOSE, lose.hands[0].outcome); assertEquals(99_000, lose.balance)
    }

    @Test fun bustLosesAndDealerDoesNotDraw() {
        val g = game(next = intArrayOf(10, 6, 6, 10, 10))
        g.deal(1000)
        g.act(Action.HIT)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.BUST, g.hands[0].outcome)
        assertEquals(2, g.dealer.size)
        assertEquals(99_000, g.balance)
    }

    @Test fun dealerBustPays() {
        val g = game(next = intArrayOf(10, 6, 2, 10, 10))
        g.deal(1000); g.act(Action.STAND); g.playDealer()
        assertTrue(g.summary!!.dealerBust)
        assertEquals(Outcome.WIN, g.hands[0].outcome)
        assertEquals(101_000, g.balance)
    }

    @Test fun twentyOneStandsAutomatically() {
        val g = game(next = intArrayOf(5, 10, 6, 7, 10))
        g.deal(1000)
        g.act(Action.HIT)
        assertEquals(Phase.DEALER, g.phase)
    }

    // ---------------------------------------------------------------- doubles

    @Test fun doubleWinsAndLosesTwice() {
        val w = game(next = intArrayOf(5, 10, 6, 7, 10))
        w.deal(1000); w.act(Action.DOUBLE)
        assertEquals(3, w.hands[0].cards.size)
        assertEquals(Phase.DEALER, w.phase)
        w.playDealer()
        assertEquals(Outcome.WIN, w.hands[0].outcome); assertEquals(102_000, w.balance)

        val l = game(next = intArrayOf(5, 10, 6, 9, 2))
        l.deal(1000); l.act(Action.DOUBLE); l.playDealer()
        assertEquals(Outcome.LOSE, l.hands[0].outcome); assertEquals(98_000, l.balance)
    }

    @Test fun doubleTakesExactlyOneCard() {
        val g = game(next = intArrayOf(2, 10, 3, 7, 2))
        g.deal(1000); g.act(Action.DOUBLE)
        assertEquals(7, g.hands[0].totals.best)
        assertTrue(g.hands[0].done)
    }

    @Test fun doubleNineToElevenRule() {
        val r = Rules(doubleRule = DoubleRule.NINE_TO_ELEVEN)
        val ok = game(r, next = intArrayOf(5, 10, 5, 7)); ok.deal(1000)
        assertTrue(Action.DOUBLE in ok.legal())
        val hard12 = game(r, next = intArrayOf(5, 10, 7, 7)); hard12.deal(1000)
        assertFalse(Action.DOUBLE in hard12.legal())
        val soft = game(r, next = intArrayOf(1, 10, 8, 7)); soft.deal(1000) // A8 = soft 19 (hard 9)
        assertFalse(Action.DOUBLE in soft.legal())
    }

    @Test fun cannotDoubleOrSplitWithoutChips() {
        val g = game(balance = 1500, next = intArrayOf(8, 10, 8, 7))
        g.deal(1000)
        assertEquals(listOf(Action.STAND, Action.HIT), g.legal())
    }

    @Test fun noDoubleAfterThirdCard() {
        val g = game(next = intArrayOf(2, 10, 3, 7, 2))
        g.deal(1000); g.act(Action.HIT)
        assertFalse(Action.DOUBLE in g.legal())
    }

    // ---------------------------------------------------------------- splits

    @Test fun splitAndDoubleAfterSplit() {
        // 8,8 v 6: split; hand 1 gets 3 (11) and doubles onto a 10; hand 2 gets 10 (18) and stands.
        val g = game(next = intArrayOf(8, 6, 8, 10, 3, 10, 10, 10))
        g.deal(1000)
        g.act(Action.SPLIT)
        assertEquals(2, g.hands.size)
        assertEquals(2, g.hands[0].cards.size)
        assertEquals(1, g.hands[1].cards.size) // second hand waits for its card
        assertTrue(Action.DOUBLE in g.legal())
        g.act(Action.DOUBLE)
        assertEquals(1, g.active)
        assertEquals(18, g.hands[1].totals.best)
        g.act(Action.STAND)
        g.playDealer() // 16 draws a 10: bust
        assertEquals(listOf(Outcome.WIN, Outcome.WIN), g.hands.map { it.outcome })
        assertEquals(100_000 + 2000 + 1000, g.balance)
        assertEquals(3000, g.summary!!.staked)
        conserved(g)
    }

    @Test fun noDoubleAfterSplitWhenRuleOff() {
        val g = game(Rules(doubleAfterSplit = false), next = intArrayOf(8, 6, 8, 10, 3))
        g.deal(1000); g.act(Action.SPLIT)
        assertFalse(Action.DOUBLE in g.legal())
    }

    @Test fun splitAcesGetOneCardAndTwentyOneIsNotBlackjack() {
        val g = game(next = intArrayOf(1, 6, 1, 10, 10, 5, 10))
        g.deal(1000)
        g.act(Action.SPLIT)
        // Both aces got one card each and play passes straight to the dealer.
        assertEquals(Phase.DEALER, g.phase)
        assertEquals(listOf(2, 2), g.hands.map { it.cards.size })
        g.playDealer() // 16 + 10 bust
        assertEquals(listOf(Outcome.WIN, Outcome.WIN), g.hands.map { it.outcome })
        assertEquals(102_000, g.balance) // 21 after a split pays 1:1, not 3:2
    }

    @Test fun splitAcesCannotBeHit() {
        val g = game(Rules(resplitAces = true), next = intArrayOf(1, 6, 1, 10, 1, 5))
        g.deal(1000); g.act(Action.SPLIT)
        // First split ace caught another ace: may resplit or stand, never hit.
        assertEquals(listOf(Action.STAND, Action.SPLIT), g.legal())
    }

    @Test fun splitUpToFourHands() {
        val g = game(next = intArrayOf(8, 6, 8, 10, 8, 8, 8, 8, 8))
        g.deal(1000)
        g.act(Action.SPLIT); g.act(Action.SPLIT); g.act(Action.SPLIT)
        assertEquals(4, g.hands.size)
        assertFalse(Action.SPLIT in g.legal()) // fourth 8 can't split again
        assertEquals(96_000, g.balance)
        conserved(g)
    }

    @Test fun maxHandsRule() {
        val g = game(Rules(maxHands = 2), next = intArrayOf(8, 6, 8, 10, 8))
        g.deal(1000); g.act(Action.SPLIT)
        assertFalse(Action.SPLIT in g.legal())
    }

    @Test fun splitTenValueCards() {
        val g = game(next = intArrayOf(10, 6, 10, 10))
        g.deal(1000)
        assertTrue(Action.SPLIT in g.legal())
    }

    @Test fun splitHandsSettleIndependently() {
        // 9,9 v 7 (hole 10 = 17): hand 1 gets 10 (19, win), hand 2 gets 8 (17, push).
        val g = game(next = intArrayOf(9, 7, 9, 10, 10, 8))
        g.deal(1000); g.act(Action.SPLIT)
        g.act(Action.STAND); g.act(Action.STAND)
        g.playDealer()
        assertEquals(listOf(Outcome.WIN, Outcome.PUSH), g.hands.map { it.outcome })
        assertEquals(101_000, g.balance)
    }

    @Test fun allSplitHandsBustSkipsDealer() {
        val g = game(next = intArrayOf(8, 6, 8, 10, 6, 10, 6, 10))
        g.deal(1000); g.act(Action.SPLIT)
        g.act(Action.HIT) // 8+6+10 bust
        g.act(Action.HIT) // 8+6+10 bust
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(2, g.dealer.size)
        assertEquals(98_000, g.balance)
    }

    // ---------------------------------------------------------------- surrender

    @Test fun lateSurrenderReturnsHalf() {
        val g = game(Rules(lateSurrender = true), next = intArrayOf(10, 10, 6, 7))
        g.deal(1000)
        assertTrue(Action.SURRENDER in g.legal())
        g.act(Action.SURRENDER)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(Outcome.SURRENDER, g.hands[0].outcome)
        assertEquals(99_500, g.balance)
    }

    @Test fun surrenderOnlyOnFirstTwoCards() {
        val off = game(next = intArrayOf(10, 10, 6, 7)); off.deal(1000)
        assertFalse(Action.SURRENDER in off.legal())
        val hit = game(Rules(lateSurrender = true), next = intArrayOf(2, 10, 3, 7, 2)); hit.deal(1000); hit.act(Action.HIT)
        assertFalse(Action.SURRENDER in hit.legal())
        val split = game(Rules(lateSurrender = true), next = intArrayOf(8, 10, 8, 7, 2)); split.deal(1000); split.act(Action.SPLIT)
        assertFalse(Action.SURRENDER in split.legal())
    }

    @Test fun lateMeansAfterPeek() {
        // Dealer blackjack ends the round before surrender is possible.
        val g = game(Rules(lateSurrender = true), next = intArrayOf(10, 1, 6, 10))
        g.deal(1000)
        assertEquals(Phase.ROUND_OVER, g.phase)
        assertEquals(99_000, g.balance)
    }

    // ---------------------------------------------------------------- dealer play

    private fun dealerFinal(rules: Rules, vararg dealerCards: Int): Game {
        // Player 10,10 stands; dealer gets the rest.
        val seq = intArrayOf(10, dealerCards[0], 10, dealerCards[1]) + dealerCards.drop(2)
        val g = game(rules, next = seq)
        g.deal(1000); g.act(Action.STAND); g.playDealer()
        return g
    }

    @Test fun dealerStandsOnSoft17ByDefault() {
        val g = dealerFinal(Rules(), 1, 6, 5)
        assertEquals(2, g.dealer.size)
        assertEquals(17, g.summary!!.dealerTotal)
    }

    @Test fun dealerHitsSoft17WhenSet() {
        val g = dealerFinal(Rules(dealerHitsSoft17 = true), 6, 1, 3)
        assertEquals(3, g.dealer.size)
        assertEquals(20, g.summary!!.dealerTotal)
    }

    @Test fun dealerHitsSixteenStandsHard17() {
        val g = dealerFinal(Rules(dealerHitsSoft17 = true), 10, 6, 1, 5)
        // 16 + A = hard 17: stands even with H17.
        assertEquals(3, g.dealer.size)
        assertEquals(17, g.summary!!.dealerTotal)
    }

    @Test fun dealerSoftHandBecomesHard() {
        // A,5 (soft 16) + 10 = hard 16, hit again + 3 = 19.
        val g = dealerFinal(Rules(), 5, 1, 10, 3)
        assertEquals(4, g.dealer.size)
        assertEquals(19, g.summary!!.dealerTotal)
    }

    @Test fun dealerStepsAreVisible() {
        val g = game(next = intArrayOf(10, 6, 10, 10, 5))
        g.deal(1000); g.act(Action.STAND)
        assertTrue(g.holeHidden)
        assertTrue(g.dealerStep()); assertFalse(g.holeHidden) // flip
        assertTrue(g.dealerStep()); assertEquals(3, g.dealer.size) // draw 5 = 21
        assertFalse(g.dealerStep()); assertEquals(Phase.ROUND_OVER, g.phase)
    }

    // ---------------------------------------------------------------- shoe

    @Test fun shoeReshufflesAtCutCard() {
        val g = Game.new(Rules(decks = 2), 1_000_000_000, 42)
        val start = g.shuffles
        assertEquals(104, g.shoe.size)
        assertEquals(26, g.cutRemaining)
        var rounds = 0
        while (g.shuffles == start) {
            g.deal(1000)
            if (g.phase == Phase.INSURANCE) g.act(Action.NO_INSURANCE)
            while (g.phase == Phase.PLAYER) g.act(Action.STAND)
            g.playDealer()
            conserved(g)
            val left = g.shoe.size
            g.collect()
            conserved(g)
            rounds++
            if (g.shuffles == start) assertTrue("dealt past the cut card", left > 0)
        }
        // The deal that triggered the reshuffle started from a full shoe.
        assertEquals(104, g.shoe.size + g.tableCards.size + g.discards.size)
        assertTrue(rounds in 10..30)
    }

    @Test fun changingDecksBuildsNewShoe() {
        val g = Game.new(Rules(), 1_000_000, 1)
        g.rules = Rules(decks = 4)
        assertTrue(g.needsShuffle)
        g.deal(1000)
        assertEquals(208, g.totalCards)
        conserved(g)
    }

    @Test fun shuffleIsAPermutation() {
        val g = Game.new(Rules(decks = 6), 1, 7)
        val counts = g.shoe.groupingBy { it }.eachCount()
        assertEquals(52, counts.size)
        assertTrue(counts.values.all { it == 6 })
    }

    @Test fun shuffleIsUniformish() {
        // Ace of spades position over many single-deck shuffles: every position roughly 1/52.
        val pos = IntArray(52)
        val g = Game.new(Rules(decks = 1), 1, 99)
        val ace = Card(Rank.ACE, Suit.SPADES)
        repeat(52_000) { g.reshuffle(); pos[g.shoe.indexOf(ace)]++ }
        assertTrue(pos.joinToString(), pos.all { it in 800..1200 })
    }

    // ---------------------------------------------------------------- save / resume

    @Test fun resumeMidHandIsExact() {
        val g = game(next = intArrayOf(8, 6, 8, 10, 3, 10, 10, 10))
        g.deal(1000); g.act(Action.SPLIT)
        val back = Game.fromJson(g.toJson())
        assertEquals(g.toJson(), back.toJson())
        back.act(Action.DOUBLE); back.act(Action.STAND); back.playDealer()
        g.act(Action.DOUBLE); g.act(Action.STAND); g.playDealer()
        assertEquals(g.balance, back.balance)
        assertEquals(g.toJson(), back.toJson())
    }
}
