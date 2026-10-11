package dadgames.sudoku.core

import dadgames.sudoku.core.Board.bit
import dadgames.sudoku.core.Board.count

/** Solving techniques a person uses, grouped into levels that set a puzzle's difficulty. */
enum class Technique(val level: Int) {
    HIDDEN_SINGLE(0), NAKED_SINGLE(0),
    POINTING(1), BOX_LINE(1), NAKED_PAIR(1), HIDDEN_PAIR(1),
    NAKED_TRIPLE(2), HIDDEN_TRIPLE(2), X_WING(2), XY_WING(2), XYZ_WING(2),
    SWORDFISH(3), NAKED_QUAD(3), COLORING(3), XY_CHAIN(3);
}

/** One deduction: either a placement or a set of candidate eliminations (cell to digit). */
class Step(
    val technique: Technique,
    val placeCell: Int = -1,
    val placeDigit: Int = 0,
    val eliminations: List<Pair<Int, Int>> = emptyList(),
    /** Unit the deduction happens in (0..26), or -1. */
    val unit: Int = -1,
    /** Pattern cells (the pair, the wing, ...). */
    val cells: List<Int> = emptyList(),
    val digits: List<Int> = emptyList(),
) {
    val isPlacement get() = placeCell >= 0
}

/** Result of solving a puzzle with human techniques only. */
data class Grade(val solved: Boolean, val level: Int, val steps: Int, val used: Set<Technique>)

/**
 * A human-style solver. Keeps pencil marks (candidates) and applies the simplest technique that makes
 * progress. The hardest level it needed is the puzzle's difficulty. Because every step is a valid
 * deduction, solving to the end also proves the solution is unique.
 */
class LogicSolver(start: IntArray) {
    val values: IntArray = start.copyOf()
    val cand: IntArray = Board.candidates(values)

    val solved get() = values.all { it != 0 }

    fun apply(s: Step) {
        if (s.isPlacement) place(s.placeCell, s.placeDigit)
        for ((c, d) in s.eliminations) cand[c] = cand[c] and bit(d).inv()
    }

    fun place(c: Int, d: Int) {
        values[c] = d
        cand[c] = 0
        for (p in Board.peers[c]) cand[p] = cand[p] and bit(d).inv()
    }

    /** True if some empty cell has no candidates or a unit lost a digit entirely. */
    fun broken(): Boolean {
        for (c in 0 until 81) if (values[c] == 0 && cand[c] == 0) return true
        for (u in Board.units) {
            var have = 0
            for (c in u) have = have or (if (values[c] != 0) bit(values[c]) else cand[c])
            if (have != Board.ALL) return true
        }
        return false
    }

    /** The simplest step available, using techniques up to [maxLevel]. */
    fun next(maxLevel: Int = 3): Step? {
        hiddenSingle()?.let { return it }
        nakedSingle()?.let { return it }
        if (maxLevel < 1) return null
        pointing()?.let { return it }
        boxLine()?.let { return it }
        nakedSubset(2)?.let { return it }
        hiddenSubset(2)?.let { return it }
        if (maxLevel < 2) return null
        nakedSubset(3)?.let { return it }
        hiddenSubset(3)?.let { return it }
        fish(2)?.let { return it }
        xyWing()?.let { return it }
        xyzWing()?.let { return it }
        if (maxLevel < 3) return null
        fish(3)?.let { return it }
        nakedSubset(4)?.let { return it }
        coloring()?.let { return it }
        xyChain()?.let { return it }
        return null
    }

    /** Solve as far as possible; returns the grade. */
    fun run(maxLevel: Int = 3): Grade {
        var level = 0
        var steps = 0
        val used = mutableSetOf<Technique>()
        while (!solved) {
            if (broken()) return Grade(false, level, steps, used)
            val s = next(maxLevel) ?: return Grade(false, level, steps, used)
            level = maxOf(level, s.technique.level)
            used += s.technique
            apply(s)
            steps++
        }
        return Grade(true, level, steps, used)
    }

    // ------------------------------------------------------------ singles

    private val unitOrder = (18 until 27) + (0 until 18) // boxes first: what people look at

    private fun positions(u: Int, d: Int): List<Int> = Board.units[u].filter { cand[it] and bit(d) != 0 }

    private fun placed(u: Int, d: Int) = Board.units[u].any { values[it] == d }

    private fun hiddenSingle(): Step? {
        for (u in unitOrder) for (d in 1..9) {
            if (placed(u, d)) continue
            val p = positions(u, d)
            if (p.size == 1) return Step(Technique.HIDDEN_SINGLE, p[0], d, unit = u)
        }
        return null
    }

    private fun nakedSingle(): Step? {
        for (c in 0 until 81) if (values[c] == 0 && count(cand[c]) == 1) return Step(Technique.NAKED_SINGLE, c, Board.firstDigit(cand[c]))
        return null
    }

    // ------------------------------------------------------------ intersections

