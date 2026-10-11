package dadgames.blackjack.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Dad's chips and level, saved under their own small key after every change, so a game save that
 * can't be read (a future format change, a bad write) never costs him his bankroll. [BEST_PRACTICES 20a]
 * [balance] counts chips on the table during a hand as his (a hand that can't be restored is refunded).
 */
@Serializable
data class Bank(val balance: Long, val level: Int, val xp: Int = 0)

object SaveRecovery {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Pulls "balance" (plus chips bet on the table) out of an old or damaged game save, if it's there. */
    fun balanceFromGame(raw: String?): Long? = runCatching {
        val o = json.parseToJsonElement(raw ?: return null) as JsonObject
        val bal = o["balance"]?.jsonPrimitive?.longOrNull ?: return null
        val phase = o["phase"]?.jsonPrimitive?.content
        val bets = runCatching {
            (o["hands"] as? kotlinx.serialization.json.JsonArray)?.sumOf { (it as JsonObject)["bet"]?.jsonPrimitive?.long ?: 0L } ?: 0L
        }.getOrDefault(0L) + (o["insuranceBet"]?.jsonPrimitive?.longOrNull ?: 0L)
        // Mid-hand the bet has left the balance; once settled (or between hands) it hasn't.
        if (phase == null || phase == "BETTING" || phase == "ROUND_OVER") bal else bal + bets
    }.getOrNull()

    /** Level and XP out of an old or damaged profile save. */
    fun levelFromProfile(raw: String?): Pair<Int, Int>? = runCatching {
        val o = json.parseToJsonElement(raw ?: return null) as JsonObject
        val lv = o["level"]?.jsonPrimitive?.intOrNull ?: return null
        lv to (o["xp"]?.jsonPrimitive?.intOrNull ?: 0)
    }.getOrNull()

    /**
     * Balance to start a fresh table with when the game save is missing or unreadable:
     * the bank key first, then whatever can be salvaged from the unreadable save, then the seed
     * (only for a genuinely new install).
     */
    fun startingBalance(bank: Bank?, unreadableGame: String?, seed: Long): Long =
        bank?.balance ?: balanceFromGame(unreadableGame) ?: seed

    /** What to keep in the bank key for this game state. */
    fun bankBalance(g: Game): Long = when (g.phase) {
        Phase.BETTING, Phase.ROUND_OVER -> g.balance
        else -> g.balance + g.onTable
    }
}
