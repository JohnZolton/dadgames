package dadgames.sudoku.core

/**
 * A hint: the cell to fill, the digit, a short plain-English reason, the area to shade ([focus]) and
 * the cells of the pattern the reason talks about ([pattern]).
 */
data class Hint(
    val cell: Int,
    val digit: Int,
    val reason: String,
    val focus: List<Int> = emptyList(),
    val pattern: List<Int> = emptyList(),
    /** The hint corrected a wrong number rather than filling an empty square. */
    val fixed: Boolean = false,
    /** The reasoning steps the answer depends on (all of them needed), and the final placement. */
    val steps: List<Step> = emptyList(),
    val target: Step? = null,
)

object Hints {
    fun find(values: IntArray, solution: IntArray, selected: Int?): Hint? {
        // A wrong number spoils every deduction after it, so fix that first.
        val wrong = (0 until 81).filter { values[it] != 0 && values[it] != solution[it] }
        if (wrong.isNotEmpty()) {
            val c = selected?.takeIf { it in wrong } ?: wrong.first()
            return Hint(c, solution[c], "This ${values[c]} isn't right. It should be ${solution[c]}.", listOf(c), fixed = true)
        }
        if (values.all { it != 0 }) return null
        val s = LogicSolver(values)
        val base = s.cand.copyOf()
        if (selected != null && values[selected] == 0) singleAt(s, selected)?.let { return make(it, emptyList()) }
        val elims = mutableListOf<Step>()
        while (elims.size < 500) {
            val step = s.next() ?: break
            if (step.isPlacement) {
                if (solution[step.placeCell] != step.placeDigit) break // can't happen with a consistent board
                return make(step, minimal(elims, step, base))
            }
            elims += step
            s.apply(step)
        }
        val c = selected?.takeIf { values[it] == 0 } ?: (0 until 81).first { values[it] == 0 }
        return Hint(c, solution[c], "This one takes some very deep thinking, so here is the answer.", listOf(c))
    }

    /** True if, starting from [base], every step in [steps] holds in turn and then [target] holds. */
    fun follows(steps: List<Step>, target: Step, base: IntArray): Boolean {
        val x = base.copyOf()
        for (st in steps) {
            if (!st.holdsIn(x)) return false
            for ((c, d) in st.eliminations) x[c] = x[c] and Board.bit(d).inv()
        }
        return target.holdsIn(x)
    }

    /** Drop every step the answer doesn't need (harder ones first), until each remaining one is needed. */
    fun minimal(steps: List<Step>, target: Step, base: IntArray): List<Step> {
        var cur = steps
        var changed = true
        while (changed) {
            changed = false
            for (i in cur.indices.sortedByDescending { cur[it].technique.level }) {
                val without = cur.filterIndexed { j, _ -> j != i }
                if (follows(without, target, base)) { cur = without; changed = true; break }
            }
        }
        return cur
    }

    private fun make(target: Step, steps: List<Step>): Hint {
        val key = steps.lastOrNull { st -> st.technique.level == steps.maxOf { it.technique.level } }
        return Hint(
            target.placeCell, target.placeDigit, explain(target, steps),
            focus = key?.area ?: target.area, pattern = key?.cells.orEmpty(), steps = steps, target = target,
        )
    }

    /** A single at the cell he has selected, if there is one: answers "what goes here?" directly. */
    private fun singleAt(s: LogicSolver, c: Int): Step? {
        for (u in Board.unitsOf[c].sortedDescending()) { // box, column, row
            for (d in Board.digits(s.cand[c])) {
                if (Board.units[u].none { it != c && s.cand[it] and Board.bit(d) != 0 } && Board.units[u].none { s.values[it] == d }) {
                    return s.hiddenSingleStep(c, d, u)
                }
            }
        }
        if (Board.count(s.cand[c]) == 1) return s.nakedSingleStep(c)
        return null
    }

    // ------------------------------------------------------------ words

