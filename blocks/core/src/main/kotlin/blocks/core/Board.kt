package blocks.core

import kotlinx.serialization.Serializable

const val SIZE = 9
const val CELLS = SIZE * SIZE

fun cellOf(row: Int, col: Int) = row * SIZE + col
fun rowOf(cell: Int) = cell / SIZE
fun colOf(cell: Int) = cell % SIZE
fun boxOf(cell: Int) = (rowOf(cell) / 3) * 3 + colOf(cell) / 3

/** 81-cell bitboard: cells 0..63 in [lo], 64..80 in [hi]. Immutable. */
@Serializable
data class Bits(val lo: Long = 0L, val hi: Long = 0L) {
    operator fun get(cell: Int): Boolean =
        if (cell < 64) (lo ushr cell) and 1L == 1L else (hi ushr (cell - 64)) and 1L == 1L

    fun with(cell: Int): Bits = if (cell < 64) Bits(lo or (1L shl cell), hi) else Bits(lo, hi or (1L shl (cell - 64)))
    infix fun or(o: Bits) = Bits(lo or o.lo, hi or o.hi)
    infix fun and(o: Bits) = Bits(lo and o.lo, hi and o.hi)
    infix fun andNot(o: Bits) = Bits(lo and o.lo.inv(), hi and o.hi.inv())
    fun intersects(o: Bits) = (lo and o.lo) != 0L || (hi and o.hi) != 0L
    fun containsAll(o: Bits) = (lo and o.lo) == o.lo && (hi and o.hi) == o.hi
    val isEmpty: Boolean get() = lo == 0L && hi == 0L
    val count: Int get() = java.lang.Long.bitCount(lo) + java.lang.Long.bitCount(hi)

    fun cells(): List<Int> {
        val out = ArrayList<Int>(count)
        var l = lo
        while (l != 0L) { val b = java.lang.Long.numberOfTrailingZeros(l); out += b; l = l and (l - 1) }
        var h = hi
        while (h != 0L) { val b = java.lang.Long.numberOfTrailingZeros(h); out += 64 + b; h = h and (h - 1) }
        return out
    }

    companion object {
        val EMPTY = Bits()
        val FULL: Bits = (0 until CELLS).fold(EMPTY) { b, c -> b.with(c) }
        fun of(cells: Iterable<Int>): Bits = cells.fold(EMPTY) { b, c -> b.with(c) }
    }
}

enum class RegionKind { ROW, COLUMN, BOX }

/** One of the 27 clearable regions: 9 rows, 9 columns, 9 boxes. */
data class Region(val kind: RegionKind, val index: Int, val mask: Bits)

object Regions {
    val all: List<Region> = buildList {
        for (r in 0 until SIZE) add(Region(RegionKind.ROW, r, Bits.of((0 until SIZE).map { cellOf(r, it) })))
        for (c in 0 until SIZE) add(Region(RegionKind.COLUMN, c, Bits.of((0 until SIZE).map { cellOf(it, c) })))
        for (b in 0 until SIZE) {
            val r0 = (b / 3) * 3
            val c0 = (b % 3) * 3
            add(Region(RegionKind.BOX, b, Bits.of((0 until 9).map { cellOf(r0 + it / 3, c0 + it % 3) })))
        }
    }

    /** Regions that are completely filled on [board]. */
    fun full(board: Bits): List<Region> = all.filter { board.containsAll(it.mask) }
}
