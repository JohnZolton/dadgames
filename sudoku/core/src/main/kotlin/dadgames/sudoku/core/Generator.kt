package dadgames.sudoku.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Difficulty(val label: String, val level: Int, val note: String) {
    EASY("Easy", 0, "Every number can be found by looking along a row, column or box."),
    MEDIUM("Medium", 1, "Now and then you need pencil marks: a pair or a pointing number."),
    HARD("Hard", 2, "Plenty of pairs, plus triples and X-wings."),
    EXPERT("Expert", 3, "Wings, colouring and chains. A real workout.");

    companion object {
        fun ofLevel(l: Int) = entries.first { it.level == l }

        /** Most steps of the Medium kind a Medium puzzle may need; more makes it Hard. */
        const val MEDIUM_MAX_STEPS = 3

        /**
         * Grade from how much reasoning a solve needs, not only the hardest step:
         * Easy only singles; Medium up to three pointing/pair steps; Hard more of those, or any triple
         * or X-wing; Expert anything harder.
         */
        fun classify(level: Int, counts: List<Int>): Difficulty = when {
            level == 0 -> EASY
            level == 1 && counts[1] <= MEDIUM_MAX_STEPS -> MEDIUM
            level <= 2 -> HARD
            else -> EXPERT
        }
    }
}

/** A puzzle: 81 givens (0 = empty) and its unique solution. */
@Serializable
data class Puzzle(val givens: List<Int>, val solution: List<Int>, val difficulty: Difficulty) {
    val clues get() = givens.count { it != 0 }

    /** Compact text form: 81 digits, then 81 digits, then the level. Used for the background pool. */
    fun encode(): String = givens.joinToString("") + solution.joinToString("") + difficulty.level

    companion object {
        fun decode(s: String): Puzzle? = runCatching {
            require(s.length == 163)
            Puzzle(s.take(81).map { it - '0' }, s.substring(81, 162).map { it - '0' }, Difficulty.ofLevel(s[162] - '0'))
        }.getOrNull()
    }
}

/**
 * Makes unique-solution puzzles graded by the techniques needed (see [LogicSolver]).
 *
 * Start from a random full grid and take out clues in symmetric pairs. A removal is kept only if the
 * human-style solver can still finish the puzzle using techniques up to the target level, which also
 * guarantees a unique solution. The result is accepted when it actually needs the target level.
 */
object Generator {
    /** Fewest clues per level. Easy stops at a random count in 30..34, like the common apps. */
    private fun minClues(d: Difficulty, rnd: Random) = when (d) {
        Difficulty.EASY -> rnd.nextInt(30, 35)
        Difficulty.MEDIUM -> 27
        Difficulty.HARD -> 24
        Difficulty.EXPERT -> 20
    }

    fun generate(target: Difficulty, rnd: Random, maxAttempts: Int = 5000): Puzzle {
        repeat(maxAttempts) {
            attempt(target, rnd)?.let { return it }
        }
        error("no ${target.label} puzzle after $maxAttempts attempts")
    }

    fun attempt(target: Difficulty, rnd: Random): Puzzle? {
        val solution = Solver.randomSolution(rnd)
        val grid = solution.copyOf()
        var clues = 81
        val floor = minClues(target, rnd)
        // Visit symmetric pairs (cell, 80 - cell) in random order; the centre cell alone. A removal stays
        // only if the puzzle can still be solved without going past the target level.
        val order = (0..40).shuffled(rnd)
        for (c in order) {
            val pair = if (c == 40) intArrayOf(40) else intArrayOf(c, 80 - c)
            if (clues - pair.size < floor) continue
            val saved = pair.map { grid[it] }
            for (p in pair) grid[p] = 0
            val d = LogicSolver(grid).run(target.level).difficulty
            if (d != null && d <= target) clues -= pair.size
            else pair.forEachIndexed { i, p -> grid[p] = saved[i] }
        }
        if (target == Difficulty.EXPERT) {
            // Expert: also try single squares (breaking symmetry a little) for a leaner grid.
            for (c in (0 until 81).shuffled(rnd)) {
                if (grid[c] == 0 || clues - 1 < floor) continue
                val saved = grid[c]
                grid[c] = 0
                if (LogicSolver(grid).run(3).solved) clues-- else grid[c] = saved
            }
        }
        if (target == Difficulty.EASY && clues > 34) return null
        if (LogicSolver.grade(grid).difficulty != target) return null
        return Puzzle(grid.toList(), solution.toList(), target)
    }

    /** Daily challenge difficulty by weekday (1 = Monday ... 7 = Sunday): gentle early week, harder at the weekend. */
    fun dailyDifficulty(dayOfWeek: Int): Difficulty = when (dayOfWeek) {
        1, 2 -> Difficulty.EASY
        3, 4, 5 -> Difficulty.MEDIUM
        else -> Difficulty.HARD
    }

    /** Same puzzle for the same date on every phone: seeded from the date (yyyy-mm-dd). */
    fun daily(date: String, dayOfWeek: Int): Puzzle {
        val seed = date.filter { it.isDigit() }.toLong() * 7919L + 17L
        return generate(dailyDifficulty(dayOfWeek), Random(seed))
    }
}
