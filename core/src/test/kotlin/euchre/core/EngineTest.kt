package euchre.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {
    private fun randomRules(rnd: Random): Rules = Rules(
        players = if (rnd.nextInt(4) == 0) 3 else 4,
        pointsToWin = listOf(5, 7, 10, 11).random(rnd),
        deckLow = listOf(Rank.NINE, Rank.EIGHT, Rank.SEVEN).random(rnd),
        joker = rnd.nextBoolean(),
        stickTheDealer = rnd.nextBoolean(),
        callTurnedDownSuit = rnd.nextBoolean(),
        noTrump = rnd.nextBoolean(),
        noAceNoFace = rnd.nextBoolean(),
        goingUnder = rnd.nextBoolean(),
        allowAlone = rnd.nextBoolean() || true,
        defendAlone = rnd.nextBoolean(),
        canadianLoner = rnd.nextBoolean(),
        partnerBestCard = rnd.nextBoolean(),
        loneLead = LoneLead.entries.random(rnd),
        superEuchre = rnd.nextBoolean(),
    )

    /** Every card in the deck is in exactly one place. */
    private fun checkCards(s: GameState) {
        val places = mutableListOf<Card>()
        s.hands.forEach { places += it }
        places += s.kitty
        s.tricks.forEach { t -> t.forEach { places += it.card } }
        s.trick.forEach { places += it.card }
        s.discards.forEach { places += it.card }
        if (!s.upcardPickedUp) places += s.upcard!!
        assertEquals("cards in ${s.phase}", buildDeck(s.rules).sortedBy { it.toString() }, places.sortedBy { it.toString() })
        if (s.phase == Phase.PLAY) {
            for (p in 0 until s.players) if (!s.isOut(p)) assertEquals(5 - s.tricks.size - (if (s.trick.any { it.seat == p }) 1 else 0), s.hands[p].size)
        }
    }

    private fun playGame(rules: Rules, seed: Long, pick: (GameState) -> Action): GameState {
        val s = GameState.new(rules, seed)
        var steps = 0
        while (s.phase != Phase.GAME_OVER) {
            checkCards(s)
            s.apply(pick(s))
            assertTrue("game too long", ++steps < 20000)
        }
        assertTrue(s.scores.max() >= s.rules.pointsToWin)
        return s
    }

    @Test fun randomPlayAcrossRules() {
        val rnd = Random(1)
        repeat(1500) { i ->
            val rules = randomRules(rnd)
            playGame(rules, i.toLong()) { s -> s.legalActions().random(rnd) }
        }
    }

    @Test fun botsAcrossRules() {
        val rnd = Random(2)
        repeat(150) { i ->
            val rules = randomRules(rnd)
            val bots = List(rules.normalized().players) { Bot(BotProfile(rnd.nextInt(101), rnd.nextInt(101)), i * 10L + it) }
            playGame(rules, i.toLong()) { s -> bots[s.turn].choose(s, s.turn) }
        }
    }

    @Test fun serializationRoundTrip() {
        val rnd = Random(3)
        val s = GameState.new(Rules(joker = true, defendAlone = true), 5)
        repeat(12) { s.apply(s.legalActions().random(rnd)) }
        val t = GameState.fromJson(s.toJson())
        assertEquals(s.toJson(), t.toJson())
        assertEquals(s.legalActions(), t.legalActions())
    }

    @Test fun bowersAndJoker() {
        val t = Suit.HEARTS
        val right = Card(Rank.JACK, Suit.HEARTS)
        val left = Card(Rank.JACK, Suit.DIAMONDS)
        val ace = Card(Rank.ACE, Suit.HEARTS)
        assertEquals(Suit.HEARTS, left.effSuit(t))
        assertTrue(beats(Card.JOKER, right, Suit.CLUBS, t))
        assertTrue(beats(right, left, Suit.HEARTS, t))
        assertTrue(beats(left, ace, Suit.HEARTS, t))
        assertTrue(beats(Card(Rank.NINE, Suit.HEARTS), Card(Rank.ACE, Suit.CLUBS), Suit.CLUBS, t))
        // Holding the left bower and a diamond, a diamond lead must be followed with the diamond, not the left.
        val hand = listOf(left, Card(Rank.TEN, Suit.DIAMONDS))
        assertEquals(listOf(Card(Rank.TEN, Suit.DIAMONDS)), legalCards(hand, listOf(Played(1, Card(Rank.KING, Suit.DIAMONDS))), t))
        // No trump: jacks are just jacks.
        assertTrue(beats(Card(Rank.ACE, Suit.DIAMONDS), left, Suit.DIAMONDS, null))
    }

    @Test fun scoring() {
        val r = Rules(superEuchre = true)
        assertEquals(listOf(1, 0), scoreHand(r, 0, false, null, intArrayOf(2, 1, 1, 1)).points)
        assertEquals(listOf(2, 0), scoreHand(r, 0, false, null, intArrayOf(3, 0, 2, 0)).points)
        assertEquals(listOf(4, 0), scoreHand(r, 0, true, null, intArrayOf(5, 0, 0, 0)).points)
        assertEquals(listOf(0, 2), scoreHand(r, 0, false, null, intArrayOf(1, 2, 1, 1)).points)
        assertEquals(listOf(0, 4), scoreHand(r, 0, false, null, intArrayOf(0, 3, 0, 2)).points)
        assertEquals(listOf(0, 4), scoreHand(r, 2, true, 1, intArrayOf(0, 3, 2, 0)).points)
        val cut = Rules(players = 3)
        assertEquals(listOf(0, 2, 2), scoreHand(cut, 0, false, null, intArrayOf(2, 2, 1)).points)
    }
}
