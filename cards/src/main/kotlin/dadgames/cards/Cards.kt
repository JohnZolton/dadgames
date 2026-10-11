package dadgames.cards

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Standard French-suited deck used by Solitaire and Blackjack. (Euchre has its own model with the joker.) */
@Serializable
enum class Suit(val symbol: String, val red: Boolean, val label: String) {
    CLUBS("♣", false, "Clubs"),
    DIAMONDS("♦", true, "Diamonds"),
    HEARTS("♥", true, "Hearts"),
    SPADES("♠", false, "Spades"),
}

@Serializable
enum class Rank(val short: String, val value: Int, val label: String) {
    ACE("A", 1, "Ace"), TWO("2", 2, "Two"), THREE("3", 3, "Three"), FOUR("4", 4, "Four"),
    FIVE("5", 5, "Five"), SIX("6", 6, "Six"), SEVEN("7", 7, "Seven"), EIGHT("8", 8, "Eight"),
    NINE("9", 9, "Nine"), TEN("10", 10, "Ten"), JACK("J", 11, "Jack"), QUEEN("Q", 12, "Queen"),
    KING("K", 13, "King");

    val isFace: Boolean get() = this == JACK || this == QUEEN || this == KING
}

@Serializable
data class Card(val rank: Rank, val suit: Suit) {
    val red: Boolean get() = suit.red
    /** "Ace of Spades": use in sentences instead of symbols. */
    val spoken: String get() = "${rank.label} of ${suit.label}"
    override fun toString(): String = "${rank.short}${suit.symbol}"
}

fun standardDeck(): List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { Card(it, s) } }

/** A fair shuffle of [decks] standard decks. */
fun shuffledShoe(decks: Int, rnd: Random): List<Card> = List(decks) { standardDeck() }.flatten().shuffled(rnd)
