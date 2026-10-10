package euchre.core

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.random.Random

/**
 * How a computer player behaves. Both are 0..100.
 *  - aggression: how light it calls trump, goes alone and defends alone.
 *  - iq: card play and bidding judgement. Low = blunders and forgets cards, mid = solid
 *    heuristics, high = simulates the rest of the hand from what it can see.
 */
@Serializable
data class BotProfile(val aggression: Int = 50, val iq: Int = 60) {
    companion object {
        fun iqLabel(iq: Int) = when {
            iq < 20 -> "Clueless"
            iq < 40 -> "Casual"
            iq < 60 -> "Solid"
            iq < 80 -> "Sharp"
            else -> "Shark"
        }

        fun aggressionLabel(a: Int) = when {
            a < 20 -> "Timid"
            a < 40 -> "Careful"
            a < 60 -> "Balanced"
            a < 80 -> "Bold"
            else -> "Reckless"
        }
    }
}

// ---------------------------------------------------------------- hand evaluation

object Eval {
    fun trumpValue(c: Card, t: Suit): Double = when {
        c.isJoker -> 1.0
        c.isRightBower(t) -> 0.95
        c.isLeftBower(t) -> 0.85
        else -> when (c.rank) {
            Rank.ACE -> 0.75
            Rank.KING -> 0.65
            Rank.QUEEN -> 0.55
            Rank.TEN -> 0.5
            Rank.NINE -> 0.45
            Rank.EIGHT -> 0.4
            else -> 0.35
        }
    }

    /** Rough number of tricks this hand takes on its own with the given trump. */
    fun strength(hand: List<Card>, trump: Suit?): Double {
        if (trump == null) return noTrumpStrength(hand)
        val trumps = hand.filter { it.isTrump(trump) }
        var v = trumps.sumOf { trumpValue(it, trump) }
        for (s in Suit.entries) {
            if (s == trump) continue
            val cs = hand.filter { !it.isTrump(trump) && it.suit == s }
            if (cs.isEmpty()) {
                if (trumps.size >= 2) v += 0.25
                continue
            }
            val ace = cs.any { it.rank == Rank.ACE }
            if (ace) v += if (cs.size <= 2) 0.75 else 0.6
            if (cs.any { it.rank == Rank.KING }) v += if (ace) 0.35 else if (cs.size >= 2) 0.2 else 0.12
        }
        if (trumps.size >= 3) v += 0.25
        if (trumps.size >= 4) v += 0.25
        return v
    }

    private fun noTrumpStrength(hand: List<Card>): Double {
        var v = 0.0
        for (s in Suit.entries) {
            val cs = hand.filter { it.suit == s }
            val a = cs.any { it.rank == Rank.ACE }
            val k = cs.any { it.rank == Rank.KING }
            if (a) v += 0.85
            if (k) v += if (a) 0.6 else if (cs.size >= 2) 0.3 else 0.1
            if (a && k && cs.any { it.rank == Rank.QUEEN }) v += 0.4
            if (a && cs.size >= 3) v += 0.2
        }
        return v
    }

    fun bestDiscard(hand: List<Card>, trump: Suit?): Card =
        hand.sortedWith(
            compareByDescending<Card> { c -> strength(hand - c, trump) }
                .thenBy { it.isTrump(trump) }
                .thenBy { it.power(trump) }
        ).first()
}

// ---------------------------------------------------------------- heuristic card play

class PlayCtx(
    val rules: Rules,
    val deck: List<Card>,
    val seat: Int,
    val hand: List<Card>,
    val trick: List<Played>,
    /** Cards from finished tricks that this player remembers. */
    val played: Set<Card>,
    val trump: Suit?,
    val maker: Int,
    /** Partner seat if they're in this hand, else null. */
    val partner: Int?,
    val activeCount: Int,
)

