package dadgames.blackjack.core

import dadgames.cards.Card
import dadgames.cards.standardDeck
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** A card on the table. [id] is unique per dealt card so the UI can follow it (deal, split, sweep). */
@Serializable
data class DCard(val card: Card, val id: Int)

@Serializable
enum class Phase { BETTING, INSURANCE, PLAYER, DEALER, ROUND_OVER }

@Serializable
enum class Action(val label: String) {
    HIT("Hit"), STAND("Stand"), DOUBLE("Double"), SPLIT("Split"), SURRENDER("Surrender"),
    INSURANCE("Insurance"), NO_INSURANCE("No insurance"),
}

@Serializable
enum class Outcome(val label: String) {
    BLACKJACK("Blackjack!"), WIN("Win"), PUSH("Push"), LOSE("Lose"), BUST("Bust"), SURRENDER("Surrendered"),
}

@Serializable
class PlayerHand(
    val cards: MutableList<DCard> = mutableListOf(),
    var bet: Long = 0,
    var doubled: Boolean = false,
    /** Created by splitting (no blackjack possible; doubling depends on the DAS rule). */
    var split: Boolean = false,
    var splitAces: Boolean = false,
    /** No more decisions on this hand. */
    var done: Boolean = false,
    var surrendered: Boolean = false,
    var outcome: Outcome? = null,
    /** Chips returned to the balance at settlement, stake included (0 for a loss). */
    var payout: Long = 0,
) {
    val plain: List<Card> get() = cards.map { it.card }
    val totals: Totals get() = totals(plain)
    val natural: Boolean get() = !split && isNatural(plain)
    val net: Long get() = payout - bet
}

