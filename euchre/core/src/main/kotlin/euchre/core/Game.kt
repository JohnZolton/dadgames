package euchre.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
enum class Phase { MISDEAL_CHECK, GO_UNDER, NAME_JOKER_SUIT, BID1, BID2, DISCARD, DEFEND_ALONE, LONER_DISCARD, PLAY, HAND_OVER, GAME_OVER }

@Serializable
enum class ResultKind { MADE, MARCH, LONE_MARCH, EUCHRE, DEFEND_ALONE_EUCHRE, SUPER_EUCHRE }

/** trump == null is a no-trump contract. */
@Serializable
data class Contract(
    val trump: Suit?,
    val maker: Int,
    val alone: Boolean,
    val round: Int,
    val defenderAlone: Int? = null,
)

@Serializable
data class Bid(val seat: Int, val text: String)

@Serializable
data class HandResult(
    val maker: Int,
    val makerTricks: Int,
    val points: List<Int>,
    val kind: ResultKind,
    val alone: Boolean,
)

sealed interface Action {
    data object Pass : Action
    data object NextHand : Action
    data class OrderUp(val alone: Boolean) : Action
    data class Call(val trump: Suit?, val alone: Boolean) : Action
    data class Discard(val card: Card) : Action
    data class DefendAlone(val yes: Boolean) : Action
    data class Misdeal(val yes: Boolean) : Action
    data class GoUnder(val yes: Boolean) : Action
    data class NameSuit(val suit: Suit) : Action
    data class Play(val card: Card) : Action
}

class Score(val points: List<Int>, val kind: ResultKind, val makerTricks: Int)

