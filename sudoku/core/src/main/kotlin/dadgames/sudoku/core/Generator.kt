package dadgames.sudoku.core

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
enum class Difficulty(val label: String, val level: Int, val note: String) {
    EASY("Easy", 0, "Every number can be found by looking at one row, column or box."),
    MEDIUM("Medium", 1, "Needs pencil marks now and then: pairs and pointing numbers."),
    HARD("Hard", 2, "Triples and X-wings. Take your time."),
    EXPERT("Expert", 3, "Swordfish and wings. A real workout.");

    companion object { fun ofLevel(l: Int) = entries.first { it.level == l } }
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
    /** Easy and Medium keep some extra clues so they feel relaxed, like the common apps. */
    private fun minClues(d: Difficulty) = when (d) {
        Difficulty.EASY -> 36
        Difficulty.MEDIUM -> 28
        Difficulty.HARD -> 22
        Difficulty.EXPERT -> 17
    }

    fun generate(target: Difficulty, rnd: Random, maxAttempts: Int = 2000): Puzzle {
        repeat(maxAttempts) {
            attempt(target, rnd)?.let { return it }
        }
        error("no ${target.label} puzzle after $maxAttempts attempts")
    }

    fun attempt(target: Difficulty, rnd: Random): Puzzle? {
        val solution = Solver.randomSolution(rnd)
        val grid = solution.copyOf()
        var clues = 81
        // Visit symmetric pairs (cell, 80 - cell) in random order; the centre cell alone.
        val order = (0..40).shuffled(rnd)
        for (c in order) {
            val pair = if (c == 40) intArrayOf(40) else intArrayOf(c, 80 - c)
            if (clues - pair.size < minClues(target)) continue
            val saved = pair.map { grid[it] }
            for (p in pair) grid[p] = 0
            if (LogicSolver(grid).run(target.level).solved) clues -= pair.size
            else pair.forEachIndexed { i, p -> grid[p] = saved[i] }
        }
        val g = LogicSolver.grade(grid, target.level)
        if (!g.solved || g.level != target.level) return null
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