    private val boxNames = listOf("top-left", "top-middle", "top-right", "middle-left", "centre", "middle-right", "bottom-left", "bottom-middle", "bottom-right")

    /** "row 3", "column 5", "the top-left box". */
    fun unitName(u: Int): String = when {
        u < 9 -> "row ${u + 1}"
        u < 18 -> "column ${u - 8}"
        else -> "the ${boxNames[u - 18]} box"
    }

    private fun lineNames(lines: List<Int>): String {
        val rows = lines.all { it < 9 }
        val nums = lines.map { if (rows) it + 1 else it - 8 }.sorted()
        return (if (rows) "rows " else "columns ") + and(nums)
    }

    private fun list(ds: List<Int>): String = when (ds.size) {
        1 -> "${ds[0]}"
        2 -> "${ds[0]} or ${ds[1]}"
        else -> ds.dropLast(1).joinToString(", ") + " or " + ds.last()
    }

    private fun and(ds: List<Int>): String = when (ds.size) {
        1 -> "${ds[0]}"
        2 -> "${ds[0]} and ${ds[1]}"
        else -> ds.dropLast(1).joinToString(", ") + " and " + ds.last()
    }

    private val counts = listOf("No", "One", "Two", "Three", "Four")

    fun placementText(step: Step): String = when (step.technique) {
        Technique.HIDDEN_SINGLE -> "This is the only place for ${step.placeDigit} in ${unitName(step.unit)}."
        else -> "Only ${step.placeDigit} fits here: its row, column and box have every other number."
    }

    fun eliminationText(step: Step): String {
        val d = step.digits.firstOrNull() ?: 0
        val where = if (step.unit >= 0) unitName(step.unit) else ""
        return when (step.technique) {
            Technique.POINTING -> {
                val line = unitName(step.lines.first())
                "In ${unitName(step.unit)}, the $d must go in $line, so the rest of $line can't be $d."
            }
            Technique.BOX_LINE -> "In $where, the $d must go in ${unitName(step.lines.first())}, so the rest of that box can't be $d."
            Technique.NAKED_PAIR, Technique.NAKED_TRIPLE, Technique.NAKED_QUAD ->
                "${counts[step.cells.size]} shaded squares in $where can only hold ${list(step.digits)}, so the rest of it can't."
            Technique.HIDDEN_PAIR, Technique.HIDDEN_TRIPLE ->
                "In $where, ${and(step.digits)} fit only in ${counts[step.cells.size].lowercase()} shaded squares, so those hold nothing else."
            Technique.X_WING, Technique.SWORDFISH -> {
                val covers = step.cells.map { if (step.lines.first() < 9) 9 + Board.col(it) else Board.row(it) }.distinct()
                "In ${lineNames(step.lines)}, the $d must go in ${lineNames(covers)}, so the rest of those can't be $d."
            }
            Technique.XY_WING, Technique.XYZ_WING ->
                "In the three shaded squares one of the outer two must be $d, so a square that sees both can't be $d."
            Technique.COLORING -> "In the shaded chain, every other square is $d, so a square that sees both kinds can't be $d."
            Technique.XY_CHAIN -> "In the shaded chain of two-option squares one end is $d, so a square that sees both ends can't be $d."
            Technique.HIDDEN_SINGLE, Technique.NAKED_SINGLE -> ""
        }
    }

    /** Explains the hardest step the answer needs, then the answer. */
    fun explain(place: Step, needed: List<Step>): String {
        if (needed.isEmpty()) return placementText(place)
        val key = needed.last { st -> st.technique.level == needed.maxOf { it.technique.level } }
        val p = if (place.technique == Technique.HIDDEN_SINGLE) placementText(place).replaceFirstChar { it.lowercase() }
        else "only ${place.placeDigit} fits here."
        val others = needed.size - 1
        return eliminationText(key) + when (others) {
            0 -> " So $p"
            1 -> " With one more step, $p"
            else -> " With $others more steps, $p"
        }
    }
}