/** What happened in the round just settled (for stats and the result banner). */
@Serializable
data class RoundSummary(
    /** Total chips put on the table this round: bets, doubles, splits, insurance. */
    val staked: Long,
    val returned: Long,
    val insuranceBet: Long,
    val insuranceWon: Boolean,
    val dealerBlackjack: Boolean,
    val dealerTotal: Int,
    val dealerBust: Boolean,
) {
    val net: Long get() = returned - staked
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * The whole table: shoe, discards, hands, balance. Mutable for speed (the simulator plays millions
 * of hands); the app works on [copy]s so Compose sees a new snapshot after every action.
 * Fair randomness only: every shuffle is a plain Fisher-Yates shuffle from [seed].
 */
@Serializable
class Game(
    var rules: Rules = Rules(),
    var balance: Long = 0,
    var seed: Long = 0,
    var shoe: MutableList<Card> = mutableListOf(),
    var discards: MutableList<Card> = mutableListOf(),
    var shoeDecks: Int = 0,
    var phase: Phase = Phase.BETTING,
    val dealer: MutableList<DCard> = mutableListOf(),
    var holeHidden: Boolean = false,
    val hands: MutableList<PlayerHand> = mutableListOf(),
    var active: Int = 0,
    var insuranceBet: Long = 0,
    var nextId: Int = 0,
    /** Counts every reshuffle; the UI shows "Shuffling" when it changes. */
    var shuffles: Int = 0,
    var summary: RoundSummary? = null,
    var rounds: Long = 0,
) {
    companion object {
        fun new(rules: Rules, balance: Long, seed: Long): Game = Game(rules.normalized(), balance, seed).also { it.reshuffle() }
        fun fromJson(s: String): Game = json.decodeFromString(serializer(), s)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)
    fun copy(): Game = fromJson(toJson())

    // ------------------------------------------------------------ shoe

    val totalCards: Int get() = shoeDecks * 52
    /** Reshuffle before the next deal once only this many cards are left (the cut card). */
    val cutRemaining: Int get() = Math.round(totalCards * (1 - rules.penetration)).toInt()
    val needsShuffle: Boolean get() = shoeDecks != rules.decks || shoe.size <= cutRemaining
    val tableCards: List<Card> get() = dealer.map { it.card } + hands.flatMap { h -> h.cards.map { it.card } }

    /** Gather everything (shoe, discards; the table must be empty) into a fresh shuffled shoe. */
    fun reshuffle() {
        check(dealer.isEmpty() && hands.isEmpty()) { "reshuffle with cards on the table" }
        if (shoeDecks != rules.decks) {
            shoeDecks = rules.decks
            shoe = List(shoeDecks) { standardDeck() }.flatten().toMutableList()
        } else {
            shoe.addAll(discards)
        }
        discards.clear()
        shuffleShoe()
    }

    private fun shuffleShoe() {
        val r = Random(seed)
        shoe.shuffle(r)
        seed = r.nextLong()
        shuffles++
    }

    private fun draw(): DCard {
        if (shoe.isEmpty()) {
            // Extremely rare (small shoe, many splits): shuffle the discards back in, table cards stay put.
            shoe.addAll(discards); discards.clear(); shuffleShoe()
        }
        return DCard(shoe.removeAt(shoe.size - 1), nextId++)
    }

    // ------------------------------------------------------------ queries

    val up: Card? get() = dealer.firstOrNull()?.card
    val hand: PlayerHand? get() = hands.getOrNull(active)
    val dealerTotals: Totals get() = totals(dealer.map { it.card })
    /** Dealer total as the player can see it (up card only while the hole card is face down). */
    val visibleDealerTotals: Totals get() = if (holeHidden) totals(dealer.take(1).map { it.card }) else dealerTotals
    val dealerNatural: Boolean get() = isNatural(dealer.map { it.card })
    val onTable: Long get() = hands.sumOf { it.bet } + insuranceBet

    fun canDouble(h: PlayerHand = hand!!): Boolean {
        if (phase != Phase.PLAYER || h.done || h.cards.size != 2 || h.splitAces) return false
        if (h.split && !rules.doubleAfterSplit) return false
        if (balance < h.bet) return false
        val t = h.totals
        return when (rules.doubleRule) {
            DoubleRule.ANY_TWO -> true
            DoubleRule.NINE_TO_ELEVEN -> !t.soft && t.hard in 9..11
        }
    }

    fun canSplit(h: PlayerHand = hand!!): Boolean {
        if (phase != Phase.PLAYER || h.done || h.cards.size != 2) return false
        if (h.cards[0].card.points != h.cards[1].card.points) return false
        if (hands.size >= rules.maxHands || balance < h.bet) return false
        if (h.splitAces && !rules.resplitAces) return false
        return true
    }

    fun canSurrender(h: PlayerHand = hand!!): Boolean =
        phase == Phase.PLAYER && rules.lateSurrender && hands.size == 1 && !h.split && h.cards.size == 2 && !h.done

    fun legal(): List<Action> = when (phase) {
        Phase.INSURANCE -> listOf(Action.INSURANCE, Action.NO_INSURANCE)
        Phase.PLAYER -> {
            val h = hand!!
            buildList {
                add(Action.STAND)
                if (canSplit(h)) add(Action.SPLIT)
                if (canDouble(h)) add(Action.DOUBLE)
                if (!h.splitAces) add(Action.HIT)
                if (canSurrender(h)) add(Action.SURRENDER)
            }
        }
        else -> emptyList()
    }

    // ------------------------------------------------------------ play

    /** Start a round. Takes the bet from the balance, reshuffles at the cut card, deals two each. */
    fun deal(bet: Long) {
        check(phase == Phase.BETTING) { "deal in $phase" }
        require(bet in 1..balance) { "bad bet $bet (balance $balance)" }
        rules = rules.normalized()
        if (needsShuffle) reshuffle()
        summary = null
        insuranceBet = 0
        balance -= bet
        val h = PlayerHand(bet = bet)
        hands.add(h)
        h.cards.add(draw())
        dealer.add(draw())
        h.cards.add(draw())
        dealer.add(draw())
        holeHidden = true
        active = 0
        if (up!!.isAce && rules.insurance && balance >= bet / 2 && bet / 2 > 0) {
            phase = Phase.INSURANCE
            return
        }
        afterInsurance()
    }

    private fun afterInsurance() {
        val peek = up!!.isAce || up!!.points == 10
        if (peek && dealerNatural) { holeHidden = false; settle(); return }
        if (hands[0].natural) { holeHidden = false; settle(); return }
        phase = Phase.PLAYER
    }

    fun act(a: Action) {
        check(a in legal()) { "$a not legal in $phase" }
        when (a) {
            Action.INSURANCE -> {
                insuranceBet = hands[0].bet / 2
                balance -= insuranceBet
                afterInsurance()
            }
            Action.NO_INSURANCE -> afterInsurance()
            Action.HIT -> {
                val h = hand!!
                h.cards.add(draw())
                if (h.totals.best >= 21) h.done = true
                advance()
            }
            Action.STAND -> { hand!!.done = true; advance() }
            Action.DOUBLE -> {
                val h = hand!!
                balance -= h.bet
                h.bet *= 2
                h.doubled = true
                h.cards.add(draw())
                h.done = true
                advance()
            }
            Action.SURRENDER -> {
                val h = hand!!
                h.surrendered = true
                h.done = true
                advance()
            }
            Action.SPLIT -> {
                val h = hand!!
                balance -= h.bet
                val aces = h.cards[0].card.isAce
                val moved = h.cards.removeAt(1)
                val n = PlayerHand(mutableListOf(moved), bet = h.bet, split = true, splitAces = aces)
                h.split = true
                h.splitAces = aces
                hands.add(active + 1, n)
                // The current hand gets its second card now; the new one when play reaches it.
                completeSplitHand(h)
                advance()
            }
        }
    }

    /** Second card for a split hand. Split aces get one card each and stop (unless they can resplit). */
    private fun completeSplitHand(h: PlayerHand) {
        h.cards.add(draw())
        if (h.totals.best == 21) h.done = true
        else if (h.splitAces && !(rules.resplitAces && h.cards[1].card.isAce && hands.size < rules.maxHands)) h.done = true
    }

    private fun advance() {
        while (active < hands.size && hands[active].done) {
            active++
            if (active < hands.size && hands[active].cards.size == 1) completeSplitHand(hands[active])
        }
        if (active < hands.size) return
        active = hands.size - 1
        if (hands.all { it.totals.bust || it.surrendered }) {
            holeHidden = false
            settle()
        } else {
            phase = Phase.DEALER
        }
    }

    val dealerMustHit: Boolean
        get() {
            val t = dealerTotals
            return t.best < 17 || (t.best == 17 && t.soft && rules.dealerHitsSoft17)
        }

    /**
     * One visible step of the dealer's turn: turn the hole card, draw one card, or (when done)
     * settle the round. Returns false once the round is over.
     */
    fun dealerStep(): Boolean {
        check(phase == Phase.DEALER)
        when {
            holeHidden -> holeHidden = false
            dealerMustHit -> dealer.add(draw())
            else -> { settle(); return false }
        }
        return true
    }

    fun playDealer() { while (phase == Phase.DEALER) dealerStep() }

    private fun settle() {
        val d = dealerTotals
        val dbj = dealerNatural
        var returned = 0L
        var staked = insuranceBet
        for (h in hands) {
            staked += h.bet
            val t = h.totals
            val (o, pay) = when {
                h.surrendered -> Outcome.SURRENDER to h.bet / 2
                t.bust -> Outcome.BUST to 0L
                dbj -> if (h.natural) Outcome.PUSH to h.bet else Outcome.LOSE to 0L
                h.natural -> Outcome.BLACKJACK to h.bet + h.bet * 3 / 2
                d.bust || t.best > d.best -> Outcome.WIN to h.bet * 2
                t.best == d.best -> Outcome.PUSH to h.bet
                else -> Outcome.LOSE to 0L
            }
            h.outcome = o
            h.payout = pay
            h.done = true
            returned += pay
        }
        val insWon = insuranceBet > 0 && dbj
        if (insWon) returned += insuranceBet * 3
        balance += returned
        summary = RoundSummary(staked, returned, insuranceBet, insWon, dbj, d.best, d.bust)
        phase = Phase.ROUND_OVER
        rounds++
    }

    /** Sweep the table into the discard tray, ready for the next bet. */
    fun collect() {
        check(phase == Phase.ROUND_OVER)
        discards.addAll(tableCards)
        dealer.clear()
        hands.clear()
        insuranceBet = 0
        holeHidden = false
        active = 0
        phase = Phase.BETTING
    }
}