object Heuristic {
    fun play(c: PlayCtx, partnerAware: Boolean = true): Card {
        val legal = legalCards(c.hand, c.trick, c.trump)
        if (legal.size == 1) return legal[0]
        val t = c.trump
        val inTrick = c.trick.map { it.card }.toSet()
        val unseen = c.deck.filter { it !in c.played && it !in c.hand && it !in inTrick }
        fun isBoss(card: Card): Boolean {
            val s = card.effSuit(t)
            val p = card.power(t)
            return unseen.none { it.effSuit(t) == s && it.power(t) > p }
        }

        if (c.trick.isEmpty()) return lead(c, legal, unseen, ::isBoss)

        val led = c.trick[0].card.effSuit(t)
        val win = trickWinner(c.trick, t)
        val last = c.trick.size == c.activeCount - 1
        val partnerWinning = partnerAware && c.partner != null && win.seat == c.partner
        val winners = legal.filter { beats(it, win.card, led, t) }

        if (partnerWinning) {
            // Partner has it unless their card can still be beaten by someone behind us.
            val safe = last || isBoss(win.card) && (win.card.isTrump(t) || unseen.none { it.isTrump(t) } || led == t)
            if (safe || winners.isEmpty()) return lowest(legal, c, ::isBoss)
            // Partner is shaky: overtake only with a sure winner of the led suit.
            val sure = winners.filter { isBoss(it) && it.effSuit(t) == led }
            return if (sure.isNotEmpty()) sure.minBy { it.power(t) } else lowest(legal, c, ::isBoss)
        }
        if (winners.isNotEmpty()) {
            if (last) return winners.minBy { it.power(t) }
            val ruffing = winners.first().isTrump(t) && led != t
            if (ruffing) return winners.minBy { it.power(t) }
            val boss = winners.filter { isBoss(it) }
            return if (boss.isNotEmpty()) boss.minBy { it.power(t) } else winners.minBy { it.power(t) }
        }
        return lowest(legal, c, ::isBoss)
    }

    private fun lead(c: PlayCtx, legal: List<Card>, unseen: List<Card>, isBoss: (Card) -> Boolean): Card {
        val t = c.trump
        val trumps = legal.filter { it.isTrump(t) }.sortedByDescending { it.power(t) }
        val off = legal.filter { !it.isTrump(t) }
        val weMade = c.rules.team(c.maker) == c.rules.team(c.seat)
        val trumpOut = unseen.count { it.isTrump(t) }

        if (weMade && trumps.isNotEmpty() && trumpOut > 0) {
            val top = trumps[0]
            if (isBoss(top) && (c.seat == c.maker || trumps.size >= 2 || c.partner == null)) return top
            if (c.seat == c.maker && trumps.size >= 3) return trumps.last()
            // Lead trump back to the partner who called it.
            if (c.seat != c.maker && c.partner == c.maker && c.played.isEmpty()) return if (isBoss(top)) top else trumps.last()
        }
        val bossOff = off.filter { isBoss(it) }
        if (bossOff.isNotEmpty()) {
            // Prefer the suit opponents are most likely still to hold, so it isn't trumped.
            return bossOff.maxBy { b -> unseen.count { it.effSuit(t) == b.effSuit(t) } }
        }
        if (off.isNotEmpty()) {
            val len = off.groupingBy { it.effSuit(t) }.eachCount()
            // Short suits first (to set up a ruff); don't lead a lone king away.
            return off.sortedWith(compareBy<Card>({ it.rank == Rank.KING && len[it.effSuit(t)] == 1 }, { len[it.effSuit(t)] }, { it.power(t) })).first()
        }
        return if (isBoss(trumps[0])) trumps[0] else trumps.last()
    }

    private fun lowest(legal: List<Card>, c: PlayCtx, isBoss: (Card) -> Boolean): Card {
        val t = c.trump
        val len = c.hand.groupingBy { it.effSuit(t) }.eachCount()
        return legal.minBy { card ->
            (if (card.isTrump(t)) 1000 else 0) + (if (isBoss(card)) 100 else 0) + card.power(t) * 2 + (len[card.effSuit(t)] ?: 0)
        }
    }
}

// ---------------------------------------------------------------- playout simulator

class Sim(
    val rules: Rules,
    val deck: List<Card>,
    val trump: Suit?,
    val maker: Int,
    val alone: Boolean,
    val defenderAlone: Int?,
    val hands: List<MutableList<Card>>,
    val active: BooleanArray,
    var turn: Int,
    val trick: MutableList<Played>,
    val played: MutableSet<Card>,
    val tricksWon: IntArray,
    var tricksDone: Int,
) {
    private val activeCount = active.count { it }

    private fun nextActive(p: Int): Int {
        var q = rules.next(p)
        while (!active[q]) q = rules.next(q)
        return q
    }

    fun step(card: Card) {
        hands[turn].remove(card)
        trick.add(Played(turn, card))
        if (trick.size == activeCount) {
            val w = trickWinner(trick, trump).seat
            tricksWon[w]++
            for (p in trick) played.add(p.card)
            trick.clear()
            tricksDone++
            turn = w
        } else {
            turn = nextActive(turn)
        }
    }

    fun run() {
        while (tricksDone < 5) {
            val p = rules.partner(turn)?.takeIf { active[it] }
            step(Heuristic.play(PlayCtx(rules, deck, turn, hands[turn], trick, played, trump, maker, p, activeCount)))
        }
    }

    /** Points for [seat]'s side minus the average of everyone else's, plus a nudge per trick. */
    fun value(seat: Int): Double {
        val sc = scoreHand(rules, maker, alone, defenderAlone, tricksWon)
        val my = rules.team(seat)
        val others = (0 until rules.teams).filter { it != my }.map { sc.points[it] }.average()
        val myTricks = (0 until rules.players).filter { rules.team(it) == my }.sumOf { tricksWon[it] }
        return sc.points[my] - others + 0.02 * myTricks
    }
}

