package blocks.core

import kotlin.random.Random

/**
 * Daily challenge: a seeded puzzle per date. Some wooden blocks start on the board and a few of them carry gems.
 * Clear the rows, columns or boxes holding every gem to win. The pieces come from the same fair random stream
 * as Classic, seeded by the date, so every attempt at a given day deals the same pieces.
 *
 * A puzzle is only accepted if the hint bot can solve it, so no day is impossible.
 */
object Daily {
    val milestones = listOf(1, 3, 10, 24)

    fun seedFor(date: String, variant: Int = 0): Long {
        var h = -0x340d631b7bdddcdbL
        for (ch in "$date#$variant") { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
        return h
    }

    fun generate(date: String): GameState {
        var fallback: GameState? = null
        for (variant in 0 until 12) {
            val g = layout(date, variant)
            if (fallback == null) fallback = g
            if (solvable(g)) return g
        }
        return fallback!!
    }

    /** Board, gems and piece stream for one variant, before checking it's solvable. */
    fun layout(date: String, variant: Int): GameState {
        val seed = seedFor(date, variant)
        val rnd = Random(seed)
        val gemCount = 6 + rnd.nextInt(5)          // 6..10 gems
        val target = gemCount + 8 + rnd.nextInt(8) // 14..25 starting blocks
        var board = Bits.EMPTY
        var guard = 0
        while (board.count < target && guard++ < 500) {
            val shape = Shapes.all[rnd.nextInt(Shapes.all.size)]
            if (shape.size > 4) continue
            val anchor = rnd.nextInt(CELLS)
            if (!shape.canPlace(board, anchor)) continue
            val next = board or shape.masks[anchor]!!
            if (Regions.full(next).isNotEmpty()) continue
            // Keep regions from getting nearly full at the start: no row/column/box above 6 of 9.
            if (Regions.all.any { (next and it.mask).count > 6 }) continue
            board = next
        }
        val filled = board.cells().shuffled(rnd)
        val gems = Bits.of(filled.take(gemCount.coerceAtMost(filled.size)))
        val g = GameState(
            mode = Mode.DAILY, board = board, gems = gems, gemsTotal = gems.count,
            rng = Rng(seed xor 0x5DEECE66DL), date = date,
        )
        return g.deal().checkOver()
    }

    fun solvable(start: GameState, maxMoves: Int = 120): Boolean {
        val bot = Bot(breadth = 3)
        var g = start
        while (!g.over && g.moves < maxMoves) {
            val m = bot.choose(g) ?: return false
            g = g.place(m.slot, m.anchor)!!.state
        }
        return g.won
    }
}
