package dadgames.sudoku.core

import kotlin.random.Random

/**
 * Geometry of a 9x9 grid. Cells are numbered 0..80 row by row. Units 0..8 are rows, 9..17 columns,
 * 18..26 boxes (numbered left to right, top to bottom). Digits are 1..9; 0 means empty. Candidate sets
 * are bitmasks with bit d set for digit d.
 */
object Board {
    fun row(cell: Int) = cell / 9
    fun col(cell: Int) = cell % 9
    fun box(cell: Int) = (cell / 27) * 3 + (cell % 9) / 3

    val units: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(9) { u * 9 + it }
            u < 18 -> IntArray(9) { it * 9 + (u - 9) }
            else -> {
                val b = u - 18
                IntArray(9) { (b / 3 * 3 + it / 3) * 9 + b % 3 * 3 + it % 3 }
            }
        }
    }

    /** The three units (row, column, box) each cell belongs to. */
    val unitsOf: Array<IntArray> = Array(81) { intArrayOf(row(it), 9 + col(it), 18 + box(it)) }

    /** The 20 other cells sharing a row, column or box. */
    val peers: Array<IntArray> = Array(81) { c ->
        unitsOf[c].flatMap { units[it].toList() }.toSet().filter { it != c }.sorted().toIntArray()
    }

    fun sees(a: Int, b: Int) = a != b && (row(a) == row(b) || col(a) == col(b) || box(a) == box(b))

    const val ALL = 0x3FE // bits 1..9

    fun bit(d: Int) = 1 shl d
    fun count(mask: Int) = Integer.bitCount(mask)
    fun digits(mask: Int): List<Int> = (1..9).filter { mask and (1 shl it) != 0 }
    fun firstDigit(mask: Int) = Integer.numberOfTrailingZeros(mask)

    /** Candidates for every empty cell given the placed values (0 for filled cells). */
    fun candidates(values: IntArray): IntArray = IntArray(81) { c ->
        if (values[c] != 0) 0 else {
            var m = ALL
            for (p in peers[c]) if (values[p] != 0) m = m and bit(values[p]).inv()
            m
        }
    }

    /** True if no digit repeats in any unit (empties ignored). */
    fun consistent(values: IntArray): Boolean = units.all { u ->
        var seen = 0
        u.all { c -> val v = values[c]; if (v == 0) true else (seen and bit(v) == 0).also { seen = seen or bit(v) } }
    }

    fun unitKind(u: Int) = when { u < 9 -> "row"; u < 18 -> "column"; else -> "box" }
}

/** Fast bitmask backtracking: counts solutions (up to a limit) and builds random full grids. */
object Solver {
    /** Number of solutions, stopping at [limit]. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val s = State.from(grid) ?: return 0
        return s.search(limit, null, null)
    }

    /** The unique solution, or null if there are none or several. */
    fun solveUnique(grid: IntArray): IntArray? {
        val s = State.from(grid) ?: return null
        val out = IntArray(81)
        return if (s.search(2, out, null) == 1) out else null
    }

    /** A uniformly shuffled valid full grid. */
    fun randomSolution(rnd: Random): IntArray {
        val s = State.from(IntArray(81))!!
        val out = IntArray(81)
        s.search(1, out, rnd)
        return out
    }

    private class State(val v: IntArray, val rows: IntArray, val cols: IntArray, val boxes: IntArray) {
        companion object {
            fun from(grid: IntArray): State? {
                val s = State(grid.copyOf(), IntArray(9), IntArray(9), IntArray(9))
                for (c in 0 until 81) {
                    val d = grid[c]
                    if (d == 0) continue
                    val b = Board.bit(d)
                    val r = Board.row(c); val co = Board.col(c); val bx = Board.box(c)
                    if ((s.rows[r] or s.cols[co] or s.boxes[bx]) and b != 0) return null
                    s.rows[r] = s.rows[r] or b; s.cols[co] = s.cols[co] or b; s.boxes[bx] = s.boxes[bx] or b
                }
                return s
            }
        }

        private fun free(c: Int) = Board.ALL and (rows[Board.row(c)] or cols[Board.col(c)] or boxes[Board.box(c)]).inv()

        fun search(limit: Int, out: IntArray?, rnd: Random?): Int {
            // Most constrained empty cell first.
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (c in 0 until 81) {
                if (v[c] != 0) continue
                val m = free(c)
                val n = Integer.bitCount(m)
                if (n < bestCount) { best = c; bestMask = m; bestCount = n; if (n <= 1) break }
            }
            if (best < 0) {
                out?.let { v.copyInto(it) }
                return 1
            }
            if (bestCount == 0) return 0
            val order = Board.digits(bestMask).let { if (rnd != null) it.shuffled(rnd) else it }
            val r = Board.row(best); val co = Board.col(best); val bx = Board.box(best)
            var found = 0
            for (d in order) {
                val b = Board.bit(d)
                v[best] = d
                rows[r] = rows[r] or b; cols[co] = cols[co] or b; boxes[bx] = boxes[bx] or b
                found += search(limit - found, out, rnd)
                rows[r] = rows[r] and b.inv(); cols[co] = cols[co] and b.inv(); boxes[bx] = boxes[bx] and b.inv()
                v[best] = 0
                if (found >= limit) break
            }
            return found
        }
    }
}