// ---------------------------------------------------------------- hidden-hand sampling

internal class Knowledge(
    val pool: List<Card>,
    val needs: Map<Int, Int>,
    val voids: Map<Int, Set<Suit>>,
    val forced: Map<Int, Card>,
)

internal object Sampler {
    /** What [seat] can legitimately know about the unseen cards during play. */
    fun knowledge(s: GameState, seat: Int, inferVoids: Boolean): Knowledge {
        val t = s.trump
        val c = s.contract!!
        val played = (s.tricks.flatten() + s.trick).map { it.card }.toSet()
        val excluded = HashSet<Card>(played)
        excluded += s.hands[seat]
        s.discards.filter { it.seat == seat }.forEach { excluded += it.card }
        val up = s.upcard!!
        if (!s.upcardPickedUp) excluded += up

        val voids = HashMap<Int, MutableSet<Suit>>()
        if (inferVoids) {
            for (tr in s.tricks + listOf(s.trick.toList())) {
                if (tr.isEmpty()) continue
                val led = tr[0].card.effSuit(t)
                for (p in tr.drop(1)) if (p.card.effSuit(t) != led) voids.getOrPut(p.seat) { mutableSetOf() } += led
            }
        }
        val forced = HashMap<Int, Card>()
        if (s.upcardPickedUp && !s.isOut(s.dealer) && s.dealer != seat && up !in played &&
            up.effSuit(t) !in voids[s.dealer].orEmpty()
        ) {
            forced[s.dealer] = up
            excluded += up
        }
        val pool = buildDeck(s.rules).filter { it !in excluded }
        val needs = (0 until s.players).filter { it != seat && !s.isOut(it) }.associateWith { s.hands[it].size }
        return Knowledge(pool, needs, voids, forced)
    }

    fun sample(k: Knowledge, trump: Suit?, rnd: Random): Map<Int, MutableList<Card>> {
        val seats = k.needs.keys.toList()
        repeat(30) { attempt ->
            val respectVoids = attempt < 25
            val out = seats.associateWith { mutableListOf<Card>() }
            val rem = IntArray(seats.size) { i -> k.needs[seats[i]]!! }
            for ((seat, card) in k.forced) {
                val i = seats.indexOf(seat)
                if (i >= 0 && rem[i] > 0) { out[seat]!!.add(card); rem[i]-- }
            }
            var kittyRoom = k.pool.size - rem.sum()
            var ok = kittyRoom >= 0
            for (card in k.pool.shuffled(rnd)) {
                if (!ok) break
                val suit = card.effSuit(trump)
                var total = kittyRoom
                for (i in seats.indices) if (rem[i] > 0 && (!respectVoids || suit !in k.voids[seats[i]].orEmpty())) total += rem[i]
                if (total == 0) { ok = false; break }
                var r = rnd.nextInt(total)
                if (r < kittyRoom) { kittyRoom--; continue }
                r -= kittyRoom
                for (i in seats.indices) {
                    if (rem[i] > 0 && (!respectVoids || suit !in k.voids[seats[i]].orEmpty())) {
                        if (r < rem[i]) { out[seats[i]]!!.add(card); rem[i]--; break }
                        r -= rem[i]
                    }
                }
            }
            if (ok && rem.all { it == 0 }) return out
        }
        error("unreachable: unconstrained sampling always succeeds")
    }
}

// ---------------------------------------------------------------- the bot

class Bot(val profile: BotProfile, seed: Long = Random.nextLong()) {
    private val rnd = Random(seed)
    private val aggr = (profile.aggression.coerceIn(0, 100) - 50) / 50.0 // -1..1
    private val iq = profile.iq.coerceIn(0, 100)

    // Bidding: thresholds are in expected team tricks (heuristic) or points (simulation).
    private val callAt = 3.0 - 0.6 * aggr
    private val aloneAt = 4.05 - 0.55 * aggr
    private val defendAloneAt = 3.0 - 0.6 * aggr
    private val evCallAt = 0.3 - 0.5 * aggr
    private val evAloneAt = 1.2 - 0.6 * aggr
    private val noise = (100 - iq) / 100.0 * 0.75