    private fun pointing(): Step? {
        for (b in 18 until 27) for (d in 1..9) {
            val p = positions(b, d)
            if (p.size < 2) continue
            for (line in listOf(Board.row(p[0]), 9 + Board.col(p[0]))) {
                if (!p.all { line in Board.unitsOf[it] }) continue
                val elim = Board.units[line].filter { it !in p && cand[it] and bit(d) != 0 }.map { it to d }
                if (elim.isNotEmpty()) return Step(Technique.POINTING, eliminations = elim, unit = b, cells = p, digits = listOf(d))
            }
        }
        return null
    }

    private fun boxLine(): Step? {
        for (line in 0 until 18) for (d in 1..9) {
            val p = positions(line, d)
            if (p.size < 2) continue
            val b = 18 + Board.box(p[0])
            if (!p.all { 18 + Board.box(it) == b }) continue
            val elim = Board.units[b].filter { it !in p && cand[it] and bit(d) != 0 }.map { it to d }
            if (elim.isNotEmpty()) return Step(Technique.BOX_LINE, eliminations = elim, unit = line, cells = p, digits = listOf(d))
        }
        return null
    }

    // ------------------------------------------------------------ subsets

    private fun nakedSubset(k: Int): Step? {
        val tech = when (k) { 2 -> Technique.NAKED_PAIR; 3 -> Technique.NAKED_TRIPLE; else -> Technique.NAKED_QUAD }
        for (u in unitOrder) {
            val open = Board.units[u].filter { values[it] == 0 && count(cand[it]) in 2..k }
            if (open.size < k) continue
            for (combo in combinations(open, k)) {
                var union = 0
                for (c in combo) union = union or cand[c]
                if (count(union) != k) continue
                val elim = Board.units[u].filter { it !in combo && values[it] == 0 && cand[it] and union != 0 }
                    .flatMap { c -> Board.digits(cand[c] and union).map { c to it } }
                if (elim.isNotEmpty()) return Step(tech, eliminations = elim, unit = u, cells = combo, digits = Board.digits(union))
            }
        }
        return null
    }

    private fun hiddenSubset(k: Int): Step? {
        val tech = if (k == 2) Technique.HIDDEN_PAIR else Technique.HIDDEN_TRIPLE
        for (u in unitOrder) {
            val ds = (1..9).filter { !placed(u, it) && positions(u, it).size in 2..k }
            if (ds.size < k) continue
            for (combo in combinations(ds, k)) {
                val cells = combo.flatMap { positions(u, it) }.toSet()
                if (cells.size != k) continue
                var keep = 0
                for (d in combo) keep = keep or bit(d)
                val elim = cells.flatMap { c -> Board.digits(cand[c] and keep.inv()).map { c to it } }
                if (elim.isNotEmpty()) return Step(tech, eliminations = elim, unit = u, cells = cells.sorted(), digits = combo)
            }
        }
        return null
    }

    // ------------------------------------------------------------ fish (X-wing, swordfish)

    private fun fish(n: Int): Step? {
        val tech = if (n == 2) Technique.X_WING else Technique.SWORDFISH
        for (d in 1..9) for (byRow in listOf(true, false)) {
            val bases = (0 until 9).map { i -> if (byRow) i else 9 + i }
            val lines = bases.filter { positions(it, d).size in 2..n }
            if (lines.size < n) continue
            for (combo in combinations(lines, n)) {
                val cells = combo.flatMap { positions(it, d) }
                val covers = cells.map { if (byRow) 9 + Board.col(it) else Board.row(it) }.toSet()
                if (covers.size != n) continue
                val elim = covers.flatMap { cu -> Board.units[cu].filter { it !in cells && cand[it] and bit(d) != 0 } }.map { it to d }
                if (elim.isNotEmpty()) return Step(tech, eliminations = elim, cells = cells, digits = listOf(d))
            }
        }
        return null
    }

    // ------------------------------------------------------------ wings

    private fun xyWing(): Step? {
        val bivalue = (0 until 81).filter { values[it] == 0 && count(cand[it]) == 2 }
        for (pivot in bivalue) {
            val (a, b) = Board.digits(cand[pivot])
            val wings = bivalue.filter { it != pivot && Board.sees(it, pivot) }
            for (p1 in wings) {
                if (cand[p1] and bit(a) == 0 || cand[p1] and bit(b) != 0) continue
                val z = Board.firstDigit(cand[p1] and bit(a).inv())
                for (p2 in wings) {
                    if (p2 == p1 || cand[p2] != (bit(b) or bit(z))) continue
                    val elim = (0 until 81).filter { it != pivot && Board.sees(it, p1) && Board.sees(it, p2) && cand[it] and bit(z) != 0 }.map { it to z }
                    if (elim.isNotEmpty()) return Step(Technique.XY_WING, eliminations = elim, cells = listOf(pivot, p1, p2), digits = listOf(z))
                }
            }
        }
        return null
    }

