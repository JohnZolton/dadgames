package blocks.core

/** A cell of a shape, relative to the shape's top-left corner. */
data class Pt(val r: Int, val c: Int)

/**
 * One tray piece. [id] is its index in [Shapes.all] (what gets saved).
 * [masks] holds the board mask for every top-left anchor cell where the shape stays on the board (null elsewhere).
 */
class Shape(val id: Int, val family: String, val cells: List<Pt>) {
    val rows: Int = cells.maxOf { it.r } + 1
    val cols: Int = cells.maxOf { it.c } + 1
    val size: Int get() = cells.size

    val masks: Array<Bits?> = Array(CELLS) { anchor ->
        val r0 = rowOf(anchor)
        val c0 = colOf(anchor)
        if (r0 + rows > SIZE || c0 + cols > SIZE) null
        else Bits.of(cells.map { cellOf(r0 + it.r, c0 + it.c) })
    }

    /** Anchors (top-left cells) where this shape fits on [board]. */
    fun fits(board: Bits): List<Int> = (0 until CELLS).filter { a -> masks[a]?.let { !it.intersects(board) } == true }
    fun fitsAnywhere(board: Bits): Boolean = masks.any { it != null && !it.intersects(board) }
    fun canPlace(board: Bits, anchor: Int): Boolean =
        anchor in 0 until CELLS && masks[anchor]?.let { !it.intersects(board) } == true

    override fun toString() = "Shape($id $family)"
}

/**
 * Woodoku's piece set: the 49 shapes read off the real app's tray by the WoodokuAI project
 * (github.com/CosmicSubspace/WoodokuAI, piecedefs.txt). No 3x3 square; diagonals and the plus are in.
 * Every shape is equally likely (fair random, no weighting or rigging).
 */
object Shapes {
    private val defs: List<Pair<String, String>> = listOf(
        "Single" to "#",
        "Line 2" to "##", "Line 2" to "#/#",
        "Diagonal 2" to ".#/#.", "Diagonal 2" to "#./.#",
        "Line 3" to "###", "Line 3" to "#/#/#",
        "Diagonal 3" to "..#/.#./#..", "Diagonal 3" to "#../.#./..#",
        "Corner" to "##/#.", "Corner" to "##/.#", "Corner" to "#./##", "Corner" to ".#/##",
        "Line 4" to "####", "Line 4" to "#/#/#/#",
        "Diagonal 4" to "...#/..#./.#../#...", "Diagonal 4" to "#.../.#../..#./...#",
        "Square" to "##/##",
        "L" to "#./#./##", "L" to ".#/.#/##", "L" to "##/#./#.", "L" to "##/.#/.#",
        "L" to "###/#..", "L" to "###/..#", "L" to "#../###", "L" to "..#/###",
        "T" to "###/.#.", "T" to ".#./###", "T" to "#./##/#.", "T" to ".#/##/.#",
        "S" to ".##/##.", "S" to "#./##/.#", "Z" to "##./.##", "Z" to ".#/##/#.",
        "Line 5" to "#####", "Line 5" to "#/#/#/#/#",
        "Big corner" to "###/#../#..", "Big corner" to "###/..#/..#", "Big corner" to "#../#../###", "Big corner" to "..#/..#/###",
        "Big T" to "###/.#./.#.", "Big T" to ".#./.#./###", "Big T" to "#../###/#..", "Big T" to "..#/###/..#",
        "Plus" to ".#./###/.#.",
        "U" to "#.#/###", "U" to "###/#.#", "U" to "##/#./##", "U" to "##/.#/##",
    )

    val all: List<Shape> = defs.mapIndexed { i, (family, pattern) ->
        val cells = pattern.split('/').flatMapIndexed { r, line -> line.mapIndexedNotNull { c, ch -> if (ch == '#') Pt(r, c) else null } }
        Shape(i, family, cells)
    }

    operator fun get(id: Int): Shape = all[id]
}
