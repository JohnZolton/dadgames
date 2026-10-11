package blocks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private fun id(family: String, n: Int = 0) = Shapes.all.filter { it.family == family }[n].id
    private val single = id("Single")
    private val square = id("Square")
    private val line3 = id("Line 3")
    private val plus = id("Plus")

    private fun row(r: Int, except: Set<Int> = emptySet()) = Bits.of((0 until SIZE).filter { it !in except }.map { cellOf(r, it) })

    @Test fun pieceSetMatchesWoodoku() {
        assertEquals(49, Shapes.all.size)
        val normalized = Shapes.all.map { s -> s.cells.toSet() }
        assertEquals("no duplicate shapes", 49, normalized.toSet().size)
        val families = Shapes.all.groupingBy { it.family }.eachCount()
        assertEquals(1, families["Single"]); assertEquals(1, families["Square"]); assertEquals(1, families["Plus"])
        assertEquals(8, families["L"]); assertEquals(4, families["T"]); assertEquals(4, families["Corner"])
        assertEquals(4, families["Big corner"]); assertEquals(4, families["Big T"]); assertEquals(4, families["U"])
        for (n in 2..5) assertEquals(2, families["Line $n"])
        for (n in 2..4) assertEquals(2, families["Diagonal $n"])
        assertTrue("no 3x3 square in Woodoku", Shapes.all.none { it.size == 9 })
        Shapes.all.forEachIndexed { i, s -> assertEquals(i, s.id); assertTrue(s.cells.any { it.r == 0 } && s.cells.any { it.c == 0 }) }
    }

    @Test fun bitsBasics() {
        val b = Bits.EMPTY.with(0).with(63).with(64).with(80)
        assertTrue(b[0] && b[63] && b[64] && b[80] && !b[1] && !b[79])
        assertEquals(listOf(0, 63, 64, 80), b.cells())
        assertEquals(4, b.count)
        assertEquals(81, Bits.FULL.count)
        assertEquals(Bits.of(listOf(63, 80)), b andNot Bits.of(listOf(0, 64)))
        assertTrue(Bits.FULL.containsAll(b))
    }

    @Test fun twentySevenRegions() {
        assertEquals(27, Regions.all.size)
        for (r in Regions.all) assertEquals(9, r.mask.count)
        // Every cell is in exactly one row, one column and one box.
        for (c in 0 until CELLS) assertEquals(3, Regions.all.count { it.mask[c] })
        val box4 = Regions.all.first { it.kind == RegionKind.BOX && it.index == 4 }
        assertEquals(listOf(30, 31, 32, 39, 40, 41, 48, 49, 50), box4.mask.cells())
    }

    @Test fun placementBoundsAndOverlap() {
        val l5 = Shapes.all.first { it.family == "Line 5" && it.rows == 1 }
        assertNotNull(l5.masks[cellOf(0, 4)])
        assertNull("would run off the right edge", l5.masks[cellOf(0, 5)])
        val g = GameState(board = Bits.of(listOf(cellOf(0, 2))), tray = listOf(l5.id, -1, -1))
        assertFalse(g.canPlace(0, cellOf(0, 0)))
        assertTrue(g.canPlace(0, cellOf(1, 0)))
        assertFalse(g.canPlace(1, cellOf(1, 0)))
        assertFalse(g.canPlace(0, -1)); assertFalse(g.canPlace(0, 81))
        assertNull(g.place(0, cellOf(0, 0)))
    }

    @Test fun onePointPerBlock() {
        // Screenshot w08: a 2x2 on an empty board scores 4.
        val g = GameState(tray = listOf(square, single, single))
        val r = g.place(0, cellOf(0, 1))!!
        assertEquals(4, r.state.score)
        assertEquals(0, r.combo)
        assertEquals(4, r.state.board.count)
    }

    @Test fun rowClearScoresEighteenBonus() {
        val g = GameState(board = row(0, setOf(8)), tray = listOf(single, single, single))
        val r = g.place(0, cellOf(0, 8))!!
        assertEquals(1, r.combo)
        assertEquals(1, r.streak)
        assertEquals(1 + 18, r.points)
        assertTrue(r.state.board.isEmpty)
        assertEquals(9, r.state.cellsCleared)
    }

    @Test fun columnAndBoxClear() {
        val col = Bits.of((1 until SIZE).map { cellOf(it, 4) })
        val r1 = GameState(board = col, tray = listOf(single, single, single)).place(0, cellOf(0, 4))!!
        assertEquals(RegionKind.COLUMN, r1.regions.single().kind)
        val box = Bits.of(listOf(0, 1, 2, 9, 10, 11, 18, 19))
        val r2 = GameState(board = box, tray = listOf(single, single, single)).place(0, 20)!!
        assertEquals(RegionKind.BOX, r2.regions.single().kind)
        assertTrue(r2.state.board.isEmpty)
    }

    @Test fun simultaneousClearsAreACombo() {
        // Row 0 and box 0 both complete with one single at (0,0): combo 2 = 1 + 28*2 + 10 - 20.
        val board = row(0, setOf(0)) or Bits.of(listOf(10, 11, 9, 18, 19, 20))
        val r = GameState(board = board, tray = listOf(single, single, single)).place(0, 0)!!
        assertEquals(2, r.combo)
        assertEquals(1 + 46, r.points)
        assertTrue(r.state.board.isEmpty)
        assertEquals(15, r.clearedCells.count)
        // Row + column + box through one cell: combo 3.
        val b3 = row(4, setOf(4)) or Bits.of((0 until SIZE).filter { it != 4 }.map { cellOf(it, 4) }) or
            (Regions.all.first { it.kind == RegionKind.BOX && it.index == 4 }.mask andNot Bits.of(listOf(40)))
        val r3 = GameState(board = b3, tray = listOf(single, single, single)).place(0, 40)!!
        assertEquals(3, r3.combo)
        assertEquals(1 + 74, r3.points)
    }

    @Test fun streakGrowsAndResets() {
        var g = GameState(board = row(0, setOf(8)) or row(1, setOf(8)), tray = listOf(single, single, single), rng = Rng(1))
        var r = g.place(0, cellOf(0, 8))!!
        assertEquals(1, r.streak); assertEquals(19, r.points)
        r = r.state.place(1, cellOf(1, 8))!!
        assertEquals(2, r.streak); assertEquals(1 + 28 + 20 - 20, r.points)
        assertEquals(2, r.state.bestStreak)
        g = r.state
        r = g.place(2, cellOf(5, 5))!!
        assertEquals(0, r.streak); assertEquals(1, r.points)
        assertEquals(0, r.state.streak)
        assertEquals(2, r.state.bestStreak)
    }

    @Test fun trayRefillsOnlyWhenEmpty() {
        val g = GameState(tray = listOf(single, single, single), rng = Rng(7))
        val a = g.place(0, 0)!!
        assertFalse(a.refilled); assertEquals(listOf(-1, single, single), a.state.tray)
        val b = a.state.place(2, 40)!!
        assertFalse(b.refilled); assertEquals(listOf(-1, single, -1), b.state.tray)
        val c = b.state.place(1, 80)!!
        assertTrue(c.refilled)
        assertTrue(c.state.tray.all { it >= 0 })
        assertEquals(g.deals + 1, c.state.deals)
    }

    @Test fun gameOverWhenNothingFits() {
        // Board full except a checkerboard of holes that only singles could use; tray has a 3-line and a plus.
        val holes = (0 until CELLS).filter { (rowOf(it) + colOf(it)) % 2 == 0 }
        val board = Bits.FULL andNot Bits.of(holes)
        val g = GameState(board = board, tray = listOf(line3, plus, -1)).checkOver()
        assertTrue(g.over); assertFalse(g.won)
        assertTrue(g.playableSlots().isEmpty())
        val g2 = GameState(board = board, tray = listOf(line3, single, -1)).checkOver()
        assertFalse(g2.over)
        assertEquals(listOf(1), g2.playableSlots())
    }

    @Test fun previewMatchesPlace() {
        val g = GameState(board = row(3, setOf(2, 3, 4)), tray = listOf(line3, single, single))
        val p = g.preview(0, cellOf(3, 2))!!
        assertEquals(1, p.regions.size)
        assertEquals(row(3), p.clearing)
        val r = g.place(0, cellOf(3, 2))!!
        assertEquals(p.clearing, r.clearedCells)
        assertNull(g.preview(0, cellOf(3, 0)))
    }

    @Test fun saveAndRestoreExactly() {
        var g = GameState.newClassic(42)
        val bot = Bot()
        repeat(10) { if (!g.over) { val m = bot.choose(g)!!; g = g.place(m.slot, m.anchor)!!.state } }
        val back = GameState.fromJson(g.toJson())
        assertEquals(g, back)
        // Same upcoming pieces after a restore.
        var a = g; var b = back
        repeat(6) {
            if (!a.over) {
                val m = bot.choose(a)!!
                a = a.place(m.slot, m.anchor)!!.state
                b = b.place(m.slot, m.anchor)!!.state
            }
        }
        assertEquals(a, b)
        // Unknown fields from a newer version are ignored.
        GameState.fromJson(g.toJson().replaceFirst("{", "{\"futureField\":1,"))
    }

    @Test fun gemsAreCollectedByClearingTheirCells() {
        val board = row(2, setOf(8))
        val g = GameState(mode = Mode.DAILY, board = board or Bits.of(listOf(70)), gems = Bits.of(listOf(cellOf(2, 0), 70)), gemsTotal = 2,
            tray = listOf(single, single, single))
        val r = g.place(0, cellOf(2, 8))!!
        assertEquals(Bits.of(listOf(cellOf(2, 0))), r.gemsTaken)
        assertEquals(1, r.state.gemsCollected)
        assertFalse(r.state.over)
        val row7 = Bits.of((0 until SIZE).filter { it != 0 }.map { cellOf(7, it) })
        val r2 = r.state.copy(board = r.state.board or row7).place(1, cellOf(7, 0))!!
        assertEquals(2, r2.state.gemsCollected)
        assertTrue(r2.state.over); assertTrue(r2.state.won)
    }

    @Test fun botPicksTheClear() {
        val g = GameState(board = row(0, setOf(8)), tray = listOf(single, -1, -1))
        assertEquals(Move(0, cellOf(0, 8)), Bot().choose(g))
    }
}