    // Play: blunders below 35, forgets played cards below 45, ignores partner below 25,
    // simulates from 65 up (more samples and void tracking as it climbs).
    private val blunder = max(0, 35 - iq) / 100.0
    private val remembers = iq >= 45
    private val partnerAware = iq >= 25
    private val readsTable = iq >= 35
    private val playSamples = if (iq >= 65) 30 + (iq - 65) * 8 else 0
    private val inferVoids = iq >= 75
    private val bidSamples = if (iq >= 85) 60 + (iq - 85) * 6 else 0

    fun choose(s: GameState, seat: Int): Action {
        check(s.turn == seat) { "not seat $seat's turn" }
        val a = when (s.phase) {
            Phase.MISDEAL_CHECK -> Action.Misdeal(true)
            Phase.GO_UNDER -> Action.GoUnder(Suit.entries.maxOf { Eval.strength(s.hands[seat], it) } < 1.6)
            Phase.NAME_JOKER_SUIT -> Action.NameSuit(Suit.entries.maxBy { st -> Eval.strength(best5(s.hands[seat] + Card.JOKER, st), st) })
            Phase.BID1 -> bid1(s, seat)
            Phase.BID2 -> bid2(s, seat)
            Phase.DISCARD, Phase.LONER_DISCARD -> Action.Discard(Eval.bestDiscard(s.hands[seat], s.trump))
            Phase.DEFEND_ALONE -> Action.DefendAlone(Eval.strength(s.hands[seat], s.trump) + jitter() >= defendAloneAt)
            Phase.PLAY -> Action.Play(play(s, seat))
            Phase.HAND_OVER -> Action.NextHand
            Phase.GAME_OVER -> error("game over")
        }
        check(a in s.legalActions()) { "bot chose illegal $a" }
        return a
    }

    private fun jitter(): Double = (rnd.nextDouble() + rnd.nextDouble() + rnd.nextDouble() - 1.5) * 2 * noise

    private fun best5(cards: List<Card>, t: Suit?): List<Card> =
        if (cards.size <= 5) cards else cards - Eval.bestDiscard(cards, t)

    private val Rules.partnerTricks get() = if (players == 4) 0.9 else 0.0

    private fun bid1(s: GameState, seat: Int): Action {
        val t = s.upSuit!!
        val up = s.upcard!!
        val hand = s.hands[seat]
        val legal = s.legalActions()
        val canSolo = Action.OrderUp(true) in legal
        val mustSolo = Action.OrderUp(false) !in legal

        if (bidSamples > 0) {
            val ev = if (mustSolo) Double.NEGATIVE_INFINITY else bidEV(s, seat, t, false, 1)
            val evA = if (canSolo) bidEV(s, seat, t, true, 1) else Double.NEGATIVE_INFINITY
            return when {
                canSolo && evA > ev && evA >= evAloneAt -> Action.OrderUp(true)
                !mustSolo && ev >= evCallAt -> Action.OrderUp(false)
                else -> Action.Pass
            }
        }

        val solo = (if (seat == s.dealer) Eval.strength(best5(hand + up, t), t) else Eval.strength(hand, t)) + jitter()
        var team = solo + s.rules.partnerTricks
        if (seat != s.dealer && readsTable) {
            val uv = Eval.trumpValue(up, t) * 0.8
            team += if (s.rules.team(s.dealer) == s.rules.team(seat)) uv else -uv
        }
        return when {
            canSolo && solo >= (if (mustSolo) aloneAt - 0.4 else aloneAt) -> Action.OrderUp(true)
            !mustSolo && team >= callAt -> Action.OrderUp(false)
            else -> Action.Pass
        }
    }

    private fun bid2(s: GameState, seat: Int): Action {
        val hand = s.hands[seat]
        val legal = s.legalActions()
        val stuck = Action.Pass !in legal
        val options = s.callableSuits()
        val canSolo = s.rules.allowAlone

        if (bidSamples > 0) {
            var bestSuit: Suit? = null
            var bestEv = Double.NEGATIVE_INFINITY
            for (o in options) {
                val ev = bidEV(s, seat, o, false, 2)
                if (ev > bestEv) { bestEv = ev; bestSuit = o }
            }
            if (bestEv == Double.NEGATIVE_INFINITY) return Action.Pass
            val evA = if (canSolo) bidEV(s, seat, bestSuit, true, 2) else Double.NEGATIVE_INFINITY
            return when {
                evA > bestEv && evA >= evAloneAt -> Action.Call(bestSuit, true)
                bestEv >= evCallAt || stuck -> Action.Call(bestSuit, false)
                else -> Action.Pass
            }
        }

        val nextSuit = s.upSuit?.sameColor
        val defendingDealer = s.rules.team(seat) != s.rules.team(s.dealer)
        val scored = options.map { o ->
            var v = Eval.strength(hand, o)
            // "Next": the turned-down suit's partner colour is a good call for the side left of the dealer.
            if (readsTable && o == nextSuit && defendingDealer) v += 0.15
            o to v + jitter()
        }
        val (suit, solo) = scored.maxBy { it.second }
        return when {
            canSolo && solo >= aloneAt -> Action.Call(suit, true)
            stuck || solo + s.rules.partnerTricks >= callAt -> Action.Call(suit, false)
            else -> Action.Pass
        }
    }

