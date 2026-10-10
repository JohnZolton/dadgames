package euchre.core

import kotlinx.serialization.Serializable

@Serializable
enum class LoneLead { LEFT_OF_DEALER, LEFT_OF_LONER }

@Serializable
data class Rules(
    /** 4 = two partnerships, 3 = cutthroat (everyone for themselves). */
    val players: Int = 4,
    val pointsToWin: Int = 10,
    /** Lowest rank in the deck: NINE = 24 cards, EIGHT = 28, SEVEN = 32. */
    val deckLow: Rank = Rank.NINE,
    /** Adds the joker ("Benny" / best bower) as the highest trump. */
    val joker: Boolean = false,

    val stickTheDealer: Boolean = false,
    val callTurnedDownSuit: Boolean = false,
    val noTrump: Boolean = false,
    /** A hand with no ace and no face card may call a misdeal before bidding. */
    val noAceNoFace: Boolean = false,
    /** Farmer's hand: three or more 9s/10s may be swapped for three face-down kitty cards before bidding. */
    val goingUnder: Boolean = false,

    val allowAlone: Boolean = true,
    val defendAlone: Boolean = false,
    /** If the dealer's partner orders up in round one, they must go alone. */
    val canadianLoner: Boolean = false,
    /** A loner swaps one card for their partner's best card. */
    val partnerBestCard: Boolean = false,
    val loneLead: LoneLead = LoneLead.LEFT_OF_DEALER,

    val makerPoints: Int = 1,
    val marchPoints: Int = 2,
    val loneMarchPoints: Int = 4,
    val euchrePoints: Int = 2,
    val defendAloneEuchrePoints: Int = 4,
    /** Defenders who take all five tricks score [superEuchrePoints]. */
    val superEuchre: Boolean = false,
    val superEuchrePoints: Int = 4,
) {
    val teams: Int get() = if (players == 4) 2 else players

    /** Strips options that can't apply to this combination. */
    fun normalized(): Rules {
        var r = this
        if (r.players != 4) r = r.copy(players = 3, allowAlone = false)
        if (!r.allowAlone) r = r.copy(defendAlone = false, canadianLoner = false, partnerBestCard = false)
        if (r.joker) r = r.copy(noTrump = false)
        return r
    }

    val deckSize: Int get() = buildDeck(this).size

    companion object {
        val presets: List<Pair<String, Rules>> = listOf(
            "Standard" to Rules(),
            "Stick the dealer" to Rules(stickTheDealer = true),
            "Canadian" to Rules(stickTheDealer = true, canadianLoner = true, defendAlone = true),
            "British (Benny)" to Rules(joker = true, pointsToWin = 11, deckLow = Rank.NINE),
            "Bar rules" to Rules(stickTheDealer = true, noAceNoFace = true, superEuchre = true),
            "Cutthroat (3p)" to Rules(players = 3, pointsToWin = 10),
        )
    }
}

fun Rules.team(seat: Int): Int = if (players == 4) seat % 2 else seat
fun Rules.partner(seat: Int): Int? = if (players == 4) (seat + 2) % 4 else null
fun Rules.next(seat: Int): Int = (seat + 1) % players
