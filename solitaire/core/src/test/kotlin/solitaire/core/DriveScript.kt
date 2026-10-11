package solitaire.core

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a regular test: writes an adb script that plays a daily deal's solver solution through the real UI
 * (drags for card moves, taps on the stock), to check a whole game end to end on the emulator.
 * Run with SOL_DRIVE=<date key> SOL_DRIVE_OUT=<file>. Geometry mirrors the app's Board `Dims`/`layout`
 * for dad's screen (1080 px wide at 2.625 px/dp). SOL_DRIVE_STOP=n stops after n moves (to leave the
 * end of the game for a screenshot).
 */
class DriveScript {
    @Test fun write() {
        val key = System.getenv("SOL_DRIVE")
        assumeTrue(key != null)
        val out = File(System.getenv("SOL_DRIVE_OUT") ?: "/tmp/drive.txt")
        val stopAfter = System.getenv("SOL_DRIVE_STOP")?.toInt() ?: Int.MAX_VALUE
        val px = (System.getenv("SOL_PX") ?: "3.0").toFloat()
        val top = (System.getenv("SOL_TOP") ?: "229").toFloat()
        val width = 1080 / px
        val height = (System.getenv("SOL_HEIGHT") ?: "576").toFloat()
        val pad = 3f; val gap = 3f
        val cw = (width - 2 * pad - 6 * gap) / 7f
        val ch = cw * 1.42f
        val topY = 4f
        val tabTop = topY + ch + 14f
        fun colX(i: Int) = pad + i * (cw + gap)
        val g0 = Daily.deal(key).game
        // SOL_DRIVE_POLICY=hint follows the app's hints until they run out (to reach "No moves left").
        val sol = if (System.getenv("SOL_DRIVE_POLICY") == "hint") {
            var g = g0
            val ms = ArrayList<Move>()
            while (ms.size < 400) { val h = g.hint() ?: break; ms += h.move; g = g.apply(h.move)!! }
            println("drive: hint policy ${if (g.hint() == null && !g.won) "gets stuck" else "does not get stuck"} after ${ms.size} moves")
            Solver.Result(Solver.Verdict.WIN, ms, 0)
        } else Solver().solve(g0)
        check(sol.verdict == Solver.Verdict.WIN)
        fun cardY(g: Klondike, col: Int, index: Int): Float {
            val cards = g.tableau[col]; val down = g.faceDown[col]
            val up = (cards.size - down - 1).coerceAtLeast(0)
            var ds = cw * 0.17f; var us = cw * 0.58f
            val avail = height - tabTop - ch - 6f
            if (down * ds + up * us > avail) {
                ds = maxOf(cw * 0.08f, if (down > 0) (avail - up * us) / down else ds)
                if (down * ds + up * us > avail && up > 0) us = maxOf(cw * 0.3f, (avail - down * ds) / up)
            }
            var y = tabTop
            for (i in 0 until index) y += if (i < down) ds else us
            return y
        }
        fun P(xDp: Float, yDp: Float) = "${(xDp * px).toInt()} ${(top + yDp * px).toInt()}"
        val lines = ArrayList<String>()
        var g = g0
        for ((n, m) in sol.moves.withIndex()) {
            if (n >= stopAfter || (System.getenv("SOL_DRIVE_AUTO") != null && g.canAutoComplete)) break
            when (m) {
                Move.Draw -> lines += "tap ${P(colX(6) + cw / 2, topY + ch / 2)}"
                is Move.Transfer -> {
                    val from = when (m.from.type) {
                        PileType.WASTE -> P(colX(4) + cw / 2, topY + ch * 0.6f)
                        PileType.FOUNDATION -> P(colX(m.from.index) + cw / 2, topY + ch * 0.6f)
                        PileType.TABLEAU -> {
                            val idx = g.tableau[m.from.index].size - m.count
                            P(colX(m.from.index) + cw / 2, cardY(g, m.from.index, idx) + cw * 0.2f)
                        }
                        else -> error("stock")
                    }
                    val to = when (m.to.type) {
                        PileType.FOUNDATION -> P(colX(m.to.index) + cw / 2, topY + ch / 2)
                        else -> {
                            val col = g.tableau[m.to.index]
                            P(colX(m.to.index) + cw / 2, (if (col.isEmpty()) tabTop else cardY(g, m.to.index, col.size - 1)) + ch * 0.55f)
                        }
                    }
                    lines += "swipe $from $to"
                }
            }
            g = g.apply(m)!!
        }
        out.writeText(lines.joinToString("\n") + "\n")
        println("drive: ${lines.size} of ${sol.moves.size} moves written to $out")
    }
}