    private fun xyzWing(): Step? {
        for (pivot in 0 until 81) {
            if (values[pivot] != 0 || count(cand[pivot]) != 3) continue
            val pm = cand[pivot]
            val wings = (0 until 81).filter { it != pivot && values[it] == 0 && count(cand[it]) == 2 && cand[it] and pm.inv() == 0 && Board.sees(it, pivot) }
            for (i in wings.indices) for (j in i + 1 until wings.size) {
                val p1 = wings[i]; val p2 = wings[j]
                if (cand[p1] == cand[p2] || (cand[p1] or cand[p2]) != pm) continue
                val zm = cand[p1] and cand[p2]
                if (count(zm) != 1) continue
                val z = Board.firstDigit(zm)
                val elim = (0 until 81).filter {
                    it != pivot && it != p1 && it != p2 && Board.sees(it, pivot) && Board.sees(it, p1) && Board.sees(it, p2) && cand[it] and zm != 0
                }.map { it to z }
                if (elim.isNotEmpty()) return Step(Technique.XYZ_WING, eliminations = elim, cells = listOf(pivot, p1, p2), digits = listOf(z))
            }
        }
        return null
    }

    // ------------------------------------------------------------ chains

    /** Simple colouring on one digit: chains of cells where a unit has exactly two spots for it. */
    private fun coloring(): Step? {
        for (d in 1..9) {
            val links = HashMap<Int, MutableList<Int>>()
            for (u in 0 until 27) {
                if (placed(u, d)) continue
                val p = positions(u, d)
                if (p.size == 2) {
                    links.getOrPut(p[0]) { mutableListOf() } += p[1]
                    links.getOrPut(p[1]) { mutableListOf() } += p[0]
                }
            }
            val color = HashMap<Int, Int>()
            for (start in links.keys) {
                if (start in color) continue
                val comp = mutableListOf(start)
                color[start] = 0
                var i = 0
                while (i < comp.size) {
                    val c = comp[i++]
                    for (n in links[c]!!) if (n !in color) { color[n] = 1 - color[c]!!; comp += n }
                }
                if (comp.size < 4) continue
                val a = comp.filter { color[it] == 0 }
                val b = comp.filter { color[it] == 1 }
                // Wrap: two cells of one colour see each other, so that colour is false everywhere.
                for (side in listOf(a, b)) {
                    if (side.any { x -> side.any { y -> Board.sees(x, y) } }) {
                        return Step(Technique.COLORING, eliminations = side.map { it to d }, cells = comp, digits = listOf(d))
                    }
                }
                // Trap: a cell outside the chain that sees both colours can't hold the digit.
                val elim = (0 until 81).filter { c ->
                    c !in color && cand[c] and bit(d) != 0 && a.any { Board.sees(it, c) } && b.any { Board.sees(it, c) }
                }.map { it to d }
                if (elim.isNotEmpty()) return Step(Technique.COLORING, eliminations = elim, cells = comp, digits = listOf(d))
            }
        }
        return null
    }

    /** XY-chain: a chain of two-option cells. If the start isn't x, the end must be x. */
    private fun xyChain(): Step? {
        val bivalue = (0 until 81).filter { values[it] == 0 && count(cand[it]) == 2 }
        if (bivalue.size < 3) return null
        for (start in bivalue) for (x in Board.digits(cand[start])) {
            val y = Board.firstDigit(cand[start] and bit(x).inv())
            // State: (cell, digit known to be in it). Parent links rebuild the chain.
            val parent = HashMap<Int, Int>()
            val startKey = start * 10 + y
            parent[startKey] = -1
            val queue = ArrayDeque(listOf(startKey))
            while (queue.isNotEmpty()) {
                val key = queue.removeFirst()
                val c = key / 10; val v = key % 10
                for (n in bivalue) {
                    if (n == c || n == start || cand[n] and bit(v) == 0 || !Board.sees(n, c)) continue
                    val other = Board.firstDigit(cand[n] and bit(v).inv())
                    val nk = n * 10 + other
                    if (nk in parent) continue
                    parent[nk] = key
                    if (other == x) {
                        val elim = (0 until 81).filter {
                            it != start && it != n && cand[it] and bit(x) != 0 && Board.sees(it, start) && Board.sees(it, n)
                        }.map { it to x }
                        if (elim.isNotEmpty()) {
                            val chain = generateSequence(nk) { parent[it]?.takeIf { p -> p >= 0 } }.map { it / 10 }.toList().reversed()
                            return Step(Technique.XY_CHAIN, eliminations = elim, cells = chain, digits = listOf(x))
                        }
                    }
                    queue.addLast(nk)
                }
            }
        }
        return null
    }

    companion object {
        fun grade(puzzle: IntArray, maxLevel: Int = 3): Grade = LogicSolver(puzzle).run(maxLevel)

        fun <T> combinations(items: List<T>, k: Int): Sequence<List<T>> = sequence {
            val idx = IntArray(k) { it }
            val n = items.size
            if (k > n) return@sequence
            while (true) {
                yield(idx.map { items[it] })
                var i = k - 1
                while (i >= 0 && idx[i] == n - k + i) i--
                if (i < 0) break
                idx[i]++
                for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
            }
        }
    }
}
