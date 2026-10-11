package dadgames.blackjack.core

import dadgames.cards.Card
import dadgames.cards.Rank
import kotlinx.serialization.Serializable

/** Which first two cards may be doubled. */
@Serializable
enum class DoubleRule(val label: String) {
    ANY_TWO("Any two cards"),
    NINE_TO_ELEVEN("9, 10 or 11 only"),
}

/**
 * House rules. Defaults are dad's settings in his old app (8 decks, dealer stands on soft 17,
 * insurance off, no surrender) plus the usual US rules it plays by.
 */
@Serializable
data class Rules(
    val decks: Int = 8,
    /** Dealer draws to a soft 17 (H17) instead of standing on it (S17). */
    val dealerHitsSoft17: Boolean = false,
    val doubleRule: DoubleRule = DoubleRule.ANY_TWO,
    val doubleAfterSplit: Boolean = true,
    /** Most hands you can end up with by splitting (4 = split up to three times). */
    val maxHands: Int = 4,
    /** Split aces may be split again if another ace arrives. Off in most casinos. */
    val resplitAces: Boolean = false,
    /** Offer insurance when the dealer shows an ace. */
    val insurance: Boolean = false,
    /** Give up half the bet on your first two cards (after the dealer checks for blackjack). */
    val lateSurrender: Boolean = false,
    /** Fraction of the shoe dealt before the cut card comes out and the shoe is reshuffled. */
    val penetration: Double = 0.75,
) {
    fun normalized(): Rules = copy(
        decks = decks.coerceIn(1, 8),
        maxHands = maxHands.coerceIn(1, 4),
        penetration = penetration.coerceIn(0.5, 0.9),
    )

    /** "Dealer must stand on soft 17" / "Dealer must hit soft 17", like the felt in his app. */
    val dealerLine: String get() = if (dealerHitsSoft17) "Dealer must hit soft 17" else "Dealer must stand on soft 17"
}

/** Blackjack value of a card: ace 1 (or 11, see [Totals]), faces 10. */
val Card.points: Int get() = if (rank.value >= 10) 10 else rank.value

val Card.isAce: Boolean get() = rank == Rank.ACE

/** Hand total with aces counted as 1 ([hard]) and whether one ace can count as 11. */
data class Totals(val hard: Int, val hasAce: Boolean) {
    val soft: Boolean get() = hasAce && hard + 10 <= 21
    val best: Int get() = if (soft) hard + 10 else hard
    val bust: Boolean get() = hard > 21

    /** "7 / 17" for soft hands (like dad's app shows), "17" otherwise. */
    val label: String get() = if (soft && best != 21) "$hard / $best" else "$best"
}

fun totals(cards: Iterable<Card>): Totals {
    var h = 0
    var a = false
    for (c in cards) { h += c.points; if (c.isAce) a = true }
    return Totals(h, a)
}

fun isNatural(cards: List<Card>): Boolean = cards.size == 2 && totals(cards).best == 21

/** A table in the carousel: same rules, different stakes and felt. */
data class TableInfo(val name: String, val minBet: Long, val maxBet: Long, val felt: Long, val feltDark: Long)

/** Chip denominations: dad's familiar row plus bigger ones for his bankroll. */
val CHIPS: List<Long> = listOf(500, 1_000, 2_500, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000)

/** Every table's minimum is one of its chips, so the smallest chip on the rack is always a legal bet. */
val TABLES: List<TableInfo> = listOf(
    TableInfo("London", 500, 1_000_000, 0xFF237A4B, 0xFF0F3A22),
    TableInfo("Paris", 1_000, 2_000_000, 0xFF1F5C8A, 0xFF0C2A44),
    TableInfo("Monte Carlo", 10_000, 3_000_000, 0xFF8A2433, 0xFF3E0C14),
    TableInfo("Macau", 50_000, 5_000_000, 0xFF5B2C83, 0xFF27103D),
    TableInfo("Las Vegas", 100_000, 7_500_000, 0xFF137070, 0xFF063232),
    TableInfo("Dubai", 500_000, 10_000_000, 0xFF3A3A3A, 0xFF121212),
)

/** Chips shown on a table's rack: from its minimum up to its maximum. */
fun chipsFor(table: TableInfo): List<Long> = CHIPS.filter { it >= table.minBet && it <= table.maxBet }

/** Largest bet allowed right now: table maximum, capped by what he has. */
fun maxBetFor(table: TableInfo, balance: Long): Long = minOf(table.maxBet, balance)

fun betAllowed(bet: Long, table: TableInfo, balance: Long): Boolean = bet >= table.minBet && bet <= maxBetFor(table, balance)

/** Adding a chip never pushes the bet over the table maximum or the balance; returns the new bet. */
fun addChip(bet: Long, chip: Long, table: TableInfo, balance: Long): Long = minOf(bet + chip, maxBetFor(table, balance)).coerceAtLeast(bet)
