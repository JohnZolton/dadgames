package blocks.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Woodoku's generator (decompiled): table, orientations, duplicate redraw, single-block rescue, fairness. */
class GeneratorTest {
    private val g = PieceGenerator

    /** Parses research/woodoku-pieces.txt (shape rows A..Q, one column per stage; "-" is 0). */
    private fun tableFromFile(): Array<IntArray> {
        val f = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "research/woodoku-pieces.txt") }.first { it.exists() }
        val rows = f.readLines().filter { it.matches(Regex("""^[A-Q]\s+\d+\s+[01/]+\s.*""")) }
        assertEquals(17, rows.size)
        val perShape = rows.map { line ->
            // Columns after the pattern: either "-" or "N (N.N%)".
            Regex("""(?<=\s)(-|\d+ \(\d+\.\d%\))""").findAll(line.substringAfter(line.split(Regex("\\s+"))[2]))
                .map { if (it.value == "-") 0 else it.value.substringBefore(' ').toInt() }.toList()
        }
        perShape.forEach { assertEquals(12, it.size) }
        return Array(12) { stage -> IntArray(17) { perShape[it][stage] } }
    }

    @Test fun stageTableMatchesDecompiledFile() {
        val file = tableFromFile()
        for (st in 0 until 12) {
            assertArrayEquals("stage $st", file[st], g.weights[st])
            assertEquals(100, g.weights[st].sum())
        }
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 6, 7, 9, 10, 12, 15, 19, 24), g.stageStarts)
        assertEquals(0, g.stage(0)); assertEquals(3, g.stage(5)); assertEquals(4, g.stage(6)); assertEquals(11, g.stage(500))
    }

    @Test fun baseCellCountsMatch() {
        val sizes = mapOf('A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 4, 'G' to 4, 'H' to 3, 'I' to 4,
            'J' to 5, 'K' to 5, 'L' to 4, 'M' to 5, 'N' to 5, 'O' to 2, 'P' to 3, 'Q' to 4)
        for (b in g.bases) assertEquals("${b.letter}", sizes[b.letter], b.cells.size)
    }

    private fun pattern(id: Int): String {
        val s = Shapes[id]
        return (0 until s.rows).joinToString("/") { r -> (0 until s.cols).joinToString("") { c -> if (Pt(r, c) in s.cells) "#" else "." } }
    }

    @Test fun orientationOddsMatchTheFile() {
        // Exact: over the 6 equally likely transforms.
        fun odds(letter: Char): Map<String, Int> {
            val b = g.bases.indexOfFirst { it.letter == letter }
            return (0..1).flatMap { m -> (0..2).map { r -> pattern(g.shapeId(b, m == 1, r)) } }.groupingBy { it }.eachCount()
        }
        assertEquals(mapOf("#./##" to 1, ".#/##" to 2, "##/.#" to 2, "##/#." to 1), odds('H'))
        assertEquals(mapOf(".##/##." to 2, "##./.##" to 2, "#./##/.#" to 1, ".#/##/#." to 1), odds('G'))
        assertEquals(mapOf("#../###" to 1, "###/..#" to 1, "..#/###" to 1, "###/#.." to 1, ".#/.#/##" to 1, "##/.#/.#" to 1), odds('I'))
        assertEquals(mapOf("#../#../###" to 1, "..#/..#/###" to 2, "###/..#/..#" to 2, "###/#../#.." to 1), odds('J'))
        assertEquals(mapOf("#./##/#." to 2, ".#/##/.#" to 2, "###/.#." to 1, ".#./###" to 1), odds('L'))
        assertEquals(mapOf("#../###/#.." to 2, "..#/###/..#" to 2, "###/.#./.#." to 1, ".#./.#./###" to 1), odds('M'))
        assertEquals(mapOf("#.#/###" to 2, "###/#.#" to 2, "##/.#/##" to 2), odds('N'))
        assertEquals(mapOf("#####" to 4, "#/#/#/#/#" to 2), odds('E'))
        assertEquals(mapOf(".#/#." to 3, "#./.#" to 3), odds('O'))
        assertEquals(mapOf("##/##" to 6), odds('F'))
        assertEquals(mapOf(".#./###/.#." to 6), odds('K'))
    }

    @Test fun drawFrequenciesWithinTolerance() {
        // Stage 0 base weights and line orientation (4/6 horizontal) over many draws.
        val n = 200_000
        val counts = IntArray(17)
        var horizontal = 0
        var lines = 0
        var r = Rng(42)
        repeat(n) {
            val (b, id, next) = g.draw(r, 0)
            r = next
            counts[b]++
            if (b in 1..4) { lines++; if (Shapes[id].rows == 1) horizontal++ }
        }
        for (b in 0 until 17) {
            val expected = g.weights[0][b] / 100.0
            assertEquals("base ${g.bases[b].letter}", expected, counts[b].toDouble() / n, 0.006)
        }
        assertEquals(4.0 / 6, horizontal.toDouble() / lines, 0.01)
    }

    @Test fun duplicateBaseIsRedrawnOnce() {
        // Over many trays, duplicates happen only when the redraw also repeats: about weight^2 rarer than without.
        var r = Rng(7)
        var dupTrays = 0
        val n = 50_000
        repeat(n) {
            val (t, next) = g.tray(r, 0, Bits.EMPTY)
            r = next
            val letters = t.map { id -> g.bases.indexOfFirst { b -> (0..1).any { m -> (0..2).any { k -> g.shapeId(g.bases.indexOf(b), m == 1, k) == id } } } }
            if (letters.toSet().size < 3) dupTrays++
        }
        // Without the redraw a stage-0 tray repeats a base ~25% of the time; with it, a few percent.
        assertTrue("dup trays ${dupTrays * 100.0 / n}%", dupTrays.toDouble() / n < 0.08)
    }

    @Test fun singleBlockRescueOnlyWhenNothingFits() {
        // Checkerboard of holes: only a single (or a 2-diagonal) can fit.
        val board = Bits.FULL andNot Bits.of((0 until CELLS).filter { (rowOf(it) + colOf(it)) % 2 == 0 })
        var rescued = 0
        for (seed in 0L until 3000L) {
            val (onEmpty, _) = g.tray(Rng(seed), 3, Bits.EMPTY)
            val (onFull, _) = g.tray(Rng(seed), 3, board)
            val anyFits = onEmpty.any { Shapes[it].fitsAnywhere(board) }
            if (anyFits) assertEquals("no rescue when something fits", onEmpty, onFull)
            else {
                rescued++
                // Exactly one piece was swapped, and it became the single block.
                val diff = onFull.indices.filter { onFull[it] != onEmpty[it] }
                assertEquals(1, diff.size)
                assertEquals(PieceGenerator.SINGLE, onFull[diff[0]])
            }
        }
        assertTrue(rescued > 100)
        // A dealt tray never ends the game on its own.
        val gs = GameState(board = board, rng = Rng(5), deals = 3).deal().checkOver()
        assertTrue(!gs.over)
    }

    @Test fun boardIsIgnoredOtherwise() {
        val partial = Bits.of((0 until 30).toList())
        for (seed in 0L until 500L) {
            val (a, _) = g.tray(Rng(seed), 8, Bits.EMPTY)
            val (b, _) = g.tray(Rng(seed), 8, partial)
            if (a.any { Shapes[it].fitsAnywhere(partial) }) assertEquals(a, b)
        }
    }

    @Test fun traysDealtIsSavedAndDrivesTheStage() {
        var s = GameState.newClassic(11)
        assertEquals(1, s.deals)
        val back = GameState.fromJson(s.toJson())
        assertEquals(s.deals, back.deals)
        // Late-game trays can contain diagonals (stage 5+), first trays never do.
        val diag = Shapes.all.filter { it.family.startsWith("Diagonal") }.map { it.id }.toSet()
        repeat(2000) { s = GameState.newClassic(it.toLong()); assertTrue(s.tray.none { id -> id in diag }) }
    }
}