    /** Average points (ours minus theirs) if [seat] names [trump] right now. */
    private fun bidEV(s: GameState, seat: Int, trump: Suit?, alone: Boolean, round: Int): Double {
        val rules = s.rules
        val deck = buildDeck(rules)
        val up = s.upcard!!
        val mine = s.hands[seat]
        val pool = deck.filter { it !in mine && it != up }
        val others = (0 until rules.players).filter { it != seat }
        val mate = if (alone) rules.partner(seat) else null
        var total = 0.0
        repeat(bidSamples) {
            val sh = pool.shuffled(rnd)
            val hands = List(rules.players) { mutableListOf<Card>() }
            hands[seat].addAll(mine)
            others.forEachIndexed { i, p -> hands[p].addAll(sh.subList(i * 5, i * 5 + 5)) }
            if (round == 1 && s.dealer != mate) {
                hands[s.dealer].add(up)
                hands[s.dealer].remove(Eval.bestDiscard(hands[s.dealer], trump))
            }
            if (mate != null && rules.partnerBestCard) {
                val b = hands[mate].maxBy { it.power(trump) }
                hands[mate].remove(b); hands[seat].add(b)
                hands[seat].remove(Eval.bestDiscard(hands[seat], trump))
            }
            val active = BooleanArray(rules.players) { it != mate }
            fun nextActive(p: Int): Int { var q = rules.next(p); while (!active[q]) q = rules.next(q); return q }
            val leader = if (alone && rules.loneLead == LoneLead.LEFT_OF_LONER) nextActive(seat) else nextActive(s.dealer)
            val sim = Sim(rules, deck, trump, seat, alone, null, hands, active, leader, mutableListOf(), HashSet(), IntArray(rules.players), 0)
            sim.run()
            total += sim.value(seat)
        }
        return total / bidSamples
    }

    private fun play(s: GameState, seat: Int): Card {
        val legal = legalCards(s.hands[seat], s.trick, s.trump)
        if (legal.size == 1) return legal[0]
        if (rnd.nextDouble() < blunder) return legal.random(rnd)
        if (playSamples > 0) return simulatePlay(s, seat, legal)
        val played = if (remembers) s.tricks.flatten().map { it.card }.toSet() else emptySet()
        val mate = s.rules.partner(seat)?.takeIf { !s.isOut(it) }
        val ctx = PlayCtx(s.rules, buildDeck(s.rules), seat, s.hands[seat], s.trick, played, s.trump, s.contract!!.maker, mate, s.activeCount)
        return Heuristic.play(ctx, partnerAware)
    }

    private fun simulatePlay(s: GameState, seat: Int, legal: List<Card>): Card {
        val k = Sampler.knowledge(s, seat, inferVoids)
        val c = s.contract!!
        val deck = buildDeck(s.rules)
        val baseTricks = s.tricks.flatten().map { it.card }
        val totals = DoubleArray(legal.size)
        repeat(playSamples) {
            val deal = Sampler.sample(k, s.trump, rnd)
            for ((i, card) in legal.withIndex()) {
                val hands = List(s.players) { p ->
                    when {
                        p == seat -> s.hands[seat].toMutableList()
                        s.isOut(p) -> mutableListOf()
                        else -> deal[p]!!.toMutableList()
                    }
                }
                val active = BooleanArray(s.players) { !s.isOut(it) }
                val sim = Sim(
                    s.rules, deck, s.trump, c.maker, c.alone, c.defenderAlone, hands, active, s.turn,
                    s.trick.toMutableList(), baseTricks.toHashSet(), s.tricksWon.toIntArray(), s.tricks.size,
                )
                sim.step(card)
                sim.run()
                totals[i] += sim.value(seat)
            }
        }
        return legal[totals.indices.maxBy { totals[it] }]
    }
}
