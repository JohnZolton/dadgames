package solitaire.core

import dadgames.cards.Card
import dadgames.cards.Rank
import dadgames.cards.Suit
import dadgames.cards.standardDeck
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
enum class PileType { STOCK, WASTE, FOUNDATION, TABLEAU }

/** A pile on the table. Foundations and tableau columns are numbered from 0, left to right. */
@Serializable
data class Loc(val type: PileType, val index: Int = 0) {
    companion object {
        val STOCK = Loc(PileType.STOCK)
        val WASTE = Loc(PileType.WASTE)
        fun foundation(i: Int) = Loc(PileType.FOUNDATION, i)
        fun tableau(i: Int) = Loc(PileType.TABLEAU, i)
    }
}

@Serializable
sealed interface Move {
    /** Turn cards from the stock onto the waste, or turn the waste back over when the stock is empty. */
    @Serializable @SerialName("draw")
    data object Draw : Move

    /** Move the top [count] cards of [from] onto [to]. */
    @Serializable @SerialName("move")
    data class Transfer(val from: Loc, val to: Loc, val count: Int = 1) : Move
}

/** What undo needs to put a move back exactly. */
@Serializable
data class Step(
    val move: Move,
    val drawn: Int = 0,
    val recycled: Boolean = false,
    val flipped: Boolean = false,
    val scoreBefore: Int,
    val ticksBefore: Int,
    val movesBefore: Int,
)

/**
 * Windows "standard" Klondike scoring, which dad's app uses. [research/solitaire.md]
 */
object Scoring {
    const val WASTE_TO_TABLEAU = 5
    const val TO_FOUNDATION = 10
    const val TURN_OVER = 5
    const val FOUNDATION_TO_TABLEAU = -15
    /** Draw 1: every time the waste is turned back over. */
    const val RECYCLE_DRAW1 = -100
    /** Draw 3: every time the waste is turned back over after the third pass. */
    const val RECYCLE_DRAW3 = -20
    const val FREE_DRAW3_RECYCLES = 3
    const val TIME_PENALTY = 2
    const val TIME_PENALTY_EVERY_MS = 10_000L
    const val BONUS_NUMERATOR = 700_000
    const val BONUS_MIN_SECONDS = 30
    /**
     * Wins faster than this get the same bonus as a win in exactly this time (700000 / 150 = 4666).
     * Windows has no such cap, but dad's records only fit with one: his top-10 wins took 1:44 to 2:29 and
     * all scored 5195-5371 (uncapped, his 1:56 win alone would get a 6034 bonus), and his fastest win
     * (1:28) isn't in his top 10 at all. With the cap his wins work out to 530-700 points before the bonus,
     * which is what Windows scoring gives a clean game. [research/README.md]
     */
    const val BONUS_TIME_FLOOR = 150L

    /** Bonus for a win that took [seconds]: none for 30 s or less (like Windows), else 700000 / seconds. */
    fun timeBonus(seconds: Long): Int =
        if (seconds <= BONUS_MIN_SECONDS) 0 else (BONUS_NUMERATOR / maxOf(seconds, BONUS_TIME_FLOOR)).toInt()
}

/**
 * One game of Klondike. Immutable: every action returns a new state, so it is a safe snapshot to save,
 * render and hand to the hint search. Undo history is part of the state, so it survives the app being killed.
 *
 * Piles are listed bottom to top (last = top card).
 */
