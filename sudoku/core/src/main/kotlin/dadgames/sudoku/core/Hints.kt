package dadgames.sudoku.core

/**
 * A hint: the cell to fill, the digit, a short plain-English reason, the unit to light up ([focus])
 * and the cells of any pattern used along the way ([pattern]).
 */
data class Hint(
    val cell: Int,
    val digit: Int,
    val reason: String,
    val focus: List<Int> = emptyList(),
    val pattern: List<Int> = emptyList(),
    /** The hint corrected a wrong number rather than filling an empty square. */
    val fixed: Boolean = false,
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
        if (selected != null && values[selected] == 0) singleAt(s, selected)?.let { return it }
        val elims = mutableListOf<Step>()
        while (elims.size < 500) {
            val step = s.next() ?: break
            if (step.isPlacement) {
                if (solution[step.placeCell] != step.placeDigit) break // can't happen with a consistent board
                return Hint(step.placeCell, step.placeDigit, explain(step, elims), focusOf(step), elims.firstOrNull()?.cells.orEmpty())
            }
            elims += step
            s.apply(step)
        }
        val c = selected?.takeIf { values[it] == 0 } ?: (0 until 81).first { values[it] == 0 }
        return Hint(c, solution[c], "This one takes some very deep thinking, so here is the answer.", listOf(c))
    }

    /** A single at the cell he has selected, if there is one: answers "what goes here?" directly. */
    private fun singleAt(s: LogicSolver, c: Int): Hint? {
        for (u in Board.unitsOf[c].sortedDescending()) { // box, column, row
            for (d in Board.digits(s.cand[c])) {
                if (Board.units[u].none { it != c && s.cand[it] and Board.bit(d) != 0 } && Board.units[u].none { s.values[it] == d }) {
                    val st = Step(Technique.HIDDEN_SINGLE, c, d, unit = u)
                    return Hint(c, d, explain(st, emptyList()), focusOf(st))
                }
            }
        }
        if (Board.count(s.cand[c]) == 1) {
            val st = Step(Technique.NAKED_SINGLE, c, Board.firstDigit(s.cand[c]))
            return Hint(c, st.placeDigit, explain(st, emptyList()), focusOf(st))
        }
        return null
    }

    private fun focusOf(step: Step): List<Int> =
        if (step.unit >= 0) Board.units[step.unit].toList() else (Board.peers[step.placeCell].toList() + step.placeCell)

    fun placementText(step: Step): String = when (step.technique) {
        Technique.HIDDEN_SINGLE -> "This is the only place for ${step.placeDigit} in this ${Board.unitKind(step.unit)}."
        else -> "Only ${step.placeDigit} fits here. Its row, column and box already have every other number."
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

    private val counts = listOf("", "One", "Two", "Three", "Four")

    fun eliminationText(step: Step): String {
        val d = step.digits.firstOrNull() ?: 0
        val kind = if (step.unit >= 0) Board.unitKind(step.unit) else ""
        return when (step.technique) {
            Technique.POINTING -> {
                val line = if (step.cells.map { Board.row(it) }.toSet().size == 1) "row" else "column"
                "In the box, $d has to go in one $line, so the rest of that $line can't be $d."
            }
            Technique.BOX_LINE -> "In this $kind, $d has to go inside one box, so the rest of that box can't be $d."
            Technique.NAKED_PAIR, Technique.NAKED_TRIPLE, Technique.NAKED_QUAD ->
                "${counts[step.cells.size]} squares in a $kind can only hold ${list(step.digits)}, so no other square in that $kind can."
            Technique.HIDDEN_PAIR, Technique.HIDDEN_TRIPLE ->
                "In a $kind, ${and(step.digits)} fit in just ${counts[step.cells.size].lowercase()} squares, so those squares hold nothing else."
            Technique.X_WING -> "The ${d}s in two lines sit at the corners of a rectangle (an X-wing), so the rest of those lines can't be $d."
            Technique.SWORDFISH -> "The ${d}s in three lines share the same three crossing lines (a swordfish), so the rest of those lines can't be $d."
            Technique.XY_WING -> "Three squares with two options each (an XY-wing) mean one end must be $d, so squares that see both ends can't be $d."
            Technique.XYZ_WING -> "Three linked squares (an XYZ-wing) mean one of them is $d, so squares that see all three can't be $d."
            Technique.COLORING -> "Following the places where $d has only two spots in a row, column or box rules $d out of some squares."
            Technique.XY_CHAIN -> "A chain of two-option squares means one end or the other is $d, so squares that see both ends can't be $d."
            Technique.HIDDEN_SINGLE, Technique.NAKED_SINGLE -> ""
        }
    }

    fun explain(place: Step, before: List<Step>): String {
        val p = if (before.isEmpty() || place.technique == Technique.HIDDEN_SINGLE) placementText(place) else "Only ${place.placeDigit} fits here."
        return when (before.size) {
            0 -> p
            1 -> "${eliminationText(before[0])} Then: ${p.replaceFirstChar { it.lowercase() }}"
            else -> "${eliminationText(before[0])} After a few steps like that: ${p.replaceFirstChar { it.lowercase() }}"
        }
    }
}
