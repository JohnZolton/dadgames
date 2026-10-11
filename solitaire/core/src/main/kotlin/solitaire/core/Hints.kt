package solitaire.core

/**
 * Tap-to-move: the best legal destination for the card at [index] in pile [loc] (a run in the tableau
 * moves with every card above it). Foundation first for a top card, then a column that already has
 * cards, then an empty column. Returns null if the card can't go anywhere.
 */
fun Klondike.autoMove(loc: Loc, index: Int): Move? {
    if (won) return null
    val pile = pile(loc)
    return when (loc.type) {
        PileType.STOCK -> if (canDraw()) Move.Draw else null
        PileType.WASTE -> {
            if (pile.isEmpty() || index != pile.lastIndex) return null
            val c = pile.last()
            foundationFor(c)?.let { return Move.Transfer(loc, Loc.foundation(it)) }
            bestColumn(c, exclude = -1, fromBottomOfColumn = false)?.let { Move.Transfer(loc, Loc.tableau(it)) }
        }
        PileType.FOUNDATION -> {
            if (pile.isEmpty() || index != pile.lastIndex) return null
            bestColumn(pile.last(), exclude = -1, fromBottomOfColumn = false)?.let { Move.Transfer(loc, Loc.tableau(it)) }
        }
        PileType.TABLEAU -> {
            if (!isRun(loc.index, index)) return null
            val c = pile[index]
            val count = pile.size - index
            if (count == 1) foundationFor(c)?.let { return Move.Transfer(loc, Loc.foundation(it)) }
            bestColumn(c, exclude = loc.index, fromBottomOfColumn = index == 0)?.let { Move.Transfer(loc, Loc.tableau(it), count) }
        }
    }
}

private fun Klondike.bestColumn(card: dadgames.cards.Card, exclude: Int, fromBottomOfColumn: Boolean): Int? {
    var empty: Int? = null
    for (t in 0..6) {
        if (t == exclude || !tableauAccepts(t, card)) continue
        if (tableau[t].isNotEmpty()) return t
        // A king that already sits at the bottom of a column gains nothing from moving to another empty one.
        if (empty == null && !fromBottomOfColumn) empty = t
    }
    return empty
}

/** A suggested move, with the reason in plain words. */
data class Hint(val move: Move, val text: String)

/**
 * A move that makes progress, best first: uncover a face-down card, build the foundations, bring a card
 * out of the waste, free a space. Falls back to drawing from the stock if that will eventually help.
 * Null when nothing helps any more ("No moves left").
 */
fun Klondike.hint(): Hint? {
    if (won) return null
    usefulMoves().firstOrNull()?.let { return Hint(it, describe(it)) }
    if (talonCanHelp()) {
        val text = if (stock.isEmpty()) "Tap the stock to turn the cards back over." else "Tap the stock to draw a card."
        return Hint(Move.Draw, text)
    }
    return null
}

/** True when no move will ever help again (the player is stuck). */
fun Klondike.noMovesLeft(): Boolean = !won && !canAutoComplete && hint() == null

/** Board moves that make progress, in order of preference. Never includes drawing. */
fun Klondike.usefulMoves(): List<Move> {
    val uncover = mutableListOf<Pair<Int, Move>>()
    val found = mutableListOf<Move>()
    val fromWaste = mutableListOf<Move>()
    val other = mutableListOf<Move>()
    waste.lastOrNull()?.let { c ->
        foundationFor(c)?.let { found += Move.Transfer(Loc.WASTE, Loc.foundation(it)) }
        bestColumn(c, -1, false)?.let { fromWaste += Move.Transfer(Loc.WASTE, Loc.tableau(it)) }
    }
    for (src in 0..6) {
        val col = tableau[src]
        if (col.isEmpty()) continue
        val down = faceDown[src]
        foundationFor(col.last())?.let { f ->
            val m = Move.Transfer(Loc.tableau(src), Loc.foundation(f))
            if (col.size - 1 == down && down > 0) uncover += (100 + down) to m else found += m
        }
        for (i in down until col.size) {
            if (!isRun(src, i)) continue
            val dest = bestColumn(col[i], src, i == 0) ?: continue
            val m = Move.Transfer(Loc.tableau(src), Loc.tableau(dest), col.size - i)
            when {
                i == down && down > 0 -> uncover += down to m
                i == 0 && col[0].rank != dadgames.cards.Rank.KING && kingWaiting(src) -> other += m
                i > down && foundationFor(col[i - 1]) != null -> other += m
            }
        }
    }
    uncover.sortByDescending { it.first }
    return uncover.map { it.second } + found + fromWaste + other
}

private fun Klondike.kingWaiting(except: Int): Boolean {
    for (c in 0..6) {
        if (c == except) continue
        val col = tableau[c]
        for (i in faceDown[c] until col.size) if (col[i].rank == dadgames.cards.Rank.KING && i > 0) return true
    }
    return (stock + waste).any { it.rank == dadgames.cards.Rank.KING }
}

/** Will drawing through the stock (and turning it over) ever bring up a card that can be played? */
fun Klondike.talonCanHelp(): Boolean {
    if (!canDraw()) return false
    var g = this.copy(history = emptyList())
    val total = stock.size + waste.size
    repeat(total + total / draw + 3) {
        g = g.apply(Move.Draw) ?: return false
        val top = g.waste.lastOrNull() ?: return@repeat
        if (g.foundationFor(top) != null) return true
        for (t in 0..6) if (g.tableauAccepts(t, top)) return true
    }
    return false
}

/** "Move the Seven of Spades onto the Eight of Hearts." */
fun Klondike.describe(m: Move): String = when (m) {
    Move.Draw -> if (stock.isEmpty()) "Tap the stock to turn the cards back over." else "Tap the stock to draw a card."
    is Move.Transfer -> {
        val src = pile(m.from)
        val card = src[src.size - m.count]
        val what = if (m.count > 1) "the ${card.spoken} and the cards on it" else "the ${card.spoken}"
        val where = when (m.to.type) {
            PileType.FOUNDATION -> "up to the foundation"
            PileType.TABLEAU -> tableau[m.to.index].lastOrNull()?.let { "onto the ${it.spoken}" } ?: "into the empty space"
            else -> ""
        }
        "Move $what $where."
    }
}
