package dadgames.sudoku.core

import kotlin.random.Random

object Fixtures {
    val classicGivens = "530070000600195000098000060800060003400803001700020006060000280000419005000080079".map { it - '0' }
    val classicSolution = "534678912672195348198342567859761423426853791713924856961537284287419635345286179".map { it - '0' }
    val classic = Puzzle(classicGivens, classicSolution, Difficulty.EASY)

    /** A small corpus of generated puzzles per level, made once and shared by the tests. */
    val corpus: Map<Difficulty, List<Puzzle>> by lazy {
        val rnd = Random(2026)
        Difficulty.entries.associateWith { d -> List(if (d == Difficulty.EASY) 40 else 25) { Generator.generate(d, rnd) } }
    }
}
