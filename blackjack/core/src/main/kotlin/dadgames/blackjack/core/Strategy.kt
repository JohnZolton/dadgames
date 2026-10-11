package dadgames.blackjack.core

import dadgames.cards.Card

/**
 * Basic strategy computed from the rules, not copied from a chart.
 *
 * For each dealer up card it works out the expected value of every move (stand, hit, double,
 * split, surrender) by exact recursion over the cards that can come next. Card odds come from
 * the shoe ([Rules.decks] decks minus the dealer's up card) and stay fixed through the recursion
 * (the standard "total-dependent" basic strategy). When the dealer shows an ace or a ten, the
 * odds are conditioned on the dealer not having blackjack, because the dealer has already
 * peeked by the time you decide. Splits are valued as two hands that each start from one card
 * (no further resplits counted), honouring double-after-split and one-card split aces.
 *
 * Results match the published charts for the common rule sets (see StrategyTest).
 */
class Strategy(val rules: Rules) {
    private val calcs = arrayOfNulls<UpCalc>(11 * 11 * 11)

    /** Odds for this up card with the up card and the hand's first two cards out of the shoe. */
    private fun calc(up: Int, hand: List<Card>): UpCalc {
        var a = hand.getOrNull(0)?.points ?: 0
        var b = hand.getOrNull(1)?.points ?: 0
        if (a > b) { val t = a; a = b; b = t }
        val k = (up * 11 + a) * 11 + b
        return calcs[k] ?: UpCalc(rules, up, a, b).also { calcs[k] = it }
    }

    /** Expected value (in units of the hand's current bet) of each move in [legal]. */
    fun evs(hand: List<Card>, up: Card, legal: Collection<Action>, splitAces: Boolean = false): Map<Action, Double> {
        val c = calc(up.points, hand)
        val t = totals(hand)
        val out = LinkedHashMap<Action, Double>()
        for (a in legal) {
            val ev = when (a) {
                Action.STAND -> c.stand(t.best)
                Action.HIT -> if (splitAces) continue else c.hit(t.hard, t.hasAce)
                Action.DOUBLE -> c.double(t.hard, t.hasAce)
                Action.SPLIT -> c.split(hand[0].points)
                Action.SURRENDER -> -0.5
                // Insurance is a losing side bet without card counting: basic strategy never takes it.
                Action.INSURANCE -> -1.0
                Action.NO_INSURANCE -> 0.0
            }
            out[a] = ev
        }
        return out
    }

    fun best(hand: List<Card>, up: Card, legal: Collection<Action>, splitAces: Boolean = false): Action {
        if (Action.NO_INSURANCE in legal) return Action.NO_INSURANCE
        val e = evs(hand, up, legal, splitAces)
        val top = e.maxBy { it.value }
        // The fixed-odds model slightly overvalues hitting (it never runs out of small cards), so a
        // double within 0.1% of hitting is taken. This matches the exact multi-deck charts (A2 v 5).
        val d = e[Action.DOUBLE]
        if (d != null && top.key == Action.HIT && d >= top.value - DOUBLE_EDGE) return Action.DOUBLE
        return top.key
    }

    /** The basic-strategy move for the hand that's up right now. */
    fun best(g: Game): Action? {
        val legal = g.legal()
        if (legal.isEmpty()) return null
        if (g.phase == Phase.INSURANCE) return Action.NO_INSURANCE
        val h = g.hand ?: return null
        return best(h.plain, g.up!!, legal, h.splitAces)
    }
}

private const val DOUBLE_EDGE = 0.001

private class UpCalc(val rules: Rules, val up: Int, own1: Int, own2: Int) {
    /** p[v]: chance the next card has value v (1 = ace, 10 = ten/face). */
    val p = DoubleArray(11)
    /** Dealer final: index 0..4 = 17..21, 5 = bust. */
    val dealer = DoubleArray(6)

