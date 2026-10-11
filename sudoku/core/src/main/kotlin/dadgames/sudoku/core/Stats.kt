package dadgames.sudoku.core

import kotlinx.serialization.Serializable

@Serializable
data class LevelStats(
    val games: Int = 0,
    val wins: Int = 0,
    val bestMs: Long = 0,
    val totalWinMs: Long = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val perfectWins: Int = 0,
) {
    val winRate: Int get() = if (games == 0) 0 else Math.round(wins * 100f / games)
    val averageMs: Long get() = if (wins == 0) 0 else totalWinMs / wins
}

@Serializable
data class GameRecord(
    val time: Long,
    val difficulty: Difficulty,
    val won: Boolean,
    val ms: Long,
    val mistakes: Int,
    val hints: Int,
    val daily: String? = null,
)

/** Records per difficulty, recent games, and which daily challenges are done. */
@Serializable
data class Stats(
    val levels: Map<Difficulty, LevelStats> = emptyMap(),
    val recent: List<GameRecord> = emptyList(),
    val dailyDone: Set<String> = emptySet(),
) {
    fun of(d: Difficulty) = levels[d] ?: LevelStats()

    val totalGames get() = levels.values.sumOf { it.games }
    val totalWins get() = levels.values.sumOf { it.wins }

    private fun with(d: Difficulty, f: (LevelStats) -> LevelStats) = copy(levels = levels + (d to f(of(d))))

    /** First input in a new game: it now counts as a game played. */
    fun started(d: Difficulty) = with(d) { it.copy(games = it.games + 1) }

    fun won(g: GameState, now: Long): Stats {
        val ms = g.elapsedMs
        val s = with(g.difficulty) {
            val streak = it.streak + 1
            it.copy(
                wins = it.wins + 1,
                bestMs = if (it.bestMs == 0L || ms < it.bestMs) ms else it.bestMs,
                totalWinMs = it.totalWinMs + ms,
                streak = streak, bestStreak = maxOf(it.bestStreak, streak),
                perfectWins = it.perfectWins + if (g.mistakes == 0 && g.hints == 0) 1 else 0,
            )
        }
        val rec = GameRecord(now, g.difficulty, true, ms, g.mistakes, g.hints, g.daily)
        return s.copy(recent = (listOf(rec) + recent).take(RECENT), dailyDone = if (g.daily != null) dailyDone + g.daily else dailyDone)
    }

    /** Left unfinished (new game started, or gave up after three mistakes). */
    fun lost(g: GameState, now: Long): Stats {
        val s = with(g.difficulty) { it.copy(streak = 0) }
        val rec = GameRecord(now, g.difficulty, false, g.elapsedMs, g.mistakes, g.hints, g.daily)
        return s.copy(recent = (listOf(rec) + recent).take(RECENT))
    }


    companion object { const val RECENT = 25 }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
