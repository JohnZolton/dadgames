package blocks.core

/**
 * Woodoku's piece generator, decompiled from his APK v2.11.02 (research/woodoku.md, research/woodoku-pieces.txt).
 *
 * - 17 base shapes A–Q. Their weights depend on the stage, picked by how many trays this game has dealt so far.
 * - Each draw: weighted pick, then a 50% horizontal mirror, then a rotation of 0, 90 or 180 degrees
 *   counter-clockwise (never 270), so 6 of the 8 orientations occur.
 * - A tray of 3 is filled one piece at a time. A piece whose base shape is already in the tray is redrawn once,
 *   and the redraw is kept.
 * - one_valid_move_rule: if none of a freshly dealt tray fits anywhere, one random piece becomes a single block.
 *   That is the only time the board is looked at, so a fresh tray never ends the game.
 */
object PieceGenerator {
    class Base(val letter: Char, val pattern: String) {
        val cells: List<Pt> = pattern.split('/').flatMapIndexed { r, line -> line.mapIndexedNotNull { c, ch -> if (ch == '1') Pt(r, c) else null } }
    }

    val bases: List<Base> = listOf(
        Base('A', "1"), Base('B', "11"), Base('C', "111"), Base('D', "1111"), Base('E', "11111"),
        Base('F', "11/11"), Base('G', "011/110"), Base('H', "10/11"), Base('I', "100/111"),
        Base('J', "100/100/111"), Base('K', "010/111/010"), Base('L', "10/11/10"), Base('M', "100/111/100"),
        Base('N', "101/111"), Base('O', "01/10"), Base('P', "001/010/100"), Base('Q', "0001/0010/0100/1000"),
    )

    /** First tray number (trays already dealt) of each stage. */
    val stageStarts = intArrayOf(0, 1, 2, 3, 6, 7, 9, 10, 12, 15, 19, 24)

    /** weights[stage][base] for bases A..Q (each stage sums to 100). */
    val weights: Array<IntArray> = arrayOf(
        //          A  B  C  D  E  F   G   H   I   J   K   L   M  N  O  P  Q
        intArrayOf(0, 10, 5, 5, 5, 5, 15, 15, 10, 10, 0, 10, 10, 0, 0, 0, 0),
        intArrayOf(0, 5, 5, 5, 5, 5, 15, 15, 10, 10, 0, 15, 10, 0, 0, 0, 0),
        intArrayOf(5, 5, 7, 5, 3, 5, 15, 15, 10, 10, 0, 15, 5, 0, 0, 0, 0),
        intArrayOf(5, 5, 7, 5, 3, 5, 15, 15, 10, 5, 5, 15, 5, 0, 0, 0, 0),
        intArrayOf(1, 5, 5, 7, 7, 7, 15, 10, 15, 8, 5, 10, 5, 0, 0, 0, 0),
        intArrayOf(1, 5, 5, 7, 7, 7, 10, 10, 10, 8, 5, 10, 5, 3, 3, 3, 1),
        intArrayOf(0, 5, 5, 5, 8, 5, 10, 10, 10, 8, 5, 10, 5, 4, 4, 4, 2),
        intArrayOf(0, 4, 4, 4, 9, 4, 9, 9, 9, 9, 6, 9, 6, 5, 5, 5, 3),
        intArrayOf(0, 3, 3, 4, 9, 4, 9, 8, 9, 9, 7, 9, 7, 5, 5, 5, 4),
        intArrayOf(0, 2, 2, 4, 9, 4, 9, 7, 9, 9, 8, 9, 8, 5, 5, 5, 5),
        intArrayOf(0, 1, 1, 3, 9, 3, 9, 7, 9, 9, 9, 9, 9, 6, 6, 5, 5),
        intArrayOf(0, 0, 0, 2, 8, 3, 9, 5, 9, 10, 10, 9, 10, 7, 5, 7, 6),
    )

    fun stage(traysDealt: Int): Int = stageStarts.indexOfLast { traysDealt >= it }

    /** Base shape after a horizontal mirror and [rot] quarter turns counter-clockwise, as a [Shapes] id. */
    fun shapeId(base: Int, mirror: Boolean, rot: Int): Int {
        var cells = bases[base].cells
        if (mirror) { val w = cells.maxOf { it.c }; cells = cells.map { Pt(it.r, w - it.c) } }
        repeat(rot) { val w = cells.maxOf { it.c }; cells = cells.map { Pt(w - it.c, it.r) } }
        return byCells.getValue(cells.toSet())
    }

    private val byCells: Map<Set<Pt>, Int> = Shapes.all.associate { it.cells.toSet() to it.id }

    /** One draw: (base index, shape id, next rng). */
    fun draw(rng: Rng, traysDealt: Int): Triple<Int, Int, Rng> {
        val w = weights[stage(traysDealt)]
        var (v, r) = rng.nextInt(w.sum())
        var base = 0
        while (v >= w[base]) { v -= w[base]; base++ }
        val (m, r2) = r.nextInt(2)
        val (rot, r3) = r2.nextInt(3)
        return Triple(base, shapeId(base, m == 1, rot), r3)
    }

    fun tray(rng: Rng, traysDealt: Int, board: Bits): Pair<List<Int>, Rng> {
        var r = rng
        val bases = ArrayList<Int>(TRAY)
        val ids = ArrayList<Int>(TRAY)
        repeat(TRAY) {
            var (b, id, n) = draw(r, traysDealt)
            r = n
            if (b in bases) { val again = draw(r, traysDealt); b = again.first; id = again.second; r = again.third }
            bases += b; ids += id
        }
        if (ids.none { Shapes[it].fitsAnywhere(board) }) {
            val (k, n) = r.nextInt(TRAY)
            r = n
            ids[k] = SINGLE
        }
        return ids to r
    }

    val SINGLE: Int = Shapes.all.first { it.size == 1 }.id
}