    init {
        val counts = DoubleArray(11)
        for (v in 1..9) counts[v] = 4.0 * rules.decks
        counts[10] = 16.0 * rules.decks
        counts[up] -= 1.0
        if (own1 > 0) counts[own1] -= 1.0
        if (own2 > 0) counts[own2] -= 1.0
        val n = counts.sum()
        for (v in 1..10) p[v] = counts[v] / n
        computeDealer()
    }

    private fun computeDealer() {
        val memo = HashMap<Int, DoubleArray>()
        fun finish(h: Int, ace: Boolean): DoubleArray = memo.getOrPut(h * 2 + if (ace) 1 else 0) {
            val soft = ace && h + 10 <= 21
            val total = if (soft) h + 10 else h
            val r = DoubleArray(6)
            when {
                total > 21 -> r[5] = 1.0
                total >= 18 || (total == 17 && !(soft && rules.dealerHitsSoft17)) -> r[total - 17] = 1.0
                else -> for (v in 1..10) {
                    val f = finish(h + v, ace || v == 1)
                    for (i in 0..5) r[i] += p[v] * f[i]
                }
            }
            r
        }
        // Hole card, excluding the one that would give the dealer blackjack (dealer peeked).
        val q = p.copyOf()
        if (up == 1) q[10] = 0.0
        if (up == 10) q[1] = 0.0
        val s = q.sum()
        for (v in 1..10) {
            if (q[v] == 0.0) continue
            val f = finish(up + v, up == 1 || v == 1)
            for (i in 0..5) dealer[i] += q[v] / s * f[i]
        }
    }

    private val standCache = DoubleArray(23) { Double.NaN }

    fun stand(total: Int): Double {
        if (total > 21) return -1.0
        val t = maxOf(total, 16)
        if (!standCache[t].isNaN()) return standCache[t]
        var ev = dealer[5]
        for (i in 0..4) {
            val d = 17 + i
            ev += when {
                t > d -> dealer[i]
                t < d -> -dealer[i]
                else -> 0.0
            }
        }
        standCache[t] = ev
        return ev
    }

    private fun best(h: Int, ace: Boolean): Int = if (ace && h + 10 <= 21) h + 10 else h

    private val hitCache = HashMap<Int, Double>()

    /** Hit once, then keep playing the best of hit/stand. */
    fun hit(h: Int, ace: Boolean): Double {
        if (h > 21) return -1.0
        return hitCache.getOrPut(h * 2 + if (ace) 1 else 0) {
            var ev = 0.0
            for (v in 1..10) ev += p[v] * hitOrStand(h + v, ace || v == 1)
            ev
        }
    }

    private fun hitOrStand(h: Int, ace: Boolean): Double {
        if (h > 21) return -1.0
        val t = best(h, ace)
        if (t == 21) return stand(21)
        return maxOf(stand(t), hit(h, ace))
    }

    fun double(h: Int, ace: Boolean): Double {
        var ev = 0.0
        for (v in 1..10) ev += p[v] * stand(best(h + v, ace || v == 1))
        return 2 * ev
    }

    private fun canDouble(h: Int, ace: Boolean): Boolean = when (rules.doubleRule) {
        DoubleRule.ANY_TWO -> true
        DoubleRule.NINE_TO_ELEVEN -> !(ace && h + 10 <= 21) && h in 9..11
    }

    /** Both hands together, in units of one original bet. */
    fun split(v: Int): Double {
        var per = 0.0
        for (x in 1..10) {
            val h = v + x
            val ace = v == 1 || x == 1
            val t = best(h, ace)
            per += p[x] * when {
                v == 1 -> stand(t) // split aces: one card each
                t == 21 -> stand(21) // 21 after a split is not blackjack
                else -> {
                    var e = maxOf(stand(t), hit(h, ace))
                    if (rules.doubleAfterSplit && canDouble(h, ace)) e = maxOf(e, double(h, ace))
                    e
                }
            }
        }
        return 2 * per
    }
}