@Serializable
data class Klondike(
    val seed: Long,
    val draw: Int = 1,
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<Card>>,
    /** How many cards at the bottom of each tableau column are face down. */
    val faceDown: List<Int>,
    val score: Int = 0,
    val moves: Int = 0,
    val recycles: Int = 0,
    val elapsedMs: Long = 0,
    /** Number of 10-second time penalties applied so far. */
    val ticks: Int = 0,
    /** The clock starts on the first move. */
    val started: Boolean = false,
    val won: Boolean = false,
    val bonus: Int = 0,
    /** Daily challenge date (yyyy-MM-dd), or null for a random deal. */
    val daily: String? = null,
    val history: List<Step> = emptyList(),
) {
    val seconds: Long get() = elapsedMs / 1000
    val finalScore: Int get() = score + bonus
    val canUndo: Boolean get() = history.isNotEmpty() && !won

    fun pile(loc: Loc): List<Card> = when (loc.type) {
        PileType.STOCK -> stock
        PileType.WASTE -> waste
        PileType.FOUNDATION -> foundations[loc.index]
        PileType.TABLEAU -> tableau[loc.index]
    }

    /** Every card face up, stock and waste used up: the rest can be played automatically. */
    val canAutoComplete: Boolean
        get() = !won && stock.isEmpty() && waste.isEmpty() && faceDown.all { it == 0 } && tableau.any { it.isNotEmpty() }

    // ------------------------------------------------------------ rules

    fun canDraw(): Boolean = !won && (stock.isNotEmpty() || waste.isNotEmpty())

    fun foundationAccepts(i: Int, card: Card): Boolean {
        val f = foundations[i]
        return if (f.isEmpty()) card.rank == Rank.ACE else f.last().suit == card.suit && f.last().rank.value + 1 == card.rank.value
    }

    fun tableauAccepts(i: Int, card: Card): Boolean {
        val col = tableau[i]
        if (col.isEmpty()) return card.rank == Rank.KING
        val top = col.last()
        return top.red != card.red && top.rank.value == card.rank.value + 1
    }

    /** Which foundation a card would go to (its suit's pile, or the first empty one for an ace). */
    fun foundationFor(card: Card): Int? {
        for (i in 0..3) if (foundations[i].isNotEmpty() && foundationAccepts(i, card)) return i
        if (card.rank == Rank.ACE) for (i in 0..3) if (foundations[i].isEmpty()) return i
        return null
    }

    /** Index in column [col] of the first face-up card, i.e. how far down a run can be picked up. */
    fun firstFaceUp(col: Int): Int = faceDown[col]

    /** True if the cards from [index] to the top of column [col] are face up and form a run. */
    fun isRun(col: Int, index: Int): Boolean {
        val c = tableau[col]
        if (index < faceDown[col] || index >= c.size) return false
        for (k in index until c.size - 1) {
            val a = c[k]; val b = c[k + 1]
            if (a.red == b.red || a.rank.value != b.rank.value + 1) return false
        }
        return true
    }

    fun isLegal(m: Move): Boolean = apply(m) != null

    /** Every legal move (used by the fuzzer, hints and auto-move). */
    fun legalMoves(): List<Move> {
        if (won) return emptyList()
        val out = mutableListOf<Move>()
        if (canDraw()) out += Move.Draw
        waste.lastOrNull()?.let { c ->
            for (f in 0..3) if (foundationAccepts(f, c)) out += Move.Transfer(Loc.WASTE, Loc.foundation(f))
            for (t in 0..6) if (tableauAccepts(t, c)) out += Move.Transfer(Loc.WASTE, Loc.tableau(t))
        }
        for (src in 0..6) {
            val col = tableau[src]
            if (col.isEmpty()) continue
            for (f in 0..3) if (foundationAccepts(f, col.last())) out += Move.Transfer(Loc.tableau(src), Loc.foundation(f))
            for (i in faceDown[src] until col.size) {
                if (!isRun(src, i)) continue
                for (t in 0..6) if (t != src && tableauAccepts(t, col[i])) out += Move.Transfer(Loc.tableau(src), Loc.tableau(t), col.size - i)
            }
        }
        for (f in 0..3) foundations[f].lastOrNull()?.let { c ->
            for (t in 0..6) if (tableauAccepts(t, c)) out += Move.Transfer(Loc.foundation(f), Loc.tableau(t))
        }
        return out
    }

    // ------------------------------------------------------------ actions

    /** Apply a move. Returns null if it isn't legal. */
    fun apply(m: Move): Klondike? {
        if (won) return null
        return when (m) {
            Move.Draw -> drawCards()
            is Move.Transfer -> transfer(m)
        }
    }

    private fun recorded(step: Step): List<Step> = history + step

    private fun drawCards(): Klondike? {
        if (stock.isEmpty()) {
            if (waste.isEmpty()) return null
            val r = recycles + 1
            val penalty = when {
                draw == 1 -> Scoring.RECYCLE_DRAW1
                r > Scoring.FREE_DRAW3_RECYCLES -> Scoring.RECYCLE_DRAW3
                else -> 0
            }
            return copy(
                stock = waste.reversed(), waste = emptyList(), recycles = r,
                score = maxOf(0, score + penalty), moves = moves + 1, started = true,
                history = recorded(Step(Move.Draw, recycled = true, scoreBefore = score, ticksBefore = ticks, movesBefore = moves)),
            )
        }
        val n = minOf(draw, stock.size)
        val drawn = stock.takeLast(n).reversed()
        return copy(
            stock = stock.dropLast(n), waste = waste + drawn, moves = moves + 1, started = true,
            history = recorded(Step(Move.Draw, drawn = n, scoreBefore = score, ticksBefore = ticks, movesBefore = moves)),
        )
    }

    private fun transfer(m: Move.Transfer): Klondike? {
        val (from, to, count) = m
        if (count < 1 || from == to) return null
        val src = pile(from)
        if (src.size < count) return null
        val moving = src.takeLast(count)
        val first = moving.first()
        // Source rules.
        when (from.type) {
            PileType.STOCK -> return null
            PileType.WASTE, PileType.FOUNDATION -> if (count != 1) return null
            PileType.TABLEAU -> if (!isRun(from.index, src.size - count)) return null
        }
        // Destination rules.
        when (to.type) {
            PileType.STOCK, PileType.WASTE -> return null
            PileType.FOUNDATION -> if (count != 1 || from.type == PileType.FOUNDATION || !foundationAccepts(to.index, first)) return null
            PileType.TABLEAU -> if (!tableauAccepts(to.index, first)) return null
        }
        var points = when {
            to.type == PileType.FOUNDATION -> Scoring.TO_FOUNDATION
            from.type == PileType.WASTE -> Scoring.WASTE_TO_TABLEAU
            from.type == PileType.FOUNDATION -> Scoring.FOUNDATION_TO_TABLEAU
            else -> 0
        }
        var newStock = stock
        var newWaste = waste
        val newFound = foundations.toMutableList()
        val newTab = tableau.toMutableList()
        val newDown = faceDown.toMutableList()
        val remaining = src.dropLast(count)
        when (from.type) {
            PileType.WASTE -> newWaste = remaining
            PileType.FOUNDATION -> newFound[from.index] = remaining
            PileType.TABLEAU -> newTab[from.index] = remaining
            PileType.STOCK -> newStock = remaining
        }
        when (to.type) {
            PileType.FOUNDATION -> newFound[to.index] = foundations[to.index] + moving
            PileType.TABLEAU -> newTab[to.index] = tableau[to.index] + moving
            else -> {}
        }
        var flipped = false
        if (from.type == PileType.TABLEAU) {
            val i = from.index
            if (remaining.isNotEmpty() && newDown[i] >= remaining.size) {
                newDown[i] = remaining.size - 1
                flipped = true
                points += Scoring.TURN_OVER
            }
        }
        val next = copy(
            stock = newStock, waste = newWaste, foundations = newFound, tableau = newTab, faceDown = newDown,
            score = maxOf(0, score + points), moves = moves + 1, started = true,
            history = recorded(Step(m, flipped = flipped, scoreBefore = score, ticksBefore = ticks, movesBefore = moves)),
        )
        return if (next.foundations.all { it.size == 13 }) next.copy(won = true, bonus = Scoring.timeBonus(next.seconds)) else next
    }

    /**
     * Take back the last move: cards, score and move count go back exactly; the clock and the time
     * penalty already charged do not.
     */
    fun undo(): Klondike? {
        if (!canUndo) return null
        val s = history.last()
        val restoredScore = maxOf(0, s.scoreBefore - Scoring.TIME_PENALTY * (ticks - s.ticksBefore))
        val base = copy(history = history.dropLast(1), score = restoredScore, moves = s.movesBefore)
        return when (val m = s.move) {
            Move.Draw -> if (s.recycled) {
                base.copy(waste = stock.reversed(), stock = emptyList(), recycles = recycles - 1)
            } else {
                val back = waste.takeLast(s.drawn).reversed()
                base.copy(waste = waste.dropLast(s.drawn), stock = stock + back)
            }
            is Move.Transfer -> {
                val moving = pile(m.to).takeLast(m.count)
                val newStock = stock
                var newWaste = waste
                val newFound = foundations.toMutableList()
                val newTab = tableau.toMutableList()
                val newDown = faceDown.toMutableList()
                when (m.to.type) {
                    PileType.FOUNDATION -> newFound[m.to.index] = foundations[m.to.index].dropLast(m.count)
                    PileType.TABLEAU -> newTab[m.to.index] = tableau[m.to.index].dropLast(m.count)
                    else -> error("bad history")
                }
                when (m.from.type) {
                    PileType.WASTE -> newWaste = waste + moving
                    PileType.FOUNDATION -> newFound[m.from.index] = foundations[m.from.index] + moving
                    PileType.TABLEAU -> {
                        newTab[m.from.index] = tableau[m.from.index] + moving
                        if (s.flipped) newDown[m.from.index] = newDown[m.from.index] + 1
                    }
                    PileType.STOCK -> error("bad history")
                }
                base.copy(stock = newStock, waste = newWaste, foundations = newFound, tableau = newTab, faceDown = newDown)
            }
        }
    }

    /**
     * Advance the clock to [ms] of play. Every full 10 seconds costs 2 points (never below zero).
     * The clock only runs once the first move is made, and stops when the game is won.
     */
    fun tick(ms: Long): Klondike {
        if (!started || won || ms <= elapsedMs) return this
        val t = (ms / Scoring.TIME_PENALTY_EVERY_MS).toInt()
        var sc = score
        repeat(t - ticks) { sc = maxOf(0, sc - Scoring.TIME_PENALTY) }
        return copy(elapsedMs = ms, ticks = t, score = sc)
    }

    /** The next card to send up during auto-complete: the lowest top card. */
    fun autoCompleteMove(): Move.Transfer? {
        if (!canAutoComplete) return null
        var best: Move.Transfer? = null
        var bestRank = 99
        for (t in 0..6) {
            val c = tableau[t].lastOrNull() ?: continue
            val f = foundationFor(c) ?: continue
            if (c.rank.value < bestRank) { bestRank = c.rank.value; best = Move.Transfer(Loc.tableau(t), Loc.foundation(f)) }
        }
        return best
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(s: String): Klondike = json.decodeFromString(serializer(), s)

        /** A fair shuffle of one deck, dealt the usual way: 7 columns of 1..7 cards, top card face up. */
        fun deal(seed: Long, draw: Int = 1, daily: String? = null): Klondike {
            val deck = standardDeck().shuffled(Random(seed)).toMutableList()
            val cols = List(7) { mutableListOf<Card>() }
            for (row in 0 until 7) for (c in row until 7) cols[c] += deck.removeAt(deck.lastIndex)
            return Klondike(
                seed = seed, draw = draw, stock = deck.toList(), waste = emptyList(),
                foundations = List(4) { emptyList() }, tableau = cols.map { it.toList() },
                faceDown = List(7) { it }, daily = daily,
            )
        }
    }
}

/** Every card exactly once, nothing lost or duplicated. */
fun Klondike.allCards(): List<Card> = stock + waste + foundations.flatten() + tableau.flatten()

/** Short index for compact encodings: 0..51. */
val Card.code: Int get() = suit.ordinal * 13 + rank.ordinal

fun cardOf(code: Int): Card = Card(Rank.entries[code % 13], Suit.entries[code / 13])
