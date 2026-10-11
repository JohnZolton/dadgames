package solitaire.core

/**
 * Klondike solver with full knowledge of the face-down cards ("thoughtful" solitaire). Used to make sure
 * every daily challenge can be won. A depth-first search with a transposition table, safe automatic
 * foundation moves and pruning of pointless shuffles. It never moves cards down from the foundations,
 * so it can miss a few rare wins; it answers "unknown" when it runs out of its node budget.
 *
 * Card codes are 0..51 (suit * 13 + rank index), see [code].
 */
class Solver(private val nodeLimit: Int = 150_000) {

    enum class Verdict { WIN, LOSS, UNKNOWN }

    class Result(val verdict: Verdict, val moves: List<Move>, val nodes: Int)

    private class S(
        val cols: Array<IntArray>,
        val down: IntArray,
        val found: IntArray, // cards on each suit's foundation (0..13)
        val stock: IntArray, // top = last
        val waste: IntArray, // top = last
    ) {
        fun won() = found.all { it == 13 }
    }

    private sealed interface SMove
    /** Draw [k] times, then play the waste top to the foundation (dest -1) or to column dest. */
    private class Talon(val k: Int, val dest: Int) : SMove
    private class Col(val src: Int, val count: Int, val dest: Int) : SMove

    private var nodes = 0
    private val seen = HashSet<Long>()
    private val path = ArrayList<SMove>()
    private var draw = 1

    fun solve(g: Klondike): Result {
        draw = g.draw
        nodes = 0
        seen.clear()
        path.clear()
        val found = IntArray(4)
        for (f in g.foundations) f.lastOrNull()?.let { found[it.suit.ordinal] = it.rank.value }
        val s = S(
            Array(7) { c -> IntArray(g.tableau[c].size) { g.tableau[c][it].code } },
            g.faceDown.toIntArray(), found,
            g.stock.map { it.code }.toIntArray(), g.waste.map { it.code }.toIntArray(),
        )
        val win = try { dfs(s, 0) } catch (e: Budget) { return Result(Verdict.UNKNOWN, emptyList(), nodes) }
        return if (win) Result(Verdict.WIN, toMoves(g), nodes) else Result(Verdict.LOSS, emptyList(), nodes)
    }

    private class Budget : RuntimeException(null, null, false, false)

    private fun dfs(s: S, depth: Int): Boolean {
        if (s.won()) return true
        if (++nodes > nodeLimit) throw Budget()
        if (!seen.add(hash(s))) return false
        if (depth > 400) return false
        for (m in moves(s)) {
            val c = apply(s, m)
            path.add(m)
            if (dfs(c, depth + 1)) return true
            path.removeAt(path.lastIndex)
        }
        return false
    }

    // ------------------------------------------------------------ helpers

    private fun suit(c: Int) = c / 13
    private fun rank(c: Int) = c % 13 + 1
    private fun red(c: Int) = suit(c) == 1 || suit(c) == 2

    private fun toFoundation(s: S, c: Int) = s.found[suit(c)] == rank(c) - 1

    /** Nothing still in play could ever need to sit on this card. */
    private fun safe(s: S, c: Int): Boolean {
        val r = rank(c)
        if (r <= 2) return true
        val opp = if (red(c)) intArrayOf(0, 3) else intArrayOf(1, 2)
        return s.found[opp[0]] >= r - 1 && s.found[opp[1]] >= r - 1
    }

    private fun accepts(s: S, col: Int, c: Int): Boolean {
        val k = s.cols[col]
        if (k.isEmpty()) return rank(c) == 13
        val top = k[k.size - 1]
        return red(top) != red(c) && rank(top) == rank(c) + 1
    }

