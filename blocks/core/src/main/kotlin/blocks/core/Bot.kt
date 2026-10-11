package blocks.core

/** A suggested move: put the piece in [slot] with its top-left at [anchor]. */
data class Move(val slot: Int, val anchor: Int)

/**
 * Hint engine. Searches every order of the pieces left in the tray (it can't see future trays), keeping the best
 * few placements per step, and scores the final board on how open and unfragmented it is.
 */
class Bot(private val breadth: Int = 6) {

    fun choose(g: GameState): Move? {
        if (g.over) return null
        var best: Move? = null
        var bestV = Double.NEGATIVE_INFINITY
        val pieces = g.tray.withIndex().filter { it.value >= 0 }
        for ((slot, id) in pieces) {
            if (pieces.any { it.index < slot && it.value == id }) continue
            val rest = pieces.filter { it.index != slot }.map { it.value }
            for (anchor in Shapes[id].fits(g.board)) {
                val step = step(g.board, g.gems, g.streak, Shapes[id].masks[anchor]!!, id)
                val v = step.gain + search(step.board, step.gems, step.streak, rest, 1)
                if (v > bestV) { bestV = v; best = Move(slot, anchor) }
            }
        }
        return best
    }

    private class Step(val board: Bits, val gems: Bits, val streak: Int, val gain: Double)

    private fun step(board: Bits, gems: Bits, streak: Int, mask: Bits, id: Int): Step {
        val filled = board or mask
        var cleared = Bits.EMPTY
        var combo = 0
        for (r in Regions.all) if (filled.containsAll(r.mask)) { cleared = cleared or r.mask; combo++ }
        val s = if (combo > 0) streak + 1 else 0
        val taken = (gems and cleared).count
        val gain = Shapes[id].size + Scoring.clearBonus(combo, s) + taken * 80.0
        return Step(filled andNot cleared, gems andNot cleared, s, gain)
    }

    private fun search(board: Bits, gems: Bits, streak: Int, pieces: List<Int>, depth: Int): Double {
        if (pieces.isEmpty()) return evaluate(board, gems)
        var best = Double.NEGATIVE_INFINITY
        val tried = HashSet<Int>()
        for ((i, id) in pieces.withIndex()) {
            if (!tried.add(id)) continue
            val rest = pieces.filterIndexed { j, _ -> j != i }
            val options = Shapes[id].fits(board).map { a ->
                val st = step(board, gems, streak, Shapes[id].masks[a]!!, id)
                st to st.gain + evaluate(st.board, st.gems)
            }.sortedByDescending { it.second }.take(breadth)
            for ((st, _) in options) {
                val v = st.gain + search(st.board, st.gems, st.streak, rest, depth + 1)
                if (v > best) best = v
            }
        }
        // Couldn't place every piece: a dead end, ranked by how bad the board is.
        return if (best == Double.NEGATIVE_INFINITY) -5000.0 + evaluate(board, gems) else best
    }

    /** Higher is better: few isolated holes, little ragged edge, boxes either empty or nearly done. */
    fun evaluate(board: Bits, gems: Bits = Bits.EMPTY): Double {
        var holes = 0
        var edges = 0
        for (cell in 0 until CELLS) {
            if (board[cell]) continue
            val r = rowOf(cell)
            val c = colOf(cell)
            var blocked = 0
            if (r == 0 || board[cell - SIZE]) blocked++
            if (r == SIZE - 1 || board[cell + SIZE]) blocked++
            if (c == 0 || board[cell - 1]) blocked++
            if (c == SIZE - 1 || board[cell + 1]) blocked++
            // Walls count half: hugging an edge is fine, a crack between blocks isn't.
            if (r > 0 && board[cell - SIZE]) edges++
            if (r < SIZE - 1 && board[cell + SIZE]) edges++
            if (c > 0 && board[cell - 1]) edges++
            if (c < SIZE - 1 && board[cell + 1]) edges++
            if (blocked == 4) holes++
        }
        var boxPenalty = 0.0
        for (r in Regions.all) if (r.kind == RegionKind.BOX) {
            val n = (board and r.mask).count
            if (n in 1..8) boxPenalty += if (n >= 6) 0.5 else 1.5
        }
        var gemScore = 0.0
        if (!gems.isEmpty) for (g in gems.cells()) {
            var most = 0
            for (r in Regions.all) if (r.mask[g]) most = maxOf(most, (board and r.mask).count)
            gemScore += most * 3.0
        }
        val empty = CELLS - board.count
        return empty * 0.6 - holes * 14.0 - edges * 1.6 - boxPenalty * 2.0 + gemScore
    }
}