fun scoreHand(rules: Rules, maker: Int, alone: Boolean, defenderAlone: Int?, tricksBySeat: IntArray): Score {
    val mt = rules.team(maker)
    val makerTricks = (0 until rules.players).filter { rules.team(it) == mt }.sumOf { tricksBySeat[it] }
    val pts = MutableList(rules.teams) { 0 }
    val kind: ResultKind
    if (makerTricks >= 3) {
        if (makerTricks == 5) {
            kind = if (alone) ResultKind.LONE_MARCH else ResultKind.MARCH
            pts[mt] = if (alone) rules.loneMarchPoints else rules.marchPoints
        } else {
            kind = ResultKind.MADE
            pts[mt] = rules.makerPoints
        }
    } else {
        var p = rules.euchrePoints
        var k = ResultKind.EUCHRE
        if (defenderAlone != null) {
            p = rules.defendAloneEuchrePoints; k = ResultKind.DEFEND_ALONE_EUCHRE
        }
        if (rules.superEuchre && makerTricks == 0 && rules.superEuchrePoints >= p) {
            p = rules.superEuchrePoints; k = ResultKind.SUPER_EUCHRE
        }
        kind = k
        for (t in 0 until rules.teams) if (t != mt) pts[t] = p
    }
    return Score(pts, kind, makerTricks)
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
class GameState(
    val rules: Rules,
    val seed: Long,
    var dealer: Int,
    val scores: MutableList<Int>,
    var handNo: Int = 0,
    var hands: MutableList<MutableList<Card>> = mutableListOf(),
    var kitty: MutableList<Card> = mutableListOf(),
    var upcard: Card? = null,
    /** Suit of the up card; the dealer names it when the joker is turned. */
    var upSuit: Suit? = null,
    var turnedDown: Boolean = false,
    var phase: Phase = Phase.BID1,
    var turn: Int = 0,
    var passes: Int = 0,
    var contract: Contract? = null,
    /** Every discard this hand (dealer pickup, loner trade). Only the discarder may look. */
    val discards: MutableList<Played> = mutableListOf(),
    /** Card a loner received from their partner, shown to the loner. */
    var givenCard: Card? = null,
    val misdealQueue: MutableList<Int> = mutableListOf(),
    val goUnderQueue: MutableList<Int> = mutableListOf(),
    /** Seat that went under this hand, if any. */
    var wentUnder: Int? = null,
    val defendQueue: MutableList<Int> = mutableListOf(),
    val pendingTrades: MutableList<Int> = mutableListOf(),
    val trick: MutableList<Played> = mutableListOf(),
    val tricks: MutableList<List<Played>> = mutableListOf(),
    val trickWinners: MutableList<Int> = mutableListOf(),
    var tricksWon: MutableList<Int> = mutableListOf(),
    val bids: MutableList<Bid> = mutableListOf(),
    var result: HandResult? = null,
    var winner: Int? = null,
    /** One-shot message for the table (redeal, misdeal). */
    var notice: String? = null,
) {
    val players: Int get() = rules.players
    val trump: Suit? get() = contract?.trump

    fun copy(): GameState = json.decodeFromString(serializer(), json.encodeToString(serializer(), this))
    fun toJson(): String = json.encodeToString(serializer(), this)

    fun isOut(seat: Int): Boolean {
        val c = contract ?: return false
        if (c.alone && seat == rules.partner(c.maker)) return true
        if (c.defenderAlone != null && seat == rules.partner(c.defenderAlone)) return true
        return false
    }

    val activeCount: Int get() = (0 until players).count { !isOut(it) }

    /** The dealer took the up card (round-one call, unless the dealer's partner went alone). */
    val upcardPickedUp: Boolean
        get() {
            val c = contract ?: return false
            return c.round == 1 && !(c.alone && rules.partner(c.maker) == dealer)
        }


    fun nextActive(seat: Int): Int {
        var p = rules.next(seat)
        while (isOut(p)) p = rules.next(p)
        return p
    }

    fun teamTricks(team: Int): Int = (0 until players).filter { rules.team(it) == team }.sumOf { tricksWon[it] }

    // ---- dealing ----

    internal fun deal() {
        val rnd = Random(seed * 1_000_003L + handNo * 7919L)
        val deck = buildDeck(rules).shuffled(rnd)
        hands = MutableList(players) { i -> deck.subList(i * 5, i * 5 + 5).toMutableList() }
        upcard = deck[players * 5]
        kitty = deck.drop(players * 5 + 1).toMutableList()
        upSuit = if (upcard!!.isJoker) null else upcard!!.suit
        turnedDown = false
        passes = 0
        contract = null
        discards.clear()
        givenCard = null
        defendQueue.clear()
        pendingTrades.clear()
        trick.clear()
        tricks.clear()
        trickWinners.clear()
        tricksWon = MutableList(players) { 0 }
        bids.clear()
        result = null
        misdealQueue.clear()
        goUnderQueue.clear()
        wentUnder = null
        if (rules.goingUnder && kitty.size >= 3) {
            var p = rules.next(dealer)
            repeat(players) {
                if (lowCards(hands[p]).size >= 3) goUnderQueue.add(p)
                p = rules.next(p)
            }
        }
        if (rules.noAceNoFace) {
            var p = rules.next(dealer)
            repeat(players) {
                if (hands[p].none { it.isFace }) misdealQueue.add(p)
                p = rules.next(p)
            }
        }
        enterPreBid()
    }

    private fun enterPreBid() {
        when {
            misdealQueue.isNotEmpty() -> { phase = Phase.MISDEAL_CHECK; turn = misdealQueue[0] }
            goUnderQueue.isNotEmpty() -> { phase = Phase.GO_UNDER; turn = goUnderQueue[0] }
            upSuit == null -> { phase = Phase.NAME_JOKER_SUIT; turn = dealer }
            else -> { phase = Phase.BID1; turn = rules.next(dealer); passes = 0 }
        }
    }

    // ---- actions ----

    fun legalActions(): List<Action> = when (phase) {
        Phase.MISDEAL_CHECK -> listOf(Action.Misdeal(true), Action.Misdeal(false))
        Phase.GO_UNDER -> listOf(Action.GoUnder(true), Action.GoUnder(false))
        Phase.NAME_JOKER_SUIT -> Suit.entries.map { Action.NameSuit(it) }
        Phase.BID1 -> {
            if (rules.canadianLoner && turn == rules.partner(dealer)) listOf(Action.Pass, Action.OrderUp(true))
            else buildList {
                add(Action.Pass); add(Action.OrderUp(false))
                if (rules.allowAlone) add(Action.OrderUp(true))
            }
        }
        Phase.BID2 -> buildList {
            if (!(rules.stickTheDealer && turn == dealer)) add(Action.Pass)
            for (s in callableSuits()) {
                add(Action.Call(s, false))
                if (rules.allowAlone) add(Action.Call(s, true))
            }
        }
        Phase.DISCARD, Phase.LONER_DISCARD -> hands[turn].map { Action.Discard(it) }
        Phase.DEFEND_ALONE -> listOf(Action.DefendAlone(true), Action.DefendAlone(false))
        Phase.PLAY -> legalCards(hands[turn], trick, trump).map { Action.Play(it) }
        Phase.HAND_OVER -> listOf(Action.NextHand)
        Phase.GAME_OVER -> emptyList()
    }

    /** Suits (null = no trump) that may be named in round two. */
    fun callableSuits(): List<Suit?> = buildList {
        for (s in Suit.entries) if (s != upSuit || rules.callTurnedDownSuit) add(s)
        if (rules.noTrump) add(null)
    }

    fun apply(action: Action) {
        require(action in legalActions()) { "Illegal $action in $phase for seat $turn" }
        notice = null
        when (action) {
            is Action.Misdeal -> {
                if (action.yes) {
                    bids.add(Bid(turn, "Misdeal"))
                    notice = "Misdeal: no ace, no face. Redeal."
                    handNo++
                    deal()
                } else {
                    misdealQueue.removeAt(0)
                    enterPreBid()
                }
            }
            is Action.GoUnder -> {
                if (action.yes) {
                    val give = lowCards(hands[turn]).take(3)
                    val take = kitty.take(3)
                    hands[turn].removeAll(give)
                    hands[turn].addAll(take)
                    kitty = (kitty.drop(3) + give).toMutableList()
                    wentUnder = turn
                    bids.add(Bid(turn, "Went under"))
                    goUnderQueue.clear()
                } else {
                    goUnderQueue.removeAt(0)
                }
                enterPreBid()
            }
            is Action.NameSuit -> {
                upSuit = action.suit
                bids.add(Bid(dealer, "Joker is ${action.suit.symbol}"))
                phase = Phase.BID1; turn = rules.next(dealer); passes = 0
            }
            Action.Pass -> {
                bids.add(Bid(turn, "Pass"))
                passes++
                if (passes < players) {
                    turn = rules.next(turn)
                } else if (phase == Phase.BID1) {
                    turnedDown = true
                    phase = Phase.BID2; turn = rules.next(dealer); passes = 0
                } else {
                    notice = "Everyone passed. Redeal."
                    dealer = rules.next(dealer)
                    handNo++
                    deal()
                }
            }
            is Action.OrderUp -> {
                val word = if (turn == dealer) "Pick it up" else "Order up"
                bids.add(Bid(turn, if (action.alone) "$word, alone!" else word))
                contract = Contract(upSuit, turn, action.alone, round = 1)
                if (!upcardPickedUp) {
                    afterContract()
                } else {
                    hands[dealer].add(upcard!!)
                    phase = Phase.DISCARD; turn = dealer
                }
            }
            is Action.Call -> {
                val name = action.trump?.let { "${it.symbol} ${it.label}" } ?: "No trump"
                bids.add(Bid(turn, if (action.alone) "$name, alone!" else name))
                contract = Contract(action.trump, turn, action.alone, round = 2)
                afterContract()
            }
            is Action.Discard -> {
                hands[turn].remove(action.card)
                discards.add(Played(turn, action.card))
                if (phase == Phase.DISCARD) {
                    afterContract()
                } else {
                    pendingTrades.removeAt(0)
                    nextTrade()
                }
            }
            is Action.DefendAlone -> {
                if (action.yes) {
                    bids.add(Bid(turn, "Defend alone!"))
                    contract = contract!!.copy(defenderAlone = turn)
                    defendQueue.clear()
                    setupTrades()
                } else {
                    defendQueue.removeAt(0)
                    if (defendQueue.isEmpty()) setupTrades() else turn = defendQueue[0]
                }
            }
            is Action.Play -> play(action.card)
            Action.NextHand -> {
                dealer = rules.next(dealer)
                handNo++
                deal()
            }
        }
    }

    private fun afterContract() {
        val c = contract!!
        if (c.alone && rules.defendAlone && players == 4) {
            defendQueue.clear()
            defendQueue.add(rules.next(c.maker))
            defendQueue.add((c.maker + 3) % 4)
            phase = Phase.DEFEND_ALONE
            turn = defendQueue[0]
        } else {
            setupTrades()
        }
    }

    private fun setupTrades() {
        val c = contract!!
        pendingTrades.clear()
        if (rules.partnerBestCard) {
            if (c.alone) pendingTrades.add(c.maker)
            c.defenderAlone?.let { pendingTrades.add(it) }
        }
        nextTrade()
    }

    private fun nextTrade() {
        if (pendingTrades.isEmpty()) {
            startPlay(); return
        }
        val loner = pendingTrades[0]
        val mate = rules.partner(loner)!!
        val best = hands[mate].maxBy { it.power(trump) }
        hands[mate].remove(best)
        hands[loner].add(best)
        givenCard = best
        bids.add(Bid(mate, "Passes best card"))
        phase = Phase.LONER_DISCARD
        turn = loner
    }

    private fun startPlay() {
        val c = contract!!
        phase = Phase.PLAY
        turn = if (rules.loneLead == LoneLead.LEFT_OF_LONER && c.alone) nextActive(c.maker) else nextActive(dealer)
    }

    private fun play(card: Card) {
        hands[turn].remove(card)
        trick.add(Played(turn, card))
        if (trick.size < activeCount) {
            turn = nextActive(turn); return
        }
        val w = trickWinner(trick, trump).seat
        tricks.add(trick.toList())
        trickWinners.add(w)
        tricksWon[w]++
        trick.clear()
        turn = w
        if (tricks.size == 5) finishHand()
    }

    private fun finishHand() {
        val c = contract!!
        val sc = scoreHand(rules, c.maker, c.alone, c.defenderAlone, tricksWon.toIntArray())
        for (t in scores.indices) scores[t] += sc.points[t]
        result = HandResult(c.maker, sc.makerTricks, sc.points, sc.kind, c.alone)
        val top = scores.max()
        if (top >= rules.pointsToWin && scores.count { it == top } == 1) {
            winner = scores.indexOf(top)
            phase = Phase.GAME_OVER
        } else {
            phase = Phase.HAND_OVER
        }
    }

    companion object {
        /** 9s and 10s (and 7s/8s in bigger decks), lowest first; what a player can go under with. */
        fun lowCards(hand: List<Card>): List<Card> =
            hand.filter { !it.isJoker && it.rank.order <= Rank.TEN.order }.sortedBy { it.rank.order }

        fun new(rules: Rules, seed: Long, firstDealer: Int? = null): GameState {
            val r = rules.normalized()
            val s = GameState(r, seed, firstDealer ?: Random(seed).nextInt(r.players), MutableList(r.teams) { 0 })
            s.deal()
            return s
        }

        fun fromJson(text: String): GameState = json.decodeFromString(serializer(), text)
    }
}
