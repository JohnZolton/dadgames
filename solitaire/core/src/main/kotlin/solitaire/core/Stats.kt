package solitaire.core

import kotlinx.serialization.Serializable

/** One row of the top-10 scores table, like dad's app: score, time, date, moves. */
@Serializable
data class ScoreEntry(val score: Int, val seconds: Long, val date: String, val moves: Int)

@Serializable
data class GameRecord(val epochMs: Long, val won: Boolean, val score: Int, val seconds: Long, val moves: Int, val daily: Boolean = false)

@Serializable
data class Stats(
    val won: Int = 0,
    val played: Int = 0,
    val bestScore: Int = 0,
    /** Fewest moves in a win (0 = none yet). */
    val leastMoves: Int = 0,
    /** Fastest win in seconds (0 = none yet). */
    val bestTime: Long = 0,
    val totalTime: Long = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val top: List<ScoreEntry> = emptyList(),
    /** Newest first, capped at 20. */
    val recent: List<GameRecord> = emptyList(),
) {
    val winRate: Int get() = if (played == 0) 0 else Math.round(won * 100f / played)

    /** Where a score would land in the top 10 (0-based), or null if it doesn't make it. */
    fun rankOf(score: Int): Int? {
        val i = top.indexOfFirst { score > it.score }.let { if (it < 0) top.size else it }
        return if (i < 10) i else null
    }

    fun recordWin(score: Int, seconds: Long, moves: Int, date: String, epochMs: Long, daily: Boolean): Stats {
        val s = streak + 1
        val entry = ScoreEntry(score, seconds, date, moves)
        val pos = rankOf(score)
        val newTop = if (pos == null) top else (top.take(pos) + entry + top.drop(pos)).take(10)
        return copy(
            won = won + 1, played = played + 1,
            bestScore = maxOf(bestScore, score),
            leastMoves = if (leastMoves == 0) moves else minOf(leastMoves, moves),
            bestTime = if (bestTime == 0L) seconds else minOf(bestTime, seconds),
            totalTime = totalTime + seconds,
            streak = s, bestStreak = maxOf(bestStreak, s),
            top = newTop,
            recent = (listOf(GameRecord(epochMs, true, score, seconds, moves, daily)) + recent).take(20),
        )
    }

    /** A game given up (new game started after at least one move). */
    fun recordLoss(score: Int, seconds: Long, moves: Int, epochMs: Long, daily: Boolean): Stats = copy(
        played = played + 1, totalTime = totalTime + seconds, streak = 0,
        recent = (listOf(GameRecord(epochMs, false, score, seconds, moves, daily)) + recent).take(20),
    )

    companion object {
        /** Dad's numbers from his old app (Draw 1), captured 2026-10-11. [research/README.md] */
        val seeded = Stats(
            won = 1069, played = 3647, bestScore = 5371, leastMoves = 90, bestTime = 88,
            totalTime = 9L * 86400 + 13 * 3600 + 14 * 60 + 57,
            top = listOf(
                ScoreEntry(5371, 140, "2025-06-08", 108),
                ScoreEntry(5305, 116, "2026-01-11", 118),
                ScoreEntry(5266, 122, "2025-05-27", 119),
                ScoreEntry(5266, 122, "2025-08-24", 126),
                ScoreEntry(5260, 123, "2025-05-15", 123),
                ScoreEntry(5258, 104, "2025-09-22", 100),
                ScoreEntry(5240, 149, "2025-07-28", 109),
                ScoreEntry(5221, 130, "2025-05-22", 114),
                ScoreEntry(5221, 130, "2025-10-14", 123),
                ScoreEntry(5195, 135, "2026-01-14", 121),
            ),
        )
    }
}

/** "1:28", "1:02:03". */
fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "9 d 13:14:57" for the total time played. */
fun longClock(seconds: Long): String {
    val d = seconds / 86400
    val rest = seconds % 86400
    val hms = "%d:%02d:%02d".format(rest / 3600, rest % 3600 / 60, rest % 60)
    return if (d > 0) "$d d $hms" else hms
}