    /** The waste top after each number of draws in one full cycle of the talon. */
    private fun talonReach(s: S): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var stock = s.stock
        var waste = s.waste
        val total = stock.size + waste.size
        if (total == 0) return out
        val seenTops = HashSet<Int>()
        val maxDraws = total + total / draw + 3
        for (k in 0..maxDraws) {
            if (k > 0) {
                if (stock.isEmpty()) { stock = waste.reversedArray(); waste = IntArray(0) } else {
                    val n = minOf(draw, stock.size)
                    val moved = IntArray(n) { stock[stock.size - 1 - it] }
                    waste = waste + moved
                    stock = stock.copyOf(stock.size - n)
                }
            }
            if (waste.isNotEmpty() && seenTops.add(waste[waste.size - 1])) out += k to waste[waste.size - 1]
        }
        return out
    }

    private fun moves(s: S): List<SMove> {
        // Safe moves are forced: no need to branch.
        for (c in 0..6) {
            val k = s.cols[c]
            if (k.isNotEmpty() && toFoundation(s, k[k.size - 1]) && safe(s, k[k.size - 1])) return listOf(Col(c, 1, -1))
        }
        if (s.waste.isNotEmpty()) {
            val w = s.waste[s.waste.size - 1]
            if (toFoundation(s, w) && safe(s, w)) return listOf(Talon(0, -1))
        }
        val out = ArrayList<SMove>()
        val flips = ArrayList<Pair<Int, SMove>>()
        val other = ArrayList<SMove>()
        for (c in 0..6) {
            val k = s.cols[c]
            if (k.isEmpty()) continue
            if (toFoundation(s, k[k.size - 1])) {
                // Sending up a card that uncovers a face-down card first.
                if (k.size - 1 == s.down[c] && s.down[c] > 0) flips += (100 + s.down[c]) to Col(c, 1, -1) else other += Col(c, 1, -1)
            }
            for (i in s.down[c] until k.size) {
                val card = k[i]
                val count = k.size - i
                var triedEmpty = false
                for (d in 0..6) {
                    if (d == c || !accepts(s, d, card)) continue
                    if (s.cols[d].isEmpty()) { if (triedEmpty) continue; triedEmpty = true } // all empty columns are alike
                    val uncovers = i == s.down[c] && s.down[c] > 0
                    val empties = i == 0
                    if (uncovers) flips += s.down[c] to Col(c, count, d)
                    else if (empties) {
                        // Moving a whole column only helps if a king is waiting for the space.
                        if (rank(card) != 13 && kingWaiting(s, c)) other += Col(c, count, d)
                    } else if (i > s.down[c] && toFoundation(s, k[i - 1])) other += Col(c, count, d)
                }
            }
        }
        flips.sortByDescending { it.first }
        out += flips.map { it.second }
        val talon = talonReach(s)
        for ((k, card) in talon) if (toFoundation(s, card)) out += Talon(k, -1)
        out += other
        for ((k, card) in talon) {
            var triedEmpty = false
            for (d in 0..6) {
                if (!accepts(s, d, card)) continue
                if (s.cols[d].isEmpty()) { if (triedEmpty) continue; triedEmpty = true }
                out += Talon(k, d)
            }
        }
        return out
    }

    private fun kingWaiting(s: S, except: Int): Boolean {
        for (c in 0..6) {
            if (c == except) continue
            val k = s.cols[c]
            for (i in s.down[c] until k.size) if (rank(k[i]) == 13 && i > 0) return true
        }
        for (c in s.stock) if (rank(c) == 13) return true
        for (c in s.waste) if (rank(c) == 13) return true
        return false
    }

    private fun apply(s: S, m: SMove): S {
        val cols = Array(7) { s.cols[it] }
        val down = s.down.copyOf()
        val found = s.found.copyOf()
        var stock = s.stock
        var waste = s.waste
        when (m) {
            is Talon -> {
                repeat(m.k) {
                    if (stock.isEmpty()) { stock = waste.reversedArray(); waste = IntArray(0) } else {
                        val n = minOf(draw, stock.size)
                        val moved = IntArray(n) { stock[stock.size - 1 - it] }
                        waste = waste + moved
                        stock = stock.copyOf(stock.size - n)
                    }
                }
                val card = waste[waste.size - 1]
                waste = waste.copyOf(waste.size - 1)
                if (m.dest < 0) found[suit(card)]++ else cols[m.dest] = cols[m.dest] + card
            }
            is Col -> {
                val k = cols[m.src]
                val moving = k.copyOfRange(k.size - m.count, k.size)
                val rest = k.copyOf(k.size - m.count)
                cols[m.src] = rest
                if (m.dest < 0) found[suit(moving[0])]++ else cols[m.dest] = cols[m.dest] + moving
                if (rest.isNotEmpty() && down[m.src] >= rest.size) down[m.src] = rest.size - 1
            }
        }
        return S(cols, down, found, stock, waste)
    }

    private fun hash(s: S): Long {
        // Column order doesn't matter for solvability: combine column hashes order-independently.
        var colsH = 0L
        for (c in 0..6) {
            var h = 1469598103934665603L xor s.down[c].toLong()
            for (x in s.cols[c]) h = (h xor x.toLong()) * 1099511628211L
            h = h * -0x61c8864680b583ebL
            colsH += h xor (h ushr 29)
        }
        var h = colsH
        for (f in s.found) h = h * 31 + f
        h = h * 131 + s.stock.size
        for (x in s.stock) h = h * 53 + x
        h = h * 137 + 77
        for (x in s.waste) h = h * 59 + x
        return h
    }

    /** Replay the found path through the real engine to get engine moves (draws expanded). */
    private fun toMoves(g0: Klondike): List<Move> {
        var g = g0
        val out = ArrayList<Move>()
        fun push(m: Move) {
            g = g.apply(m) ?: error("solver produced illegal move $m")
            out += m
        }
        for (m in path) when (m) {
            is Talon -> {
                repeat(m.k) { push(Move.Draw) }
                val card = g.waste.last()
                push(Move.Transfer(Loc.WASTE, if (m.dest < 0) Loc.foundation(g.foundationFor(card)!!) else Loc.tableau(m.dest)))
            }
            is Col -> {
                val card = g.tableau[m.src].let { it[it.size - m.count] }
                push(Move.Transfer(Loc.tableau(m.src), if (m.dest < 0) Loc.foundation(g.foundationFor(card)!!) else Loc.tableau(m.dest), m.count))
            }
        }
        // Finish: anything left is face up and goes straight up.
        while (!g.won) {
            val m = g.autoCompleteMove() ?: break
            push(m)
        }
        return out
    }
}
