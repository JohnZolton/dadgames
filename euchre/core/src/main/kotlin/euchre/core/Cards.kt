package euchre.core

import kotlinx.serialization.Serializable

@Serializable
enum class Suit(val symbol: String, val red: Boolean, val label: String) {
    CLUBS("♣", false, "Clubs"),
    DIAMONDS("♦", true, "Diamonds"),
    SPADES("♠", false, "Spades"),
    HEARTS("♥", true, "Hearts");

    /** The other suit of the same colour (home of the left bower). */
    val sameColor: Suit
        get() = when (this) {
            CLUBS -> SPADES
            SPADES -> CLUBS
            DIAMONDS -> HEARTS
            HEARTS -> DIAMONDS
        }
}

@Serializable
enum class Rank(val short: String, val order: Int) {
    SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
    JACK("J", 11), QUEEN("Q", 12), KING("K", 13), ACE("A", 14),
    JOKER("★", 20),
}

/** The joker is always `Card(JOKER, SPADES)`; its suit only matters through [effSuit]. */
@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    val isJoker: Boolean get() = rank == Rank.JOKER
    val isFace: Boolean get() = rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING || rank == Rank.ACE || isJoker
    override fun toString(): String = if (isJoker) "Joker" else "${rank.short}${suit.symbol}"

    companion object {
        val JOKER = Card(Rank.JOKER, Suit.SPADES)
    }
}

/** trump == null means a no-trump hand. */
fun Card.effSuit(trump: Suit?): Suit = when {
    trump == null -> suit
    isJoker -> trump
    rank == Rank.JACK && suit == trump.sameColor -> trump
    else -> suit
}

fun Card.isTrump(trump: Suit?): Boolean = trump != null && effSuit(trump) == trump

fun Card.isRightBower(trump: Suit?) = trump != null && rank == Rank.JACK && suit == trump
fun Card.isLeftBower(trump: Suit?) = trump != null && rank == Rank.JACK && suit == trump.sameColor

/** Strength within the card's effective suit. Every trump outranks every non-trump. */
fun Card.power(trump: Suit?): Int = when {
    !isTrump(trump) -> rank.order
    isJoker -> 90
    isRightBower(trump) -> 89
    isLeftBower(trump) -> 88
    else -> 50 + rank.order
}

/** Does [a] beat [b] given the suit that was led? */
fun beats(a: Card, b: Card, led: Suit, trump: Suit?): Boolean {
    val at = a.isTrump(trump)
    val bt = b.isTrump(trump)
    if (at != bt) return at
    if (at) return a.power(trump) > b.power(trump)
    val af = a.effSuit(trump) == led
    val bf = b.effSuit(trump) == led
    if (af != bf) return af
    return af && a.power(trump) > b.power(trump)
}

@Serializable
data class Played(val seat: Int, val card: Card)

fun trickWinner(trick: List<Played>, trump: Suit?): Played {
    val led = trick[0].card.effSuit(trump)
    var best = trick[0]
    for (p in trick.drop(1)) if (beats(p.card, best.card, led, trump)) best = p
    return best
}

fun legalCards(hand: List<Card>, trick: List<Played>, trump: Suit?): List<Card> {
    if (trick.isEmpty()) return hand
    val led = trick[0].card.effSuit(trump)
    val follow = hand.filter { it.effSuit(trump) == led }
    return follow.ifEmpty { hand }
}

fun buildDeck(rules: Rules): List<Card> {
    val ranks = Rank.entries.filter { it != Rank.JOKER && it.order >= rules.deckLow.order }
    val cards = Suit.entries.flatMap { s -> ranks.map { Card(it, s) } }
    return if (rules.joker) cards + Card.JOKER else cards
}

/** Sort for display: trump first (if known), then alternating colours, high to low. */
fun sortHand(hand: List<Card>, trump: Suit?): List<Card> {
    val order = when (trump) {
        null, Suit.SPADES -> listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)
        Suit.HEARTS -> listOf(Suit.HEARTS, Suit.SPADES, Suit.DIAMONDS, Suit.CLUBS)
        Suit.CLUBS -> listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS)
        Suit.DIAMONDS -> listOf(Suit.DIAMONDS, Suit.CLUBS, Suit.HEARTS, Suit.SPADES)
    }
    return hand.sortedWith(compareBy<Card>({ order.indexOf(it.effSuit(trump)) }, { -it.power(trump) }))
}
